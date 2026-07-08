# Final Usable System Acceptance

Time: 2026-07-01 Asia/Shanghai

## Purpose

This document is the top-level recovery gate for the user's final goal: the project must become a system that real people can use.

The R-series recovery batches are evidence inputs only. They do not equal final completion. A local page fix, an API 200 response, a generated CRUD table, a successful package build, or a single browser smoke cannot close the final product goal by itself.

## 2026-07-01 User Feedback Reopen

The user's latest deployed-use feedback reopens final usable-system acceptance.

Observed failure class:

- the standalone deployed product still feels close to the previous broken state
- pages and functions remain confusing or visually crowded in real use
- earlier engineering work reported completion while the user-facing final goal was not reached
- screenshot-heavy checks did not adequately confirm requirement coverage, workflow closure, persistence, permissions, or product usefulness
- the framework let old slice evidence look stronger than it was

Decision:

- historical R-series evidence remains useful engineering evidence
- historical journey `PASS` rows below are downgraded to `PARTIAL_PASS_ENGINEERING` unless the gate is release-only and still verified by current release scripts
- no gate in this file may be treated as final user acceptance while `gates.user_script_passed=false`
- the next execution must upgrade the framework and then run fresh deployed role-journey audits from requirement rows, not from screenshots or old summaries

## Final Product Target

The accepted target comes from `docs/user_requirement.md` and `docs/design/prototype-brief.md`:

- An administrator can create a system, configure module groups, modules, fields, actions, permissions, approval flow, dictionaries, members, external access, import/export, and publish it.
- A normal member can log in, enter an authorized system, open a business module, create/edit/query/filter/detail data, upload attachments, handle approval, read messages/todos, and export authorized data.
- External systems can access authorized data safely through OpenAPI.
- The release can be packaged, deployed, restarted, health-checked, audited, and maintained.
- The product has four clear shells: platform workspace, platform admin, system business page, and system admin.

## Non-Completion Cases

The final goal is not accepted if any of these are true:

- A batch script passes but the role journey is not runnable from login to result.
- A page exists but the user cannot finish the business action.
- A backend API works but the deployed frontend is not bound to it.
- A frontend action only shows a toast, local state, prompt, or static prototype data.
- Generated CRUD exists but coded business behavior, permission, state, and evidence are missing.
- Admin-only evidence is used to claim normal-member usability.
- One happy path passes while permission negative, empty state, error state, mobile containment, or persistence readback is missing.
- R5/R20 passes but there is no single coherent final journey across admin configuration, normal runtime work, approval, messages/todos, external integration, and deployment.

## Evidence Standard

Each final journey gate must include:

- deployed frontend browser evidence on desktop and mobile where relevant
- real login through the deployed page unless the step is explicitly deployer-only
- API readback or database-backed readback
- permission positive and negative cases
- persisted state after reload or restart where the journey depends on it
- loading, empty, disabled, validation, error, or async-task states for the key action
- traceId, taskId, auditLogId, or equivalent operational evidence for critical actions
- cleanup of disposable systems/data unless intentionally kept

## Journey Gates

| Gate | Name | Required user-level outcome | Current evidence input | Current status |
|---|---|---|---|---|
| J0 | Release health | Build/package/start/restart/health/deployed asset/admin login all pass from the release package. | R5, verify-release, R50 release/log evidence. | `PASS_ENGINEERING` |
| J1 | Register first system | A new user registers with a system, lands on initialization guidance, enters system admin, and receives system-super-admin context. | R16. | `PARTIAL_PASS_ENGINEERING` |
| J2 | Admin builds business app | System admin creates module group/module/fields/list/actions/dictionary/role/flow/member, publishes, and sees the business app become usable. | R2, R12, R19, R20, R38, R44-R46. | `PARTIAL_PASS_ENGINEERING` |
| J3 | Normal user daily work | Normal member logs in, opens authorized business shell, creates/edits/filters/details/uploads/exports data, and cannot access admin. | R17, R18, R10, R37, R40-R43, R47. | `PARTIAL_PASS_ENGINEERING` |
| J4 | Approval and notification | Requester submits approval, approver receives todo/message, processes it, and record/todo/message states close. | R3, R8, R32, R48. | `PARTIAL_PASS_ENGINEERING` |
| J5 | Admin configuration depth | System admin surfaces for org, roles, modules, work config, flow canvas, dictionaries, data source, SSO, Agent, external app, logs are usable and not stacked/mixed. | R4, R6, R9, R11, R13, R19, R20, R36, R38, R42, R44-R46, R50. | `PARTIAL_PASS_ENGINEERING` |
| J6 | External and import/export | External app, SecretRef, OpenAPI call, upload, import, export, call logs, and result files work from UI and API. | R10, R47, R49. | `PARTIAL_PASS_ENGINEERING` |
| J7 | Operations and maintenance | Release package supports start/stop/restart/health, deployed assets match, logs/trace evidence exist, and backup/rollback/maintenance gaps are explicitly tracked. | R22, R50. | `PARTIAL_PASS_ENGINEERING` |

