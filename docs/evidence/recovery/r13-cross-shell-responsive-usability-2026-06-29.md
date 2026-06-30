# R13 Cross-Shell Responsive Usability Evidence

Time: 2026-06-29 Asia/Shanghai

Task: `REC-P0-016 Cross-Shell Responsive Usability Closure`

## Result

Status: `PASS`

R13 closes the reopened user-facing layout regression where the deployed product still looked piled together on narrow screens after R12.

## What Changed

- System admin narrow layout now turns the vertical admin sidebar into a compact horizontal navigation strip, so selected content is visible in the first viewport.
- Shared grid/card containers now set `min-width: 0` where needed, preventing child grids from forcing document-level horizontal overflow.
- Work dashboard, calendar, kanban, and work list layouts now adapt at narrow widths instead of clipping cards.
- Generic table shells now scroll horizontally inside their own container instead of clipping wide tables or widening the whole document.
- Added `scripts/recovery-r13-cross-shell-responsive-smoke.ps1` and included it in `scripts/recovery-r5-final-user-script.ps1`.

## Browser Evidence

Repeatable script:

- `scripts/recovery-r13-cross-shell-responsive-smoke.ps1 -BaseUrl http://127.0.0.1:18131`

Standalone R13 result:

- `status=PASS`
- `task=REC-P0-016`
- `screenshotCount=16`
- `cleanup=DELETE`

Machine-readable browser audit:

- `docs/evidence/recovery/screenshots/r13-cross-shell-responsive/responsive-layout-audit.json`

Screenshot directory:

- `docs/evidence/recovery/screenshots/r13-cross-shell-responsive/`

Routes covered at desktop `1280x720` and mobile `390x720`:

- Platform workspace
- Platform admin
- System dashboard
- System modules
- System work
- System todos
- System messages
- System admin module management

Assertions:

- No document-level horizontal overflow.
- No selected content clipped horizontally.
- Narrow system admin sidebar height stays compact.
- Narrow system admin selected content appears in the first viewport.
- Work management cards are readable and not clipped on narrow viewport.

## Final Orchestration

`scripts/recovery-r5-final-user-script.ps1 -BaseUrl http://127.0.0.1:18131` passed after adding R13:

- `status=PASS`
- `stepsPassed=21`
- `stepsFailed=0`
- `stoppedAfterRun=false`
- release left running for user verification

Post-restart verification:

- release assets match deployed assets: `/assets/index-BDHwOq79.js`, `/assets/index-sTkcXDBK.css`
- health: database/schema/Redis all `UP`
- Redis: `192.168.0.211:6379`
- admin login succeeds

Running release after final script:

- URL: `http://127.0.0.1:18131/`
- backendPid: `76`
- frontendPid: `22336`

## Boundary

This is engineering recovery acceptance, not user signoff. `gates.user_script_passed` remains `false` until the user verifies or signs off.
