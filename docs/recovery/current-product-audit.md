# Current Product Audit

Time: 2026-06-29 Asia/Shanghai

## Purpose

This file is the recovery audit map for the user-reported issue: the standalone product looked crowded, mixed unrelated pages, and several previously claimed-complete areas were not actually usable end to end.

Recovery completion is strict: a row is `OK` only when layout, API binding, persistence, permissions, state handling, repeatable script evidence, and deployed-browser evidence all pass for the business outcome.

## Final Usable System Gate

Area-level `OK` rows are not the final product goal.

The top-level acceptance source is now `docs/recovery/final-usable-system-acceptance.md`. The final goal is accepted only when J0-J7 pass together:

- J0 release health
- J1 register first system
- J2 admin builds business app
- J3 normal user daily work
- J4 approval and notification
- J5 admin configuration depth
- J6 external and import/export
- J7 operations and maintenance

Current final engineering status: `REOPENED_PARTIAL`.

Evidence:

- `scripts/recovery-r5-final-user-script.ps1` was rerun after R20 and R22 were included, and `docs/evidence/recovery/r5-final-release-result.json` reports `PASS`.
- `scripts/recovery-r21-final-usable-system-audit.ps1` reports J0-J7 all `PASS`.
- R22 proves deployed platform-admin operations governance for health, feature flag, quota, rate-limit, backup task, restore drill, archive restore, deployment rollback dry-run, and API cache policy.
- `gates.user_script_passed=false` until the user personally verifies or signs off.

2026-07-01 V7 update:

- The previous area-level `OK` rows and R21/R5 `PASS` are now historical engineering evidence only.
- User feedback says the deployed product still does not meet the final "human-usable system" target.
- Framework V7 is active and requires a fresh human role-journey gate before claiming progress toward final acceptance.
- Active next task: `REC-P0-057 FRC-6 Human Acceptance Pass Fresh Closure`.
- Required rows: `REQ-2.1`, `REQ-4.1`, `REQ-4.2`, `REQ-6.2`.

2026-07-02 V8 update:

- User feedback now says repeated local fixes can become unlimited and still fail to produce a coherent system.
- The active correction is a flow-first rebuild contract: no token -> login/register/password recovery -> role routing -> four shells -> system switch -> first-use configuration -> runtime/workflow/OpenAPI/AI/operations.
- Active flow source: `docs/framework/final-system-flow-blueprint.md`.
- Latest accepted flow task: `REC-P0-062 Platform And System Shell Landing Flow Closure`.
- Latest accepted admin first-use task: `REC-P0-059 FRC-2/FRC-6 Admin Configuration Human First-Use Closure`.
- Latest accepted page/visual/page-designer task: `REC-P0-068 Page Visual Designer Fresh Evidence Closure`.
- Latest accepted requirement evidence promotion task: `REC-P0-069 Requirement Evidence Promotion And Residual Gap Decision`.
- Latest accepted no-code configuration residual depth task: `REC-P0-070 No-Code Configuration Residual Depth Closure`.
- Latest accepted no-code frontend binding task: `REC-P0-071 No-Code Frontend Binding And Hierarchy Residual Closure`.
- Latest accepted runtime file/import-export/error-state task: `REC-P0-072 Runtime Daily-Use File Import Export Error-State Residual Closure`.
- Latest accepted workflow/todo/message residual task: `REC-P0-073 Workflow Todo Message Integration Error-State Residual Closure`.
- Latest accepted OpenAPI/AI external-service residual task: `REC-P0-074 OpenAPI AI External-Service Error-State Residual Closure`.
- Latest accepted auth/shell entry role-flow residual task: `REC-P0-076 Auth Shell Entry Role Flow Residual`.
- Latest accepted operations/logs/release residual task: `REC-P0-075 Operations Logs Release Maintenance Error-State Residual Closure`.
- Latest accepted final candidate refresh task: `REC-P0-077 Final Requirement Candidate Refresh After Residual Closure`.
- Active next task: `REC-P0-078 FRC-1 Product Surface Human Acceptance Residual Closure`.
- R62 specifically closed a browser-discovered stale-token/empty-account bug where the platform shell could render as "鏈櫥褰?; business shells now require initialized account state.
- R76 specifically closed remaining entry/shell foundation drift: platform-role login no longer auto-switches into a system, stale `/platform/dashboard` landing is normalized to `/platform`, platform/system admin surfaces are standalone admin shells, registration still enters system admin, and password recovery exposes a return-to-login action.

