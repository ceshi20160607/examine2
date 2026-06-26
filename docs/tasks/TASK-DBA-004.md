# TASK-DBA-004

## meta

- task_id: TASK-DBA-004
- type: implementation
- owner: dba
- phase: build
- parallel_group: G0
- depends_on: []

## goal

Create dynamic business runtime data schema fragments.

## inputs

- `docs/api/api.md`
- `docs/user_requirement.md`

## outputs

- `sql/fragments/004-dynamic-runtime.sql`
- `docs/database/dynamic-runtime.md`

## scope

### 做

- Define dynamic record, value, index, child row, relation, history, attachment reference, print/export record reference, draft, and sequence tables.
- Include indexes for list search, sort, field filters, record ownership, child row lookup, relation lookup, and sequence allocation.

### 不做

- Do not implement module configuration, workflow, message, work, AI, integration, async task, or ops tables.
- Do not write backend code.

## self_check_commands

```powershell
Test-Path sql/fragments/004-dynamic-runtime.sql
Test-Path docs/database/dynamic-runtime.md
```

## acceptance

- [ ] Dynamic data belongs to `systemId/tenantId/moduleId`.
- [ ] Sequence table avoids max plus one allocation.
- [ ] History captures before/after value, source, permission snapshot, trace, and desensitization context.
- [ ] `task-accept` verdict is pass.

## integration_test

Runtime list, detail, import, export, draft, relation, attachment, and field permission tasks consume this schema.
