# G8 / TASK-BE-037 AI Agent APIs Evidence

## Scope

- Implemented platform/system/work AI Agent APIs in `backend/examine-ai-work/src/main/java/com/unique/examine/aiwork/manage/agent/**`.
- Kept platform confirmation, system write confirmation, and work draft confirmation as separate contracts.
- Did not allow platform Agent to open or write system business data.
- Did not bypass human confirmation.

## Implemented Endpoints

- `GET /api/v1/platform/agent/model-authorizations`
- `POST /api/v1/platform/agent/model-authorizations`
- `PATCH /api/v1/platform/agent/model-authorizations/{authorizationId}`
- `POST /api/v1/platform/agent/sessions`
- `POST /api/v1/platform/agent/sessions/{sessionId}/messages`
- `POST /api/v1/platform/agent/platform-agent-confirm`
- `GET /api/v1/systems/{systemId}/agent/policies`
- `POST /api/v1/systems/{systemId}/agent/policies`
- `PATCH /api/v1/systems/{systemId}/agent/policies/{policyId}`
- `POST /api/v1/systems/{systemId}/agent/policies/{policyId}/publish-check`
- `POST /api/v1/systems/{systemId}/agent/sessions`
- `POST /api/v1/systems/{systemId}/agent/sessions/{sessionId}/messages`
- `POST /api/v1/systems/{systemId}/agent/system-agent-write-confirmations`
- `POST /api/v1/systems/{systemId}/agent/system-agent-write-confirmations/{confirmationId}/confirm`
- `POST /api/v1/systems/{systemId}/agent/system-agent-write-confirmations/{confirmationId}/reject`
- `POST /api/v1/systems/{systemId}/work/agent/work-agent-draft-confirm`
- `GET /api/v1/systems/{systemId}/agent/audit-logs`

## Acceptance Coverage

- Platform confirmation uses `PLATFORM_AGENT_CONFIRM`.
- System write confirmation uses `SYSTEM_AGENT_WRITE_CONFIRM`.
- Work draft confirmation uses `WORK_AGENT_DRAFT_CONFIRM`.
- Platform Agent boundary denies `SYSTEM_BUSINESS_RECORD`, `SYSTEM_MODULE_WRITE`, and `SYSTEM_APPROVAL_WRITE`.
- Platform confirmation with `targetScope=system` returns `allowed=false` and rejected item metadata.
- System write confirmation is created as `WAITING_HUMAN_CONFIRM`; write execution is separated into `/confirm` and `/reject`.
- Work draft confirmation has `manualConfirmRequired=true`.
- Agent audit logs include:
  - `modelVersion`
  - `promptVersion`
  - `policyVersion`
  - `permissionSnapshotId`
  - `conversationSnapshot`
  - `toolCallSnapshot`
  - `desensitizeResult`
  - `outboundSnapshot`
  - `traceId`
  - `auditLogId`

## Verification

- `mvn -f backend/pom.xml -DskipTests compile`: PASS.
- `mvn -f backend/pom.xml -pl examine-web -am -DskipTests package`: PASS.
- Startup smoke on port `18088`: PASS.
- Smoke detail: `docs/evidence/build-g8-smoke.json`.
- Server logs:
  - `docs/evidence/build-g8-server.out.log`
  - `docs/evidence/build-g8-server.err.log`
- `git diff --check`: PASS with only known LF/CRLF warnings on `.cursor/session/issues/registry.jsonl`, `.cursor/session/state.json`, `docs/design/pre-coding-readiness.md`, and `docs/design/user-approval.md`.

