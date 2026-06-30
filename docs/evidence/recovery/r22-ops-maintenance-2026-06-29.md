# R22 Operations And Maintenance Smoke

Status: PASS

Base URL: http://127.0.0.1:18131

## Covered Flow

- Release verification passed with deployed frontend asset comparison.
- Packaged backend server.sh exposes start, stop, restart, status, and health commands.
- Platform ops health returned traceId trc_7f26d14e-f977-4c46-a0d8-e0a28beef814.
- Feature flag, quota, and rate-limit updates returned trace ids.
- Backup, restore drill, archive restore, and deployment rollback returned async task ids: TASK-20260629225608, TASK-20260629225609, TASK-20260629225609, TASK-20260629225609.
- Restore drill, archive restore, and deployment rollback declare rollbackSupported=true.
- API cache policy read/update returned 1 policies.
- Browser used real /login, opened platform admin configuration, clicked operations governance buttons, and saw visible task/trace results.
- Mobile containment kept document overflow at 0.

## Screenshots

- docs/evidence/recovery/screenshots/r22-ops-maintenance/platform-ops-desktop.png
- docs/evidence/recovery/screenshots/r22-ops-maintenance/platform-ops-mobile.png

## Result JSON

- docs/evidence/recovery/r22-ops-maintenance-result.json
