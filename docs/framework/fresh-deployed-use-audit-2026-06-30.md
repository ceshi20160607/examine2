# Fresh Deployed-Use Audit

Time: 2026-06-30 Asia/Shanghai

## Purpose

This audit starts from the currently running deployment instead of relying only on old recovery summaries.

The user reported that the standalone system still feels crowded, confused, and not directly usable. Therefore, this audit treats automated PASS as engineering evidence only, not as user signoff.

This audit is for the current product only. Future framework extraction is not part of this pass.

## Environment

- baseUrl: `http://127.0.0.1:18131`
- Redis: `192.168.0.211:6379`
- health at audit start: database/schema/redis `UP`
- default login: `admin / 123123aa`

## Commands Run

| Check | Command | Result |
|---|---|---|
| Health | `Invoke-RestMethod http://127.0.0.1:18131/api/v1/health` | PASS |
| Final journey evidence consistency | `scripts/recovery-r21-final-usable-system-audit.ps1 -BaseUrl http://127.0.0.1:18131` | PASS, failedGateCount=0 |
| Module builder usability | `scripts/recovery-r12-module-builder-usability-smoke.ps1 -BaseUrl http://127.0.0.1:18131` | PASS |
| Cross-shell responsive/browser layout | `scripts/recovery-r13-cross-shell-responsive-smoke.ps1 -BaseUrl http://127.0.0.1:18131` | PASS, 16 screenshots |

## Evidence

- `docs/evidence/recovery/r21-final-usable-system-audit-result.json`
- `docs/evidence/recovery/r12-module-builder-usability-result.json`
- `docs/evidence/recovery/screenshots/r13-cross-shell-responsive/responsive-layout-audit.json`
- `docs/evidence/recovery/screenshots/r13-cross-shell-responsive/*.png`

## Finding

Engineering evidence is currently coherent for the checked subset:

- R21 final journey evidence is internally consistent.
- R12 module builder can create/configure/publish and now writes fresh result JSON to disk.
- R13 browser layout audit passes for platform workspace/admin and system business/admin desktop/mobile routes.

But this does not close the user's reported issue by itself:

- R21 mainly checks evidence presence and child result status.
- R12 proves a task path, not the whole product feeling usable.
- R13 proves layout containment and route coverage, not subjective information architecture quality.
- User signoff remains open.

## Correction Made During Audit

`scripts/recovery-r12-module-builder-usability-smoke.ps1` previously printed fresh JSON to stdout but did not update `docs/evidence/recovery/r12-module-builder-usability-result.json`. The fixed script now writes the result file and stdout from the same result object.

This matters because stale evidence files can make a later audit read the wrong run.

## Current Status

- engineering_subset_status: `PASS`
- user_reported_usability_status: `OPEN`
- user_signoff_status: `OPEN`
- next_required_action: manual role-journey inspection or targeted task card for any route where the user still sees stacked/mixed UI