2026-07-06 R79 update:

- R78 is accepted as product-surface engineering evidence only after release/static/framework checks and fresh R68/R75/R77 child evidence passed.
- The active next task is `REC-P0-079 Final User Verification Readiness And Continuation Contract`.
- R79 is accepted as handoff engineering evidence. It does not code new product surfaces. It makes the current project structure, final target, developed evidence, user verification path, and future change workflow durable on disk.
- Future coding must continue through role journey -> requirement row -> task card -> fix batch -> implementation -> deterministic script evidence.
- `gates.user_script_passed=false` remains the final user verification boundary.

2026-07-06 R80 update:

- The active next task is `REC-P0-080 Live User Trial Workspace Seed`.
- R80 creates a retained live trial workspace with credentials, routes, runtime records, and workflow/todo/message data so human review can start from the running product instead of reports only.
- R80 is engineering evidence only. It must not promote coverage rows or set `gates.user_script_passed=true`.
- Future changes still use role journey -> requirement row -> task card -> fix batch -> implementation -> deterministic script evidence -> user verification.
- R80 PASS is now accepted as live trial engineering evidence: runtime trial system `1118`, workflow trial system `1121`, release/framework/static checks passed, coverage remains notClosed `45`, and user signoff remains false.

2026-07-06 R81 update:

- The active next task is `REC-P0-081 Trial Login And Role Use Audit`.
- R81 uses the retained R80 accounts from the deployed login page instead of seeding another workspace.
- R81 must verify platform admin entry, normal runtime record visibility, readonly create denial, requester workflow terminal visibility, approver todo/message surfaces, browser containment, and API/readback permission evidence.
- R81 remains engineering evidence only. It must not promote coverage rows or set `gates.user_script_passed=true`.


2026-07-07 R81 acceptance and R82 activation update:

- R81 is accepted as deployed login/role engineering evidence only: result `docs/evidence/recovery/r81-trial-login-role-use-audit-result.json` reports `status=PASS`, browser results `8`, overflow `0`, blockers `0`, coverage notClosed `45`, and `gates.user_script_passed=false`.
- The active next task is `REC-P0-082 Visible Copy Encoding And Trial Usability Cleanup`.
- R82 targets the next human-usability gap exposed by the trial paths: readable visible copy on login, shell, runtime, readonly, workflow, todo, and message routes without weakening role permissions or claiming user signoff.

## Audit Labels

| Label | Meaning |
|---|---|
| `OK` | Layout, API binding, persistence, permission, and acceptance evidence pass. |
| `UI_BROKEN` | Entry exists but layout, shell separation, visual hierarchy, or interaction structure is wrong. |
| `STATIC_ONLY` | Page exists but is static or mostly local data. |
| `API_ONLY` | Backend exists but frontend is not correctly bound to it. |
| `FAKE_DATA` | Production path still uses mock/sample/stub/prototype data. |
| `FLOW_BROKEN` | Screens exist, but the role cannot finish the business flow. |
| `PERMISSION_BROKEN` | UI or backend allows/blocks actions incorrectly for the role. |
| `STATE_BROKEN` | Empty, loading, disabled, validation, failure, or async task state is missing or fake. |
| `MISSING` | Required entry, page, API, table behavior, or script is absent. |
| `USER_DECISION_REQUIRED` | Existing files do not define final behavior clearly enough to code safely. |
| `UNKNOWN` | Not audited yet. |

## Current Matrix

