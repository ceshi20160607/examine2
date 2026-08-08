#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_env
require_compose
wait_health "${1:-15}"
compose ps
