# Acceptance Rules

Acceptance is a judge role, not an implementation role. The implementer can provide self-check evidence, but cannot declare their own task complete.

## Acceptance Levels

| Level | Meaning | Required evidence |
|---|---|---|
| Task | One business task card is complete. | Task outputs, self-checks, cross-layer evidence, independent verifier. |
| Batch | A planned group of tasks is complete. | All task cards accepted plus clean build/release checks. |
| Journey | One role can finish one real business outcome. | Deployed browser/API journey, readback, permissions, states, cleanup. |
| Final Goal | The broad user objective is usable end to end. | Final goal ledger, all journey gates pass, release evidence, user signoff boundary. |

Batch acceptance never equals final-goal acceptance.

## Role Separation

| Role | Can do | Cannot do |
|---|---|---|
| implementer | write code and self-check evidence | declare final acceptance |
| test agent | design and run verification | implement business code |
| task-accept skill | compare evidence to task card | change implementation |
| pm/conductor | update state and gates from evidence | replace user signoff |
| user | sign off subjective usability and final acceptance | be replaced by scripts |

## Task Acceptance Checklist

Every task must satisfy the task template in `.cursor/templates/task.md`.

The verifier must check:

1. The task links to a final goal ledger or journey gate when the work is part of a broad objective.
2. The role, entry point, and business outcome are explicit.
3. Declared outputs exist and are non-empty.
4. Self-check commands were run and evidence is stored under `docs/evidence`.
5. Generated plumbing is separated from coded business behavior.
6. Frontend, backend, data, permission, state, and readback evidence pass where applicable.
7. Browser/API evidence starts from the realistic role entry point.
8. No new open P0 issue points to the task.
9. Implementer and verifier are not the same actor.

## Final Goal Acceptance

A broad objective must have a final goal ledger before final completion can be claimed.

Final acceptance requires:

- all linked journey gates are `PASS`
- release/package/deployment evidence passes when the goal is deployable software
- permission positive and negative cases are covered
- key empty/loading/disabled/validation/error/async states are covered
- disposable test data is cleaned or intentionally retained with a reason
- user signoff is recorded separately

Agents must not set `gates.user_script_passed=true` from engineering evidence alone.

## Evidence Format

Task evidence should be stored as:

```markdown
# TASK-XXX Acceptance

- task_id: TASK-XXX
- linked_goal_id:
- linked_journey_gate:
- implementer:
- acceptor:
- verdict: pass | fail
- checked_at:
- business_outcome:
- evidence:
  - browser:
  - api:
  - data_readback:
  - permission:
  - states:
  - release:
- issues: []
```

## Failure Handling

If acceptance fails:

- do not claim completion
- update the task card, final goal ledger, or issue registry with the exact gap
- route the work back to design, contract, build, or verify according to the gap
- ask the user only when the decision changes the final product goal, user-facing workflow, or subjective usability target
