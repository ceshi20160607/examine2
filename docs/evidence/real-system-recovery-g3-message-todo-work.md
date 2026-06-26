# Real System Recovery G3 Message Todo Work Evidence

Time: 2026-06-24 18:40 Asia/Shanghai

## Scope

Continue the real-system recovery after the completion-claim retraction. This batch moves message, todo and work-management runtime APIs from sample responses to persisted data.

## Code Changes

- `backend/examine-core/src/main/java/com/unique/examine/core/context/CurrentRequestHeaders.java`
  - Added lightweight `X-Account-Id` lookup for feature modules that should not own token/session middleware.
- `backend/examine-message-log/src/main/java/com/unique/examine/messagelog/manage/message/MessageService.java`
  - Reads and mutates `un_message_message`.
  - Supports current receiver, platform/system scope, system, tenant, template, type, read/archive, keyword, pagination, mark read, archive and mark-all-read.
  - Parses persisted `target_payload` into message jump metadata.
- `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/todo/TodoService.java`
  - Reads and mutates `un_message_todo`.
  - Supports todo type tree counts, status, priority, keyword, current assignee, and row-level action handling.
- `backend/examine-flow/pom.xml`
  - Added dependency on `examine-message-log` for todo persistence.
- `backend/examine-ai-work/src/main/java/com/unique/examine/aiwork/manage/work/WorkManagementService.java`
  - Reads and mutates persisted work tables: projects, tasks, daily reports, comments and events.
  - Resolves current system member context from platform account-member bindings.
  - Supports project creation/search, project tasks, plain tasks, list/kanban, daily reports, auto draft, comments, events and dashboard counts.
- `backend/examine-ai-work/src/main/java/com/unique/examine/aiwork/manage/work/WorkManagementController.java`
  - Added project create endpoint.
- `backend/examine-ai-work/src/main/java/com/unique/examine/aiwork/manage/work/WorkManagementModels.java`
  - Added project create request.
- `backend/examine-ai-work/pom.xml`
  - Added dependency on `examine-plat` for member-context resolution.
- `frontend/src/api/client.ts`
  - Adds `X-Account-Id` from local storage to API requests.
- `frontend/src/app/state.ts`
  - Bootstraps account profile, system-switch options and current `SystemSwitchContext` from real APIs.
- `frontend/src/app/app.ts`
  - Initializes shell state before first render.
- `frontend/src/features/system-shell/systemShell.ts`
  - Removed hard-coded system context assignment.

## Verification

Backend compile and package passed with JDK 21 and Maven from `docs/user_setting.md`.

Runtime was started against:

```text
jdbc:mysql://192.168.0.211:3306/examine2
username=examine
password=examine
```

Smoke result:

```json
{
  "health": "UP",
  "schema": "UP",
  "systemMessagesTotal": 1,
  "platformMessagesTotal": 1,
  "todoTotal": 1,
  "markReadAffected": 1,
  "todoActionStatus": "HANDLED",
  "projectSearchTotal": 1,
  "projectTaskSearchTotal": 1,
  "plainTaskSearchTotal": 1,
  "kanbanColumns": 5,
  "reportSearchTotal": 1,
  "commentsCount": 1,
  "eventsCount": 2
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

## Remaining Risk

This is not final completion. Notification template, delivery logs, workflow definition/runtime, OpenAPI, SSO, upload and AI Agent services still contain sample behavior and must be recovered in later batches.
