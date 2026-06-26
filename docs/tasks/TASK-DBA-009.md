# TASK-DBA-009

## meta

- task_id: TASK-DBA-009
- type: implementation
- owner: dba
- phase: build
- parallel_group: G0
- depends_on: []

## goal

Create async task and operations governance schema fragments.

## inputs

- `docs/api/api.md`
- `.cursor/agents/dba.md`

## outputs

- `sql/fragments/009-async-ops.sql`
- `docs/database/async-ops.md`

## scope

### 做

- Define AsyncTask, task file/result/error metadata, idempotency record, feature flag, quota, health check result, backup, restore drill, archive restore request, deployment, deployment rollback, API cache policy, and ops operation history tables.
- Include the frozen async task status state machine, retry/cancel flags, rollback support, progress, result/error files, trace/audit fields, and comments.

### 不做

- Do not define business workflow approval task tables, message delivery log tables, SecretRef/OpenAPI/Agent tables, or dynamic import/export business payload tables.
- Do not implement scheduler, runtime jobs, backend code, or SQL merge output.

## self_check_commands

```powershell
Test-Path sql/fragments/009-async-ops.sql
Test-Path docs/database/async-ops.md
```

## acceptance

- [ ] Async task status covers `QUEUED/RUNNING/SUCCESS/PARTIAL_SUCCESS/FAILED/CANCELED/ROLLBACKING/ROLLED_BACK`.
- [ ] Idempotency records can support write, import/export, publish check, secret rotation, Agent confirmation, and OpenAPI write scenarios.
- [ ] Ops governance tables cover health, feature flags, quotas, backup/restore drill, archive restore, deployment rollback, and cache policy.
- [ ] `task-accept` verdict is pass.

## integration_test

QA validates async progress, partial success, retry/cancel, rollback metadata, health check, quota, feature flag, backup/restore drill, and cache policy scenarios.
