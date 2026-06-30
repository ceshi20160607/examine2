# R1 Release Context Smoke Evidence

Time: 2026-06-27 Asia/Shanghai

## Scope

This evidence covers API-level release verification for:

- health readiness
- default admin login
- Redis-backed token authentication
- platform system creation
- system switch context creation
- current system context persistence
- tenant switch context persistence

It uses the current release package with a local temporary Redis container because the configured production Redis at `192.168.0.211:6379` is not reachable from this machine.

## Fix

Changed:

- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/context/SystemContextService.java`

Problem found during smoke:

- `POST /api/v1/platform/system-switch` returned the requested target system and tenant.
- `GET /api/v1/context/current-system` then returned the first account binding instead of the just-switched target.
- This meant server-side current system context was not persisted across requests.

Correction:

- `switchSystem` now stores the current `accountMemberBindingId` in Redis.
- `switchTenant` also updates the stored current binding.
- `currentSystem` now reads the stored binding first and falls back to the first enabled binding only when no current binding exists.

## Repeatable Script

Added:

```powershell
scripts/recovery-r1-release-context-smoke.ps1
```

The script fails if any of these are false:

- `/api/v1/health` reports `status/database/schema/redis` all `UP`.
- `admin / 123123aa` login returns an access token.
- authenticated `/api/v1/platform/systems` create succeeds.
- created system appears in `/api/v1/platform/system-switch/options`.
- switching to the created system succeeds.
- `/api/v1/context/current-system` returns the switched system and tenant.
- tenant switch succeeds.
- `/api/v1/context/current-system` still returns the switched system and tenant after tenant switch.

## Verification Commands

Temporary Redis:

```powershell
docker run -d --name unexamine-recovery-redis -p 6379:6379 redis:latest redis-server --requirepass 123456
```

Backend compile:

```powershell
D:\dev\maven\bin\mvn.cmd -f backend/pom.xml -pl examine-web -am -DskipTests package
```

Result: `BUILD SUCCESS`

Release package:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\package-release.ps1 -JavaHome D:\dev\jdk21 -MavenPath D:\dev\maven\bin\mvn.cmd -NpmPath D:\dev\nodejs24\npm.cmd
```

Result: `PASS`

Release startup with local Redis override:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\local-start-release.ps1 -JavaExe D:\dev\jdk21\bin\java.exe -RedisHost 127.0.0.1 -RedisPort 6379 -RedisPassword 123456 -RedisDatabase 10
```

Result: exit code `0`

Context smoke:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\recovery-r1-release-context-smoke.ps1
```

Result:

```json
{
  "status": "PASS",
  "healthStatus": "UP",
  "healthDatabase": "UP",
  "healthSchema": "UP",
  "healthRedis": "UP",
  "loginCode": "SUCCESS",
  "accountId": "25",
  "accountName": "admin",
  "platformRoles": ["PLATFORM_ROOT"],
  "createdSystemId": "60",
  "createdTenantId": "61",
  "switchCode": "SUCCESS",
  "switchSystemId": "60",
  "switchTenantId": "61",
  "currentCode": "SUCCESS",
  "currentSystemId": "60",
  "currentTenantId": "61",
  "tenantSwitchCode": "SUCCESS",
  "currentAfterTenantSystemId": "60",
  "currentAfterTenantTenantId": "61"
}
```

## Acceptance Status

Accepted for API-level R1 smoke:

- Release package can reach full health when Redis is reachable.
- Admin login and Redis-backed Bearer token auth work.
- System switch context now persists across requests.

Not final acceptance:

- Production/default release health still depends on `192.168.0.211:6379`, which remains unreachable from this machine.
- Browser screenshots and role/permission UI evidence are still required for full R1 acceptance.
