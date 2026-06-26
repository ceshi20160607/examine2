# TASK-BE-038

## meta

- task_id: TASK-BE-038
- type: implementation
- owner: backend
- phase: build
- parallel_group: G4
- depends_on: [TASK-BE-003, TASK-BE-033]

## goal

Implement ops governance APIs.

## inputs

- `docs/api/api.md`
- `backend/examine-core/src/main/java/com/unique/examine/core/base/**`

## outputs

- `backend/examine-core/src/main/java/com/unique/examine/core/manage/ops/**`

## scope

### Do

- Implement health checks, feature flags, quotas, rate limits, backup/restore, archive/restore, deployment rollback, and cache policy APIs.

### Do Not

- Do not run destructive operations without explicit task contract and evidence.

## self_check_commands

```powershell
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

## acceptance

- [ ] Ops actions return sync result or `AsyncTask`.
- [ ] Audit logs and trace IDs are recorded.

