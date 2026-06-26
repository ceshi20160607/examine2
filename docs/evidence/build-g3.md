# Build G3 Evidence

## Scope

- `TASK-BE-010`: authentication and account profile APIs.
- `TASK-BE-011`: platform system lifecycle APIs.
- `TASK-BE-012`: system switch, tenant switch, organization, member, and account binding APIs.
- `TASK-BE-013`: role, permission, effective permission, and preview APIs.
- `TASK-BE-033`: audit log and async task APIs.

## Dependency Coordination

G3 API controllers live in business modules, so the following POM coordination was required:

- `backend/examine-plat/pom.xml`: added Spring Web dependency for controllers.
- `backend/examine-message-log/pom.xml`: added Spring Web dependency for controllers.
- `backend/examine-web/pom.xml`: added dependencies on `examine-plat` and `examine-message-log` so the web app can scan and serve G3 controllers.
- `backend/examine-web/src/main/java/com/unique/examine/web/config/RequestContextFilter.java`: renamed the Spring bean to avoid conflict with Spring Boot's built-in `requestContextFilter`, and set top-level `auditLogId` for non-read HTTP methods.

This is recorded as G3 API-layer dependency coordination because the G3 task sheets own runtime API endpoints, and the web module already scans `com.unique.examine`.

## Output Summary

Platform module:

- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/auth/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/account/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/system/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/context/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/tenant/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/org/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/member/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/role/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/permission/**`

Message/log module:

- `backend/examine-message-log/src/main/java/com/unique/examine/messagelog/manage/audit/**`
- `backend/examine-message-log/src/main/java/com/unique/examine/messagelog/manage/task/**`

## Endpoint Coverage

Static endpoint scan found 43 controller mappings, including:

- Auth/account: login, logout, token refresh, register-with-system, password reset, current profile, profile/password update, login logs.
- Platform systems: list, create, detail, update, enable, disable, delete, restore, platform health.
- Context/tenant/org/member: system switch options, system switch, current system, tenant switch, tenant CRUD, department tree/create, member list/create/update/bind-account, member bindings.
- Role/permission: platform roles, system roles, member assignment, role permissions, effective permissions, permission preview.
- Logs/tasks: platform/system log search/detail, async task search/detail/cancel/retry.

## Compile And Package

Command:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -pl examine-web -am -DskipTests package
```

Result: PASS.

The package command rebuilt `examine-core`, `examine-plat`, `examine-message-log`, and `examine-web`, and repackaged the executable web jar.

## Startup Smoke

Temporary server command:

```powershell
java -jar backend/examine-web/target/examine-web-0.0.1-SNAPSHOT.jar --server.port=18080
```

Smoke requests:

- `GET /api/v1/health`
- `POST /api/v1/auth/login`
- `POST /api/v1/platform/system-switch`
- `POST /api/v1/platform/logs/search?pageNo=1&pageSize=20`
- `GET /api/v1/tasks/TASK-20260623-001`

Result:

```json
{
  "health": "SUCCESS",
  "login": "SUCCESS",
  "loginAudit": "aud_trc_dd60ee51-b698-4662-a6ef-b4e8e56cd18f",
  "switchMember": "member_001",
  "logs": "SUCCESS",
  "taskStatus": "SUCCESS"
}
```

Startup logs:

- `docs/evidence/build-g3-server.out.log`
- `docs/evidence/build-g3-server.err.log`

No G3 server process was left running after the smoke check.

## Diff Check

Command:

```powershell
git diff --check
```

Result: PASS with LF/CRLF warnings only on tracked project/session/design files already noted in previous evidence.

## Known Limits

- G3 is contract-first and currently uses deterministic in-memory/demo responses. Durable persistence and deeper service rules are expected to be tightened in later integration slices.
- Request/response BO/VO records currently do not use Swagger `@Schema` because the scaffold has not introduced the Swagger annotations dependency yet. This should be addressed when API documentation dependencies are formalized.

## Verdict

G3 self-check passed and is ready for independent task acceptance.
