# Recovery Fix Batches

Time: 2026-06-27 Asia/Shanghai

## Execution Rule

Only tasks listed here may be coded during recovery. If a new issue is found, add it here before coding.

The order is intentional: stabilize entry and shell first, then make one core business loop real, then broaden admin and release coverage.

## Batch R0: Product Audit And Task Alignment

Status: accepted

Goal: produce a current, evidence-based map of what is actually usable.

Tasks:

- Audit all rows in `docs/recovery/current-product-audit.md`.
- For each non-OK row, keep or add a task card in `docs/recovery/p0-task-cards.md`.
- Link each task to prototype, frontend, backend, data, permission, and acceptance evidence.
- Stop old broad "coding complete" claims. Use only recovery task status.

Exit criteria:

- Every initial audit row is labeled with evidence.
- No `UNKNOWN` remains for P0 rows.
- No `USER_DECISION_REQUIRED` remains without a recorded user answer.
- `fix-batches.md` contains all open P0 fixes.

Current R0 evidence:

- Static audit evidence: `docs/evidence/recovery/r0-static-audit-2026-06-27.md`
- Repeatable static audit script: `scripts/recovery-r0-static-audit.ps1`
- Navigation/typecheck remediation evidence: `docs/evidence/recovery/r1-navigation-typecheck-2026-06-27.md`
- Interaction/pagination/release package evidence: `docs/evidence/recovery/r1-interactions-pagination-release-2026-06-27.md`
- Health schema contract evidence: `docs/evidence/recovery/r1-health-schema-contract-2026-06-27.md`
- Release context smoke evidence: `docs/evidence/recovery/r1-release-context-smoke-2026-06-27.md`
- Browser shell smoke evidence: `docs/evidence/recovery/r1-browser-shell-smoke-2026-06-27.md`
- Permission smoke evidence: `docs/evidence/recovery/r1-permission-smoke-2026-06-27.md`
- Static audit now passes. Runtime/browser audit was completed by R1/R5 evidence.

## Batch R1: Entry, Health, Release Baseline, And Four Shells

Status: accepted

Partial progress:

- Platform/system admin duplicate sidebar targets are fixed in source.
- Prompt/confirm/alert usage under `frontend/src` is removed.
- Platform todo/message pagination has real page callbacks; remaining no-callback pagination is disabled with an explicit reason.
- Frontend `typecheck` and production `build` pass.
- Release package starts; health now reports `database=UP` and `schema=UP`.
- With local Redis override, release health reports all UP and `scripts/recovery-r1-release-context-smoke.ps1` passes for admin login, platform system create, system switch, current-system persistence, and tenant switch.
- Browser shell smoke passes for platform workbench, platform admin, system dashboard, and system admin with no Loading state, console errors, or failed HTTP responses.
- Permission smoke passes for unauthenticated rejection, normal-user platform-admin API rejection, normal-user system-switch allowance, and admin platform-admin access.
- Default-config release verification now passes against Redis `192.168.0.211:6379`; stale-port startup protection is recorded in `docs/evidence/recovery/r1-release-local-start-2026-06-27.md`.
- Standalone visible clutter regression was reduced: platform dashboard no longer stacks create-system/todos/messages, runtime no-module state no longer renders unavailable toolbar/filter/batch/detail controls, recovery test systems were cleaned from the shared database, and release scripts default to the current local tool paths. Evidence: `docs/evidence/recovery/r1-standalone-ui-declutter-2026-06-27.md`.
- Standalone first-use empty bootstrap now has browser evidence: default `admin` with no switchable systems sees an explicit empty state, creates a system through the page, receives real `SystemSwitchContext`, lands on the system dashboard, and the temporary system is cleaned. Evidence: `docs/evidence/recovery/r1-standalone-empty-bootstrap-2026-06-27.md`.
- Standalone first-use path now lands new systems on the system backend initialization checklist instead of an empty dashboard; direct non-hash `/platform/admin` and `/systems/{systemId}/admin` URLs normalize to hash routes and do not fall back to login when a session exists. Evidence: `docs/evidence/recovery/r1-standalone-empty-bootstrap-2026-06-27.md`.
- R1 is accepted for the recovery baseline: release health, shell separation, permission negative checks, first-use bootstrap, direct-route normalization, and visible clutter reduction are covered by R1/R2/R3/R5 evidence.

Tasks:

- `REC-P0-001 Auth, Health, And Release Baseline`
- `REC-P0-002 Four Shells And Navigation Separation`
- `REC-P0-009 Frontend Interaction Contract Cleanup`

Exit criteria:

- Login and release health are verified from browser and API.
- Platform workspace, platform admin, system business, and system admin screenshots prove shell separation.
- Direct forbidden route checks pass for normal member/platform member boundaries.
- No P0 operation in the R1 shells depends on `window.prompt` or inert pagination controls.

## Batch R2: System/Tenant Context And Admin Initialization

Status: accepted

Partial progress:

- `scripts/recovery-r2-module-publish-smoke.ps1` passes against the standalone local release through `http://127.0.0.1:18131`.
- `scripts/recovery-r2-tenant-switch-smoke.ps1` passes against the standalone local release through `http://127.0.0.1:18131`.
- `scripts/recovery-r2-system-member-permission-smoke.ps1` passes against the standalone local release through `http://127.0.0.1:18131`.
- System admin module initialization now has repeatable evidence for module group, module, dictionary, text/date/select/attachment/auto-number fields, scene, row action, publish-check, publish version, admin schema, runtime schema, and browser visibility in the system business shell.
- Frontend tenant list/switch state and a multi-tenant header selector now have browser evidence: a multi-tenant system shows one tenant selector, switching to tenant B updates `/context/current-system`, and system admin still renders one selected panel after the switch.
- Normal-member permission negatives now have backend and browser evidence: a `SYSTEM_MEMBER` can read runtime navigation, receives HTTP 403 for system-admin member/module/role configuration APIs, and direct browser navigation to system admin renders the no-permission page.
- System initialization now has a visible backend checklist on the system information page with direct actions for organization, roles, modules, flow/dictionaries, and work/integration configuration.
- Non-empty tenant-specific business-data redraw now has API and browser evidence: tenant A and tenant B each publish a tenant-specific module and create a tenant-specific business record; switching tenants changes context member id, module list, runtime record list, and browser-visible records without leaking the other tenant's module or record.
- Evidence: `docs/evidence/recovery/r2-module-publish-smoke-2026-06-27.md`, `docs/evidence/recovery/r2-tenant-switch-smoke-2026-06-27.md`, `docs/evidence/recovery/r2-system-member-permission-smoke-2026-06-27.md`, `docs/evidence/recovery/r2-tenant-business-redraw-2026-06-27.md`.

Tasks:

- `REC-P0-003 System And Tenant Switch Context`
- `REC-P0-004 System Admin Initializes A Usable Module`

Exit criteria:

- Switching systems/tenants refreshes context-dependent UI/API data.
- System admin creates, configures, and publishes a module.
- Published module appears in system business shell.

## Batch R3: Runtime Records And Approval Closure

Status: accepted

Evidence:

- `scripts/recovery-r3-runtime-approval-smoke.ps1` passes against the standalone local release through `http://127.0.0.1:18131`.
- API/business evidence now covers separate requester and approver accounts with a custom runtime role: runtime schema permissions, real file upload, draft attachment readback, record attachment binding/readback, two-batch sequence continuity, record create/search/detail/update/history, approval submit, requester pending-todo total `0`, approver pending-todo total `1`, visible approver message, requester approval denied with HTTP `403`, approver approve action, business record terminal status readback, approval sidebar terminal readback, submit action disabled after terminal approval, pending todo closure, handled todo readback, duplicate approve idempotency, and cleanup.
- Browser evidence now covers the standalone runtime list/detail, approved terminal status, disabled submit approval action, handled todo page, and approval message page with zero console errors, failed requests, or HTTP 4xx/5xx responses.
- Evidence: `docs/evidence/recovery/r3-runtime-approval-smoke-2026-06-27.md`.

Tasks:

- `REC-P0-005 Runtime Record CRUD, Detail, Draft, Sequence, And History`
- `REC-P0-006 Approval, Todo, Message, And Audit Closure`

Exit criteria:

- Normal user creates and reads real records with draft, sequence, history, and permissions.
- Approval creates instance, task, todo, message, and audit evidence.
- Approver actions are idempotent and visible in UI/API readback.

## Batch R4: Admin Breadth And Remaining P0 Surfaces

Status: accepted

Evidence:

- `scripts/recovery-r4-admin-breadth-smoke.ps1` passes against the standalone local release through `http://127.0.0.1:18131`.
- Work config is no longer a fake success: `PATCH /work/config` persists to `un_module_work_config`, and the smoke performs a follow-up `GET /work/config` asserting the saved field code/name are present.
- Browser evidence covers system info, organization, roles, dictionaries, flows, OpenAPI, work config, SSO, Agent, data source, and logs. Each sidebar target renders exactly one admin panel.
- Browser-visible values include `R4 Department 0627205335`, `R4 Role 0627205335`, `R4 Status 0627205335`, `R4 Flow 0627205335`, `R4 OpenAPI 0627205335`, `R4 Priority`, `r4Priority0627205335`, `r4-0627205335.example.test`, and `r4_agent_0627205335`.
- At the R4 checkpoint, data source remained explicitly `寰呮帴鍏 because no data-source backend contract/table existed; R6 later closes this gap with API-backed data-source management.
- Evidence: `docs/evidence/recovery/r4-admin-breadth-2026-06-27.md`.

Tasks:

- `REC-P0-007 Admin Configuration Breadth Smoke`
- Continue `REC-P0-009 Frontend Interaction Contract Cleanup` for admin breadth surfaces if not fully accepted in R1.
- Add separate cards if R0 marks work, SSO, OpenAPI, upload/import-export, or AI Agent as P0-broken.
- Broader module configuration UI interaction coverage is tracked here after R2 accepted the module publish and tenant redraw business outcomes.

Exit criteria:

- Platform/system admin pages are API-backed, not static shells.
- Each admin action either performs real API behavior or is explicitly disabled with reason.

## Batch R5: Final Release And User Script

Status: accepted

Evidence:

- Final orchestration script `scripts/recovery-r5-final-user-script.ps1` passes against the standalone local release through `http://127.0.0.1:18131`.
- Final result file: `docs/evidence/recovery/r5-final-release-result.json`.
- Final evidence summary: `docs/evidence/recovery/r5-final-release-2026-06-27.md`.
- Fresh release package exists at `release/unexamine-0.0.1-SNAPSHOT`.
- Fresh release zip exists at `release/unexamine-0.0.1-SNAPSHOT.zip`.
- With default Redis `192.168.0.211:6379`, local standalone release startup writes `backend/start-result.json`, health reports `database/schema/redis=UP`, deployed frontend assets match release assets, and admin login succeeds.
- The final script serially runs release package, R6 schema migration, local start, verify-release, R2 module publish, R2 tenant business redraw, R3 runtime approval, R4 admin breadth, R6 data source smoke, R7 work management smoke, R8 todo/message smoke, R9 SSO/no-member smoke, R10 OpenAPI/upload/import-export, R11 AI Agent, R12 module-builder usability, R13 responsive layout, R14 real-login session evidence, R15 password reset, R16 register first-use, release stop, release restart, and verify-release after restart.
- Final result: `status=PASS`, `stepsPassed=24`, `stepsFailed=0`, `stoppedAfterRun=false`.
- The release is left running for user verification at `http://127.0.0.1:18131/`.

Remaining user gate:

- `gates.user_script_passed` remains `false` until the user personally verifies or signs off. The engineering script passed, but user signoff has not been claimed.

Tasks:

- `REC-P0-008 Release Package And Final User Script`

Exit criteria:

- Fresh release package exists in the workspace.
- Backend starts/stops/restarts through packaged scripts.
- Health requires database/schema/redis UP.
- Frontend is same-origin `/api` with no embedded backend host.
- Final user acceptance script passes.
- Only after user verification/signoff may `gates.user_script_passed` become `true`.

## Batch R6: Data Source Management Closure

Status: accepted

Reason:

- The approved brief requires data source management as a system backend surface.
- R4 intentionally exposed data source as `寰呮帴鍏 because no backend contract/table existed.
- This is not acceptable as a final product end state, so it must be implemented before claiming the user's final goal.

Tasks:

- Add persisted system data source configuration with system/tenant scope.
- Add data source list/create/detail/update, connection check, and publish-check APIs.
- Bind the system admin data-source page to real APIs and remove the `寰呮帴鍏 placeholder.
- Add repeatable R6 smoke evidence proving POST -> GET/list -> check -> publish-check -> browser-visible data.

Exit criteria:

- Data source page is API-backed, not static or pending.
- Created data source is read back from the backend and visible in the deployed frontend.
- Connection check and publish-check return traceable results.
- R5 final release script is updated to include R6 before final verification.

Evidence:

- `scripts/recovery-r6-apply-schema.ps1` passes and creates `un_system_data_source` when missing.
- `scripts/recovery-r6-data-source-smoke.ps1` passes for POST -> detail/list -> connection-check -> publish-check.
- `scripts/recovery-r5-final-user-script.ps1` passes after adding R6 with `stepsPassed=14`, `stepsFailed=0`.
- Browser evidence against deployed frontend shows `R6 Internal Data Source 0627215506 / r6_internal_0627215506`, `PASSED`, and a successful page-triggered publish-check.
- Evidence: `docs/evidence/recovery/r6-data-source-2026-06-27.md`.

## Batch R7: Work Management Runtime Closure

Status: accepted

Reason:

- The approved product rules require work management to be a daily-use area with four fixed tabs: dashboard, project tasks, plain tasks, and daily reports.
- Current browser inspection shows the four tabs exist, but the row remains `STATE_BROKEN` because CRUD/readback, pagination state, list/kanban switching, daily report creation, and browser-visible evidence were not accepted as one business flow.

Tasks:

- Add or update the recovery task card for work management runtime closure.
- Ensure project task and plain task lists keep independent page state and previous/next controls actually reload data when available.
- Prove project creation, project task creation, plain task creation, daily report creation, auto draft, dashboard count readback, and list/kanban visibility through a repeatable script.
- Verify the deployed frontend shows created work records in the proper tab instead of only API responses.

Exit criteria:

- Work management dashboard counts reflect created project/task/report data.
- Project tasks, plain tasks, and daily reports are separated into the intended tabs.
- List and kanban are mutually exclusive views for task tabs.
- Pagination controls are either functional with real page reloads or disabled only when the backend page says there is no previous/next page.
- Evidence includes API readback, browser-visible data, and cleanup/default data handling.

Evidence:

- `scripts/recovery-r7-work-management-smoke.ps1` passes and is included in the final R5 orchestration.
- `scripts/recovery-r5-final-user-script.ps1` passes with `stepsPassed=15`, `stepsFailed=0`.
- Browser evidence proves the deployed frontend uses `/assets/index-CTS5z8uP.js`, dashboard counts update, project/plain task/report tabs show created records, and project task list/kanban are mutually exclusive.
- `deploy/templates/nginx/unexamine.conf` now disables caching for `/index.html` and SPA fallback HTML so standalone deployments do not keep pointing at old frontend bundles.
- Kept R6/R7 evidence systems were cleaned; `scripts/recovery-clean-test-systems.ps1` dry-run reports `matchedCount=0`.
- Evidence: `docs/evidence/recovery/r7-work-management-2026-06-27.md`.

## Batch R8: Todo And Message Center Runtime Closure

Status: accepted

Reason:

- The approved product rules require todo and message centers to be daily workbenches, not passive API readbacks.
- R3 proves approval can create todo/message records and approval can close the business flow, but the product audit still marks todo and message centers as `STATE_BROKEN` because browser-visible filtering, paging, read/archive, row jump, and center-level state changes are not accepted as one user flow.

Tasks:

- Add or update the recovery task card for todo/message center runtime closure.
- Fix system message frontend filters so they send real backend query values for read/archive state.
- Add system message page state for keyword, read status, archive status, page number, mark-all-read, archive, and reload.
- Prove requester/approver todo separation, todo search/filter, message read, mark-all-read, archive, active/archived readback, and handled todo transition through a repeatable script.
- Verify the deployed frontend shows the created todo/message records and visible state changes after read/archive actions.

Exit criteria:

- Requester does not see the approver's pending approval todo.
- Approver sees exactly the generated pending approval todo/message and can filter/search them.
- Message read and archive actions change backend readback and deployed frontend visibility.
- Default message stream excludes archived messages; archived filter shows them.
- Todo pending moves to handled after approval action.
- Evidence includes API readback, deployed-browser data/state checks, final release orchestration, and cleanup/default data handling.

Evidence:

- `scripts/recovery-r8-todo-message-center-smoke.ps1` passes and is included in the final R5 orchestration.
- `scripts/recovery-r5-final-user-script.ps1` passes with `stepsPassed=16`, `stepsFailed=0`.
- Browser evidence proves the deployed frontend uses `/assets/index-BUGBsCVB.js`, the todo center shows the generated approval todo, the message center exposes keyword/read/archive controls, mark-all-read changes visible state, archive removes the message from the active stream, and the archived stream shows the archived message.
- Kept browser evidence systems were cleaned; `scripts/recovery-clean-test-systems.ps1` dry-run reports `matchedCount=0`.
- Evidence: `docs/evidence/recovery/r8-todo-message-center-2026-06-27.md`.

## Batch R9: SSO And No-Member Lifecycle Closure

Status: accepted

Reason:

- The approved product rules require SSO/no-member to be a real lifecycle, not a static policy page.
- The audit previously marked SSO/no-member as `UI_BROKEN, STATE_BROKEN`.
- The existing create endpoint incorrectly required a target system member context, which prevented the exact user who lacks a member mapping from submitting a request.

Tasks:

- Add `REC-P0-014 SSO And No-Member Lifecycle Closure`.
- Allow authenticated accounts without target system member mapping to submit and read their own no-member access request.
- Keep approve/reject restricted to the target system administrator.
- Persist full approval as account-member binding, role-member binding, and SSO binding.
- Prove before/after system switch behavior, incomplete-review state, SSO policy/precheck, approval readback, and deployed-browser visibility through repeatable evidence.

Exit criteria:

- No-member request creation works without pre-existing target system member mapping.
- System switch is forbidden before approval and after incomplete approval.
- Full approval returns and later reads back account-member binding, system member, role, data scope, and SSO binding evidence.
- Requester can switch into the target system only after full approval and receives the assigned role.
- R5 final release orchestration includes R9 before final verification.

Evidence:

- `scripts/recovery-r9-sso-no-member-smoke.ps1` passes against the standalone local release through `http://127.0.0.1:18131`.
- `scripts/recovery-r5-final-user-script.ps1` includes R9 and passes with `stepsPassed=17`, `stepsFailed=0`.
- API evidence proves identity provider test/publish, active system SSO policy, org/member precheck, no-member request creation without target system membership, incomplete approval remaining blocked, full approval creating account-member binding, role-member binding, and SSO binding, and requester switch after approval with assigned role.
- Browser evidence on deployed frontend asset `/assets/index-C49n4ymp.js` proves the approved no-member page shows `APPROVED`, member/binding/SSO ids, no stale `NO_SYSTEM_MEMBER_MAPPING` blocker, and the `杩涘叆绯荤粺` action routes to the target system dashboard.
- Kept R9 browser evidence systems were cleaned; cleanup deleted systems `203` and `204`.
- Evidence: `docs/evidence/recovery/r9-sso-no-member-2026-06-27.md`.

## Batch R10: OpenAPI Upload Import Export Closure

Status: completed

Reason:

- The approved product rules require OpenAPI, upload, and import/export to be real operational flows, not admin placeholders or success toasts.
- The audit previously marked OpenAPI/upload/import-export as `UI_BROKEN, FLOW_BROKEN`; R10 now marks it `OK`.
- R4 only proved basic OpenAPI admin visibility; R10 proves secret references, rotation jobs, call logs, real upload, import tasks, export tasks, and deployed same-origin `/openapi` routing.

Tasks:

- Add `REC-P0-012 OpenAPI Upload Import Export Closure`.
- Prove OpenAPI app creation/readback without plaintext secret exposure.
- Prove secret rotation creates readable secret-reference/rotation evidence without returning plaintext.
- Prove call-log readback for an OpenAPI operation.
- Prove real upload plus runtime record attachment binding/readback.
- Prove import and export async task lifecycle with task result/file readback.
- Verify deployed frontend shows OpenAPI/upload/import/export state from real backend data.
- Add R10 to the final R5 orchestration after the standalone R10 smoke passes.

Accepted evidence:

- `scripts/recovery-r10-openapi-upload-import-export-smoke.ps1` passed standalone against backend `9999`.
- R5 final release orchestration passed with `stepsPassed=18`, including `r10-openapi-upload-import-export` through `http://127.0.0.1:18131`.
- Release verification now requires both `/api` and `/openapi` same-origin proxy rules.
- Evidence: `docs/evidence/recovery/r10-openapi-upload-import-export-2026-06-29.md`, `docs/evidence/recovery/r5-final-release-result.json`.

Exit criteria:

- External app management is API-backed and permission-checked.
- Secret references and rotation evidence are persisted and masked.
- Upload binds to real business records and survives readback.
- Import/export create persisted task/result evidence instead of only toasts.

## Batch R11: AI Agent Scope Confirmation Audit Closure

Status: completed

Reason:

- The approved product rules require AI Agent platform/system/work scopes to be separated and auditable.
- Prior R4 evidence only covered Agent policy admin visibility.
- R11 proves model authorization SecretRef, policy scope, platform/system sessions, manual confirmations, work draft confirmation, audit logs, and normal-member negative permission.

Tasks:

- Add `REC-P0-013 AI Agent Scope Confirmation Audit Closure`.
- Prove platform model authorization readback without plaintext credential exposure.
- Prove platform Agent cannot confirm system business scope.
- Prove system Agent policy publish-check with module/field/action/data/outbound/desensitize scope.
- Prove system Agent message proposes write and work confirmations.
- Prove write confirm/reject transitions and work draft confirmation with audit logs.
- Prove normal system member cannot manage Agent policies.
- Add R11 to final R5 orchestration.

Accepted evidence:

- `scripts/recovery-r11-ai-agent-scope-confirmation-smoke.ps1` passed against deployed release `http://127.0.0.1:18131`.
- Evidence: `docs/evidence/recovery/r11-ai-agent-scope-confirmation-2026-06-29.md`.

Exit criteria:

- Platform Agent remains platform-scoped and rejects system business target scope.
- System Agent write/work outputs require human confirmation evidence.
- Agent audit logs include session/confirmation/policy/permission/outbound/desensitize snapshots.
- Ordinary system members cannot manage Agent policies.
- R5 final release orchestration includes R10 before final verification.

## Batch R12: Module Builder Usability And Fresh-System Initialization

Status: accepted

Reason:

- User acceptance on 2026-06-29 reports that the standalone product is still not usable despite R5 passing: module settings actions are obscured by layout, publish cannot be clicked, the filter action beside reset is missing, field type/configuration shape does not match a real module builder, and fresh systems lack default department clarity.
- This invalidates the previous broad `OK` claim for module configuration and system admin initialization. API smoke and publish scripts are not sufficient when the deployed UI cannot be operated by a human.

Tasks:

- Add `REC-P0-015 Module Builder Usability And Initialization Closure`.
- Reproduce the deployed module-management defects in a real browser.
- Fix module management toolbar layout so create/filter/reset/publish actions are visible, stable, and clickable.
- Replace scattered field forms with a dedicated builder shape: left field list, center form/preview, right property panel, using fixed field type choices.
- Verify fresh system default department initialization or add an explicit default department bootstrap path.
- Add browser evidence and repeatable checks before considering R12 accepted.

Exit criteria:

- A system admin can create a fresh system, see or create the default department baseline, configure module fields through the deployed UI, run publish check, publish, and then see the module in the system business shell.
- Desktop and narrow browser screenshots show no overlapped or clipped module builder controls.
- Filter and reset controls have real state changes or precise disabled reasons.
- R5 final release orchestration is updated only after R12 standalone evidence passes.

Accepted evidence:

- `scripts/recovery-r12-module-builder-usability-smoke.ps1` passed against deployed release `http://127.0.0.1:18131`.
- Deployed browser evidence passed at desktop `1280x720` and mobile `390x720`, with old table removed, filter/apply controls present, fixed field type controls present, publish controls present, and no measured overflow.
- Fresh-system initialization was verified for both platform-create and register-with-system paths; both create `default_department` and bind the owner member to it.
- R5 final release orchestration now includes R12 and passes with 20 steps, 0 failures.
- Evidence: `docs/evidence/recovery/r12-module-builder-usability-2026-06-29.md`.

