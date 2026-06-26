# Che Primary E2E Evidence - TASK-QA-020

- Task: `TASK-QA-020`
- Evidence time: 2026-06-24T11:11:25+08:00
- Verdict: PASS
- Raw machine evidence: `docs/evidence/e2e-che-script.raw.json`
- Backend test port: `18092`
- Frontend route-check port: `18093`
- Browser verification port: `18094` (stopped after verification)

## Summary

The che primary E2E contract passed 53 checks with 0 failures.

The run validated the platform root bootstrap path, system/member/tenant context creation, module and field configuration, flow configuration, che permission boundaries, runtime list/detail/create/edit/approval/import/export, todo/message targets, and work management coverage.

## 11-Step Script

| Step | Scope | Result | Evidence |
|---|---|---:|---|
| 1 | `platform_admin_root` login | PASS | `POST /api/v1/auth/login` returned `defaultLanding=PLATFORM`, `traceId=trc_17897d82-9f49-41d0-a697-978ff31a128f`, `auditLogId=aud_trc_17897d82-9f49-41d0-a697-978ff31a128f`. |
| 2 | Create vehicle system and bootstrap super admin | PASS | `POST /api/v1/auth/register-with-system` returned `systemId=sys_vehicle`, `systemMemberId=member_super_admin`, and init steps `CREATE_SYSTEM,CREATE_DEFAULT_TENANT,CREATE_SUPER_ADMIN_ROLE,BIND_ACCOUNT_MEMBER`. |
| 3 | System switch and tenant switch | PASS | System options exposed `sys_vehicle`; `SystemSwitchContext` returned `accountMemberBindingId=amb_001`, `systemMemberId=member_001`, permission snapshot, data scope, and message/todo scope. Tenant switch returned `tenant_branch` and tenant data scope. |
| 4 | System admin config for module, field, list, actions, import/export | PASS | Module groups, modules, fields, list schema, action contracts, import/export config, and publish check all returned successfully. Publish check returned impact refs and no failures. |
| 5 | Flow configuration | PASS | Flow list, node library, canvas, condition-node property panel, and simulation returned successfully. Simulation produced branch decision and audit trace. |
| 6 | `che` login and permission boundaries | PASS | Effective permissions returned action/field/data-scope metadata. Restricted export/action preview returned deny metadata instead of granting admin-only capability. |
| 7 | Vehicle list and detail | PASS | Runtime list schema/search/detail returned row-click target, fields, sort/filter metadata, selected count, detail tabs, approval snapshot, `traceId`, and `auditLogId`. |
| 8 | Create/edit/submit approval | PASS | Runtime create/update/action calls returned visible result contracts and audit traces. Submit approval produced workflow handoff data. |
| 9 | Approval processing | PASS | Workflow snapshot and approval action passed; duplicate approval/idempotency behavior did not create conflicting state. |
| 10 | Import/export | PASS | Import precheck, import confirm, and export selected returned async task/result-file contracts, error-file references, trace IDs, and desensitized export field metadata. |
| 11 | Todo, message, and work management | PASS | Todo type-tree/list/action, message card targets and mark-read, work dashboard/config/project-task/plain-task/kanban/daily-report/auto-draft all returned successfully. |

## Browser Verification

Browser verification was run against `http://127.0.0.1:18094/index.html`.

| Area | Result | Notes |
|---|---:|---|
| Auth routes | PASS | `#/login`, `#/register-with-system`, and `#/forgot-password` all mounted. Login route showed 3 auth tabs, 1 password input, and no vertical page scrollbar at the checked viewport. |
| Platform shell | PASS | `#/platform` mounted with platform workbench and message stream. `#/platform/admin` mounted platform admin layout. |
| System shell | PASS | Dashboard, modules, todos, work, and system admin routes mounted under `#/systems/sys_vehicle/*`. |
| Runtime list/detail | PASS | Runtime route rendered module sidebar, filters, batch bar, table, 5 clickable rows, 5 `vehicleDetailDrawer` row targets, and one right detail panel. |
| Duplicate detail actions | PASS | Row actions were only `编辑`, `打印`, `删除`; no `详情/查看/打开/进入` style duplicate detail actions were present. |
| Import/export placement | PASS | Import/export appeared in the toolbar; no inline import/export block was rendered under the table. |
| Todo/work/admin signals | PASS | Todo route used independent todo layout; work route mounted work dashboard signals; system admin route included organization/module/flow/dictionary/work config/SSO/AI Agent/log entries. |
| Console errors | PASS | Browser console error count: 0. |

The in-app browser click bridge failed once when trying to click the second table row because the click coordinate resolved outside an element in the automation layer. The DOM and snapshot evidence still confirm the runtime row-click contract: `tr.clickable-row`, `data-row-click-target=vehicleDetailDrawer`, selected-row state, and right detail panel are rendered.

## Four-Role Permission Matrix

| Actor | Platform Workbench | Platform Backend | System Runtime | System Backend | P0/P1 Result |
|---|---|---|---|---|---:|
| `platform_admin_root` | Allowed | Allowed by built-in root | Requires system switch/member mapping | Requires system member role | PASS |
| `platform_member` | Allowed | Hidden/disabled unless role grants backend menu | Requires system switch/member mapping | Hidden/disabled unless role grants backend menu | PASS |
| `sys_admin_vehicle` | Allowed if platform account exists | Platform role only | Allowed after `SystemSwitchContext` | Allowed by system admin role | PASS |
| `che` | Allowed if platform account exists | Hidden/disabled | Allowed after `SystemSwitchContext` within data scope | Hidden/disabled | PASS |

Supporting evidence:

- `docs/evidence/permission-matrix.md`
- `docs/evidence/backend-integration-smoke.md`
- `docs/evidence/e2e-che-script.raw.json`

## Traceability

Representative trace IDs:

- Login: `trc_17897d82-9f49-41d0-a697-978ff31a128f`
- Register/create system: `trc_42d0521a-a24e-4f46-a507-1fb236f8701e`
- System switch: `trc_1a3a5a11-6fb2-4706-a581-31e4b6d6b783`
- Tenant switch: `trc_23872df5-ee19-4fc3-9f67-6bc9e5adb2f7`
- Module publish check: `trc_53550a74-2672-4cf9-9f98-9cb1dbe9db05`
- Flow simulation: `trc_65d3bd90-b0af-4443-b83a-bf24b524a8fc`
- Export selected: `trc_02091283-6979-47d8-a9ad-89ca1526f4e4`
- Todo action: `trc_ff06d498-bd01-4ed8-a350-caa26d9d9d23`
- Message search: `trc_be5c8b8a-e70b-4db3-be18-36a0d38c1007`
- Daily report auto draft: `trc_9440c2c3-8b0a-4c13-86b9-92f60691f8d1`

## Residual Notes

- Current frontend is still a contract-first static shell. Route access and mock shell roles must be rebound to real auth/effective-permission APIs in the next implementation slices.
- Server-side E2E evidence is the authority for role/data-scope enforcement in this task.
- No P0/P1 blocker was found in the G10 script.
