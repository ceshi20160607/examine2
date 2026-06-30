# Prototype To Implementation Matrix

Time: 2026-06-29 Asia/Shanghai

## Rule

The approved prototype is not a visual suggestion. For P0 work it is a functional contract. Each prototype area must map to:

- task card
- frontend implementation
- backend business API
- generator/base API usage
- database tables
- permission rule
- acceptance script

If a prototype area cannot be mapped, coding stops for that area until the task card is clarified.

## Matrix

| Prototype Area | Task Card | Frontend Implementation | Backend Business API | Generator/Base Usage | Data Tables | Acceptance |
|---|---|---|---|---|---|---|
| Login/register/password reset | REC-P0-001, REC-P0-017, REC-P0-018, REC-P0-019 | Auth pages, token state, real login session browser evidence, password reset request/confirm state, register-first-use browser initialization evidence | Auth/account/token/health/password reset ticket/register bootstrap | Account base persistence; Redis reset ticket state | account, role, login audit, Redis token/reset ticket, system, tenant, department, member binding | Login browser/API script plus R14 real UI login smoke; R15 password reset browser/API smoke; R16 register-first-use browser smoke |
| Platform workspace | REC-P0-002, REC-P0-007, REC-P0-016 | Platform shell/workbench plus responsive shell audit | Platform todos/messages/systems | Platform base reads | platform system/todo/message/log | Role shell screenshots/API smoke plus R13 responsive smoke |
| Platform admin | REC-P0-002, REC-P0-007, REC-P0-022 | Platform admin pages plus aggregated list pagination | Systems, roles, SSO, model auth, health, logs | Base CRUD for config tables | platform config tables | Admin API smoke plus R19 admin pagination browser smoke |
| System switch | REC-P0-003 | Switcher and state redraw | SystemSwitchContext | System/member/role base reads | system, tenant, member, binding | Multi-system switch script |
| Tenant switch | REC-P0-003 | Tenant switcher and state redraw | Tenant list/create/switch | Tenant/member base reads | tenant, member, role binding | Multi-tenant script |
| System business shell | REC-P0-002, REC-P0-004, REC-P0-005, REC-P0-016, REC-P0-020 | Module group/nav/list/detail shell plus responsive shell audit and normal-member real-login runtime evidence | Module nav/runtime schema | Module base reads | module, field, publish version | Published module appears plus R13 responsive smoke plus R17 normal-member browser smoke |
| System admin | REC-P0-002, REC-P0-004, REC-P0-007, REC-P0-015, REC-P0-016, REC-P0-017, REC-P0-019, REC-P0-022, REC-P0-023 | System admin pages plus fresh-system initialization baseline, compact narrow navigation, real-login session evidence, register-first-use initialization routing, aggregated list pagination, and flow canvas designer | Org, role, module, dict, flow, SSO, Agent, logs; platform-create and register-with-system default department bootstrap | Base CRUD for config | system admin config tables, department/member/tenant/flow node/flow edge | R4 admin breadth, R12 fresh-system initialization smoke, R13 responsive smoke, R14 real session smoke, R16 register-first-use smoke, R19 admin pagination browser smoke, and R20 flow canvas designer smoke |
| Module configuration | REC-P0-004, REC-P0-015, REC-P0-022 | Module builder with filter/apply/reset, left module list, publish actions, left/middle/right fixed-type field builder, and module-list pagination | Module config/publish APIs | Module base CRUD | module group/module/field/scene/action/version | R2 publish smoke plus R12 deployed desktop/mobile browser evidence plus R19 pagination smoke |
| Flow configuration | REC-P0-006, REC-P0-023 | System-admin flow designer with node library, canvas nodes, edge editor, property panel, save, simulation, publish-check, and publish readiness | Flow definition node-library/canvas/simulate/publish-check/publish APIs | Flow base persistence only | flow definition/node/edge/snapshot/simulation log | R3 runtime approval smoke plus R20 deployed flow canvas designer smoke |
| Runtime list/detail | REC-P0-005, REC-P0-020, REC-P0-021 | Runtime list/filter/detail/draft plus frontend form create/readback from a real normal-member login session; mobile action/filter/detail containment gate | Runtime record APIs | Dynamic base layer | dynamic record/value/history/draft/sequence | CRUD/readback script plus R17 normal-member browser form smoke plus R18 mobile containment smoke |
| Approval sidebar | REC-P0-006 | Detail approval sidebar/actions | Flow runtime/approval APIs | Flow base layer | flow instance/task/action/history | Submit/approve script |
| Todo center | REC-P0-006, REC-P0-011 | Todo type tree/list/filter/action | Todo search/action APIs | Todo base layer | todo/task | Todo/message center smoke and browser evidence |
| Message center | REC-P0-006, REC-P0-011 | Message stream/filter/read/archive | Message search/read/archive APIs | Message base layer | message/delivery log | Todo/message center smoke and browser evidence |
| Work management | REC-P0-010, REC-P0-016, REC-P0-017 | Dashboard/project/plain task/daily report plus responsive non-clipped work layout and real-login session evidence | Work project/task/report/dashboard/kanban APIs | Work base layer | work project/task/report/comment/event | R7 work-management smoke and browser evidence plus R13 responsive smoke plus R14 real session smoke |
| Data source management | REC-P0-008, REC-P0-022 | System admin data-source panel with pagination | Data source list/create/detail/update/check APIs | App base data-source persistence | un_system_data_source | R6 data-source smoke and browser evidence plus R19 pagination smoke |
| SSO/no-member | REC-P0-014 | Identity provider/system SSO/no-member UI | SSO/no-member APIs | SSO base layer | identity provider, policy, binding, request | R9 SSO/no-member lifecycle smoke and browser evidence |
| OpenAPI/upload/import-export | REC-P0-012, REC-P0-022 | External app/upload/import-export UI plus OpenAPI app pagination | OpenAPI/upload/task APIs | App/upload base layer | app, secret, upload, task file | R10 OpenAPI/upload/import-export smoke and browser evidence plus R19 pagination smoke |
| AI Agent | REC-P0-013, REC-P0-022 | Platform/system/work Agent UI plus Agent authorization/policy pagination | Agent auth/policy/session/confirm APIs | Agent base layer | agent policy/session/audit/secret | R11 Agent scope/confirmation/audit smoke plus R19 pagination smoke |
| Release/deployment | REC-P0-008, REC-P0-017, REC-P0-020, REC-P0-021, REC-P0-022 | Production assets/config, real-login browser session gate, normal-member deployed runtime usability gate, runtime mobile containment gate, and admin pagination gate | Health/server scripts | None | External DB/Redis | Package final script plus R14 real login smoke plus R17 normal-member browser smoke plus R18 mobile containment smoke plus R19 admin pagination smoke |
| Final usable system journey | REC-P0-024 | Four-shell journey across platform workspace/admin, system admin, and system business runtime | Existing business APIs from R2-R20 plus release health | Generated CRUD is evidence plumbing only | Full cross-domain evidence set | R21 final usable system audit: J0 release health, J1 register first system, J2 admin builds app, J3 normal daily work, J4 approval/notification, J5 admin depth, J6 external/import-export, J7 operations/maintenance |
| Final requirement coverage | REC-P0-026 | All production routes indirectly; coverage status prevents final completion claims | All coded business APIs indirectly | Generated CRUD cannot close coverage rows | Requirement ledger and evidence files | R23 coverage audit and gap report; final completion blocked until every row is PROVEN or USER_EXCLUDED |
| Pages/home/page designer/advanced surfaces | REC-P0-027 | System home page configuration and runtime dashboard proven; page designer API/runtime loop proven; visual-density pass reduces system dashboard/admin stacking; platform home, browser page-designer interaction/preview, broader visual/wording audit, and advanced capability split cards still open | Home page config APIs proven; module page draft/save/list/publish-check/publish/runtime readback APIs proven; advanced capability APIs still split by task | Generated page/config entities are persistence plumbing only | Home page config and module page designer snapshots use `un_module_work_config`; advanced capability tables still pending per split task | R24 home page config smoke PASS, R24 page designer API/runtime smoke PASS, and R24 visual browser audit evidence captured; FRC-1 remains in progress for page designer browser/visual breadth and advanced capabilities |

