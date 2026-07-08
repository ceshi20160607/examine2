# R28 Command Center Closure Evidence

Status: PASS for the first command-center navigation loop; REQ-9 remains PARTIAL.

Date: 2026-06-30

Deployed URL: `http://127.0.0.1:18131/`

Active deployed frontend assets:

- `/assets/index-CnD8miaq.js`
- `/assets/index-CuXcp5xt.css`

Implemented scope:

- Backend authenticated command search endpoint: `GET /api/v1/command-center`.
- Platform and system command groups for dashboard/work/todo/message/admin entries.
- Permission-aware admin commands:
  - platform/system admin entries enabled for authorized admin.
  - platform/system admin entries disabled with reason for normal member.
- Frontend command center overlay.
- Platform header and system header command entry.
- `Ctrl/Cmd+K` command-center shortcut.
- Search filtering and route execution through existing shell navigation.

Evidence:

- API smoke: `scripts/recovery-r28-command-center-smoke.ps1`
- API result: `docs/evidence/recovery/r28-command-center-result.json`
- Browser audit result: `docs/evidence/recovery/screenshots/r28-command-center/command-center-browser-audit.json`
- Browser screenshot: `docs/evidence/recovery/screenshots/r28-command-center/desktop-command-center-admin-search.png`

Assertions:

- Release verification passed against the deployed package.
- Redis `192.168.0.211:6379`, database, schema, and admin login are UP/PASS.
- Admin command-center result exposes enabled `/platform/admin`.
- Admin command-center result exposes enabled `/systems/{systemId}/admin`.
- Admin command-center result exposes enabled `/systems/{systemId}/work`.
- Normal member command-center result exposes enabled `/systems/{systemId}/dashboard`.
- Normal member command-center result exposes enabled `/systems/{systemId}/work`.
- Normal member system-admin command is disabled with a disabled reason.
- Normal member platform-admin command is disabled with a disabled reason.
- Browser real deployed page shows the `命令` header button.
- Browser command overlay opens and search keyword `后台` returns platform/system backend commands.
- Browser audit records active asset `/assets/index-CnD8miaq.js` and `overflowX=0`.

Remaining scope before command-center can be considered broad `PROVEN`:

- Keyboard result selection and focus management.
- Recent/favorite command persistence.
- Safe quick-create/draft execution beyond route navigation.
- Normal-member deployed browser evidence for disabled admin command display.
- Broader mobile command-center browser audit.
