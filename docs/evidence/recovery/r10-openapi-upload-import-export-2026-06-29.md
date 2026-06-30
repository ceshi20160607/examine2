# R10 OpenAPI Upload Import Export Evidence

Date: 2026-06-29

## Scope

`REC-P0-012 OpenAPI Upload Import Export Closure`

The acceptance target was not a prototype page or isolated endpoint. It had to prove one deployed business loop:

- OpenAPI app creation returns SecretRef metadata without plaintext secret exposure.
- Secret rotation creates persisted rotation/task evidence without returning plaintext.
- External OpenAPI record create/search/detail works through `/openapi/...`.
- OpenAPI call logs are readable and tied to the app.
- A real CSV upload returns a persisted file.
- Import precheck and confirm create successful async tasks and result files.
- Export creates a successful async task and result file.
- The standalone release same-origin frontend path proxies both `/api/...` and `/openapi/...`.

## Changes

- Added multipart support in `frontend/src/api/client.ts`.
- Added `uploadRuntimeImportFile` in `frontend/src/api/liveData.ts`.
- Rebuilt `frontend/src/features/runtime/import-export/importExportPanel.ts` as clean UTF-8 and changed import from manual fileId entry to file selection plus upload/precheck.
- Updated `frontend/src/features/runtime/records/runtimeRecords.ts` to upload selected import files before precheck.
- Added `scripts/recovery-r10-openapi-upload-import-export-smoke.ps1`.
- Updated local release frontend proxy to route `/openapi/` as well as `/api/`.
- Updated Nginx release template and release verification to require `/openapi/` proxy.
- Added R10 to `scripts/recovery-r5-final-user-script.ps1`.

## Standalone R10 Smoke

Command:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/recovery-r10-openapi-upload-import-export-smoke.ps1 -BaseUrl http://127.0.0.1:9999
```

Result: PASS

Key result:

```json
{
  "result": "PASS",
  "systemId": "223",
  "moduleId": "101",
  "openApiAppId": "23",
  "openApiRecordId": "82",
  "openApiSuccessfulLogCount": 5,
  "precheckId": "task_import_precheck_d088def6",
  "importTaskId": "task_import_confirm_251bd063",
  "exportTaskId": "task_runtime_record_export_08b3bffe",
  "runtimeTotal": 3,
  "cleanup": "DELETE"
}
```

## Final Release Orchestration

Command:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/recovery-r5-final-user-script.ps1 -BaseUrl http://127.0.0.1:18131
```

Result file: `docs/evidence/recovery/r5-final-release-result.json`

Result: PASS, `stepsPassed=18`, `stepsFailed=0`.

R10 step through deployed same-origin frontend:

```json
{
  "name": "r10-openapi-upload-import-export",
  "status": "PASS",
  "result": "PASS",
  "systemId": "237",
  "moduleId": "107",
  "openApiAppId": "25",
  "openApiRecordId": "89",
  "openApiSuccessfulLogCount": 5,
  "uploadedImportFileId": "file_fce1766d_1782697296969",
  "precheckId": "task_import_precheck_70684dc2",
  "importTaskId": "task_import_confirm_0964825c",
  "exportTaskId": "task_runtime_record_export_b1113e8e",
  "runtimeTotal": 3,
  "cleanup": "DELETE"
}
```

Release remains running for user verification:

- Frontend: `http://127.0.0.1:18131/`
- Backend: `http://127.0.0.1:9999`
- Backend PID: `12940`
- Frontend PID: `18860`

Release verification also proves:

- frontend config uses same-origin API
- Nginx proxies `/api` to backend `9999`
- Nginx proxies `/openapi` to backend `9999`
- Redis `192.168.0.211:6379` reachable
- health reports `status/database/schema/redis = UP`
- deployed frontend assets match release assets
