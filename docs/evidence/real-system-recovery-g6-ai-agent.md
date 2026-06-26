# REAL-G6 AI Agent Persistence Evidence

## Scope

- Replaced `AgentService` sample responses with persisted AI Agent behavior.
- Persisted platform model authorizations in `un_agent_model_authorization`.
- Persisted model credential metadata as `un_sys_secret_ref` SecretRef entries. No plaintext credential is returned by the API.
- Persisted system Agent policies in `un_agent_policy`, including module scope, field scope, action scope, data scope expression, outbound limit and desensitization policy JSON.
- Persisted platform/system Agent sessions in `un_agent_session`.
- Persisted Agent confirmations in `un_agent_confirmation`, including platform confirmation, system write confirmation and work draft confirmation.
- Persisted Agent audit evidence in `un_agent_audit_log`, including conversation snapshot, tool calls, desensitization result, outbound snapshot, traceId and auditLogId.
- Fixed confirmation id generation so different session ids no longer collapse to the same `agentsession` prefix.

## Verification Commands

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -pl examine-web -am -DskipTests compile
mvn -pl examine-web -am -DskipTests package
```

Both compile and package passed with JDK 21.

## Runtime Smoke

Backend jar was started on `127.0.0.1:18124` with:

```powershell
$env:UNEXAMINE_SERVER_PORT='18124'
$env:UNEXAMINE_DB_URL='jdbc:mysql://192.168.0.211:3306/examine2?characterEncoding=utf8&useSSL=false&zeroDateTimeBehavior=convertToNull&tinyInt1isBit=false&serverTimezone=Asia/Shanghai&useAffectedRows=true&allowPublicKeyRetrieval=true'
$env:UNEXAMINE_DB_USERNAME='examine'
$env:UNEXAMINE_DB_PASSWORD='examine'
```

Health check result:

```json
{
  "status": "UP",
  "database": "UP",
  "schema": "UP"
}
```

AI Agent smoke result:

```json
{
  "health": "UP",
  "accountId": "16",
  "systemId": "17",
  "modelAuthorizationId": "2",
  "modelAuthorizationPersisted": true,
  "policyId": "2",
  "policyPersisted": true,
  "publishPassed": true,
  "platformSessionId": "agent_session_platform_trcc8f42de96",
  "platformAuditId": "audit_agent_trcac9933100",
  "platformConfirmed": "CONFIRMED",
  "generatedPlatformObjects": 1,
  "systemSessionId": "agent_session_system_trc75fc62d18",
  "systemAuditId": "audit_agent_trc05d415900",
  "writeConfirmationFromMessage": "confirm_write_agentsc62d18",
  "workConfirmationFromMessage": "confirm_work_agentsc62d18",
  "confirmationIdsDistinct": true,
  "writePreviewId": "confirm_write_agentsc62d18",
  "writePreviewStatus": "WAITING_HUMAN_CONFIRM",
  "writeConfirmStatus": "CONFIRMED",
  "workDraftStatus": "CONFIRMED",
  "auditTotal": 6
}
```

The smoke covered:

- Register a new account and system.
- Create persisted platform model authorization.
- Query persisted model authorization page.
- Create persisted system Agent policy.
- Query persisted policy page.
- Run policy publish check.
- Create platform Agent session.
- Send platform Agent message and persist audit/confirmation.
- Confirm platform-only generated action.
- Create system Agent session in current member context.
- Send system Agent message and persist write/work confirmations.
- Create system write confirmation preview.
- Confirm system write confirmation.
- Confirm work draft.
- Query persisted Agent audit logs by keyword.
- Confirm generated write and work confirmation ids are distinct.

The test process was stopped afterwards and port `18124` was released.

## Remaining Recovery Work

- `REAL-G7-frontend-primary-api-integration`
- `REAL-G8-real-e2e-release`
