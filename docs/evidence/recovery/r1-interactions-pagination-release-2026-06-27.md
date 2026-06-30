# R1 Interaction, Pagination, And Release Package Evidence

Time: 2026-06-27 Asia/Shanghai

## Scope

This is a recovery progress record for `REC-P0-002`, `REC-P0-008`, and `REC-P0-009`.

Superseding note: the schema part of this runtime failure was later fixed and verified in `docs/evidence/recovery/r1-health-schema-contract-2026-06-27.md`. Current release health reports `database=UP`, `schema=UP`, and `redis=DOWN`.

## Frontend Interaction Cleanup

Implemented:

- Added `frontend/src/shared/dialogs.ts` for in-app text input, multi-field form input, and confirmation dialogs.
- Replaced all `window.prompt`, `window.confirm`, and `alert` matches under `frontend/src`.
- Replaced prompt-driven actions in:
  - no-member access request
  - platform password change
  - platform admin lifecycle, role, SSO provider, model authorization, provider test/publish
  - system admin org/member/role, module/field/action, flow/dict, OpenAPI, work config, Agent policy, SSO policy
  - system-shell approval approve/reject/transfer
  - runtime scene save

## Pagination Cleanup

Implemented:

- Platform todo/message panels now load real next/previous pages through `loadPlatformTodos({ pageNo })` and `loadPlatformMessages({ pageNo })`.
- Platform/system admin and shared table pagination now require an `onPageChange` callback for active buttons; without one the controls are disabled with an explicit reason instead of rendering inert clickable buttons.
- System shell pagination without callbacks is disabled with a reason; system todo keeps its real callback.

## Release Package

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\package-release.ps1 -JavaHome D:\dev\jdk21 -MavenPath D:\dev\maven\bin\mvn.cmd -NpmPath D:\dev\nodejs24\npm.cmd
```

Result:

- Backend Maven reactor package: PASS.
- Frontend production build: PASS.
- Release directory exists: `release/unexamine-0.0.1-SNAPSHOT`.
- Release zip exists: `release/unexamine-0.0.1-SNAPSHOT.zip`.

## Static Audit

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\recovery-r0-static-audit.ps1
```

Result: `PASS`

- `promptOrConfirmCount=0`
- `duplicateSidebarTargetCount=0`
- `inertPaginationCount=0`
- `releaseDirectoryExists=true`

## Runtime Release Health

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\local-start-release.ps1 -JavaExe D:\dev\jdk21\bin\java.exe
```

Result: FAIL, and the script stopped the unhealthy backend process.

Health response showed:

- `database=UP`
- `schema=MISMATCH`
- `redis=DOWN`
- Missing schema includes platform permission, SSO, module config, runtime draft/sequence, upload, flow, message, work, async, agent, and ops columns.

## Acceptance Status

Accepted for static recovery gate only:

- R0 static audit is now PASS.
- Frontend typecheck/build and release package build pass.

Not accepted for final product:

- Release runtime health does not pass until Redis is reachable.
- Browser/runtime user-script evidence is still required after health is UP.
