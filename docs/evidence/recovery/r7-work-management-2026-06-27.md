# R7 Work Management Runtime Evidence

Time: 2026-06-27 22:17 Asia/Shanghai

## Result

- Batch: R7 Work Management Runtime Closure
- Task card: `REC-P0-010 Work Management Runtime Closure`
- Status: PASS
- Script: `scripts/recovery-r7-work-management-smoke.ps1`
- Final orchestration: `scripts/recovery-r5-final-user-script.ps1`

## API Evidence

`scripts/recovery-r7-work-management-smoke.ps1 -BaseUrl http://127.0.0.1:18131` passed inside the final R5 run.

Latest final-run values:

- systemId: `172`
- tenantId: `187`
- projectId: `6`
- projectTaskId: `11`
- plainTaskId: `12`
- dailyReportId: `5`
- dashboard before: active projects `0`, project tasks `0`, plain tasks `0`
- dashboard after: active projects `1`, project tasks `1`, plain tasks `1`
- project search total: `1`
- project task search total: `1`
- plain task search total: `1`
- daily report search total: `1`
- project kanban columns: `5`
- plain kanban columns: `4`
- cleanup: `DELETE`

The script proves project creation, project task creation, plain task creation, daily report creation, auto-draft generation, dashboard count readback, list search readback, and kanban readback.

## Browser Evidence

An explicit kept-data browser run used system `164` before cleanup.

- Forced full page load used `/assets/index-CTS5z8uP.js`.
- Work dashboard showed active projects `1`, project tasks `1`, plain tasks `1`.
- Project task tab showed `R7 Project Task 0627221254` in a table with pagination disabled only because there was no previous/next page.
- Project task kanban showed `R7 Project Task 0627221254` with `tableCount=0` and `kanbanCount=1`, proving list and kanban were mutually exclusive.
- Plain task tab showed `R7 Plain Task 0627221254`.
- Daily report tab showed `2026-06-27: R7 daily report 0627221254`.

## Deployment Cache Finding

Before the forced full page load, the open browser tab still executed old asset `/assets/index-D1hoPWsv.js` even though the deployed `index.html` pointed at `/assets/index-CTS5z8uP.js`. This explains why an already-open SPA tab can continue showing old behavior after a new deployment.

Mitigation:

- `deploy/templates/nginx/unexamine.conf` now sends `Cache-Control: no-store` for `/index.html` and SPA fallback HTML.
- The release package contains the updated Nginx template.
- `verify-release.ps1 -CheckDeployedFrontend` still compares deployed asset hashes with the release package.

## Cleanup

`scripts/recovery-clean-test-systems.ps1 -Execute` deleted kept evidence systems:

- `164 / R7 Work System 0627221254`
- `154 / R6 Data Source System 0627215506`

Follow-up dry run returned `matchedCount=0`.

## Boundary

R7 closes the work-management runtime row. It does not close unrelated open product rows such as broader SSO/no-member, OpenAPI/import-export, message-center filtering/archive, or AI Agent runtime separation.
