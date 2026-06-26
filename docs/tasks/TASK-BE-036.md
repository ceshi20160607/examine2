# TASK-BE-036

## meta

- task_id: TASK-BE-036
- type: implementation
- owner: backend
- phase: build
- parallel_group: G7
- depends_on: [TASK-BE-021, TASK-BE-031, TASK-BE-032]

## goal

Implement work management APIs.

## inputs

- `docs/api/api.md`
- `backend/examine-ai-work/src/main/java/com/unique/examine/aiwork/base/**`

## outputs

- `backend/examine-ai-work/src/main/java/com/unique/examine/aiwork/manage/work/**`

## scope

### Do

- Implement work dashboard, project search, project task list/kanban, plain task list/kanban, comments, events, daily report search/create, auto draft, and work config APIs.

### Do Not

- Do not merge project task and plain task lifecycles.

## self_check_commands

```powershell
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

## acceptance

- [ ] Work management exposes four tabs: dashboard, project task, plain task, daily report.
- [ ] Kanban uses configured select/multi-select fields and dictionary color/icon semantics.

