# G7 / TASK-BE-036 Task Acceptance

Verdict: PASS

## Scope

- Accepted task: `TASK-BE-036`
- Accepted batch: `G7`
- Reviewed implementation scope:
  - `backend/examine-ai-work/src/main/java/com/unique/examine/aiwork/manage/work/**`
  - `backend/examine-ai-work/pom.xml`
  - `backend/examine-web/pom.xml`
- Report output only: `docs/evidence/task-accept-g7.md`

## Inputs Read

- `.cursor/README.md`
- `.cursor/session/state.json`
- `.cursor/knowledge/agent-operating-rules.md`
- `.cursor/knowledge/project-operating-rules.md`
- `.cursor/knowledge/failure-lessons.md`
- `docs/tasks/TASK-BE-036.md`
- `docs/api/api.md` section `10. 工作管理`
- `docs/evidence/build-g7.md`
- `docs/evidence/build-g7-be036.md`
- `docs/evidence/build-g7-smoke.json`

## Checks

### 1. G7 scope control

PASS.

- `TASK-BE-036` output is limited to `backend/examine-ai-work/src/main/java/com/unique/examine/aiwork/manage/work/**`.
- Actual work management implementation files are only:
  - `WorkManagementController.java`
  - `WorkManagementService.java`
  - `WorkManagementModels.java`
- Aggregation dependency change is present in `backend/examine-web/pom.xml`, which adds `examine-ai-work`.
- `backend/examine-ai-work/pom.xml` remains module-local and only declares expected core/web dependencies.
- I did not modify or revert unrelated dirty workspace files.

### 2. Required work management APIs

PASS.

`WorkManagementController.java` exposes the required API surface:

- Dashboard: `GET /api/v1/systems/{systemId}/work/dashboard`
- Project search: `POST /api/v1/systems/{systemId}/work/projects/search`
- Project task list/create/update: `/work/project-tasks/search`, `/work/project-tasks`, `/work/project-tasks/{taskId}`
- Plain task list/create/update: `/work/plain-tasks/search`, `/work/plain-tasks`, `/work/plain-tasks/{taskId}`
- Kanban query for both task types: `POST /api/v1/systems/{systemId}/work/kanban/query`
- Daily report search/create/auto draft: `/work/daily-reports/search`, `/work/daily-reports`, `/work/daily-reports/auto-draft`
- Work config get/update/publish-check: `/work/config`, `/work/config/publish-check`
- Comments/events: `/work/tasks/{taskId}/comments`, `/work/tasks/{taskId}/events`

This matches `docs/api/api.md` section `10. 工作管理` and covers the extra comments/events scope from `TASK-BE-036`.

### 3. Fixed four work tabs

PASS.

- API contract requires four fixed tabs: `dashboard`, `projectTask`, `plainTask`, `dailyReport`.
- `WorkManagementService.tabs(...)` returns exactly those four tab codes.
- `docs/evidence/build-g7-smoke.json` summary confirms:
  - `dashboard`
  - `projectTask`
  - `plainTask`
  - `dailyReport`

### 4. Project task and plain task lifecycle separation

PASS.

- Project task endpoints and plain task endpoints are separated in the controller.
- Service methods use explicit task types: `PROJECT` and `PLAIN`.
- Project task lifecycle is `projectTaskLifecycle` with `TODO/DOING/REVIEW/DONE/OVERDUE`.
- Plain task lifecycle is `plainTaskLifecycle` with `TODO/DOING/DONE/CANCELED`.
- Status dictionaries are also separated as `dict_project_task_status` and `dict_plain_task_status`.
- Smoke summary confirms `projectTaskLifecycle` and `plainTaskLifecycle` separately.

### 5. Kanban configured fields and dictionary semantics

PASS.

- `KanbanQueryRequest` carries `taskType/columnFieldId/swimlaneFieldId/groupFieldId/pageCursor`, matching the API contract.
- Project kanban defaults to `field_project_status`; plain kanban defaults to `field_plain_status`.
- Column and swimlane fields are `select`; group field is `multi_select`.
- Field refs are marked published and bind dictionary types.
- Dictionary items include `color`, `icon`, `semantic`, `sort`, `enabled`, and `defaultItem`.
- Smoke summary confirms:
  - project kanban task type `PROJECT`
  - plain kanban task type `PLAIN`
  - project column field `field_project_status`
  - plain column field `field_plain_status`
  - dictionary color/icon/semantic present

### 6. Daily report auto draft rule, manual confirmation, permission policy

PASS.

- `DailyReportAutoSourceRuleVO` models `sourceTypes/taskScope/todoScope/messageScope/logScope/approvalScope/permissionPolicy/manualConfirmRequired`.
- `autoDraftDailyReport(...)` returns sources for task, todo, and business log samples with `CURRENT_MEMBER_AUTHORIZED_ONLY`.
- Auto source rule includes `TASK/TODO/MESSAGE/BUSINESS_LOG/APPROVAL`.
- `manualConfirmRequired` is `true`.
- Auto draft returns a permission snapshot ID.
- Smoke summary confirms `dailyAutoManualConfirm=true` and `dailyAutoPermissionPolicy=CURRENT_MEMBER_AUTHORIZED_ONLY`.

### 7. Build and smoke evidence

PASS.

- `docs/evidence/build-g7.md` records backend reactor compile, web package, startup smoke, and `git diff --check` as passed.
- `docs/evidence/build-g7-be036.md` records:
  - `mvn -f backend/pom.xml -DskipTests compile`: PASS
  - `mvn -f backend/pom.xml -pl examine-web -am -DskipTests package`: PASS
  - startup smoke on port `18087`: PASS
  - `git diff --check`: PASS with known LF/CRLF warnings
- `docs/evidence/build-g7-server.out.log` shows `ExamineWebApplication` started from `backend/examine-web/target/examine-web-0.0.1-SNAPSHOT.jar` on port `18087`.
- `docs/evidence/build-g7-smoke.json` contains successful responses for dashboard, projects, project/plain task lists, both kanban modes, daily reports, auto draft, config, publish check, comments, and events.
- I reran `git diff --check`; it produced only LF/CRLF warnings on existing tracked files:
  - `.cursor/session/issues/registry.jsonl`
  - `.cursor/session/state.json`
  - `docs/design/pre-coding-readiness.md`
  - `docs/design/user-approval.md`

## Risks / Notes

- I did not rerun Maven compile/package during this acceptance pass to avoid generating additional build outputs beyond the requested report. The existing evidence is still sufficient because the packaged web jar startup log and smoke archive corroborate the compile/package result.
- Current implementation is contract-first/sample-data style and does not prove persistent CRUD behavior. This is not blocking for `TASK-BE-036` as written, but future persistence or integration tasks should validate repository/service wiring against the generated base services.
- The workspace already contains unrelated modified/untracked files. This acceptance report only evaluates the requested G7 implementation scope and does not attribute unrelated dirty files to this task.