## Batch R13: Cross-Shell Responsive Usability Sweep

Status: accepted

Reason:

- User acceptance after R12 still reports the deployed product is not usable as a system.
- A clean cross-page browser audit found two first-order responsive defects: narrow system admin shows the full sidebar before the selected page content, and narrow work/dashboard pages clip cards horizontally.
- Prior per-feature scripts proved business APIs and selected pages, but they did not enforce cross-shell desktop/narrow layout gates across the actual deployed release.

Tasks:

- Add `REC-P0-016 Cross-Shell Responsive Usability Closure`.
- Convert the current cross-page browser audit into repeatable machine-readable evidence.
- Fix system admin navigation so narrow viewports keep the selected configuration content visible instead of stacking the full sidebar above it.
- Fix work/dashboard grids so narrow viewports do not clip cards or create document-level horizontal overflow.
- Re-run package, deployed frontend verification, R13 responsive browser smoke, and R5 final orchestration after the standalone R13 evidence passes.

Exit criteria:

- Desktop and narrow screenshots exist for platform workspace, system admin module management, system business dashboard/modules, work, todo, and message pages.
- Machine-readable browser evidence reports no document-level horizontal overflow and no selected-content-below-first-viewport failure.
- System admin primary navigation is compact on narrow screens and does not bury the active page below the sidebar.
- Work dashboard/cards are readable without right-side clipping on narrow screens.
- R5 final release orchestration includes R13 before the next user-facing claim.

Accepted evidence:

- `scripts/recovery-r13-cross-shell-responsive-smoke.ps1` passed against deployed release `http://127.0.0.1:18131`.
- Browser evidence covers platform workspace, platform admin, system dashboard, system modules, work, todo, messages, and system admin module management at desktop `1280x720` and mobile `390x720`.
- Machine-readable audit reports no document-level horizontal overflow, no clipped selected content, compact mobile system-admin sidebar, and active admin content visible in the first viewport.
- R5 final release orchestration now includes R13 and passes with 21 steps, 0 failures.
- Evidence: `docs/evidence/recovery/r13-cross-shell-responsive-usability-2026-06-29.md`.

## Batch R14: Real Login Session State Browser Evidence

Status: accepted

Reason:

- R13 responsive browser evidence proved layout breadth, but its browser setup wrote tokens into localStorage after the SPA had already mounted. That can leave the shell account label in an unauthenticated-looking state and makes the evidence weaker than the real human login path.
- The product may already behave correctly through real login, but the recovery pipeline must not accept a browser screenshot that bypasses login/session initialization.

Tasks:

- Add `REC-P0-017 Real Login Session State Browser Evidence Closure`.
- Add a repeatable browser smoke that starts from `/login`, fills `admin / 123123aa`, clicks the real login form button, and waits for shell initialization.
- Assert authenticated account state, platform workspace, system admin module management, and work management all render from the same real browser session.
- Capture screenshots and machine-readable assertions for account label, route hash, token/account state, and overflow.
- Add R14 to R5 final release orchestration only after standalone R14 passes.

Exit criteria:

- Real UI login initializes token/account state without post-mounted token injection.
- Browser evidence shows the authenticated account label and does not show an unauthenticated account label.
- System admin and work pages remain usable in the same session with no document-level horizontal overflow.
- R5 final release orchestration includes R14 before the next user-facing claim.

Accepted evidence:

- `scripts/recovery-r14-real-login-session-smoke.ps1` passed standalone against deployed release `http://127.0.0.1:18131`.
- The browser opened `/login`, filled `admin / 123123aa`, clicked the real login form button, received token/account state, displayed `admin`, and opened system admin module management plus work management from the same session.
- Browser evidence reports `overflowX=0`, no blockers, module toolbar visible in system admin, and work content visible.
- `scripts/recovery-r5-final-user-script.ps1` now includes R14 and passes with 22 steps, 0 failures.
- Evidence: `docs/evidence/recovery/r14-real-login-session-state-2026-06-29.md`.

## Batch R15: Password Reset Account Closure

Status: accepted

Reason:

- Follow-up audit after R14 found the password reset confirmation endpoint returns an accepted result but does not update `un_plat_account.password_hash`.
- The approved auth surface includes login, register, and password reset. A visible password reset page that cannot change the login credential is a P0 flow break.

Tasks:

- Add `REC-P0-018 Password Reset Account Closure`.
- Store password reset tickets in Redis with account id, verification code, and a 15-minute TTL.
- Make reset request fail for an unknown account instead of issuing an unusable ticket.
- Make reset confirm validate the ticket/code, update the password hash, delete the ticket, and reject ticket reuse.
- Bind the frontend password reset page to the verification code returned by the current standalone/local delivery mode.
- Add standalone R15 browser/API evidence and include R15 in R5 final release orchestration.

Exit criteria:

- Browser password reset request/confirm succeeds for a real disposable account.
- Old password is rejected after reset.
- New password logs in successfully.
- The same reset ticket cannot be reused.
- R5 final release orchestration includes R15 before the next user-facing claim.

Accepted evidence:

- `scripts/recovery-r15-password-reset-smoke.ps1` passed standalone against deployed release `http://127.0.0.1:18131`.
- Browser evidence opened `/forgot-password`, requested a reset ticket for a disposable registered account, received a 6-digit verification code into the confirm form, and confirmed the new password from the deployed page.
- API readback rejected the old password, accepted the new password, and rejected reuse of the same reset ticket.
- Cleanup deleted the disposable system.
- `scripts/recovery-r5-final-user-script.ps1` now includes R15 and passes with 23 steps, 0 failures.
- Evidence: `docs/evidence/recovery/r15-password-reset-account-closure-2026-06-29.md`, `docs/evidence/recovery/r15-password-reset-result.json`.

## Batch R16: Register First-Use Browser Initialization Closure

Status: accepted

Reason:

- R12 proved register-with-system default department creation through API readback, but did not independently prove the deployed browser registration path from `/register-with-system` through first-use initialization.
- A new account that lands on an empty dashboard or cannot enter system backend after registration would recreate the user's core complaint: the product starts, but the first real user cannot understand how to make it usable.
- R16 closes this evidence gap with a browser-first script rather than another API-only assertion.

Tasks:

- Add `REC-P0-019 Register First-Use Browser Initialization Closure`.
- Open the deployed register page in a fresh browser profile and submit the real form.
- Assert token/account state, dashboard route, first-use onboarding panel, and initialization action.
- Click the initialization action and assert system backend content renders.
- Read back the registered account's system switch context and default department/member binding.
- Add R16 to final R5 orchestration.

Exit criteria:

- Browser registration lands on `/systems/{systemId}/dashboard`.
- The new system dashboard shows first-use initialization guidance instead of an empty runtime shell.
- The initialization action opens system backend.
- The registered account receives `SYSTEM_SUPER_ADMIN` only in the created system.
- `default_department` exists and the owner member is bound to it.
- R5 final release orchestration includes R16 before the next user-facing claim.

Accepted evidence:

- `scripts/recovery-r16-register-first-use-smoke.ps1` passed standalone against deployed release `http://127.0.0.1:18131`.
- Browser evidence submitted the real register form, landed on `#/systems/360/dashboard`, entered `#/systems/360/admin`, and captured desktop/mobile screenshots with no browser-measured failures.
- API readback showed tenant `384`, system member/account-member binding `472`, `SYSTEM_SUPER_ADMIN`, `default_department`, and owner-member binding.
- Cleanup deleted the disposable system.
- Evidence: `docs/evidence/recovery/r16-register-first-use-browser-closure-2026-06-29.md`, `docs/evidence/recovery/r16-register-first-use-result.json`.
- `scripts/recovery-r5-final-user-script.ps1` now includes R16 and passes with 24 steps, 0 failures.

## Batch R17: Normal Member Real-Login Runtime Usability Closure

Status: accepted

Reason:

- R3 proves normal-member runtime behavior mostly through API setup and browser evidence after prepared state.
- R14 proves real browser login, but only with the default admin account.
- The remaining high-risk user path is the everyday normal member: real login, authorized system business page, frontend form create/readback, and no system backend access.

Tasks:

- Add `REC-P0-020 Normal Member Real-Login Runtime Usability Closure`.
- Prepare a disposable published module and normal member with runtime permission but no system-admin permission.
- Open the deployed login page in a fresh browser profile and submit the normal account through the real form.
- Use the deployed frontend runtime page to create a record and prove visible list/detail readback.
- Assert the system-admin header entry is absent, direct system-admin URL renders access denied, and backend admin APIs return forbidden for the normal member.
- Add R17 to final R5 orchestration after standalone evidence passes.

Exit criteria:

- Normal member real UI login initializes token/account state without token injection.
- Runtime module page renders from the same browser session.
- Frontend form creates a record whose field value is visible after save/readback.
- System backend entry is absent for the normal member and direct admin URL is denied.
- Browser evidence covers desktop and mobile without document-level horizontal overflow.
- R5 final release orchestration includes R17 before the next user-facing claim.

Accepted evidence:

- `scripts/recovery-r17-normal-member-real-login-runtime-smoke.ps1` passed standalone against deployed release `http://127.0.0.1:18131`.
- Browser evidence opened `/login`, submitted the normal member account through the real form, opened the target system runtime module page, created a record from the deployed frontend form, and verified desktop/mobile list/detail readback.
- Permission evidence showed the normal member had only the disposable runtime role, backend system member-list API returned HTTP 403, the system backend header entry was absent, and direct `#/systems/{systemId}/admin` rendered access denied instead of admin content.
- Cleanup deleted the disposable target and owned systems.
- Full R5 final release orchestration now includes R17 and passes with 25 steps, 0 failures.
- Evidence: `docs/evidence/recovery/r17-normal-member-real-login-runtime-2026-06-29.md`, `docs/evidence/recovery/r17-normal-member-real-login-runtime-result.json`, and `docs/evidence/recovery/r5-final-release-result.json`.

## Batch R18: Runtime Mobile Action Containment Closure

Status: accepted

Reason:

- Visual review of the accepted R17 mobile screenshot showed the runtime page's primary action group partially escaping the right side of a 390px viewport.
- R17 proved the normal-member flow and permission boundary, but it did not enforce that primary runtime actions remain fully reachable on mobile without document-level horizontal scrolling.
- This recreates the user's original complaint in a narrower form: the product technically works, but the page still feels piled up and not directly usable on a real small viewport.

Tasks:

- Add `REC-P0-021 Runtime Mobile Action Containment Closure`.
- Make runtime page header, action group, filter controls, batch bar, table shell, and detail/edit panels contain themselves at mobile width.
- Add a deployed browser smoke that logs in as a normal member at 390x720, verifies visible action bounds, creates a record, reads it back, and rechecks no horizontal document overflow.
- Preserve R17 permission expectations: no system-admin entry and direct admin URL access denied.
- Add R18 to final R5 orchestration after standalone evidence passes.

Exit criteria:

- On a 390px viewport, `鏂板缓`, `瀵煎叆`, `鍏ㄩ儴瀵煎嚭`, and `鍒楄缃甡 are fully inside the viewport or wrapped into visible rows.
- Runtime filters and action buttons remain clickable without horizontal document scrolling.
- Creating and saving a runtime record from the deployed mobile browser still succeeds.
- The normal member still cannot see or enter system backend.
- R5 final release orchestration includes R18 before the next user-facing readiness claim.

Accepted evidence:

- `scripts/recovery-r18-runtime-mobile-action-containment-smoke.ps1` passed standalone against deployed release `http://127.0.0.1:18131`.
- Browser evidence submitted the normal member through the real `/login` form at 390x720, opened the runtime page, verified primary actions and filter controls were inside the mobile viewport, created a record, and read it back in list/detail.
- The runtime browser summary reported `maxOverflowX=0`, `screenshotCount=5`, and no blocker results.
- Permission evidence stayed intact: backend system member-list API returned HTTP 403 and direct system-admin URL rendered access denied for the normal member.
- Cleanup deleted the disposable target and owned systems.
- Full R5 final release orchestration now includes R18 and passes with 26 steps, 0 failures.
- Evidence: `docs/evidence/recovery/r18-runtime-mobile-action-containment-2026-06-29.md`, `docs/evidence/recovery/r18-runtime-mobile-action-containment-result.json`, and `docs/evidence/recovery/r5-final-release-result.json`.

## Batch R19: Admin Aggregated Pagination Closure

Status: accepted

Reason:

- Audit found that platform-admin and system-admin aggregated screens load many API-backed lists as `PageResult` objects but render pagination without page-change callbacks.
- This means administrators can be blocked at page 1 even when backend data has page 2, which matches the user's complaint that the system still looks present but is not actually usable.

Tasks:

- Add `REC-P0-022 Admin Aggregated Pagination Closure`.
- Thread page-number state through platform-admin and system-admin aggregated data loaders.
- Wire every admin `createPagination` call to a real page-change callback for page-backed resources.
- Add deployed browser evidence that page 2 is reachable and record content changes after clicking next page.
- Add R19 to final R5 orchestration after standalone evidence passes.

Exit criteria:

- Platform admin paged lists can move beyond page 1 and back.
- System admin paged lists can move beyond page 1 and back.
- Page changes re-query backend APIs with the requested page number.
- Browser evidence proves real deployed UI behavior, not just API results.
- R5 final release orchestration includes R19 before the next user-facing readiness claim.

Accepted evidence:

- `scripts/recovery-r19-admin-aggregated-pagination-smoke.ps1` passed standalone against deployed release `http://127.0.0.1:18131`.
- Browser evidence submitted admin through the real `/login` form, opened platform admin system lifecycle, clicked from page 1 to page 2, and verified the first visible system row changed.
- Browser evidence opened the disposable system admin role-management page, clicked from page 1 to page 2, and verified the first visible role row changed.
- Pagination controls did not show the unsupported-pagination disabled reason in the verified admin lists.
- Cleanup deleted all 21 disposable R19 systems.
- Full R5 final release orchestration now includes R19 and passes with 27 steps, 0 failures.
- Evidence: `docs/evidence/recovery/r19-admin-aggregated-pagination-2026-06-29.md`, `docs/evidence/recovery/r19-admin-aggregated-pagination-result.json`, and `docs/evidence/recovery/r5-final-release-result.json`.

## Batch R20: System Flow Canvas Designer Closure

Status: accepted

Reason:

- Follow-up product audit after R19 found that system flow management still exposed create, publish-check, and publish actions without a real frontend canvas configuration workspace.
- This recreates a known prototype-stage failure mode: the backend API supports node library, canvas save, node properties, simulation, and snapshots, but the deployed UI only looks like a list of flows.

Tasks:

- Add `REC-P0-023 System Flow Canvas Designer Closure`.
- Bind the system-admin flow page to node-library, canvas read/save, simulation, and publish-check APIs.
- Add a three-part configuration workspace: node library, canvas/edge area, and property panel.
- Ensure new flow creation still starts as an empty draft; nodes and edges are added only by explicit administrator action.
- Add deployed browser evidence and include R20 in final R5 orchestration after standalone evidence passes.

Exit criteria:

- A system administrator can open flow management, configure nodes/edges/properties, save, run simulation, run publish-check, and publish a snapshot from the deployed system.
- Empty canvas publish-check fails before configuration.
- Saved nodes and edges are read back from backend APIs, not only local DOM.
- Mobile layout contains the flow designer without document-level horizontal overflow; canvas overflow is limited to the canvas panel.
- R5 final release orchestration includes R20 before the next user-facing readiness claim.

Accepted evidence:

- `scripts/recovery-r20-flow-canvas-designer-smoke.ps1` passed standalone against deployed release `http://127.0.0.1:18131`.
- Browser evidence used real `/login`, opened system admin flow management, clicked `閰嶇疆鐢诲竷`, inserted approval/end nodes, edited node properties, saved the canvas, ran simulation, and ran publish-check.
- API readback confirmed persisted canvas nodes and edges; empty-canvas publish-check failed before configuration; saved-canvas publish-check passed; publish created a snapshot.
- Mobile evidence kept document-level overflow at `0`, with canvas overflow contained inside the canvas panel.
- Evidence: `docs/evidence/recovery/r20-flow-canvas-designer-2026-06-29.md`, `docs/evidence/recovery/r20-flow-canvas-designer-result.json`.

## Batch R21: Final Usable System Journey Gate

Status: accepted

Reason:

- The user's current correction is that isolated R batches are still not the same as the final goal: a system people can use.
- The recovery process must now promote the acceptance unit from feature/batch evidence to coherent role journeys.
- The interrupted R5 run left `docs/evidence/recovery/r5-final-release-result.json` corrupted with NUL bytes, so final release health cannot currently be accepted.

Tasks:

- Add `REC-P0-024 Final Usable System Journey Gate`.
- Add `docs/recovery/final-usable-system-acceptance.md` as the top-level J0-J7 acceptance ledger.
- Add `scripts/recovery-r21-final-usable-system-audit.ps1` to read the ledger and evidence files.
- Make the audit fail rather than falsely pass when R5 evidence is invalid, R20 evidence is missing, or operations/maintenance coverage is not proven.
- Use R21 as the next gate before any new user-facing completion claim.

Exit criteria:

- J0 release health has a valid, regenerated R5 PASS result after R20 is included.
- J1 through J6 are either passed by coherent journey evidence or explicitly reopened as fix batches.
- J7 operations/maintenance backup/rollback gaps are either implemented and proven or recorded as an open non-final gap.
- `gates.user_script_passed` remains `false` until user verification/signoff.

Accepted evidence:

- `docs/recovery/final-usable-system-acceptance.md`
- `scripts/recovery-r21-final-usable-system-audit.ps1`
- `docs/evidence/recovery/r21-final-usable-system-audit-result.json`
- R21 reports `status=PASS`, `failedGateCount=0`, and J0-J7 all `PASS` after R5 was rerun with R20/R22 included.
- `gates.user_script_passed` remains false until user verification/signoff.

## Batch R22: Operations And Maintenance Journey Closure

Status: accepted

Reason:

- R21 now proves J0-J6, but J7 fails because backup/rollback/maintenance has not been shown as a deployed user journey.
- The backend already exposes `OpsGovernanceController`, but the platform admin configuration page did not expose a coherent operations governance panel.

Tasks:

- Add `REC-P0-025 Operations And Maintenance Journey Closure`.
- Bind platform admin configuration to platform ops APIs for health check, feature flag, quota, rate limit, backup, restore drill, archive restore, deployments, deployment rollback dry-run, and API cache policy.
- Add a deployed browser/API smoke that clicks the operations governance panel and validates task ids or trace ids.
- Update R21 so J7 requires R22 evidence.

Exit criteria:

- The deployed platform admin configuration page exposes operations governance actions.
- Each critical operation returns visible result text with taskId, traceId, rollbackSupported, or cache/deployment data.
- The release package exposes `server.sh start|stop|restart|status|health`, `verify-release.ps1` passes, and deployed assets match the current release.
- R21 final usable system audit passes J7 after R22 evidence exists.

Accepted evidence:

- `scripts/recovery-r22-ops-maintenance-smoke.ps1` passed standalone against deployed release `http://127.0.0.1:18131`.
- Browser evidence used real `/login`, opened platform admin configuration, clicked operations governance buttons, and saw visible task/trace/cache/deployment results.
- API evidence covered platform health, feature flag update, quota update, rate-limit update, backup task, restore drill, archive restore, deployment rollback dry-run, and API cache read/update.
- Release evidence verified deployed frontend asset match and packaged `server.sh` command support for start/stop/restart/status/health.
- R5 final release orchestration includes R22 and passes after R22 was added.
- Evidence: `docs/evidence/recovery/r22-ops-maintenance-2026-06-29.md`, `docs/evidence/recovery/r22-ops-maintenance-result.json`.

## Batch R23: Final Requirement Coverage Gate

Status: accepted

Reason:

- R21 proves a top-level journey set, but the original `docs/user_requirement.md` is broader than J0-J7.
- The process needs a hard gate that prevents any agent from claiming final completion while requirement rows are still `PARTIAL` or `OPEN`.

Tasks:

- Add `REC-P0-026 Final Requirement Coverage Gate`.
- Add `docs/framework/final-requirement-coverage-ledger.md`.
- Add `docs/framework/final-requirement-closure-plan.md`.
- Add `scripts/final-requirement-coverage-audit.ps1`.
- Add `scripts/final-requirement-gap-report.ps1`.
- Add J11 to `docs/framework/next-execution-ledger.md`.

Exit criteria:

- The coverage audit parses major sections of `docs/user_requirement.md`.
- The ledger contains a row for every required major section.
- The audit fails while any row is `PARTIAL` or `OPEN`.
- The gap report orders the remaining work into FRC batches.

Accepted evidence:

- `scripts/final-requirement-coverage-audit.ps1 -NoFailExit` ran and reported `requiredCount=43`, `missingCount=0`, `notClosedCount=45`, `status=FAIL`.
- `scripts/final-requirement-gap-report.ps1` generated `docs/evidence/final-requirement-gap-report.md`, with FRC-1 as the first recommended batch.
- This batch is accepted as a governance gate only. It intentionally does not close final product completion.
- Evidence: `docs/evidence/final-requirement-coverage-audit-result.json`, `docs/evidence/final-requirement-gap-report.md`.

## Batch R24: FRC-1 Missing Product Surfaces Closure

Status: in_progress

Reason:

- The final requirement coverage audit shows five `OPEN` rows: pages, overall visual style, home page, page designer, and advanced/new capabilities.
- These surfaces must be implemented, proven, or split into explicit sub-task cards before broader PARTIAL rows can be honestly closed.

Tasks:

- Add `REC-P0-027 FRC-1 Missing Product Surfaces Closure`.
- Audit existing frontend/backend for page designer, home page, command center, assistant, schema-driven page, advanced table, flow simulation, and print designer coverage.
- Split any oversized advanced capability into its own task card before coding.
- Implement the smallest complete page/home/designer loop first: system admin configures a page/home surface, publishes it, and normal member sees the result.
- Add deployed browser/API/readback evidence.
- Update `docs/framework/final-requirement-coverage-ledger.md` from `OPEN` to `PROVEN` or narrower `PARTIAL` sub-rows only when evidence scope matches.

Exit criteria:

- `REQ-5.8`, `REQ-6.1`, `REQ-6.3`, `REQ-6.8`, and `REQ-9` are no longer broad unworked `OPEN` rows.
- The page/home/designer path has deployed browser evidence.
- Visual style is evaluated beyond overflow checks.
- Advanced capabilities are either proven or split into explicit implementation task cards without calling them complete.

Accepted evidence:

- The earlier JDK 21.0.10 javac blocker is superseded. Current build/release verification uses Temurin JDK 21.0.11 at `D:\dev\jdk21-temurin`.
- The home page configuration loop now passes on the deployed release:
  - system admin PATCH saves home page config,
  - admin GET reads it back,
  - runtime GET reads the same title,
  - normal member runtime GET succeeds,
  - normal member admin PATCH is rejected with HTTP 403,
  - publish-check passes with three items,
  - deployed browser shows the configured title in admin config, desktop dashboard, and mobile dashboard.
- The module page designer API/runtime loop now passes on the deployed release:
  - system admin saves a page designer draft with toolbar/list/detail/chart components,
  - admin list readback returns the draft,
  - publish-check passes,
  - publish writes a published snapshot,
  - runtime readback returns the published page and components,
  - normal member runtime read succeeds,
  - normal member designer write is rejected with HTTP 403.
- The visual-density pass now reduces the most obvious deployed stacking issues:
  - system dashboard no longer renders duplicated home widgets plus duplicate default metrics as separate blocks,
  - system dashboard desktop browser audit reports four panels and no horizontal overflow,
  - system admin load warnings are summarized to the first three rows plus a count instead of stacking every trace,
  - system admin initialization checklist removes explanatory paragraphs from each step and keeps step/status/action visible.
