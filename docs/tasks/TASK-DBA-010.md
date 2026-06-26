# TASK-DBA-010

## meta

- task_id: TASK-DBA-010
- type: implementation
- owner: dba
- phase: build
- parallel_group: G1
- depends_on: [TASK-DBA-001, TASK-DBA-002, TASK-DBA-003, TASK-DBA-004, TASK-DBA-005, TASK-DBA-006, TASK-DBA-007, TASK-DBA-008, TASK-DBA-009]

## goal

Merge schema fragments into the executable initial SQL baseline.

## inputs

- `sql/fragments/001-platform-identity.sql`
- `sql/fragments/002-permission-rbac.sql`
- `sql/fragments/003-module-config.sql`
- `sql/fragments/004-dynamic-runtime.sql`
- `sql/fragments/005-workflow-approval.sql`
- `sql/fragments/006-message-todo-log.sql`
- `sql/fragments/007-work-management.sql`
- `sql/fragments/008-secret-openapi-agent.sql`
- `sql/fragments/009-async-ops.sql`

## outputs

- `sql/init.sql`
- `docs/database/init-sql-check.md`

## scope

### 做

- Merge fragments in dependency order.
- Check naming, comments, indexes, nullable history fields, unique constraints, soft delete, and tenant isolation.

### 不做

- Do not implement Java entities or migrations beyond the initial SQL baseline.

## self_check_commands

```powershell
Test-Path sql/init.sql
Test-Path docs/database/init-sql-check.md
```

## acceptance

- [ ] `sql/init.sql` includes all fragments exactly once.
- [ ] No duplicate table names or unscoped unique indexes.
- [ ] Secret plaintext is absent.
- [ ] `task-accept` verdict is pass.

## integration_test

Backend scaffold and generator tasks consume `sql/init.sql`.