## Current Decision

The older engineering final usable-system gate is reopened.

Current status is `REOPENED_BY_USER_FEEDBACK`. Engineering evidence exists, but it does not prove final acceptance. `gates.user_script_passed` remains `false`.

The next accepted work must prove one requirement-driven role journey at a time and keep any still-unfinished requirement row visible in `docs/framework/final-requirement-coverage-ledger.md`, `docs/evidence/final-requirement-gap-report.md`, and `docs/framework/next-execution-ledger.md`.

## 2026-07-01 Root-Cause Summary For Repeated Misses

The repeated failure was not caused by one missing button or one broken API. The execution model allowed four weaker signals to replace the user's real target:

- target drift: batch/page/API completion was treated as progress toward "a usable system" even when the full role journey was still partial
- task drift: work was often split by implementation surface instead of small user jobs that combine frontend, backend, data, permission, state, and release proof
- evidence drift: screenshots, build success, API 200, generated CRUD, and smoke PASS were accepted as stronger evidence than they actually were
- feedback drift: user feedback that the deployed product was still confusing did not always become a harder active-task gate before the next coding slice started

Correction rule: after any user feedback that the deployed product is still not usable, the next work must first state and persist the root cause, then continue only from an unfinished requirement row plus role journey. A recovery slice can be accepted only as engineering evidence until full requirement coverage and explicit user signoff close this file.

## R51 Diagnostic Result

R51 is diagnostic evidence only:

- Script: `scripts/recovery-r51-final-role-journey-gap-audit.ps1`
- Result: `docs/evidence/recovery/r51-final-role-journey-gap-audit-result.json`
- `status=PASS`
- `productStatus=FAIL_EXPECTED`
- Requirement rows not closed: `45`
- Final journey partial rows: `8`
- User signoff: `false`
- First implementation batch: `FRC-1 Missing Product Surfaces`
- Next task: `REC-P0-052 FRC-1 Missing Product Surfaces Fresh Deployed Closure`

## Operating Rule

When a new problem is found, update this file, `docs/recovery/p0-task-cards.md`, `docs/recovery/fix-batches.md`, and `docs/recovery/current-product-audit.md` before coding. The recovery process must move from isolated feature batches toward coherent role journeys.

## 2026-07-01 V7 Human-Usable Gate Upgrade

The user's latest correction makes the active final gate stricter:

- the framework itself was still below the final target because it allowed local engineering slices to look like user-facing completion
- future work must prefer human role-journey acceptance over another feature aggregation whenever the deployed product is still reported as confusing or unusable
- ordinary engineering decisions should be executed by the agent from the approved requirement, prototype, ledgers, and source rather than repeatedly pushed back to the user
- page stacking, mixed shells, ambiguous tips, unclear disabled/error/success messages, stale prototype behavior, and generated-only capabilities are final-goal blockers, not cosmetic follow-ups

Current V7 record:

