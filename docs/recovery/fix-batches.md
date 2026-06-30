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
- At the R4 checkpoint, data source remained explicitly `待接入` because no data-source backend contract/table existed; R6 later closes this gap with API-backed data-source management.
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
- R4 intentionally exposed data source as `待接入` because no backend contract/table existed.
- This is not acceptable as a final product end state, so it must be implemented before claiming the user's final goal.

Tasks:

- Add persisted system data source configuration with system/tenant scope.
- Add data source list/create/detail/update, connection check, and publish-check APIs.
- Bind the system admin data-source page to real APIs and remove the `待接入` placeholder.
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
- Browser evidence on deployed frontend asset `/assets/index-C49n4ymp.js` proves the approved no-member page shows `APPROVED`, member/binding/SSO ids, no stale `NO_SYSTEM_MEMBER_MAPPING` blocker, and the `进入系统` action routes to the target system dashboard.
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

- On a 390px viewport, `新建`, `导入`, `全部导出`, and `列设置` are fully inside the viewport or wrapped into visible rows.
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
- Browser evidence used real `/login`, opened system admin flow management, clicked `配置画布`, inserted approval/end nodes, edited node properties, saved the canvas, ran simulation, and ran publish-check.
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
- Evidence: `docs/evidence/recovery/r24-home-page-config-2026-06-30.md`, `docs/evidence/recovery/r24-home-page-config-result.json`, `docs/evidence/recovery/screenshots/r24-home-page-config/home-page-config-browser-audit.json`, `docs/evidence/recovery/r24-page-designer-2026-06-30.md`, `docs/evidence/recovery/r24-page-designer-result.json`, `docs/evidence/recovery/r24-visual-style-browser-audit-2026-06-30.md`, and `docs/evidence/recovery/r24-visual-style-browser-audit-result.json`.

Remaining work before R24/FRC-1 can be accepted:

- `REQ-6.1` overall visual style is narrowed to `PARTIAL`; it still needs broader human/UI review across role journeys before `PROVEN`.
- `REQ-6.8` page designer is narrowed to `PARTIAL`; it still needs deployed browser interaction, visual preview, broader component coverage, copy/drag-drop/mobile preview, and usability evidence before `PROVEN`.
- `REQ-9` advanced/new capabilities still need split task cards and evidence.
- `REQ-5.8` pages and `REQ-6.3` home page are narrowed to `PARTIAL`, not final completion.

## Status Language

Use these status words only:

- `open`: ready for work or currently being audited.
- `planned`: ordered but not started.
- `in_progress`: actively being worked in this session.
- `blocked`: cannot proceed without missing input or environment.
- `accepted`: completed with required evidence.

Do not use "done", "basically done", "complete", or "self-checked" for user-facing completion.
