# TASK-DBA-001

## meta

- task_id: TASK-DBA-001
- type: implementation
- owner: dba
- phase: build
- parallel_group: G0
- depends_on: []

## goal

Create platform identity schema fragments for account, system, tenant, member, account-member binding, SSO member mapping, no-member request, and login audit context.

## inputs

- `docs/api/api.md`
- `.cursor/architecture/backend-structure.md`

## outputs

- `sql/fragments/001-platform-identity.sql`
- `docs/database/platform-identity.md`

## scope

### 做

- Define platform account, system lifecycle, tenant, department/member, account-member binding, SSO binding, no-member access request, and login audit tables.
- Include tenant/system isolation fields, soft delete, trace fields, indexes, and comments.

### 不做

- Do not write role, permission snapshot, module runtime, workflow, message, work, AI, OpenAPI, async task, or ops tables.
- Do not merge into `sql/init.sql`.

## self_check_commands

```powershell
Test-Path sql/fragments/001-platform-identity.sql
Test-Path docs/database/platform-identity.md
```

## acceptance

- [ ] Outputs exist and are non-empty.
- [ ] Table names use documented prefixes.
- [ ] Key indexes cover `systemId`, `tenantId`, account, member, binding, SSO, and login audit lookups.
- [ ] No secret plaintext columns.
- [ ] `task-accept` verdict is pass.

## integration_test

DBA merge task validates all fragments together.
