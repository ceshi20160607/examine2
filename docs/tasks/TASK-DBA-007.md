# TASK-DBA-007

## meta

- task_id: TASK-DBA-007
- type: implementation
- owner: dba
- phase: build
- parallel_group: G0
- depends_on: []

## goal

Create work-management schema fragments.

## inputs

- `docs/api/api.md`
- `docs/design/prototype-brief.md`

## outputs

- `sql/fragments/007-work-management.sql`
- `docs/database/work-management.md`

## scope

### 做

- Define work dashboard cache/reference, project, project task, plain task, task collaborator, tag, kanban config, kanban lane/group state, daily report, daily report source snapshot, and daily report auto source rule tables.
- Include system/member/tenant scope, permission snapshot references, status history, related object references, cursor pagination fields, and comments.

### 不做

- Do not define dynamic module field tables, workflow approval task tables, todo/message tables, Agent confirmation tables, or async task tables.
- Do not write backend code or SQL merge output.

## self_check_commands

```powershell
Test-Path sql/fragments/007-work-management.sql
Test-Path docs/database/work-management.md
```

## acceptance

- [ ] Project task and plain task storage supports assignee, collaborators, status, tags, progress, due time, related object, and permission snapshot.
- [ ] Kanban config references published field/dictionary metadata without duplicating module config ownership.
- [ ] Daily report auto source rule is explicit, permission-aware, and requires manual confirmation.
- [ ] `task-accept` verdict is pass.

## integration_test

QA validates dashboard, project/plain task list, kanban pagination, daily report draft, manual confirmation, and permission-filtered source scenarios.
