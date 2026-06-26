# Final Local Run And Package Evidence

Time: 2026-06-24 14:20 Asia/Shanghai

## Scope

本轮按真实本地服务验证当前构建结果，不只停留在编译：

- Backend jar package.
- Frontend production build.
- Backend Spring Boot jar startup.
- Frontend Vite preview startup.
- Backend live HTTP smoke.
- Frontend browser route and DOM smoke.

## Package Results

Backend package:

- Command: `mvn -f backend/pom.xml -pl examine-web -am -DskipTests package`
- Result: PASS
- Log: `docs/evidence/final-local-backend-package.log`
- Artifact: `backend/examine-web/target/examine-web-0.0.1-SNAPSHOT.jar`
- Artifact size: `21192962` bytes

Frontend package:

- Command: `npm --prefix frontend run build`
- Result: PASS
- Log: `docs/evidence/final-local-frontend-build.log`
- Artifacts:
  - `frontend/dist/index.html`
  - `frontend/dist/assets/index-Bu-Clafy.css`
  - `frontend/dist/assets/index-CsZo1RTx.js`

## Local Services

Backend:

- URL: `http://127.0.0.1:18100`
- Health: `GET /api/v1/health`
- Result: `SUCCESS / UP`

Frontend:

- URL: `http://127.0.0.1:18101/index.html`
- Result: HTTP 200

## Smoke Results

Backend live smoke:

- Evidence: `docs/evidence/final-local-live-smoke.json`
- Result: PASS
- Total checks: 126
- Failed checks: 0
- Coverage includes auth, account, platform, tenant, org, member, role, module config, runtime records, import/export, upload, flow, approval, todo, messages, logs, SSO, OpenAPI, ops, work, and AI Agent APIs.

Frontend browser smoke:

- Evidence: `docs/evidence/final-local-frontend-smoke.json`
- Result: PASS
- Route checks: 11
- Failed routes: 0
- Console errors: 0
- Routes checked:
  - `#/login`
  - `#/register-with-system`
  - `#/forgot-password`
  - `#/platform`
  - `#/platform/admin`
  - `#/systems/sys_vehicle/dashboard`
  - `#/systems/sys_vehicle/modules`
  - `#/systems/sys_vehicle/todos`
  - `#/systems/sys_vehicle/messages`
  - `#/systems/sys_vehicle/work`
  - `#/systems/sys_vehicle/admin`

Focused browser checks:

- Auth pages have no vertical scroll in the current local viewport.
- System message route renders stream cards, not the runtime module list.
- Message cards carry direct jump targets and no extra row action buttons.
- Work page contains dashboard, project tasks, normal tasks, and daily reports.
- Work page contains configured kanban columns.
- Runtime data rows still open detail by row click and have no duplicate detail buttons.

## Fixes Found During Real Run

真实启动前端后发现并修复：

- `#/systems/sys_vehicle/messages` fell through to the runtime module list.
- `#/systems/sys_vehicle/work` was too shallow and did not expose enough work-module structure.
- Registration/auth shell could create unnecessary vertical scroll in the validation viewport.

Touched files:

- `frontend/src/features/system-shell/systemShell.ts`
- `frontend/src/styles.css`

## Current Boundary

当前计划内 G0-G10 coding batches have been accepted and this final local package/smoke pass is complete. `gates.user_script_passed` remains `false` until the user's own final script or manual acceptance is recorded.

## Cleanup

After verification, local validation services were stopped and ports `18100` / `18101` were confirmed clear.
