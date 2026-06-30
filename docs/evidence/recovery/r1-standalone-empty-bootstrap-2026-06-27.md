# R1 Standalone Empty Bootstrap Evidence

Time: 2026-06-27 Asia/Shanghai

Scope: respond to the standalone deployment feedback that the running product still felt unusable after Redis `192.168.0.211:6379` became available.

## Change

- Platform workbench now renders an explicit empty state when the current account has no switchable system.
- Platform system creation dialog now pre-fills a generated system code, so a fresh deployment can create and enter a system without forcing the user to invent a technical code first.
- Creating a system now lands in the system backend initialization path instead of an empty dashboard.
- System information now shows a system initialization checklist with direct actions for organization, roles, modules, flow/dictionaries, and work/integration configuration.
- Direct non-hash URLs such as `/platform/admin` and `/systems/{systemId}/admin` are normalized to hash routes, so they do not fall back to the login page when a session exists.

Files:

- `frontend/src/app/app.ts`
- `frontend/src/features/platform/platformShell.ts`
- `frontend/src/features/system-admin/systemAdmin.ts`
- `frontend/src/features/system-shell/systemShell.ts`
- `frontend/src/styles.css`

## Verification

Release was rebuilt and restarted through the local standalone package:

- `scripts/package-release.ps1`: PASS
- `scripts/local-start-release.ps1`: PASS through `release/unexamine-0.0.1-SNAPSHOT/backend/start-result.json`
- `scripts/verify-release.ps1 -BaseUrl http://127.0.0.1:18131 -CheckDeployedFrontend`: PASS
- Release health: `status=UP`, `database=UP`, `schema=UP`, `redis=UP`
- Deployed frontend assets matched release assets:
  - `/assets/index-C8CAAU0i.css`
  - `/assets/index-fEtiEECt.js`

Browser smoke against `http://127.0.0.1:18131`:

1. Login with `admin / 123123aa`.
2. Platform workbench showed `暂无可进入系统` instead of a blank system switch area.
3. Clicked `创建系统`.
4. Created a temporary system with the generated system code.
5. Browser navigated to `/systems/118/admin`.
6. `/api/v1/context/current-system` returned a real `SystemSwitchContext`:
   - `systemId=118`
   - `tenantId=123`
   - `systemMemberId=143`
   - `effectiveRoleIds=[SYSTEM_SUPER_ADMIN]`
   - `dataScope.type=ALL`
7. Browser console errors: `0`.
8. Failed HTTP requests: `0`.
9. System backend rendered exactly one selected admin content panel plus one initialization checklist.
10. Direct navigation to `/systems/118/admin` normalized to `/#/systems/118/admin` and stayed on the system backend.
11. Direct navigation to `/platform/admin` normalized to `/#/platform/admin` and rendered platform backend, not login.
12. Browser console errors: `0`; failed requests: `0`; HTTP responses >= 400: `0`.
13. Temporary systems `111` and `118` created during current UI inspection were deleted after evidence capture.

Screenshots:

- `docs/evidence/recovery/screenshots/standalone-empty-system-switch-after-fix.png`
- `docs/evidence/recovery/screenshots/standalone-create-system-enter-dashboard.png`
- `docs/evidence/recovery/screenshots/current-inspect-after-fix/after-create-system-admin.png`
- `docs/evidence/recovery/screenshots/current-inspect-after-fix/direct-system-admin-path.png`
- `docs/evidence/recovery/screenshots/current-inspect-after-fix/direct-platform-admin-path.png`

## Result

Immediate standalone first-use regression is reduced: a fresh admin with no system context now sees a clear empty state, can create a system, receives system member context, and lands in the system backend initialization checklist instead of an empty shell.

This does not complete the project. R2 non-empty tenant data redraw, broader admin interaction coverage, and final user acceptance script remain open.
