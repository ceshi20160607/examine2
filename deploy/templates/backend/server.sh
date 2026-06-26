#!/usr/bin/env bash
set -euo pipefail

ACTION="${1:-status}"
APP_HOME="$(cd "$(dirname "$0")" && pwd)"
APP_NAME="examine-web"
JAR_FILE="${APP_HOME}/${APP_NAME}.jar"
PID_FILE="${APP_HOME}/${APP_NAME}.pid"
PID_KIND_FILE="${APP_HOME}/${APP_NAME}.pid.kind"
LOG_DIR="${APP_HOME}/logs"
CONSOLE_LOG="${LOG_DIR}/console.out"
CONSOLE_ERR="${LOG_DIR}/console.err"
STOP_TIMEOUT="${STOP_TIMEOUT:-30}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-60}"

ensure_dirs() {
  mkdir -p "${LOG_DIR}" "${APP_HOME}/data/uploads"
}

resolve_java_bin() {
  if [[ -n "${JAVA_HOME:-}" ]]; then
    if [[ -x "${JAVA_HOME}/bin/java" ]]; then
      echo "${JAVA_HOME}/bin/java"
      return 0
    fi
    if [[ -x "${JAVA_HOME}/bin/java.exe" ]]; then
      echo "${JAVA_HOME}/bin/java.exe"
      return 0
    fi
  fi
  if command -v java >/dev/null 2>&1; then
    command -v java
    return 0
  fi
  if command -v java.exe >/dev/null 2>&1; then
    command -v java.exe
    return 0
  fi
  return 1
}

is_running() {
  [[ -f "${PID_FILE}" ]] || return 1
  local pid
  pid="$(cat "${PID_FILE}")"
  [[ -n "${pid}" ]] || return 1
  if [[ -f "${PID_KIND_FILE}" ]] && [[ "$(cat "${PID_KIND_FILE}")" == "windows" ]]; then
    SERVER_PID="${pid}" WSLENV="SERVER_PID${WSLENV:+:${WSLENV}}" powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "\$process = Get-Process -Id ([int]\$env:SERVER_PID) -ErrorAction SilentlyContinue; if (\$null -eq \$process) { exit 1 }" >/dev/null 2>&1
    return $?
  fi
  kill -0 "${pid}" >/dev/null 2>&1
}

