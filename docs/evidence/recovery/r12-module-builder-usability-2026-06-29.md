# R12 Module Builder Usability And Fresh-System Initialization Evidence

Date: 2026-06-29

## Scope

Task card: `REC-P0-015 Module Builder Usability And Initialization Closure`.

This evidence responds to the user acceptance failure that reported the module management page was crowded, publish controls were obscured, the filter action was missing, field types were not a fixed usable selection, and fresh systems lacked a default department baseline.

## Code Changes

- Replaced the module-management table surface with a module builder:
  - module filter bar with `应用筛选` and `重置`
  - left module list
  - workspace header with `发布检查`, `发布`, `回滚`
  - field builder with left field list, middle form preview, right properties
  - fixed field type choices: `TEXT`, `DATE`, `SELECT`, `ATTACHMENT`, `AUTO_NUMBER`
- Added frontend field readback API `listSystemModuleFields`.
- Created default department bootstrap for both system creation paths:
  - platform admin `POST /api/v1/platform/systems`
  - auth `POST /api/v1/auth/register-with-system`
- Bound the created owner/super-admin member to the default department.
- Added repeatable R12 script: `scripts/recovery-r12-module-builder-usability-smoke.ps1`.
- Added R12 cleanup patterns to `scripts/recovery-clean-test-systems.ps1`.

## Verification

Release package and deployed frontend:

- `scripts/package-release.ps1`: PASS
- `scripts/local-start-release.ps1`: PASS
- `scripts/verify-release.ps1 -BaseUrl http://127.0.0.1:18131 -CheckDeployedFrontend`: PASS
- Deployed assets matched release assets:
  - `/assets/index-BxmEllS0.css`
  - `/assets/index-CNZuLF4Y.js`
- Health: database/schema/Redis all `UP`
- Redis: `192.168.0.211:6379`

R12 business script:

- `scripts/recovery-r12-module-builder-usability-smoke.ps1 -BaseUrl http://127.0.0.1:18131`: PASS
- Result: `docs/evidence/recovery/r12-module-builder-usability-result.json`
- Platform-created system evidence:
  - default department id: `22`
  - owner member id: `348`
  - field readback includes `TEXT`, `DATE`, `SELECT`, `ATTACHMENT`, `AUTO_NUMBER`
  - publish version: `MODULE_v1782703411608`
  - cleanup: `DELETE`
- Register-created system evidence:
  - default department id: `23`
  - owner member id: `349`
  - initGuideSteps includes `CREATE_DEFAULT_DEPARTMENT`
  - cleanup: `DELETE`

Browser evidence on deployed release:

- Measurement JSON: `docs/evidence/recovery/screenshots/r12-module-builder/browser-measurement.json`
- Desktop screenshot: `docs/evidence/recovery/screenshots/r12-module-builder/desktop-module-builder.png`
- Mobile screenshot: `docs/evidence/recovery/screenshots/r12-module-builder/mobile-module-builder.png`

Desktop browser assertions:

- viewport: `1280x720`
- old module table: `false`
- filter buttons: `2`
- field type buttons: `5`
- publish action buttons: `3`
- field builder sections: `3`
- overflow: `[]`

Mobile browser assertions:

- viewport: `390x720`
- old module table: `false`
- filter buttons: `2`
- field type buttons: `5`
- publish action buttons: `3`
- field builder sections: `3`
- overflow: `[]`

Cleanup:

- `scripts/recovery-clean-test-systems.ps1 -BaseUrl http://127.0.0.1:18131`: DRY_RUN, `matchedCount=0`

Final release orchestration:

- `scripts/recovery-r5-final-user-script.ps1 -BaseUrl http://127.0.0.1:18131`: PASS
- Result: `docs/evidence/recovery/r5-final-release-result.json`
- Steps: `20` passed, `0` failed
- R12 step name: `r12-module-builder-usability`
- Release left running for user verification:
  - frontend: `http://127.0.0.1:18131/`
  - backend PID: `8636`
  - frontend PID: `20876`

## Result

R12 engineering acceptance: `accepted`.

Final product user signoff is still pending. `gates.user_script_passed` remains `false` until the user verifies the standalone release.
