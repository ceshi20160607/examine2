# G0 Task Acceptance Evidence

## Verdict

PASS.

G0 batch outputs for `TASK-DBA-001`..`TASK-DBA-009`, `TASK-BE-001`, `TASK-FE-001`, `TASK-QA-001`, `TASK-QA-005`, and `TASK-QA-010` are accepted for moving to the next dependency batch. No implementation files were modified by this acceptance worker.

## Scope Read

- `.cursor/README.md`
- `.cursor/session/state.json`
- `.cursor/knowledge/agent-operating-rules.md`
- `.cursor/knowledge/project-operating-rules.md`
- `.cursor/knowledge/failure-lessons.md`
- `docs/tasks/plan.md`
- G0 task files under `docs/tasks/`
- `docs/evidence/build-g0.md`

## Output Checks

All declared G0 outputs exist and are non-empty:

- `sql/fragments/001-platform-identity.sql` and `docs/database/platform-identity.md`
- `sql/fragments/002-permission-rbac.sql` and `docs/database/permission-rbac.md`
- `sql/fragments/003-module-config.sql` and `docs/database/module-config.md`
- `sql/fragments/004-dynamic-runtime.sql` and `docs/database/dynamic-runtime.md`
- `sql/fragments/005-workflow-approval.sql` and `docs/database/workflow-approval.md`
- `sql/fragments/006-message-todo-log.sql` and `docs/database/message-todo-log.md`
- `sql/fragments/007-work-management.sql` and `docs/database/work-management.md`
- `sql/fragments/008-secret-openapi-agent.sql` and `docs/database/secret-openapi-agent.md`
- `sql/fragments/009-async-ops.sql` and `docs/database/async-ops.md`
- `backend/pom.xml`, `backend/examine-core/**`, `backend/examine-web/**`
- `frontend/package.json`, `frontend/vite.config.ts`, `frontend/src/app/**`, `frontend/src/shared/**`
- `docs/testing/test-strategy.md`, `docs/testing/evidence-conventions.md`
- `docs/testing/fixtures.md`, `docs/testing/contract-fixtures.md`
- `docs/testing/static-acceptance-checks.md`, `docs/evidence/static-check-template.md`
- `docs/evidence/build-g0.md`

## SQL Fragment Checks

Command summary:

```powershell
Get-ChildItem sql/fragments/*.sql
# count CREATE TABLE statements and check odd single-quote counts per line
```

Result:

- Total tables: 87.
- Per fragment table counts:
  - `001-platform-identity.sql`: 9
  - `002-permission-rbac.sql`: 9
  - `003-module-config.sql`: 11
  - `004-dynamic-runtime.sql`: 9
  - `005-workflow-approval.sql`: 8
  - `006-message-todo-log.sql`: 7
  - `007-work-management.sql`: 11
  - `008-secret-openapi-agent.sql`: 11
  - `009-async-ops.sql`: 12
- Quote-pair check: PASS.

Spot checks against task acceptance criteria:

- Identity, RBAC, module config, message/log, SecretRef/OpenAPI/Agent fragments contain the expected table families, scope fields, indexes, trace/audit fields, and safety notes.
- Dynamic runtime stores record ownership under `system_id/tenant_id/module_id`, sequence state avoids max-plus-one allocation, and history stores `field_diff`, source, permission snapshot, trace, audit, and desensitization result.
- Workflow stores definitions, nodes, edges, snapshots, instances, approval tasks, action logs, simulation logs, idempotency keys, trace/audit fields, and action reason/result.
- Work management stores project and plain tasks through `un_work_task.task_type`, collaborator rows, related objects, kanban config, daily reports, report sources, calendar items, and explicit auto-source rules.
- Async/ops stores async tasks, events, files, idempotency keys, health checks, feature flags, quotas, rate limits, backup/restore, archive restore, deployment rollback, and API cache policy.

## Backend Verification

The configured `D:\Tools\...` paths from the project guide were not present on this machine. Actual available tools were:

- Java: `D:\java\jdk\jdk21`, version `21.0.10`
- Maven: `D:\java\apache-maven-3.8.5\bin\mvn.cmd`

Command:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

Result: PASS.

Reactor summary:

- `examine`: SUCCESS
- `examine-core`: SUCCESS
- `examine-web`: SUCCESS

Additional backend spot check:

- `ApiResponse` includes `requestId`, `traceId`, and optional `auditLogId`.
- Request context and global exception handling exist.
- Async task status enum includes `ROLLBACKING` and `ROLLED_BACK`.

## Frontend Verification

Actual available tool:

- npm: `D:\java\nodejs\npm.cmd`, version `11.9.0`

Command:

```powershell
$env:Path="D:\java\nodejs;$env:Path"
& 'D:\java\nodejs\npm.cmd' --prefix frontend run build
& 'D:\java\nodejs\npm.cmd' --prefix frontend audit --audit-level=moderate
```

Result: PASS.

- `tsc --noEmit && vite build`: PASS.
- Vite version: `8.0.16`.
- Built assets:
  - `dist/index.html`
  - `dist/assets/index-BFl90XBZ.css`
  - `dist/assets/index-zg5gsyiU.js`
- `npm audit --audit-level=moderate`: found 0 vulnerabilities.

Frontend spot checks found shared patterns for disabled reasons, trace display, async task result, pagination, and row-click acceptance rules.

## Static/Git Checks

Command:

```powershell
git diff --check
```

Result: PASS with line-ending warnings only:

- `.cursor/session/issues/registry.jsonl`
- `.cursor/session/state.json`
- `docs/design/pre-coding-readiness.md`
- `docs/design/user-approval.md`

These warnings were already present in the G0 self-check evidence and are outside this worker's write scope.

## Residual Risks

- Some DBA criteria are represented by JSON fields and documentation rather than SQL-level `CHECK` constraints. Examples: runtime before/after values in `field_diff`, workflow transfer/revoke through `action_code`, and async task status through `VARCHAR` plus documented/backend enum. This is acceptable for G0 scaffold acceptance but should stay visible in later implementation and migration review.
- G0 does not include `sql/init.sql`; merge validation remains owned by `TASK-DBA-010`.
- E2E is intentionally not run in G0; later QA tasks must validate the che/user scripts after backend and frontend functional slices exist.

