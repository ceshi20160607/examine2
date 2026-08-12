#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
"$SCRIPT_DIR/stop.sh"
"$SCRIPT_DIR/start.sh" "${1:-240}"
