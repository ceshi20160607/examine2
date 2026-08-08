#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_env
require_compose
label="${1:-manual}"
[[ "$label" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]] || { echo 'Invalid backup label' >&2; exit 1; }
backup_root="$EXAMINE_DATA_ROOT/backups/$(date +%Y%m%d-%H%M%S)-$label"
mkdir -p "$backup_root"
compose exec -T mysql sh -c 'exec mysqldump --single-transaction --routines --triggers --events -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' >"$backup_root/database.sql"
python_cmd="${PYTHON:-python}"
"$python_cmd" - "$PACKAGE_ROOT" "$backup_root" "$label" <<'PY'
import datetime, hashlib, json, pathlib, sys, zipfile
root, backup, label = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]), sys.argv[3]
with zipfile.ZipFile(backup/'files.zip','w',compression=zipfile.ZIP_DEFLATED) as archive:
  files=pathlib.Path(__import__('os').environ['EXAMINE_DATA_ROOT'])/'files'
  if files.is_dir():
    for path in sorted(p for p in files.rglob('*') if p.is_file()):
      archive.write(path,path.relative_to(files))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
manifest = {
  'schemaVersion': 1, 'createdAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
  'label': label, 'sourceVersion': json.loads((root/'VERSION.json').read_text(encoding='utf-8')),
  'database': {'path':'database.sql','sha256':sha(backup/'database.sql')},
  'files': {'path':'files.zip','sha256':sha(backup/'files.zip')}, 'secretsIncluded': False
}
(backup/'backup-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'status':'BACKED_UP','backupRoot':str(backup),'secretsIncluded':False}))
PY
