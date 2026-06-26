# Real System Recovery G1 Database Startup Evidence

Time: 2026-06-24 16:42 Asia/Shanghai

## Scope

Validate that the rebuilt release jar can start with a real MySQL datasource and that `/api/v1/health` performs an actual database probe.

## Environment

- JDK: `D:\java\jdk\jdk21`
- Maven: `D:\java\apache-maven-3.8.5\bin\mvn.cmd`
- Release backend: `release/unexamine-0.0.1-SNAPSHOT/backend`
- Test port: `18110`
- Database host checked:
  - `127.0.0.1:3306` = not listening
  - `192.168.0.211:3306` = reachable

## Code Change

`backend/examine-web/src/main/java/com/unique/examine/web/HealthController.java` now uses `JdbcTemplate` and executes `SELECT 1`.

Expected health data:

```json
{
  "status": "UP",
  "database": "UP"
}
```

If the probe fails, the response reports:

```json
{
  "status": "UP",
  "database": "DOWN",
  "databaseError": "<exception class>"
}
```

## Startup Verification

Runtime environment used:

```powershell
$env:UNEXAMINE_SERVER_PORT = '18110'
$env:UNEXAMINE_DB_URL = 'jdbc:mysql://192.168.0.211:3306/examine1?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true'
$env:UNEXAMINE_DB_USERNAME = 'examine'
$env:UNEXAMINE_DB_PASSWORD = 'examine'
$env:UNEXAMINE_LOG_FILE = './logs/real-g1-db-health-app.log'
```

Result:

```json
{
  "Started": true,
  "ProcessExited": false,
  "LastResult": {
    "code": "SUCCESS",
    "data": {
      "status": "UP",
      "database": "UP"
    }
  }
}
```

Relevant log lines:

```text
Tomcat started on port 18110 (http)
HikariPool-1 - Starting...
HikariPool-1 - Added connection com.mysql.cj.jdbc.ConnectionImpl@...
HikariPool-1 - Start completed.
```

Cleanup:

```text
127.0.0.1:18110 TcpTestSucceeded=False
```

## Package Artifact

Rebuilt after the health endpoint change:

```text
release/unexamine-0.0.1-SNAPSHOT/backend/examine-web.jar = 27196097 bytes
release/unexamine-0.0.1-SNAPSHOT.zip = 24561186 bytes
```

## Remaining Risk

The jar starts with a real datasource and probes the database, but this still does not mean the business system is complete.

Remaining sample-backed areas include manage services for auth/context/RBAC/module runtime/workflow/messages/work/Agent and the frontend mock shell. These remain open in `docs/tasks/real-system-replan.md`.
