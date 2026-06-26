# TASK-BE-004

## meta

- task_id: TASK-BE-004
- type: implementation
- owner: backend
- phase: build
- parallel_group: G2
- depends_on: [TASK-BE-002, TASK-DBA-010]

## goal

Generate module runtime and upload base files.

## inputs

- `sql/init.sql`
- `backend/examine-generator/**`

## outputs

- `backend/examine-module/src/main/java/com/unique/examine/module/base/**`
- `backend/examine-upload/src/main/java/com/unique/examine/upload/base/**`
- `backend/examine-module/src/main/resources/mapper/base/**`
- `backend/examine-upload/src/main/resources/mapper/base/**`

## scope

### Do

- Generate base files for module config, dynamic runtime, import/export, attachment, and upload tables.

### Do Not

- Do not implement runtime record or import/export APIs.

## self_check_commands

```powershell
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

## acceptance

- [ ] Generated base files compile.
- [ ] Dynamic runtime tables map to `examine-module`.
- [ ] Upload storage tables map to `examine-upload`.
