# Real System Recovery G5 Permission Flow Notification Evidence

Time: 2026-06-24 19:36 Asia/Shanghai

## Scope

Continue real-system recovery against the `examine2` database configured in `docs/user_setting.md`.

This batch replaces additional sample behavior with persisted behavior for:

- system role permission save/query/effective/preview;
- flow definition, canvas, publish check and published snapshots;
- notification template configuration and delivery log query.

## Code Changes

- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/permission/PermissionService.java`
  - Replaced fixed permission payloads with persisted `un_plat_permission_version`, `un_plat_role_permission`,
    `un_plat_role_field_permission`, `un_plat_data_scope_rule`, `un_plat_deny_policy`,
    `un_plat_effective_permission_snapshot` and `un_plat_permission_preview_log` behavior.
  - Added system super-admin effective permission wildcard handling.
  - Added persisted preview audit log.
- `backend/examine-flow/pom.xml`
  - Added platform module dependency so flow APIs can resolve real system/member context.
- `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/definition/FlowDefinitionService.java`
  - Replaced sample flow list/detail/canvas/publish/snapshot behavior with `un_flow_definition`, `un_flow_node`,
    `un_flow_edge`, `un_flow_snapshot` and `un_flow_simulation_log` persistence.
  - Kept node library and property panel metadata as design-time dictionaries.
- `backend/examine-message-log/src/main/java/com/unique/examine/messagelog/manage/notification/NotificationTemplateService.java`
  - Replaced sample notification templates with persisted `un_message_notification_template` CRUD and publish check.
- `backend/examine-message-log/src/main/java/com/unique/examine/messagelog/manage/notification/MessageDeliveryLogService.java`
  - Replaced fixed delivery log samples with persisted `un_message_delivery_log` query joined to `un_message_message`.

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

Runtime jar started on `127.0.0.1:18114` using:

- `UNEXAMINE_DB_URL=jdbc:mysql://192.168.0.211:3306/examine2?...`
- `UNEXAMINE_DB_USERNAME=examine`
- `UNEXAMINE_DB_PASSWORD=examine`

Health:

```json
{
  "health": "UP",
  "schema": "UP"
}
```

Permission smoke:

```json
{
  "accountId": "5",
  "systemId": "6",
  "roleId": "8",
  "savedVersion": "perm_1782300154894_trcdfcbc",
  "getCreateAllowed": true,
  "getExportAllowed": false,
  "getSecretField": "MASKED",
  "denyAllowed": false,
  "allowAllowed": true,
  "effectiveWildcard": true,
  "effectiveSnapshotId": "eps_2b5b1854e014460b85476379f2408dac"
}
```

Flow definition smoke:

```json
{
  "accountId": "6",
  "systemId": "7",
  "flowId": "1",
  "canvasNodes": 7,
  "canvasEdges": 6,
  "publishCheckPassed": true,
  "publishResult": "PUBLISHED",
  "version": "flow_v1782300513564",
  "snapshotsCount": 1,
  "detailNodeCount": 7,
  "detailEdgeCount": 6
}
```

Notification smoke:

```json
{
  "accountId": "7",
  "systemId": "8",
  "templateId": "1",
  "templateCode": "tpl_smoke_1782300842029",
  "channels": 2,
  "targetType": "business_record",
  "publishCheckPassed": true,
  "listTotal": 1,
  "deliveryLogTotal": 0
}
```

`deliveryLogTotal=0` is expected because this smoke validates real query behavior without fabricating a delivery record.

## Remaining Risk

The following services still need real-system recovery before claiming full system completion:

- workflow runtime instance/task/action execution beyond definition snapshots;
- SSO/no-member access request persistence;
- OpenAPI external app/scopes/secrets/call-log persistence;
- upload storage and import/export execution beyond metadata;
- AI Agent authorization/policy/session/confirmation persistence;
- broader frontend primary API integration and end-to-end user script.
