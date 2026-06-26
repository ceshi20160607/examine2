# Real System Recovery G4 Module Runtime Evidence

Time: 2026-06-24 19:00 Asia/Shanghai

## Scope

Replace the core configurable module and runtime record sample behavior with persisted module configuration and business record data.

## Code Changes

- `backend/examine-module/pom.xml`
  - Added dependency on `examine-plat` so module APIs can resolve account, tenant and system-member context through real platform bindings.
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/common/ModuleSystemContextResolver.java`
  - Added shared resolver for current account, system, tenant, system member, permission snapshot id and permission version.
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/config/ModuleConfigService.java`
  - Replaced fixed sample module groups, modules, fields, dictionaries, scenes, actions, import/export config, print template and publish result with real reads/writes against `un_module_*` tables.
  - Creating a module now persists a default module group when needed and creates default fields, status dictionary, scene, actions and import/export metadata.
  - Publish/check/rollback now updates persisted module state and writes `un_module_publish_version`.
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/runtime/RuntimeRecordService.java`
  - Replaced sample rows/details/history with real `un_module_dynamic_record`, `un_module_dynamic_value` and `un_module_dynamic_history` persistence.
  - Runtime search now applies persisted field schema, keyword, filters, sorting, pagination and current data-scope metadata.
  - Create/update/delete/action now persist record mutations and history.
- `frontend/src/features/system-admin/systemAdmin.ts`
  - Removed `sampleSystemContext`; return-to-business navigation now uses current shell system context.
- `frontend/src/features/system-shell/systemShell.ts`
  - Removed `sampleTask`; work trace line no longer depends on mock data.
- `frontend/src/mocks/g0.ts`
  - Deleted unused mock fixture to avoid future accidental reuse as real data.

## Verification

Backend compile:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -f backend\pom.xml -pl examine-web -am -DskipTests compile
```

Result:

```text
BUILD SUCCESS
```

Backend package:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -f backend\pom.xml -pl examine-web -am -DskipTests package
```

Result:

```text
BUILD SUCCESS
```

Runtime jar started on `127.0.0.1:18114` against the configured `examine2` database.

Health:

```json
{
  "status": "UP",
  "database": "UP",
  "schema": "UP"
}
```

Real API chain:

```json
{
  "accountId": "4",
  "systemId": "5",
  "switchSystemId": "5",
  "moduleId": "1",
  "fieldsTotal": 3,
  "firstField": "title",
  "runtimeColumns": 3,
  "recordId": "1",
  "createResult": "CREATED",
  "searchTotal": 1,
  "detailTabs": 2,
  "historyTotal": 1
}
```

Runtime schema action verification after repackaging:

```json
{
  "health": "UP",
  "schemaStatus": "UP",
  "rowActions": 2,
  "toolbarActions": 2,
  "batchActions": 1,
  "firstRowAction": "record.edit"
}
```

Frontend typecheck:

```powershell
$env:Path='D:\java\nodejs;' + $env:Path
npm.cmd run typecheck
```

Result:

```text
tsc --noEmit PASS
```

## Current Status

The module configuration and runtime record main chain is now persisted and verified against `examine2`.

This still is not final completion. Remaining real-system recovery must remove sample behavior from RBAC/org, flow definition/runtime, notification templates/delivery logs, SSO/no-member, OpenAPI, upload/import-export execution, AI Agent and the rest of frontend primary pages.
