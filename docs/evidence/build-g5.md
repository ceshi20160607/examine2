# G5 Build Evidence

## Verdict

PASS self-check. G5 backend slices and FE-020 admin configuration views are complete enough for independent task-accept review.

## Implemented Tasks

- `TASK-BE-021`: dynamic runtime record APIs.
- `TASK-BE-030`: flow definition and workflow configuration APIs.
- `TASK-BE-035`: OpenAPI external app and call log APIs.
- `TASK-FE-020`: platform admin and system admin configuration views.

## Aggregation Fixes

- `backend/examine-web/pom.xml` now depends on `examine-flow` and `examine-app` so G5 controllers are available from the web entry.
- `examine-flow` and `examine-app` declare Spring Web where their controllers live.
- Added readable class-level JavaDoc near the OpenAPI controller/model declarations after detecting unreadable generated comments.
- After independent `task-accept-g5` returned FAIL, FE-020 was corrected:
  - unauthorized platform/system admin entries are hidden instead of rendered disabled;
  - direct admin routes are guarded by a no-permission page;
  - platform system lifecycle actions now include reason input, impact scope, `AsyncTask` result, retry/rollback status, `traceId`, and `auditLogId`.

## Commands

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -f backend/pom.xml -DskipTests compile
mvn -f backend/pom.xml -pl examine-web -am -DskipTests package
& 'D:\java\nodejs\npm.cmd' --prefix frontend run build
```

All commands passed.

Re-run after FE-020 acceptance fixes on 2026-06-24:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -pl examine-web -am -DskipTests package
& 'D:\java\nodejs\npm.cmd' --prefix frontend run build
```

All commands passed.

## G5 Startup Smoke

Temporary server:

```powershell
java -jar backend/examine-web/target/examine-web-0.0.1-SNAPSHOT.jar --server.port=18084
```

Result:

```json
{
  "health": "SUCCESS",
  "runtimeSearch": "SUCCESS",
  "runtimeRecordCount": 2,
  "runtimeDetail": "SUCCESS",
  "approvalSidebar": true,
  "runtimeAction": "SUCCESS",
  "flows": "SUCCESS",
  "canvas": "SUCCESS",
  "canvasNodeCount": 7,
  "simulation": "SUCCESS",
  "publishCheck": "SUCCESS",
  "openapiApps": "SUCCESS",
  "openapiCreate": "SUCCESS",
  "openapiSecretHasPlaintext": false,
  "callLogs": "SUCCESS"
}
```

Server logs:

- `docs/evidence/build-g5-server.out.log`
- `docs/evidence/build-g5-server.err.log`

## Supporting Evidence

- `docs/evidence/build-g5-be021.md`
- `docs/evidence/build-g5-be030.md`
- `docs/evidence/build-g5-be035.md`
- `docs/evidence/build-g5-fe020.md`

## Residual Risks

- G5 remains contract-first and sample-data driven. Runtime persistence, import/export execution, attachments, workflow runtime approval tasks, and full frontend data wiring remain in later batches.
- OpenAPI and runtime record writes expose idempotency contracts but do not yet persist idempotency records.
- `git diff --check` should still be run after this evidence file; known LF/CRLF warnings may appear on previously modified tracked files.