| Area | Expected User Outcome | Current Label | Evidence / Remaining Closure |
|---|---|---:|---|
| Auth and default admin | `admin / 123123aa` logs in; token is Redis-backed; health requires DB/schema/Redis UP; register and password reset are real account flows. | `OK` | R14 closes real login session evidence. R15 closes password reset account closure. R16 closes register-first-use browser evidence: deployed browser submits the real register form, lands on the new system dashboard, sees first-use initialization, enters system backend, and cleanup succeeds. Evidence: `docs/evidence/recovery/r14-real-login-session-state-2026-06-29.md`, `docs/evidence/recovery/r15-password-reset-account-closure-2026-06-29.md`, `docs/evidence/recovery/r16-register-first-use-browser-closure-2026-06-29.md`. |
| Four shells | Platform workspace, platform admin, system business, and system admin are visibly separate and not stacked. | `OK` | R13 closes the reopened responsive shell issue: deployed desktop/mobile browser audit covers platform workspace, platform admin, system business pages, and system admin module management with no horizontal overflow and compact narrow admin navigation. Evidence: `docs/evidence/recovery/r13-cross-shell-responsive-usability-2026-06-29.md`. |
| Platform workspace | Platform member/admin sees platform workspace actions without stacked unrelated panels. | `OK` | Declutter and first-use bootstrap evidence prove explicit empty state, page-based system creation, and real `SystemSwitchContext`. Evidence: `docs/evidence/recovery/r1-standalone-ui-declutter-2026-06-27.md`, `docs/evidence/recovery/r1-standalone-empty-bootstrap-2026-06-27.md`. |
| Platform admin | Platform admin manages systems, tenants, platform roles, identity providers, model authorizations, health, and logs. | `OK` | R4 admin breadth smoke and browser evidence prove API-backed admin breadth and one selected panel per sidebar target. Evidence: `docs/evidence/recovery/r4-admin-breadth-2026-06-27.md`. |
| System switch | Switching system creates current context and redraws brand, menus, permissions, and data scope. | `OK` | R1 context smoke and R2 tenant/business redraw prove context persistence and redraw. Evidence: `docs/evidence/recovery/r1-release-context-smoke-2026-06-27.md`, `docs/evidence/recovery/r2-tenant-business-redraw-2026-06-27.md`. |
| Tenant switch | Multi-tenant system switch refreshes tenant/member/role/data-scope/business data. | `OK` | R2 tenant switch and tenant business redraw prove tenant A/B context and data isolation. Evidence: `docs/evidence/recovery/r2-tenant-switch-smoke-2026-06-27.md`, `docs/evidence/recovery/r2-tenant-business-redraw-2026-06-27.md`. |
| System admin initialization | System admin configures org, roles, modules, dictionaries, flows, work/integration setup. | `OK` | R19 closes admin aggregated pagination: system-admin page-number state is wired into members, roles, modules, flows, dictionaries, data sources, OpenAPI apps, Agent policies, and logs. Browser evidence proves role-management page 1 -> page 2 changes real rows with no unsupported-pagination reason. Evidence: `docs/evidence/recovery/r19-admin-aggregated-pagination-2026-06-29.md`. |
| Module config and publish | Admin creates module fields/scenes/actions, publishes, and business shell shows the module. | `OK` | R19 exposes and wires module-list pagination in the module builder so larger systems can reach modules beyond the first API page. R12 remains the module builder publish/usability evidence and R19 closes the reopened paged-list gap. Evidence: `docs/evidence/recovery/r12-module-builder-usability-2026-06-29.md`, `docs/evidence/recovery/r19-admin-aggregated-pagination-2026-06-29.md`. |
| Runtime records | User creates, lists, filters, opens detail, edits, sees history, drafts, sequence, attachments. | `OK` | R3 and R17 prove runtime API, persistence, normal-member real-login create/readback, and permission denial. R18 closes the reopened mobile containment gap: the deployed 390px browser keeps runtime actions, filters, panels, create form, and saved list/detail inside the viewport with `maxOverflowX=0`; direct system-admin URL remains denied. Evidence: `docs/evidence/recovery/r3-runtime-approval-smoke-2026-06-27.md`, `docs/evidence/recovery/r17-normal-member-real-login-runtime-2026-06-29.md`, `docs/evidence/recovery/r18-runtime-mobile-action-containment-2026-06-29.md`. |
| Workflow and approval | Admin configures flow canvas; submit approval creates instance, pending task, todo, message, approval actions, and closure. | `OK` | R3 proves runtime approval closure. R20 closes system-admin flow canvas designer: deployed browser real-login evidence configures nodes/edges/properties, saves canvas, runs simulation and publish-check, then API readback confirms persisted canvas and snapshot. Evidence: `docs/evidence/recovery/r3-runtime-approval-smoke-2026-06-27.md`, `docs/evidence/recovery/r20-flow-canvas-designer-2026-06-29.md`. |
| Todo center | Todo workbench supports pending/handled state, filters, row action, and business jump. | `OK` | R8 closes todo center. Evidence: `docs/evidence/recovery/r8-todo-message-center-2026-06-27.md`. |
| Message center | Message stream supports filters, read/archive/paging, and row state changes. | `OK` | R8 closes message center. Evidence: `docs/evidence/recovery/r8-todo-message-center-2026-06-27.md`. |
| Work management | Dashboard, project tasks, plain tasks, and daily reports are separate tabs and API-backed. | `OK` | R7 closes API/state/runtime behavior, and R13 closes the narrow-layout clipping regression with deployed desktop/mobile browser evidence. Evidence: `docs/evidence/recovery/r7-work-management-2026-06-27.md`, `docs/evidence/recovery/r13-cross-shell-responsive-usability-2026-06-29.md`. |
| Data source management | System admin creates, tests, publishes, and reads data-source configuration. | `OK` | R6 closes data-source management. Evidence: `docs/evidence/recovery/r6-data-source-2026-06-27.md`. |
| SSO/no-member | Identity provider, system policy, precheck, binding, and no-member lifecycle are persisted and permission-checked. | `OK` | R9 closes SSO/no-member lifecycle: no-member request creation without target member, incomplete approval remains blocked, full approval creates account-member/role/SSO bindings, requester can switch only after approval, and browser page shows approved binding evidence. Evidence: `docs/evidence/recovery/r9-sso-no-member-2026-06-27.md`. |
| OpenAPI/upload/import-export | External app, secret ref, call log, upload, attachment, import/export async tasks are real and usable from UI. | `OK` | R10 closes the flow: OpenAPI app create/readback without plaintext secret exposure, secret rotation task, external record create/search/detail through `/openapi`, call-log readback, real CSV upload, import precheck/confirm tasks with result files, export task with result file, and release same-origin `/openapi` proxy. Evidence: `docs/evidence/recovery/r10-openapi-upload-import-export-2026-06-29.md`, `docs/evidence/recovery/r5-final-release-result.json`. |
| AI Agent | Platform/system/work Agent scopes, policies, sessions, confirmations, and audit logs are separated. | `OK` | R11 closes Agent scope and confirmation boundaries: model authorization SecretRef readback, platform Agent system-scope rejection, system policy publish-check, system write confirm/reject, work draft confirmation, audit logs, and normal-member policy-management denial. Evidence: `docs/evidence/recovery/r11-ai-agent-scope-confirmation-2026-06-29.md`, `docs/evidence/recovery/r5-final-release-result.json`. |
| Release package | Package starts with external config, health gates DB/schema/Redis, same-origin frontend uses `/api` and `/openapi`. | `OK` | R5 was rerun after R20 and R22 were included. Final result reports `status=PASS`, `stepsFailed=0`; R21 also reports J0 release health PASS. Evidence: `docs/evidence/recovery/r5-final-release-result.json`, `docs/evidence/recovery/r21-final-usable-system-audit-result.json`. |
| Operations and maintenance | Platform admin can run health, feature flags, quotas, rate limits, backup task, restore drill, archive restore, deployment rollback dry-run, cache policy, and deployer can verify release scripts/assets. | `OK` | R22 closes operations/maintenance: deployed browser real-login opens platform admin configuration, clicks operations governance buttons, sees taskId/traceId/cache/deployment results, mobile overflow is 0, release verification passes, and packaged `server.sh` exposes start/stop/restart/status/health. Evidence: `docs/evidence/recovery/r22-ops-maintenance-2026-06-29.md`. |

