# TASK-BE-035

## meta

- task_id: TASK-BE-035
- type: implementation
- owner: backend
- phase: build
- parallel_group: G5
- depends_on: [TASK-BE-006, TASK-BE-033]

## goal

Implement OpenAPI external app and call log APIs.

## inputs

- `docs/api/api.md`
- `backend/examine-app/src/main/java/com/unique/examine/app/base/**`

## outputs

- `backend/examine-app/src/main/java/com/unique/examine/app/manage/openapi/**`

## scope

### Do

- Implement external app CRUD, scope management, secret rotation trigger, rate-limit metadata, and call log search.

### Do Not

- Do not expose plaintext app secret.

## self_check_commands

```powershell
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

## acceptance

- [ ] OpenAPI writes support idempotency key.
- [ ] Call logs filter by app, scope, result, time range, and trace ID.

