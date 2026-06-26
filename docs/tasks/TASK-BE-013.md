# TASK-BE-013

## meta

- task_id: TASK-BE-013
- type: implementation
- owner: backend
- phase: build
- parallel_group: G3
- depends_on: [TASK-BE-003]

## goal

Implement role, permission, effective permission, and preview APIs.

## inputs

- `docs/api/api.md`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/base/**`

## outputs

- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/role/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/permission/**`

## scope

### Do

- Implement role CRUD, member assignment, permission save, effective permission query, and permission preview.
- Include field readable/writable/masked/hidden decisions, data scope, deny policies, and disabled reasons.

### Do Not

- Do not hard-code backend entry access except built-in root/super-admin rules.

## self_check_commands

```powershell
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

## acceptance

- [ ] Permission snapshots are server-side.
- [ ] Preview logs include explanation and trace ID.

