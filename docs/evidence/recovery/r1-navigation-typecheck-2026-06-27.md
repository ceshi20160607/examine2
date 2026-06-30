# R1 Navigation And Build Recovery Evidence

Time: 2026-06-27 Asia/Shanghai

## Scope

This evidence covers the first corrective coding pass after the R0 static audit. It is a partial recovery result, not final product acceptance.

Mapped tasks:

- `REC-P0-002 Four Shells And Navigation Separation`
- `REC-P0-009 Frontend Interaction Contract Cleanup`

## Changes

- Platform admin sidebar targets are now unique for platform info, platform org, platform roles, dashboard, config, and logs.
- System admin sidebar targets are now unique for system info, org structure, role management, module config, flow management, dictionary management, dashboard config, data source, OpenAPI apps, work config, SSO config, Agent config, and logs.
- Added platform org and dashboard panels so those menu items no longer jump into unrelated panels.
- Added system dashboard panel and stable DOM targets inside grouped admin sections so each menu item has a concrete landing area.
- Repaired `systemShell.ts` half-finished todo pagination/action-panel wiring so frontend typecheck and production build pass again.
- `systemShell.ts` todo pagination now supports an actual page-change callback.

## Commands

```powershell
cd frontend
npm.cmd ci
npm.cmd run typecheck
npm.cmd run build
cd ..
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\recovery-r0-static-audit.ps1
git diff --check -- frontend/src/features/platform-admin/platformAdmin.ts frontend/src/features/system-admin/systemAdmin.ts frontend/src/features/system-shell/systemShell.ts
```

## Results

- `npm.cmd ci`: PASS, 16 packages installed, 0 vulnerabilities.
- `npm.cmd run typecheck`: PASS.
- `npm.cmd run build`: PASS.
- `git diff --check`: PASS except repository LF/CRLF warnings.
- Static audit script: still `FAIL`, but `duplicateSidebarTargetCount` is now `0`.

Current static audit remaining blockers:

- `promptOrConfirmCount=61`
- `inertPaginationCount=4`
- `releaseDirectoryExists=false`
- `mockSampleStubCount=3`

## Acceptance Status

Partial acceptance only:

- Navigation target duplication is remediated.
- Frontend typecheck/build are restored.
- R1 cannot be accepted yet because prompt-driven P0 actions, inert pagination, release package verification, and runtime/browser screenshots remain open.

Superseded follow-up:

- `docs/evidence/recovery/r1-interactions-pagination-release-2026-06-27.md` later reduced `promptOrConfirmCount` and `inertPaginationCount` to `0` and rebuilt the release package.
- Runtime/browser acceptance still remains open. The later health-schema correction made `schema=UP`, but release health still fails with `redis=DOWN`.
