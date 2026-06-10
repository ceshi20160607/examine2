#!/usr/bin/env sh

APP_NAME="${APP_NAME:-unexamine}"
JAR_NAME="${JAR_NAME:-unexamine.jar}"
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR_PATH="${JAR_PATH:-$SCRIPT_DIR/$JAR_NAME}"
PID_FILE="${PID_FILE:-$SCRIPT_DIR/$APP_NAME.pid}"
LOG_DIR="${LOG_DIR:-$SCRIPT_DIR/logs}"
LOG_FILE="${LOG_FILE:-$LOG_DIR/$APP_NAME.log}"
JAVA_BIN="${JAVA_BIN:-java}"
JAVA_OPTS="${JAVA_OPTS:-}"
APP_ARGS="${APP_ARGS:-}"
STOP_TIMEOUT="${STOP_TIMEOUT:-30}"

usage() {
    echo "Usage: $0 {start|stop|sotp|restart|status}"
    echo "Environment: JAVA_BIN, JAVA_OPTS, APP_ARGS, JAR_PATH, PID_FILE, LOG_DIR, LOG_FILE, STOP_TIMEOUT"
}

is_running() {
    pid="$1"
    [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null
}

read_pid() {
    if [ -f "$PID_FILE" ]; then
        sed -n '1p' "$PID_FILE"
    fi
}

start_app() {
    pid=$(read_pid)
    if is_running "$pid"; then
        echo "$APP_NAME is already running, pid=$pid"
        return 0
    fi

    if [ ! -f "$JAR_PATH" ]; then
        echo "Jar file not found: $JAR_PATH"
        return 1
    fi

    mkdir -p "$LOG_DIR"
    echo "Starting $APP_NAME ..."
    # shellcheck disable=SC2086
    nohup "$JAVA_BIN" $JAVA_OPTS -jar "$JAR_PATH" $APP_ARGS >> "$LOG_FILE" 2>&1 &
    pid="$!"
    echo "$pid" > "$PID_FILE"
    sleep 2

    if is_running "$pid"; then
        echo "$APP_NAME started, pid=$pid, log=$LOG_FILE"
        return 0
    fi

    echo "$APP_NAME failed to start, check log: $LOG_FILE"
    rm -f "$PID_FILE"
    return 1
}

stop_app() {
    pid=$(read_pid)
    if ! is_running "$pid"; then
        echo "$APP_NAME is not running"
        rm -f "$PID_FILE"
        return 0
    fi

    echo "Stopping $APP_NAME, pid=$pid ..."
    kill "$pid" 2>/dev/null || true

    elapsed=0
    while is_running "$pid" && [ "$elapsed" -lt "$STOP_TIMEOUT" ]; do
        sleep 1
        elapsed=$((elapsed + 1))
    done

    if is_running "$pid"; then
        echo "$APP_NAME did not stop within ${STOP_TIMEOUT}s, forcing kill"
        kill -9 "$pid" 2>/dev/null || true
    fi

    rm -f "$PID_FILE"
    echo "$APP_NAME stopped"
}

status_app() {
    pid=$(read_pid)
    if is_running "$pid"; then
        echo "$APP_NAME is running, pid=$pid"
    else
        echo "$APP_NAME is not running"
    fi
}

case "$1" in
    start)
        start_app
        ;;
    stop)
        stop_app
        ;;
    sotp)
        echo "Treating 'sotp' as 'stop'."
        stop_app
        ;;
    restart)
        stop_app
        start_app
        ;;
    status)
        status_app
        ;;
    *)
        usage
        exit 1
        ;;
esac
