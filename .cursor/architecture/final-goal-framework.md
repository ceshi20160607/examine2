# Final Goal Engineering Framework

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
  -> role journeys
  -> journey gates
  -> business task cards
  -> frontend/backend/data/permission/state contracts
  -> implementation
  -> deterministic evidence
  -> user signoff
```

Do not start from API modules, page lists, generated entities, or role skill ownership. Those are implementation tools, not the goal.

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
- The result survives reload, re-login, or restart where the journey depends on persistence.
- The evidence is linked from the final goal ledger or journey gate.

Missing any item means the task is still open.

## Evidence strength

Prefer evidence in this order:

1. User signoff after using the deployed system.
2. Deployed browser journey plus API readback and permission negatives.
3. Release package script with health, asset, login, restart, and cleanup checks.
4. Focused browser smoke for one journey.
5. API smoke with real persistence.
6. Static checks, type checks, unit tests, and build logs.

Lower-strength evidence can support a task, but cannot replace the higher-level journey evidence when the final goal is user-facing.

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