- `REQ-9` advanced/new capabilities are no longer a broad unworked row; they are split into `REC-P0-028` through `REC-P0-033` for command center, right-side assistant, schema-driven pages, advanced table, flow simulation, and print template visual designer.
- Evidence: `docs/evidence/recovery/r24-home-page-config-2026-06-30.md`, `docs/evidence/recovery/r24-home-page-config-result.json`, `docs/evidence/recovery/screenshots/r24-home-page-config/home-page-config-browser-audit.json`, `docs/evidence/recovery/r24-page-designer-2026-06-30.md`, `docs/evidence/recovery/r24-page-designer-result.json`, `docs/evidence/recovery/r24-visual-style-browser-audit-2026-06-30.md`, `docs/evidence/recovery/r24-visual-style-browser-audit-result.json`, and `docs/evidence/recovery/r24-req9-split-2026-06-30.md`.

Remaining work before R24/FRC-1 can be accepted:

- `REQ-6.1` overall visual style is narrowed to `PARTIAL`; it still needs broader human/UI review across role journeys before `PROVEN`.
- `REQ-6.8` page designer is narrowed to `PARTIAL`; it still needs deployed browser interaction, visual preview, broader component coverage, copy/drag-drop/mobile preview, and usability evidence before `PROVEN`.
- `REQ-9` advanced/new capabilities are narrowed to `PARTIAL`; split cards still need implementation and deployed evidence.
- `REQ-5.8` pages and `REQ-6.3` home page are narrowed to `PARTIAL`, not final completion.

## Batch R28: REC-P0-028 Command Center First Navigation Loop

Status: accepted as first-loop evidence; REQ-9 remains PARTIAL.

Reason:

- User feedback identified that pages and functions still felt scattered and hard to use.
- The first recovery target is a role-aware command center that gives platform/system users one predictable entry for navigation.

Implemented scope:

- Added authenticated command-center API at `GET /api/v1/command-center`.
- Added coded command search/permission service for platform workbench, platform admin, system dashboard, work, todos, messages, system admin, and published runtime modules.
- Added frontend command overlay, platform/system header entry, and Ctrl/Cmd+K shortcut.
- Added route execution through existing platform/system shells.
- Added permission evidence for admin and normal-member command results.

Accepted evidence:

- `scripts/recovery-r28-command-center-smoke.ps1` PASS on the deployed release.
- `docs/evidence/recovery/r28-command-center-result.json` records:
  - admin platform admin command enabled,
  - admin system admin command enabled,
  - normal member dashboard/work commands enabled,
  - normal member system/platform admin commands disabled with reasons,
  - cleanup of created systems.
- `docs/evidence/recovery/screenshots/r28-command-center/command-center-browser-audit.json` records:
  - active deployed asset `/assets/index-CnD8miaq.js`,
  - command header button present,
  - command dialog present,
  - keyword `鍚庡彴` returns platform/system backend commands,
  - desktop overflowX=0.
- Release verification passed with deployed assets `/assets/index-CnD8miaq.js` and `/assets/index-CuXcp5xt.css`.

Remaining work:

- Keyboard result selection and focus management.
- Recent/favorite command persistence.
- Safe quick-create/draft actions beyond route navigation.
- Normal-member deployed browser evidence for disabled admin command display.
- Mobile command-center browser audit.
- REC-P0-029 through REC-P0-033 remain unimplemented.

## Batch R29: REC-P0-029 Right-Side Assistant First Loop

Status: accepted as first-loop evidence; REQ-9 remains PARTIAL.

Reason:

- User feedback identified that existing work still did not behave like a human-usable integrated system.
- The second advanced-capability recovery target is a right-side assistant that is reachable from the existing system shell and uses real Agent confirmation/audit APIs instead of a static text panel.

Implemented scope:

- Added frontend Agent API bindings for system sessions, messages, write previews, write confirm/reject, work draft confirm, and audit log readback.
- Added right-side assistant drawer in the existing system shell header.
- Drawer shows current system context, prompt input, generated analysis, write preview, permission clipping, work draft, confirmation/rejection actions, and trace/audit lines.
- Reused existing coded Agent policy/session/confirmation/audit backend instead of creating a duplicate assistant backend.
- Added deployed smoke coverage for admin and normal-member paths.

Accepted evidence:

- `scripts/recovery-r29-right-assistant-smoke.ps1` PASS on the deployed release.
- `docs/evidence/recovery/r29-right-assistant-result.json` records:
  - Agent policy publish-check passed,
  - admin message proposed system write and work draft confirmations,
  - write preview waited for human confirmation,
  - write confirm became `CONFIRMED`,
  - separate write reject became `REJECTED`,
  - work draft preview waited and final confirmation became `CONFIRMED`,
  - normal-member scoped assistant session/message worked,
  - normal-member admin policy creation was rejected with HTTP 403,
  - Agent audit readback returned confirmation records.
- `docs/evidence/recovery/screenshots/r29-right-assistant/assistant-drawer-browser-audit.json` records:
  - active deployed asset `/assets/index-B9EQzV1l.js`,
  - assistant drawer present from the system shell,
  - prompt input and generate/admin buttons present,
  - desktop overflowX=0.
- Release verification passed with deployed assets `/assets/index-B9EQzV1l.js` and `/assets/index-C0jCwNCi.css`.

Remaining work:

- Normal-member deployed browser evidence for disabled admin-generation action display.
- Mobile assistant drawer audit.
- Task-specific field/process/export draft flows.
- Drawer focus management and keyboard handling.
- Missing policy/model, backend failure, and write conflict UI states.
- REC-P0-030 schema-driven runtime is now first-loop implemented in R30; REC-P0-031 through REC-P0-033 remain unimplemented.

## Batch R30: REC-P0-030 Schema-Driven Page Runtime First Loop

Status: accepted as first-loop evidence; REQ-9 and REQ-6.8 remain PARTIAL.

Reason:

- User feedback identified that page designer work previously stopped at page shells and did not prove that configured pages became runtime contracts.
- The next usable-system step is a schema contract that ties admin configuration, published snapshots, runtime rendering, permissions, and backend write enforcement together.

Implemented scope:

- Added page schema models and endpoints for admin draft/published schema and runtime published schema.
- Schema composition now combines persisted page components, module fields, published version, validation metadata, permission snapshot metadata, hidden-field removal, readonly flags, and component pruning for hidden bound fields.
- Runtime live data now prefers published `main` page schema when available and falls back to list schema when a module has no page schema.
- Shared frontend schema renderer now drives admin schema preview and runtime create/edit fields.
- Runtime form no longer submits disabled/readonly schema fields from the frontend; backend still enforces forbidden writes.
- System backend page design panel exposes a `Schema 棰勮` action and renders published schema fields/components.

Evidence:

- `scripts/recovery-r30-schema-runtime-smoke.ps1`
- `docs/evidence/recovery/r30-schema-runtime-2026-06-30.md`
- `docs/evidence/recovery/r30-schema-runtime-result.json`
- `docs/evidence/recovery/screenshots/r30-schema-runtime/schema-runtime-api-audit.json`
- `docs/evidence/recovery/screenshots/r30-schema-runtime/schema-preview-browser-audit.json`
- `docs/evidence/recovery/screenshots/r30-schema-runtime/desktop-schema-preview.png`
- Release verification after packaging passed against `http://127.0.0.1:18131/` on deployed asset `/assets/index-Bm_DgZ50.js`.

Remaining work:

- Move page schema preview into the main module-management workflow or provide a clearer direct module page entry.
- Normal-member deployed browser evidence for readonly runtime form rendering.
- Drag/drop component layout, mobile preview, validation-error focus, richer component catalog, and broader page designer usability review.
- REC-P0-031 advanced table, REC-P0-032 flow simulation, and REC-P0-033 print designer remain open.

## Batch R31: REC-P0-031 Advanced Table First Loop

Status: accepted as first-loop evidence; REQ-6.4, REQ-5.11, and REQ-9 remain PARTIAL.

Reason:

- User feedback identified that scattered pages and crowded list surfaces still made the product feel unusable.
- The next usable-system target is a real advanced table loop where saved views, server-side query behavior, permission-filtered columns, and row-click detail work together in the deployed runtime instead of stopping at a static table shell.

Implemented scope:

- Added a runtime-safe scene endpoint for normal members so saved table views can be selected without exposing admin scene management APIs.
- Runtime list schema now applies scene-driven column crop/order, quick filters, default sort, and fixed-column metadata.
- Runtime record search now applies scene default sorts when no explicit sort is supplied.
- Numeric record sorting now compares `NUMBER` values as decimals instead of lexicographic strings.
- Runtime frontend now loads saved scenes, sends `sceneCode` in list queries, renders a saved-view selector, supports column hide/reorder save, shows fixed-column affordance, deduplicates row-level detail buttons, and keeps row-click detail as the primary action.
- Mobile table overflow is contained inside the table shell instead of creating document-level horizontal overflow.

Accepted evidence:

- `scripts/recovery-r31-advanced-table-smoke.ps1` PASS on the deployed release.
- `docs/evidence/recovery/r31-advanced-table-2026-06-30.md`
- `docs/evidence/recovery/r31-advanced-table-result.json`
- `docs/evidence/recovery/screenshots/r31-advanced-table/advanced-table-browser-audit.json`
- `docs/evidence/recovery/screenshots/r31-advanced-table/desktop-saved-view.png`
- `docs/evidence/recovery/screenshots/r31-advanced-table/mobile-saved-view.png`
- Release verification passed with deployed assets `/assets/index-Dg4-dv2W.js` and `/assets/index-CCOysJ5I.css`.

R31 proven scope:

- A normal member can select a saved compact table view from the runtime list.
- Server paging returns 10 rows from a 25-record data set.
- Saved view column order is honored as `amountValue, publicName`.
- The first configured column is fixed and marked in the UI.
- Hidden `secretNote` does not leak through columns or row fields.
- Saved scene default sort returns `1.000000` first; explicit descending sort returns `25.000000` first.
- Server-side field filtering returns the expected `Item 2` subset.
- Re-login preserves the saved view behavior through backend scene state.
- Browser evidence proves desktop row-click detail, no duplicate detail buttons, and mobile document overflow `0`.

Remaining work:

- Admin list pages need advanced-table parity beyond aggregated pagination.
- Per-user saved view ownership and preference scope need clearer product behavior.
- Batch actions, empty/error states, column width controls, richer fixed-column controls, keyboard navigation, and accessibility still need deployed evidence.
- Broader list/detail usability review and user acceptance remain required before `REQ-6.4`, `REQ-5.11`, or `REQ-9` can be `PROVEN`.
- REC-P0-033 print template visual designer now has first-loop evidence in R33, but broader print/PDF fidelity remains partial.
- REC-P0-032 flow simulation has first-loop evidence, but broader workflow coverage remains partial.

## Batch R32: REC-P0-032 Flow Simulation First Loop

Status: accepted as first-loop evidence; REQ-4.5, REQ-5.12, REQ-6.9, and REQ-9 remain PARTIAL.

Reason:

- R20 proved that the flow canvas designer exists and can run a basic simulation action, but the simulation still behaved like a fixed demo path.
- A no-code workflow platform is not usable unless an administrator can enter sample record data, predict branches and approvers, see blockers, and verify that simulation does not create runtime approval instances.

Planned scope:

- Replace fixed-path simulation with draft/snapshot canvas-based path prediction.
- Evaluate condition edge payloads against sample field values.
- Return predicted step traces, condition decisions, approver candidates, blockers, publish impact refs, trace id, and a `runtimeInstanceCreated=false` guarantee.
- Add a deployed system-admin simulation panel with editable sample amount, record id, and actor member id.
- Add repeatable R32 evidence proving two payloads choose different paths and no runtime instance is created.

Exit criteria:

- A system administrator can configure a condition-branch flow, run simulation for at least two payloads, and see different predicted paths.
- Simulation result includes predicted approvers, blockers or no-blocker state, impact refs, and trace id.
- Simulation writes only `un_flow_simulation_log` and does not create `un_flow_instance`.
- Deployed browser evidence shows the simulation panel and result without document-level horizontal overflow.
- REQ-9.5 moves from open sub-capability to first-loop evidence only; broader workflow coverage remains PARTIAL.

Implemented scope:

- Backend simulation now evaluates the selected draft/snapshot canvas instead of returning a fixed demo path.
- Condition edges read sample `fieldValues` and choose different branches for `amount >= 100000` and `amount < 100000`.
- Simulation output includes step trace, condition decision, predicted approvers, blocker items, impact refs, trace id, and `runtimeInstanceCreated=false`.
- Frontend system-admin flow designer includes editable sample amount, record id, actor, and a visible result panel.
- R32 also fixed a real deployed layout defect: system-admin content and large flow canvases no longer create document-level horizontal overflow; the canvas scrolls inside its panel.

Evidence:

- `scripts/recovery-r32-flow-simulation-smoke.ps1` PASS.
- `docs/evidence/recovery/r32-flow-simulation-result.json`
- `docs/evidence/recovery/r32-flow-simulation-2026-06-30.md`
- `docs/evidence/recovery/screenshots/r32-flow-simulation/flow-simulation-api-audit.json`
- `docs/evidence/recovery/screenshots/r32-flow-simulation/flow-simulation-browser-audit.json`
- `docs/evidence/recovery/screenshots/r32-flow-simulation/desktop-flow-simulation.png`
- `docs/evidence/recovery/screenshots/r32-flow-simulation/mobile-flow-simulation.png`

Proven:

- High amount path: submit review -> amount condition -> manager review -> end.
- Low amount path: submit review -> amount condition -> end.
- High path predicts two approvers; low path predicts one approver.
- Missing approver blocker returns `E_SIM_APPROVER_EMPTY`.
- Simulation does not create runtime flow instances.
- Deployed browser uses `/assets/index-DSRIRs9p.js` and `/assets/index-BbjPFRgk.css`; desktop and mobile document overflowX are `0`.

Still partial:

- Broader node library behavior, timers, external API nodes, field update nodes, publish impact breadth, runtime approval closure, and user acceptance remain open.
- REC-P0-033 print template visual designer now has first-loop evidence in R33, but broader print/PDF fidelity remains partial.

## Batch R33: REC-P0-033 Print Template Visual Designer First Loop

Status: accepted as first-loop evidence; REQ-9, REQ-14.7, REQ-5.4, and REQ-5.11 remain PARTIAL.

Reason:

- The no-code platform target includes print template design and runtime print/export. A static print mockup or admin-only template table is not a usable system feature.
- The first acceptable loop must connect admin template design, draft/published separation, module field binding, runtime record permissions, and deployed browser usability.

Implemented scope:

- Extended print template save/read models with header, footer, detail-table fields, signature labels, page setup, draft payload, and published snapshot metadata.
- Added print template publish-check, publish, and preview endpoints.
- Added runtime record print-preview and print-export endpoints that require readable records and published templates.
- Added system-admin print designer inside module management: field picker, center preview, template actions, page/header/footer/signature/detail settings.
- Added runtime detail actions for print preview and print export, with rendered print preview in the print tab.
- Added responsive print-designer and print-preview styles for desktop/mobile containment.
- Fixed R33 smoke data setup so field/template codes are unique per run instead of colliding with existing module bootstrap data.

Evidence:

- `scripts/recovery-r33-print-template-smoke.ps1` PASS.
- `docs/evidence/recovery/r33-print-template-result.json`
- `docs/evidence/recovery/r33-print-template-2026-06-30.md`
- `docs/evidence/recovery/screenshots/r33-print-template/print-template-api-audit.json`
- `docs/evidence/recovery/screenshots/r33-print-template/print-template-browser-audit.json`
- `docs/evidence/recovery/screenshots/r33-print-template/runtime-print-desktop.png`
- `docs/evidence/recovery/screenshots/r33-print-template/runtime-print-mobile.png`

Proven:

- Admin can save a field-bound print template draft, preview it with sample values, publish-check it, and publish a versioned snapshot.
- Runtime print preview/export uses published template data for a real record and returns merged field/detail/signature content plus export file id.
- Draft runtime access before publish is denied and normal-member admin template write is denied with HTTP 403.
- Browser evidence on deployed asset `/assets/index-ByJqmpyP.js` proves system-admin print designer, runtime print preview/export, desktop overflowX `0`, and mobile overflowX `0`.

Still partial:

- Runtime direct navigation once showed empty state until reload despite API data; follow-up should harden initial runtime refresh behavior if reproducible.
- Export is an HTML/file-id artifact; PDF pagination, print CSS fidelity, page breaks, and real printer/PDF download flow are not proven.
- Rich drag/drop print layout, multiple detail tables, batch print, template rollback UI, accessibility, and broader user acceptance remain open.

## Batch R34: Final Goal Execution Framework V4 Lock

Status: accepted as framework-health evidence; product requirements remain PARTIAL.

Reason:

- User feedback made clear that the framework itself was still too weak: multiple rounds could produce pages, APIs, and R-batch evidence without reaching the real final target.
- The immediate need is not future cross-project framework extraction. The current need is an execution lock that keeps this product moving from requirement gaps and deployed role journeys.

Implemented scope:

- `.cursor/architecture/final-goal-framework.md` now carries `v4-current-product-execution` and defines execution locks, next-task selection, and completion-claim lock.
- `.cursor/workflows/final-goal-recovery.md` now requires gap report, framework audit, and `build_plan.nextTasks` before coding.
- `.cursor/templates/task.md` now includes an evidence contract for requirement row, journey gate, browser/API/readback, permission cases, persistence, layout, text/state checks, and release verification.
- `docs/framework/next-execution-ledger.md` now includes `Framework v4 execution lock` and an executable next-work queue: FRC-1A visual/wording audit, FRC-1B page designer, FRC-3A runtime initial refresh, FRC-2A module lifecycle/admin table parity, and FRC-5A print/PDF/launch split.
- Added `scripts/final-goal-framework-audit.ps1` to check that unfinished requirements, next tasks, task-card contract fields, and user signoff separation remain visible.

Evidence:

- `scripts/final-goal-framework-audit.ps1` PASS.
- `docs/evidence/final-goal-framework-audit-result.json`
- `docs/evidence/final-goal-framework-audit.md`

Still partial:

- This batch improves execution control only. It does not make the product final-complete.
- At the R34 checkpoint the next target was `FRC-1A Visual and wording role-journey audit`; R35 has since accepted that audit as engineering evidence, so current next work is tracked in `docs/framework/next-execution-ledger.md`.

## Batch R35: REC-P0-035 Visual And Wording Role-Journey Audit

Status: accepted as deployed visual/wording role-journey engineering evidence; product requirements remain PARTIAL.

Reason:

- User feedback repeatedly said the running system still felt crowded, confusing, and not usable even after many feature batches.
- The next framework step had to inspect the deployed product across roles and routes before adding more features, so FRC-1A was treated as a deterministic role-journey audit rather than another page implementation.

Implemented scope:

- Added `scripts/recovery-r35-visual-wording-role-journey-audit.ps1`.
- The script creates disposable real systems, modules, fields, records, and a normal member, then audits admin and normal-member browser routes on desktop 1280x720 and mobile 390x720.
- The audit checks document overflow, clipped panels, control text overflow, stale loading text, placeholder/demo/generic-toast wording, mojibake/private-use characters, normal-member admin shell exposure, duplicate detail-like buttons, high panel density, and mobile action overload.
- Runtime initial loading now does not render empty/filter/table/detail content alongside the loading state before live data exists.
- Platform system cards now use the card as the primary entry action instead of duplicating a same-meaning `杩涘叆绯荤粺` button inside each card.

Evidence:

- `scripts/recovery-r35-visual-wording-role-journey-audit.ps1` PASS.
- `docs/evidence/recovery/r35-visual-wording-role-journey-result.json`
- `docs/evidence/recovery/r35-visual-wording-role-journey-2026-06-30.md`
- `docs/evidence/recovery/screenshots/r35-visual-wording-role-journey/visual-wording-role-journey-audit.json`
- Release verification passed after packaging with deployed frontend asset `/assets/index-Bnn6q9PB.js`.

Proven:

- 16 role routes and 32 desktop/mobile route combinations passed with `failureCount=0` and `warningCount=0`.
- Admin and normal-member paths were both inspected.
- Normal-member platform/system admin routes render denied states rather than admin shells.
- Desktop and mobile document overflow is `0` for inspected routes.
- No stale loading text, obvious placeholder/demo/generic-toast wording, duplicate detail actions, mojibake, or role-shell leakage was found after settle.

Still partial:

- This is a deployed usability audit slice, not final product completion.
- FRC-1B page designer module-entry/runtime readonly proof, FRC-3A runtime first-navigation/list-detail hardening, FRC-2A module lifecycle/admin table parity, FRC-5A print/PDF/launch-rule split, broader requirement coverage, and user signoff remain open.

## Batch R36: REC-P0-036 Page Designer Module Entry And Runtime Readonly

Status: accepted as deployed FRC-1B engineering evidence; product requirements remain PARTIAL.

Reason:

- R30 proved the page schema API/runtime contract, but the page designer still lived in the dashboard/home panel and used the first module instead of the selected module workflow.
- FRC-1B needed deployed browser proof that a system admin can reach page design from module management and that a normal member sees readonly runtime fields with hidden fields removed.

Implemented scope:

- Added `listModulePageDesigns` frontend API.
- Moved module page designer from the dashboard/home configuration panel into each selected module work panel in system backend module management.
- Renamed the dashboard panel to `棣栭〉閰嶇疆` and removed the module page designer surface from that area.
- Added `scripts/recovery-r36-page-designer-module-entry-readonly-smoke.ps1`.

Evidence:

- `scripts/recovery-r36-page-designer-module-entry-readonly-smoke.ps1` PASS.
- `docs/evidence/recovery/r36-page-designer-module-entry-readonly-result.json`
- `docs/evidence/recovery/r36-page-designer-module-entry-readonly-2026-06-30.md`
- `docs/evidence/recovery/screenshots/r36-page-designer-module-entry-readonly/page-designer-browser-audit.json`
- Release verification passed on deployed assets `/assets/index-D7YKdz44.js` and `/assets/index-C4k4CRmL.css`.

Proven:

- Admin configured/published module page `main`; page publish-check passed.
- Admin and normal runtime schema versions both equal `PAGE_v1782816529664` in the accepted run.
- System backend module management shows `妯″潡椤甸潰璁捐鍣╜, selected module, selected page, and schema read success together.
- Dashboard/home configuration shows `棣栭〉閰嶇疆` and no longer shows the module page designer.
- Normal member browser runtime form renders readonly fields on desktop and mobile, hidden `secretNote` does not leak, and browser overflow count is `0`.
- Direct normal-member record create returns HTTP `403`.
- Cleanup deleted systems `722` and `723`.

Still partial:

- Page designer remains partial for richer component catalog, copy/drag-drop, validation-error focus, and broader user acceptance.
- FRC-3A runtime first-navigation/list-detail hardening, FRC-2A module lifecycle/admin table parity, FRC-5A print/PDF/launch-rule split, broader requirement coverage, and user signoff remain open.

## Batch R37: REC-P0-037 Runtime First Navigation And List/Detail Usability

Status: accepted as deployed FRC-3A engineering evidence; product requirements remain PARTIAL.

Reason:

- Earlier deployed browser evidence showed the runtime page could require manual reload or temporarily show stale/empty/loading content after direct navigation even though API data existed.
- FRC-3A needed proof from a realistic normal-member entry: deployed login form, direct non-hash runtime URL, first-load list/detail, print preview, mobile containment, readback, and permission-negative create.

Implemented scope:

- Scoped runtime record frontend state to the active system context.
- Added guarded `currentLiveData()` access so stale live data from another system is not rendered during a new context load.
- Added a dedicated runtime loading state before context data exists instead of rendering an empty/stale shell.
- Cleared record-scoped print preview state when the active record changes.
- Ensured post-save active record selection follows the saved/created record.
- Added `scripts/recovery-r37-runtime-first-navigation-smoke.ps1`.

Evidence:

- `scripts/recovery-r37-runtime-first-navigation-smoke.ps1` PASS.
- `docs/evidence/recovery/r37-runtime-first-navigation-result.json`
- `docs/evidence/recovery/r37-runtime-first-navigation-2026-06-30.md`
- `docs/evidence/recovery/screenshots/r37-runtime-first-navigation/runtime-first-navigation-browser.json`
- `docs/evidence/recovery/screenshots/r37-runtime-first-navigation/desktop-direct-runtime-first-navigation.png`
- `docs/evidence/recovery/screenshots/r37-runtime-first-navigation/mobile-direct-runtime-first-navigation.png`
- Release verification passed on deployed assets `/assets/index-CDOOMXu_.js` and `/assets/index-W1IbOf1-.css`.

Proven:

- Normal member logged in through the deployed `/login` form.
- Desktop `1280x720` and mobile `390x720` direct `/systems/726/modules` first navigation normalized to `#/systems/726/modules`.
- Runtime table, first row, detail panel, and published print preview rendered on first navigation without manual reload.
- Record `R37 First Navigation Record 0630191726034_a6f571` was visible in list/detail/print preview.
- Browser result count `2`, blocker count `0`, overflow count `0`.
- Normal-member API readback found `1` matching record.
- Direct normal-member record create returned HTTP `403`.
- Cleanup deleted systems `726` and `727`.

