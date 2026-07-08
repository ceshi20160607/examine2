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

## Requirement Confirmation Contract

Fill this before implementation. Do not use screenshots as the source of requirement truth.

| Contract item | Required content |
|---|---|
| Requirement source | Exact `docs/user_requirement.md` row/section, approved prototype brief section, API contract section, or user answer written to project files |
| Target role | The real user role completing the job |
| Entry point | Login/system switch/route/menu/action that starts the journey |
| User job | The business result the user must achieve |
| Data contract | Objects/tables/API payloads that must persist and read back |
| Permission contract | Allowed case and forbidden case |
| State contract | Loading, empty, disabled, validation, backend error, async, success/failure, and retry states |
| Copy contract | Labels, tips, disabled reasons, validation messages, and result messages that must be unambiguous |
| Acceptance assertions | Machine-checkable assertions proving the job works |
| Screenshot evidence boundary | What screenshots prove visually, and what they do not prove |

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

## V6 Readiness Lock

Do not start implementation until these are true:

- the task maps to at least one unfinished requirement ledger row
- the task maps to a role journey that remains open or partial after latest user feedback
- the user result is a business outcome, not a page/API/file deliverable
- generated plumbing is explicitly excluded from completion evidence unless coded behavior uses it
- screenshots are scoped to visual evidence only
- the acceptance script starts from a real deployed role entry and proves API/readback, permission positive/negative, states, and visible result where applicable
- old batch evidence is referenced only as prior evidence, not as current completion

## V7 Human-Usable Gate

Use this section whenever the task affects a deployed user-facing route or the user has reported the product is still confusing, crowded, mismatched, or unusable.

- primary_role_journey:
- real_entry_sequence:
- one_primary_task_surface:
- role_shell_separation:
- page_stacking_risk:
- ambiguous_copy_or_tip_risk:
- data_readback_after_action:
- permission_positive_and_negative:
- reload_relogin_restart_requirement:
- user_signoff_boundary:

## Evidence Contract

Fill this before implementation. Use `not applicable` only with a reason.

| Evidence type | Required? | Planned path or command | What it proves |
|---|---|---|---|
| Requirement ledger row | yes |  |  |
| Journey gate | yes |  |  |
| Deployed browser path | yes/no + reason |  |  |
| API/readback script | yes/no + reason |  |  |
| Permission positive case | yes/no + reason |  |  |
| Permission negative case | yes/no + reason |  |  |
| Persistence reload/re-login/restart | yes/no + reason |  |  |
| Mobile/desktop layout check | yes/no + reason |  |  |
| Text/tips/error-state check | yes/no + reason |  |  |
| Screenshot visual check | yes/no + reason |  | Visual only: hierarchy, clipping, overflow, visible copy, role-specific surface |
| Release/package verification | yes/no + reason |  |  |

## Non-Completion Cases

This task is not complete if any of these are true:

- The page exists but the role cannot finish the business action.
- The API returns 200 but the deployed frontend is not bound to it.
- Generated CRUD exists but the coded behavior is missing.
- The result is not visible after reload, re-login, or restart when persistence matters.
- Permission positive and negative cases are not both checked.
- Empty, loading, disabled, validation, error, or async states are missing where relevant.
- Evidence does not start from the real role entry point.
- Evidence proves only a component but not the role-facing business result named above.
- Screenshots are used as requirement confirmation instead of visual evidence for an already-defined requirement contract.
- Human-usable checks are missing for role shell separation, one primary task surface, page stacking, ambiguous copy/tips, and visible post-action state.
- The task asks the user to decide ordinary engineering details that the agent can infer from current requirements, prototype, ledgers, or source.
- The task lacks machine-checkable acceptance assertions for function, data, permission, or state.
- The final requirement ledger remains unchanged after the task changes coverage.
- `scripts/final-goal-framework-audit.ps1` fails after the task updates framework, ledger, or recovery status.

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
- [ ] evidence contract rows are all filled and linked
- [ ] final requirement ledger is updated without over-claiming `PROVEN`
- [ ] final goal framework audit passes if framework/status/ledger files changed
- [ ] no new open P0 issue points to this task
- [ ] skill task-accept or equivalent independent verifier records verdict=pass

## Integration Test

Describe the test entry for the test agent or script. It must prove behavior, not just build output.
