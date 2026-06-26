# TASK-BE-030

## meta

- task_id: TASK-BE-030
- type: implementation
- owner: backend
- phase: build
- parallel_group: G5
- depends_on: [TASK-BE-005, TASK-BE-020]

## goal

Implement flow definition and workflow configuration APIs.

## inputs

- `docs/api/api.md`
- `backend/examine-flow/src/main/java/com/unique/examine/flow/base/**`
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/config/**`

## outputs

- `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/definition/**`

## scope

### 做

- Implement flow definition list/detail/create/update, node library, node configs, canvas edges, condition labels, simulation, publish check, version snapshot, and impact analysis.
- Cover approval, condition, field update, external API, timer, timeout reminder, and end nodes with dedicated property payloads.

### 不做

- Do not implement workflow runtime instances, approval actions, todo handling, messages, notification templates, audit logs, or async task retry/cancel.
- Do not implement module record storage or frontend pages.

## self_check_commands

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -f backend/pom.xml -pl examine-flow -am -DskipTests compile
```

## acceptance

- [ ] Flow canvas returns nodes, edges, branch labels, and node-specific property payloads.
- [ ] Publish check returns blocking items, warnings, impact refs, traceId, and auditLogId.
- [ ] Simulation returns step traces and condition decisions.
- [ ] `task-accept` verdict is pass.

## integration_test

QA validates flow publishing and later approval runtime tasks depend on this definition contract.
