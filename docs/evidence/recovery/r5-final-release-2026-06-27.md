# R5 Final Release Evidence

Time: 2026-06-27 23:13 Asia/Shanghai

## Result

- Final orchestration script: `scripts/recovery-r5-final-user-script.ps1`
- Result file: `docs/evidence/recovery/r5-final-release-result.json`
- Status: PASS
- Steps passed: 17
- Steps failed: 0
- Release left running for user verification: yes

## Release Runtime

- Frontend URL: `http://127.0.0.1:18131/`
- Backend URL: `http://127.0.0.1:9999`
- Backend PID after restart: `3816`
- Frontend PID after restart: `17128`
- Health after restart: `status=UP`, `database=UP`, `schema=UP`, `redis=UP`
- Redis: `192.168.0.211:6379`
- Admin login: `admin / 123123aa`

## Script Coverage

The final script rebuilt and restarted from the release package, then ran these checks serially:

- `scripts/verify-release.ps1 -BaseUrl http://127.0.0.1:18131 -CheckDeployedFrontend`
- `scripts/recovery-r6-apply-schema.ps1`
- `scripts/recovery-r2-module-publish-smoke.ps1`
- `scripts/recovery-r2-tenant-business-redraw-smoke.ps1`
- `scripts/recovery-r3-runtime-approval-smoke.ps1`
- `scripts/recovery-r4-admin-breadth-smoke.ps1`
- `scripts/recovery-r6-data-source-smoke.ps1`
- `scripts/recovery-r7-work-management-smoke.ps1`
- `scripts/recovery-r8-todo-message-center-smoke.ps1`
- `scripts/recovery-r9-sso-no-member-smoke.ps1`
- release stop, restart, and second `verify-release.ps1`

## Important Assertions

- Deployed frontend assets match the fresh release assets: `/assets/index-C8CAAU0i.css`, `/assets/index-C49n4ymp.js`.
- Same-origin `/api` mode is preserved; no embedded backend host is shipped in the frontend package.
- The release Nginx template sends `Cache-Control: no-store` for `/index.html` and SPA fallback HTML, preventing a standalone deployment from continuing to serve an old frontend bundle entry.
- Redis TCP and backend health are hard gates.
- R2 module publishing and tenant-specific business redraw pass.
- R3 runtime record, upload attachment, separate requester/approver approval, todo, message, and idempotency pass.
- R4 admin breadth passes, including work config PATCH-to-GET persistence readback.
- R6 data-source management passes, including schema migration, persisted readback, connection-check, publish-check, and deployed-browser evidence.
- R7 work management passes, including project creation, project task creation, plain task creation, daily report creation, auto-draft generation, dashboard count readback, list search readback, kanban readback, and deployed-browser evidence.
- R8 todo/message center passes, including requester/approver todo separation, todo target readback, message unread/read/active/archived readback, mark-read, mark-all-read, archive, active stream exclusion, archived stream readback, todo handled transition, and deployed-browser evidence.
- R9 SSO/no-member passes, including identity provider test/publish, active system SSO policy, precheck, no-member request creation without target system membership, incomplete approval remaining blocked, full approval creating account-member/role/SSO binding evidence, requester switch after approval, and deployed-browser evidence.
- Recovery test systems from kept browser evidence were cleaned, including R9 systems `203` and `204`.

## Boundary

This is engineering acceptance evidence for R5. The project gate `gates.user_script_passed` remains `false` until the user personally verifies or signs off on the running release.
