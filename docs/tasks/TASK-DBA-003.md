# TASK-DBA-003

## meta

- task_id: TASK-DBA-003
- type: implementation
- owner: dba
- phase: build
- parallel_group: G0
- depends_on: []

## goal

Create module configuration schema fragments.

## inputs

- `docs/api/api.md`
- `docs/design/prototype-brief.md`

## outputs

- `sql/fragments/003-module-config.sql`
- `docs/database/module-config.md`

## scope

### 做

- Define module group, module, field definition, dictionary type/item, page/list scene, action, import/export config, publish version, and publish check reference tables.
- Include publish status, versioning, visibility role refs, dynamic field type metadata, list/filter/sort capability flags, trace fields, and comments.

### 不做

- Do not define dynamic record/value/index data, workflow, message, work, SecretRef, OpenAPI, Agent, backup, quota, or feature flag tables.

## self_check_commands

```powershell
Test-Path sql/fragments/003-module-config.sql
Test-Path docs/database/module-config.md
```

## acceptance

- [ ] Module group, module, field, dictionary, scene, action, and publish version tables are separated.
- [ ] Dictionary item schema supports color, icon, semantic, disabled history, and kanban use.
- [ ] Field metadata supports storage type, filter operators, sortable flag, mask rule, and import/export rule.
- [ ] `task-accept` verdict is pass.

## integration_test

Runtime list, detail, import, export, workflow, and work kanban tasks consume this schema.
