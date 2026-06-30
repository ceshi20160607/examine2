# R6 Data Source Management Evidence

Time: 2026-06-27 Asia/Shanghai

## Result

Status: PASS

R6 closes the previous system-admin data-source gap. The data-source entry is no longer an explicit pending placeholder. It is backed by a persisted table, manage APIs, deployed frontend UI, schema migration, and repeatable smoke evidence.

## Implementation

- Added table contract: `un_system_data_source`.
- Added backend base mapper/service/entity for `SystemDataSource`.
- Added business APIs:
  - `GET /api/v1/systems/{systemId}/data-sources`
  - `POST /api/v1/systems/{systemId}/data-sources`
  - `GET /api/v1/systems/{systemId}/data-sources/{dataSourceId}`
  - `PATCH /api/v1/systems/{systemId}/data-sources/{dataSourceId}`
  - `POST /api/v1/systems/{systemId}/data-sources/{dataSourceId}/connection-check`
  - `POST /api/v1/systems/{systemId}/data-sources/{dataSourceId}/publish-check`
- Bound `frontend/src/features/system-admin/systemAdmin.ts` data-source panel to real APIs.
- Added `scripts/recovery-r6-apply-schema.ps1`.
- Added `scripts/recovery-r6-data-source-smoke.ps1`.
- Updated `scripts/recovery-r5-final-user-script.ps1` to run R6 schema migration and R6 smoke.

## Verification

Compile/build:

- Backend compile passed: `mvn -pl examine-web -am -DskipTests compile`.
- Frontend production build passed: `npm run build`.

Schema:

- `scripts/recovery-r6-apply-schema.ps1` returned `{"status":"PASS","table":"un_system_data_source"}`.

Final release orchestration:

- `scripts/recovery-r5-final-user-script.ps1` returned PASS.
- Result file: `docs/evidence/recovery/r5-final-release-result.json`.
- Result summary:
  - `status=PASS`
  - `stepsPassed=14`
  - `stepsFailed=0`
  - `stoppedAfterRun=false`
  - release left running at `http://127.0.0.1:18131/`

R6 smoke:

- Final script step `r6-data-source` passed.
- Smoke evidence:
  - `systemId=153`
  - `tenantId=166`
  - `dataSourceId=1`
  - `sourceCode=r6_internal_0627215055`
  - `listBefore=0`
  - `listAfter=1`
  - `connectionCheckTraceId=trc_0824d98f-54a4-45ab-86c0-885b52d06f7c`
  - `publishCheckTraceId=trc_7c30d8f0-34a8-430e-b9f9-2b58784d37ad`
  - `targetVersion=ds_20260627215059`

Browser evidence on deployed frontend:

- Created kept visual evidence system:
  - `systemId=154`
  - `tenantId=167`
  - `dataSourceId=2`
  - `sourceCode=r6_internal_0627215506`
- Browser login to `http://127.0.0.1:18131/#/login` succeeded as `admin / 123123aa`.
- Browser opened `http://127.0.0.1:18131/#/systems/154/admin`.
- System admin data-source panel showed a real table row:
  - `R6 Internal Data Source 0627215506`
  - `r6_internal_0627215506`
  - `系统内数据源`
  - `启用`
  - `PASSED`
  - `DRAFT`
- Browser clicked the deployed frontend `发布检查` button.
- Page returned:
  - `发布检查通过`
  - `目标版本=ds_20260627215622`
  - `traceId=trc_afc295bf-6c6c-421d-ac78-97d3ef52d9a0`

## Remaining Gate

`gates.user_script_passed` remains `false` because the user has not personally verified or signed off.
