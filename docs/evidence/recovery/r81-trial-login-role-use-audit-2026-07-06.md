# R81 Trial Login And Role Use Audit

Status: PASS

This is engineering evidence only. It proves the retained R80 trial package can be entered from the deployed login page by each role, but it does not close user signoff.

## Role Entry Evidence

- Admin login: `admin / 123123aa` -> `http://127.0.0.1:18131/#/platform`
- Runtime normal member: `r63_member_0706162707_4a00b6 / Aa123456!` -> `http://127.0.0.1:18131/#/systems/1118/modules`
- Runtime readonly member: `r63_readonly_0706162707_4a00b6 / Aa123456!` -> `http://127.0.0.1:18131/#/systems/1118/modules`
- Workflow requester: `r3_requester_0706162803307_ca6efa / Aa123456!` -> `http://127.0.0.1:18131/#/systems/1121/modules`
- Workflow approver: `r3_approver_0706162803307_ca6efa / Aa123456!` -> `http://127.0.0.1:18131/#/systems/1121/todos` and `http://127.0.0.1:18131/#/systems/1121/messages`

## Evidence

- Browser role audit: `docs/evidence/recovery/screenshots/r81-trial-login-role-use-audit/trial-login-role-use-browser-audit.json`
- Screenshots: `docs/evidence/recovery/screenshots/r81-trial-login-role-use-audit/`
- Result JSON: `docs/evidence/recovery/r81-trial-login-role-use-audit-result.json`
- Readonly create denial: `403`
- Workflow terminal status: `APPROVED`
- Requirement coverage remains partial: notClosed `45`
- User signoff remains `false`.
