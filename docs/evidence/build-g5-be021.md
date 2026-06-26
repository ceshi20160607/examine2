# TASK-BE-021 G5 backend evidence

## Scope

- Worker: G5 backend worker
- Task: TASK-BE-021 dynamic runtime record APIs
- Ownership touched:
  - `backend/examine-module/src/main/java/com/unique/examine/module/manage/runtime/RuntimeRecordController.java`
  - `backend/examine-module/src/main/java/com/unique/examine/module/manage/runtime/RuntimeRecordService.java`
  - `backend/examine-module/src/main/java/com/unique/examine/module/manage/runtime/RuntimeRecordModels.java`
  - `docs/evidence/build-g5-be021.md`

## Endpoint list

- `GET /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/list-schema`
- `POST /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/search`
- `GET /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}`
- `POST /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records`
- `PATCH /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}`
- `DELETE /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}`
- `POST /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/actions/{actionCode}`
- `GET /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/history`

## Contract coverage

- Runtime list schema returns server-side page metadata, field permission modes, filter capability, sort capability, row detail target, data scope, toolbar actions, row actions, batch action limits, permission snapshot id/version, and action `disabledReason`.
- Search response returns `PageResult` and applies sample server-side keyword, field filter, sort, and page slicing.
- Row response includes field mask state, data scope, row detail target, per-action disabled reasons, and permission snapshot version.
- Detail response includes business-specific tabs (`assetProfile`, `maintenancePlan`, `costBudget`, `relations`, `operationLogs`), `approvalSidebar`, history hook, mask results, row detail target, and data scope.
- Create/update/delete/action responses include idempotency key, trace id, audit log id, permission snapshot, field diffs or disabled reason, and result hooks.
- History response is server-side paginated and includes field diff, source type, desensitize result, permission snapshot, trace id, and audit log id.

## Self-check

Command run:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -f backend/pom.xml -pl examine-module -am -DskipTests compile
```

Result:

- `examine-core`: SUCCESS
- `examine-module`: SUCCESS
- Maven summary: `BUILD SUCCESS`
- Finished at: `2026-06-23T23:35:12+08:00`

## Residual risks

- Implementation is contract-first sample logic only; it does not persist dynamic records into generated base tables yet.
- Field permission, data scope, idempotency, and audit ids are represented in response contracts, but not backed by the final permission engine or idempotency table.
- Import/export execution, attachment upload, workflow approval runtime, sequence allocation, and Agent write integration remain outside TASK-BE-021 and are intentionally not implemented here.
- The runtime list-schema hook is exposed under `records/list-schema` to satisfy TASK-BE-021 without modifying `manage/config`.

## Diff check

- Command: `git diff --check`
- Result: exit code 0.
- Output only reported LF/CRLF warnings on existing tracked files:
  - `.cursor/session/issues/registry.jsonl`
  - `docs/design/pre-coding-readiness.md`
  - `docs/design/user-approval.md`
- No whitespace error was reported for TASK-BE-021 files.
