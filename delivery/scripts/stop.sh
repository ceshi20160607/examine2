#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_compose
compose stop --timeout "${1:-60}"
printf '{"status":"STOPPED","dataPreserved":true}\n'
