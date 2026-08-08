#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
[[ "${3:-}" == '--confirm-rollback' ]] || { echo 'Usage: rollback.sh PREVIOUS_PACKAGE_ROOT BACKUP_ROOT --confirm-rollback' >&2; exit 1; }
previous="$(cd "$1" && pwd -P)"
bash "$previous/scripts/verify-package.sh" >/dev/null
[[ -f "$previous/.env" ]] || cp -p "$ENV_FILE" "$previous/.env"
bash "$previous/scripts/restore.sh" "$2" --confirm-restore "$previous"