- Framework upgrade: `docs/framework/framework-v7-human-usable-system-upgrade.md`
- Latest accepted human journey evidence: `REC-P0-057 FRC-6 Human Acceptance Pass Fresh Closure`
- Latest accepted density convergence evidence: `REC-P0-058 High-Density Surface Convergence`
- Latest accepted flow blueprint evidence: `REC-P0-060 Flow Blueprint And Rebuild Contract Lock`
- Latest accepted auth/entry evidence: `REC-P0-061 Auth Entry Guard And Registration Landing Closure`
- Latest accepted shell-landing evidence: `REC-P0-062 Platform And System Shell Landing Flow Closure`
- Latest accepted admin first-use evidence: `REC-P0-059 FRC-2/FRC-6 Admin Configuration Human First-Use Closure`
- Latest accepted configured runtime evidence: `REC-P0-063 B1/B2 Configured Runtime First-Use And Daily Business Closure`
- Latest accepted workflow/todo/message evidence: `REC-P0-064 C4/B4/B5 Workflow Todo Message First-Use And Closure`
- Latest accepted OpenAPI/AI evidence: `REC-P0-065 E1/AI2 OpenAPI Assistant External-Service First-Use Closure`
- Latest accepted operations/logs/release evidence: `REC-P0-066 O1/O2/O3 Operations Logs Release Maintenance First-Use Closure`
- Latest accepted final candidate refresh evidence: `REC-P0-067 Final Role Journey And Requirement Acceptance Candidate Refresh`
- Latest accepted page/visual/page-designer evidence: `REC-P0-068 Page Visual Designer Fresh Evidence Closure`
- Latest accepted requirement evidence promotion decision: `REC-P0-069 Requirement Evidence Promotion And Residual Gap Decision`
- Latest accepted no-code configuration residual depth evidence: `REC-P0-070 No-Code Configuration Residual Depth Closure`
- Latest accepted no-code frontend binding evidence: `REC-P0-071 No-Code Frontend Binding And Hierarchy Residual Closure`
- Latest accepted runtime file/import-export/error-state evidence: `REC-P0-072 Runtime Daily-Use File Import Export Error-State Residual Closure`
- Latest accepted workflow/todo/message residual evidence: `REC-P0-073 Workflow Todo Message Integration Error-State Residual Closure`
- Latest accepted OpenAPI/AI external-service residual evidence: `REC-P0-074 OpenAPI AI External-Service Error-State Residual Closure`
- Latest accepted auth/shell entry role-flow residual evidence: `REC-P0-076 Auth Shell Entry Role Flow Residual`
- Latest accepted operations/logs/release residual evidence: `REC-P0-075 Operations Logs Release Maintenance Error-State Residual Closure`
- Latest accepted final candidate refresh evidence: `REC-P0-077 Final Requirement Candidate Refresh After Residual Closure`
- Latest accepted product-surface evidence: `REC-P0-078 FRC-1 Product Surface Human Acceptance Residual Closure`
- Active engineering boundary: `REC-P0-082 Visible Copy Encoding And Trial Usability Cleanup`
- Active flow source: `docs/framework/final-system-flow-blueprint.md`
- Required scope: auth state, registration, password recovery, role routing, four shells, system switch, first-use configuration, runtime, workflow/todo/message, OpenAPI/import/export, AI, logs, tasks, and operations
- User signoff: still `false`

## R57/R58 Engineering Evidence Update

R57 and R58 are useful evidence, but they do not close final acceptance:

- R57 passed on deployed `http://127.0.0.1:18131` with release health `UP`, static blockers/warnings `0`, role journey results `32`, failures `0`, warnings `0`, permission negatives `403/403`, and V7 human-usable gate assertions.
- R58 passed on the same deployed release with asset `/assets/index-BnZhqLS7.js`, runtime module max visible button count `27`, work max panel count `7`, work max button count `14`, static blockers/warnings `0`, and fresh R57 still PASS.

R62 is accepted as deployed engineering evidence because the logged-in platform/system shell landing path now has role-aware navigation, a live platform AI entry, initialized-account shell guard, browser login evidence, AI health evidence, and system switch evidence.

R59 is accepted as deployed engineering evidence because system-admin module configuration now uses one active task surface across lifecycle, fields, list/action/import-export, page, and print; browser evidence covers all five task tabs on desktop/mobile with blockers `0` and overflow `0`; and the fresh R53 no-code configuration chain still proves API/readback, permission preview, and runtime schema reflection. The next execution is R63 because the product now needs the published admin configuration to become a normal-member daily runtime business page, not another local admin-page repair.

R63 is accepted as deployed engineering evidence because the admin-published module became a normal-member runtime business page: deployed browser evidence created, read, edited, and read back record `469` on desktop/mobile through published version `MODULE_v1782963082835`, hidden fields did not leak, and forbidden runtime/admin actions returned `403`.

R64 is accepted as deployed engineering evidence because workflow/todo/message closed as one requester/approver role journey: requester submitted record `474` from the deployed runtime edit form, approver opened message/todo surfaces and approved through the deployed browser, terminal detail/sidebar reached `APPROVED`, requester/admin denials returned `403`, duplicate terminal action returned `TASK_STATE_CONFLICT` HTTP `400`, browser overflow/blockers were `0`, and cleanup succeeded.

R65 is accepted as deployed engineering evidence because OpenAPI/external service and AI assistant closed as a scoped first-use journey: external app `56` created record `475`, SecretRef/rotation/log boundaries passed, wrong-secret/read-only/normal-member denials passed, assistant write/draft confirmations enforced human confirmation states, browser overflow/blockers were `0`, and cleanup succeeded.

