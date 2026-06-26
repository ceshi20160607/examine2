# TASK-BE-021

## meta

- task_id: TASK-BE-021
- type: implementation
- owner: backend
- phase: build
- parallel_group: G5
- depends_on: [TASK-BE-020]

## goal

Implement dynamic runtime record APIs.

## inputs

- `docs/api/api.md`
- `backend/examine-module/src/main/java/com/unique/examine/module/base/**`

## outputs

- `backend/examine-module/src/main/java/com/unique/examine/module/manage/runtime/**`

## scope

### Do

- Implement dynamic list schema, server-side search, detail, create, update, delete, action execution, and history.
- Enforce field permissions, data scope, filters, sorting, row-click target, and action disabled reasons.

### Do Not

- Do not implement import/export or workflow approval runtime.

## self_check_commands

```powershell
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

## acceptance

- [ ] Runtime list is server-side paginated.
- [ ] Detail payload contains business-specific tabs and approval sidebar hook.

