# TASK-BE-010

## meta

- task_id: TASK-BE-010
- type: implementation
- owner: backend
- phase: build
- parallel_group: G3
- depends_on: [TASK-BE-003]

## goal

Implement authentication and account profile APIs.

## inputs

- `docs/api/api.md`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/base/**`

## outputs

- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/auth/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/account/**`

## scope

### Do

- Implement login, logout, token refresh, register-with-system, password reset request/confirm, account profile, profile update, password update, and personal login logs.
- Return login response with account/profile/default landing/SSO binding summary plus request and trace metadata.

### Do Not

- Do not implement platform system lifecycle; owned by `TASK-BE-011`.
- Do not implement tenant/member/context APIs; owned by `TASK-BE-012`.
- Do not implement role/effective permission APIs; owned by `TASK-BE-013`.
- Do not implement frontend pages.

## self_check_commands

```powershell
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

## acceptance

- [ ] Auth/account APIs match frozen endpoints.
- [ ] Register-with-system returns the initial system and super-admin bootstrap result.
- [ ] Login and account update responses include request and trace metadata.
- [ ] `task-accept` verdict is pass.

## integration_test

Frontend auth shell and login/register/password-reset flows consume these APIs.
