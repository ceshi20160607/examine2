# TASK-DBA-008

## meta

- task_id: TASK-DBA-008
- type: implementation
- owner: dba
- phase: build
- parallel_group: G0
- depends_on: []

## goal

Create SecretRef, SSO provider security, OpenAPI, and AI Agent schema fragments.

## inputs

- `docs/api/api.md`
- `.cursor/agents/dba.md`

## outputs

- `sql/fragments/008-secret-openapi-agent.sql`
- `docs/database/secret-openapi-agent.md`

## scope

### 做

- Define SecretRef, typed secret reference metadata, SecretRotationJob, identity provider config, system SSO policy, OpenAPI app/scope/secret reference/call log, model authorization, Agent policy, Agent session, Agent confirmation, Agent tool-call/audit detail, and model quota reference tables.
- Include version, rotation status, no-plaintext constraints, scope boundaries, quota/version fields, trace/audit fields, and comments.

### 不做

- Do not store secret plaintext.
- Do not define no-member access request tables owned by `TASK-DBA-001`.
- Do not define generic async task, feature flag, backup, deployment, or cache policy tables.
- Do not write backend code or SQL merge output.

## self_check_commands

```powershell
Test-Path sql/fragments/008-secret-openapi-agent.sql
Test-Path docs/database/secret-openapi-agent.md
```

## acceptance

- [ ] Secret references use type, version, expiry, rotation status, and last-used metadata without plaintext columns.
- [ ] OpenAPI app, scopes, secret refs, rate-limit references, and call logs are traceable by app, scope, result, time range, and trace ID.
- [ ] Platform Agent, system Agent, and work Agent confirmation/audit storage boundaries are separate and permission-snapshot aware.
- [ ] `task-accept` verdict is pass.

## integration_test

QA validates SSO provider config, secret rotation, OpenAPI call log, Agent policy, Agent confirmation, and Agent audit boundary scenarios.
