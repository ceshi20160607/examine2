# TASK-DBA-002

## meta

- task_id: TASK-DBA-002
- type: implementation
- owner: dba
- phase: build
- parallel_group: G0
- depends_on: []

## goal

Create role, permission, data scope, deny policy, and effective permission snapshot schema fragments.

## inputs

- `docs/api/api.md`
- `docs/user_requirement.md`

## outputs

- `sql/fragments/002-permission-rbac.sql`
- `docs/database/permission-rbac.md`

## scope

### 做

- Define platform/system role, role-member assignment, menu/action/module/field permission, data scope rule, deny policy, permission publish version, and effective permission snapshot tables.
- Include indexes for role lookup, permission version lookup, snapshot lookup by member/tenant, and deny policy evaluation.

### 不做

- Do not implement account/member identity, module config, dynamic runtime, workflow, message, work, AI, or integration tables.
- Do not write backend code.

## self_check_commands

```powershell
Test-Path sql/fragments/002-permission-rbac.sql
Test-Path docs/database/permission-rbac.md
```

## acceptance

- [ ] Role and permission rows are scoped by platform/system/tenant where applicable.
- [ ] Effective permission snapshot supports `permissionVersion`, `sourceRoleIds`, `denyPolicyIds`, field/action/dataScope, disabled reason, and explain data.
- [ ] Key indexes cover permission version, member, tenant, role, and target lookup.
- [ ] `task-accept` verdict is pass.

## integration_test

Identity, runtime, workflow, Agent, and API authorization tasks consume this schema.
