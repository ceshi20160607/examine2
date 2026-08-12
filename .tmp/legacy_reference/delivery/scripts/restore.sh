#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
[[ "${2:-}" == '--confirm-restore' ]] || { echo 'Restore is destructive; pass BACKUP_ROOT --confirm-restore [ARTIFACT_ROOT]' >&2; exit 1; }
require_env
require_compose
backup_root="$(inside_data_root "$1")"
artifact_root="${3:-$PACKAGE_ROOT}"
artifact_root="$(cd "$artifact_root" && pwd -P)"
python_cmd="${PYTHON:-python}"
"$python_cmd" - "$backup_root" <<'PY'
import hashlib, json, pathlib, sys
root=pathlib.Path(sys.argv[1]); m=json.loads((root/'backup-manifest.json').read_text(encoding='utf-8'))
for key in ('database','files'):
 p=root/m[key]['path']
 if hashlib.sha256(p.read_bytes()).hexdigest()!=m[key]['sha256']: raise SystemExit(f'{key} checksum mismatch')
PY
EXAMINE_ARTIFACT_ROOT="$artifact_root" compose stop backend frontend
EXAMINE_ARTIFACT_ROOT="$artifact_root" compose up -d mysql redis
compose exec -T mysql mysql -uroot "-p$EXAMINE_DB_ROOT_PASSWORD" -e "DROP DATABASE IF EXISTS \`$EXAMINE_DB_NAME\`; CREATE DATABASE \`$EXAMINE_DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
compose exec -T mysql mysql -uroot "-p$EXAMINE_DB_ROOT_PASSWORD" "$EXAMINE_DB_NAME" <"$backup_root/database.sql"
mkdir -p "$EXAMINE_DATA_ROOT/files"
find "$EXAMINE_DATA_ROOT/files" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
"$python_cmd" - "$backup_root/files.zip" "$EXAMINE_DATA_ROOT/files" <<'PY'
import pathlib,sys,zipfile
archive=zipfile.ZipFile(sys.argv[1]); target=pathlib.Path(sys.argv[2]).resolve()
for item in archive.infolist():
 destination=(target/item.filename).resolve()
 if target not in destination.parents and destination != target: raise SystemExit('Unsafe archive path')
archive.extractall(target)
PY
EXAMINE_ARTIFACT_ROOT="$artifact_root" compose up -d --remove-orphans
wait_health 300
printf '{"status":"RESTORED","artifactRoot":"%s"}\n' "$artifact_root"
