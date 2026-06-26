# REAL-G5 SSO And No-Member Persistence

Time: 2026-06-24T22:41:00+08:00

## Scope

- Replaced sample platform identity provider responses with persisted `un_plat_identity_provider` reads and writes.
- Replaced sample system SSO policy responses with persisted `un_plat_system_sso_policy` reads and writes.
- Persisted SSO external member binding confirmations into `un_plat_sso_binding`.
- Replaced sample no-member access requests with persisted `un_plat_no_member_access_request` lifecycle.
- No-member approval now updates the request and creates or reuses `un_plat_account_member_binding` plus SSO binding when member, role, and data scope are assigned.
- No-member create supports idempotent reuse by request number derived from `idempotencyKey`.

## Changed Files

- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/sso/SsoService.java`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/nomember/NoMemberService.java`

## Verification

Environment from `docs/user_setting.md`:

- DB: `jdbc:mysql://192.168.0.211:3306/examine2`
- DB user: `examine`
- JDK: `D:\java\jdk\jdk21`
- Maven: `D:\java\apache-maven-3.8.5`

Commands:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -pl examine-web -am -DskipTests compile
mvn -pl examine-web -am -DskipTests package
```

Results:

- Maven compile: PASS.
- Maven package: PASS.
- Live backend started on `127.0.0.1:18122`.
- `/api/v1/health`: `status=UP`, `database=UP`, `schema=UP`.

Live SSO/no-member smoke used a newly registered system/account and real persisted ids:

```json
{
  "health": "UP",
  "accountId": "10",
  "systemId": "11",
  "providerId": "idp_oidc_b22048f2",
  "providerTestPassed": true,
  "providerPublishStatus": "PUBLISHED",
  "providerPersisted": "idp_oidc_b22048f2",
  "policyStatus": "ACTIVE",
  "policyProviderCount": 1,
  "precheckStatus": "PRECHECK_QUEUED",
  "bindingStatus": "BOUND",
  "bindingId": "1",
  "noMemberRequestId": "1",
  "noMemberInitialStatus": "SUBMITTED",
  "noMemberListTotal": 1,
  "noMemberApprovedStatus": "APPROVED",
  "noMemberAccessAllowed": true,
  "accountMemberBindingId": "12"
}
```

No-member create idempotency smoke:

```json
{
  "first": "2",
  "second": "2",
  "same": true,
  "status": "SUBMITTED"
}
```

## Remaining Real-System Recovery Work

- `REAL-G5-openapi-upload-persistence`
- `REAL-G6-ai-agent-persistence`
- `REAL-G7-frontend-primary-api-integration`
- `REAL-G8-real-e2e-release`
