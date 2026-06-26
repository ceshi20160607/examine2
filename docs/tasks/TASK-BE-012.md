# TASK-BE-012

## meta

- task_id: TASK-BE-012
- type: implementation
- owner: backend
- phase: build
- parallel_group: G3
- depends_on: [TASK-BE-003]

## goal

Implement context, tenant, organization, member, and account binding APIs.

## inputs

- `docs/api/api.md`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/base/**`

## outputs

- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/context/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/tenant/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/org/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/member/**`

## scope

### Do

- Implement system switch, tenant switch, department tree, member list, member save/update, and account binding precheck/confirm.
- Return `SystemSwitchContext` and `TenantSwitchContext` from server-side permission decisions.

### Do Not

- Do not calculate effective permissions in frontend.

## self_check_commands

```powershell
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

## acceptance

- [ ] No system business context is returned without `systemMemberId`.
- [ ] Tenant switch refreshes permission snapshot summary.