Still partial:

- R37 is not final product completion and does not close full runtime CRUD breadth, admin table parity, batch actions, rich detail tabs, attachment/history/approval/log actions, richer print/PDF fidelity, broader mobile task usability, or user signoff.
- FRC-2A module lifecycle/admin table parity, FRC-5A print/PDF/launch-rule split, broader requirement coverage, and user signoff remain open.

## Batch R38: REC-P0-038 Module Lifecycle And Admin Table Parity

Status: accepted as deployed FRC-2A engineering evidence; product requirements remain PARTIAL.

Reason:

- FRC-2A needed the system administrator module-management workflow to stop being only a stacked card shell and cover module lifecycle, admin table parity, list scenes/schema, actions, import/export, publish-check, publish, rollback, readback, mobile containment, and permission negatives.
- The earlier module management area had fields/page/print/action pieces, but scene/list-schema and import/export were not visible in the deployed browser workflow.

Implemented scope:

- Added frontend API bindings for module update, action list, scene list/save, admin list schema, and import/export config load/save.
- Added a module admin table inside system backend module management with selected-row state, publish/status/version columns, and stable test attributes.
- Added a module lifecycle panel for name/code/group/status updates.
- Added list-scene/schema and import/export panels inside the selected module workflow.
- Added direct admin subsection routing: `/systems/{systemId}/admin/module-config`.
- Added `scripts/recovery-r38-module-lifecycle-admin-table-smoke.ps1`.

Evidence:

- `scripts/recovery-r38-module-lifecycle-admin-table-smoke.ps1` PASS.
- `docs/evidence/recovery/r38-module-lifecycle-admin-table-result.json`
- `docs/evidence/recovery/r38-module-lifecycle-admin-table-2026-06-30.md`
- `docs/evidence/recovery/screenshots/r38-module-lifecycle-admin-table/desktop-admin-module-lifecycle.png`
- `docs/evidence/recovery/screenshots/r38-module-lifecycle-admin-table/mobile-admin-module-lifecycle.png`
- Release verification passed on deployed assets `/assets/index-i69FrJ3O.js` and `/assets/index-CsLMbxnd.css`.

Proven:

- API readback proved module lifecycle update, action readback, list schema with `3` columns, `6` filters, `1` sorter, import/export mapping count `3`, publish-check pass, published version `MODULE_v1782820734922`, and rollback result `ROLLED_BACK`.
- Browser desktop and mobile direct `/systems/{systemId}/admin/module-config` showed module admin table, selected module workspace, lifecycle panel, scene config, and import/export config.
- Browser result count `2`, blocker count `0`, overflow count `0`.
- Normal-member runtime create returned HTTP `403`.
- Normal-member backend module-action read returned HTTP `403`.
- Cleanup deleted systems `735` and `736`.

Still partial:

- R38 is not final product completion and does not close full print/PDF fidelity, full runtime data-record breadth, all admin list batch/empty/error/accessibility states, all module permissions/field-type permutations, or user signoff.
- FRC-5A print/PDF/launch-rule split, FRC-4A runtime record breadth/detail-action closure, broader requirement coverage, and user signoff remain open.

## Batch R39: REC-P0-039 Print/PDF Fidelity And Launch Rule Split

Status: accepted as deployed FRC-5A engineering evidence; product requirements remain PARTIAL.

Reason:

- R33 proved only a first-loop print designer and runtime export file id.
- FRC-5A required the print/export path to expose page setup, pagination CSS, print CSS, published-template separation, runtime permission, and a launch-rule split instead of treating REQ-14 as one merged claim.

Implemented scope:

- Backend print template preview/export now returns normalized page setup, `exportMeta`, `HTML_PRINT` format, content type, CSS readiness, pagination readiness, estimated page count, and print-ready HTML.
- Backend print-template publish-check now validates page setup and returns split warnings for missing detail table and signature area.
- Frontend module print designer now exposes paper, orientation, and margins.
- Runtime print preview/export now displays page setup and export boundary metadata.
- Added print media CSS for browser print, page-break avoidance, table header repeat, and print-only cleanup.
- Added `scripts/recovery-r39-print-pdf-launch-rule-smoke.ps1`.

Evidence:

- `scripts/recovery-r39-print-pdf-launch-rule-smoke.ps1` PASS.
- `docs/evidence/recovery/r39-print-pdf-launch-rule-result.json`
- `docs/evidence/recovery/r39-print-pdf-launch-rule-2026-06-30.md`
- `docs/evidence/recovery/screenshots/r39-print-pdf-launch-rule/desktop-admin-print-designer.png`
- `docs/evidence/recovery/screenshots/r39-print-pdf-launch-rule/mobile-admin-print-designer.png`
- `docs/evidence/recovery/screenshots/r39-print-pdf-launch-rule/desktop-runtime-print-export.png`
- `docs/evidence/recovery/screenshots/r39-print-pdf-launch-rule/mobile-runtime-print-export.png`
- Release verification passed on deployed assets `/assets/index-CXChJ5rg.js` and `/assets/index-DL6dc6oi.css`.

Proven:

- Warning-template launch-rule split returned `warningCount=2`.
- Draft runtime preview returned HTTP `403`.
- Publish-check passed with impact types `REFRESH_TEMPLATE`, `PRINT_READY_HTML`, and `CHECK_RUNTIME_READ`.
- Runtime preview used published version `PRINT_TEMPLATE_v1782822414358`.
- Runtime export returned file id `print_print_r39_0630202644_47bf2f_348_1782822414590.html`, format `HTML_PRINT`, content type `text/html; charset=utf-8`, `printCssReady=true`, `paginationReady=true`, estimated page count `1`, and HTML containing `@page`, `@media print`, page-break CSS, and record values.
- Browser result count `4`, blocker count `0`, overflow count `0`.
- Normal-member admin template write returned HTTP `403`.
- Cleanup deleted systems `745` and `746`.

Still partial:

- R39 proves a print-ready HTML export boundary, not binary PDF generation.
- It does not close all REQ-14.x launch rules, broader runtime record breadth, full print/PDF product expectations, or user signoff.
- FRC-4A runtime record breadth/detail-action closure, broader requirement coverage, and user signoff remain open.

## Batch R40: REC-P0-040 Runtime Record Breadth And Detail-Action Closure

Status: accepted as deployed FRC-4A engineering evidence; product requirements remain PARTIAL.

Reason:

- R37 proved the runtime page could first-load list/detail/print preview, but it did not close the daily record work surface.
- FRC-4A needed proof that runtime records handle real attachments, drafts, edit preservation, detail tabs, operation history, permitted actions, permission negatives, and desktop/mobile usability from a normal-member login.

Implemented scope:

- Runtime detail now returns separate `attachments`, `print`, and `operationLogs` tabs with attachment metadata, print template metadata, and latest history rows.
- Runtime save/update now preserves attachments when the caller does not submit `attachmentIds`.
- Frontend runtime edit/create panel uses real file upload and keeps existing attachments during edit.
- Runtime operation logs are read only from the history/log tab payload, not from unrelated print/attachment payloads.
- Attachment detail and history sections have browser-testable markers.
- Added `scripts/recovery-r40-runtime-record-detail-action-smoke.ps1`.

Evidence:

- `scripts/recovery-r40-runtime-record-detail-action-smoke.ps1` PASS.
- `docs/evidence/recovery/r40-runtime-record-detail-action-result.json`
- `docs/evidence/recovery/r40-runtime-record-detail-action-2026-06-30.md`
- `docs/evidence/recovery/screenshots/r40-runtime-record-detail-action/runtime-record-detail-action-browser.json`
- Release verification passed on deployed assets `/assets/index-DQnwg1ks.js` and `/assets/index-DL6dc6oi.css`.

Proven:

- Real upload returned file id `file_a2a86657_1782824822227` and file name `r40-runtime-attachment-0630210606836_d0fcc5.txt`.
- Draft readback preserved attachment ids.
- Record update without `attachmentIds` preserved existing attachment metadata.
- Detail tabs were `base`, `attachments`, `print`, and `operationLogs`.
- Detail history count was `2`; history total before action was `2`.
- Delete returned `DELETED`.
- Archive action was accepted and history total after action was `3`.
- Normal-member backend admin action API returned HTTP `403`.
- Browser result count `6`, blocker count `0`, overflow count `0`.
- Cleanup deleted systems `755` and `756`.

Still partial:

- R40 is not final product completion.
- Runtime approval detail breadth, import/export rollback UX, file preview/version/storage failure states, batch actions, empty/error/accessibility states, broader mobile task usability, and user signoff remain open.
- According to `docs/evidence/final-requirement-gap-report.md`, the next executable work returns to FRC-1 product surfaces/page usability, tracked as `REC-P0-041`.

## Batch R41: REC-P0-041 Page/Home And Component Usability Closure

Status: accepted as deployed FRC-1C engineering evidence; product requirements remain PARTIAL.

Reason:

- The user-reported problem is that page surfaces still felt like copied prototype shells: crowded, mixed, and not usable as a coherent system.
- FRC-1C needed proof from the deployed product that home/dashboard, page designer, and runtime page-designed components work as visible, stateful product surfaces.

Implemented scope:

- Runtime schema form fields now carry stable test markers, show inline validation errors, and focus the first invalid field after save.
- Runtime record detail no longer silently falls back to a fabricated frontend detail object when the real detail API fails.
- System dashboard/home, system-admin home config, module page designer, and runtime schema forms now expose stable browser-audit markers.
- Page-schema readonly disabled reasons now use Chinese user-facing copy.
- Added `scripts/recovery-r41-page-home-component-usability-smoke.ps1`.

Evidence:

- `scripts/recovery-r41-page-home-component-usability-smoke.ps1` PASS.
- `docs/evidence/recovery/r41-page-home-component-usability-result.json`
- `docs/evidence/recovery/r41-page-home-component-usability-2026-06-30.md`
- `docs/evidence/recovery/screenshots/r41-page-home-component-usability/page-designer-browser-audit.json`
- Release verification passed on deployed assets `/assets/index-CbTvbGMT.js` and `/assets/index-CQNpsfh0.css`.

Proven:

- System dashboard rendered real home overview and operations panels.
- Admin runtime schema form showed required validation for `title`, `status`, and `publicName`, then cleared validation after a successful save.
- Module page designer rendered in selected module management and displayed the published schema preview.
- Normal-member runtime schema version matched admin published schema `PAGE_v1782826653390`.
- Normal-member schema hid `secretNote`, removed the hidden component, marked 5 fields readonly, and rejected direct create with HTTP `403`.
- Browser result count `8`, overflow count `0`.
- Cleanup deleted systems `759` and `760`.

Still partial:

- R41 is not final product completion.
- User acceptance, broader page designer component breadth, accessibility, richer role-specific home usability, and full requirement coverage remain open.

## Batch R42: REC-P0-042 Page Designer Component Breadth And Accessibility Closure

Status: accepted as deployed FRC-1D engineering evidence; product requirements remain PARTIAL.

Reason:

- R41 proved validation and basic page/home/schema state, but the page designer still risked being a thin copied schema preview.
- FRC-1D needed deployed proof that component ordering/copy/visibility, mobile preview, focusability, runtime validation preservation, and normal-member permission state work together.

Implemented scope:

- Module page designer now uses a structured component draft instead of a comma-separated component list.
- Added a component workbench with component rows, copy, reorder, visibility toggle, save layout, stable browser markers, and mobile preview.
- Runtime record frontend now derives form fields from page schema where available and enforces action/field permission in the UI: `record.create` disabled when backend action permission is missing, create/edit fields become readonly when mutation is blocked, save/draft/submit and attachment upload/remove are disabled with reasons.
- R42 browser script now accepts the correct permission behavior: normal-member create disabled, or if the form opens, all fields must be readonly.
- Added `scripts/recovery-r42-page-designer-component-accessibility-smoke.ps1`.

Evidence:

- `scripts/recovery-r42-page-designer-component-accessibility-smoke.ps1` PASS.
- `docs/evidence/recovery/r42-page-designer-component-accessibility-result.json`
- `docs/evidence/recovery/r42-page-designer-component-accessibility-2026-06-30.md`
- `docs/evidence/recovery/screenshots/r42-page-designer-component-accessibility/page-designer-browser-audit.json`
- Release verification passed on deployed assets `/assets/index-B6v_geL0.js` and `/assets/index-B5pUhLMV.css`.

Proven:

- Component workbench rendered with `componentReadbackCount=5`, `actionButtonCount=22`, and `focusableControlCount=20`.
- Browser copy/reorder/hide/save persisted copied component `toolbar_copy` and hidden component state through API readback.
- Mobile preview rendered in the module page designer.
- Runtime validation still caught `title`, `status`, and `publicName`, then cleared after successful save.
- Normal-member schema hid `secretNote`, removed hidden component, marked 5 fields readonly, browser create was disabled on desktop/mobile, and direct create returned HTTP `403`.
- Browser result count `8`, overflow count `0`.
- Cleanup deleted systems `767` and `768`.

Still partial:

- R42 is not final product completion.
- Role-specific home/dashboard usefulness, platform-vs-system first impression, whole-path usability, broader requirement coverage, and user signoff remain open.
- Next executable work is `REC-P0-043` / FRC-1E.

## Batch R43: REC-P0-043 Role-Specific Home And Whole-Path Usability Closure

Status: accepted.

Reason:

- The user's main complaint is still about whether the running system is coherent and usable by people, not whether a single component workbench passes.
- After R42, the next FRC-1 gap is role-specific first-screen usefulness and whole-path visual/task coherence across platform/system/admin/runtime paths.

Tasks:

- Add and run a deployed FRC-1E browser/API script.
- Start from realistic login/session context for platform/system admin and normal member.
- Inspect platform workbench, platform/admin boundaries, system dashboard, module admin page, normal-member runtime page, and related message/todo/work entry fit.
- Remove or disable any mixed placeholder, duplicate entry, stale/generic wording, or confusing disabled state found by the script.
- Verify permission negatives, desktop/mobile containment, deployed asset hash, and cleanup.

Exit criteria:

- Role paths are visibly task-oriented and not mixed piles.
- Platform and system contexts remain separated.
- Normal-member routes do not expose admin surfaces.
- Disabled/no-permission states match backend state and are understandable.
- Browser/API evidence is linked back to the task card and ledgers.

Accepted evidence:

- `scripts/recovery-r43-role-home-whole-path-usability-smoke.ps1` PASS.
- `docs/evidence/recovery/r43-role-home-whole-path-usability-result.json`
- `docs/evidence/recovery/r43-role-home-whole-path-usability-2026-06-30.md`
- `docs/evidence/recovery/screenshots/r43-role-home-whole-path-usability/role-home-whole-path-usability-audit.json`
- Release verification passed on deployed assets `/assets/index-CNWWdmkH.js` and `/assets/index-B5pUhLMV.css`.

Proven:

- 16 role routes across desktop/mobile produced `resultCount=32`, `failureCount=0`, and `warningCount=0`.
- Normal platform workbench no longer exposes platform-admin wording or the create-system entry.
- Normal member admin routes show denied pages instead of admin shells.
- System dashboard/runtime/work/todos/messages/admin routes did not mix platform/admin/runtime shell markers.
- Normal direct record create and admin action read returned HTTP `403`.
- Cleanup deleted systems `771` and `772`.

Still partial:

- R43 is not final product completion.
- FRC-2 no-code configuration depth, runtime/workflow/integration/operations depth, requirement coverage, and user signoff remain open.
- Next executable work is `REC-P0-044` / FRC-2B.

## Batch R44: REC-P0-044 No-Code Configuration Depth And Permission Preview Closure

Status: accepted as engineering evidence only.

Reason:

- After R43, role first screens are cleaner, but the system still needs deeper no-code configuration proof before it can be called broadly usable.
- The next gap report bucket is FRC-2: fields, dictionaries, module groups, menus, permissions, and effective permission preview.

Tasks:

- Add and run a deployed FRC-2B browser/API script.
- Configure module groups, multiple field types, dictionary-bound fields, and role permission rules.
- Verify admin readback and effective permission preview.
- Verify normal-member runtime navigation/fields/actions match the preview.
- Verify forbidden admin/runtime mutation negatives, desktop/mobile containment, deployed asset hash, and cleanup.

Exit criteria:

- Admin configuration writes and reads back real data.
- Permission preview matches normal-member runtime behavior.
- Runtime navigation hides unauthorized groups/modules/fields/actions.
- Browser/API evidence is linked back to task card and ledgers.

Evidence:

- `scripts/recovery-r44-no-code-permission-preview-smoke.ps1`
- `docs/evidence/recovery/r44-no-code-permission-preview-result.json`
- `docs/evidence/recovery/r44-no-code-permission-preview-2026-06-30.md`
- Browser audit: `docs/evidence/recovery/screenshots/r44-no-code-permission-preview/no-code-permission-preview-browser-audit.json`

Result:

- PASS on deployed release `http://127.0.0.1:18131`.
- Deployed release verification passed with frontend asset `/assets/index-y9MJVo-u.js` and backend jar hash `C3EA89E3F8FAF39BF4D2A6398DAC4FD613949C79F296BCAACE9546F78263CAA9`.
- R44 configured module group visibility, visible/hidden modules, dictionary-bound `SELECT` field, role action/field/data-scope permissions, and effective permission preview.
- Normal runtime matched preview: hidden field did not leak, create action disabled, direct create/admin config read returned HTTP `403`, desktop/mobile browser blockers `0`, overflow `0`, cleanup `783/784`.
- R44 also fixed role context mismatch: `effectiveRoleIds` now carries both role IDs and role codes so runtime group visibility can match persisted `visibleRoleIds` without breaking system-admin role-code checks.

Still partial:

- FRC-2 remains partial for nested menu behavior, disabled/unpublished permutations, formula/summary or other specialized widgets if in scope, broader cross-surface admin usability, and user acceptance.
- Next executable work is `REC-P0-046` / FRC-1F.

## Batch R45: REC-P0-045 Field Type Breadth, Dictionary Impact, And Menu Configuration Closure

Status: accepted as engineering evidence only.

Reason:

- R44 proves the first no-code permission-preview loop, but not the full no-code depth requested by the final target.
- Remaining FRC-2 gaps include field-specific configuration breadth, dictionary versioning/reference impact, and explicit menu ordering/visibility management beyond module group visibility.

Tasks:

- Filled the REC-P0-045 requirement confirmation contract before coding; screenshots are visual evidence only.
- Added and ran a deployed FRC-2C browser/API script.
- Configured and read back 12 field types and field-specific options: long text, number, date/datetime, single/multi select, user/department, attachment/image, relation, and child-table boundary metadata.
- Proved dictionary publish/version/reference impact and disabled item behavior.
- Proved group/module ordering and visibility in admin/runtime readback.
- Verified normal-member runtime effects, permission negatives, desktop/mobile containment, cleanup, and kept `gates.user_script_passed=false`.

Evidence:

- `scripts/recovery-r45-field-dict-menu-smoke.ps1`
- `docs/evidence/recovery/r45-field-dict-menu-result.json`
- `docs/evidence/recovery/r45-field-dict-menu-2026-07-01.md`
- Browser audit: `docs/evidence/recovery/screenshots/r45-field-dict-menu/field-dict-menu-browser-audit.json`

Result:

- PASS on deployed release `http://127.0.0.1:18131`.
- Release verification passed with frontend asset `/assets/index-CejDtUdM.js`.
- R45 configured 12 field types, read back default/validation/type metadata, published dictionary version `DICT_TYPE_v1782871517684`, proved 2 field refs, 1 disabled item, active runtime options excluding `DISABLED_OLD`, visible/hidden group versions, normal runtime schema column count `15`, normal-member admin HTTP `403`, browser blockers `0`, overflow `0`, cleanup `794/795`.

Still partial:

- R45 is not final product completion.
- FRC-1 product-surface rows, richer page definitions, component catalog, live home/runtime usability, workflow/integration/operations depth, requirement coverage, and user signoff remain open.
- Next executable work is `REC-P0-046` / FRC-1F.

## Batch R46: REC-P0-046 Page Definition, Component Catalog, And Home Runtime Surface Closure

Status: accepted as engineering evidence only.

Reason:

- R45 strengthened no-code configuration depth, but the earliest gap report bucket is still FRC-1 Missing Product Surfaces.
- The user's repeated complaint is that the running product feels like stacked pages and mixed functions, so the next slice must prove page/home/runtime surfaces as human-usable role journeys.

Tasks:

- Fill the REC-P0-046 requirement confirmation contract before coding; screenshots are visual evidence only.
- Add a deployed FRC-1F browser/API script.
- Configure page components, ordering/visibility, and home/dashboard content from admin surfaces.
- Read back and publish/expose page/home configuration to runtime.
- Log in as normal member and verify runtime/home surfaces match the configured role-visible page definition without admin clutter.
- Verify forbidden admin access, no fake/generic placeholder states, desktop/mobile containment, cleanup, and keep `gates.user_script_passed=false`.

Evidence:

- `scripts/recovery-r46-page-surface-smoke.ps1`
- `docs/evidence/recovery/r46-page-surface-result.json`
- `docs/evidence/recovery/r46-page-surface-2026-07-01.md`
- Browser audit: `docs/evidence/recovery/screenshots/r46-page-surface/page-surface-browser-audit.json`

Result:

- PASS on deployed release `http://127.0.0.1:18131`.
- Configured home title/widgets, proved admin save/readback, runtime readback, normal-member readback, and home publish-check.
- Configured/published page `main` with 5 components and normal runtime readback at published version `PAGE_v1782872581003`.
- Normal runtime hid the secret component/field, kept visible components `toolbar/list/detail/chart`, and normal-member admin writes returned HTTP `403`.
- Browser audit covered 10 admin/normal desktop/mobile routes with overflow `0`, blockers `0`, and no audited admin/runtime marker mixing.
- Cleanup deleted systems `798` and `799`.

Still partial:

- R46 is not final product completion.
- Runtime daily-use depth, file/import/export states, workflow/integration/operations breadth, full requirement coverage, user acceptance, and `gates.user_script_passed` remain open.
- Next executable work is `REC-P0-047` / FRC-3B.

## Batch R47: REC-P0-047 Runtime Daily-Use Record, File, Import/Export, And State Closure

Status: accepted as engineering evidence only.

Reason:

- R46 proves the configured page/home/runtime surfaces can stay separated, but the user target is still a human-usable product.
- The next gap is the normal member's daily runtime path: records, files, import/export task state, empty/error/no-permission states, and mobile containment.

Tasks:

- Fill the REC-P0-047 requirement confirmation contract before coding; screenshots are visual evidence only.
- Add a deployed FRC-3B browser/API script.
- Configure and publish a runtime module with list scene, fields, file-capable behavior, and permission variations.
- Log in as normal member and prove create/edit/detail/readback, real file binding/readback, import/export task/result state, and reload/re-login survival.
- Verify empty/error/no-permission states, denied direct APIs, no fake/generic placeholder states, desktop/mobile containment, cleanup, and keep `gates.user_script_passed=false`.

Evidence:

- `scripts/recovery-r47-runtime-daily-use-smoke.ps1`
- `docs/evidence/recovery/r47-runtime-daily-use-result.json`
- `docs/evidence/recovery/r47-runtime-daily-use-2026-07-01.md`
- Browser audit: `docs/evidence/recovery/screenshots/r47-runtime-daily-use/runtime-daily-use-browser-audit.json`

Result:

- PASS on deployed release `http://127.0.0.1:18131`.
- R47 configured/published runtime module `297`, proved normal-member empty state, record create/edit/detail readback, real file upload/binding, import precheck/confirm task success, export task success/result file, hidden-field non-leakage, permission negatives, browser blockers `0`, overflow `0`, and cleanup `803/804/805`.

Still partial:

- R47 is not final product completion.
- Workflow/approval/todo/message/flow surface closure, integration/operations breadth, broader mobile/accessibility depth, full requirement coverage, user acceptance, and `gates.user_script_passed=false` remain open.
- Next executable work is `REC-P0-048` / FRC-4B.

