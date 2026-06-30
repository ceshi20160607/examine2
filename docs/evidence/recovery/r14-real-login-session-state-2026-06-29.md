# R14 Real Login Session State Evidence

Time: 2026-06-29 Asia/Shanghai

Task: `REC-P0-017 Real Login Session State Browser Evidence Closure`

Status: PASS

## Purpose

R13 proved broad responsive layout, but its browser setup wrote tokens into localStorage after the SPA had already mounted. R14 closes that evidence gap by proving the deployed product works through the real login page and initializes shell state from the authenticated browser session.

## Evidence

- Script: `scripts/recovery-r14-real-login-session-smoke.ps1`
- Browser audit JSON: `docs/evidence/recovery/screenshots/r14-real-login-session/real-login-session-audit.json`
- Screenshots:
  - `docs/evidence/recovery/screenshots/r14-real-login-session/mobile-after-real-login.png`
  - `docs/evidence/recovery/screenshots/r14-real-login-session/mobile-system-admin-after-real-login.png`
  - `docs/evidence/recovery/screenshots/r14-real-login-session/mobile-work-after-real-login.png`
- Final orchestration: `docs/evidence/recovery/r5-final-release-result.json`

## Assertions

- The browser opened `/login`, filled `admin / 123123aa`, and clicked the real login form button.
- Login initialized browser state with `hasToken=true` and `localAccountId=25`.
- The authenticated shell displayed the `admin` account label.
- The same browser session opened `#/systems/{systemId}/admin` and `#/systems/{systemId}/work`.
- System admin module management exposed `.admin-content` and `.module-builder-toolbar`.
- Work management exposed the work page content.
- All three mobile captures reported `overflowX=0` and no blockers.
- Created system `328` was deleted by the script.

## R5 Result

`scripts/recovery-r5-final-user-script.ps1` now includes R14 and passed with `stepsPassed=22`, `stepsFailed=0`, `stoppedAfterRun=false`.

The release remains running at `http://127.0.0.1:18131/` for user verification.