## Current Boundary

Engineering recovery has accepted many R-batches as area-level API/release/browser evidence. R5/R21/R56 and related scripts remain useful engineering evidence, but they no longer prove final acceptance after the user's deployed-use feedback reopened the human-usable system gate.

The final product goal remains open:

- `gates.user_script_passed=false` until the user personally verifies or signs off.
- Engineering gate status: historical R21 final usable-system audit is downgraded to engineering evidence.
- Latest accepted configured runtime task: `REC-P0-063 B1/B2 Configured Runtime First-Use And Daily Business Closure`.
- Latest accepted workflow/todo/message task: `REC-P0-064 C4/B4/B5 Workflow Todo Message First-Use And Closure`.
- Latest accepted OpenAPI/AI task: `REC-P0-065 E1/AI2 OpenAPI Assistant External-Service First-Use Closure`.
- Latest accepted operations/logs/release task: `REC-P0-066 O1/O2/O3 Operations Logs Release Maintenance First-Use Closure`.
- Latest accepted final candidate refresh task: `REC-P0-067 Final Role Journey And Requirement Acceptance Candidate Refresh`.
- Latest accepted page/visual/page-designer task: `REC-P0-068 Page Visual Designer Fresh Evidence Closure`.
- Latest accepted requirement evidence promotion task: `REC-P0-069 Requirement Evidence Promotion And Residual Gap Decision`.
- Latest accepted no-code configuration residual depth task: `REC-P0-070 No-Code Configuration Residual Depth Closure`.
- Latest accepted no-code frontend binding task: `REC-P0-071 No-Code Frontend Binding And Hierarchy Residual Closure`.
- Latest accepted runtime file/import-export/error-state task: `REC-P0-072 Runtime Daily-Use File Import Export Error-State Residual Closure`.
- Latest accepted workflow/todo/message residual task: `REC-P0-073 Workflow Todo Message Integration Error-State Residual Closure`.
- Latest accepted OpenAPI/AI external-service residual task: `REC-P0-074 OpenAPI AI External-Service Error-State Residual Closure`.
- Latest accepted auth/shell entry role-flow residual task: `REC-P0-076 Auth Shell Entry Role Flow Residual`.
- Latest accepted operations/logs/release residual task: `REC-P0-075 Operations Logs Release Maintenance Error-State Residual Closure`.
- Latest accepted final candidate refresh task: `REC-P0-077 Final Requirement Candidate Refresh After Residual Closure`.
- Latest accepted product-surface task: `REC-P0-078 FRC-1 Product Surface Human Acceptance Residual Closure`.
- Latest accepted live trial workspace task: `REC-P0-080 Live User Trial Workspace Seed`.
- Active next task: `REC-P0-082 Visible Copy Encoding And Trial Usability Cleanup`.
- Active gate: `REC-P0-082 Visible Copy Encoding And Trial Usability Cleanup`.
- Active framework: `docs/framework/framework-v8-flow-blueprint-rebuild-contract.md`.
- Active flow source: `docs/framework/final-system-flow-blueprint.md`.