health_url() {
  if [[ -n "${UNEXAMINE_HEALTH_URL:-}" ]]; then
    echo "${UNEXAMINE_HEALTH_URL}"
    return 0
  fi
  local port context
  port="${UNEXAMINE_SERVER_PORT:-9999}"
  context="${UNEXAMINE_CONTEXT_PATH:-}"
  if [[ -n "${context}" && "${context}" != /* ]]; then
    context="/${context}"
  fi
  if [[ "${context}" == "/" ]]; then
    context=""
  fi
  echo "http://127.0.0.1:${port}${context}/api/v1/health"
}

fetch_health() {
  local url="$1"
  if command -v curl >/dev/null 2>&1; then
    curl -fsS --max-time 5 "${url}"
    return $?
  fi
  if command -v curl.exe >/dev/null 2>&1; then
    curl.exe -fsS --max-time 5 "${url}"
    return $?
  fi
  if command -v wget >/dev/null 2>&1; then
    wget -qO- --timeout=5 "${url}"
    return $?
  fi
  echo "curl or wget is required for health checks" >&2
  return 127
}

json_value_is() {
  local response="$1"
  local key="$2"
  local value="$3"
  printf '%s' "${response}" | tr -d '\r\n' | grep -Eq "\"${key}\"[[:space:]]*:[[:space:]]*\"${value}\"[[:space:]]*[,}]"
}

is_health_up() {
  local response="$1"
  local python_bin
  python_bin="$(command -v python3 2>/dev/null || command -v python 2>/dev/null || true)"
  if [[ -n "${python_bin}" ]]; then
    if HEALTH_RESPONSE="${response}" "${python_bin}" - <<'PY'
import json
import os
import sys

try:
    payload = json.loads(os.environ.get("HEALTH_RESPONSE", ""))
    data = payload.get("data") or {}
    ok = (
        data.get("status") == "UP"
        and data.get("database") == "UP"
        and data.get("schema") == "UP"
        and data.get("redis") == "UP"
    )
except Exception:
    ok = False

sys.exit(0 if ok else 1)
PY
    then
      return 0
    fi
    return 1
  fi

  json_value_is "${response}" "status" "UP" \
    && json_value_is "${response}" "database" "UP" \
    && json_value_is "${response}" "schema" "UP" \
    && json_value_is "${response}" "redis" "UP"
}

print_recent_logs() {
  echo "--- ${CONSOLE_ERR} tail ---" >&2
  tail -n 80 "${CONSOLE_ERR}" >&2 2>/dev/null || true
  echo "--- ${CONSOLE_LOG} tail ---" >&2
  tail -n 80 "${CONSOLE_LOG}" >&2 2>/dev/null || true
}

health_app() {
  local url response
  url="$(health_url)"
  echo "Health URL: ${url}"
  if ! response="$(fetch_health "${url}" 2>&1)"; then
    echo "${response}" >&2
    return 1
  fi
  echo "${response}"
  is_health_up "${response}"
}

wait_health() {
  local url response
  url="$(health_url)"
  echo "Waiting for health: ${url}"
  response=""
  for ((i = 1; i <= HEALTH_TIMEOUT; i++)); do
    if ! is_running; then
      echo "${APP_NAME} stopped before health became UP" >&2
      print_recent_logs
      return 1
    fi
    if response="$(fetch_health "${url}" 2>&1)" && is_health_up "${response}"; then
      echo "${APP_NAME} health is UP"
      return 0
    fi
    sleep 1
  done

  echo "${APP_NAME} health did not become UP in ${HEALTH_TIMEOUT}s" >&2
  echo "Last health response:" >&2
  echo "${response}" >&2
  print_recent_logs
  return 1
}

start_windows_java() {
  local java_bin="$1"
  local java_win jar_win app_home_win out_win err_win config_dir win_pid bridge_vars
  java_win="$(wslpath -w "${java_bin}")"
  jar_win="$(wslpath -w "${JAR_FILE}")"
  app_home_win="$(wslpath -w "${APP_HOME}")"
  out_win="$(wslpath -w "${CONSOLE_LOG}")"
  err_win="$(wslpath -w "${CONSOLE_ERR}")"
  config_dir="optional:file:$(wslpath -m "${APP_HOME}")/"

  export SERVER_JAVA_BIN="${java_win}"
  export SERVER_JAR="${jar_win}"
  export SERVER_APP_HOME="${app_home_win}"
  export SERVER_STDOUT="${out_win}"
  export SERVER_STDERR="${err_win}"
  export SERVER_CONFIG_DIR="${config_dir}"
  bridge_vars="SERVER_JAVA_BIN:SERVER_JAR:SERVER_APP_HOME:SERVER_STDOUT:SERVER_STDERR:SERVER_CONFIG_DIR:JAVA_OPTS:UNEXAMINE_SERVER_PORT:UNEXAMINE_CONTEXT_PATH:UNEXAMINE_DB_URL:UNEXAMINE_DB_USERNAME:UNEXAMINE_DB_PASSWORD:UNEXAMINE_REDIS_HOST:UNEXAMINE_REDIS_PORT:UNEXAMINE_REDIS_PASSWORD:UNEXAMINE_REDIS_DATABASE:UNEXAMINE_REDIS_TIMEOUT:UNEXAMINE_LOG_LEVEL:UNEXAMINE_UPLOAD_LOCAL_ROOT:UNEXAMINE_CORS_ALLOWED_ORIGINS:UNEXAMINE_ALLOW_ACCOUNT_ID_HEADER:UNEXAMINE_ACCESS_TOKEN_TTL:UNEXAMINE_REFRESH_TOKEN_TTL"
  export WSLENV="${bridge_vars}${WSLENV:+:${WSLENV}}"

  win_pid="$(powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "\$arguments = @(); if (-not [string]::IsNullOrWhiteSpace(\$env:JAVA_OPTS)) { \$arguments += \$env:JAVA_OPTS -split ' ' }; \$arguments += @('-jar', \$env:SERVER_JAR, \"--spring.config.additional-location=\$env:SERVER_CONFIG_DIR\"); \$process = Start-Process -FilePath \$env:SERVER_JAVA_BIN -ArgumentList \$arguments -WorkingDirectory \$env:SERVER_APP_HOME -RedirectStandardOutput \$env:SERVER_STDOUT -RedirectStandardError \$env:SERVER_STDERR -PassThru; [Console]::Out.Write(\$process.Id)" | tr -d '\r')"
  if [[ -z "${win_pid}" ]]; then
    echo "Failed to start ${APP_NAME} through Windows java.exe" >&2
    exit 1
  fi
  echo "${win_pid}" > "${PID_FILE}"
  echo "windows" > "${PID_KIND_FILE}"
}

start_linux_java() {
  local java_bin="$1"
  nohup "${java_bin}" ${JAVA_OPTS:-} -jar "${JAR_FILE}" \
    --spring.config.additional-location="optional:file:${APP_HOME}/" \
    >> "${CONSOLE_LOG}" 2>> "${CONSOLE_ERR}" &
  echo "$!" > "${PID_FILE}"
  echo "linux" > "${PID_KIND_FILE}"
}

start_app() {
  ensure_dirs
  if [[ ! -f "${JAR_FILE}" ]]; then
    echo "Jar not found: ${JAR_FILE}" >&2
    exit 1
  fi
  if is_running; then
    echo "${APP_NAME} is already running, pid=$(cat "${PID_FILE}")"
    health_app
    return $?
  fi
  rm -f "${PID_FILE}" "${PID_KIND_FILE}"

  local java_bin
  if ! java_bin="$(resolve_java_bin)"; then
    echo "java not found. Set JAVA_HOME or put java in PATH." >&2
    exit 1
  fi

  cd "${APP_HOME}"
  if [[ "${java_bin}" == *.exe ]] && command -v wslpath >/dev/null 2>&1 && command -v powershell.exe >/dev/null 2>&1; then
    start_windows_java "${java_bin}"
  else
    start_linux_java "${java_bin}"
  fi
  echo "${APP_NAME} started, pid=$(cat "${PID_FILE}"), log=${CONSOLE_LOG}, err=${CONSOLE_ERR}"
  if ! wait_health; then
    echo "${APP_NAME} startup health check failed; stopping broken service" >&2
    stop_app >/dev/null 2>&1 || true
    exit 1
  fi
}

stop_app() {
  if [[ ! -f "${PID_FILE}" ]]; then
    echo "${APP_NAME} is not running"
    return 0
  fi

  local pid
  pid="$(cat "${PID_FILE}")"
  if [[ -z "${pid}" ]] || ! is_running; then
    rm -f "${PID_FILE}" "${PID_KIND_FILE}"
    echo "${APP_NAME} is not running"
    return 0
  fi

  if [[ -f "${PID_KIND_FILE}" ]] && [[ "$(cat "${PID_KIND_FILE}")" == "windows" ]]; then
    SERVER_PID="${pid}" WSLENV="SERVER_PID${WSLENV:+:${WSLENV}}" powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Stop-Process -Id ([int]\$env:SERVER_PID) -ErrorAction SilentlyContinue" >/dev/null 2>&1 || true
  else
    kill "${pid}"
  fi
  for ((i = 1; i <= STOP_TIMEOUT; i++)); do
    if ! is_running; then
      rm -f "${PID_FILE}" "${PID_KIND_FILE}"
      echo "${APP_NAME} stopped"
      return 0
    fi
    sleep 1
  done

  echo "${APP_NAME} did not stop in ${STOP_TIMEOUT}s, killing pid=${pid}"
  if [[ -f "${PID_KIND_FILE}" ]] && [[ "$(cat "${PID_KIND_FILE}")" == "windows" ]]; then
    SERVER_PID="${pid}" WSLENV="SERVER_PID${WSLENV:+:${WSLENV}}" powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Stop-Process -Id ([int]\$env:SERVER_PID) -Force -ErrorAction SilentlyContinue" >/dev/null 2>&1 || true
  else
    kill -9 "${pid}" >/dev/null 2>&1 || true
  fi
  rm -f "${PID_FILE}" "${PID_KIND_FILE}"
  echo "${APP_NAME} stopped"
}

status_app() {
  if is_running; then
    echo "${APP_NAME} is running, pid=$(cat "${PID_FILE}")"
    return 0
  fi
  if [[ -f "${PID_FILE}" ]]; then
    echo "${APP_NAME} is stopped, stale pid file=${PID_FILE}"
    return 1
  fi
  echo "${APP_NAME} is stopped"
  return 3
}

case "${ACTION}" in
  start)
    start_app
    ;;
  stop)
    stop_app
    ;;
  restart)
    stop_app
    start_app
    ;;
  status)
    status_app
    ;;
  health)
    health_app
    ;;
  *)
    echo "Usage: $0 {start|stop|restart|status|health}" >&2
    exit 2
    ;;
esac
