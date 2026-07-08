# Current Final System Engineering

Time: 2026-06-30 Asia/Shanghai

## Purpose

This is the current execution framework for finishing this project as a usable product.

Do not use this phase to organize a future reusable framework. Future extraction is deferred until this project is actually complete and accepted.

## Final Target

The target is a system that real people can use comfortably:

- users can complete the requirements in `docs/user_requirement.md`
- pages are visually clean, task-oriented, and not stacked together
- labels, tips, disabled reasons, validation errors, and result messages are unambiguous
- no fake success toast, generic placeholder, stale demo data, or confusing technical wording is exposed as production behavior
- administrators can customize systems through no-code configuration
- normal members can use the customized systems for daily work
- workflows, external API service, AI features, tasks, messages, logs, permissions, and operations form one coherent product

## Why Previous Work Failed

The previous framework did not force every coding task to inherit the final product goal.

Repeated failure causes:

- final goal was not an executable ledger
- prototype was copied as a page shell instead of used as a functional contract
- task cards focused on files, APIs, and batches instead of role journeys
- generated CRUD was not strictly separated from coded business behavior
- frontend, backend, data, permission, state, UX text, and operations were accepted separately
- scripts proved local slices but not whether a person could use the whole system comfortably
- engineering PASS was linguistically blurred with user acceptance

Direct user-facing summary:

- The work repeatedly optimized for "implemented artifacts" instead of "a usable product". A route, component, controller, generated table, or passing smoke script was allowed to look like progress even when the user journey was still broken.
- The prototype was treated too much like a visual page to copy, not as a contract for entry, task purpose, data source, permissions, states, backend readback, and role-specific behavior.
- Task decomposition was too engineering-shaped. It split work by pages, APIs, files, and agent roles, but did not always split it into small business outcomes where one role can finish one real action end to end.
- Generated interfaces and coded product behavior were blurred. Generated CRUD can create infrastructure, but it does not by itself make page design, workflow, permissions, runtime records, messages, imports, exports, or operations usable.
- Acceptance evidence was too local. Many scripts proved one slice in isolation, while the final target needs full deployed role journeys: login, switch context, configure, publish, use as normal member, read back persisted data, enforce permissions, handle failure states, and survive reload or restart.
- User acceptance was not kept visually and procedurally separate enough from engineering acceptance. A batch can be useful evidence, but it cannot mean "the final goal is complete" until the running system is actually accepted by the user.
- R42 exposed the same class of failure in a concrete place: the backend schema and direct API correctly denied normal-member writes, but the browser runtime still opened a writable create form. This proves that "API correct" and "UI route exists" are not sufficient; the frontend must render from the same permission contract and the deployed browser path must be part of acceptance.
- R43 exposed another role-boundary failure: the normal-member platform workbench hid the platform-admin button, but the first-screen copy still described the platform-admin entry and the create-system action was still visible. This proves that role-specific usability must check visible text and available primary actions, not only route permission and sidebar visibility.
- R44 exposed a cross-layer permission identity mismatch: system switch context exposed role codes while module group visibility persisted role IDs. Runtime navigation therefore could incorrectly show "no available modules" even after permission configuration passed API readback. Effective permission work must verify that the same role identity shape drives admin configuration, preview, runtime navigation, field/action rendering, and backend negatives.
- R45 proves the process can now close a cross-layer configuration slice with backend readback, deployed browser evidence, permission negatives, and cleanup. It also confirms why the next slice must return to page/home/runtime usability: field and dictionary depth alone does not make the product feel coherent to a person using it.
- R46 proves page/home/component surfaces can be configured, published, read back, rendered for admin and normal member, and kept free of obvious admin/runtime marker mixing in the audited routes. It still does not prove the product is ready for daily use: the next gap is normal-member runtime depth, including records, files, import/export task states, empty/error/no-permission behavior, mobile usability, and user acceptance.
- R47 proves a deployed normal-member runtime path can cover empty list, create/edit/detail readback, real file binding, import/export task state, hidden-field non-leakage, permission negatives, desktop/mobile containment, and cleanup. It also confirms why "record CRUD passed" is still too small as a completion standard: workflow, approval, todo/message, flow surfaces, integrations, operations, broader accessibility, full requirement coverage, and user acceptance remain separate unfinished journeys.
- The latest framework issue is that screenshots were carrying too much meaning. Screenshots can show whether a rendered page is crowded, clipped, visually mixed, or using bad copy; they cannot confirm requirement scope, feature completeness, data persistence, permission correctness, workflow closure, or whether the result solves the user's job. Those must come from requirement rows, role journeys, task contracts, and executable assertions.
- The corrective path is not to ask the user one more broad question or keep patching random pages. Each next task must start from the unfinished requirement row and role journey, state the exact non-completion cases, implement the frontend/backend/data/permission/state loop together, and prove it on the deployed release.

