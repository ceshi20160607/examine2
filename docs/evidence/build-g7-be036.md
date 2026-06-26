# G7 / TASK-BE-036 Work Management APIs Evidence

## Scope

- Implemented work management APIs in `backend/examine-ai-work/src/main/java/com/unique/examine/aiwork/manage/work/**`.
- Added `examine-ai-work` to the web aggregation module.
- Kept project task and plain task lifecycles separate.

## Implemented Endpoints

- `GET /api/v1/systems/{systemId}/work/dashboard`
- `POST /api/v1/systems/{systemId}/work/projects/search`
- `POST /api/v1/systems/{systemId}/work/project-tasks/search`
- `POST /api/v1/systems/{systemId}/work/project-tasks`
- `PATCH /api/v1/systems/{systemId}/work/project-tasks/{taskId}`
- `POST /api/v1/systems/{systemId}/work/plain-tasks/search`
- `POST /api/v1/systems/{systemId}/work/plain-tasks`
- `PATCH /api/v1/systems/{systemId}/work/plain-tasks/{taskId}`
- `POST /api/v1/systems/{systemId}/work/kanban/query`
- `POST /api/v1/systems/{systemId}/work/daily-reports/search`
- `POST /api/v1/systems/{systemId}/work/daily-reports`
- `POST /api/v1/systems/{systemId}/work/daily-reports/auto-draft`
- `GET /api/v1/systems/{systemId}/work/config`
- `PATCH /api/v1/systems/{systemId}/work/config`
- `POST /api/v1/systems/{systemId}/work/config/publish-check`
- `GET /api/v1/systems/{systemId}/work/tasks/{taskId}/comments`
- `POST /api/v1/systems/{systemId}/work/tasks/{taskId}/comments`
- `GET /api/v1/systems/{systemId}/work/tasks/{taskId}/events`

## Acceptance Coverage

- Four fixed work tabs are exposed: `dashboard`, `projectTask`, `plainTask`, `dailyReport`.
- Project task lifecycle is `projectTaskLifecycle`.
- Plain task lifecycle is `plainTaskLifecycle`.
- Project task kanban uses `field_project_status`; plain task kanban uses `field_plain_status`.
- Kanban column, swimlane, and grouping fields are select/multi-select field references with dictionary item `color`, `icon`, and `semantic`.
- Daily report auto draft returns `DailyReportAutoSourceRule`, `manualConfirmRequired=true`, and `CURRENT_MEMBER_AUTHORIZED_ONLY`.
- Work config exposes project task fields, plain task fields, daily report fields, kanban configs, dictionary bindings, status color semantics, and publish check result.

## Verification

- `mvn -f backend/pom.xml -DskipTests compile`: PASS.
- `mvn -f backend/pom.xml -pl examine-web -am -DskipTests package`: PASS.
- Startup smoke on port `18087`: PASS.
- Smoke detail: `docs/evidence/build-g7-smoke.json`.
- Server logs:
  - `docs/evidence/build-g7-server.out.log`
  - `docs/evidence/build-g7-server.err.log`
- `git diff --check`: PASS with only known LF/CRLF warnings on `.cursor/session/issues/registry.jsonl`, `.cursor/session/state.json`, `docs/design/pre-coding-readiness.md`, and `docs/design/user-approval.md`.

