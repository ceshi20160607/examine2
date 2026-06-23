# Backend smoke — 2026-06-22

**Environment:** Windows; `JAVA_HOME=D:\dev\jdk21` (directory present, used for Maven).

## Compile

| Step | Result |
|------|--------|
| `powershell -File scripts/build.ps1` | **Failed** during `mvn clean compile` on module `examine-web`: `maven-clean-plugin` could not delete `backend/examine-web/target/unexamine.jar` (file in use — consistent with a running backend). Modules `examine-core` through `examine-generator` compiled successfully before failure. |
| Follow-up: `mvn compile -pl examine-web -am -DskipTests` (no `clean`) | **Success** (all 8 reactor modules including `examine-web`). |

`build.ps1` did not reach the frontend npm build because the backend step exited non-zero.

## Runtime / health

| Check | Result |
|-------|--------|
| `GET http://127.0.0.1:9999/actuator/health` | **200** — body: `{"status":"UP"}` |
| `GET http://127.0.0.1:9999/actuator/health/db` | **200** — body: `{"status":"UP"}` (DB health indicator reports up while process is running) |

Backend was already listening on port **9999**; no new jar start was required for this smoke.

## Artifact path

If starting manually after a full package build:

- `backend/examine-web/target/unexamine.jar` (~41.6 MB, last modified 2026-06-22 13:09:58 local)

## Database (`application.yml`)

Configured in `backend/examine-web/src/main/resources/application.yml`:

- **URL:** `jdbc:mysql://192.168.0.211:3306/examine1?...` (MySQL, `serverTimezone=Asia/Shanghai`, `useSSL=false`, `allowPublicKeyRetrieval=true`)
- **Username:** `examine`
- **Driver:** `com.mysql.cj.jdbc.Driver`

Runtime health `UP` implies the running instance could reach this datasource from this host at smoke time. Maven **tests were not run** (`-DskipTests` / build stopped before test phase); DB-dependent unit/integration tests were not verified in this run.
