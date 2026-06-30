# Recovery R4 Admin Breadth Evidence

Date: 2026-06-27 Asia/Shanghai

Verdict: PASS / accepted for `REC-P0-007 Admin Configuration Breadth Smoke`.

## Scope

R4 verifies that platform/system admin surfaces are not static stacked shells and that each P0 admin surface is either API-backed or explicitly marked as not implemented.

Covered surfaces:

- Platform admin dependencies: systems, roles, identity providers, agent model authorizations, platform logs, platform health.
- System admin dependencies: departments, members, roles, tenants, dictionaries, flows, work config, OpenAPI apps, SSO policy, Agent policies, system logs.
- Browser surfaces: system info, organization, roles, dictionaries, flows, OpenAPI apps, work config, SSO, Agent, data source, logs.

## Fixes Included

- `backend/examine-ai-work/src/main/java/com/unique/examine/aiwork/manage/work/WorkManagementService.java`
  - `PATCH /api/v1/systems/{systemId}/work/config` now persists to `un_module_work_config`.
  - `GET /api/v1/systems/{systemId}/work/config` now reads the persisted config back.
  - Work management context now uses the same active `accountMemberBindingId` resolver as module/runtime APIs, so tenant/system switching does not fall back to the first binding.
- `backend/examine-ai-work/pom.xml`
  - Added `examine-module` dependency so work management can use the generated work-config table service and active module context resolver.
- `frontend/src/features/system-admin/systemAdmin.ts`
  - Work config summaries now show persisted field names and field codes, for example `R4 Priority (r4Priority0627205335)`.
  - SSO summaries show saved tenant domains instead of only a count.
- `scripts/recovery-r4-admin-breadth-smoke.ps1`
  - Added full admin breadth smoke.
  - Strengthened work-config acceptance: after `PATCH`, the script performs `GET /work/config` and asserts the saved field code/name are present.
- `scripts/recovery-clean-test-systems.ps1`
  - Added cleanup support for `R4 Admin System*` / `r4adm_*` evidence systems.

## API Evidence

Latest R4 run after rebuild/restart:

- Command: `powershell -ExecutionPolicy Bypass -File scripts/recovery-r4-admin-breadth-smoke.ps1 -BaseUrl http://127.0.0.1:18131`
- Result: PASS
- Evidence system: `135`, cleaned by script
- Work config readback:
  - `workConfigProjectFields`: `1`
  - `workConfigReadbackProjectFields`: `1`
  - `workConfigReadbackFieldCode`: `r4Priority0627210012`
- SSO domain readback: `r4-0627210012.example.test`
- OpenAPI rotation job: `srj_openapi_10_1f361a9e`
- Agent publish-check traceId returned.

Kept-data browser run:

- System: `134`
- Suffix: `0627205335`
- Work config field code: `r4Priority0627205335`
- Browser evidence was captured, then systems `134` and older kept system `131` were deleted with `scripts/recovery-clean-test-systems.ps1 -Execute`.

## Browser Evidence

Screenshots:

- `docs/evidence/recovery/screenshots/r4-admin-breadth/system-info.png`
- `docs/evidence/recovery/screenshots/r4-admin-breadth/org-structure.png`
- `docs/evidence/recovery/screenshots/r4-admin-breadth/role-management.png`
- `docs/evidence/recovery/screenshots/r4-admin-breadth/dict-management.png`
- `docs/evidence/recovery/screenshots/r4-admin-breadth/flow-management.png`
- `docs/evidence/recovery/screenshots/r4-admin-breadth/openapi-apps.png`
- `docs/evidence/recovery/screenshots/r4-admin-breadth/work-config.png`
- `docs/evidence/recovery/screenshots/r4-admin-breadth/sso-config.png`
- `docs/evidence/recovery/screenshots/r4-admin-breadth/agent-config.png`
- `docs/evidence/recovery/screenshots/r4-admin-breadth/data-source.png`
- `docs/evidence/recovery/screenshots/r4-admin-breadth/log-management.png`
- `docs/evidence/recovery/screenshots/r4-admin-breadth/result.json`

Browser assertions:

- Deployed asset in browser: `/assets/index-q_Q_PWjk.js`
- Every admin sidebar target rendered exactly one `.panel`.
- Visible evidence:
  - system info: `R4 Admin System 0627205335 Updated`
  - organization: `R4 Department 0627205335`
  - role: `R4 Role 0627205335`
  - dictionary: `R4 Status 0627205335`
  - flow: `R4 Flow 0627205335`
  - OpenAPI: `R4 OpenAPI 0627205335`
  - work config: `R4 Priority` and `r4Priority0627205335`
  - SSO: `r4-0627205335.example.test`
  - Agent: `r4_agent_0627205335`
  - data source: explicit `待接入`
  - logs: `日志管理`
- Console errors: `0`

## Release And Regression

- `mvn -pl examine-ai-work -am compile`: PASS
- `scripts/package-release.ps1`: PASS
- `scripts/local-start-release.ps1`: PASS
- `scripts/verify-release.ps1 -BaseUrl http://127.0.0.1:18131 -CheckDeployedFrontend`: PASS
  - Redis TCP `192.168.0.211:6379`: PASS
  - backend health `status/database/schema/redis`: UP
  - deployed assets match release assets: PASS
  - admin login: PASS
- `scripts/recovery-r0-static-audit.ps1`: PASS
- `scripts/recovery-r2-tenant-business-redraw-smoke.ps1`: PASS when run serially.
- `scripts/recovery-r3-runtime-approval-smoke.ps1`: PASS.

## Boundary

Data-source management remains an explicit product/API gap. The system admin data-source page is accepted only as an explicit disabled/not-connected surface, not as implemented data-source management.

R5 final release/user script remains pending.
