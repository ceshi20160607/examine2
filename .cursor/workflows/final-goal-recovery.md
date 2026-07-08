# Final Goal Recovery Workflow

Use this workflow when the user says the system is still not usable after tasks, batches, or scripts were reported as complete.

## Entry

- user reports final-goal failure, usability failure, prototype mismatch, integration failure, or repeated false completion
- current evidence claims are not enough to explain or satisfy the user's target

## Required Reading

1. `.cursor/README.md`
2. `.cursor/architecture/final-goal-framework.md`
3. `.cursor/session/state.json`
4. `.cursor/knowledge/agent-operating-rules.md`
5. `.cursor/knowledge/project-operating-rules.md`
6. `.cursor/knowledge/failure-lessons.md`
7. current requirement, prototype, API, task, recovery, and evidence files named by the project

## Procedure

1. State the failure in final-goal terms, not in batch terms.
2. If the user says the deployed product is still unusable, reopen final usable-system acceptance and downgrade historical journey PASS rows to engineering evidence.
3. Create or update the final goal ledger.
4. Split the target into role-level journey gates.
5. Write the requirement confirmation contract for each next gap before coding.
6. Mark which existing evidence supports each journey and which gaps remain.
7. For each gap, create or update one task card with generated-vs-coded boundary and acceptance script.
8. Treat screenshots only as visual evidence. They can prove density, clipping, overflow, visible copy, and rendered state; they cannot confirm requirement scope, workflow completeness, data persistence, permission correctness, or final acceptance.
9. Split implementation work from the requirement row, not from page/API lists:
   - requirement row
   - role journey
   - user job
   - generated plumbing
   - coded business behavior
   - frontend binding
   - backend/data/permission/readback
   - deployed evidence
10. If the user says the product is confusing, crowded, mismatched, or still not useful, select a human-usable role journey before selecting another feature-slice aggregation.
11. Do not ask the user for ordinary engineering choices. Ask only for product decisions that change user-facing behavior and cannot be inferred from the frozen requirement, prototype, ledgers, or source.
12. Run or refresh:
   - `scripts/final-requirement-gap-report.ps1`
   - `scripts/final-goal-framework-audit.ps1 -NoFailExit`
13. Choose the next task from the earliest unfinished requirement row and the most severe deployed-use journey gap.
14. Write the selected task into `.cursor/session/state.json` `build_plan.nextTasks` before coding.
15. Implement only tasks covered by the ledger, gate, and requirement confirmation contract.
16. Run `scripts/final-goal-framework-audit.ps1` and confirm it checks the active next task card's requirement confirmation contract, not only the template. The active task's requirement rows must also match the coverage ledger, gap report, and next-execution ledger.
17. Run standalone evidence first, then add the script to final orchestration when the task affects final journeys.
18. Update session state with engineering status and keep user signoff separate.

## V4 Framework Audit

After any framework/rule/task-ledger change, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\final-goal-framework-audit.ps1
```

This audit only proves that the execution framework is still forcing unfinished work to remain visible. It does not prove the product is usable.

## Non-Completion Cases

The recovery is not done if:

- the final goal ledger is missing
- a journey gate has no role entry point
- an area-level OK is used to replace a role journey
- generated CRUD or API 200 is used as final evidence
- screenshots are used as requirement confirmation instead of visual/layout evidence
- a task starts coding without requirement source, role entry, user job, data contract, permission contract, state contract, copy contract, and acceptance assertions
- the active task named in `build_plan.nextTasks` is not mapped to a task card with a complete requirement confirmation contract
- the active task's requirement rows are missing from the coverage ledger, already closed without removing the task, missing a concrete gap, or not listed in the gap report and next-execution ledger
- user signoff is implied from engineering scripts
- the lesson is not written back into current framework files
- `build_plan.nextTasks` is empty while requirement rows remain `PARTIAL` or `OPEN`
- the next implementation is chosen from convenience instead of the earliest unfinished FRC batch or deployed journey gap
- old journey PASS rows survive new user feedback that the deployed product is still unusable
- generated plumbing is accepted without coded business behavior and deployed role evidence
- the next task after "still unusable" feedback is another narrow feature slice while human role-journey acceptance rows remain partial
- the task asks the user for engineering choices that can be inferred locally
- page stacking, mixed role shells, ambiguous tips, or generic success/failure copy are not part of the acceptance assertions
- framework health is claimed without `scripts/final-goal-framework-audit.ps1`

## Exit

- final goal ledger is current
- all journey gates have status and evidence links
- remaining gaps are explicit task cards or user decisions
- engineering final gate and user signoff status are separate