## Audit Procedure

For every remaining non-OK row:

1. Add or update a task card in `docs/recovery/p0-task-cards.md`.
2. Define the generated-vs-coded boundary before coding.
3. Implement only the smallest business loop that closes the row.
4. Prove backend persistence, frontend usability, permissions, state transitions, browser behavior, and cleanup.
5. Add the script to R5 only after its standalone smoke passes.

## 2026-07-08 R93 Product Audit Addendum

User feedback identifies a live information-architecture blocker: platform Application currently behaves like system entry, while Flow and Application should be separate platform modules. This makes the product feel confusing even if individual engineering slices pass. R93 must correct platform navigation, copy, page structure, and DOM/source markers before more feature-slice acceptance.

Audit rule added: platform Application may show application/authorization/configuration objects and actions, but it must not render the system-switch panel, system cards, or primary “enter system” actions. Platform Flow must read as its own module. System entry remains on `/platform` workbench/system switch only.

R93 engineering acceptance update: the current release passed scripts/recovery-r93-platform-flow-app-ia-boundary.ps1 on http://127.0.0.1:18132. The live IA boundary is now: platform Application is application/authorization configuration, platform Flow is independent, and /platform keeps system entry. Final user signoff remains open; continue with R92 as the next active feature slice.

## 2026-07-08 R92 Acceptance And R94 Audit Gate

R92 engineering acceptance update: the current release passed `scripts/recovery-r92-workflow-advanced-node-publish-impact.ps1` on `http://127.0.0.1:18132`. The workflow designer now has deployed evidence for advanced nodes, property readback, publish impact, simulation, runtime todo/message terminal states, permission negatives, and mobile containment. R73 child evidence passed fresh, and R93 platform Flow/Application IA regression also passed.

