# Backend Integration Smoke Evidence - G9 / TASK-BE-040

- Evidence time: 2026-06-24T10:29:00+08:00
- Scope: frozen backend contract integration smoke after G8 acceptance.
- Server: `http://127.0.0.1:18089`
- Logs:
  - `docs/evidence/build-g9-server.out.log`
  - `docs/evidence/build-g9-server.err.log`

## Commands

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -pl examine-web -am -DskipTests package
```

Result: PASS. `examine-web` repackaged successfully.

## HTTP Smoke Summary

- Total calls: 75
- Passed calls: 75
- Failed calls: 0
- Server startup: PASS
- `docs/evidence/build-g9-server.err.log`: empty

Covered endpoint groups:

- Health: `/api/v1/health`
- SSO and system SSO policy:
  - platform identity provider list/create/update/test/publish
  - system SSO policy get/update
  - organization sync precheck
  - member binding confirm
- SecretRef:
  - secret ref read
  - secret rotation job create/read
- OpenAPI:
  - app list/create/read/update
  - scope read/update
  - rate limit read/update
  - secret rotation
  - call log list/search
- Ops governance:
  - platform/system health check
  - feature flags
  - quotas
  - rate-limit policies
  - backup, restore drill, archive restore
  - deployments and rollback drill
  - API cache policy
- Work management:
  - dashboard
  - project search
  - project task search/create/update
  - plain task search/create/update
  - project/plain kanban query
  - daily report search/create/auto-draft
  - work config and publish check
  - task comments and events
- AI Agent:
  - platform model authorization list/create/update
  - platform session/message
  - platform confirmation boundary
  - system policy list/create/update/publish-check
  - system session/message
  - system write confirmation create/confirm/reject
  - work draft confirmation
  - audit log list

## Required Assertions

| Assertion | Result |
|---|---|
| SSO links identity provider, org mapping, employee binding, and member binding confirmation | PASS |
| SecretRef API does not return plaintext secret material | PASS |
| OpenAPI secret rotation returns a rotation job path instead of plaintext material | PASS |
| Platform Agent denies system business target/write boundary | PASS |
| Agent confirmations are split into platform, system write, and work draft confirmation flows | PASS |
| System Agent write starts as `WAITING_HUMAN_CONFIRM` before confirm/reject actions | PASS |
| Work Agent draft confirmation requires manual confirmation | PASS |
| Work project task returns `taskType=PROJECT` and keeps `projectId` | PASS |
| Work plain task returns `taskType=PLAIN` without `projectId` | PASS |
| Ops governance covers health, backup, restore drill, archive restore, rollback, and cache policy | PASS |

## Contract Boundary Note

`TASK-BE-040.md` mentions "data source" in the high-level goal, but the frozen API contract (`docs/api/api.md`) has no standalone data-source endpoint section. A repository search found "data source" only in the early frontend draft mapping, where it is described as part of dashboard/external-app configuration. This smoke run therefore did not invent new data-source APIs after API freeze; the currently frozen integration coverage is represented through OpenAPI app/source governance and configuration-oriented endpoints.

## Endpoint Samples

Representative successful calls:

- `POST /api/v1/platform/identity-providers`
- `PATCH /api/v1/systems/sys_vehicle/sso/policies`
- `POST /api/v1/systems/sys_vehicle/sso/org-sync/precheck`
- `POST /api/v1/secrets/sec_openapi_vehicle/rotation-jobs`
- `POST /api/v1/systems/sys_vehicle/openapi/apps`
- `POST /api/v1/systems/sys_vehicle/openapi/apps/{externalAppId}/rotate-secret`
- `POST /api/v1/platform/ops/health-check`
- `POST /api/v1/platform/ops/backups/{backupId}/restore-drill`
- `POST /api/v1/systems/sys_vehicle/work/project-tasks`
- `POST /api/v1/systems/sys_vehicle/work/plain-tasks`
- `POST /api/v1/systems/sys_vehicle/work/kanban/query`
- `POST /api/v1/platform/agent/platform-agent-confirm`
- `POST /api/v1/systems/sys_vehicle/agent/system-agent-write-confirmations`
- `POST /api/v1/systems/sys_vehicle/work/agent/work-agent-draft-confirm`

Verdict: PASS.