## Batch R48: REC-P0-048 Workflow, Approval, Todo/Message, And Flow Surface Closure

Status: accepted as engineering evidence only.

Reason:

- R47 proves a normal member can use runtime records/files/import-export on the deployed product, but daily product use still depends on workflow, approval, todo, message, and flow-designer surfaces working as one role journey.
- Previous evidence has approval slices, but the current final-goal framework requires an active requirement-linked FRC-4B pass that ties workflow configuration, requester submit, approver todo/message, terminal state, permission negatives, browser containment, and cleanup together.

Tasks:

- Fill the REC-P0-048 requirement confirmation contract before coding; screenshots are visual evidence only.
- Add a deployed FRC-4B browser/API script.
- Configure or publish a simple approval flow and verify simulation/publish metadata.
- Submit a runtime record as requester, verify flow instance/task/history plus todo/message creation, then process it as approver through deployed todo/message surfaces.
- Verify requester/normal-member forbidden actions, duplicate approval idempotency, no fake/generic placeholder states, desktop/mobile containment, cleanup, and keep `gates.user_script_passed=false`.

Evidence:

- `scripts/recovery-r48-workflow-message-flow-smoke.ps1`
- `docs/evidence/recovery/r48-workflow-message-flow-result.json`
- `docs/evidence/recovery/r48-workflow-message-flow-2026-07-01.md`
- Browser audit: `docs/evidence/recovery/screenshots/r48-workflow-message-flow/workflow-message-flow-browser-audit.json`

Result:

- PASS on deployed release `http://127.0.0.1:18131`.
- R48 proved published flow `flow_v1782874494680`, publish-check impact refs `2`, simulation `runtimeInstanceCreated=false`, requester pending todo `0`, approver pending todo `1`, approver message `1`, requester approve HTTP `403`, normal admin flow HTTP `403`, approval action `HANDLED`, duplicate action same trace id, terminal record/sidebar `APPROVED`, browser before/after results `8/6`, overflow `0`, blockers `0`, and cleanup `812/813/814`.

Still partial:

- R48 is not final product completion.
- OpenAPI/assistant/integration breadth, operations breadth, broader workflow variants, full requirement coverage, user acceptance, and `gates.user_script_passed=false` remain open.
- Next executable work is `REC-P0-049` / FRC-4C.

## Batch R49: REC-P0-049 OpenAPI, Assistant, And Integration Boundary Closure

Status: accepted as engineering evidence only.

Reason:

- R48 proves workflow collaboration closure, but the product still cannot be considered usable as a complete system until external integration and assistant boundaries are safe, visible, and auditable.
- Previous OpenAPI and AI evidence exists in slices, but the current final-goal framework requires a requirement-linked FRC-4C pass tying OpenAPI scopes/secrets/call logs, assistant preview/confirm/reject behavior, permission negatives, browser containment, and cleanup together.

Tasks:

- Fill the REC-P0-049 requirement confirmation contract before coding; screenshots are visual evidence only.
- Add a deployed FRC-4C browser/API script.
- Configure an OpenAPI app and verify secret masking or rotation behavior.
- Execute allowed and denied scoped API calls, then read back call logs/audit.
- Open the assistant as normal member, verify scoped session/readback and write preview/confirm/reject behavior.
- Verify forbidden admin/integration mutation, no fake/generic placeholder states, desktop/mobile containment, cleanup, and keep `gates.user_script_passed=false`.

Evidence:

- `scripts/recovery-r49-openapi-assistant-integration-smoke.ps1`
- `docs/evidence/recovery/r49-openapi-assistant-integration-result.json`
- `docs/evidence/recovery/r49-openapi-assistant-integration-2026-07-01.md`
- Browser audit: `docs/evidence/recovery/screenshots/r49-openapi-assistant-integration/openapi-assistant-integration-browser-audit.json`

Result:

- PASS on deployed release `http://127.0.0.1:18131`.
- R49 proved OpenAPI app `52`, runtime record `394`, masked SecretRef `sec_openapi_r49_app_0701133505709_821aad_e662e71b`, secret rotation job `srj_openapi_52_bee0d801`, allowed external create/search/detail, success logs `5`, failed logs `1`, read-only scope denial HTTP `403`, wrong SecretRef denial HTTP `401`.
- R49 proved platform Agent system-write denial `REJECTED_BY_SCOPE`, system Agent policy publish-check, assistant write/work proposed confirmations, write preview/confirm/reject, work draft preview/confirm, normal-member OpenAPI/policy mutation denials HTTP `403`, agent audit logs `12`, confirmation audit logs `6`, browser blockers `0`, overflow `0`, and cleanup `817/818`.

Still partial:

- R49 is not final product completion.
- Callback/webhook/API documentation UX, rate-limit behavior under load, broader assistant flows, operations/log/release breadth, full requirement coverage, user acceptance, and `gates.user_script_passed=false` remain open.
- Next executable work is Framework V6 final usable-system remediation and deployed role-journey audit preparation.

## Batch R50: REC-P0-050 Operations, Logs, Release, And Launch-Rule Closure

Status: accepted as engineering evidence only; not final product completion.

Reason:

- R49 proves integration and assistant boundaries, but the product still needs operator/admin-usable operations, logs, release health, robustness, and launch-rule evidence before it can approach final usability.
- Previous release/log evidence exists in separate slices, but the current final-goal framework requires a requirement-linked FRC-5B pass tying health, logs/audits, settings/risky operations, permission negatives, deployed browser containment, and release evidence together.

Tasks:

- Fill the REC-P0-050 requirement confirmation contract before coding; screenshots are visual evidence only.
- Add a deployed FRC-5B browser/API/release script.
- Verify release health/status, backend/frontend asset identity, Redis/database/schema health, and admin login.
- Produce or locate log/audit rows, then verify list/filter/detail/readback and trace/audit ids.
- Verify operational setting readback or risky-operation confirmation/task state.
- Verify normal-member forbidden admin/ops APIs, masking, no fake/generic placeholder states, desktop/mobile containment, cleanup, and keep `gates.user_script_passed=false`.

Evidence:

- Script: `scripts/recovery-r50-operations-release-log-smoke.ps1`
- Result: `docs/evidence/recovery/r50-operations-release-log-result.json`
- Summary: `docs/evidence/recovery/r50-operations-release-log-2026-07-01.md`
- Browser audit: `docs/evidence/recovery/screenshots/r50-operations-release-log/operations-release-log-browser-audit.json`

Result:

- `status=PASS`
- Release verification PASS.
- Server script exposes `start`, `stop`, `restart`, `status`, `health`.
- Platform/system ops requests create persisted audit rows with traceId and detail readback.
- Forbidden normal-member platform/system ops attempts return `403` and write `FAILURE` audit rows.
- Browser results `6`, overflow `0`, blockers `0`, cleanup `819/820`.

Still partial:

- Final usable-system acceptance is reopened by user feedback.
- This batch remains evidence only; requirement coverage, broad role-journey coherence, and user signoff remain open.

## Batch R51: Framework V6 Final Usable-System Remediation And Role-Journey Audit Preparation

Status: accepted as framework/diagnostic evidence only; not final product completion.

Reason:

- User feedback reports the deployed product is still confusing/unusable after many accepted engineering slices.
- The framework must stop old PASS rows, screenshots, and generated plumbing from masquerading as final product completion.

Tasks:

- Reopen `docs/recovery/final-usable-system-acceptance.md`.
- Downgrade historical journey PASS rows to engineering evidence.
- Upgrade `.cursor/architecture/final-goal-framework.md`, task template, recovery workflow, failure lessons, and current engineering ledger to V6.
- Update `.cursor/session/state.json` next task to framework remediation and role-journey audit preparation.
- Extend `scripts/final-goal-framework-audit.ps1` so the reopen lock is machine-checkable.
- Add and run `scripts/recovery-r51-final-role-journey-gap-audit.ps1`.
- Generate the next requirement-driven coding task from the first recommended gap batch.
- Keep `gates.user_script_passed=false`.

Evidence:

- Script: `scripts/recovery-r51-final-role-journey-gap-audit.ps1`
- Result: `docs/evidence/recovery/r51-final-role-journey-gap-audit-result.json`
- Summary: `docs/evidence/recovery/r51-final-role-journey-gap-audit-2026-07-01.md`

Result:

- `status=PASS`
- `productStatus=FAIL_EXPECTED`
- Requirement rows not closed: `45`
- Final journey partial rows: `8`
- First recommended batch: `FRC-1 Missing Product Surfaces`
- Recommended next task: `REC-P0-052 fresh deployed role-journey closure from FRC-1 Missing Product Surfaces (REQ-5.8, REQ-6.1, REQ-6.3, REQ-6.8, REQ-9)`.

Still partial:

- R51 did not implement product behavior.
- Final product completion, requirement coverage closure, deployed human-usable coherence, and `gates.user_script_passed=false` remain open.

## Batch R52: REC-P0-052 FRC-1 Missing Product Surfaces Fresh Deployed Closure

Status: accepted as deployed engineering evidence only.

Reason:

- R51 shows the first recommended implementation gap is FRC-1: product surfaces, home page, page designer, visual style, and advanced/assistant/command-style entry expectations.
- These rows directly match the user's complaint that pages feel crowded, mixed, and confusing after deployment.

Tasks:

- Fill and keep the REC-P0-052 requirement confirmation contract before coding; screenshots are visual evidence only.
- Add `scripts/recovery-r52-frc1-deployed-surface-closure.ps1`.
- Start from real deployed role entries: platform workspace, system dashboard/home, runtime list/detail/form, system admin page designer/home configuration, and assistant/command entry.
- Prove backend/API readback for configured home/page/runtime/assistant state where the UI claims functionality.
- Verify permission positives and negatives for normal member, system admin, platform member, and platform admin.
- Remove or wire any generic success, stale local state, placeholder, admin-copy leak, stacked panels, or mobile overflow found in the accepted scope.
- Update requirement coverage ledger, gap report, next ledger, and session state after evidence.

Evidence:

- Script: `scripts/recovery-r52-frc1-deployed-surface-closure.ps1`
- Result: `docs/evidence/recovery/r52-frc1-deployed-surface-closure-result.json`
- Summary: `docs/evidence/recovery/r52-frc1-deployed-surface-closure-2026-07-01.md`
- Browser audit: `docs/evidence/recovery/screenshots/r52-frc1-deployed-surface/frc1-deployed-surface-browser-audit.json`

Result:

- `status=PASS`, `productStatus=PARTIAL_ENGINEERING_EVIDENCE_ONLY`.
- Static usability audit: blockers `0`, warnings `0`.
- Fresh R43 role/home whole-path browser audit: results `32`, failures `0`, warnings `0`.
- Fresh R46 page/home/runtime surface browser audit: browserResults `10`, overflow `0`, blockers `0`.
- Print preview frontend/API contract now uses `previewValues`; production frontend no longer emits sample/demo preview payloads.
- Release verification passed on deployed asset `/assets/index-W9bu10bH.js` and CSS `/assets/index-B5pUhLMV.css`.
- Cleanup: `825:DELETE`.

Still partial:

- R52 is engineering evidence only. `REQ-5.8`, `REQ-6.1`, `REQ-6.3`, `REQ-6.8`, `REQ-9`, broader requirement coverage, and `gates.user_script_passed=false` remain open.

## Batch R53: REC-P0-053 FRC-2 No-Code Configuration Coherence Fresh Closure

Status: accepted; engineering evidence only, not final product completion.

Reason:

- R52 refreshed FRC-1 product-surface evidence after user feedback. The next product-critical gap is no-code configuration coherence: modules, fields, dictionaries, menus, permissions, role/member context, and runtime schema must agree.
- These rows decide whether the system is a usable no-code platform rather than a set of separate admin pages.

Tasks:

- Fill and keep the REC-P0-053 requirement confirmation contract before coding; screenshots are visual evidence only.
- Add `scripts/recovery-r53-frc2-no-code-configuration-coherence.ps1`.
- Start from deployed system-admin entries for module, field, dictionary, menu/module group, role permission, and permission preview.
- Prove admin save/readback/publish, normal runtime menu/schema reflection, and permission positives/negatives.
- Remove or wire any generic success, stale local state, placeholder, admin-copy leak, or mismatched permission/menu/field behavior found in the accepted scope.
- Update requirement coverage ledger, gap report, next ledger, and session state after evidence.

Evidence:

- Script: `scripts/recovery-r53-frc2-no-code-configuration-coherence.ps1`
- Result: `docs/evidence/recovery/r53-frc2-no-code-configuration-coherence-result.json`
- Summary: `docs/evidence/recovery/r53-frc2-no-code-configuration-coherence-2026-07-01.md`
- Browser audit: `docs/evidence/recovery/screenshots/r53-frc2-no-code-configuration/no-code-configuration-browser-audit.json`

Result:

- PASS on deployed `http://127.0.0.1:18131`.
- Fresh R38 module lifecycle/admin table evidence passed: columns `3`, filters `6`, sorters `1`, import/export mappings `3`, publish-check passed, rollback `ROLLED_BACK`, forbidden create/admin `403/403`, browser results `2`, overflow `0`, blockers `0`.
- Fresh R44 permission preview/runtime evidence passed: runtime role `1214`, preview denied `record.create`, hidden field rule `HIDDEN`, normal schema columns `5`, secret field leaked `false`, create disabled `true`, forbidden create/admin `403/403`, browser results `4`, overflow `0`, blockers `0`.
- Fresh R45 field/dictionary/menu evidence passed: field count `12`, dictionary disabled item count `1`, active runtime options `ACTIVE/PENDING`, normal schema columns `15`, forbidden admin `403`, browser results `6`, overflow `0`, blockers `0`.

Still partial:

- R53 is no-code configuration coherence evidence only. Runtime user depth, messages/work management breadth, final requirement coverage, user acceptance, and `gates.user_script_passed=false` remain open.

## Batch R54: REC-P0-054 FRC-3 Runtime User Depth Fresh Closure

Status: accepted; engineering evidence only, not final product completion.

Reason:

- R53 refreshed FRC-2 no-code configuration coherence. The next product-critical gap is proving that normal members can actually use runtime records, files, import/export, messages, work management, list/form/detail, and mobile surfaces as one daily-use system.
- These rows decide whether the configured system becomes a usable runtime product rather than admin-only capability evidence.

Tasks:

- Keep the REC-P0-054 requirement confirmation contract complete before coding; screenshots are visual evidence only.
- Add `scripts/recovery-r54-frc3-runtime-user-depth.ps1`.
- Start from deployed normal-member login, system switch, runtime modules, message entry, work-management entry, and mobile-width routes.
- Prove runtime record/file/import-export/message/work states with API readback, permission positives/negatives, reload/re-login where relevant, and browser containment.
- Remove or wire any generic success, stale local state, placeholder, admin-copy leak, or runtime state mismatch found in the accepted scope.
- Update requirement coverage ledger, gap report, next ledger, and session state after evidence.

Evidence:

- Script: `scripts/recovery-r54-frc3-runtime-user-depth.ps1`
- Result: `docs/evidence/recovery/r54-frc3-runtime-user-depth-result.json`
- Summary: `docs/evidence/recovery/r54-frc3-runtime-user-depth-2026-07-01.md`
- Browser audit: `docs/evidence/recovery/screenshots/r54-frc3-runtime-user-depth/runtime-user-depth-browser-audit.json`

Result:

- PASS on deployed `http://127.0.0.1:18131`.
- Fresh R47 runtime daily-use evidence passed: record `422`, attachment count `4`, history count `2`, import `SUCCESS/2`, export `SUCCESS`, hidden-field leak `false`, browser results `10`, overflow `0`, blockers `0`.
- Fresh R48 message/todo/approval evidence passed: todo `76`, message `78`, action `HANDLED`, terminal `APPROVED`, pending after approve `0`, handled after approve `1`, browser before/after `8/6`, overflow `0`, blockers `0`.
- Fresh R43 work/todo/message/mobile surface evidence passed: routes `16`, results `32`, failures `0`, warnings `0`, work/todo/message routes `4/4/4`.

Still partial:

- R54 is runtime user depth evidence only. Workflow variants, OpenAPI/AI depth, operations breadth, final requirement coverage, user acceptance, and `gates.user_script_passed=false` remain open.

## Batch R55: REC-P0-055 FRC-4 Workflow Integration AI Depth Fresh Closure

Status: accepted as deployed engineering evidence only.

Reason:

- R54 refreshed runtime user depth. The next product-critical gap is proving workflow, OpenAPI, and assistant/AI integrations as connected user journeys with permission negatives and persisted readback.
- These rows decide whether the system supports real enterprise automation and integration rather than isolated runtime CRUD.

Tasks:

- Keep the REC-P0-055 requirement confirmation contract complete before coding; screenshots are visual evidence only.
- Add `scripts/recovery-r55-frc4-workflow-integration-ai-depth.ps1`.
- Start from deployed workflow/OpenAPI/assistant entries and normal runtime approval/todo/message surfaces.
- Prove workflow publish/runtime/todo/message closure, OpenAPI scope/secret/log boundaries, assistant preview/confirm/reject, and permission positives/negatives.
- Update requirement coverage ledger, gap report, next ledger, and session state after evidence.

Planned evidence:

- Script: `scripts/recovery-r55-frc4-workflow-integration-ai-depth.ps1`
- Result: `docs/evidence/recovery/r55-frc4-workflow-integration-ai-depth-result.json`
- Summary: `docs/evidence/recovery/r55-frc4-workflow-integration-ai-depth-2026-07-01.md`
- Browser audit: `docs/evidence/recovery/screenshots/r55-frc4-workflow-integration-ai-depth/workflow-integration-ai-browser-audit.json`

Accepted evidence:

- `scripts/recovery-r55-frc4-workflow-integration-ai-depth.ps1` PASS on `http://127.0.0.1:18131`.
- Release health database/schema/Redis `UP`.
- Static usability audit blockers `0`, warnings `0`.
- Fresh R48 workflow/message/approval evidence passed: flow `131`, todo `77`, message `79`, action `HANDLED`, terminal `APPROVED`, duplicate trace same `true`, browser before/after `8/6`, overflow `0`, blockers `0`.
- Fresh R49 OpenAPI/assistant evidence passed: OpenAPI app `54`, record `435`, SecretRef `sec_openapi_r49_app_0701161859351_3df407_fac2bc9a`, rotation job `srj_openapi_54_e43ab661`, success logs `5`, failed logs `1`, read-only denied `403`, wrong secret denied `401`, platform Agent denied `REJECTED_BY_SCOPE`, confirmations `SYSTEM_AGENT_WRITE_CONFIRM/WORK_AGENT_DRAFT_CONFIRM`, browser results `6`, overflow `0`, blockers `0`.

Still partial:

- R55 is workflow/integration/AI depth evidence only. Operations breadth, broader workflow/OpenAPI/AI variants, final requirement coverage, user acceptance, and `gates.user_script_passed=false` remain open.

## Batch R56: REC-P0-056 FRC-5 Operations Robustness Delivery Fresh Closure

Status: accepted as deployed FRC-5 engineering evidence only.

Reason:

- R55 refreshed workflow/integration/AI depth. The next product-critical gap is proving operations, logs, robustness, delivery, launch rules, and appendix clarification coverage as usable deployed product capabilities.
- These rows decide whether the system can be operated, audited, delivered, and trusted rather than only used in happy-path business flows.

Tasks:

- Keep the REC-P0-056 requirement confirmation contract complete before coding; screenshots are visual evidence only.
- Add `scripts/recovery-r56-frc5-operations-robustness-delivery.ps1`.
- Start from deployed operations/log/release surfaces and packaged release checks.
- Prove health/release, persisted audit search/detail, operation setting/action readback, forbidden-operation failure logs, permission positives/negatives, and browser containment.
- Update requirement coverage ledger, gap report, next ledger, and session state after evidence.

Evidence:

- Script: `scripts/recovery-r56-frc5-operations-robustness-delivery.ps1`
- Result: `docs/evidence/recovery/r56-frc5-operations-robustness-delivery-result.json`
- Summary: `docs/evidence/recovery/r56-frc5-operations-robustness-delivery-2026-07-01.md`
- Browser audit: `docs/evidence/recovery/screenshots/r56-frc5-operations-robustness-delivery/operations-robustness-delivery-browser-audit.json`

Result:

- R56 PASS on deployed release `http://127.0.0.1:18131`: health `database/schema/redis UP`, static usability blockers `0` warnings `0`, fresh R50 release verification `PASS`, server commands `start/stop/restart/status/health`, operations task/readback for backup/restore/archive/rollback, deployment/cache readback, persisted platform/system audit search/detail, normal-member operation denials `403/403` with `FAILURE` audit rows, browser results `6`, overflow `0`, blockers `0`, cleanup `875/876`.

Still partial:

- R56 is operations/robustness/delivery evidence only. Human information architecture, broad role-journey coherence, remaining requirement rows, user acceptance, and `gates.user_script_passed=false` remain open.

## Batch R57: REC-P0-057 FRC-6 Human Acceptance Pass Fresh Closure

Status: accepted as deployed FRC-6 engineering evidence only.

Reason:

- R56 refreshed operations, logs, robustness, and delivery evidence. The next product-critical gap is the one the user is still reporting: whether a real person can use the deployed product as one coherent system rather than a stack of implemented slices.
- These rows decide whether the platform/system shells, role-specific entry points, page density, and primary journeys are understandable enough for human acceptance.

Tasks:

- Keep the REC-P0-057 requirement confirmation contract complete before coding; screenshots are visual evidence only.
- Add `scripts/recovery-r57-frc6-human-acceptance-pass.ps1`.
- Start from deployed login and primary platform/system/work/runtime/admin journeys.
- Prove role shell separation, navigation coherence, page containment, data/readback, permission positives/negatives, and post-action states.
- Keep `gates.user_script_passed=false`; the script can support user acceptance but cannot replace it.

Planned evidence:

- Script: `scripts/recovery-r57-frc6-human-acceptance-pass.ps1`
- Result: `docs/evidence/recovery/r57-frc6-human-acceptance-pass-result.json`
- Summary: `docs/evidence/recovery/r57-frc6-human-acceptance-pass-2026-07-01.md`
- Browser audit: `docs/evidence/recovery/screenshots/r57-frc6-human-acceptance-pass/human-acceptance-browser-audit.json`

Accepted evidence:

- R57 PASS on deployed release `http://127.0.0.1:18131`.
- Release health `database/schema/redis UP`.
- Static usability audit blockers `0`, warnings `0`.
- Fresh role journey routes `16`, viewports `2`, browser results `32`, failures `0`, warnings `0`.
- Forbidden create/admin HTTP `403/403`, journey-created system `883`, module `338`, and deployed asset `/assets/index-BnZhqLS7.js`.

Still partial:

- R57 is human-usable journey engineering evidence only. It does not close all density issues, all requirement rows, or user signoff.

## Batch R58: REC-P0-058 High-Density Surface Convergence

Status: accepted as deployed density/usability engineering evidence only.

Reason:

- R57 proved role journeys and shell separation, but the deployed product still had high-density runtime/work surfaces that could feel like page stacking.
- The next correction had to reduce visible action/panel density without losing role/data/permission evidence.

Tasks:

- Keep the REC-P0-058 requirement confirmation contract complete before coding; screenshots are visual evidence only.
- Consolidate runtime header actions and row actions into task-oriented controls.
- Consolidate work dashboard/task view controls so dashboard, warnings, calendar, and task mode controls no longer look like competing primary panels.
- Rebuild/repackage/restart the release and verify deployed frontend assets.
- Rerun R57 after convergence and prove role safety remains intact.
- Keep `gates.user_script_passed=false`; density evidence is not user signoff.

Evidence:

- Script: `scripts/recovery-r58-high-density-surface-convergence.ps1`
- Result: `docs/evidence/recovery/r58-high-density-surface-convergence-result.json`
- Summary: `docs/evidence/recovery/r58-high-density-surface-convergence-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r58-high-density-surface-convergence/high-density-surface-convergence-browser-audit.json`

Result:

- R58 PASS on deployed release `http://127.0.0.1:18131`.
- Deployed asset `/assets/index-BnZhqLS7.js`.
- Static usability audit blockers `0`, warnings `0`.
- Runtime module max visible button count `27`; work max panel count `7`; work max button count `14`.
- Fresh R57 role journey still passed after convergence; forbidden create/admin HTTP `403/403`.

Still partial:

