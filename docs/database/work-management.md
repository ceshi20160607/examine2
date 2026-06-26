# Work Management Schema

## Scope

Fragment: `sql/fragments/007-work-management.sql`

This fragment owns projects, project tasks, plain tasks, task comments, task events, task relations, daily reports, daily report source records, and work calendar projections.

## Tables

- `un_work_project`: project record.
- `un_work_task`: project and plain task shared table with `taskType`.
- `un_work_task_collaborator`: collaborators and watchers.
- `un_work_task_comment`: replies and comments.
- `un_work_task_event`: status, due, progress, warning, and assignment changes.
- `un_work_task_relation`: task to project, module record, todo, message, or approval relation.
- `un_work_kanban_config`: configured list/kanban source fields.
- `un_work_daily_report`: my daily reports and status.
- `un_work_daily_report_source`: auto-draft candidate source snapshot.
- `un_work_calendar_item`: calendar projection for project tasks, plain tasks, daily reports, and todos.
- `un_work_daily_report_auto_source_rule`: daily report source rules.

## Constraints

- Work management has four runtime tabs: dashboard, project tasks, plain tasks, daily reports.
- Project tasks and plain tasks are separate business objects, each with list/kanban mutual view switching.
- Kanban columns, swimlanes, and groups come from configured select fields and dictionary items.
- Daily report auto draft reads only authorized task, todo, message, business log, and approval data, then requires human confirmation.

## Acceptance Notes

- Indexes cover task type, project, assignee, collaborator, status, due time, tag, report date, and calendar date.
- Comments and events preserve trace and audit context.