R66 is accepted as deployed engineering evidence because operations/logs/release maintenance closed as an operator-readable journey: release verification passed, packaged server commands were present, operations tasks and log readback were tied to trace ids, normal-member denials were logged as failures, static audit blockers/warnings were `0`, browser overflow/blockers were `0`, and cleanup succeeded.

R67 is accepted as final-candidate engineering evidence because it ran release/static/framework/requirement audits, refreshed R57 role-shell evidence, aggregated R59/R63/R64/R65/R66 browser evidence, and produced a 45-row candidate matrix without promoting any row to final `PROVEN`. It found the next concrete gap: `REQ-5.8`, `REQ-6.1`, and `REQ-6.8` still needed fresh page/visual/page-designer evidence.

R68 is accepted as page/visual/page-designer engineering evidence because it reran release/static/framework checks, refreshed R42 page-designer component evidence, refreshed R46 page publish/runtime evidence, proved hidden field/component pruning and normal-member write denials, and produced desktop/mobile browser evidence with overflow/blockers `0`.

R69 is accepted as requirement evidence promotion decision evidence because it reran release/static/framework/coverage checks, ingested R67/R68, produced a 45-row matrix, confirmed all rows now have fresh evidence, promoted `0` rows, kept `45` rows partial, and emitted the next residual batch: `REC-P0-070 No-Code Configuration Residual Depth Closure`. User signoff remains explicitly false.

R70 is accepted as no-code configuration residual-depth engineering evidence because it rebuilt/restarted the release, fixed backend navigation metadata/runtime direct-access guarding, proved normal-member direct denials for hidden/draft/disabled/draft-group modules, refreshed permission preview and field/dictionary/menu evidence, and kept browser overflow/blockers at `0`. It also records a real remaining model gap: module groups currently have no persisted `parentId`, so nested module groups cannot be claimed until implemented or explicitly excluded. Active next work is R71 frontend binding and hierarchy residual closure. User signoff remains explicitly false.

R71 is accepted as no-code frontend binding engineering evidence because it rebuilt/repackaged/restarted the release, exposed stable browser markers for configured group/module/field/member/role ids and state, proved member-role binding/workbench markers, proved normal-member runtime hidden DOM non-leakage for hidden modules/fields, kept direct API denials at `403`, kept desktop/mobile overflow and blockers at `0`, and cleaned systems `1007/1008`. It does not claim nested module hierarchy because the backend model still lacks `parentId`.

R72 is accepted as runtime file/import-export/error-state engineering evidence because it rebuilt/repackaged/restarted the release, fixed failed import-precheck confirmation so it now returns `400`, proved attachment readback/preview/download/missing/denied states, proved import success and failed-precheck error-file behavior, proved export-all and selected-export result files, proved batch archive async status `QUEUED`, kept hidden field leakage `false`, kept readonly/admin forbidden states at `403`, kept browser overflow/blockers at `0`, and cleaned systems `1015/1016/1017`.

R73 is accepted as workflow/todo/message residual engineering evidence because it proved publish-check/simulation, requester submit, assigned approver todo/message delivery, message read/archive state, approval terminal `APPROVED`, rejection terminal `REJECTED`, requester/admin denials `403`, duplicate todo `TASK_STATE_CONFLICT`, terminal reject/transfer conflicts `400`, browser overflow/blockers `0`, and cleanup `1024/1025/1026/1027/1028/1029`. Active next work is R74 OpenAPI/AI/external-service error-state residual closure. User signoff remains explicitly false.

R74 is accepted as OpenAPI/AI external-service residual engineering evidence because it proved new active SecretRef rotation, old SecretRef denial `401`, read-only denial `403`, wrong-secret denial `401`, success/failure call logs, platform AI scope denial `REJECTED_BY_SCOPE`, assistant write preview/confirm/reject, duplicate confirm denial `400`, work draft preview/confirm, normal-member OpenAPI/policy denials `403/403`, browser overflow/blockers `0`, and cleanup `1032/1033`. Active next work is R75 operations/logs/release maintenance error-state residual closure. User signoff remains explicitly false.