- R58 reduces runtime/work density but does not close admin configuration first-use depth, all list/runtime variants, all requirement rows, or user signoff.

## Batch R59: REC-P0-059 FRC-2/FRC-6 Admin Configuration Human First-Use Closure

Status: accepted as deployed engineering evidence only; final usable-system acceptance and user signoff remain open.

Reason:

- R58 lowered the runtime/work density, but the remaining highest-value gap is the admin no-code configuration chain: module, field, dictionary, menu, role permission, permission preview, publish/readback, and normal runtime reflection.
- This is the area most likely to regress into broad stacked admin pages while still passing API/readback slices.

Tasks:

- Keep the REC-P0-059 requirement confirmation contract complete before coding; screenshots are visual evidence only.
- Start from deployed login and system switch, then perform a system-admin first-use configuration path.
- Prove module/menu/field/dictionary/permission save-readback-publish-preview-runtime-reflection as one user job.
- Reduce any broad mixed admin configuration panels found in that path.
- Prove normal-member runtime schema/navigation reflects the configured permissions and hidden/readonly states.
- Verify permission positives/negatives, density/overflow/copy checks, cleanup, and `gates.user_script_passed=false`.

Evidence:

- Script: `scripts/recovery-r59-frc2-frc6-admin-configuration-human-first-use.ps1`
- Result: `docs/evidence/recovery/r59-frc2-frc6-admin-configuration-human-first-use-result.json`
- Summary: `docs/evidence/recovery/r59-frc2-frc6-admin-configuration-human-first-use-2026-07-02.md`
- Browser first-use result: `docs/evidence/recovery/r59-admin-first-use-browser-result.json`
- Browser audit: `docs/evidence/recovery/screenshots/r59-admin-first-use/admin-first-use-browser-audit.json`
- Deep no-code chain: `docs/evidence/recovery/r53-frc2-no-code-configuration-coherence-result.json`

Accepted evidence:

- R59 PASS on deployed `http://127.0.0.1:18131` with frontend asset `/assets/index-Dc298sMo.js`.
- Module configuration no longer renders lifecycle, fields, scenes/import-export, page designer, and print designer as one broad stack.
- Browser first-use evidence covers lifecycle, fields, scene, page, and print task tabs on desktop/mobile with `browserResultCount=10`, `browserOverflowCount=0`, and `browserBlockerCount=0`.
- Fresh R53 no-code chain still passes after the task-tab change: R38, R44, and R45 all pass with API/readback, permission positives/negatives, runtime reflection, and browser containment.
- Static usability audit remains blockers `0`, warnings `0`; release verification reports database/schema/Redis `UP`.

Still partial:

- R59 is engineering evidence only. It does not set `gates.user_script_passed=true`.
- The project must continue from `docs/framework/final-system-flow-blueprint.md` and the next unfinished requirement/journey rows rather than from isolated page patches.

## Batch R63: REC-P0-063 B1/B2 Configured Runtime First-Use And Daily Business Closure

Status: accepted as deployed engineering evidence only; final usable-system acceptance and user signoff remain open.

Reason:

- R59 proves the system administrator can configure the no-code structure without stacked admin task panels.
- The next flow-order gap is B1/B2: the published configuration must become a normal-member daily business page, not just an admin configuration success.
- This prevents the repeated failure mode where backend/API/admin evidence passes but the standalone product still does not feel like a usable system.

Tasks:

- Create or configure one system/module/group/fields/dictionary/role/member in one deterministic flow.
- Publish the module and record `systemId/moduleId/publishedVersion/roleId/memberId` as the handoff contract.
- Login as the normal member in the deployed browser and prove the system dashboard/runtime shell shows the authorized module.
- Create, list, open detail, edit, and read back one runtime record from the deployed frontend and API.
- Assert hidden/readonly fields do not leak and direct forbidden admin/config/runtime writes return `403`.
- Verify desktop/mobile containment and visible copy/state quality; screenshots are visual evidence only.
- Cleanup created systems/accounts/data and keep `gates.user_script_passed=false`.

Planned evidence:

- Script: `scripts/recovery-r63-configured-runtime-first-use.ps1`
- Result: `docs/evidence/recovery/r63-configured-runtime-first-use-result.json`
- Summary: `docs/evidence/recovery/r63-configured-runtime-first-use-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r63-configured-runtime-first-use/configured-runtime-browser-audit.json`

Accepted evidence:

- R63 PASS on deployed `http://127.0.0.1:18131` with frontend asset `/assets/index-DXYcfcQU.js`.
- Handoff recorded `systemId=929`, `moduleId=373`, `publishedVersion=MODULE_v1782963082835`, `normalRoleId=1353`, `normalMemberId=1249`, and `accountMemberBindingId=1249`.
- Deployed browser normal-member flow created, read, edited, and read back record `469` across desktop and mobile.
- Hidden field leakage was false in list and detail; readonly create and normal-member admin access returned `403`.
- Browser audit result count `7`, overflow `0`, blockers `0`; cleanup deleted systems `929`, `930`, and `931`.
- R63 is engineering evidence only. It does not set `gates.user_script_passed=true`.

## Batch R64: REC-P0-064 C4/B4/B5 Workflow Todo Message First-Use And Closure

Status: accepted as deployed engineering evidence only.

Reason:

- R63 proves the admin-published module can become normal-member runtime business work.
- The next flow-order gap is C4/B4/B5: workflow configuration must produce real requester/approver todo and message work, then close back into terminal runtime record state.
- This prevents the repeated failure mode where workflow/todo/message APIs pass separately but the human role journey still feels disconnected.

Tasks:

- Configure or create one module and one workflow in the same deterministic flow.
- Publish the workflow and record `systemId/moduleId/flowId/recordId/requesterMemberId/approverMemberId/todoId/messageId` as the handoff contract.
- Login as requester and submit a real runtime record into approval from the deployed browser or runtime route.
- Login as assigned approver and prove pending todo/message delivery in deployed browser.
- Approver handles the todo; requester direct approval is denied; duplicate action remains idempotent.
- Read back terminal record/sidebar, handled todo, read message, audit/trace, desktop/mobile containment, and cleanup.
- Keep `gates.user_script_passed=false`.

Planned evidence:

- Script: `scripts/recovery-r64-workflow-todo-message-first-use.ps1`
- Result: `docs/evidence/recovery/r64-workflow-todo-message-first-use-result.json`
- Summary: `docs/evidence/recovery/r64-workflow-todo-message-first-use-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r64-workflow-todo-message-first-use/workflow-todo-message-browser-audit.json`

Accepted evidence:

- R64 PASS on deployed `http://127.0.0.1:18131` with frontend asset `/assets/index-D3FrPh_v.js`.
- Requester submitted a real runtime record through the deployed browser edit form; approver opened message/todo surfaces and approved through the deployed browser.
- Flow `136`, record `474`, pending task/todo `81`, message `83`; publish-check passed, simulation passed without creating a runtime instance.
- Permission negatives returned requester approve `403` and requester flow/admin read `403`.
- Terminal readback reached detail/sidebar `APPROVED`; pending todos became `0`, handled todos became `1`; duplicate terminal action returned `TASK_STATE_CONFLICT` HTTP `400`.
- Browser audit result count `8`, overflow `0`, blockers `0`; cleanup deleted systems `944`, `945`, and `946`.
- R64 is engineering evidence only. It does not set `gates.user_script_passed=true`.

## Batch R65: REC-P0-065 E1/AI2 OpenAPI Assistant External-Service First-Use Closure

Status: accepted as deployed engineering evidence only.

Reason:

- R64 closes workflow/todo/message as a requester/approver browser/API journey.
- The next final-goal gap is the external-service and AI surface: scoped OpenAPI access, safe secrets, call logs, assistant confirmations, denied unsafe actions, and readable deployed UI state.
- This prevents the repeated failure mode where integration and assistant APIs exist but are not usable or safe as a human-facing platform capability.

Tasks:

- Configure one scoped OpenAPI app, secret reference, and assistant policy in a disposable system.
- Call allowed external APIs and prove search/detail/readback plus success logs.
- Prove wrong-secret and read-only denials, normal-member OpenAPI/admin denials, and platform/scope denials.
- Use the system assistant through preview/confirm/reject/draft confirmation boundaries with audit traces.
- Show deployed browser evidence for admin integration/assistant/log surfaces and normal assistant use on desktop/mobile.
- Read back logs, confirmation records, created data, permission negatives, desktop/mobile containment, and cleanup.
- Keep `gates.user_script_passed=false`.

Planned evidence:

- Script: `scripts/recovery-r65-openapi-assistant-external-service-first-use.ps1`
- Result: `docs/evidence/recovery/r65-openapi-assistant-external-service-first-use-result.json`
- Summary: `docs/evidence/recovery/r65-openapi-assistant-external-service-first-use-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r65-openapi-assistant-external-service-first-use/openapi-assistant-browser-audit.json`

Accepted evidence:

- R65 PASS on deployed `http://127.0.0.1:18131`.
- Fresh scoped OpenAPI app `56` created record `475` in system `947` and module `379`.
- SecretRef/rotation/log boundary passed: secret ref `sec_openapi_r49_app_0702120745045_74d61e_d3d943ae`, rotate job `srj_openapi_56_3332295e`, success logs `5`, failed logs `1`.
- Denials passed: read-only `403`, wrong secret `401`, normal OpenAPI create `403`, normal policy create `403`, platform-scope denied `REJECTED_BY_SCOPE`.
- Assistant boundaries passed: write preview waited, write confirmed, write rejected, work draft waited and confirmed.
- Browser audit result count `6`, overflow `0`, blockers `0`; cleanup deleted systems `947` and `948`.
- R65 is engineering evidence only. It does not set `gates.user_script_passed=true`.

## Batch R66: REC-P0-066 O1/O2/O3 Operations Logs Release Maintenance First-Use Closure

Status: accepted as deployed engineering evidence only.

Reason:

- R65 closes OpenAPI external service and assistant confirmation boundaries as deployed engineering evidence.
- The next final-goal gap is operations: release health, logs, maintenance controls, backup/rollback/cache/rate-limit style governance, and operator-readable evidence.
- This prevents the repeated failure mode where logs and ops exist as isolated APIs or static rows but cannot be trusted by a human operator.

Tasks:

- Run deployed release verification and preserve asset/backend/Redis/DB/schema evidence.
- Create real success/failure/audit events and read them through platform/system log surfaces.
- Exercise or dry-run maintenance controls with task/readback evidence and risk-boundary copy.
- Verify release scripts/assets/pids and same-origin proxy behavior.
- Prove normal-member denials for operations/log surfaces and direct forbidden APIs.
- Browser-check operator pages on desktop/mobile for overflow, blockers, stale/static rows, and ambiguous tips.
- Keep `gates.user_script_passed=false`.

Planned evidence:

- Script: `scripts/recovery-r66-operations-logs-release-maintenance-first-use.ps1`
- Result: `docs/evidence/recovery/r66-operations-logs-release-maintenance-first-use-result.json`
- Summary: `docs/evidence/recovery/r66-operations-logs-release-maintenance-first-use-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r66-operations-logs-release-maintenance-first-use/operations-logs-release-browser-audit.json`

Accepted evidence:

- R66 PASS on deployed `http://127.0.0.1:18131`.
- Release verify passed and packaged server commands include `start`, `stop`, `restart`, `status`, and `health`.
- Static usability audit passed with blockers `0` and warnings `0`.
- Operations tasks/readback passed: backup/restore/archive/rollback task `TASK-20260702121503`, deployment count `1`, cache policies `4`, cache updated `1`.
- Logs/readback passed: platform audit `9`, system audit `1`, trace ids present.
- Permission negatives passed: platform denied `403/FAILURE`, system denied `403/FAILURE`.
- Browser audit result count `6`, overflow `0`, blockers `0`; cleanup deleted systems `949` and `950`.
- R66 is engineering evidence only. It does not set `gates.user_script_passed=true`.

## Batch R67: REC-P0-067 Final Role Journey And Requirement Acceptance Candidate Refresh

Status: accepted as final-candidate engineering evidence only.

Reason:

- R63-R66 close the major flow-derived slices as deployed engineering evidence: configured runtime, workflow/todo/message, OpenAPI/AI, and operations/logs/release.
- The next work must stop slicing by feature and run a whole-system candidate refresh across all roles and all 45 requirement rows.
- This prevents the repeated failure mode where many local passes exist but the user still receives a confusing or incomplete product.

Tasks:

- Run release, static usability, framework, and requirement coverage audits.
- Aggregate fresh R63-R66 evidence and verify their browser/API/readback files exist.
- Browser-check primary role shells on desktop/mobile for mixed shells, page stacking, stale demo text, ambiguous tips, and overflow.
- Produce a requirement-row candidate matrix: proven by fresh evidence, still partial, or user-signoff required.
- Keep `gates.user_script_passed=false`; do not claim final completion without user verification.

Planned evidence:

- Script: `scripts/recovery-r67-final-role-journey-requirement-acceptance-candidate.ps1`
- Result: `docs/evidence/recovery/r67-final-role-journey-requirement-acceptance-candidate-result.json`
- Summary: `docs/evidence/recovery/r67-final-role-journey-requirement-acceptance-candidate-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r67-final-role-journey-requirement-acceptance-candidate/final-role-journey-browser-audit.json`

Accepted evidence:

- R67 PASS on `http://127.0.0.1:18131`.
- Release/static/framework audits passed; requirement coverage remained honestly open with `missing=0`, `notClosed=45`.
- Fresh R57 role-shell browser refresh passed with `32` results and `0` failures/warnings.
- R59/R63/R64/R65/R66 browser evidence all had overflow `0` and blockers `0`.
- Candidate matrix includes all `45` requirement rows, `42` rows have fresh evidence, and `0` rows were promoted to `PROVEN`.
- The next concrete gap is `REQ-5.8`, `REQ-6.1`, and `REQ-6.8`.

Still partial:

- R67 is engineering evidence only. Final completion and user signoff remain open.

## Batch R68: REC-P0-068 Page Visual Designer Fresh Evidence Closure

Status: accepted as deployed engineering evidence only; final usable-system acceptance and user signoff remain open.

Reason:

- R67 found exactly three rows without fresh evidence: pages, overall visual style, and page designer.
- These rows map to the same product loop: system admin configures a page, publishes it, and normal members use the resulting runtime surface.
- The next task must close that loop with backend readback, permission boundaries, desktop/mobile browser evidence, and visual/copy checks.

Tasks:

- Keep the REC-P0-068 task card complete before coding.
- Create or reuse a fresh system/module/page definition that exercises page designer components, order, properties, and hidden component behavior.
- Save and publish the page definition, then read it back from APIs.
- Render the published runtime page for admin and normal member on desktop/mobile.
- Assert normal-member admin write denial and hidden/admin-only component pruning.
- Audit visual hierarchy, overflow, clipping, control text overflow, generic/demo copy, and stacked competing views.
- Keep `gates.user_script_passed=false`.

Planned evidence:

- Script: `scripts/recovery-r68-page-visual-designer-fresh-evidence.ps1`
- Result: `docs/evidence/recovery/r68-page-visual-designer-fresh-evidence-result.json`
- Summary: `docs/evidence/recovery/r68-page-visual-designer-fresh-evidence-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r68-page-visual-designer-fresh-evidence/page-visual-designer-browser-audit.json`

Accepted evidence:

- R68 PASS on `http://127.0.0.1:18131`.
- Release verification, static usability audit, and final-goal framework audit passed.
- Fresh R42 page-designer evidence passed with `componentReadbackCount=5`, browser results `8`, overflow `0`, hidden field/component leakage `false`, and forbidden create `403`.
- Fresh R46 page publish/runtime evidence passed with page version `PAGE_v1782970607916`, admin schema components `5`, browser results `10`, overflow/blockers `0`, hidden field/component leakage `false`, and forbidden page/home writes `403`.
- R68 is engineering evidence only. It does not close final user signoff.

## Batch R69: REC-P0-069 Requirement Evidence Promotion And Residual Gap Decision

Status: planned; active next executable task after R68.

Reason:

- R67 produced the first full 45-row candidate matrix but found only 42 rows with fresh evidence.
- R68 closed the remaining fresh-evidence gap for pages, overall visual style, and page designer.
- The next step must promote or keep partial requirement rows by evidence, then name the next residual coding batch without asking the user to audit manually.

Tasks:

- Keep the REC-P0-069 task card complete before running the audit.
- Rerun release, static usability, framework, and coverage audits.
- Read R59/R63/R64/R65/R66/R67/R68 evidence files and browser aggregates.
- Produce a 45-row promotion matrix with decision, evidence references, permission coverage, browser/API/readback coverage, and residual gap.
- Refuse screenshot-only, generated-only, or route-only promotion.
- Keep `gates.user_script_passed=false`.
- If any row remains partial, write the next residual implementation batch and active next task.

Planned evidence:

- Script: `scripts/recovery-r69-requirement-evidence-promotion-and-gap-decision.ps1`
- Result: `docs/evidence/recovery/r69-requirement-evidence-promotion-and-gap-decision-result.json`
- Summary: `docs/evidence/recovery/r69-requirement-evidence-promotion-and-gap-decision-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r69-requirement-evidence-promotion-and-gap-decision/requirement-evidence-browser-audit.json`

Accepted evidence:

- R69 PASS on `http://127.0.0.1:18131`.
- Release/static/framework audits passed; coverage audit remained honestly open with `missing=0`, `notClosed=45`.
- R67/R68 evidence was readable.
- Promotion matrix rows `45`; fresh evidence rows after R68 `45`; no fresh evidence rows `0`; promoted rows `0`; still partial rows `45`.
- Next residual batch emitted: `REC-P0-070 No-Code Configuration Residual Depth Closure` with `11` rows.

## Batch R70: REC-P0-070 No-Code Configuration Residual Depth Closure

Status: accepted as deployed engineering evidence only.

Reason:

- R69 selected FRC-2/no-code configuration depth as the largest coherent residual block.
- These rows share the same admin-to-runtime loop: configure app/module/menu/field/dictionary/member/permission, publish/read back, and verify normal-member runtime reflection.
- Closing this block reduces the main reason the project still feels like isolated prototype surfaces instead of a coherent no-code system.

Tasks:

- Keep the REC-P0-070 task card complete before coding.
- Configure app/module lifecycle with publish/readback/rollback where supported.
- Configure nested and disabled/unpublished module groups or menus and prove runtime visibility rules.
- Configure field/dictionary variants and prove disabled dictionary items and hidden fields do not leak.
- Bind org/member/role context and prove permission preview agrees with runtime behavior.
- Render admin and normal-member routes on desktop/mobile with no overflow, stacked competing panels, or vague tips.
- Assert forbidden API/browser negatives and cleanup.
- Keep `gates.user_script_passed=false`.

Planned evidence:

- Script: `scripts/recovery-r70-no-code-configuration-residual-depth.ps1`
- Result: `docs/evidence/recovery/r70-no-code-configuration-residual-depth-result.json`
- Summary: `docs/evidence/recovery/r70-no-code-configuration-residual-depth-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r70-no-code-configuration-residual-depth/no-code-configuration-browser-audit.json`

Accepted evidence:

- R70 PASS on deployed `http://127.0.0.1:18131` after rebuilding/restarting release.
- Backend runtime navigation now exposes real group role ids and runtime visibility from module/group publish and module status.
- Normal-member runtime direct access returned `403` for hidden-role, draft, disabled, and draft-group modules while visible published schema readback succeeded.
- Fresh R44 permission preview/runtime agreement and fresh R45 field/dictionary/menu evidence passed.
- Browser aggregate result count `10`, overflow `0`, blockers `0`; cleanup deleted systems `1001` and `1002`.
- Nested module groups remain a recorded model gap because current `ModuleGroup` has no persisted `parentId`.

## Batch R71: REC-P0-071 No-Code Frontend Binding And Hierarchy Residual Closure

Status: accepted as deployed engineering evidence only.

Reason:

- R70 closed the most dangerous backend leakage: runtime APIs now reject hidden/draft/disabled/draft-group modules.
- The remaining no-code configuration gap is browser-verifiable usability: a human and script must see object states and member-role binding without reading API internals.
- Nested module group support cannot be faked because the current backend model has no `parentId`; R71 must either implement persisted hierarchy deliberately or mark the unsupported boundary clearly in product copy/evidence.

Tasks:

- Add stable browser selectors and visible state markers for module group rows, module rows, field cards, dictionary rows, runtime module buttons, and runtime group navigation.
- Add a visible member-role binding workbench or equivalent browser-verifiable binding state for member/role manager flows.
- Prove normal-member runtime DOM does not contain hidden module/field identifiers.
- Prove direct API denials still hold after frontend changes.
- Record nested module group boundary honestly: no frontend-only fake hierarchy without backend schema.
- Keep `gates.user_script_passed=false`.

Planned evidence:

- Script: `scripts/recovery-r71-no-code-frontend-binding-and-hierarchy-residual.ps1`
- Result: `docs/evidence/recovery/r71-no-code-frontend-binding-and-hierarchy-residual-result.json`
- Summary: `docs/evidence/recovery/r71-no-code-frontend-binding-and-hierarchy-residual-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r71-no-code-frontend-binding-and-hierarchy-residual/no-code-frontend-binding-browser-audit.json`

Accepted evidence:

- R71 PASS on deployed `http://127.0.0.1:18131` after typecheck/build/package/restart/verify-release.
- Frontend-visible selectors now expose group/module/field/member/role ids, publish/status/runtimeVisible state, visible role ids, active runtime context, and `hierarchySupported=false`.
- Browser evidence proved admin module config markers, member-role binding marker, role permission workbench options, normal runtime visible module marker, hidden/disabled module absence, hidden field absence, system header group marker, desktop/mobile overflow `0`, and blockers `0`.
- API/readback negatives still held: hidden direct `403`, disabled direct `403`, forbidden create `403`; cleanup deleted systems `1007` and `1008`.
- Nested module groups remain a recorded model gap because current `ModuleGroup` has no persisted `parentId`.
- User signoff remains false.

## Batch R72: REC-P0-072 Runtime Daily-Use File Import Export Error-State Residual Closure

Status: accepted as deployed engineering evidence only.

Reason:

- R71 closed no-code frontend binding evidence, so the next largest user-facing gap is runtime daily use depth.
- Prior R47/R54/R63 proved happy-path runtime records, files, import/export, and configured-runtime handoff, but final acceptance still needs broader failure states, rollback/error-file cases, file permission/version states, approval detail context, batch actions, and mobile clarity.
- These rows map directly to the user's target: a normal person can actually use the system after an admin configures it.

Tasks:

- Prove runtime record list/create/edit/detail/history from the deployed browser and API readback.
- Prove attachment preview/download success, permission denial, and missing/deleted/version failure states.
- Prove import precheck/confirm success plus rollback/error-file failure cases.
- Prove export task success plus result-file and failure states.
- Prove approval detail/sidebar state tied to the runtime record/todo/message context.
- Prove batch action permission and partial result behavior where supported.
- Prove readonly/forbidden form permutations and hidden-field/file DOM non-leakage for normal members.
- Render desktop/mobile runtime surfaces with no overflow, stacked competing panels, or vague tips.
- Keep `gates.user_script_passed=false`.

Planned evidence:

- Script: `scripts/recovery-r72-runtime-file-import-export-error-state-residual.ps1`
- Result: `docs/evidence/recovery/r72-runtime-file-import-export-error-state-residual-result.json`
- Summary: `docs/evidence/recovery/r72-runtime-file-import-export-error-state-residual-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r72-runtime-file-import-export-error-state-residual/runtime-file-import-export-error-browser-audit.json`

Accepted evidence:

- R72 PASS on deployed `http://127.0.0.1:18131` after typecheck/build/package/restart/verify-release.
- Backend import confirmation now rejects failed precheck results; failed confirm returned HTTP `400` with error-file evidence.
- File evidence proved attachment readback `READY`, preview/download HTTP `200`, missing file HTTP `400`, denied file access `allowed=false`, detail attachments `4`, and history rows `2`.
- Import/export evidence proved successful precheck/confirm with `2` inserted rows and rollback support, failed precheck `FAILED` with error file, export-all and selected-export result files, and batch archive async status `QUEUED`.
- Permission and UI evidence proved hidden field leakage `false`, readonly create `403`, normal-member admin access `403`, browser results `10`, overflow `0`, blockers `0`, and cleanup `1015/1016/1017`.
- User signoff remains false.

