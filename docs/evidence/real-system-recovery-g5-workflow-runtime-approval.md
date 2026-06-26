# REAL-G5 Workflow Runtime And Approval Persistence

Time: 2026-06-24T22:29:38+08:00

## Scope

- Replaced workflow runtime sample response with persisted `un_flow_instance`, `un_flow_approval_task`, `un_flow_approval_action_log`, `un_flow_definition`, and `un_flow_snapshot` reads.
- Added `WorkflowRuntimeMutationService` so module runtime action `record.submitApproval` creates a real workflow instance and pending approval task.
- Replaced approval action in-memory map with persisted approve/reject/transfer state transitions and idempotent action logs.
- Connected module record detail `approvalSidebar` to real workflow runtime ids.
- Added duplicate module field-code validation so repeated fields return `FIELD_VALIDATION_FAILED` instead of database 500.

## Changed Files

- `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/runtime/WorkflowRuntimeMutationService.java`
- `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/runtime/WorkflowRuntimeService.java`
- `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/approval/ApprovalService.java`
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/runtime/RuntimeRecordService.java`
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/config/ModuleConfigService.java`
- `backend/examine-module/pom.xml`

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
- Live backend started on `127.0.0.1:18121`.
- `/api/v1/health`: `status=UP`, `database=UP`, `schema=UP`.

Live workflow smoke used a newly registered system/account and real numeric ids:

```json
{
  "health": "UP",
  "accountId": "9",
  "systemId": "10",
  "moduleId": "3",
  "flowId": "2",
  "publishResult": "PUBLISHED",
  "recordId": "2",
  "submitAccepted": true,
  "sidebarInstanceId": "1",
  "sidebarTaskId": "1",
  "beforeStatus": "RUNNING",
  "beforeCurrentTasks": 1,
  "approveInstanceStatus": "APPROVED",
  "duplicateApprove": true,
  "afterStatus": "APPROVED",
  "afterCurrentTasks": 0,
  "actionLogCount": 1
}
```

Duplicate field-code validation smoke:

```json
{
  "statusCode": 400,
  "code": "FIELD_VALIDATION_FAILED",
  "message": "字段编码已存在"
}
```

## Remaining Real-System Recovery Work

- `REAL-G5-sso-no-member-persistence`
- `REAL-G5-openapi-upload-persistence`
- `REAL-G6-ai-agent-persistence`
- `REAL-G7-frontend-primary-api-integration`
- `REAL-G8-real-e2e-release`
