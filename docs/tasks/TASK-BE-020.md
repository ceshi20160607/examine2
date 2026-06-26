# TASK-BE-020

## meta

- task_id: TASK-BE-020
- type: implementation
- owner: backend
- phase: build
- parallel_group: G4
- depends_on: [TASK-BE-004, TASK-BE-013]

## goal

Implement module configuration APIs.

## inputs

- `docs/api/api.md`
- `sql/init.sql`

## outputs

- `backend/examine-module/**`

## scope

### 做

- Implement module group, module, field, dictionary, scene/list schema, action, publish check, permission binding, import/export schema configuration, print template configuration, and column/filter/sort metadata.
- Ensure module configuration can support runtime list/detail pages, advanced filters, scenarios, batch action restrictions, and row detail targets.

### 不做

- Do not implement dynamic runtime record CRUD, drafts, import/export execution, attachments, sequence allocation, workflow engine internals, or Agent write actions.

## self_check_commands

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -f backend/pom.xml -pl examine-module -am -DskipTests compile
```

## acceptance

- [ ] Configuration responses describe pagination, filters, sorts, column schema, row detail target, and batch action restrictions.
- [ ] Import/export configuration is modeled, but execution remains owned by `TASK-BE-022`.
- [ ] Field configuration records read/write permission metadata for runtime enforcement.
- [ ] `task-accept` verdict is pass.

## integration_test

Che runtime list/detail and system admin module configuration depend on this task.
