# Leader Reality Audit

Time: 2026-06-24 15:10 Asia/Shanghai

## Verdict

前面把“G0-G10 当前任务计划内通过”表达成“项目原型之后的完整系统已经 coding 完成”，这是错误口径。

当前产物不是一个完整可交付的前后端分离业务系统。它更准确的状态是：

- API contract and prototype-aligned scaffold: partly done.
- Backend contract-first sample services: partly done.
- Frontend prototype/static shell: partly done.
- Deployable package shape: fixed to a simple jar-level package.
- Real database-backed, permission-enforced, frontend-to-backend integrated system: not done.

因此，“coding 完成”这句话撤回。后续必须按真实系统开发重新规划。

## Evidence

Repository evidence:

- Current backend Java files: 492.
- Current frontend source files: 21.
- Old backup files: 1546.
- Current Open Design prototype: `docs/design/prototypes/index.html`, size `379515` bytes.
- Current sample/mock/contract-first references found in backend/frontend: `163`.

Important files:

- `frontend/src/api/types.ts` says: `Contract artifact only: no request client, no page implementation, no runtime logic.`
- `frontend/src/app/state.ts` imports `sampleSystemContext` and `sampleTenantContext`.
- `frontend/src/mocks/g0.ts` contains sample system context, rows, actions, and tasks.
- `frontend/src/features/system-shell/systemShell.ts` imports and renders `sampleSystemContext` and hard-coded message/work data.
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/config/ModuleConfigService.java` is explicitly a contract-first service and returns sample modules, fields, scenes, columns, filters, actions, import/export, and print templates.
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/runtime/RuntimeRecordService.java` is explicitly a contract-first runtime dynamic record service and returns sample rows.
- `backend/examine-app/src/main/java/com/unique/examine/app/manage/openapi/OpenApiService.java`, `backend/examine-ai-work/...`, `backend/examine-message-log/...` all contain sample data methods.

Old backup evidence:

- `.oldbk/backend/pom.xml` already included MyBatis-Plus versions and backend persistence dependencies.
- `.oldbk/backend/examine-web/src/main/resources/application.yml` existed.
- `.oldbk/frontend/src` had `api`, `components`, `layouts`, `pages`, `router`, `stores`, `App.ts`, and `main.ts`.
- `.oldbk/sql/init.sql` contained extensive schema and seed/config data.

Conclusion from evidence:

- Current code did not yet become the real system implied by the prototype and old backup.
- It ignored too much of the old implementation depth and stopped at scaffold/sample behavior.
- The prior validation checked build, package, smoke, and route existence, but did not validate real persistence, real login, real permissions, real frontend API integration, or real business workflows.

## What A Real System Must Have

Backend must include:

- Real datasource configuration and migration/init flow from `sql/init.sql`.
- MyBatis-Plus mapper scanning and actual CRUD through generated base services.
- Transactional manage services that use database data, not `sample*` methods.
- Real account login, password hashing, token/session lifecycle, SSO callback handling, MFA hooks, and password reset persistence.
- Server-side system/member/tenant context binding.
- Server-side RBAC, menu/button/action/field/data-scope enforcement.
- Dynamic module runtime backed by module/field/scene/action/dict/publish tables.
- Dynamic record persistence and query/filter/sort/pagination.
- Flow runtime persistence: definitions, published versions, instances, tasks, actions, timeline.
- Message/todo/log persistence and read/archive/filter state.
- Import/export/upload with actual file storage and async task lifecycle.
- AI Agent configuration persistence, policy enforcement, audit, and confirmation workflow.
- OpenAPI app secret lifecycle, request signing/rate-limit/idempotency/audit.
- Tests against an initialized database, not only sample HTTP smoke.

Frontend must include:

- Real route structure and auth guard, not only hash switching.
- API client integration for every major page.
- Token/session storage and refresh behavior.
- Platform/system/tenant switch using backend context APIs.
- Real data tables/forms/drawers bound to API responses.
- Permission-driven menu/button/field rendering from backend data.
- Loading/empty/error/no-permission/disabled states from API response.
- Real create/update/delete/import/export/approval/message/task interactions.
- Frontend E2E against backend and seeded database.

Deployable system must include:

- Simple backend package: `examine-web.jar`, `application.yml`, `server.sh` in one directory.
- Frontend static package and deployment notes.
- Database init/migration instructions.
- Environment variable and external config list.
- Start/status/restart/stop validation.

## Immediate Correction Already Done

The backend release package was simplified after user feedback:

```text
release/unexamine-0.0.1-SNAPSHOT/backend/
  examine-web.jar
  application.yml
  server.sh
  logs/
  data/uploads/
```

Command:

```bash
./server.sh start
./server.sh status
./server.sh restart
./server.sh stop
```

Evidence:

- `docs/evidence/deployable-package-flat-script-build.log`
- `release/unexamine-0.0.1-SNAPSHOT.zip`

## Required Replan

The current task plan must be reopened. The new plan should not call the project complete until the following phases pass:

1. Baseline recovery:
   Compare `.oldbk`, current prototype, current API contract, and current code. Decide what to port, what to regenerate, and what to discard.

2. Backend real persistence:
   Wire datasource, MyBatis-Plus, mapper scanning, base services, migrations, tenant plugin, transactions, and first real CRUD slice.

3. Auth and permission:
   Implement account login, session/token, system/member/tenant context, RBAC, field/action/data-scope enforcement.

4. Module runtime:
   Implement module group/module/field/dict/scene/action/publish configuration and dynamic record runtime with real DB storage.

5. Flow/work/message/log:
   Implement persisted workflow, todo, message, notification template, logs, work management, and AI Agent policies.

6. Frontend integration:
   Replace static shell/sample data with real API-bound pages using the old frontend structure as reference.

7. Real E2E:
   Run backend + DB + frontend with seeded data and validate ordinary member, system admin, platform member, and platform admin flows.

8. Release:
   Package backend/frontend/database scripts with simple external config and operational scripts.

## Leader Commitment

Leader is the current Codex conductor for this thread. I should not delegate blame to agents or role labels. The previous completion claim was mine and was wrong. Further work must be managed as a real system build, not a prototype/demo acceptance exercise.
