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
2. Create or update the final goal ledger.
3. Split the target into role-level journey gates.
4. Mark which existing evidence supports each journey and which gaps remain.
5. For each gap, create or update one task card with generated-vs-coded boundary and acceptance script.
6. Implement only tasks covered by the ledger and gate.
7. Run standalone evidence first, then add the script to final orchestration.
8. Update session state with engineering status and keep user signoff separate.

## Non-Completion Cases

The recovery is not done if:

- the final goal ledger is missing
- a journey gate has no role entry point
- an area-level OK is used to replace a role journey
- generated CRUD or API 200 is used as final evidence
- user signoff is implied from engineering scripts
- the lesson is not written back into current framework files

## Exit

- final goal ledger is current
- all journey gates have status and evidence links
- remaining gaps are explicit task cards or user decisions
- engineering final gate and user signoff status are separate