## Required Evidence Per Row

For each row that moves to `OK`, store evidence under `docs/evidence/recovery/` with:

- source prototype reference
- implemented route and screenshot
- API request/response
- database readback or API readback
- permission positive and negative cases
- final label from `current-product-audit.md`

## Prohibited Shortcuts

- Do not close a row with only controller/service files.
- Do not close a row with only a screenshot.
- Do not close a row with only generated CRUD.
- Do not close a row with only a smoke route check.
- Do not close a row with browser local data or hard-coded records.

## R0 Static Audit Mapping

| Finding | Affected Prototype Area | Recovery Task |
|---|---|---|
| System admin and platform admin sidebar entries target shared mixed panels. | Platform admin, system admin, module config, flow/dict, SSO/log, work/Agent | `REC-P0-002`, `REC-P0-007`, `REC-P0-009` |
| P0 actions use `window.prompt` / `window.confirm` instead of task-specific forms, drawers, and result panels. | Platform admin, system admin, approval, no-member, work, SSO, OpenAPI, Agent | `REC-P0-006`, `REC-P0-007`, `REC-P0-009` |
| Several previous/next pagination controls have no data-changing click behavior. | Platform workspace, platform admin, system admin, message center, work management | `REC-P0-007`, `REC-P0-009` |
| No dedicated tenant switch UI was found. | Tenant switch | `REC-P0-003` |
| No fresh release package exists in the current workspace. | Release/deployment, auth baseline | `REC-P0-001`, `REC-P0-008` |
