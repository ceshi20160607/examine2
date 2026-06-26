# TASK-BE-033

## meta

- task_id: TASK-BE-033
- type: implementation
- owner: backend
- phase: build
- parallel_group: G3
- depends_on: [TASK-BE-005]

## goal

Implement audit log and async task APIs.

## inputs

- `docs/api/api.md`
- `backend/examine-message-log/src/main/java/com/unique/examine/messagelog/base/**`

## outputs

- `backend/examine-message-log/src/main/java/com/unique/examine/messagelog/manage/audit/**`
- `backend/examine-message-log/src/main/java/com/unique/examine/messagelog/manage/task/**`

## scope

### Do

- Implement platform/system log search/detail, log type filters, async task query, retry, cancel, and result/error file references.

### Do Not

- Do not implement business-specific write APIs.

## self_check_commands

```powershell
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

## acceptance

- [ ] One log management entry covers login, business, risk, import/export, Agent, OpenAPI, and ops filters.
- [ ] Async task state machine matches frozen contract.

