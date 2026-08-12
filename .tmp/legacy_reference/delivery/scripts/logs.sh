#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_compose
tail_count="${1:-200}"
[[ "$tail_count" =~ ^[0-9]+$ ]] || { echo 'Tail must be numeric' >&2; exit 1; }
compose logs --tail "$tail_count" "${@:2}"
