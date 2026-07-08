# Final Goal Engineering Framework

Version: v8-flow-blueprint-rebuild-contract

This file defines the framework for turning the current broad user goal into a usable system.

Current priority is this project only. Cross-project framework extraction is deferred until the product is complete and accepted.

## Why this exists

The previous process failed because local engineering outputs were allowed to stand in for the final user outcome.

These are evidence, not completion:

- a page exists
- an API returns 200
- generated CRUD exists
- build or package passes
- a recovery batch passes
- one happy-path browser smoke passes

Completion means the target role can finish the intended real-world job in the running system, with data persisted, permissions enforced, failure states handled, and the result visible after reload or restart.

## V5 Requirement-First Lock

The framework must not confirm product scope from screenshots.

Screenshots are useful only for visual evidence: hierarchy, density, clipping, overflow, copy visibility, role-specific surface, and whether the browser rendered the expected state. They cannot define whether a requirement exists, whether a feature is complete, or whether a workflow solves the user's problem.

Every coding task must start from a requirement confirmation contract before implementation:

- requirement source: exact section or row from `docs/user_requirement.md`, approved prototype brief, API contract, or a user answer written back to project files
- target role and real entry point
- user job to complete, not the page or API to create
- required data objects and persistence/readback point
- permission positive and negative cases
- UI states, copy/tips, validation, errors, async states, and empty states
- generated plumbing versus coded business behavior
- deterministic acceptance assertions
- screenshot evidence boundary: what the screenshot may prove and what it may not prove

If this contract is missing or vague, coding stops. The next action is to update the task card, requirement ledger, or journey gate, not to inspect more screenshots or patch a convenient component.

## V6 Usable-System Operating Model

The current recovery problem is no longer only a missing feature. The framework itself must prevent the same false-completion loop:

- historical engineering PASS does not survive new user feedback that the deployed product is still unusable
- screenshots are visual evidence only, never requirement confirmation
- a task is not ready for coding until it names the requirement row, the role journey, the exact user result, and the cross-layer proof
- generated interfaces are plumbing only and must not be counted as user-facing capability
- implementation tasks are small business outcomes; pages, APIs, files, modules, or agent skill ownership are only implementation structure
- every accepted slice must update the next unfinished requirement and journey, so the work cannot drift into an old R-batch sequence

When the user reports "still unusable", "still confused", "pages are stacked", "prototype mismatch", or "not the final goal", the framework must immediately reopen final usable-system acceptance. Existing scripts and screenshots remain evidence inputs, but all affected journey gates become at most `PARTIAL_PASS_ENGINEERING` until a fresh deployed role-journey audit and the user's own verification close them again.

Coding must now follow this executable split:

```text
requirement row
  -> role journey
  -> user job and durable result
  -> generated plumbing needed
  -> coded business behavior needed
  -> frontend binding and states
  -> backend/data/permission/readback
  -> deployed evidence
  -> updated ledger
```

The only acceptable reason to ask the user is a product decision that cannot be inferred from `docs/user_requirement.md`, the approved prototype, current ledgers, or source behavior. A tool permission prompt is not product confirmation. Ordinary implementation choices are made by the agent and written back to the framework artifacts.

## V7 Human-Usable System Gate

The framework now treats "a person can use the system" as a first-class gate, not as a late subjective review. The product is not considered close to the final goal until a deployed role journey proves all of these together:

- the user starts from a real entry point such as login, system switch, platform workspace, or system shell
- the role sees the correct shell and cannot see confusing or unauthorized actions from another shell
- the page presents one primary task surface at a time; lists, boards, calendars, detail panels, import/export, and configuration panels must not be stacked as competing primary views
- labels, tips, disabled reasons, validation messages, empty states, and success/failure messages are specific and non-contradictory
- the action writes to or reads from the real backend and survives reload, relogin, or restart when persistence matters
- permission positive and negative cases are both proven from the same role context shape that the frontend uses
- generated APIs, CRUD pages, and scaffolded routes are counted only as plumbing; coded business behavior and bound frontend states decide completion
- screenshots can show visual quality only after the above requirement contract exists; they are never the source of truth for what should be built

The active next task must be selected from the first still-unfinished human-usable gap in `docs/evidence/final-requirement-gap-report.md` and `docs/framework/next-execution-ledger.md`. If the user reports that the deployed product is still confusing, crowded, mismatched with the prototype, or not useful, the framework must prefer a human-journey task over another feature-slice aggregation.

### No-Prompt Execution Boundary

The user has authorized the agent to execute ordinary engineering choices without step-by-step confirmation. The framework must distinguish:

- product decisions: ask only when the answer changes user-facing product behavior and cannot be inferred from requirements, prototype, ledgers, or source
- engineering decisions: decide, implement, verify, and write the reasoning back to project files
- environment/tool permissions: not product approval and not a reason to pause the recovery plan when the execution environment already permits the command

`gates.user_script_passed` is still special: only explicit user verification or signoff can set it to `true`.

### Useless Artifact Cleanup Rule

If an artifact encourages false completion, duplicate entry points, stale prototype behavior, demo data, screenshot-only acceptance, or generic "done" language, it should be removed or downgraded instead of preserved for history. Historical evidence may remain under `docs/evidence/`, but active execution must read the current ledger, task card, and framework audit first.

## V8 Flow Blueprint Rebuild Contract

The user's 2026-07-02 correction changes the active framework again: the project must not continue as unlimited local fixes. Before more coding, the system flow must be explicit enough that each small task composes into the final usable product.

The active flow source is:

- `docs/framework/final-system-flow-blueprint.md`

This file is now part of the framework contract. Future development must start from flow ids such as `A1 Login`, `P1 Platform Dashboard`, `S1 Enter System`, `C1 First-Use Configuration Guide`, `C2 No-Code Module Configuration`, `B2 Runtime Module Navigation`, `E1 OpenAPI Caller`, or `AI2 System Agent`.

V8 rules:

- The first decision in the frontend is always auth state: no token or invalid token renders login/register/password recovery only; it must not render platform/system pages.
- After login, routing is decided by platform role, system switch context, tenant context, system member mapping, and requested route.
- The four shells remain mandatory: platform workspace, platform admin, system business shell, and system admin shell.
- A coding task cannot start from a page name alone. It must name the flow id, target role, entry URL/navigation action, user job, data/readback, permissions, states/copy, and deterministic evidence.
- The framework must prefer flow reconstruction over another local page patch when the user reports that the product is still not a coherent system.
- Old R-series evidence remains useful, but it is subordinate to the flow blueprint when choosing the next work.

The current immediate task is `REC-P0-060 Flow Blueprint And Rebuild Contract Lock`. It is a framework task, not a final product completion claim.

## Mandatory artifacts

Every major objective must create these artifacts before coding starts:

1. Final goal ledger
   - use `.cursor/templates/final-goal-ledger.md`
   - states the final user outcome, roles, non-completion cases, journey gates, evidence matrix, and signoff boundary
2. Journey gates
   - use `.cursor/templates/journey-gate.md`
   - each gate is one role-level business journey from entry to durable result
3. Task cards
   - use `.cursor/templates/task.md`
   - each task must map to one journey gate and one business outcome
4. Evidence scripts
   - browser/API/database/release scripts that prove the journey, not just a component
5. User signoff record
   - user approval is a separate final gate and cannot be set by an agent

## Decomposition rule

Work must be decomposed in this order:

```text
final user goal
  -> system flow blueprint
  -> role journeys
  -> journey gates
  -> requirement confirmation contract
  -> business task cards
  -> frontend/backend/data/permission/state contracts
  -> implementation
  -> deterministic evidence
  -> user signoff
```

Do not start from API modules, page lists, generated entities, or role skill ownership. Those are implementation tools, not the goal.

Do not start from a route or screen before identifying the flow id in `docs/framework/final-system-flow-blueprint.md`.

Do not start from screenshots. Use screenshots after the requirement contract exists, and only to test whether the implemented surface visibly satisfies the contracted user journey.

## Task closure rule

A task can be accepted only when all of these are true:

- The role and business action are explicit.
- The prototype or product requirement reference is named.
- The frontend entry is real and not visually crowded or mixed with unrelated work.
- The backend behavior writes to and reads from the real store.
- Generated code is identified as plumbing only; coded business behavior is named separately.
- Permission positive and negative cases are checked.
- Empty, loading, disabled, validation, error, and async states are handled where relevant.
- Browser/API evidence starts from a realistic entry such as login, system switch, or deployed URL.
- Visual screenshots are scoped to the requirement contract and cannot replace API/readback/permission/state evidence.
- The result survives reload, re-login, or restart where the journey depends on persistence.
- The evidence is linked from the final goal ledger or journey gate.

Missing any item means the task is still open.

## V4 execution lock

The framework is not allowed to rely on guidance text alone. Every new recovery or build turn must keep these machine-checkable locks current:

1. `docs/framework/final-requirement-coverage-ledger.md`
   - every major requirement row is `PROVEN`, `PARTIAL`, `OPEN`, or `USER_EXCLUDED`
   - `PARTIAL` and `OPEN` rows must keep a concrete gap/next-action
2. `docs/framework/next-execution-ledger.md`
   - unfinished journeys are visible
   - the next executable work is named
   - engineering evidence and user signoff are not merged