## Batch R73: REC-P0-073 Workflow Todo Message Integration Error-State Residual Closure

Status: accepted as deployed engineering evidence only.

Reason:

- R72 closed runtime daily-use file/import-export residual evidence, so the next largest user-facing gap is the daily workflow loop: submitter, assigned approver, todo, message, terminal state, duplicate prevention, and readable error states.
- Prior R48/R55/R64 proved first workflow/todo/message paths, but final acceptance still needs deeper error states, read/archive/filter/pagination behavior, reassignment/rejection/terminal conflict where supported, and stricter role/DOM evidence.
- These rows map directly to the user's target: normal users can process work without guessing which page, card, button, or message is authoritative.

Tasks:

- Prove workflow-backed runtime submit from deployed browser and API readback.
- Prove assigned approver receives and handles the todo; requester self-approval and unassigned approval are denied.
- Prove todo pending/handled/empty states and duplicate/terminal conflict disabled or denied states.
- Prove message creation, unread/read/archive, filter, pagination, and target jump/readback where supported.
- Prove rejection, transfer/reassignment, or explicitly unsupported boundaries where the current product model cannot support them.
- Prove record detail, approval sidebar, todo, and message all agree on terminal workflow state.
- Prove forbidden actions do not leak as successful DOM/actions for unauthorized roles.
- Render desktop/mobile workflow, todo, and message surfaces with no overflow, stacked competing panels, or vague tips.
- Keep `gates.user_script_passed=false`.

Planned evidence:

- Script: `scripts/recovery-r73-workflow-todo-message-error-state-residual.ps1`
- Result: `docs/evidence/recovery/r73-workflow-todo-message-error-state-residual-result.json`
- Summary: `docs/evidence/recovery/r73-workflow-todo-message-error-state-residual-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r73-workflow-todo-message-error-state-residual/workflow-todo-message-error-browser-audit.json`

Accepted evidence:

- R73 PASS on deployed `http://127.0.0.1:18131`.
- Flow/runtime handoff: `systemId=1024`, `moduleId=422`, `recordId=592`, `flowId=139`, `pendingTaskId=83`, `pendingTodoId=83`, `pendingMessageId=85`.
- Message read/archive: unread before `1`, mark-all-read affected `1`, unread after `0`, read after `1`, archive affected `1`, active after archive `0`, archived after archive `1`.
- Permission/terminal states: requester approve `403`, requester flow-admin `403`, duplicate todo `TASK_STATE_CONFLICT`, terminal reject `400`, terminal transfer `400`.
- Reject path proved terminal detail/sidebar `REJECTED`, pending after reject `0`, handled after reject `1`.
- Browser results `8`, overflow `0`, blockers `0`; cleanup `1024/1025/1026/1027/1028/1029`.
- User signoff remains false.

## Batch R74: REC-P0-074 OpenAPI AI External-Service Error-State Residual Closure

Status: accepted as deployed engineering evidence only.

Reason:

- R73 closed workflow/todo/message residual error-state evidence; the next largest platform-level gap is the external-service and AI capability that lets this no-code system interoperate outside the platform.
- Prior R49/R55/R65 proved first OpenAPI/assistant paths, but final acceptance still needs deeper credential rotation, old-secret invalidation, wrong-scope/read-only failures, assistant terminal confirmation behavior, traceable logs, and stricter frontend state evidence.
- These rows map directly to the user's target: platform-created systems can expose safe APIs, use AI with human confirmation, and keep operations/logs understandable.

Tasks:

- Prove scoped OpenAPI app/secret creation and readback through deployed browser/API evidence.
- Prove secret rotation makes the old credential fail and the new credential succeed without leaking raw secrets.
- Prove allowed external create/search/detail and denied read-only, wrong-secret, wrong-scope, and forbidden normal-member states.
- Prove assistant preview, confirm, reject, and duplicate/terminal confirmation behavior with audit/log readback.
- Prove platform/system AI authority boundaries and normal-member policy mutation denial.
- Prove success/failure call logs and assistant/audit logs can be traced by ids generated in the same run.
- Render desktop/mobile OpenAPI, assistant, and log surfaces with no overflow, stacked competing panels, leaked secrets, or vague tips.
- Keep `gates.user_script_passed=false`.

Planned evidence:

- Script: `scripts/recovery-r74-openapi-ai-external-service-error-state-residual.ps1`
- Result: `docs/evidence/recovery/r74-openapi-ai-external-service-error-state-residual-result.json`
- Summary: `docs/evidence/recovery/r74-openapi-ai-external-service-error-state-residual-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r74-openapi-ai-external-service-error-state-residual/openapi-ai-external-service-error-browser-audit.json`

Accepted evidence:

- R74 PASS on deployed `http://127.0.0.1:18131`.
- Secret rotation switched from old SecretRef to active SecretRef and old-secret calls returned `401`.
- External-call states proved success logs `5`, failed logs `2`, read-only denied `403`, wrong-secret denied `401`.
- AI states proved platform scope denial `REJECTED_BY_SCOPE`, write preview/confirm/reject, duplicate confirm `400`, draft preview/confirm, normal-member OpenAPI/policy denials `403/403`.
- Browser results `6`, overflow `0`, blockers `0`; cleanup `1032/1033`.
- User signoff remains false.

## Batch R76: REC-P0-076 Auth Shell Entry Role Flow Residual

Status: accepted as engineering evidence only.

Reason:

- User feedback showed the current deployed result still did not behave like a coherent human-usable system even after previous shell and entry fixes.
- Multi-agent review found the concrete failure class: platform-role login could be pulled into a system context too early, `/platform/dashboard` still leaked as a stale landing, platform/system admin could be wrapped inside broader workbench shells, and entry pages still carried unclear or corrupted copy in places.
- This batch corrects the flow contract before continuing R75, so later operations/logs work is not built on a broken entry and role-routing foundation.

Tasks:

- Keep unauthenticated non-auth routes on the auth entry surface.
- Route platform-role users to `/platform` first instead of auto-switching into the first system.
- Normalize stale `/platform/dashboard` frontend/backend landing to `/platform`.
- Keep registration landing on `/systems/{systemId}/admin`.
- Keep password recovery able to return to login after completion.
- Render platform admin and system admin as standalone admin shells rather than inside the daily workbench shell.
- Remove mojibake from entry, route, and platform shell source touched by this batch.
- Preserve `gates.user_script_passed=false`.

Planned evidence:

- Script: `scripts/recovery-r76-auth-shell-entry-role-flow-residual.ps1`
- Result: `docs/evidence/recovery/r76-auth-shell-entry-role-flow-residual-result.json`
- Summary: `docs/evidence/recovery/r76-auth-shell-entry-role-flow-residual-2026-07-02.md`

Accepted evidence:

- R76 PASS as source/build engineering evidence.
- `initializeShellState()` no longer auto-switches into the first switchable system during login/bootstrap.
- Frontend and backend landing contracts now use `/platform` instead of stale `/platform/dashboard`.
- Platform-role authenticated users land on the platform workspace unless an explicit system context is already selected.
- Platform admin exposes `data-platform-admin-standalone=true`; system admin exposes `data-system-admin-standalone=true`.
- Registration still enters `/systems/{systemId}/admin`; password recovery exposes a return-to-login action.
- Entry and platform shell source checks found no mojibake in the touched surfaces.
- `npm typecheck` and `npm build` passed; build asset `/assets/index-QDWgcnIm.js`.
- Release package, local release start, and `scripts/verify-release.ps1 -BaseUrl http://127.0.0.1:18131 -CheckDeployedFrontend` passed after the R76 backend/frontend changes; deployed assets match `/assets/index-DzYIVez0.css` and `/assets/index-QDWgcnIm.js`, health reports database/schema/Redis `UP`, and admin login succeeds.
- User signoff remains false; R75 remains the next residual batch.

## Batch R75: REC-P0-075 Operations Logs Release Maintenance Error-State Residual Closure

Status: accepted as deployed engineering evidence only.

Reason:

- R74 closed OpenAPI/AI external-service residual evidence; the next platform-level gap is operator confidence: release health, logs, maintenance tasks, denial rows, and rollback/cache/backup boundaries.
- Prior R50/R56/R66 proved first operations paths, but final acceptance still needs deeper error states, task/detail readback, denied-operation audit traceability, deployed asset matching, and stricter frontend state evidence.
- These rows map directly to the user's target: a platform-level no-code system must be operable and diagnosable, not only usable on happy-path business screens.

Tasks:

- Prove release health and deployed asset matching from the running release.
- Prove platform/system log success and failure rows can be traced by traceId/auditLogId.
- Prove operations task/dry-run creation and readback for maintenance controls.
- Prove normal-member/wrong-role operation and log denials are persisted as failure evidence where applicable.
- Prove release scripts/assets and backend/frontend process state remain understandable to deployers.
- Render desktop/mobile operations and log surfaces with no overflow, stale demo data, stacked competing panels, or vague tips.
- Keep `gates.user_script_passed=false`.

Planned evidence:

- Script: `scripts/recovery-r75-operations-logs-release-error-state-residual.ps1`
- Result: `docs/evidence/recovery/r75-operations-logs-release-error-state-residual-result.json`
- Summary: `docs/evidence/recovery/r75-operations-logs-release-error-state-residual-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r75-operations-logs-release-error-state-residual/operations-logs-release-error-browser-audit.json`

Accepted evidence:

- R75 PASS on deployed `http://127.0.0.1:18131`.
- Release verification passed and deployed frontend assets matched the release package.
- Packaged server commands remained present: `start`, `stop`, `restart`, `status`, and `health`.
- Operations task ids were traceable: backup, restore, archive, and rollback all returned task ids.
- Platform/system audit log readback stayed tied to trace ids: platform audit rows `9`, system audit rows `1`.
- Forbidden normal-member platform/system operations returned `403` and persisted failure audit rows.
- Frontend residual markers now expose operation action/result/task/dry-run/rollback/deployment/cache/log state through stable DOM selectors.
- Deployed browser clicked operations controls and proved taskId, traceId, dry-run, rollbackSupported, deployment/cache state, platform logs, and system logs on desktop/mobile.
- Browser result count `6`, overflow `0`, blockers `0`.
- User signoff remains false.

## Batch R77: REC-P0-077 Final Requirement Candidate Refresh After Residual Closure

Status: accepted as diagnostic engineering evidence only.

Reason:

- R70-R75 closed the named residual batches for no-code configuration, frontend binding, runtime daily use, workflow/todo/message, OpenAPI/AI, operations/logs/release, plus R76 entry/shell foundation drift.
- The next step must not claim final completion. It must refresh the final requirement candidate matrix and role-journey status from the latest deployed evidence, keeping all rows partial unless the evidence standard and user signoff allow promotion.

Tasks:

- Re-run final framework, static usability, requirement coverage, and requirement-gap audits.
- Ingest fresh R70-R76 evidence and current release verification.
- Produce an updated candidate matrix across all requirement rows and journey rows.
- Decide the next concrete executable gap without asking the user unless a real product decision is missing.
- Keep `gates.user_script_passed=false`.

Planned evidence:

- Script: `scripts/recovery-r77-final-requirement-candidate-refresh-after-residual.ps1`
- Result: `docs/evidence/recovery/r77-final-requirement-candidate-refresh-after-residual-result.json`
- Summary: `docs/evidence/recovery/r77-final-requirement-candidate-refresh-after-residual-2026-07-02.md`

Accepted evidence:

- R77 PASS on deployed `http://127.0.0.1:18131`.
- Framework audit PASS; static usability audit PASS with blockers `0`, warnings `0`.
- Requirement coverage remained honest: ledger rows `45`, missing `0`, notClosed `45`, promotedToProven `0`.
- R70-R76 residual evidence files were present, PASS, and did not claim user signoff.
- Next concrete gap is the first group from the refreshed gap report: FRC-1 product surfaces/human acceptance rows `REQ-5.8`, `REQ-6.1`, `REQ-6.3`, `REQ-6.8`, and `REQ-9`.
- User signoff remains false.

## Batch R78: REC-P0-078 FRC-1 Product Surface Human Acceptance Residual Closure

Status: accepted as product-surface engineering evidence only; user signoff remains open.

Reason:

- R77 refreshed the final requirement matrix after R70-R76 and found all `45` rows still partial with no missing rows and no promotion to final proven status.
- The first recommended gap group is FRC-1: pages, overall visual style, home page, page designer, and advanced/new capabilities.
- This batch must turn the strongest current engineering evidence into a tighter deployed role journey without claiming final user signoff.

Tasks:

- Re-audit product surfaces for page stacking, visual hierarchy, misleading copy, stale demo content, and confusing task mixing.
- Prove page/home/page-designer/runtime surface behavior from deployed browser and API/readback evidence.
- Include normal-member and admin role boundaries.
- Keep screenshots visual-only and keep `gates.user_script_passed=false`.

Planned evidence:

- Script: `scripts/recovery-r78-frc1-product-surface-human-acceptance-residual.ps1`
- Result: `docs/evidence/recovery/r78-frc1-product-surface-human-acceptance-residual-result.json`
- Summary: `docs/evidence/recovery/r78-frc1-product-surface-human-acceptance-residual-2026-07-02.md`

Accepted evidence 2026-07-06:

- Full R78 PASS on deployed `http://127.0.0.1:18131`.
- Release verification passed with database/schema/Redis `UP`; static usability passed with blockers `0` and warnings `0`; framework audit passed.
- Fresh R68 child evidence passed for page designer and page runtime: page designer browser results `8`, runtime browser results `10`, overflow `0`, blockers `0`, hidden field/component leakage `false`, and forbidden page/home writes `403`.
- Fresh R75 child evidence passed for operations/logs/release residual surfaces: browser results `6`, overflow `0`, blockers `0`, platform audit rows `9`, system audit rows `1`, and forbidden operation denials `403/403` with `FAILURE` logs.
- Fresh R77 child evidence kept the final coverage boundary honest: notClosed `45`, promotedToProven `0`, userSignoff `false`.
- Result: `docs/evidence/recovery/r78-frc1-product-surface-human-acceptance-residual-result.json`
- Browser audit: `docs/evidence/recovery/screenshots/r78-frc1-product-surface-human-acceptance-residual/product-surface-human-acceptance-browser-audit.json`
- This is engineering evidence only. `gates.user_script_passed=false` remains unchanged until the user verifies or signs off.

## Batch R79: REC-P0-079 Final User Verification Readiness And Continuation Contract

Status: accepted as final handoff engineering evidence only; user signoff remains open.

Reason:

- R78 closed the latest product-surface engineering evidence, but the final goal still cannot close without user verification.
- The next work must not restart broad coding or repeat R78. It must turn the current project structure, target, developed evidence, verification path, and future change process into a durable handoff contract.
- Future changes must continue through this architecture: role journey, requirement row, task card, fix batch, implementation, deterministic script, and user verification boundary.

Tasks:

- Add `docs/recovery/continuation-implementation-guide.md` to explain current structure, developed state, remaining boundary, and future change rules.
- Make R79 the active next executable task in the next execution ledger and session state.
- Run framework, static usability, and coverage audits from disk.
- Verify R78 remains accepted engineering evidence only.
- Keep coverage partial and `gates.user_script_passed=false` until user verification.

Planned evidence:

- Script: `scripts/recovery-r79-final-user-verification-readiness.ps1`
- Result: `docs/evidence/recovery/r79-final-user-verification-readiness-result.json`
- Summary: `docs/evidence/recovery/r79-final-user-verification-readiness-2026-07-06.md`
- Continuation guide: `docs/recovery/continuation-implementation-guide.md`

Accepted evidence:

- R79 PASS on 2026-07-06.
- Framework, static usability, and coverage audits ran from disk.
- R78 prerequisite evidence remained PASS and accepted as engineering evidence only.
- Static usability remained PASS with blockers `0` and warnings `0`.
- Coverage stayed honest with missing `0`, notClosed `45`, and `gates.user_script_passed=false`.
- Result: `docs/evidence/recovery/r79-final-user-verification-readiness-result.json`
- Summary: `docs/evidence/recovery/r79-final-user-verification-readiness-2026-07-06.md`

## Batch R80: REC-P0-080 Live User Trial Workspace Seed

Status: accepted as live trial engineering evidence only; user signoff remains open.

Reason:

- R79 made the continuation path durable, but a human reviewer still needs a live seeded workspace with accounts, routes, and retained data.
- The next step should reduce friction for real use: open the release, log in, inspect runtime data, and review workflow/todo/message state without rebuilding context from reports.
- The trial workspace must keep the final boundary honest: engineering evidence can prepare the handoff, but only the user can close signoff.

Tasks:

- Keep R80 as the active next executable task in the next execution ledger and session state.
- Verify the release, framework, static usability, and coverage boundary.
- Run configured runtime seeding with cleanup skipped and emit trial credentials.
- Run workflow/todo/message seeding with cleanup skipped and emit requester/approver credentials.
- Write a R80 result and summary containing trial entries, retained ids, and the signoff boundary.

Planned evidence:

- Script: `scripts/recovery-r80-live-user-trial-workspace.ps1`
- Result: `docs/evidence/recovery/r80-live-user-trial-workspace-result.json`
- Summary: `docs/evidence/recovery/r80-live-user-trial-workspace-2026-07-06.md`
- Child configured runtime evidence: `docs/evidence/recovery/r63-configured-runtime-first-use-result.json`
- Child workflow/todo/message evidence: `docs/evidence/recovery/r64-workflow-todo-message-first-use-result.json`

Accepted evidence:

- R80 PASS on 2026-07-06.
- Release verification, framework audit, static usability audit, and coverage audit all ran from the deployed release context.
- R63 child evidence passed with cleanup `SKIPPED` and retained runtime trial data.
- R64 child evidence passed with cleanup `SKIPPED` and retained workflow/todo/message trial data.
- Trial credentials and routes are emitted in `docs/evidence/recovery/r80-live-user-trial-workspace-result.json` and summarized in `docs/evidence/recovery/r80-live-user-trial-workspace-2026-07-06.md`.
- Coverage remains partial with notClosed `45`; `gates.user_script_passed=false`.

## Batch R81: REC-P0-081 Trial Login And Role Use Audit

Status: accepted as deployed login/role engineering evidence only; user signoff remains open.

Reason:

- R80 created retained trial accounts and data, but the next proof must start like a human reviewer starts: from the deployed login page.
- The audit must prove that the trial pack is not just a report artifact. Each role must log in, reach the expected surface, and see role boundaries.
- This batch keeps the final boundary honest: it may prepare a usable reviewer path, but only the user can close signoff.

Tasks:

- Keep R81 as the active next executable task in the next execution ledger and session state.
- Verify the release, framework, static usability, and coverage boundary.
- Read the R80 trial pack and submit the deployed login form for admin, normal, readonly, requester, and approver roles.
- Verify retained runtime record visibility, readonly create denial, workflow terminal state, todo surface, and message surface.
- Write a R81 result and summary containing role entries, browser evidence, permission evidence, and the signoff boundary.

Planned evidence:

- Script: `scripts/recovery-r81-trial-login-role-use-audit.ps1`
- Result: `docs/evidence/recovery/r81-trial-login-role-use-audit-result.json`
- Summary: `docs/evidence/recovery/r81-trial-login-role-use-audit-2026-07-06.md`
- Browser audit: `docs/evidence/recovery/screenshots/r81-trial-login-role-use-audit/trial-login-role-use-browser-audit.json`
- Prerequisite trial pack: `docs/evidence/recovery/r80-live-user-trial-workspace-result.json`

Accepted evidence:

- R81 PASS on 2026-07-06.
- R81 started release inside the script lifecycle when needed and release verification passed.
- Browser role audit submitted the deployed login form for admin, normal, readonly, requester, and approver roles.
- Browser result count `8`, overflow `0`, blockers `0`.
- Runtime normal and readonly users saw retained record `625`; readonly create returned `403`.
- Workflow requester saw terminal record `626` as `APPROVED`; approver todo/message surfaces loaded with handled todo total `1` and message total `1`.
- Framework/static checks passed; coverage remained partial with notClosed `45`; `gates.user_script_passed=false`.


## Batch R85: REC-P0-085 System Dashboard Daily Action Hub Closure

Status: accepted as deployed dashboard daily-action-hub engineering evidence only; user signoff remains open.

Reason:

- The gap report keeps `REQ-6.3`, `REQ-6.2`, `REQ-5.20`, and `REQ-5.16` partial: the first screen must connect home, work, todo, and message into a daily action surface.
- R84 proved the work-management loop, so the next usable-system step is to make those results discoverable from the system dashboard after login.
- R85 keeps the final-goal architecture active by linking the change to flow `S2/B1/B3/B4/B5` and unfinished requirement rows.

Tasks:

- Convert the system dashboard into a daily action hub backed by real work, todo, message, and module-navigation APIs.
- Add stable deployed UI markers for dashboard daily hub, work preview, todo preview, message preview, module preview, and quick actions.
- Keep quick actions as navigation into existing real routes instead of fake local success states.
- Prove source markers, deployed API readback, browser-visible dashboard markers, anonymous denial, framework/static boundaries, and user signoff separation.

Planned evidence:

- Script: `scripts/recovery-r85-system-dashboard-daily-action-hub.ps1`
- Result: `docs/evidence/recovery/r85-system-dashboard-daily-action-hub-result.json`
- Summary: `docs/evidence/recovery/r85-system-dashboard-daily-action-hub-2026-07-07.md`

Still partial:

- R85 can only add engineering evidence for the system dashboard daily action hub. It does not close all requirement rows or set `gates.user_script_passed=true`.

Accepted evidence for Batch R85:

- R85 PASS on deployed `http://127.0.0.1:18131`.
- Typecheck/build/package-release/local-start/verify-release passed with deployed frontend asset `index-C40SlAQy.js`.
- API/readback evidence proved dashboard/home/work/todo/message/module data for system `1118`, quick task `53`, anonymous dashboard/todo/message denial, and deployed frontend marker presence.
- R85 remains engineering evidence only; user signoff is still open.
## Batch R84: REC-P0-084 System Work Management Daily Use Closure

Status: accepted as deployed work-management engineering evidence only; user signoff remains open.

Reason:

- The gap report keeps `REQ-5.20` partial: work management still needs full daily work usability, dashboard/calendar, task board, report draft, and message/todo integration breadth.
- Existing backend and frontend work surfaces already exist; the next change should close the normal-member daily-use loop rather than adding a new shell.
- R84 keeps the final-goal architecture active by linking the change to flow `B3/B4/B5` and unfinished requirement rows.

Tasks:

- Preserve the four fixed work tabs: dashboard, project tasks, plain tasks, daily reports.
- Add stable deployed UI markers for workbench, tabs, dashboard, warnings, calendar, list, kanban, task detail, create panel, action result, daily reports, auto draft, and draft confirmation.
- Keep project tasks and plain tasks separated while using mutually exclusive list/kanban views.
- Require manual confirmation before an auto-generated daily report draft is saved.
- Add deterministic API/readback evidence for project, project task, plain task, kanban, daily draft, daily report, dashboard, and unauthenticated denial.

Planned evidence:

- Script: `scripts/recovery-r84-work-management-daily-use.ps1`
- Result: `docs/evidence/recovery/r84-work-management-daily-use-result.json`
- Summary: `docs/evidence/recovery/r84-work-management-daily-use-2026-07-07.md`

Still partial:

- R84 can only add engineering evidence for work management. It does not close all requirement rows or set `gates.user_script_passed=true`.
## Batch R83: REC-P0-083 User Trial Feedback Intake And Next Change Selection

Status: accepted as continuation/control evidence only; user signoff remains open.

Reason:

- R80 retained a trial workspace, R81 proved deployed login and role-use paths, and R82 proved those paths are readable on the current deployment.
- The next step is not another broad cleanup batch. It is user trial feedback intake and selection of one concrete follow-up task from user feedback or the still-partial requirement ledger.
- Engineering evidence must stay separate from user verification.

Tasks:

- Keep R83 as the active next executable task in the next execution ledger and session state.
- Preserve R81/R82 evidence as current deployed engineering evidence.
- Record the release URL, trial route/credential evidence, coverage notClosed count, and user signoff boundary.
- If user feedback names a blocker, map it to a role journey and requirement row before coding.
- Create exactly one follow-up REC-P0 task card and batch for the selected change.

Planned evidence:

