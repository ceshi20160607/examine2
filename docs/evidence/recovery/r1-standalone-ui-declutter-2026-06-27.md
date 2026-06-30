# R1 Standalone UI Declutter Verification - 2026-06-27

## Scope

User reported that the standalone deployment still looked crowded and confused after Redis `192.168.0.211:6379` became available.

This evidence covers the immediate visible regressions found in the deployed release:

- platform workbench still stacked create-system, system-switch, platform todos, and platform messages on one page;
- runtime module empty state still rendered toolbar, filters, batch actions, and right-side detail when no published module existed;
- previous recovery scripts left `Recovery...` systems in the shared database and polluted the platform workbench;
- release scripts still defaulted to stale local tool paths on this machine.

This does not close final project acceptance. It narrows R1/R2 visible clutter and keeps R5 final user script open.

## Changes

- `frontend/src/features/platform/platformShell.ts`
  - Platform workbench now shows the system-switch task only.
  - Create system opens an in-app form instead of a permanent full-width panel.
  - Platform todos and messages remain available through their own header entries and are no longer rendered on the dashboard by default.
- `frontend/src/features/runtime/records/runtimeRecords.ts`
  - When no published business module is available, runtime renders one clean empty state.
  - The empty state hides toolbar actions, search/filter panels, batch actions, and right-side detail until a real module is published.
- `frontend/src/app/state.ts` and `frontend/src/features/system-shell/systemShell.ts`
  - Added frontend tenant list/switch state support.
  - Multi-tenant systems can render a tenant selector in the system header; single-tenant systems hide it.
- `scripts/recovery-r1-release-context-smoke.ps1`
  - Uses ASCII recovery system names.
  - Cleans its created system by default unless `-KeepCreatedData` is explicit.
- `scripts/recovery-clean-test-systems.ps1`
  - Adds a guarded cleanup script for recovery smoke systems.
  - Defaults to dry-run; `-Execute` is required to delete.
- `scripts/package-release.ps1` and `scripts/local-start-release.ps1`
  - Updated local tool defaults to current verified paths: `D:\dev\jdk21`, `D:\dev\maven`, `D:\dev\nodejs24`.

## Cleanup Evidence

Dry-run before cleanup matched seven recovery systems:

```json
{
  "status": "DRY_RUN",
  "matchedCount": 7,
  "matchedSystemCodes": [
    "ctx_20260627160203",
    "ctx_20260627155836",
    "ctx_20260627155324",
    "ctx_20260627152508",
    "ctx_20260627152353",
    "ctx_20260627152319",
    "recovery_20260627151700"
  ]
}
```

Execute cleanup deleted all seven with `SUCCESS`.

Final dry-run after browser smoke:

```json
{
  "status": "DRY_RUN",
  "matchedCount": 0
}
```

The fixed R1 context smoke also passed and cleaned up its own created system:

```json
{
  "status": "PASS",
  "createdSystemId": "71",
  "createdTenantId": "72",
  "tenantSwitchCode": "SUCCESS",
  "cleanupCode": "SUCCESS"
}
```

## Build And Release Evidence

Frontend:

```text
npm.cmd run typecheck
npm.cmd run build
```

Both passed.

Release package:

```text
powershell -ExecutionPolicy Bypass -File scripts/package-release.ps1
```

Result:

```json
{
  "status": "PASS",
  "releaseDir": "D:\\workspace\\01_project\\snow\\cursor\\examine2\\release\\unexamine-0.0.1-SNAPSHOT",
  "zipPath": "D:\\workspace\\01_project\\snow\\cursor\\examine2\\release\\unexamine-0.0.1-SNAPSHOT.zip",
  "frontendApiMode": "same-origin-nginx-proxy"
}
```

Standalone start:

```json
{
  "backendPid": 21232,
  "frontendPid": 4320,
  "frontendUrl": "http://127.0.0.1:18131/",
  "health": {
    "status": "UP",
    "redis": "UP",
    "database": "UP",
    "schema": "UP"
  }
}
```

Release verification:

```json
{
  "status": "PASS",
  "baseUrl": "http://127.0.0.1:18131",
  "checks": [
    "frontend config uses same-origin API",
    "nginx proxies /api to backend 9999",
    "redis tcp reachable",
    "deployed frontend matches release assets",
    "backend health all UP",
    "admin login succeeds"
  ],
  "deployedAssets": [
    "/assets/index-C7wMYZoS.css",
    "/assets/index-CfQsPpsN.js"
  ]
}
```

R0 static audit:

```json
{
  "status": "PASS",
  "promptOrConfirmCount": 0,
  "duplicateSidebarTargetCount": 0,
  "inertPaginationCount": 0,
  "releaseDirectoryExists": true
}
```

## Browser Rendering Evidence

Temporary Playwright rendered the standalone release at `http://127.0.0.1:18131`.

Platform dashboard result:

```json
{
  "url": "http://127.0.0.1:18131/#/platform/dashboard",
  "bodyHeight": 720,
  "headings": ["平台工作台", "系统切换"],
  "createSystemPanelCount": 0,
  "todoPanelVisible": false,
  "messagePanelVisible": false,
  "systemCards": 0
}
```

Runtime empty-state result, using a temporary system that was deleted after the check:

```json
{
  "url": "http://127.0.0.1:18131/#/systems/72/modules",
  "bodyHeight": 720,
  "emptyStateCount": 1,
  "filterPanelCount": 0,
  "batchBarCount": 0,
  "sidePanelCount": 0,
  "toolbarButtons": [],
  "cleanupCode": "SUCCESS",
  "failed": []
}
```

Screenshots:

- `docs/evidence/recovery/screenshots/r1-platform-dashboard-after-cleanup.png`
- `docs/evidence/recovery/screenshots/r1-runtime-empty-state-clean.png`

## Result

PASS for the immediate standalone visible clutter regression:

- platform dashboard no longer stacks unrelated workbench panels;
- runtime empty module state no longer exposes unavailable controls;
- recovery smoke systems are cleaned from the shared database;
- current release package and running standalone frontend are aligned.

Remaining open work:

- tenant switch UI needs a dedicated multi-tenant browser smoke;
- normal-member permission negative evidence for module/runtime remains pending;
- runtime record CRUD, approval, todo, message, and final release user script are still open recovery tasks.
