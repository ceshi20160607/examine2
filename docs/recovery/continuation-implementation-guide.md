# Continuation Implementation Guide

Time: 2026-07-06 Asia/Shanghai

## Current Structure

The project already has the correct working architecture:

| Area | Purpose | How to continue |
|---|---|---|
| `.cursor/` | The process controller. It stores rules, current session state, knowledge, and workflow boundaries. | Read it before each new task and update `session/state.json` when the active batch changes. |
| `docs/user_requirement.md` and `docs/design/` | The product target and signed design baseline. | Do not invent new product scope from chat. Link every later change back to requirement rows and design/prototype evidence. |
| `docs/framework/` | Final-goal framework, flow blueprint, coverage ledger, and next execution ledger. | Use it to decide the next work from unfinished role journeys, not from random pages or APIs. |
| `docs/recovery/` | Recovery task cards, fix batches, current product audit, and final usable-system acceptance. | Add or update a task card and batch before coding any new product change. |
| `scripts/` | Deterministic evidence. | Each accepted batch needs a script result file. Browser evidence must use stable selectors and deployed release paths. |
| `backend/`, `frontend/`, `sql/` | Real implementation. | Change these only after the task card names role, data, permission, state, and evidence boundaries. |
| `release/` and `deploy/` | Deployable package and server scripts. | Final evidence must use the release package, not only dev build output. |

## Current Target

The target is not "all scripts passed". The target is a real no-code business system that people can use:

- platform workspace, platform admin, system business shell, and system admin shell are separated
- administrators can configure and publish a usable system
- normal members can use authorized runtime data without admin leakage
- approval, todo, message, OpenAPI, AI, import/export, logs, and operations are connected to real state
- release package can start, stop, restart, health-check, and expose understandable logs
- user verification is separate from engineering evidence

## Developed State

Engineering evidence is strong through R78:

- release health, deployed assets, database, schema, and Redis have passed latest release verification
- auth, register, password recovery, role landing, and shell separation have evidence
- admin configuration, runtime business use, workflow/todo/message, OpenAPI/AI, import/export, logs, operations, and product surfaces have deployed evidence
- R78 is accepted as product-surface engineering evidence only

Still open:

- `gates.user_script_passed=false`
- final requirement coverage still has open/partial rows until user verification or explicit exclusion
- no agent may set user signoff from script evidence

## How To Continue

Use this order for every later change:

1. Read `.cursor/README.md`, `.cursor/session/state.json`, `.cursor/knowledge/agent-operating-rules.md`, `.cursor/knowledge/project-operating-rules.md`, and `.cursor/knowledge/failure-lessons.md`.
2. Identify the affected role journey in `docs/framework/final-system-flow-blueprint.md`.
3. Identify the affected requirement row in `docs/framework/final-requirement-coverage-ledger.md` and `docs/evidence/final-requirement-gap-report.md`.
4. Add or update one task card in `docs/recovery/p0-task-cards.md`.
5. Add or update one batch in `docs/recovery/fix-batches.md`.
6. Update `docs/framework/next-execution-ledger.md` and `.cursor/session/state.json`.
7. Implement only the named business loop in `backend/`, `frontend/`, `sql/`, or release scripts.
8. Add or update a deterministic script under `scripts/`.
9. Run framework audit, static usability audit, and the task script.
10. Keep user signoff false unless the user verifies or signs.

## Change Types

| Change type | Required evidence |
|---|---|
| UI or route change | Deployed browser evidence, stable selectors, role shell check, desktop/mobile containment. |
| Backend behavior | API/readback evidence, permission positive and negative cases, trace/task/audit id where relevant. |
| Data model or SQL | Migration or init proof, readback from the affected role path, cleanup plan. |
| Permission change | Same-role positive and forbidden-role negative checks, no hidden field/module leakage. |
| Release or operations change | Package verification, health check, start/stop/restart/status evidence, logs. |
| Final acceptance change | Requirement coverage audit and explicit user verification boundary. |

## R79 Purpose

R79 is the handoff and continuation contract. It verifies that:

- R78 is accepted only as engineering evidence
- the continuation architecture is present on disk
- framework/static/coverage audits still run
- the system is ready for user verification without falsely closing signoff

After R79, future work should not create broad "cleanup" batches. It should create the smallest role-journey task that changes the real system and proves the result through deployed evidence.
