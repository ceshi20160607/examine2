# TASK-BE-032

## meta

- task_id: TASK-BE-032
- type: implementation
- owner: backend
- phase: build
- parallel_group: G4
- depends_on: [TASK-BE-005, TASK-BE-012]

## goal

Implement message, notification template, and delivery log APIs.

## inputs

- `docs/api/api.md`
- `backend/examine-message-log/src/main/java/com/unique/examine/messagelog/base/**`

## outputs

- `backend/examine-message-log/src/main/java/com/unique/examine/messagelog/manage/message/**`
- `backend/examine-message-log/src/main/java/com/unique/examine/messagelog/manage/notification/**`

## scope

### Do

- Implement platform/system message search, mark-read, archive, load-more, notification template CRUD, and delivery log query.
- Enforce platform/system message target boundary.

### Do Not

- Do not build frontend message drawer.

## self_check_commands

```powershell
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

## acceptance

- [ ] Platform message cannot directly open system business detail.
- [ ] Filters include system, tenant, template, type, read/archive status, time range, and keyword.

