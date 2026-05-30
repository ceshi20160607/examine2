#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

APP_NAME="unexamine"
MODE="${1:-start}"
LOG_DIR="$DIR/logs"
PID_FILE="$LOG_DIR/${APP_NAME}.pid"
CONSOLE_LOG="$LOG_DIR/${APP_NAME}.console.log"
CONFIG_DIR="$DIR/config"
CONFIG_FILE="$CONFIG_DIR/application.yml"

mkdir -p "$LOG_DIR"

usage() {
  echo "Usage: ./unexamine.sh {init-config|start|stop|restart|status|fg}"
}

is_running() {
  [[ -f "$PID_FILE" ]] || return 1
  local pid
  pid="$(cat "$PID_FILE" 2>/dev/null)" || return 1
  [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null
}

status_cmd() {
  if is_running; then
    echo "running pid=$(cat "$PID_FILE") log=$CONSOLE_LOG"
    return 0
  fi
  [[ -f "$PID_FILE" ]] && rm -f "$PID_FILE"
  echo "not running"
  return 1
}

stop_cmd() {
  if [[ ! -f "$PID_FILE" ]]; then
    echo "not running (no pid file)"
    return 0
  fi

  local pid
  pid="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    kill "$pid"
    for _ in $(seq 1 30); do
      kill -0 "$pid" 2>/dev/null || break
      sleep 1
    done
    if kill -0 "$pid" 2>/dev/null; then
      echo "force kill $pid"
      kill -9 "$pid" 2>/dev/null || true
    fi
    echo "stopped pid=$pid"
  else
    echo "stale pid file removed"
  fi
  rm -f "$PID_FILE"
}

ensure_config() {
  if [[ ! -f "$CONFIG_FILE" ]]; then
    if [[ -f "$CONFIG_DIR/application.yml.example" ]]; then
      echo "Missing config/application.yml." >&2
      echo "Run: ./unexamine.sh init-config" >&2
      echo "Then edit MySQL / Redis settings in $CONFIG_FILE before starting." >&2
    else
      echo "Missing config file: $CONFIG_FILE" >&2
    fi
    exit 1
  fi
  if grep -Eq 'change-me|replace-with-random-32-plus-chars' "$CONFIG_FILE"; then
    echo "Config still contains placeholders. Edit these lines before starting:" >&2
    grep -nE 'change-me|replace-with-random-32-plus-chars' "$CONFIG_FILE" >&2 || true
    exit 1
  fi
}

random_key() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 32
    return
  fi
  if [[ -r /dev/urandom ]] && command -v od >/dev/null 2>&1; then
    od -An -N32 -tx1 /dev/urandom | tr -d ' \n'
    return
  fi
  date +%s%N | sha256sum | awk '{print $1}'
}

init_config_cmd() {
  mkdir -p "$CONFIG_DIR"
  if [[ -f "$CONFIG_FILE" ]]; then
    echo "config/application.yml already exists: $CONFIG_FILE"
    echo "Not overwritten."
    return 0
  fi
  local example="$CONFIG_DIR/application.yml.example"
  if [[ ! -f "$example" ]]; then
    echo "Missing config template: $example" >&2
    exit 1
  fi
  cp "$example" "$CONFIG_FILE"
  local key
  key="$(random_key)"
  sed -i "s/replace-with-random-32-plus-chars/$key/g" "$CONFIG_FILE"
  echo "created $CONFIG_FILE"
  echo "A random examine.openapi.signing-master-key has been generated."
  echo "Now edit datasource/redis settings, then run: ./unexamine.sh start"
  if grep -nE 'change-me|replace-with-random-32-plus-chars' "$CONFIG_FILE"; then
    echo "Remaining placeholders are shown above."
  fi
}

resolve_java() {
  local java_bin="${JAVA_HOME:-}/bin/java"
  if [[ ! -x "$java_bin" ]]; then
    java_bin="$(command -v java || true)"
  fi
  if [[ -z "$java_bin" || ! -x "$java_bin" ]]; then
    echo "Java 17+ is required. Set JAVA_HOME or put java on PATH." >&2
    exit 1
  fi

  local major
  major="$("$java_bin" -version 2>&1 | awk -F[\".] '/version/ {print $2; exit}')"
  if [[ -z "$major" || "$major" -lt 17 ]]; then
    echo "Java 17+ is required. Current java: $("$java_bin" -version 2>&1 | head -1)" >&2
    exit 1
  fi
  echo "$java_bin"
}

build_args() {
  local jar="unexamine-0.0.1.jar"
  if [[ ! -f "$jar" ]]; then
    echo "missing $jar in $DIR" >&2
    exit 1
  fi

  local xmx="${EXAMINE_JAVA_XMX:-512m}"
  local config_uri="optional:file:${CONFIG_DIR}/"
  JAVA_ARGS=(-Xmx"$xmx" -XX:+UseSerialGC -jar "$jar" --spring.config.additional-location="$config_uri")
}

start_cmd() {
  ensure_config
  if is_running; then
    echo "already running pid=$(cat "$PID_FILE")" >&2
    exit 1
  fi

  local java_bin
  java_bin="$(resolve_java)"
  build_args

  nohup "$java_bin" "${JAVA_ARGS[@]}" >>"$CONSOLE_LOG" 2>&1 &
  echo $! >"$PID_FILE"
  sleep 1
  if is_running; then
    echo "started pid=$(cat "$PID_FILE")"
    echo "console log: $CONSOLE_LOG"
    echo "app log: see logging.file.name in config/application.yml"
  else
    echo "start failed. tail -50 $CONSOLE_LOG" >&2
    exit 1
  fi
}

fg_cmd() {
  ensure_config
  local java_bin
  java_bin="$(resolve_java)"
  build_args
  echo "foreground mode (Ctrl+C to stop)"
  exec "$java_bin" "${JAVA_ARGS[@]}"
}

case "$MODE" in
  init-config) init_config_cmd ;;
  start) start_cmd ;;
  stop) stop_cmd ;;
  restart) stop_cmd; start_cmd ;;
  status) status_cmd ;;
  fg) fg_cmd ;;
  -h|--help|help) usage ;;
  *) usage >&2; exit 2 ;;
esac
