# REAL-G7 Frontend Primary API Integration Evidence

## Scope

- Frontend API client now supports same-origin `/api/...` calls, runtime `window.__UNEXAMINE_API_BASE_URL__`, stored access token, and `X-Account-Id`.
- Added `frontend/src/api/liveData.ts` as the real API adapter for auth, system module navigation, runtime records, system messages, system todos, and work management.
- Auth pages now call real backend APIs for login, register-with-system, password reset request, and password reset confirmation.
- Platform system entry now calls `/api/v1/platform/system-switch` before navigating into a system.
- Runtime module page now loads real module groups/modules, dynamic list schema, record search rows, and record details. A newly created system without published modules now shows a real empty state instead of falling back to fake vehicle data.
- System shell now loads real module groups in the top navigation and uses real APIs for todo workspace, message stream, and work management dashboard/task/report views.
- Backend now exposes configurable CORS for frontend-backend separated deployment via `unexamine.security.cors-allowed-origins`.

## Validation

- Backend package:
  - `mvn.cmd -pl examine-web -am -DskipTests package`
  - Result: PASS after stopping the previous temporary jar process that had locked the target jar.
- Frontend typecheck:
  - `npm.cmd run typecheck`
  - Result: PASS.
- Frontend production build:
  - `npm.cmd run build`
  - Result: PASS.
- Backend live health:
  - `GET http://127.0.0.1:18125/api/v1/health`
  - Result: `status=UP`, `database=UP`, `schema=UP`.
- CORS preflight:
  - `OPTIONS http://127.0.0.1:18125/api/v1/auth/login`
  - Origin: `http://127.0.0.1:18126`
  - Result: HTTP 200, `Access-Control-Allow-Origin: http://127.0.0.1:18126`, methods `GET,POST,PATCH,DELETE,OPTIONS`.
- Browser smoke on `http://127.0.0.1:18126`:
  - Register-with-system created a real account/system and navigated to `#/systems/18/dashboard`.
  - Login with the created account navigated to `#/systems/18/dashboard`.
  - `#/systems/18/modules` rendered a real empty state: `业务模块`, no fake vehicle rows, no interface error.
  - `#/systems/18/todos` rendered the todo workspace with no app errors.
  - `#/systems/18/messages` rendered the message stream with no app errors.
  - `#/systems/18/work` rendered work management with no app errors.
  - Browser app console errors: `[]`.

## Notes

- The smoke system was newly registered, so it has no published business modules yet. This is now represented as an empty state and should be covered in G8 by creating/publishing a module and inserting at least one runtime record before the final E2E.
- The browser automation runtime printed an external Statsig network timeout unrelated to this application; the tab's app console error list remained empty.
