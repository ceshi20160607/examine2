# G6 Build Evidence

## Verdict

PASS self-check. `TASK-BE-022`, `TASK-BE-031`, and `TASK-QA-016` are implemented and verified enough for independent task-accept review.

## Implemented Tasks

- `TASK-BE-022`: import/export, draft, attachment, upload result, and sequence allocation APIs.
- `TASK-BE-031`: workflow runtime snapshot, approval actions, and platform/system todo APIs.
- `TASK-QA-016`: clean-build and smoke evidence for G6.

## Integration Fixes

- `backend/examine-web/pom.xml` now depends on `examine-upload`, so upload manage controllers are available from the web entry.
- `backend/examine-upload/pom.xml` declares Spring Web for its manage controllers.
- Flow runtime controller methods received method-level JavaDoc during conductor integration.

## Commands

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -pl examine-web -am -DskipTests package
```

Both commands passed.

## G6 Startup Smoke

Temporary server:

```powershell
java -jar backend/examine-web/target/examine-web-0.0.1-SNAPSHOT.jar --server.port=18086
```

Result summary is saved at `docs/evidence/build-g6-smoke.json`.

Key checks:

```json
{
  "health": "SUCCESS",
  "draftSave": "SUCCESS",
  "draftGet": "SUCCESS",
  "importPrecheck": "SUCCESS",
  "importPrecheckHasResultFile": true,
  "importPrecheckHasErrorFile": true,
  "importConfirm": "SUCCESS",
  "importConfirmTaskStatus": "RUNNING",
  "export": "SUCCESS",
  "exportHasResultFile": true,
  "attachment": "SUCCESS",
  "uploadResult": "SUCCESS",
  "uploadObjectStorageConnected": false,
  "sequenceAtomic": true,
  "flowInstance": "SUCCESS",
  "flowCurrentTaskCount": 1,
  "approvalIdempotent": true,
  "reject": "SUCCESS",
  "transfer": "SUCCESS",
  "platformTodos": "SUCCESS",
  "platformTodoScope": "platform",
  "systemTodos": "SUCCESS",
  "systemTodoScope": "system",
  "todoAction": "SUCCESS"
}
```

Server logs:

- `docs/evidence/build-g6-server.out.log`
- `docs/evidence/build-g6-server.err.log`

## Acceptance Coverage

- Import/export async tasks return task status plus result and error file references.
- Attachment handling returns bound attachment views and upload access results.
- Upload APIs stay in `LOCAL_SAMPLE` mode and do not connect production object storage.
- Sequence allocation uses in-memory `AtomicLong.getAndAdd(count)` sample semantics; smoke confirmed the second allocation starts after the first allocation's last value.
- Workflow runtime snapshot returns current tasks, node status, business target, permission snapshot, trace, and audit data.
- Approval actions are idempotent: smoke submitted the same approve request twice and confirmed the duplicate returned `workflowAdvanced=false` and `duplicate=true`.
- Reject and transfer actions return reason/target details.
- Platform todo and system todo APIs are separate, each returning type tree plus paged list.

## Supporting Evidence

- `docs/evidence/build-g6-be022.md`
- `docs/evidence/build-g6-be031.md`
- `docs/evidence/build-g6-smoke.json`

## Residual Risks

- G6 remains contract-first and sample-data driven. Durable async task persistence, file storage, sequence row locking, workflow engine execution, and real todo persistence remain later integration work.
- The in-memory idempotency and sequence samples demonstrate API semantics only; production implementation must move these guarantees to database-backed idempotency and sequence allocation.
- `git diff --check` should still be run after this evidence file; known LF/CRLF warnings may appear on previously modified tracked files.
