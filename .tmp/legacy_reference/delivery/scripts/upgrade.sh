#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
[[ "${2:-}" == '--confirm-upgrade' ]] || { echo 'Usage: upgrade.sh NEW_PACKAGE_ROOT --confirm-upgrade' >&2; exit 1; }
new_root="$(cd "$1" && pwd -P)"
[[ "$new_root" != "$PACKAGE_ROOT" ]] || { echo 'New package must be different' >&2; exit 1; }
bash "$new_root/scripts/verify-package.sh" >/dev/null
backup_json="$(bash "$PACKAGE_ROOT/scripts/backup.sh" pre-upgrade)"
backup_root="${backup_json#*\"backupRoot\": \"}"; backup_root="${backup_root%%\"*}"
bash "$PACKAGE_ROOT/scripts/stop.sh" >/dev/null
cp -p "$ENV_FILE" "$new_root/.env"
if bash "$new_root/scripts/start.sh"; then
  printf '{"status":"UPGRADED","from":"%s","to":"%s","backupRoot":"%s"}\n' "$PACKAGE_ROOT" "$new_root" "$backup_root"
else
  echo "Upgrade failed. Roll back: bash '$PACKAGE_ROOT/scripts/rollback.sh' '$PACKAGE_ROOT' '$backup_root' --confirm-rollback" >&2
  exit 1
fi
