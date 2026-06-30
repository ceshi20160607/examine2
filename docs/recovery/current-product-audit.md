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

Current final engineering status: `PASS`.

Evidence:

- `scripts/recovery-r5-final-user-script.ps1` was rerun after R20 and R22 were included, and `docs/evidence/recovery/r5-final-release-result.json` reports `PASS`.
- `scripts/recovery-r21-final-usable-system-audit.ps1` reports J0-J7 all `PASS`.
- R22 proves deployed platform-admin operations governance for health, feature flag, quota, rate-limit, backup task, restore drill, archive restore, deployment rollback dry-run, and API cache policy.
- `gates.user_script_passed=false` until the user personally verifies or signs off.

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

Engineering recovery has accepted R0 through R22 as area-level API/release/browser evidence. R5 final orchestration was rerun after R20 and R22 were included, and R21 final usable-system audit now reports J0-J7 all `PASS`.

The final product goal remains user-signoff pending:

- `gates.user_script_passed=false` until the user personally verifies or signs off.
- Engineering gate: `REC-P0-024 Final Usable System Journey Gate` is accepted by `docs/evidence/recovery/r21-final-usable-system-audit-result.json`.

## Audit Procedure

For every remaining non-OK row:

1. Add or update a task card in `docs/recovery/p0-task-cards.md`.
2. Define the generated-vs-coded boundary before coding.
3. Implement only the smallest business loop that closes the row.
4. Prove backend persistence, frontend usability, permissions, state transitions, browser behavior, and cleanup.
5. Add the script to R5 only after its standalone smoke passes.
