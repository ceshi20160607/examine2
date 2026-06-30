# Final Usable System Acceptance

Time: 2026-06-29 Asia/Shanghai

## Purpose

This document is the top-level recovery gate for the user's final goal: the project must become a system that real people can use.

The R-series recovery batches are evidence inputs only. They do not equal final completion. A local page fix, an API 200 response, a generated CRUD table, a successful package build, or a single browser smoke cannot close the final product goal by itself.

## Final Product Target

The accepted target comes from `docs/user_requirement.md` and `docs/design/prototype-brief.md`:

- An administrator can create a system, configure module groups, modules, fields, actions, permissions, approval flow, dictionaries, members, external access, import/export, and publish it.
- A normal member can log in, enter an authorized system, open a business module, create/edit/query/filter/detail data, upload attachments, handle approval, read messages/todos, and export authorized data.
- External systems can access authorized data safely through OpenAPI.
- The release can be packaged, deployed, restarted, health-checked, audited, and maintained.
- The product has four clear shells: platform workspace, platform admin, system business page, and system admin.

## Non-Completion Cases

The final goal is not accepted if any of these are true:

- A batch script passes but the role journey is not runnable from login to result.
- A page exists but the user cannot finish the business action.
- A backend API works but the deployed frontend is not bound to it.
- A frontend action only shows a toast, local state, prompt, or static prototype data.
- Generated CRUD exists but coded business behavior, permission, state, and evidence are missing.
- Admin-only evidence is used to claim normal-member usability.
- One happy path passes while permission negative, empty state, error state, mobile containment, or persistence readback is missing.
- R5/R20 passes but there is no single coherent final journey across admin configuration, normal runtime work, approval, messages/todos, external integration, and deployment.

## Evidence Standard

Each final journey gate must include:

- deployed frontend browser evidence on desktop and mobile where relevant
- real login through the deployed page unless the step is explicitly deployer-only
- API readback or database-backed readback
- permission positive and negative cases
- persisted state after reload or restart where the journey depends on it
- loading, empty, disabled, validation, error, or async-task states for the key action
- traceId, taskId, auditLogId, or equivalent operational evidence for critical actions
- cleanup of disposable systems/data unless intentionally kept

## Journey Gates

| Gate | Name | Required user-level outcome | Current evidence input | Current status |
|---|---|---|---|---|
| J0 | Release health | Build/package/start/restart/health/deployed asset/admin login all pass from the release package. | R5 and verify-release after R20/R22 were included. | `PASS` |
| J1 | Register first system | A new user registers with a system, lands on initialization guidance, enters system admin, and receives system-super-admin context. | R16. | `PASS` |
| J2 | Admin builds business app | System admin creates module group/module/fields/list/actions/dictionary/role/flow/member, publishes, and sees the business app become usable. | R2, R12, R19, R20. | `PASS` |
| J3 | Normal user daily work | Normal member logs in, opens authorized business shell, creates/edits/filters/details/uploads/exports data, and cannot access admin. | R17, R18, R10. | `PASS` |
| J4 | Approval and notification | Requester submits approval, approver receives todo/message, processes it, and record/todo/message states close. | R3 and R8. | `PASS` |
| J5 | Admin configuration depth | System admin surfaces for org, roles, modules, work config, flow canvas, dictionaries, data source, SSO, Agent, external app, logs are usable and not stacked/mixed. | R4, R6, R9, R11, R13, R19, R20. | `PASS` |
| J6 | External and import/export | External app, SecretRef, OpenAPI call, upload, import, export, call logs, and result files work from UI and API. | R10. | `PASS` |
| J7 | Operations and maintenance | Release package supports start/stop/restart/health, deployed assets match, logs/trace evidence exist, and backup/rollback/maintenance gaps are explicitly tracked. | R22 operations and maintenance smoke. | `PASS` |

## Current Decision

The engineering final usable-system gate passes through `scripts/recovery-r21-final-usable-system-audit.ps1`.

This does not set `gates.user_script_passed=true`. User verification/signoff remains a separate gate.

## Operating Rule

When a new problem is found, update this file, `docs/recovery/p0-task-cards.md`, `docs/recovery/fix-batches.md`, and `docs/recovery/current-product-audit.md` before coding. The recovery process must move from isolated feature batches toward coherent role journeys.
