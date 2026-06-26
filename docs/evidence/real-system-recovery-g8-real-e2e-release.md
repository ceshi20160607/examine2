# REAL-G8 Real E2E And Release Evidence

> 2026-06-25 deployment correction: production frontend packages now keep `frontend/config.js` empty and use an Nginx same-origin `/api/` reverse proxy. The earlier temporary local cross-origin package mode has been removed from `scripts/package-release.ps1`.

## Scope

- Rebuilt the deployable frontend/backend separated package from real source code.
- Verified the release backend jar against the configured `examine2` database.
- Verified the release frontend reads runtime `frontend/config.js` before the built application script.
- Ran a real API E2E from account/system creation through module, field, scene, workflow, record, approval, todo and message.
- Ran a rendered frontend smoke with a real browser session against the release frontend and release backend.

## Fixes Made During G8

- `scripts/package-release.ps1`
  - Added `-JavaHome` so release builds use the configured JDK 21.
  - Added native command exit-code enforcement. Maven or npm failures now stop the release build instead of returning a false PASS.
  - Removed frontend API host injection. Release packages keep `frontend/config.js` empty and rely on Nginx same-origin `/api/` proxying.
- `frontend/index.html`
  - Moved `/config.js` into `<head>` before the built module script, so runtime API config is available before frontend boot.
- `frontend/public/config.js`
  - Added the deploy-time API base URL placeholder.
- `backend/examine-flow/.../WorkflowRuntimeMutationService.java`
  - Approval submission now creates a persisted system todo and system message in the same transaction as the workflow task.
- Deployment docs/templates
  - Documented same-origin Nginx deployment and the simplified backend layout.
  - Backend release layout remains `backend/examine-web.jar`, `backend/application.yml`, and `backend/server.sh` at the same level.
  - Added `nginx/unexamine.conf` as the production reverse-proxy reference.

## Build And Package

- Command:
  - `powershell -ExecutionPolicy Bypass -File .\scripts\package-release.ps1 -JavaHome 'D:\java\jdk\jdk21' -MavenPath 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -NpmPath 'D:\java\nodejs\npm.cmd'`
- Result: PASS.
- Backend Maven reactor: PASS.
- Frontend `tsc --noEmit && vite build`: PASS.
- Release directory: `release/unexamine-0.0.1-SNAPSHOT`.
- Release zip: `release/unexamine-0.0.1-SNAPSHOT.zip`.
- Release zip size at verification: `24886035` bytes.

## Release Runtime Verification

- Backend started from release jar:
  - `release/unexamine-0.0.1-SNAPSHOT/backend/examine-web.jar`
  - Java: `D:\java\jdk\jdk21\bin\java.exe`
  - Port: `18130`
  - DB: `jdbc:mysql://192.168.0.211:3306/examine2`
- Health result:
  - `status=UP`
  - `database=UP`
  - `schema=UP`
- Frontend preview started from release frontend directory:
  - Port: `18131`
  - `GET /config.js` returned `window.__UNEXAMINE_API_BASE_URL__ = '';`
- Release `index.html` ordering:
  - `/config.js` is loaded before `/assets/index-*.js`.
- Backend `server.sh`:
  - Exists next to `examine-web.jar` and `application.yml`.
  - `bash -n release/unexamine-0.0.1-SNAPSHOT/backend/server.sh` passed. WSL printed local network warnings, but shell syntax validation exited successfully.

## API E2E Result

Raw result: `docs/evidence/real-g8-api-e2e-result.json`.

```json
{
  "status": "PASS",
  "baseUrl": "http://127.0.0.1:18132",
  "proxyMode": "same-origin-nginx-like-proxy",
  "suffix": "0625110648",
  "accountId": "21",
  "systemId": "22",
  "tenantId": "22",
  "systemMemberId": "23",
  "moduleGroupId": "11",
  "moduleId": "11",
  "moduleCode": "contract_0625110648",
  "sceneId": "14",
  "flowId": "6",
  "recordId": "10",
  "searchTotal": 1,
  "approvalResult": "ACTION_ACCEPTED",
  "pendingTaskId": "5",
  "todoTotal": 1,
  "firstTodoId": "5",
  "messageTotal": 1,
  "firstMessageId": "7"
}
```

Covered API path:

- `POST /api/v1/auth/register-with-system`
- `POST /api/v1/platform/system-switch`
- `POST /api/v1/systems/{systemId}/module-groups`
- `POST /api/v1/systems/{systemId}/modules`
- `POST /api/v1/systems/{systemId}/modules/{moduleId}/fields`
- `POST /api/v1/systems/{systemId}/modules/{moduleId}/scenes`
- `POST /api/v1/systems/{systemId}/modules/{moduleId}/publish`
- `POST /api/v1/systems/{systemId}/flows`
- `POST /api/v1/systems/{systemId}/flows/{flowId}/publish`
- `POST /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records`
- `POST /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/search`
- `POST /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/actions/record.submitApproval`
- `GET /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}`
- `POST /api/v1/systems/{systemId}/todos/search`
- `POST /api/v1/systems/{systemId}/messages/search`

## Browser E2E Result

- In-app browser automation was attempted first, but its control layer timed out even on the backend health JSON page and reset the automation kernel. This was not counted as a frontend pass.
- Fallback rendered browser verification used `npx agent-browser@0.30.1` in isolated session `real-g8`.
- Browser entered `http://127.0.0.1:18131/#/systems/21/dashboard` after real login.
- Browser verification for production deployment must use same-origin `/api/...` through Nginx or an equivalent local reverse proxy; built frontend assets must not contain the backend host.
- Browser confirmed modules route:
  - `http://127.0.0.1:18131/#/systems/21/modules`
  - Real module `contract_0625095728` and record `LLC VIMPEL STROY 0625095728` rendered.
  - Row click opened the right detail panel.
  - Detail approval sidebar showed current approval flow and pending state.
- Browser confirmed todo route:
  - `http://127.0.0.1:18131/#/systems/21/todos`
  - Left todo type tree and right filtered/paged todo list rendered.
  - Approval todo count was `1`.
- Browser confirmed message route:
  - `http://127.0.0.1:18131/#/systems/21/messages`
  - Message stream rendered with system, tenant, template, type and time filters.
  - No duplicate small action buttons were shown on the message card.
  - Clicking the approval message navigated to `#/systems/21/todos` and called `messages/mark-read`.
- Browser page errors: none.
- Browser console errors: none.
- Screenshot: `docs/evidence/real-g8-browser-final.png`.

## Remaining Gate

- REAL-G8 self-check is complete.
- `gates.user_script_passed` remains `false` until the user runs or signs off the final local script.
