# TASK-BE-006

## meta

- task_id: TASK-BE-006
- type: implementation
- owner: backend
- phase: build
- parallel_group: G2
- depends_on: [TASK-BE-002, TASK-DBA-010]

## goal

Generate OpenAPI, AI Agent, and work management base files.

## inputs

- `sql/init.sql`
- `backend/examine-generator/**`

## outputs

- `backend/examine-app/src/main/java/com/unique/examine/app/base/**`
- `backend/examine-ai-work/src/main/java/com/unique/examine/aiwork/base/**`
- `backend/examine-app/src/main/resources/mapper/base/**`
- `backend/examine-ai-work/src/main/resources/mapper/base/**`

## scope

### Do

- Generate base files for external apps, OpenAPI logs, Agent policies/sessions/confirmations/audit, work projects/tasks/reports/calendar.

### Do Not

- Do not implement Agent execution or work APIs.

## self_check_commands

```powershell
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

## acceptance

- [ ] Generated base files compile.
- [ ] Agent and work tables map to `examine-ai-work`.
