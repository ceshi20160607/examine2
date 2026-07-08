# Framework V7 Human-Usable System Upgrade

Date: 2026-07-01 Asia/Shanghai

## Why The Previous Framework Still Missed

The repeated misses were not caused by one missing page or one weak smoke script. The framework still allowed engineering progress to be measured below the user's actual target.

Root causes:

- Slice completion replaced system completion: R-batches proved local mechanics, but the final target is a coherent system that people can use.
- Implementation surfaces replaced user jobs: pages, APIs, generated CRUD, and role ownership were accepted too early, before the role could finish a real job from login to durable result.
- Screenshots became too strong: screenshots proved visual rendering, but they did not prove requirement coverage, data persistence, permission correctness, workflow closure, or usefulness.
- Feedback did not harden the next gate enough: after the user said the deployed product was still confusing, the next task sometimes continued as another feature slice instead of a human journey acceptance gate.
- Asking leaked responsibility: ordinary engineering choices were treated like product decisions, which slowed execution and hid the framework's responsibility to infer and implement from the approved requirement, prototype, ledgers, and source.

## V7 Correction

The active framework now treats human usability as a hard gate.

Any user-facing recovery task must prove:

- real entry from deployed login, system switch, platform workspace, or system shell
- correct role shell with no mixed platform/system/admin/runtime actions
- one primary task surface at a time; no stacked list/board/calendar/detail/config panels as competing primary views
- specific labels, tips, disabled reasons, validation messages, empty states, and success/failure feedback
- real backend data write/readback and persistence after reload, relogin, or restart where applicable
- permission positive and negative cases using the same role context shape used by the frontend
- generated CRUD and generated APIs are only plumbing, not completion
- screenshots are visual-only evidence after the requirement contract exists
- user signoff remains separate from engineering evidence

## Current Execution Decision

R56/FRC-5 operations robustness delivery passed as engineering evidence, but it does not close final usability.

The next task is:

- `REC-P0-057 FRC-6 Human Acceptance Pass Fresh Closure`

It must start from `REQ-2.1`, `REQ-4.1`, `REQ-4.2`, and `REQ-6.2`, then prove deployed human role journeys instead of another narrow implementation slice.

## No-Prompt Execution Rule

The agent should not ask the user for ordinary implementation choices. It should infer from:

- `docs/user_requirement.md`
- `docs/design/prototype-brief.md`
- `docs/design/prototypes/index.html`
- `docs/framework/final-requirement-coverage-ledger.md`
- `docs/evidence/final-requirement-gap-report.md`
- current source and deployed behavior

Ask only when the decision changes user-facing product behavior and cannot be inferred from those sources. Tool permission prompts are not product approval. `gates.user_script_passed=true` still requires explicit user verification/signoff.

## Cleanup Rule

Artifacts that make false completion easier should be removed or downgraded from active execution:

- screenshot-only acceptance claims
- stale prototype/demo behavior in production paths
- duplicate route/task ledgers that contradict the active next-execution ledger
- generic "done/complete" language for engineering evidence
- generated CRUD claims used as user-facing capability

Historical evidence can remain under `docs/evidence/`, but active work must follow the current framework, task card, requirement coverage ledger, and audit script.
