# Real System Recovery G2 Auth And Schema Evidence

Time: 2026-06-24 17:08 Asia/Shanghai

## Scope

Start replacing contract-first/sample authentication with persisted platform identity behavior, and prevent database connectivity from being mistaken for full system readiness.

## Code Changes

- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/auth/AuthService.java`
  - Replaced sample login/register responses.
  - Login now queries `un_plat_account`, verifies BCrypt password, resolves system/tenant/member binding, returns persisted ids and role codes, updates `last_login_at`, and writes `un_audit_login_log`.
  - Register-with-system now creates persisted account, system, default tenant, system member, system super admin role, role-member relation, and account-member binding.
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/auth/AuthSecurityConfig.java`
  - Added BCrypt `PasswordEncoder`.
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/auth/AuthErrorCode.java`
  - Added explicit auth/bootstrap error codes.
- `backend/examine-plat/pom.xml`
  - Added `examine-message-log`, `spring-security-crypto`, and `spring-tx` dependencies.
- `backend/examine-web/src/main/java/com/unique/examine/web/config/GlobalExceptionHandler.java`
  - Logs business and unhandled exceptions instead of swallowing root causes.
- `backend/examine-web/src/main/java/com/unique/examine/web/HealthController.java`
  - Health now distinguishes database connectivity from required schema readiness.
  - Returns `schema=MISMATCH` and `missingSchema` when required tables/columns are absent.
- `sql/migrations/20260624_legacy_identity_schema_compat.sql`
  - Added migration SQL for older identity-schema databases.
- `docs/deployment/package-and-service.md`
  - Added database initialization and migration notes.

## Verification

Backend compile:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -f backend\pom.xml -pl examine-web -am -DskipTests compile
```

Result:

```text
BUILD SUCCESS
```

Backend package:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -f backend\pom.xml -pl examine-web -am -DskipTests package
```

Result:

```text
BUILD SUCCESS
```

Runtime health against `jdbc:mysql://192.168.0.211:3306/unexamine`:

```json
{
  "status": "DEGRADED",
  "database": "UP",
  "schema": "MISMATCH",
  "missingSchema": [
    "un_plat_account.account_name",
    "un_plat_account.deleted",
    "un_plat_tenant.tenant_code",
    "un_plat_tenant.tenant_name",
    "un_plat_tenant.deleted",
    "un_plat_member.system_id",
    "un_plat_member.tenant_id",
    "un_plat_member.member_name",
    "un_plat_member.deleted",
    "un_audit_login_log.account_id",
    "un_audit_login_log.auth_method",
    "un_audit_login_log.login_result",
    "un_audit_login_log.request_id",
    "un_audit_login_log.trace_id",
    "un_plat_system.system_code",
    "un_plat_system.system_name",
    "un_plat_system.deleted",
    "un_plat_role_member.role_id",
    "un_plat_role_member.account_id",
    "un_plat_role_member.system_member_id",
    "un_plat_account_member_binding.account_id",
    "un_plat_account_member_binding.system_id",
    "un_plat_account_member_binding.tenant_id",
    "un_plat_account_member_binding.system_member_id",
    "un_plat_account_member_binding.binding_status",
    "un_plat_role.scope",
    "un_plat_role.system_id",
    "un_plat_role.tenant_id",
    "un_plat_role.role_code",
    "un_plat_role.role_name",
    "un_plat_role.role_type",
    "un_plat_role.builtin",
    "un_plat_role.deleted"
  ]
}
```

## Database Finding

The available MySQL account can connect and read metadata, but it cannot create databases, create tables, or alter tables.

Observed failures:

```text
CREATE DATABASE ... => Access denied for user 'examine'@'%'
ALTER TABLE un_plat_account ... => ALTER command denied
CREATE TABLE un_plat_member ... => CREATE command denied
```

Current accessible schemas are older structures:

- `examine`
- `examine1`
- `unexamine`

They do not match current `sql/init.sql`.

## Current Status

REAL-G2 is in progress, not complete.

Code has moved past sample auth, but true runtime acceptance is blocked until a database with the current schema exists or a privileged account runs:

```text
sql/init.sql
```

for a fresh deployment, or:

```text
sql/migrations/20260624_legacy_identity_schema_compat.sql
```

for the older identity-schema database.

After schema is fixed, rerun register/login/system-switch persistence tests before accepting REAL-G2.
