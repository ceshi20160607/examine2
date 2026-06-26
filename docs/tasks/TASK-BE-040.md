# TASK-BE-040

## meta

- task_id: TASK-BE-040
- type: implementation
- owner: backend
- phase: build
- parallel_group: G4
- depends_on: [TASK-BE-020, TASK-BE-030]

## goal

Implement SSO, SecretRef, OpenAPI, data source, work management, AI Agent, and ops APIs.

## inputs

- `docs/api/api.md`
- `sql/init.sql`

## outputs

- `backend/examine-app/**`
- `backend/examine-ai-work/**`

## scope

### 做

- Implement identity provider config, system SSO policy, no-member access request, SecretRotationJob, OpenAPI apps and logs, work dashboard/tasks/daily reports/config, Agent model authorization, policy, sessions, confirmations, health checks, flags, quotas, backup, archive restore, deployment, and cache policy APIs.

### 不做

- Do not allow platform Agent to open or write system business data.
- Do not expose secret plaintext.

## self_check_commands

```powershell
$env:JAVA_HOME='D:\Tools\JDK\JDK-21.0.6+7'
$env:Path="$env:JAVA_HOME\bin;D:\Tools\apache-maven-3.9.9\bin;$env:Path"
mvn -f backend/pom.xml -pl examine-app,examine-ai-work -am -DskipTests compile
```

## acceptance

- [ ] SSO links identity provider, org mapping, employee binding, and `NoMemberAccessRequest`.
- [ ] Agent confirmations are split into platform, system write, and work draft confirmation.
- [ ] Work tasks support project and plain task boundaries.
- [ ] `task-accept` verdict is pass.

## integration_test

Security, SSO, work, and Agent regression scripts consume this task.

