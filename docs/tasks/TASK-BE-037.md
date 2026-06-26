# TASK-BE-037

## meta

- task_id: TASK-BE-037
- type: implementation
- owner: backend
- phase: build
- parallel_group: G8
- depends_on: [TASK-BE-006, TASK-BE-034, TASK-BE-036]

## goal

Implement platform/system/work AI Agent APIs.

## inputs

- `docs/api/api.md`
- `backend/examine-ai-work/src/main/java/com/unique/examine/aiwork/base/**`

## outputs

- `backend/examine-ai-work/src/main/java/com/unique/examine/aiwork/manage/agent/**`

## scope

### Do

- Implement Agent policy CRUD/publish-check, sessions, messages, platform confirmation, system write confirmation, work draft confirmation, and Agent audit log query.

### Do Not

- Do not allow platform Agent to open or write system business data.
- Do not bypass human confirmation.

## self_check_commands

```powershell
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

## acceptance

- [ ] Platform, system write, and work draft confirmations are separate.
- [ ] Audit logs include model, prompt, policy, permission, desensitize, and trace data.

