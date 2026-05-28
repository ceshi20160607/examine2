#!/usr/bin/env bash
# Usage: ./start.sh       -> background (default)
#        ./start.sh fg    -> foreground (logs to terminal)
#        ./start.sh status
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

MODE="${1:-bg}"
LOG_DIR="$DIR/logs"
PID_FILE="$LOG_DIR/examine.pid"
CONSOLE_LOG="$LOG_DIR/console.log"
mkdir -p "$LOG_DIR"

is_running() {
  [[ -f "$PID_FILE" ]] || return 1
  local pid
  pid="$(cat "$PID_FILE" 2>/dev/null)" || return 1
  [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null
}

status_cmd() {
  if is_running; then
    echo "running pid=$(cat "$PID_FILE") log=$CONSOLE_LOG"
  else
    echo "not running"
    [[ -f "$PID_FILE" ]] && rm -f "$PID_FILE"
    exit 1
  fi
}

if [[ "$MODE" == "status" ]]; then
  status_cmd
  exit 0
fi

CONFIG_DIR="$DIR/config"
CONFIG_FILE="$CONFIG_DIR/application.yml"
if [[ ! -f "$CONFIG_FILE" ]]; then
  if [[ -f "$CONFIG_DIR/application.yml.example" ]]; then
    echo "Copy config/application.yml.example to config/application.yml and edit DB/Redis settings." >&2
  else
    echo "Missing config file: $CONFIG_FILE" >&2
  fi
  exit 1
fi

if is_running; then
  echo "already running pid=$(cat "$PID_FILE"). Use ./stop.sh first." >&2
  exit 1
fi

if [[ -f application.env ]]; then
  set -a
  # shellcheck disable=SC1091
  source application.env
  set +a
fi

JAR="$(ls -1 examine-web-*.jar 2>/dev/null | head -1)"
if [[ -z "$JAR" ]]; then
  echo "missing examine-web-*.jar in $DIR" >&2
  exit 1
fi

JAVA_BIN="${JAVA_HOME:-}/bin/java"
if [[ ! -x "$JAVA_BIN" ]]; then
  JAVA_BIN="$(command -v java)"
fi
XMX="${EXAMINE_JAVA_XMX:-512m}"
CONFIG_URI="optional:file:${CONFIG_DIR}/"

JAVA_ARGS=(-Xmx"$XMX" -XX:+UseSerialGC -jar "$JAR" --spring.config.additional-location="$CONFIG_URI")

if [[ "$MODE" == "fg" ]]; then
  echo "foreground mode (Ctrl+C to stop)"
  exec "$JAVA_BIN" "${JAVA_ARGS[@]}"
fi

nohup "$JAVA_BIN" "${JAVA_ARGS[@]}" >>"$CONSOLE_LOG" 2>&1 &
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
