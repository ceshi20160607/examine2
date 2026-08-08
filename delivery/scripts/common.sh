#!/usr/bin/env bash
set -euo pipefail

PACKAGE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
COMPOSE_FILE="$PACKAGE_ROOT/deploy/compose.yaml"
ENV_FILE="$PACKAGE_ROOT/.env"

require_env() {
  [[ -f "$ENV_FILE" ]] || { echo "Missing $ENV_FILE; copy .env.example and replace placeholders." >&2; return 1; }
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
  local key value
  for key in EXAMINE_DB_NAME EXAMINE_DB_USERNAME EXAMINE_DB_PASSWORD EXAMINE_DB_ROOT_PASSWORD EXAMINE_REDIS_PASSWORD EXAMINE_DATA_ROOT EXAMINE_BOOTSTRAP_ROOT_USERNAME EXAMINE_BOOTSTRAP_ROOT_PASSWORD; do
    value="${!key:-}"
    [[ -n "$value" ]] || { echo "Missing $key" >&2; return 1; }
    [[ "$value" != *CHANGE_ME* && "$value" != *PLACEHOLDER* && "$value" != *EXAMPLE* ]] || { echo "Replace placeholder for $key" >&2; return 1; }
  done
  [[ "$EXAMINE_DATA_ROOT" == /* || "$EXAMINE_DATA_ROOT" =~ ^[A-Za-z]:[\\/] ]] || { echo 'EXAMINE_DATA_ROOT must be absolute' >&2; return 1; }
  for key in EXAMINE_DB_PASSWORD EXAMINE_DB_ROOT_PASSWORD EXAMINE_REDIS_PASSWORD EXAMINE_BOOTSTRAP_ROOT_PASSWORD; do
    value="${!key}"
    (( ${#value} >= 16 )) || { echo "$key must contain at least 16 characters" >&2; return 1; }
  done
}

require_compose() {
  docker compose version >/dev/null
  [[ -f "$COMPOSE_FILE" ]] || { echo "Missing $COMPOSE_FILE" >&2; return 1; }
}

compose() {
  EXAMINE_ARTIFACT_ROOT="${EXAMINE_ARTIFACT_ROOT:-$PACKAGE_ROOT}" \
    docker compose --project-name examine2 --project-directory "$PACKAGE_ROOT" \
    --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

wait_health() {
  local timeout="${1:-240}" backend_port="${EXAMINE_BACKEND_PORT:-8080}" web_port="${EXAMINE_WEB_PORT:-8088}" elapsed=0
  while (( elapsed < timeout )); do
    if curl --fail --silent "http://127.0.0.1:${backend_port}/management/health" | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"' \
      && curl --fail --silent --head "http://127.0.0.1:${web_port}/" >/dev/null; then
      printf '{"status":"UP","backend":"http://127.0.0.1:%s/management/health","frontend":"http://127.0.0.1:%s/"}\n' "$backend_port" "$web_port"
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  echo "Examine2 did not become healthy in ${timeout}s" >&2
  return 1
}

inside_package() {
  local candidate
  candidate="$(cd "$(dirname "$1")" && pwd -P)/$(basename "$1")"
  [[ "$candidate" == "$PACKAGE_ROOT"/* ]] || { echo "Path must stay inside package: $candidate" >&2; return 1; }
  printf '%s\n' "$candidate"
}

inside_data_root() {
  local root candidate
  root="$(cd "$EXAMINE_DATA_ROOT" 2>/dev/null && pwd -P || printf '%s' "$EXAMINE_DATA_ROOT")"
  candidate="$(cd "$(dirname "$1")" && pwd -P)/$(basename "$1")"
  [[ "$candidate" == "$root"/* ]] || { echo "Path must stay inside data root: $candidate" >&2; return 1; }
  printf '%s\n' "$candidate"
}
