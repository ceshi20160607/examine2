# TASK-BE-005

## meta

- task_id: TASK-BE-005
- type: implementation
- owner: backend
- phase: build
- parallel_group: G2
- depends_on: [TASK-BE-002, TASK-DBA-010]

## goal

Generate flow, message, todo, log, and async task base files.

## inputs

- `sql/init.sql`
- `backend/examine-generator/**`

## outputs

- `backend/examine-flow/src/main/java/com/unique/examine/flow/base/**`
- `backend/examine-message-log/src/main/java/com/unique/examine/messagelog/base/**`
- `backend/examine-flow/src/main/resources/mapper/base/**`
- `backend/examine-message-log/src/main/resources/mapper/base/**`

## scope

### Do

- Generate base files for workflow, approval, todo, message, notification, audit log, and task tables.

### Do Not

- Do not implement approval runtime or notification delivery logic.

## self_check_commands

```powershell
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

## acceptance

- [ ] Generated base files compile.
- [ ] Workflow and message-log ownership is separated.