3. `.cursor/session/state.json`
   - `build_plan.nextTasks` is not empty while requirement rows remain unfinished
   - `gates.user_script_passed` stays `false` unless the user signs off
4. `docs/recovery/p0-task-cards.md`
   - task cards keep role, entry, business outcome, frontend, backend, data, permission, states, non-completion cases, and evidence path fields
5. `scripts/final-goal-framework-audit.ps1`
   - must be run before claiming framework health after a recovery framework change
   - a PASS is only framework-health evidence, not product completion
6. Requirement-first contract lock
   - task templates and active task cards must include requirement source, role entry, user result, acceptance assertions, and screenshot evidence boundary
   - browser screenshots may support visual quality but cannot be the only proof for function, data, permission, workflow, or requirement coverage
   - `scripts/final-goal-framework-audit.ps1` must check the currently active `build_plan.nextTasks` task card contract, not only the generic task template
   - the active task's requirement rows must exist in `docs/framework/final-requirement-coverage-ledger.md`, remain unfinished, keep concrete gaps, and match `docs/evidence/final-requirement-gap-report.md` plus `docs/framework/next-execution-ledger.md`
7. Flow blueprint lock
   - `docs/framework/final-system-flow-blueprint.md` must exist
   - active framework/rebuild tasks must reference the blueprint
   - active coding tasks must name flow ids before implementation

If these locks conflict, the most conservative status wins. A task or batch can be accepted as evidence while the final product remains incomplete.

## Next-task selection rule

When the requirement ledger still has `PARTIAL` or `OPEN` rows, the next implementation task must be selected by this order:

1. rows in `docs/evidence/final-requirement-gap-report.md` under the earliest recommended FRC batch
2. journey rows in `docs/framework/next-execution-ledger.md` with `OPEN`, `PARTIAL_PASS_ENGINEERING`, or `FAIL_EXPECTED`
3. user-reported concrete route defects, if they map to an unfinished requirement row
4. only then, local code risks found during implementation

Do not start from a convenient controller, UI panel, generated entity, or old R-batch sequence if it does not close a named requirement gap.

## Completion claim lock

Any response, document, state update, or evidence summary that implies final completion is invalid unless all of these are true:

- `scripts/final-requirement-coverage-audit.ps1` passes without `-NoFailExit`
- `scripts/final-goal-framework-audit.ps1` passes
- all required deployed role-journey audits pass
- release verification passes from the packaged deployment
- the user has explicitly verified or signed off, and only then `gates.user_script_passed=true`

If one of these is missing, the correct wording is "engineering evidence for this slice", "first-loop evidence", or "partial coverage", never "final complete".

## Evidence strength

Prefer evidence in this order:

1. User signoff after using the deployed system.
2. Deployed browser journey plus API readback and permission negatives.
3. Release package script with health, asset, login, restart, and cleanup checks.
4. Focused browser smoke for one journey.
5. API smoke with real persistence.
6. Static checks, type checks, unit tests, and build logs.

Lower-strength evidence can support a task, but cannot replace the higher-level journey evidence when the final goal is user-facing.

Screenshots sit inside evidence levels 2 and 4 only as visual checks. They must be paired with requirement assertions and readback evidence whenever the claim is functional.

## Generated versus coded boundary

Generated interfaces are allowed only as infrastructure. They do not close product behavior.

For each task, the task card must state:

- generated API/table/entity scope
- coded business service or workflow scope
- frontend surface that uses the coded behavior
- states and permissions not covered by generation
- evidence proving the coded behavior

If this boundary is unclear, coding stops until the task card is corrected.

## User consultation boundary

Ask the user only when the decision changes the final product goal, user-facing workflow, or subjective usability target.

Do not ask for ordinary implementation details that can be inferred from the ledger, prototype, API contract, source code, or task cards.

When asking, write the answer back into the ledger, task card, product rules, or session state. Chat-only answers are not binding for later sessions.

## Final completion boundary

Engineering acceptance and user signoff are separate:

- `engineering_final_gate=PASS` means deterministic evidence currently supports the final goal.
- `gates.user_script_passed=true` means the user has verified or signed off.

Agents must never set `gates.user_script_passed=true` from engineering evidence alone.

## Current product usability gate

For this project, final completion also requires:

- requirements in `docs/user_requirement.md` are implemented or explicitly marked out of current scope by the user
- pages are task-oriented and visually clean, not stacked or mixed
- tips, labels, empty states, disabled reasons, validation errors, and success/failure messages are clear and not contradictory
- production pages do not expose fake success toasts, generic placeholders, stale demo data, or confusing technical wording
- normal users, system administrators, platform administrators, external integrators, and operators can each complete their journeys from real entry points

Future framework extraction is not part of the current execution target.
