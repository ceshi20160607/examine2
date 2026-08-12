# P6 Work, Todo and message phase gap report

- outcomeId: `P6_PHASE_ACCEPTANCE`
- proposed verdict: `NOT_PROVEN`
- promotion safety: `DO_NOT_PROMOTE`

## Frozen requirements

P6 owns projects/tasks/daily reports/calendar, reminders, platform/system Todo and messages. `REQ-WORK-001` explicitly includes field/dictionary/card/Kanban configuration and publication, not only fixed task tables and views.

## Evidence audit

| requirement | evidence | assessment |
|---|---|---|
| Work project/member/task lifecycle with list/Kanban/calendar equivalence | `.cursor/session/evidence/fast-work-project-kanban-calendar-56/acceptance.md` | `PROVEN` |
| Daily reports and manager review | `.cursor/session/evidence/fast-work-daily-report-57/acceptance.md` | `PROVEN` |
| Durable reminders and Event delivery | `.cursor/session/evidence/fast-work-task-reminder-58/acceptance.md` | `PROVEN` |
| Unified Todo owner delegation for Work/Flow and Event reminder/CC | fast acceptances 59 and 101 | `PROVEN` |
| Message templates, preferences, retry and INBOX/EMAIL/WEBHOOK administration | fast acceptances 32, 93, 102 and Cycle111 | `PROVEN` |
| Configurable project-task, ordinary-task and daily-report fields, field permissions, card fields, Kanban numeric fields, checks, publication and rollback | `REQ-WORK-001`; `docs/user_requirement.md` §§1517-1596; Batch56/57 Deferred sections | `CONTRADICTED`: accepted Work slices explicitly defer configurable Work fields; no administration route or acceptance exists. |
| One platform/system Todo contract | roadmap P6 and requirement journeys | `PARTIAL`: system Todo is accepted and platform personal tasks exist, but no evidence proves a unified platform-context Todo action center equivalent to the system owner path. |

## Blocking gap

Fixed Work schemas and three consistent views do not satisfy the frozen no-code Work configuration and publish/rollback contract. The accepted evidence itself records that deferral.

## Required closure evidence

Add a system-admin Work configuration/version lifecycle and prove that published fields/permissions/card/Kanban settings control the ordinary member Work runtime. Add or formally delimit the platform-context Todo journey.

