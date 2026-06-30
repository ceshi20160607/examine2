# Next Execution Ledger

Time: 2026-06-30 Asia/Shanghai

## Objective

Turn the current recovered engineering state into a system the user can actually use.

Future reusable framework extraction is deferred until this product is complete and accepted.

## Current Gate State

| Gate | Status | Meaning |
|---|---|---|
| Engineering recovery R0-R22 | PASS | Area-level recovery evidence exists. |
| Final usable-system audit J0-J7 | PASS | Deterministic engineering audit currently passes. |
| User signoff | OPEN | `gates.user_script_passed=false`; user feedback still reports usability/deployment mismatch. |
| Framework v3 baseline | APPLIED | `.cursor` final-goal framework and templates are updated. |

## Required Next Pass

Run a fresh deployed-use audit. It must start from the running system, not from old evidence summaries.

The audit must evaluate both function and human usability: visual hierarchy, ambiguous tips, misleading disabled states, stale demo text, generic placeholders, and page/task mixing.

| Journey | Role | Audit Question | Status |
|---|---|---|---|
| J1 | new administrator | Can register/create a system and understand next setup actions? | OPEN |
| J2 | system administrator | Can build and publish a usable business app without pages feeling stacked or mixed? | PARTIAL_PASS_ENGINEERING |
| J3 | normal member | Can log in, use authorized business data, and avoid admin surfaces? | OPEN |
| J4 | requester/approver | Can approval, todo, and message close as one workflow? | OPEN |
| J5 | system administrator | Are admin configuration pages task-oriented and not generic shells? | PARTIAL_PASS_ENGINEERING |
| J6 | external integrator | Can OpenAPI/upload/import/export work with safe secrets and logs? | OPEN |
| J7 | operator | Can health, backup, rollback, cache policy, and release scripts be understood and used? | OPEN |
| J8 | all roles | Are visible labels, tips, empty states, disabled reasons, and success/failure messages clear and not misleading? | OPEN |
| J9 | all roles | Are production routes free of fake success toasts, generic placeholders, stale demo/mock data, and obvious mojibake? | OPEN |
| J10 | frontend production source | Does static usability audit pass for obvious corrupted text, fake dialogs, generic closure, and unfinished placeholders? | PASS |
| J11 | full requirement scope | Does every major section in `docs/user_requirement.md` have coverage, and are all rows `PROVEN` or `USER_EXCLUDED`? | FAIL_EXPECTED |

## Task Creation Rule

If any audit row fails:

1. Add or update the corresponding task card.
2. Link it to the journey.
3. Define generated-vs-coded boundary.
4. Implement only that business loop.
5. Prove it with deployed browser/API/readback evidence.

No direct coding from vague feedback is allowed.

## Completion Rule

This ledger can close only when:

- all fresh deployed-use audit rows are PASS or explicitly excluded by the user
- final orchestration still passes after any corrections
- user verifies or signs off
- `.cursor/session/state.json` sets `gates.user_script_passed=true` only after user signoff

## 2026-06-30 Fresh Audit Note

See `docs/framework/fresh-deployed-use-audit-2026-06-30.md`.

- R21 final journey evidence consistency: PASS.
- R12 module builder usability: PASS after fixing stale result-file persistence.
- R13 cross-shell responsive/browser layout: PASS with 16 screenshots.
- J10 static usability audit: PASS, scannedFiles=24, blockerCount=0, warningCount=0. Evidence: `docs/evidence/final-usability-static-audit-result.json`.
- J11 final requirement coverage audit: FAIL as expected, requiredCount=43, missingCount=0, notClosedCount=45. Evidence: `docs/evidence/final-requirement-coverage-audit-result.json`. This is now the hard blocker against claiming full completion from existing R-batches.
- This is still not user signoff. The user-reported usability concern remains OPEN until manual role-journey inspection or targeted task cards close the exact routes the user finds mixed or unusable.
