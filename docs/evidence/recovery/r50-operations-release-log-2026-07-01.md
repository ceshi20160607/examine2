# R50 Operations Release Log Smoke

- Status: PASS
- Base URL: http://127.0.0.1:18131
- Release verify: PASS
- Server script commands: start, stop, restart, status, health
- System: 1095
- Ops traces: platform=trc_r50_platform_health_0706153449837_40cdbc, system=trc_r50_system_health_0706153449837_40cdbc, feature=trc_r50_feature_flag_0706153449837_40cdbc, quota=trc_r50_quota_0706153449837_40cdbc, rate=trc_r50_rate_0706153449837_40cdbc
- Async tasks: backup=TASK-20260706153501, restore=TASK-20260706153501, archive=TASK-20260706153501, rollback=TASK-20260706153501
- Audit readback: platform=9, system=1, deniedPlatform=FAILURE, deniedSystem=FAILURE
- Browser results: count=6, overflow=0, blockers=0
- Browser audit JSON: docs/evidence/recovery/screenshots/r50-operations-release-log/operations-release-log-browser-audit.json
- Cleanup: 1095:DELETE, 1096:DELETE

This is engineering evidence only. gates.user_script_passed remains false.
