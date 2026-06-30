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

## Current Execution Rule

Every new change must follow this order:

```text
final product goal
  -> role journey
  -> exact usability or function gap
  -> task card
  -> generated-vs-coded boundary
  -> implementation
  -> deployed browser/API/readback evidence
  -> updated final ledger
```

Do not code from vague feedback. Convert the feedback into a role journey gap first.

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

The current mode is not "continue R batches".

The current mode is:

1. Run a fresh role-journey audit against the deployed system.
2. Run the requirement coverage gate against `docs/user_requirement.md`.
3. Record exact failures in `docs/framework/next-execution-ledger.md` and `docs/framework/final-requirement-coverage-ledger.md`.
4. Add task cards only for concrete gaps.
5. Implement those gaps.
6. Re-run deployed evidence and requirement coverage.
7. Keep `gates.user_script_passed=false` until user verification or signoff.

## Deferred

Future reusable framework extraction is intentionally deferred.

Do not spend current effort organizing cross-project reuse unless it directly helps the current system become usable.
