# REAL-G5 OpenAPI / Upload / Import-Export Persistence Evidence

Time: 2026-06-24 23:12 +08:00

## Scope

Recovered the remaining REAL-G5 persistence gaps for OpenAPI, upload files, record attachments, import/export execution results, and async task center.

## Changes

- `backend/examine-core/src/main/java/com/unique/examine/core/manage/task/PersistedAsyncTaskService.java`
  - Added a shared persisted async task service backed by `un_sys_async_task`, `un_sys_async_task_file`, and `un_sys_async_task_event`.
  - Supports task upsert, event persistence, detail view, cancel, and retry.

- `backend/examine-message-log/src/main/java/com/unique/examine/messagelog/manage/task/AsyncTaskManageService.java`
  - Replaced fixed sample task responses with real async task search, detail, cancel, and retry operations.

- `backend/examine-app/src/main/java/com/unique/examine/app/manage/openapi/OpenApiService.java`
  - Replaced sample OpenAPI applications and sample call logs with persisted `un_openapi_app` and `un_openapi_call_log`.
  - Persists SecretRef metadata in `un_sys_secret_ref`.
  - Persists OpenAPI secret rotation jobs in `un_sys_secret_rotation_job` and creates a real async task.
  - Fixed `rollback_plan` persistence to store valid JSON while returning user-facing rollback text.

- `backend/examine-upload/src/main/java/com/unique/examine/upload/manage/UploadManageService.java`
  - Replaced in-memory upload file map with persisted `un_upload_file`, `un_upload_file_version`, `un_upload_file_access_log`, and `un_upload_storage_policy`.
  - Added `registerGeneratedFile` for backend-generated import/export files.

- `backend/examine-module/src/main/java/com/unique/examine/module/manage/attachment/AttachmentService.java`
  - Replaced sample attachment binding with persisted `un_module_dynamic_attachment` rows.
  - Reads uploaded file metadata from upload service and returns permission snapshot metadata from current system member context.

- `backend/examine-module/src/main/java/com/unique/examine/module/manage/importexport/ImportExportService.java`
  - Replaced fixed sample precheck issues and in-memory precheck map with persisted async tasks, upload file refs, and precheck event payloads.
  - Import precheck, import confirm, and export now all create queryable task records.

- POM updates:
  - `examine-app` now depends on `examine-plat` to resolve the system default tenant.
  - `examine-module` now depends on `examine-upload` for attachment and import/export file binding.
  - `examine-upload` now declares `spring-tx` for transactional upload registration.

## Verification

Commands:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -pl examine-web -am -DskipTests compile
mvn -pl examine-web -am -DskipTests package
```

Result:

- Compile: PASS
- Package: PASS

Runtime:

- Started `backend/examine-web/target/examine-web-0.0.1-SNAPSHOT.jar` on `127.0.0.1:18123`.
- Database: `jdbc:mysql://192.168.0.211:3306/examine2`
- Health: `status=UP`, `database=UP`, `schema=UP`

Smoke output:

```json
{
  "health": "UP",
  "accountId": "14",
  "systemId": "15",
  "moduleId": "7",
  "recordId": "6",
  "openapiAppId": "4",
  "openapiPersisted": true,
  "scopeCount": 2,
  "rateLimit": 200,
  "rotationTaskId": "task_openapi_secret_srj_openapi_4_f8cd5bdd",
  "callLogTotal": 4,
  "fileId": "file_45cae885_1782313756756",
  "uploadPersisted": true,
  "fileAccessAllowed": true,
  "attachmentCount": 1,
  "precheckPassed": true,
  "precheckTask": "task_import_precheck_a0d24487",
  "importTask": "task_import_confirm_91b413e9",
  "exportTask": "task_runtime_record_export_a973fc35",
  "taskDetailStatus": "QUEUED",
  "taskSearchTotal": 1
}
```

Port cleanup:

- `18123` released after smoke.

## Residual Notes

- Import precheck now validates real request/file existence and persists the task/event result. Actual Excel row parsing remains a deeper implementation task; this recovery removes the sample/in-memory execution gap and provides the durable task/file/event boundary for the parser.
- The service is still not project-complete. Remaining recovery work in session state is AI Agent persistence, broader frontend primary API binding, and final real E2E release verification.
