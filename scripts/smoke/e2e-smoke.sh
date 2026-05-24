#!/usr/bin/env bash
# Forward to tests/api (kept for backward compatibility)
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
exec bash "$ROOT/tests/api/e2e-smoke.sh" "$@"
