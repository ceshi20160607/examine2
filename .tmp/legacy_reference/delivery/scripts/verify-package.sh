#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"

for entry in backend/examine-web.jar frontend/index.html sql/migration deploy/compose.yaml deploy/nginx.conf .env.example VERSION.json manifest.json; do
  [[ -e "$PACKAGE_ROOT/$entry" ]] || { echo "Missing package entry: $entry" >&2; exit 1; }
done
python_cmd="${PYTHON:-python}"
"$python_cmd" - "$PACKAGE_ROOT" <<'PY'
import hashlib, json, pathlib, sys
root = pathlib.Path(sys.argv[1])
manifest = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
for entry in manifest['files']:
    path = root / entry['path']
    if not path.is_file(): raise SystemExit(f"Manifest entry missing: {entry['path']}")
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if digest != entry['sha256']: raise SystemExit(f"Manifest checksum mismatch: {entry['path']}")
version = json.loads((root / 'VERSION.json').read_text(encoding='utf-8'))
print(json.dumps({'status':'PASS','cycleId':version['cycleId'],'version':version['version']}))
PY
