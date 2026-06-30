# Journey Gate

## Meta

- gate_id:
- linked_goal_id:
- linked_task_ids:
- status: OPEN | PASS | FAIL
- updated_at:

## Role Journey

- role:
- entry_point:
- business_outcome:
- prototype_or_requirement_reference:

## Flow

1. Start from:
2. Do:
3. System must:
4. Result must be visible at:
5. Result must persist after:

## Cross-Layer Contract

| Layer | Required behavior | Path or API | Status |
|---|---|---|---|
| Frontend |  |  | OPEN |
| Backend |  |  | OPEN |
| Data |  |  | OPEN |
| Permission |  |  | OPEN |
| State and errors |  |  | OPEN |
| Operations/audit |  |  | OPEN |

## Generated Versus Coded Boundary

- generated plumbing:
- coded behavior:
- not allowed as completion evidence:

## Acceptance Evidence

- browser script:
- API script:
- database/readback evidence:
- screenshots:
- release evidence:
- cleanup:

## Non-Completion Cases

- Page exists but the action cannot be completed.
- API returns success but readback does not show the result.
- UI uses local/mock/prototype data in a production route.
- Permission negative case is missing.
- Failure/empty/disabled/loading state is missing for the key action.

Project-specific additions:

-
