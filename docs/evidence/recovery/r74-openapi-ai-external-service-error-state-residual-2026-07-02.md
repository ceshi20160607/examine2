# REC-P0-074 / R74 OpenAPI AI External-Service Error-State Residual Closure

- Status: PASS
- Base URL: http://127.0.0.1:18131
- System: 1032
- OpenAPI: app=60, record=595, secretRef=sec_openapi_r74_app_0702161011730_b380bc_rot_faf7cbfc, rotateJob=srj_openapi_60_faf7cbfc
- OpenAPI logs: success=5, failed=2, readonlyDenied=403, wrongSecretDenied=401
- Agent: policy=48, publishCheck=True, platformDenied=REJECTED_BY_SCOPE
- Assistant confirmations: preview=WAITING_HUMAN_CONFIRM, confirm=CONFIRMED, duplicateConfirmDenied=400, reject=REJECTED, draftPreview=WAITING_HUMAN_CONFIRM, draftConfirm=CONFIRMED
- Permission negatives: normalOpenAPI=403, normalAgentPolicy=403
- Audits: agentLogs=12, confirmationLogs=6
- Browser results: count=6, overflow=0, blockers=0
- Rotation: oldSecret=sec_openapi_r74_app_0702161011730_b380bc_f8e70a96, activeSecret=sec_openapi_r74_app_0702161011730_b380bc_rot_faf7cbfc, oldSecretDenied=401
- Browser audit JSON: docs/evidence/recovery/screenshots/r74-openapi-ai-external-service-error-state-residual/openapi-ai-external-service-error-browser-audit.json
- Cleanup: 1032:DELETE, 1033:DELETE

This is engineering evidence only. gates.user_script_passed remains false.
