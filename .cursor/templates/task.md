# TASK-{ID}

## Meta

- task_id: TASK-XXX
- type: implementation | contract-only | design | test | recovery
- owner: backend | frontend | dba | uiux | test | planner | pm
- phase: discovery | design | contract | build | verify | recovery
- parallel_group: G1 | none
- depends_on: []
- linked_goal_id:
- linked_journey_gate:

## Business Goal

One sentence from the target role's point of view. Do not describe only files, APIs, pages, or generated entities.

## Role And Entry

- role:
- entry_point:
- prototype_or_requirement_reference:
- expected_user_result:

## Inputs

- path/to/input.md

## Outputs

Outputs must not overlap with another parallel task.

- path/to/output

## Scope

### Do

-

### Do Not

-

## Generated Versus Coded Boundary

- generated plumbing:
- coded business behavior:
- frontend binding:
- permission rule:
- data persistence/readback:
- states to handle:

## Non-Completion Cases

This task is not complete if any of these are true:

- The page exists but the role cannot finish the business action.
- The API returns 200 but the deployed frontend is not bound to it.
- Generated CRUD exists but the coded behavior is missing.
- The result is not visible after reload, re-login, or restart when persistence matters.
- Permission positive and negative cases are not both checked.
- Empty, loading, disabled, validation, error, or async states are missing where relevant.
- Evidence does not start from the real role entry point.

## Self Check Commands

```powershell
# command
```

## Acceptance

- [ ] linked final goal ledger or journey gate is named
- [ ] role, entry point, and business outcome are explicit
- [ ] all outputs exist and are non-empty
- [ ] self_check_commands passed and evidence is stored in docs/evidence
- [ ] generated-vs-coded boundary is proven
- [ ] frontend, backend, data, permission, state, and readback evidence all pass where applicable
- [ ] browser/API script proves the role journey or the task's part of it
- [ ] no new open P0 issue points to this task
- [ ] skill task-accept or equivalent independent verifier records verdict=pass

## Integration Test

Describe the test entry for the test agent or script. It must prove behavior, not just build output.