R76 is accepted as auth/shell entry role-flow engineering evidence because multi-agent review identified a foundation drift before R75: platform-role login could still be pulled into a system context too early, stale `/platform/dashboard` remained in landing contracts, and admin routes could still be visually wrapped by broader workbench shells. R76 source/build evidence proves login bootstrap no longer auto-switches to the first system, frontend/backend landing normalizes to `/platform`, registration still enters `/systems/{systemId}/admin`, password recovery exposes a return-to-login action, platform admin and system admin render as standalone admin shells, touched entry/platform sources are mojibake-free, and `npm typecheck/build` passed with asset `/assets/index-QDWgcnIm.js`. The release package was rebuilt and restarted at `http://127.0.0.1:18131`; `verify-release` passed with deployed assets `/assets/index-DzYIVez0.css` and `/assets/index-QDWgcnIm.js`, database/schema/Redis `UP`, and admin login success. R76 did not replace operations work; R75 remained the next step at that point. User signoff remains explicitly false.

R75 is accepted as operations/logs/release residual engineering evidence because it proved the deployed release, packaged server commands, operations task ids, trace/audit log readback, normal-member forbidden-operation failure logs, and browser-visible residual state markers for operations action/result/task/dry-run/rollback/deployment/cache/log surfaces. R75 ran fresh R66 child evidence, release verification, and deployed browser checks on desktop/mobile with result count `6`, overflow `0`, and blockers `0`. Active next work is R77 final requirement candidate refresh after residual closure. User signoff remains explicitly false.

R77 is accepted as diagnostic engineering evidence because it refreshed the final candidate state after R70-R76: framework audit passed, static usability passed with blockers `0` and warnings `0`, requirement coverage had ledger rows `45`, missing `0`, notClosed `45`, promotedToProven `0`, and all R70-R76 residual evidence remained PASS without user signoff. The next concrete gap is R78/FRC-1 product surface human acceptance residual closure for `REQ-5.8`, `REQ-6.1`, `REQ-6.3`, `REQ-6.8`, and `REQ-9`. User signoff remains explicitly false.

R78 is accepted as product-surface engineering evidence only. On 2026-07-06 the full R78 wrapper passed on deployed `http://127.0.0.1:18131`: release verification reported database/schema/Redis `UP`, static usability had blockers `0` and warnings `0`, framework audit passed, fresh R68 page/page-designer/runtime evidence passed with browser overflow/blockers `0`, fresh R75 operations/logs/release residual evidence passed with browser overflow/blockers `0`, and fresh R77 kept final coverage honest with notClosed `45` and promotedToProven `0`. This evidence improves confidence in the product surface, but it does not promote requirement rows to final `PROVEN` and does not replace user verification. User signoff remains explicitly false.

R79 is accepted as final handoff engineering evidence only. It makes the continuation path explicit: current architecture, target, developed evidence, user verification script, and future change rules are now readable from disk in `docs/recovery/continuation-implementation-guide.md`. R79 PASS ran framework/static/coverage audits, confirmed R78 remains engineering evidence only, kept coverage honest with missing `0` and notClosed `45`, and kept `gates.user_script_passed=false`. It does not replace user verification and does not close final product acceptance.

R80 is accepted as live trial workspace engineering evidence only. It seeded retained runtime and workflow/todo/message data, emitted credentials and routes, and made the deployed product easier for a human reviewer to try directly. R80 PASS keeps coverage rows partial with notClosed `45` and keeps `gates.user_script_passed=false` until the user verifies or signs off.

R81 is accepted as trial login and role-use engineering evidence only. It started from the deployed login page with the retained R80 accounts, verified platform admin, normal runtime, readonly, requester, approver, todo, and message surfaces from the running product, and kept coverage rows partial with `gates.user_script_passed=false`. R82 is now active as visible-copy engineering evidence only: it must clean and prove readable auth, shell, runtime, readonly, workflow, todo, and message copy on the same trial paths without weakening behavior or claiming user signoff.

## 2026-07-08 User Feedback: Platform Flow/Application Boundary Reopened

The final usable-system gate remains reopened. The latest user feedback reports two product-level blockers: page hierarchy/copy/layout are still confusing, and the platform Application page was misunderstood as a system-entry page. This contradicts `temp_flow.md` and `temp_flow_persion.html`, where platform Flow and platform Application are independent platform modules, while system entry belongs to the system-switch workflow and must create `SystemSwitchContext`.

Immediate correction: `REC-P0-093 Platform Flow And Application IA Boundary Realignment` is active before continuing feature-slice work. The architecture itself remains viable, but the active gate is strengthened so future work must preserve platform/system separation, application/system-entry separation, and human-readable page hierarchy before claiming engineering progress. `gates.user_script_passed` remains false.
