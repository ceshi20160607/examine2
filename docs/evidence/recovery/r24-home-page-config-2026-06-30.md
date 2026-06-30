# R24 Home Page Config Closure Evidence

- Time: 2026-06-30 13:00 Asia/Shanghai
- Scope: FRC-1 first closed loop for system home page/page surface configuration.
- Status: PASS for the home page configuration loop. FRC-1 remains open for page designer, broad visual style, and advanced capabilities.

## What Was Proven

- System admin can save a system home page configuration through `/work/home-page-config`.
- Admin GET reads back the same persisted title, subtitle, visual tone, and widgets.
- Runtime GET `/work/home-page` reads the same persisted configuration.
- Normal system member can read the runtime home page configuration.
- Normal system member cannot write the admin home page configuration; write attempt returns HTTP 403.
- Publish-check returns `passed=true` with three check items.
- Deployed browser evidence shows the configured title in:
  - system admin home/page configuration panel,
  - desktop runtime dashboard,
  - mobile runtime dashboard.

## Verification

- Backend package: PASS with Temurin JDK 21.0.11.
  - `JAVA_HOME=D:\dev\jdk21-temurin`
  - `D:\dev\maven\bin\mvn.cmd -pl examine-web -am package -DskipTests`
- Frontend build: PASS.
  - `D:\dev\nodejs24\npm.cmd run build`
  - Deployed asset: `/assets/index-C1wA-Rqx.js`
- Release package: PASS.
  - `scripts/package-release.ps1`
- Release verification: PASS.
  - `scripts/verify-release.ps1 -BaseUrl http://127.0.0.1:18131 -CheckDeployedFrontend`
  - Health/database/schema/Redis all UP.
  - Redis: `192.168.0.211:6379`.
- R24 smoke: PASS.
  - `scripts/recovery-r24-home-page-config-smoke.ps1 -BaseUrl http://127.0.0.1:18131`

## Evidence Files

- Machine result: `docs/evidence/recovery/r24-home-page-config-result.json`
- Browser result: `docs/evidence/recovery/screenshots/r24-home-page-config/home-page-config-browser-audit.json`
- Screenshots:
  - `docs/evidence/recovery/screenshots/r24-home-page-config/desktop-admin-home-page-config.png`
  - `docs/evidence/recovery/screenshots/r24-home-page-config/desktop-runtime-dashboard-home-page.png`
  - `docs/evidence/recovery/screenshots/r24-home-page-config/mobile-runtime-dashboard-home-page.png`

## Remaining FRC-1 Gaps

- `REQ-6.1` overall visual style still needs a human/UI audit beyond containment and one home page loop.
- `REQ-6.8` page designer is not proven by this home page configuration evidence.
- `REQ-9` advanced/new capabilities still need split task cards and evidence.
- `REQ-5.8` pages and `REQ-6.3` home page move from broad `OPEN` to narrower `PARTIAL`, not final completion.