Active next task: `REC-P0-094 Fresh Deployed Role Journey Audit After R92/R93`. The product goal remains open because user signoff is still false and final coverage rows remain partial. R94 must inspect the current deployed product across role journeys before the next repair is selected.

## 2026-07-08 R97 C1 fresh system initialization path

R97 PASS on the deployed release proves a fresh empty system now directs system admins to initialization instead of mixing runtime panels into the dashboard. It remains engineering evidence only; R98 is active for final user trial/signoff readiness and gates.user_script_passed=false.

## 2026-07-08 R98 Trial Readiness Addendum

R98 prepared the current human trial path on the deployed release. Evidence: `docs/evidence/recovery/r98-final-user-trial-readiness-and-signoff-path-result.json`, checklist `docs/evidence/recovery/r98-user-trial-checklist-2026-07-08.md`, and browser audit `docs/evidence/recovery/screenshots/r98-final-user-trial-readiness-and-signoff-path/final-user-trial-readiness-browser-audit.json`.

Audit result: browser smoke started from the deployed login page for admin, runtime normal/readonly, workflow requester, and workflow approver routes; resultCount `11`, blockers `0`, warnings `0`, overflow `0`. This improves trial readiness but does not close final acceptance. Current active gate moves to R99: explicit user signoff or feedback intake before the next repair.
## 2026-07-08 R99/R100 Flow Application Depth Selection

R99 PASS converted the open post-trial gate into a concrete next repair without claiming user acceptance. It confirmed R98 PASS/checklist, `gates.user_script_passed=false`, coverage notClosed `45`, and selected R100 because `temp_flow.md` P2/P3 require platform Flow/Application depth beyond the R93/R95 boundary fix.

Active next task: `REC-P0-100 Platform Flow Application Workbench Depth And Action Clarity Closure`. The product audit rule is now stronger: `/platform/flow` must expose task-grade Flow rows, detail, run feedback, retry/compensation and trace evidence; `/platform/apps` must expose authorization/application rows, request/change ids and platform feedback without rendering system-entry actions.
## 2026-07-08 R100 Platform Flow Application Depth Acceptance

R100 PASS on the deployed release deepened platform Flow and Application from boundary-correct pages into task-grade workbench surfaces. Flow now exposes rows, detail, runBatch/traceId, retry and compensation feedback. Application now exposes authorization rows, requestId, authorizationChangeId, authorization detail, request/change feedback, and no system-entry action leakage. Browser desktop/mobile blockers `0`, maxOverflow `0`.

This remains engineering evidence only. R101 is active because R100 rows/actions are frontend contract samples; the next step must persist Flow/Application objects and read them back through APIs with permission positives/negatives.
## 2026-07-08 R101 Platform Flow Application API Readback Acceptance

R101 PASS on the deployed release proves platform Flow and platform Application are now API-backed persisted platform objects instead of R100 frontend contract samples. Flow create/update/run/retry/compensation, Application authorization request/adjust/disable, platform todo/message/log feedback, admin/member permission boundaries, and the `NO_SYSTEM_BUSINESS_WRITE` boundary all passed with deployed browser containment blockers `0` and maxOverflow `0`.

Evidence: `docs/evidence/recovery/r101-platform-flow-application-api-readback-result.json`, summary `docs/evidence/recovery/r101-platform-flow-application-api-readback-2026-07-08.md`, and browser audit `docs/evidence/recovery/screenshots/r101-platform-flow-application-api-readback/platform-flow-application-api-readback-browser-audit.json`.

This remains engineering evidence only. Current active task moves to `REC-P0-102 Final User Trial Refresh After Platform Flow Application Readback` so the user trial/signoff path is refreshed on the current deployment before further feature coding.
## 2026-07-08 R102 Final User Trial Refresh After R101

R102 PASS on the deployed release refreshed the current user trial path after R101. It verified release health, retained trial account login/readback, readonly permission denial, workflow terminal/todo readback, R101 Flow/Application API readback, browser Flow/Application/trial routes with blockers `0`, warnings `0`, maxOverflow `0`, framework/static PASS, and coverage notClosed `45`.

This remains engineering evidence only. Active next task moves to `REC-P0-103 Fresh Deployed Human Usability Audit After R102` so the next change is selected from current deployed hierarchy/copy/layout evidence rather than vague continued patching.
