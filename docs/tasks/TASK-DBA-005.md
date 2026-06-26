# TASK-DBA-005

## meta

- task_id: TASK-DBA-005
- type: implementation
- owner: dba
- phase: build
- parallel_group: G0
- depends_on: []

## goal

Create workflow and approval schema fragments.

## inputs

- `docs/api/api.md`
- `docs/design/prototype-brief.md`

## outputs

- `sql/fragments/005-workflow-approval.sql`
- `docs/database/workflow-approval.md`

## scope

### 做

- Define flow definition, flow snapshot, node config, edge/condition, publish check, flow instance, approval task, approval action, transfer, revoke/terminate, and workflow operation history tables.
- Include version, idempotency, status, node-specific config, trace/audit fields, and comments.

### 不做

- Do not define todo, message, audit log, dynamic record, module config, work management, async task, Agent, or OpenAPI tables.
- Do not write backend code or SQL merge output.

## self_check_commands

```powershell
Test-Path sql/fragments/005-workflow-approval.sql
Test-Path docs/database/workflow-approval.md
```

## acceptance

- [ ] Workflow definitions and snapshots are versioned separately.
- [ ] Approval tasks support approve, reject, transfer, revoke/terminate reason, idempotency, and trace/audit fields.
- [ ] External API/timer/update/condition node configs have clear storage boundaries without storing implementation code.
- [ ] `task-accept` verdict is pass.

## integration_test

QA validates publish, simulation, approval, rejection, transfer, duplicate submit, and workflow history scenarios.
