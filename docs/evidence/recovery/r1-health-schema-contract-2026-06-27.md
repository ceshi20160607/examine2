# R1 Health Schema Contract Evidence

Time: 2026-06-27 Asia/Shanghai

## Scope

This evidence supersedes the schema part of `docs/evidence/recovery/r1-interactions-pagination-release-2026-06-27.md`.

The earlier release runtime failure reported both `schema=MISMATCH` and `redis=DOWN`. Follow-up inspection showed the target database already matches `sql/init.sql`; the mismatch came from a stale hard-coded schema contract in `HealthController`.

## Diagnosis

- MySQL at `192.168.0.211:3306/examine2` was reachable.
- Redis at `192.168.0.211:6379` was not reachable from this machine.
- The live database contains all 92 tables defined by `sql/init.sql`.
- `HealthController.requiredSchema()` contained outdated column names such as legacy department and flow-node fields, so health reported a false schema mismatch.

## Fix

Changed:

- `backend/examine-web/src/main/java/com/unique/examine/web/HealthController.java`

The required schema map now follows the current `sql/init.sql` table/column contract.

## Verification

Backend package:

```powershell
D:\dev\maven\bin\mvn.cmd -f backend/pom.xml -pl examine-web -am -DskipTests package
```

Result: `BUILD SUCCESS`

Release package:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\package-release.ps1 -JavaHome D:\dev\jdk21 -MavenPath D:\dev\maven\bin\mvn.cmd -NpmPath D:\dev\nodejs24\npm.cmd
```

Result: `PASS`

Static recovery audit:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\recovery-r0-static-audit.ps1
```

Result: `PASS`

- `promptOrConfirmCount=0`
- `duplicateSidebarTargetCount=0`
- `inertPaginationCount=0`
- `releaseDirectoryExists=true`

Release runtime:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\local-start-release.ps1 -JavaExe D:\dev\jdk21\bin\java.exe
```

Result: FAIL, and the script stopped the unhealthy backend process.

Health response:

- `database=UP`
- `schema=UP`
- `redis=DOWN`
- `redisError=RedisConnectionFailureException`

Network check:

```powershell
Test-NetConnection -ComputerName 192.168.0.211 -Port 6379
```

Result:

- `TcpTestSucceeded=False`

Local fallback check:

- `redis-server` and `redis-cli` are not available in `PATH`.
- No Windows Redis service was found.
- Docker CLI is installed, but Docker daemon is not running.
- Starting `com.docker.service` failed because the current process cannot open the service.

## Acceptance Status

Accepted:

- Schema health contract is corrected and release health now reports `schema=UP`.
- Release package and static recovery gate pass.

Blocked:

- Final release health still cannot pass until Redis at `192.168.0.211:6379` is reachable or the approved deployment configuration is changed.
- Browser/runtime user-script evidence remains pending after health is fully `UP`.
