# TASK-DBA-006

## meta

- task_id: TASK-DBA-006
- type: implementation
- owner: dba
- phase: build
- parallel_group: G0
- depends_on: []

## goal

Create todo, message, notification, and audit log schema fragments.

## inputs

- `docs/api/api.md`
- `docs/design/prototype-brief.md`

## outputs

- `sql/fragments/006-message-todo-log.sql`
- `docs/database/message-todo-log.md`

## scope

### 做

- Define todo, todo action history, notification template, delivery channel, message, message target, message read/archive state, message delivery log, audit log, risk event log, and login/security log extension tables.
- Include platform/system scope boundaries, target jump metadata, trace/request/audit IDs, delivery retry fields, and pagination/filter indexes.

### 不做

- Do not define workflow instance internals, work task tables, dynamic record tables, OpenAPI call log tables, Agent audit tables, or async task tables.
- Do not write backend code or SQL merge output.

## self_check_commands

```powershell
Test-Path sql/fragments/006-message-todo-log.sql
Test-Path docs/database/message-todo-log.md
```

## acceptance

- [ ] `MessageTarget` distinguishes platform/system scope and prevents platform messages from directly binding system business detail jumps.
- [ ] Message delivery log is first-class and supports channel, retry, status, trace, and failure reason.
- [ ] Audit log supports log type tree filtering, object reference, field diff, permission snapshot, desensitization result, and trace lookup.
- [ ] `task-accept` verdict is pass.

## integration_test

QA validates platform/system message separation, todo actions, log search/detail, no-permission audit, and delivery result scenarios.
