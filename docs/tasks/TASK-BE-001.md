# TASK-BE-001

## meta

- task_id: TASK-BE-001
- type: implementation
- owner: backend
- phase: build
- parallel_group: G0
- depends_on: []

## goal

Create backend Maven scaffold, core protocol, error domains, trace/audit context, and web startup module.

## inputs

- `docs/api/api.md`
- `.cursor/architecture/backend-structure.md`
- `.oldbk/backend/` as read-only reference

## outputs

- `backend/pom.xml`
- `backend/examine-core/**`
- `backend/examine-web/**`

## scope

### 做

- Create Maven parent, core module, and web module.
- Implement unified response, request trace, audit context, domain error enum shell, idempotency contracts, async task status enum, and global exception handling.

### 不做

- Do not implement platform, module, flow, message, work, OpenAPI, or Agent business APIs.
- Do not write SQL.

## self_check_commands

```powershell
$env:JAVA_HOME='D:\Tools\JDK\JDK-21.0.6+7'
$env:Path="$env:JAVA_HOME\bin;D:\Tools\apache-maven-3.9.9\bin;$env:Path"
mvn -f backend/pom.xml -DskipTests compile
```

## acceptance

- [ ] Backend compiles with JDK 21.
- [ ] Core response includes `requestId`, `traceId`, and optional `auditLogId`.
- [ ] Error domains match `docs/api/api.md`.
- [ ] `task-accept` verdict is pass.

## integration_test

Downstream backend modules depend on this scaffold.