## Current Execution Rule

Every new change must follow this order:

```text
final product goal
  -> role journey
  -> requirement confirmation contract
  -> exact usability or function gap
  -> task card
  -> generated-vs-coded boundary
  -> implementation
  -> deployed browser/API/readback evidence
  -> updated final ledger
```

Do not code from vague feedback. Convert the feedback into a role journey gap first.

Do not code from screenshots. Convert the requirement into a contract first, then use screenshots only to verify the visible surface against that contract.

Framework audit must verify the active task, not only the framework text. When `build_plan.nextTasks` names an executable FRC task, `scripts/final-goal-framework-audit.ps1` must find the linked task card and check its requirement confirmation contract, planned evidence paths, and requirement-row linkage to the coverage ledger, gap report, and next-execution ledger before coding.

## V6 Framework Upgrade: Stop False Completion

The user feedback on 2026-07-01 proves that the prior framework still let local evidence outrun the final product target.

The framework is upgraded with these rules:

- User feedback that the deployed product is still unusable reopens final usable-system acceptance immediately.
- Old `PASS` journey rows become engineering evidence only; they do not remain final acceptance.
- Screenshots are never a requirement source. They prove only visual rendering, layout density, clipping, overflow, and visible copy after the requirement contract already exists.
- Coding work must be selected from an unfinished requirement row and role journey, not from an old batch sequence or a convenient implementation area.
- A task must explicitly split generated plumbing from coded business behavior before coding starts.
- A task can be accepted only when frontend, backend, data, permission, state, readback, release evidence, and cleanup all support the same user job.
- Asking the user is reserved for unresolved product decisions. Implementation choices must be made by the agent from the ledger and written back to project files.

The next work is framework remediation plus fresh deployed role-journey audit preparation, not another isolated UI patch.

## Quality Gates

Each journey must pass these gates before it can be called usable:

| Gate | Required result |
|---|---|
| Function | The role can finish the business action from a real entry point. |
| UX structure | The page has one clear purpose, clean hierarchy, no mixed unrelated panels, and no stacked task surfaces. |
| Text and tips | Labels, placeholders, disabled reasons, validation messages, success/failure messages, and empty states are understandable and not contradictory. |
| Real data | Production routes do not use mock, demo, stale fixture, or local-only data. |
| Integration | Frontend calls the real backend behavior and reads the persisted result back. |
| Permission | Allowed roles can act; forbidden roles are blocked by backend and reflected in UI. |
| State | Loading, empty, disabled, validation, backend failure, async task, and retry states are visible where relevant. |
| Operations | Critical actions expose traceId, taskId, auditLogId, result file, or equivalent operational evidence. |
| Mobile/desktop | The path is usable on desktop and mobile without clipped or overlapping text. |
| Release | The deployed package, not only dev mode, proves the behavior. |
| Requirement contract | Requirement source, role entry, data, permission, state, copy, generated-vs-coded boundary, and acceptance assertions are explicit before implementation. |
| Screenshot boundary | Screenshots prove only visual rendering and copy visibility; functional claims require API/readback/permission/state evidence. |

Missing any applicable gate means the journey remains open.

## Static Usability Gate

Before claiming a frontend journey is usable, run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/final-usability-static-audit.ps1
```

This gate blocks obvious production UX failures:

- mojibake or replacement characters
- generic success wording that can hide unfinished behavior
- `alert`, `prompt`, or `confirm` browser dialogs
- default/generic drawer closure for P0 behavior
- visible `待接入`, `未实现`, `占位`, or coming-soon states on production routes
- demo/mock/fixture wording outside explicit test contexts

The static gate is not enough by itself; it only prevents obvious failures before browser journey audit.

## Requirement Coverage Gate

Before final completion can be claimed, run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/final-requirement-coverage-audit.ps1
```

