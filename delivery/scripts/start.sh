#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_env
require_compose
bash "$PACKAGE_ROOT/scripts/verify-package.sh" >/dev/null
mkdir -p "$EXAMINE_DATA_ROOT/files" "$EXAMINE_DATA_ROOT/logs" "$EXAMINE_DATA_ROOT/backups"
compose up -d --remove-orphans
wait_health "${1:-240}"
