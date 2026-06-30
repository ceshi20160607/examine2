# Phase 3: Build

## Entry

- `design_user_approved = true`
- `api_frozen = true`
- `tasks_planned = true`
- broad objectives have a final goal ledger and journey gates

## Steps

1. Planner creates or updates `docs/tasks/TASK-*.md` from `.cursor/templates/task.md`.
2. For recovery or broad objectives, planner links every task to a final goal ledger and a journey gate.
3. Conductor schedules tasks by dependency graph; parallel tasks must not write overlapping outputs.
4. DBA/backend/frontend implement only the declared task scope.
5. Each implementer records self-check evidence.
6. Independent acceptance verifies each task against `.cursor/architecture/acceptance.md`.
7. Batch-level clean build is run only after all tasks in the batch pass.

## Hard Rules

- Do not start coding from vague feedback such as "make it usable" or "fix the page".
- First update the final goal ledger, journey gate, or task card.
- Generated CRUD is plumbing only; it does not close a business task.
- A successful build does not close a journey gate.
- A batch pass does not close the final goal.

## Exit

- [ ] current milestone tasks are accepted
- [ ] clean build or package evidence passes
- [ ] no open P0 build issue remains
- [ ] journey gates affected by this batch have updated status and evidence links

## Next

Proceed to `phase-4-verify.md`.
