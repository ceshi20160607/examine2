# TASK-BE-031

## meta

- task_id: TASK-BE-031
- type: implementation
- owner: backend
- phase: build
- parallel_group: G6
- depends_on: [TASK-BE-021, TASK-BE-030]

## goal

Implement workflow runtime, approval, and todo APIs.

## inputs

- `docs/api/api.md`
- `backend/examine-flow/src/main/java/com/unique/examine/flow/base/**`

## outputs

- `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/runtime/**`
- `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/approval/**`
- `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/todo/**`

## scope

### Do

- Implement workflow instance snapshot, approval actions, transfer/reject handling, todo search, and todo action execution.
- Approval actions must be idempotent and audited.

### Do Not

- Do not implement flow definition canvas editing in this task.

## self_check_commands

```powershell
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

## acceptance

- [ ] Todo is independent from business module sidebar.
- [ ] Duplicate approval action does not advance workflow twice.

