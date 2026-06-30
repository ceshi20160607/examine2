# R1 Browser Shell Smoke Evidence

Time: 2026-06-27 Asia/Shanghai

## Scope

This evidence covers browser-level smoke for the four primary shells:

- platform workbench
- platform admin
- system runtime dashboard
- system admin

The in-app browser control plugin repeatedly timed out while reading the local app after one successful login-page check, so this smoke used a temporary `playwright-core` install outside the repository and the locally installed Chrome executable. The project dependencies were not changed.

## Runtime

- Backend: release package on `http://127.0.0.1:9999`
- Frontend: Vite dev server on `http://127.0.0.1:5173`
- Redis: temporary local Docker container `unexamine-recovery-redis` on `127.0.0.1:6379`
- Login: `admin / 123123aa`

## Result

Status: `PASS`

- Browser login succeeded.
- Platform workbench rendered with headings:
  - `平台工作台`
  - `创建系统`
  - `系统切换`
  - `平台代办`
  - `平台消息`
- Platform admin rendered with headings:
  - `平台后台`
  - `平台信息`
  - `平台组织架构`
  - `平台仪表盘`
  - `系统生命周期`
  - `平台角色与权限`
  - `配置管理`
  - `日志管理`
- System dashboard rendered with heading:
  - `系统仪表盘`
- System admin rendered with headings:
  - `系统后台`
  - `系统信息`
  - `仪表盘管理`
  - `组织架构与角色`
  - `模块管理`
  - `流程与字典`
  - `数据源与对外应用`
  - `工作配置与 AI Agent`
  - `统一认证与日志`
- No page remained on `Loading...`.
- Console error count: `0`.
- Failed HTTP response count: `0`.

## Screenshots

- `docs/evidence/recovery/screenshots/r1-platform-workbench.png`
- `docs/evidence/recovery/screenshots/r1-platform-admin.png`
- `docs/evidence/recovery/screenshots/r1-system-dashboard.png`
- `docs/evidence/recovery/screenshots/r1-system-admin.png`

## Notes

- Added `frontend/public/favicon.svg` and referenced it from `frontend/index.html` to remove the browser's automatic missing favicon request.
- Some system names created by earlier PowerShell smoke commands contain mojibake because the test data was sent through a non-UTF-8 shell path. This affects test data labels, not the browser shell rendering itself.
- This is still R1 smoke evidence, not final product acceptance. Deeper P0 flows such as module publish, runtime record CRUD, approval, todo/message closure, import/export, SSO, OpenAPI/upload, and AI Agent remain covered by later recovery batches.
