# R11 AI Agent Scope Confirmation Evidence

Date: 2026-06-29

## Scope

`REC-P0-013 AI Agent Scope Confirmation Audit Closure`

The acceptance target was platform/system/work Agent separation, explicit confirmation boundaries, audit evidence, and negative permission checks.

## Changes

- Added `REC-P0-013 AI Agent Scope Confirmation Audit Closure`.
- Added `scripts/recovery-r11-ai-agent-scope-confirmation-smoke.ps1`.
- Added R11 to `scripts/recovery-r5-final-user-script.ps1`.
- Extended `scripts/recovery-clean-test-systems.ps1` to clean R11 systems.

## Standalone Smoke

Command:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/recovery-r11-ai-agent-scope-confirmation-smoke.ps1 -BaseUrl http://127.0.0.1:18131
```

Result: PASS

Key result:

```json
{
  "result": "PASS",
  "targetSystemId": "240",
  "modelAuthorizationId": "6",
  "modelSecretRefId": "sec_model_r11_0629095151380_9f2161",
  "platformDeniedStatus": "REJECTED_BY_SCOPE",
  "platformAllowedStatus": "CONFIRMED",
  "agentPolicyId": "17",
  "policyPublishPassed": true,
  "proposedConfirmationTypes": [
    "SYSTEM_AGENT_WRITE_CONFIRM",
    "WORK_AGENT_DRAFT_CONFIRM"
  ],
  "writeConfirmedStatus": "CONFIRMED",
  "writeRejectedStatus": "REJECTED",
  "workDraftStatus": "CONFIRMED",
  "agentAuditLogCount": 9,
  "normalMemberPolicyCreateStatus": 403,
  "cleanup": [
    "240:DELETE",
    "241:DELETE"
  ]
}
```

## Proven Outcomes

- Platform model authorization persists SecretRef metadata and does not expose plaintext credential material.
- Platform Agent session has platform scope and denies system business targets.
- Platform Agent system-scope confirmation returns `REJECTED_BY_SCOPE`.
- Platform Agent platform-scope confirmation returns `CONFIRMED` and generated-object evidence.
- System Agent policy persists module/field/action/data/outbound/desensitize scope and publish-check passes.
- System Agent session binds to target system and permission snapshot.
- System Agent message proposes both `SYSTEM_AGENT_WRITE_CONFIRM` and `WORK_AGENT_DRAFT_CONFIRM`.
- System Agent write confirmation supports both confirm and reject transitions.
- Work Agent draft confirmation requires manual confirmation and preserves source snapshots.
- Agent audit logs include system session/message/confirmation/work records.
- Normal system member policy creation is rejected with HTTP 403.

## Final Release Orchestration

Command:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/recovery-r5-final-user-script.ps1 -BaseUrl http://127.0.0.1:18131
```

Result file: `docs/evidence/recovery/r5-final-release-result.json`

Result: PASS, `stepsPassed=19`, `stepsFailed=0`.

R11 step:

```json
{
  "name": "r11-ai-agent-scope-confirmation",
  "status": "PASS",
  "result": "PASS",
  "targetSystemId": "256",
  "modelAuthorizationId": "7",
  "platformDeniedStatus": "REJECTED_BY_SCOPE",
  "platformAllowedStatus": "CONFIRMED",
  "agentPolicyId": "19",
  "policyPublishPassed": true,
  "proposedConfirmationTypes": [
    "SYSTEM_AGENT_WRITE_CONFIRM",
    "WORK_AGENT_DRAFT_CONFIRM"
  ],
  "writeConfirmedStatus": "CONFIRMED",
  "writeRejectedStatus": "REJECTED",
  "workDraftStatus": "CONFIRMED",
  "agentAuditLogCount": 9,
  "normalMemberPolicyCreateStatus": 403,
  "cleanup": [
    "256:DELETE",
    "257:DELETE"
  ]
}
```

Release remains running for user verification:

- Frontend: `http://127.0.0.1:18131/`
- Backend: `http://127.0.0.1:9999`
- Backend PID: `4456`
- Frontend PID: `16124`