- Script: `scripts/recovery-r83-user-trial-feedback-intake.ps1`
- Result: `docs/evidence/recovery/r83-user-trial-feedback-intake-result.json`
- Summary: `docs/evidence/recovery/r83-user-trial-feedback-intake-2026-07-07.md`
- Prerequisite role audit: `docs/evidence/recovery/r81-trial-login-role-use-audit-result.json`
- Prerequisite visible-copy audit: `docs/evidence/recovery/r82-visible-copy-encoding-trial-usability-result.json`

Still partial:

- R83 is an intake/control batch. It cannot set `gates.user_script_passed=true` or claim final completion.
## Batch R82: REC-P0-082 Visible Copy Encoding And Trial Usability Cleanup

Status: accepted as deployed visible-copy engineering evidence only; user signoff remains open.

Reason:

- R81 proves the retained trial paths are technically reachable from login, but a normal human-usable system also needs readable visible copy.
- Existing R81 paths still expose areas historically affected by mojibake and unclear state wording, especially auth/system/runtime surfaces.
- The next step should clean visible copy on the exact trial routes rather than starting another broad feature batch.

Tasks:

- Keep R82 as the active next executable task in the next execution ledger and session state.
- Add a deterministic source and deployed-browser visible-copy audit for R81 routes.
- Clean user-visible mojibake/stale placeholder/generic state copy on auth, platform/system shell, runtime, todo, and message surfaces used by R81.
- Re-run R81 after copy cleanup to prove behavior and permissions still hold.
- Keep coverage partial and `gates.user_script_passed=false` until user verification.

Planned evidence:

- Script: `scripts/recovery-r82-visible-copy-encoding-trial-usability.ps1`
- Result: `docs/evidence/recovery/r82-visible-copy-encoding-trial-usability-result.json`
- Summary: `docs/evidence/recovery/r82-visible-copy-encoding-trial-usability-2026-07-07.md`
- Browser audit: `docs/evidence/recovery/screenshots/r82-visible-copy-encoding-trial-usability/visible-copy-browser-audit.json`
- Prerequisite role audit: `docs/evidence/recovery/r81-trial-login-role-use-audit-result.json`

## Batch R60: REC-P0-060 Flow Blueprint And Rebuild Contract Lock

Status: accepted as framework evidence only.

Reason:

- The user clarified that continued local fixing is likely to become unlimited and still not produce a coherent system.
- The next work must first define the full human flow: no token, login, registration, password recovery, role routing, platform shell, system switch, system admin configuration, normal runtime, workflow, OpenAPI, AI, tasks, messages, logs, and operations.
- Development must then split tasks from that blueprint instead of from scattered pages or APIs.

Tasks:

- Add `docs/framework/final-system-flow-blueprint.md`.
- Upgrade `.cursor/architecture/final-goal-framework.md` to require flow ids before coding.
- Update current engineering docs, task cards, fix batches, next execution ledger, and session state so the active task is flow-first.
- Update `scripts/final-goal-framework-audit.ps1` so the blueprint lock is machine-checkable.
- Run framework audit and keep `gates.user_script_passed=false`.

Planned evidence:

- Flow blueprint: `docs/framework/final-system-flow-blueprint.md`
- Framework audit result: `docs/evidence/final-goal-framework-audit-result.json`

Accepted evidence:

- Added `docs/framework/final-system-flow-blueprint.md`.
- Upgraded `.cursor/architecture/final-goal-framework.md` to V8 flow blueprint rebuild contract.
- Added `docs/framework/framework-v8-flow-blueprint-rebuild-contract.md`.
- Updated final acceptance, current product audit, next execution ledger, task cards, fix batches, and session state.
- `scripts/final-goal-framework-audit.ps1` PASS with errors `0`, warnings `0`.

Still partial:

- R60 is framework evidence only. It does not implement auth/entry/routing or set user signoff.

## Batch R61: REC-P0-061 Auth Entry Guard And Registration Landing Closure

Status: accepted as deployed auth/entry engineering evidence only.

Reason:

- The new flow blueprint development order starts with auth and entry guard.
- Current source can render platform/system shells from direct routes before a token is present, and registration currently lands on the system dashboard instead of the system admin first-use path.

Tasks:

- Keep the REC-P0-061 flow contract complete before coding.
- Ensure unauthenticated direct routes render only the auth entry.
- Keep register and password recovery as public auth routes.
- Redirect authenticated users away from auth pages to the platform/system landing.
- Change registration success to enter the system admin first-use path.
- Add deterministic route-guard evidence and run typecheck/build.

Planned evidence:

- Result: `docs/evidence/recovery/r61-auth-entry-guard-result.json`

Accepted evidence:

- Script: `scripts/recovery-r61-auth-entry-guard.ps1`
- Result: `docs/evidence/recovery/r61-auth-entry-guard-result.json`
- Summary: `docs/evidence/recovery/r61-auth-entry-guard-2026-07-02.md`
- R61 source checks passed for auth route list, no-token non-auth guard, authenticated auth-route redirect, default landing, and registration landing.
- `npm typecheck` PASS.
- `npm build` PASS with frontend asset `/assets/index-_ZDECw8K.js`.
- Release package PASS.
- `scripts/verify-release.ps1 -BaseUrl http://127.0.0.1:18131 -CheckDeployedFrontend` PASS with database/schema/Redis `UP` and deployed frontend matching release assets.

Still partial:

- R61 is the first entry-flow implementation slice only. Next work must continue from the flow blueprint rather than claiming the system is usable.

## Batch R62: REC-P0-062 Platform And System Shell Landing Flow Closure

Status: accepted as deployed engineering evidence only.

Reason:

- After auth entry is guarded, the next flow-blueprint step is login landing and shell routing.
- The user explicitly called out the platform root/admin landing shape: dashboard, flow, applications, AI, todos, messages, and login profile information.

Tasks:

- Keep the REC-P0-062 flow contract complete before coding.
- Verify and correct platform workspace navigation for platform root/admin and platform member.
- Verify and correct platform admin entry visibility.
- Verify and correct system switch, system business shell, system admin guard, todo/message/profile entries, and disabled/no-member states.
- Add deployed route/role evidence.

Planned evidence:

- Result: `docs/evidence/recovery/r62-platform-system-shell-landing-result.json`
- Summary: `docs/evidence/recovery/r62-platform-system-shell-landing-2026-07-02.md`

Accepted evidence:

- `scripts/recovery-r62-platform-system-shell-landing.ps1` PASS with platform navigation, AI route, route registry, initialized-account shell guard, platform/system admin guards, system switch context, `npm typecheck`, and `npm build`.
- Release package PASS with frontend asset `/assets/index-CLQXbZ2s.js`.
- `scripts/verify-release.ps1 -BaseUrl http://127.0.0.1:18131 -CheckDeployedFrontend` PASS with database/schema/Redis `UP`.
- Browser verification caught and closed a stale-token/empty-account shell issue: direct platform route now redirects to login after reload instead of rendering "鏈櫥褰? platform shell.
- Browser login as `admin` proved platform navigation, `/platform/ai`, backend AI health check, and switch into `#/systems/258/dashboard`.

Still partial:

- R62 is the login landing and shell-routing slice. R59 remains required for the no-code first-use configuration chain that makes the platform actually useful end to end.

## Status Language

Use these status words only:

- `open`: ready for work or currently being audited.
- `planned`: ordered but not started.
- `in_progress`: actively being worked in this session.
- `blocked`: cannot proceed without missing input or environment.
- `accepted`: completed with required evidence.

Do not use "done", "basically done", "complete", or "self-checked" for user-facing completion.


Accepted evidence for Batch R84:

- `scripts/recovery-r84-work-management-daily-use.ps1` PASS.
- Result: `docs/evidence/recovery/r84-work-management-daily-use-result.json`.
- Summary: `docs/evidence/recovery/r84-work-management-daily-use-2026-07-07.md`.
- Release verification PASS on `http://127.0.0.1:18131` after package/restart.
- R84 remains engineering evidence only; user signoff is still open.

## Batch R86: REC-P0-086 Page Designer Drag Canvas And Runtime Component Contract Closure

Status: accepted as deployed page-designer drag-canvas/runtime-component engineering evidence only; user signoff remains open.

Reason:

- The earliest remaining FRC-1 gap still names page designer drag/drop polish, richer runtime widget behavior, mobile usability, and user acceptance.
- Existing R42/R46/R68 evidence proves component copy/order/hide and publish/readback, but the designer can still feel like a table of buttons rather than a page canvas.
- R86 must strengthen the no-code page design loop without inventing a second page-design backend contract.

Tasks:

- Keep REC-P0-086 as the active next executable task in the next execution ledger and session state.
- Add page-designer drag/canvas affordances, keyboard-reachable reorder controls, layout prop chips, and mobile preview markers.
- Persist layout metadata through existing `PageComponentConfig.props` and prove readback through existing page APIs.
- Extend schema/runtime component preview so published component order and props are visible and browser-auditable.
- Add deterministic R86 evidence that checks source markers, deployed asset markers, API save/publish/runtime readback, hidden component pruning, permission negatives, and user signoff separation.

Planned evidence:

- Script: `scripts/recovery-r86-page-designer-drag-canvas-runtime-contract.ps1`
- Result: `docs/evidence/recovery/r86-page-designer-drag-canvas-runtime-contract-result.json`
- Summary: `docs/evidence/recovery/r86-page-designer-drag-canvas-runtime-contract-2026-07-07.md`
- Browser audit: `docs/evidence/recovery/screenshots/r86-page-designer-drag-canvas-runtime-contract/page-designer-browser-audit.json`

Still partial:

- R86 cannot set `gates.user_script_passed=true` or claim final completion.
- Full page designer accessibility, every page widget variant, binary PDF boundary, all advanced capability breadth, and user acceptance remain governed by the coverage ledger.
Accepted evidence for Batch R86:

- `scripts/recovery-r86-page-designer-drag-canvas-runtime-contract.ps1` PASS on deployed `http://127.0.0.1:18131`.
- Result: `docs/evidence/recovery/r86-page-designer-drag-canvas-runtime-contract-result.json`.
- Summary: `docs/evidence/recovery/r86-page-designer-drag-canvas-runtime-contract-2026-07-07.md`.
- Browser audit/screenshots: `docs/evidence/recovery/screenshots/r86-page-designer-drag-canvas-runtime-contract/`.
- Typecheck/build/package-release/local-start/verify-release passed; source and deployed asset markers proved drag canvas, draggable rows, width/placement props, and schema component strip.
- R86 remains engineering evidence only; user signoff is still open and coverage rows remain PARTIAL.
## Batch R87: REC-P0-087 System Todo Message Workbench Usability Closure

Status: accepted as deployed todo/message workbench usability engineering evidence only; user signoff remains open.

Reason:

- R84/R85 made work management and the dashboard usable, but the todo/message pages still need a stronger daily handling loop.
- Remaining coverage gaps explicitly call out message breadth, daily work usability, dashboard/work/todo/message integration, mobile primary journeys, and user acceptance.
- R87 must strengthen the existing system shell and existing todo/message APIs instead of creating parallel routes or duplicate backend contracts.

Tasks:

- Keep REC-P0-087 as the active next executable task in the next execution ledger and session state.
- Add selected todo detail, target summary, action/disabled-reason clarity, and action result readback to the system todo workbench.
- Add message detail metadata, read/archive controls, state/result markers, and empty/filter-state clarity to the system message center.
- Add deterministic R87 evidence that checks source markers, deployed asset markers, API readback, browser DOM markers, permission negatives, and user signoff separation.

Planned evidence:

- Script: `scripts/recovery-r87-todo-message-workbench-usability.ps1`
- Result: `docs/evidence/recovery/r87-todo-message-workbench-usability-result.json`
- Summary: `docs/evidence/recovery/r87-todo-message-workbench-usability-2026-07-07.md`
- Browser audit: `docs/evidence/recovery/screenshots/r87-todo-message-workbench-usability/todo-message-workbench-browser-audit.json`

Still partial:

- R87 cannot set `gates.user_script_passed=true` or claim final completion.
- Message templates/channels, delivery retry breadth, do-not-disturb policy breadth, every workflow variant, and user acceptance remain governed by the coverage ledger.
Accepted evidence for Batch R87:

- `scripts/recovery-r87-todo-message-workbench-usability.ps1` PASS on deployed `http://127.0.0.1:18131`.
- Result: `docs/evidence/recovery/r87-todo-message-workbench-usability-result.json`.
- Summary: `docs/evidence/recovery/r87-todo-message-workbench-usability-2026-07-07.md`.
- Browser audit/screenshots: `docs/evidence/recovery/screenshots/r87-todo-message-workbench-usability/`.
- Typecheck/build/package-release/local-start/verify-release passed; source and deployed asset markers proved todo detail panel, action result slot, message toolbar, read/archive controls, message result slot, and responsive workbench layout.
- R87 remains engineering evidence only; user signoff is still open and coverage rows remain PARTIAL.
## Batch R88: REC-P0-088 Runtime Efficiency Entry Search Recent Draft Closure

Status: accepted as deployed runtime efficiency entry/search/recent/draft engineering evidence only; user signoff remains open.

Reason:

- R85 made the system dashboard a daily hub, and R87 made todo/message workbench handling clearer, but ordinary runtime data work still needs a coherent efficiency loop.
- Remaining runtime gaps call out daily runtime usability, list/form/mobile states, search, drafts, and user acceptance before any row can become `PROVEN`.
- R88 must strengthen the existing system shell and runtime record page using existing runtime APIs and scoped browser state, not create a duplicate runtime surface.

Tasks:

- Keep REC-P0-088 as the active next executable task in the next execution ledger and session state.
- Add dashboard entries for runtime search, recent work, saved draft continuation, and authorized quick create.
- Add a runtime efficiency strip with stable markers for search/recent/draft/quick-create states.
- Persist recent/draft index state scoped by system/module and prove a saved draft can be resumed into the runtime form.
- Add deterministic R88 evidence that checks source markers, deployed asset markers, API readback, draft/recent behavior, permission negatives, and user signoff separation.

Planned evidence:

- Script: `scripts/recovery-r88-runtime-efficiency-entry-search-recent-draft.ps1`
- Result: `docs/evidence/recovery/r88-runtime-efficiency-entry-search-recent-draft-result.json`
- Summary: `docs/evidence/recovery/r88-runtime-efficiency-entry-search-recent-draft-2026-07-07.md`
- Browser audit: `docs/evidence/recovery/screenshots/r88-runtime-efficiency-entry-search-recent-draft/runtime-efficiency-browser-audit.json`

Still partial:

- R88 cannot set `gates.user_script_passed=true` or claim final completion.
- Import/export rollback breadth, file versioning, every field widget variant, operations breadth, and user acceptance remain governed by the coverage ledger.
Accepted evidence for Batch R88:

- `scripts/recovery-r88-runtime-efficiency-entry-search-recent-draft.ps1` PASS on deployed `http://127.0.0.1:18131`.
- Result: `docs/evidence/recovery/r88-runtime-efficiency-entry-search-recent-draft-result.json`.
- Summary: `docs/evidence/recovery/r88-runtime-efficiency-entry-search-recent-draft-2026-07-07.md`.
- Browser audit/screenshots: `docs/evidence/recovery/screenshots/r88-runtime-efficiency-entry-search-recent-draft/`.
- Typecheck/build/package-release/local-start/verify-release passed; source and deployed asset markers proved dashboard/runtime efficiency entries, recent/draft state, draft resume, quick create, and field-error marker.
- R88 remains engineering evidence only; user signoff is still open and coverage rows remain PARTIAL.
## Batch R89: REC-P0-089 Runtime File Import Export Recovery Detail Closure

Status: accepted as deployed runtime file/import/export recovery-detail engineering evidence only; user signoff remains open.

Reason:

- R88 made ordinary runtime entry/search/draft work faster, but the gap report still names import/export rollback UX, file preview/download permission breadth, error-file failure cases, field mapping breadth, large-file UX, detail states, mobile, and user acceptance.
- R72 proved the backend paths and a basic deployed residual, but a human operator still needs a visible recovery detail surface that ties task, files, rollback boundary, and scope together.
- R89 must strengthen the existing runtime import/export panel and existing APIs instead of creating a parallel data movement page.

Tasks:

- Keep REC-P0-089 as the active next executable task in the next execution ledger and session state.
- Add import/export recovery detail cards with stable markers for precheck, task, result/error files, selected/all/template scope, trace/audit ids, and rollback boundary.
- Add clear selected-export empty/disabled state and file result actions that are visible in the deployed UI.
- Add deterministic R89 evidence that checks source markers, deployed asset markers, API file/import/export readback, browser DOM markers, permission negatives, and user signoff separation.

Planned evidence:

- Script: `scripts/recovery-r89-runtime-file-import-export-recovery-detail.ps1`
- Result: `docs/evidence/recovery/r89-runtime-file-import-export-recovery-detail-result.json`
- Summary: `docs/evidence/recovery/r89-runtime-file-import-export-recovery-detail-2026-07-07.md`
- Browser audit: `docs/evidence/recovery/screenshots/r89-runtime-file-import-export-recovery-detail/runtime-file-import-export-recovery-browser-audit.json`

Still partial:

- R89 cannot set `gates.user_script_passed=true` or claim final completion.
- Full file versioning, every storage-policy variant, every import mapping/large-file path, operations breadth, and user acceptance remain governed by the coverage ledger.
Accepted evidence for Batch R89:

- `scripts/recovery-r89-runtime-file-import-export-recovery-detail.ps1` PASS on deployed `http://127.0.0.1:18131`.
- Result: `docs/evidence/recovery/r89-runtime-file-import-export-recovery-detail-result.json`.
- Summary: `docs/evidence/recovery/r89-runtime-file-import-export-recovery-detail-2026-07-07.md`.
- Browser audit/screenshots: `docs/evidence/recovery/screenshots/r89-runtime-file-import-export-recovery-detail/`.
- Typecheck/build/package-release/local-start/verify-release passed; source and deployed asset markers proved import/export recovery detail, selected/all/template export state, file actions, rollback boundary, permission negatives, and responsive containment.
- R89 remains engineering evidence only; user signoff is still open and coverage rows remain PARTIAL.
## Batch R90: REC-P0-090 System Org Member Role Binding First-Use Closure

Status: accepted as deployed organization/member/role binding engineering evidence only; user signoff remains open.

Reason:

- The gap report still names `REQ-5.7` departments and members plus `REQ-5.10` permission matrix usability as partial.
- R70/R71/R86 proved no-code and runtime permission mechanics, but a normal deployment still needs the system administrator to bind real people to accounts and roles before the system can be handed to users.
- R90 must strengthen the existing system admin organization/role surfaces and existing APIs instead of creating a parallel identity page.

Tasks:

- Keep REC-P0-090 as the active next executable task in the next execution ledger and session state.
- Add organization/member first-use operations with stable markers for department selection, member creation/update, account binding, role assignment, binding/readback state, and switch readiness.
- Add deterministic R90 evidence that checks source markers, deployed asset markers, API org/member/bind/assign readback, browser DOM markers, normal-member switch/runtime access, admin/API permission negatives, and user signoff separation.

Planned evidence:

- Script: `scripts/recovery-r90-org-member-role-binding-first-use.ps1`
- Result: `docs/evidence/recovery/r90-org-member-role-binding-first-use-result.json`
- Summary: `docs/evidence/recovery/r90-org-member-role-binding-first-use-2026-07-07.md`
- Browser audit: `docs/evidence/recovery/screenshots/r90-org-member-role-binding-first-use/org-member-role-binding-browser-audit.json`

Accepted evidence:

- R90 PASS on deployed `http://127.0.0.1:18131`.
- API readback proved system `1134`, member `1551`, role `1651`, binding `1551`, binding status `BOUND`, role code `R90_RUNTIME_20260707224102`, and normal login `r90_normal_20260707224102`.
- Normal account switch context included the assigned role, while normal-member org/member/role admin APIs were denied.
- Browser audit produced 5 results with desktop/mobile org/member delivery markers, role workbench marker, normal-admin denied state, overflow `0`, blockers `0`, and no user signoff claim.

Still partial:

- R90 cannot set `gates.user_script_passed=true` or claim final completion.
- Full org import/sync, every role/permission matrix permutation, SSO mapping breadth, and user acceptance remain governed by the coverage ledger.


## Batch R91: REC-P0-091 Role Permission Matrix Impact Preview Audit Closure

Status: accepted as deployed permission-matrix impact-preview engineering evidence only; user signoff remains open.

Reason:

- R90 proved real people can be bound to roles, but the gap report still names `REQ-5.10` and `REQ-6.10` permission matrix usability as partial.
- A normal deployment needs the system administrator to understand permission impact before handing the role to users: which actions are denied, which fields are hidden or masked, which data scope applies, and which members are affected.
- R91 must strengthen the existing role permission workbench and permission APIs instead of creating a parallel authorization page.

Tasks:

- Keep REC-P0-091 as the active next executable task in the next execution ledger and session state.
- Add batch permission preview and impact/audit readback to the existing permission API surface.
- Add deployed permission workbench markers for batch action decisions, field mask/data-scope/conflict explanation, affected member count, recent preview audit rows, and denied normal-admin state.
- Add deterministic R91 evidence that checks source markers, deployed asset markers, API save/batch-preview/audit readback, browser DOM markers, normal-member runtime/admin negatives, and user signoff separation.

Planned evidence:

- Script: `scripts/recovery-r91-permission-impact-preview-audit.ps1`
- Result: `docs/evidence/recovery/r91-permission-impact-preview-audit-result.json`
- Summary: `docs/evidence/recovery/r91-permission-impact-preview-audit-2026-07-07.md`
- Browser audit: `docs/evidence/recovery/screenshots/r91-permission-impact-preview-audit/permission-impact-preview-browser-audit.json`

Still partial:

- R91 cannot set `gates.user_script_passed=true` or claim final completion.
- Every role/permission matrix permutation, workflow/timer permissions, SSO mapping breadth, audit-history breadth, and user acceptance remain governed by the coverage ledger.
## Batch R92: REC-P0-092 Workflow Designer Advanced Node Publish Impact Closure

Status: in_progress as deployed workflow-designer advanced-node and publish-impact engineering evidence only; user signoff remains open.

Reason:

- R91 closed the next permission-matrix impact-preview loop, but the gap report still names `REQ-4.5`, `REQ-5.12`, and `REQ-6.9` workflow designer breadth as partial.
- A normal deployment needs administrators to understand advanced node configuration and publish impact before runtime users depend on workflow/todo/message outcomes.
- R92 must strengthen the existing flow management/designer and workflow runtime instead of creating a parallel workflow page.

Tasks:

- Keep REC-P0-092 as the active next executable task in the next execution ledger and session state.
- Add deterministic advanced-node configuration/readback and publish-impact evidence for timer, reject/transfer, field update, external API, and simulation/failure states where required.
- Add deployed flow designer/runtime markers for advanced node library, node properties, publish impact, simulation result, requester/approver todo/message states, terminal action states, and denied normal-admin state.
- Add deterministic R92 evidence that checks source markers, deployed asset markers, API readback, browser DOM markers, workflow runtime positives/negatives, and user signoff separation.

Planned evidence:

- Script: `scripts/recovery-r92-workflow-advanced-node-publish-impact.ps1`
- Result: `docs/evidence/recovery/r92-workflow-advanced-node-publish-impact-result.json`
- Summary: `docs/evidence/recovery/r92-workflow-advanced-node-publish-impact-2026-07-08.md`
- Browser audit: `docs/evidence/recovery/screenshots/r92-workflow-advanced-node-publish-impact/workflow-advanced-node-browser-audit.json`

Still partial:

- R92 cannot set `gates.user_script_passed=true` or claim final completion.
- Every workflow/node permutation, full timer/escalation scheduler semantics, OpenAPI/AI breadth, operations breadth, and user acceptance remain governed by the coverage ledger.

## RECOVERY-R93 Platform Flow/Application IA Boundary

Trigger: 2026-07-08 user feedback plus `temp_flow.md` and `temp_flow_persion.html` review.

Scope: correct platform workbench information architecture before resuming workflow feature slices. Main platform modules must be dashboard/workbench, Flow, Application, and Work; AI, system switch, create system, todo, message, and profile are auxiliary actions. `/platform/apps` must become an application/authorization/configuration surface, not a system-entry page. `/platform/flow` must become an independent platform Flow surface. `/platform` keeps the system-entry panel.

Evidence: `scripts/recovery-r93-platform-flow-app-ia-boundary.ps1` and deployed/source browser checks. Engineering evidence only; no user signoff claim.
