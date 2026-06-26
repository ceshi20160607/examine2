# TASK-BE-011

## meta

- task_id: TASK-BE-011
- type: implementation
- owner: backend
- phase: build
- parallel_group: G3
- depends_on: [TASK-BE-003]

## goal

Implement platform system lifecycle APIs.

## inputs

- `docs/api/api.md`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/base/**`

## outputs

- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/system/**`

## scope

### Do

- Implement list, create, detail, update, enable, disable, delete, restore, health, and lifecycle result contracts.
- Record trace and audit context.

### Do Not

- Do not implement system members, roles, or module config.

## self_check_commands

```powershell
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

## acceptance

- [ ] APIs match frozen endpoints.
- [ ] Lifecycle actions return sync result or async task.
- [ ] `task-accept` verdict is pass.

