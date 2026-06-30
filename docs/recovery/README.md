# Recovery Execution Rules

Time: 2026-06-27 Asia/Shanghai

This directory is the current recovery entry point after the user reported that the running system has crowded pages, confused functions, prototype mismatch, and incomplete frontend-backend integration.

## Current Verdict

Do not continue free-form coding from the old build plan.

The next work must convert the approved prototype and real implementation status into small, explicit, independently verifiable business tasks. A task is not complete because a page exists, an API returns 200, generated CRUD exists, or the project builds. A task is complete only when the assigned role can finish the stated business action in the running system and the result is visible, persisted, permission-checked, and repeatable.

## Required Reading Order

Every new recovery/coding session must read:

1. `.cursor/README.md`
2. `.cursor/architecture/final-goal-framework.md`
3. `.cursor/session/state.json`
4. `.cursor/knowledge/agent-operating-rules.md`
5. `.cursor/knowledge/project-operating-rules.md`
6. `.cursor/knowledge/failure-lessons.md`
7. `docs/framework/next-execution-ledger.md`
8. `docs/design/prototype-brief.md`
9. `docs/design/prototypes/index.html`
10. `docs/api/api.md`
11. `docs/recovery/final-usable-system-acceptance.md`
12. `docs/recovery/current-product-audit.md`
13. `docs/recovery/p0-task-cards.md`
14. `docs/recovery/prototype-to-implementation-matrix.md`
15. `docs/recovery/generated-vs-coded-api.md`
16. `docs/recovery/fix-batches.md`

## Hard Gate

Coding is allowed only for tasks listed in `docs/recovery/fix-batches.md`.

Before touching source code, the worker must identify the exact task card id, prototype reference, affected frontend paths, affected backend paths, data tables, role/permission expectation, and acceptance script.

If a requested fix is not covered by a task card, first update the recovery task card and matrix. Do not patch code from a vague instruction such as "fix the page", "connect the API", or "make it usable".

For final-goal failures, first update `docs/framework/next-execution-ledger.md` and the affected journey in `docs/recovery/final-usable-system-acceptance.md`. Area-level OK rows and R-batch acceptance are evidence only; they do not replace a role-level journey gate.

## User Consultation Protocol

Agents must not ask the user about ordinary implementation details that can be inferred from the approved prototype, API contract, source code, or recovery task cards. But agents must ask the user before proceeding when the decision changes the final product goal or user-facing experience.

Ask the user for confirmation when any of these are true:

- The prototype and current implementation conflict, and there is more than one reasonable product direction.
- A task card cannot define the business outcome clearly from existing files.
- A UI/layout correction may substantially change how the user works, not just fix crowding or broken layout.
- A feature could be implemented as either a generated CRUD/admin function or a coded business workflow, and the business expectation is unclear.
- A P0 scope item seems too large and must be split or deferred.
- The acceptance result depends on subjective usability: "this page feels usable", "this flow is not confusing", "this is the target work style".

When asking, provide:

- The exact page/flow/task id.
- The conflict or unclear decision.
- Two or three concrete options.
- The recommended option and why.
- The consequence of not deciding now.

Do not ask broad questions such as "what should I do next". Ask narrow product questions that unblock a task card.

User answers must be written back into one of:

- `docs/recovery/p0-task-cards.md`
- `docs/recovery/prototype-to-implementation-matrix.md`
- `docs/recovery/current-product-audit.md`
- `.cursor/session/state.json`

Chat-only decisions are not binding for later sessions.

## Completion Rule

For each recovery task, provide evidence for all of these:

- Prototype reference matches the implemented entry and layout.
- Frontend page is not visually crowded or stacked incorrectly.
- The page calls real APIs, not local mock/sample/stub data.
- Backend business logic writes to and reads from the real database.
- Browser refresh or re-login can read the same result back.
- Role permissions are enforced on the server and reflected in the UI.
- Empty, loading, forbidden, validation failure, and backend failure states are handled.
- A browser/API script starts from login and completes the task.

Missing any item means the task remains open.
