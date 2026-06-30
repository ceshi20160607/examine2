# R9 SSO And No-Member Evidence

Time: 2026-06-27 23:13 Asia/Shanghai

## Result

- Task: `REC-P0-014 SSO And No-Member Lifecycle Closure`
- Repeatable script: `scripts/recovery-r9-sso-no-member-smoke.ps1`
- Final orchestration: `scripts/recovery-r5-final-user-script.ps1`
- Result: PASS

## API Evidence

The R9 smoke proves the lifecycle with real backend persistence and permission checks:

- Created target system `216`, tenant `234`, provider `idp_oidc_d842be38`.
- Platform identity provider test passed and provider was published.
- System SSO policy was saved with status `ACTIVE`.
- SSO org/member precheck returned unmatched department/member evidence.
- Requester `r9_requester_0627231220362_2ae04e` had no target system membership before approval.
- Switching into target system before approval returned HTTP `403`.
- Requester submitted no-member request `5` and could read back the submitted request.
- Incomplete approval returned status `REVIEWING`, `businessAccessAllowed=false`, and switching remained HTTP `403`.
- Full approval returned `APPROVED`, `businessAccessAllowed=true`, system member `287`, account-member binding `287`, and SSO binding `5`.
- Requester could switch into target system after approval and received role `R9_APPROVED_0627231220362_2ae04e`.
- R9 systems from the final orchestration were cleaned: `216`, `217`.

## Browser Evidence

A kept R9 browser-evidence run created target system `203`, tenant `220`, requester `r9_requester_0627230518453_5bbfc8`, approved system member `268`, account-member binding `268`, and SSO binding `4`.

After the frontend fix and release redeploy, browser verification used cache-busted URL `http://127.0.0.1:18131/?v=r9-20260627-2313#/no-member?systemId=203&tenantId=220` and confirmed:

- Active deployed bundle: `/assets/index-C49n4ymp.js`.
- Page shows `APPROVED`.
- Page shows `已生成系统成员映射，可以进入系统。`
- Page shows `系统成员 268`, `账号成员绑定 268`, and `SSO 绑定 4`.
- Page shows exactly the expected action buttons, including `进入系统`.
- The old misleading blocker `NO_SYSTEM_MEMBER_MAPPING` is no longer visible after approval.
- Clicking `进入系统` routes to `#/systems/203/dashboard` and shows `R9 SSO Target 0627230518453_5bbfc8` system dashboard.

The kept browser-evidence systems were cleaned after capture: `203`, `204`.

## Release Evidence

The final R5 orchestration includes R9 and passes with:

- `status=PASS`
- `stepsPassed=17`
- `stepsFailed=0`
- Backend health after restart: `status=UP`, `database=UP`, `schema=UP`, `redis=UP`
- Redis: `192.168.0.211:6379`
- Deployed frontend assets: `/assets/index-C49n4ymp.js`, `/assets/index-C8CAAU0i.css`

## Boundary

This closes the SSO/no-member lifecycle row. It does not close unrelated P0 rows such as OpenAPI/upload/import-export or AI Agent.
