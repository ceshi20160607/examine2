# TASK-BE-038 G4 Backend Ops Governance Evidence

## Scope

- Worker: G4 backend worker
- Task: `TASK-BE-038`
- Output scope:
  - `backend/examine-core/src/main/java/com/unique/examine/core/manage/ops/**`
  - `backend/examine-core/pom.xml`
  - `docs/evidence/build-g4-be038.md`

## Implementation Summary

- Added `OpsGovernanceController`, `OpsGovernanceService`, and `OpsGovernanceModels`.
- Added `spring-boot-starter-web` to `examine-core` so the new controller compiles in the core module.
- Implemented contract-first, non-destructive ops APIs for:
  - health checks
  - feature flags
  - capacity quotas
  - rate limit policies
  - backup task creation
  - restore drill task creation
  - archive/restore request task creation
  - deployment listing and rollback task creation
  - API cache policy query/update
- Write and dangerous operations return synchronous VO results or `AsyncTaskView` placeholders.
- Dangerous operations are dry-run/task placeholders only and do not delete, restore, rollback, or mutate real infrastructure.
- Returned payloads include `traceId` and `auditLogId`; POST/PATCH responses also receive top-level audit IDs from the existing request context filter when running through web.

## Endpoint List

- `POST /api/v1/platform/ops/health-check`
- `POST /api/v1/systems/{systemId}/ops/health-check`
- `GET /api/v1/platform/ops/feature-flags`
- `PATCH /api/v1/platform/ops/feature-flags/{flagId}`
- `GET /api/v1/platform/ops/quotas`
- `PATCH /api/v1/platform/ops/quotas/{quotaId}`
- `GET /api/v1/platform/ops/rate-limit-policies`
- `PATCH /api/v1/platform/ops/rate-limit-policies/{policyId}`
- `POST /api/v1/platform/ops/backups`
- `POST /api/v1/platform/ops/backups/{backupId}/restore-drill`
- `POST /api/v1/platform/ops/archive-restore-requests`
- `GET /api/v1/platform/ops/deployments`
- `POST /api/v1/platform/ops/deployments/{deploymentId}/rollback`
- `GET /api/v1/platform/ops/api-cache-policy`
- `PATCH /api/v1/platform/ops/api-cache-policy`

## Command Results

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -f backend/pom.xml -pl examine-core -am -DskipTests compile
```

Result: PASS. Maven returned `BUILD SUCCESS`; it reported classes were up to date.

Extra compiler verification:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -f backend/pom.xml -pl examine-core -am -DskipTests clean compile
```

Result: PASS. Maven returned `BUILD SUCCESS` and compiled 74 source files with Java 21.

Diff check:

```powershell
git diff --check
```

Result: PASS with LF/CRLF warnings only on pre-existing tracked files:

- `.cursor/session/issues/registry.jsonl`
- `docs/design/pre-coding-readiness.md`
- `docs/design/user-approval.md`

Additional untracked-file whitespace/conflict-marker scan for this task's files returned no matches.

## Residual Risks

- `docs/api/api.md` lists the main ops endpoints but does not explicitly name rate-limit policy endpoints. TASK-BE-038 and the generated `OpsRateLimitPolicy` base entity require rate limit strategy APIs, so this implementation adds `/api/v1/platform/ops/rate-limit-policies`.
- Current G3/G4 backend pattern is contract-first sample services, not persistent CRUD. The ops service returns auditable contract payloads and task placeholders, but does not yet write real audit rows or async task rows to storage.
- `git diff` does not show the new backend files normally because the repository currently has `backend/` as an untracked directory from prior parallel worker output.
