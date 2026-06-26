# TASK-BE-003

## meta

- task_id: TASK-BE-003
- type: implementation
- owner: backend
- phase: build
- parallel_group: G2
- depends_on: [TASK-BE-002, TASK-DBA-010]

## goal

Generate platform/core base files from platform identity, permission, async, and ops tables.

## inputs

- `sql/init.sql`
- `backend/examine-generator/**`

## outputs

- `backend/examine-core/src/main/java/com/unique/examine/core/base/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/base/**`
- `backend/examine-core/src/main/resources/mapper/base/**`
- `backend/examine-plat/src/main/resources/mapper/base/**`

## scope

### Do

- Generate entity, mapper, XML, service, and base conversion stubs for platform/core tables.
- Keep generated files under `base/**`.

### Do Not

- Do not hand-edit generated files after generation.
- Do not implement `manage/**` APIs.

## self_check_commands

```powershell
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

## acceptance

- [ ] Generated base files compile.
- [ ] Generated package roots match table ownership.
- [ ] `task-accept` verdict is pass.
