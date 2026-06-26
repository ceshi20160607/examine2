# TASK-BE-034

## meta

- task_id: TASK-BE-034
- type: implementation
- owner: backend
- phase: build
- parallel_group: G4
- depends_on: [TASK-BE-013]

## goal

Implement SSO policy, SecretRef, Secret rotation, and no-member request APIs.

## inputs

- `docs/api/api.md`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/base/**`

## outputs

- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/sso/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/secret/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/nomember/**`

## scope

### Do

- Implement platform identity provider config, system SSO inheritance policy, org/member mapping precheck, member binding confirm, no-member approval/reject, and Secret rotation task creation.

### Do Not

- Do not return plaintext secrets.

## self_check_commands

```powershell
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

## acceptance

- [ ] Secret responses only return `SecretRef` metadata.
- [ ] SSO no-member flow cannot enter business pages before approval.