This gate checks `docs/framework/final-requirement-coverage-ledger.md` against the major sections of `docs/user_requirement.md`.

It must fail while any requirement row is `PARTIAL` or `OPEN`. A passing build, passing R-batch, or passing journey script cannot override this gate.

After running the audit, generate the execution report:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/final-requirement-gap-report.ps1
```

Use `docs/framework/final-requirement-closure-plan.md` for the batch order.

## Current Work Mode

The current mode is not "continue R batches", not "patch what a screenshot shows", and not "keep fixing local pages until the product happens to feel better".

The current mode is:

1. Reopen final usable-system acceptance from the user's deployed-use feedback.
2. Upgrade framework locks so historical PASS and screenshots cannot masquerade as final completion.
3. Run or prepare a fresh role-journey audit against the deployed system.
4. Run the requirement coverage gate against `docs/user_requirement.md`.
5. Record exact failures in `docs/framework/next-execution-ledger.md` and `docs/framework/final-requirement-coverage-ledger.md`.
6. Add task cards only for concrete requirement-row and role-journey gaps.
7. Implement those gaps in small business outcomes.
8. Re-run deployed evidence and requirement coverage.
9. Keep `gates.user_script_passed=false` until user verification or signoff.

## V8 Flow Blueprint Mode

The user's 2026-07-02 correction adds a stricter execution mode:

- first rebuild the system flow contract
- then split coding tasks from that flow contract
- then implement the smallest task whose completion composes into the full product

Active flow source:

- `docs/framework/final-system-flow-blueprint.md`

Why this is now required:

- the previous process still allowed repeated local fixes after the user reported the product was not a coherent system
- route smoke, page density, and API evidence can improve slices without proving the product's real entry sequence
- the system must first define what happens for no token, invalid token, registration, password recovery, platform admin, platform member, system admin, normal member, SSO/no-member, external API, AI, tasks, messages, logs, and operations

Current accepted flow tasks:

- `REC-P0-060 Flow Blueprint And Rebuild Contract Lock`
- `REC-P0-061 Auth Entry Guard And Registration Landing Closure`
- `REC-P0-062 Platform And System Shell Landing Flow Closure`
- `REC-P0-059 FRC-2/FRC-6 Admin Configuration Human First-Use Closure`
- `REC-P0-063 B1/B2 Configured Runtime First-Use And Daily Business Closure`
- `REC-P0-064 C4/B4/B5 Workflow Todo Message First-Use And Closure`
- `REC-P0-065 E1/AI2 OpenAPI Assistant External-Service First-Use Closure`
- `REC-P0-066 O1/O2/O3 Operations Logs Release Maintenance First-Use Closure`
- `REC-P0-067 Final Role Journey And Requirement Acceptance Candidate Refresh`
- `REC-P0-068 Page Visual Designer Fresh Evidence Closure`
- `REC-P0-069 Requirement Evidence Promotion And Residual Gap Decision`
- `REC-P0-071 No-Code Frontend Binding And Hierarchy Residual Closure`
- `REC-P0-072 Runtime Daily-Use File Import Export Error-State Residual Closure`
- `REC-P0-073 Workflow Todo Message Integration Error-State Residual Closure`
- `REC-P0-074 OpenAPI AI External-Service Error-State Residual Closure`
- `REC-P0-076 Auth Shell Entry Role Flow Residual`
- `REC-P0-075 Operations Logs Release Maintenance Error-State Residual Closure`
- `REC-P0-077 Final Requirement Candidate Refresh After Residual Closure`

Current immediate coding task:

- `REC-P0-078 FRC-1 Product Surface Human Acceptance Residual Closure`

Next coding work must cite flow ids from the blueprint. For example, an auth fix must cite `A1-A4`, a shell fix must cite `P1/S1`, a no-code configuration fix must cite `C1-C3`, the accepted configured-runtime handoff cites `B1/B2`, the accepted workflow/todo/message closure cites `C4/B4/B5`, the accepted external-service/AI closure cites `E1/AI2`, the accepted operations/log/release closure cites `O1/O2/O3`, the accepted final candidate refresh cites all primary flow groups, R68 cites `C1/C2/C3/B1/B2`, R69 cites all primary flow groups because it decides requirement promotion and residual gaps across all 45 rows, R70 cites `C1/C2/C3/B1/B2`, R71 cites `C1/C2/C3/B1/B2`, R72 cites `B1/B2/B3/B4/B5/C1/C2/C3`, R73 cites `C4/B4/B5/B1/B2`, R74 cites `E1/E2/AI1/AI2/AI3/O2`, R76 cites `A1/A2/A3/A4/P1/S1/C1`, R75 cites `O1/O2/O3/O4`, R77 cites all primary flow groups because it refreshes the final candidate matrix after residual closure, and R78 cites `A1-A4/P1-P2/S1-S2/C1-C3/B1-B2/AI1-AI3/O1-O4`.

This is still engineering framework work. It does not set `gates.user_script_passed=true`.

## Latest Recovery Lessons

- R48/FRC-4B proved workflow closure only after the browser audit stopped treating Chinese text matching as functional proof. Future deployed browser checks should use structural markers, data/readback, role state, permission negatives, and screenshots for visual evidence; localized copy remains important, but it must not be the only wait condition for business completion.
- R48 is accepted as engineering evidence for requester -> approver workflow, todo/message, idempotent approval, and terminal state. It does not close OpenAPI, assistant, operations, broader workflow variants, full requirement coverage, or user signoff.
- R49/FRC-4C proved OpenAPI and assistant integration only after the browser audit used a stable structural selector for the assistant entry. Chinese copy matching in generated browser scripts can be corrupted by command/file encoding, so localized text can be asserted as visible copy but must not be the only way to perform the action. R49 is accepted as engineering evidence for scoped OpenAPI, SecretRef rotation, success/failure call logs, platform/system Agent boundaries, assistant confirmation states, permission negatives, and browser containment. It does not close operations, broader AI/OpenAPI variants, full requirement coverage, or user signoff.
- R50/FRC-5B proves the log/operations/release slice only after logs were changed from static sample rows to persisted audit rows tied to real request traceIds. Static logs and visual log pages are not usable operations evidence. Audit/log acceptance must include request action, traceId, auditLogId/detail readback, success and forbidden-failure rows, role negatives, deployed browser containment, and release verification.
- 2026-07-01 user feedback reopens final usable-system acceptance. The framework must now treat all old journey PASS rows as engineering evidence until fresh requirement-driven role journeys and user verification close them again.
- R51 proves the framework can now produce an honest next-work decision: audit execution PASS, productStatus FAIL_EXPECTED, 45 requirement rows still partial, 8 final journey rows still engineering partial, and the first implementation batch is FRC-1 Missing Product Surfaces. The next task is REC-P0-052 and must close requirement rows REQ-5.8, REQ-6.1, REQ-6.3, REQ-6.8, and REQ-9 through deployed role evidence, not screenshots alone.
- R62 proves browser inspection must be part of shell acceptance: script/release checks passed, but browser reload exposed a stale-token/empty-account path rendering the platform shell as "未登录". The fix now requires initialized account state before any business shell can render. Future shell tasks must include reload/stale-session evidence, not only happy-path login evidence.

- R59 proves admin first-use configuration must be accepted as a user task surface, not a stack of configuration panels. The next flow gap is the handoff into B1/B2: the configured/published module must become normal-member runtime business work with data readback and permission states.
- R63 proves the configured/published module can become normal-member runtime business work only when the deployed browser creates, edits, and reads back data through the same published handoff.
- R64 proves workflow configuration, todo, and message close only when requester and approver complete the same deployed browser/API journey with terminal readback and permission negatives.
- R65 proves OpenAPI external service and AI assistant close only when scoped credentials, SecretRef/logs, human confirmations, permission negatives, and browser-visible surfaces are all asserted together.
- R66 proves operations, logs, release health, and maintenance controls close only when operator-readable browser evidence is tied to release verification, trace/audit ids, task readback, static audit, and permission negatives. The next flow gap is R67 final candidate refresh across all primary role journeys and all 45 requirement rows.
- R67 proves the framework can now produce an honest final-candidate matrix: 45 requirement rows present, 42 rows with fresh evidence, 0 rows promoted to PROVEN, and user signoff still false. It also identifies the next concrete gap as `REQ-5.8`, `REQ-6.1`, and `REQ-6.8`, so R68 must close pages, overall visual style, and page designer through a deployed page-designer-to-runtime journey.
- R68 proves the page/visual/page-designer fresh-evidence gap closes only when fresh R42 component evidence and fresh R46 page publish/runtime evidence both pass on the deployed release, with browser overflow/blockers `0`, hidden field/component pruning, and normal-member write denials. The next flow gap is R69: promote or keep partial every requirement row by evidence, then name the next residual coding batch without user prompting.
- R69 proves the framework can now make a 45-row requirement decision without pretending to be user signoff: all rows have fresh evidence after R68, 0 rows are promoted, 45 rows remain partial, and the next residual implementation batch is R70 no-code configuration depth.
- R70 proves no-code configuration residual depth now has a backend runtime guard: navigation metadata carries real group role ids and runtime visibility, normal-member direct access to hidden/draft/disabled/draft-group modules returns `403`, R44/R45 fresh evidence still passes, and browser aggregate overflow/blockers are `0`. The next gap is R71: frontend-visible binding/state selectors and the honest module-group hierarchy boundary.
- R71 proves no-code frontend binding and hierarchy boundary evidence only after the deployed browser can identify group/module/field/member/role ids and states through stable markers, normal-member runtime DOM excludes hidden modules/fields, direct hidden/disabled/create denials stay `403`, and desktop/mobile overflow/blockers are `0`. The next gap is R72: runtime daily-use depth for files, import/export failure states, approval detail context, batch actions, readonly/forbidden permutations, and mobile clarity.
- R72 proves runtime file/import-export/error-state evidence only after the deployed browser/API can read back real attachment files, preview/download/missing/denied states, successful and failed import flows, export result files, batch async task state, hidden-field non-leakage, readonly/admin permission negatives, and desktop/mobile overflow/blockers `0`. The next gap is R73: workflow/todo/message residual depth for assignment, terminal states, duplicate prevention, forbidden actions, message read/archive/filter/page behavior, and mobile clarity.
- R73 proves workflow/todo/message residual evidence only after deployed browser/API evidence covers message read/archive, approval and rejection terminal states, requester/admin denials, duplicate and terminal conflict states, and desktop/mobile overflow/blockers `0`. The next gap is R74: OpenAPI/AI/external-service residual depth for credential rotation, old-secret invalidation, wrong-scope/read-only failures, assistant terminal confirmation behavior, traceable logs, secret non-leakage, and mobile clarity.
- R74 proves OpenAPI/AI external-service residual evidence only after deployed browser/API evidence covers active SecretRef rotation, old-secret invalidation, wrong-secret/read-only failures, assistant terminal and duplicate-confirm behavior, normal-member denials, traceable logs, secret non-leakage, and desktop/mobile overflow/blockers `0`. The next gap is R75: operations/logs/release residual depth for health, release assets, maintenance tasks, denied-operation audit rows, trace/detail readback, and mobile clarity.
- R76 proves the framework must protect the entry and role-routing contract even after earlier shell batches pass. Multi-agent review found that platform-role users could still be redirected into system context too early, stale `/platform/dashboard` could remain as a landing target, and admin pages could still be visually wrapped by workbench shells. The correction is now explicit: bootstrap loads account/profile/switch options but does not auto-switch systems; platform-role landing is `/platform`; registration lands on system admin; password recovery returns to login; and platform/system admin surfaces render as standalone shells. This is engineering evidence only, and it does not replace R75 operations/logs/release residual work.
- R75 proves operations/logs/release residual evidence only after fresh R66 child evidence, release verification, deployed browser operations clicks, task/dry-run/rollback DOM markers, trace/audit readback, and forbidden-operation failure logs all agree. It also exposed why residual scripts must avoid Chinese string literals in browser expressions when PowerShell encoding can corrupt them; use stable DOM markers and source/static copy checks instead. The next gap is R77: refresh the final requirement candidate matrix after R70-R76, keep user signoff false, and name the next concrete gap.
- R77 proves the framework can honestly refresh after residual closure without pretending the product is final: framework/static/release checks passed, R70-R76 evidence was present and PASS, coverage still has missing `0` and notClosed `45`, promotedToProven `0`, and user signoff false. The selected next gap is R78/FRC-1 product surface human acceptance residual closure for pages, visual style, home page, page designer, and advanced capability boundaries.

## Deferred

Future reusable framework extraction is intentionally deferred.

Do not spend current effort organizing cross-project reuse unless it directly helps the current system become usable.
