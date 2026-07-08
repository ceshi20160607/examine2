# REC-P0-065 / R65 OpenAPI Assistant External-Service First-Use Closure

Status: PASS as deployed engineering evidence only.

- Base URL: http://127.0.0.1:18131
- Requirement rows: REQ-5.15, REQ-5.19, REQ-9, REQ-14.1-14.37
- Fresh OpenAPI system/module: system=947, module=379, app=56, record=475
- Secret/log boundary: secretRef=sec_openapi_r49_app_0702120745045_74d61e_d3d943ae, rotateJob=srj_openapi_56_3332295e, successLogs=5, failedLogs=1
- Denials: readOnly=403, wrongSecret=401, normalOpenApi=403, normalPolicy=403, platformDenied=REJECTED_BY_SCOPE
- Assistant: policyPublish=True, confirmations=SYSTEM_AGENT_WRITE_CONFIRM,WORK_AGENT_DRAFT_CONFIRM, write=WAITING_HUMAN_CONFIRM/CONFIRMED/REJECTED, draft=WAITING_HUMAN_CONFIRM/CONFIRMED
- Logs/browser: agentLogs=12, confirmationLogs=6, browser=6, overflow=0, blockers=0
- Browser audit: docs/evidence/recovery/screenshots/r65-openapi-assistant-external-service-first-use/openapi-assistant-browser-audit.json
- Cleanup: 947:DELETE, 948:DELETE

This does not close final product acceptance. Broader operations breadth, full requirement coverage, and user signoff remain open.
