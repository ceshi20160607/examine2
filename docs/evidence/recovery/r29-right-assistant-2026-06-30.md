# R29 Right-Side Intelligent Assistant First Loop

Date: 2026-06-30

Task: REC-P0-029 Right-Side Intelligent Assistant

Status: PASS as first-loop engineering evidence. This does not close REQ-9.

## Evidence

- `scripts/recovery-r29-right-assistant-smoke.ps1` passed on the deployed release at `http://127.0.0.1:18131/`.
- Release verification passed before the smoke with deployed assets `/assets/index-B9EQzV1l.js` and `/assets/index-C0jCwNCi.css`.
- Browser evidence:
  - `docs/evidence/recovery/screenshots/r29-right-assistant/assistant-drawer-browser-audit.json`
  - `docs/evidence/recovery/screenshots/r29-right-assistant/desktop-assistant-drawer.png`
- API result:
  - `docs/evidence/recovery/r29-right-assistant-result.json`

## What Passed

- System header exposes the right-side `助手` entry in the existing system shell.
- The assistant drawer renders context summary, prompt input, generate actions, and admin-generation action state.
- System Agent policy publish-check passed.
- Admin Agent message proposed both `SYSTEM_AGENT_WRITE_CONFIRM` and `WORK_AGENT_DRAFT_CONFIRM`.
- Write preview stayed `WAITING_HUMAN_CONFIRM` and included permission clipping evidence.
- Human-confirmed write became `CONFIRMED`.
- Separate write preview rejection became `REJECTED`.
- Work draft preview stayed `WAITING_HUMAN_CONFIRM`; human confirmation became `CONFIRMED`.
- Normal system member could create a scoped assistant session and message.
- Normal system member admin policy creation was rejected with HTTP 403.
- Agent audit readback returned 13 records, including 6 confirmation audit records.
- Browser audit confirmed active deployed asset `/assets/index-B9EQzV1l.js`, drawer presence, expected buttons/input, and `overflowX=0`.

## Still Open

- Normal-member browser proof for disabled admin-generation action.
- Mobile drawer audit.
- Richer task-specific assistant drafts for field/process/export scenarios.
- Better focus management and keyboard handling inside the drawer.
- Broader failure-state coverage for missing policy/model authorization and write conflicts.
