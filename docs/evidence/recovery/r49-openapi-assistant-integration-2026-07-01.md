# R49 OpenAPI Assistant Integration Smoke

- Status: PASS
- Base URL: http://127.0.0.1:18131
- System: 947
- OpenAPI: app=56, record=475, secretRef=sec_openapi_r49_app_0702120745045_74d61e_d3d943ae, rotateJob=srj_openapi_56_3332295e
- OpenAPI logs: success=5, failed=1, readonlyDenied=403, wrongSecretDenied=401
- Agent: policy=46, publishCheck=True, platformDenied=REJECTED_BY_SCOPE
- Assistant confirmations: preview=WAITING_HUMAN_CONFIRM, confirm=CONFIRMED, reject=REJECTED, draftPreview=WAITING_HUMAN_CONFIRM, draftConfirm=CONFIRMED
- Permission negatives: normalOpenAPI=403, normalAgentPolicy=403
- Audits: agentLogs=12, confirmationLogs=6
- Browser results: count=6, overflow=0, blockers=0
- Browser audit JSON: docs/evidence/recovery/screenshots/r49-openapi-assistant-integration/openapi-assistant-integration-browser-audit.json
- Cleanup: 947:DELETE, 948:DELETE

This is engineering evidence only. gates.user_script_passed remains false.
