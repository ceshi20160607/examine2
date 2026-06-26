# TASK-FE-020

## meta

- task_id: TASK-FE-020
- type: implementation
- owner: frontend
- phase: build
- parallel_group: G5
- depends_on: [TASK-FE-010, TASK-BE-020]

## goal

Implement admin configuration views for platform and system backends.

## inputs

- `docs/design/prototypes/index.html`
- `docs/api/api.md`
- `frontend/docs/api-contract-map.md`

## outputs

- `frontend/src/features/platform-admin/**`
- `frontend/src/features/system-admin/**`

## scope

### Do

- Implement platform admin views for platform information, organization, platform roles, dashboard management, configuration management, SSO/identity provider summary, ops governance entry, and log management entry.
- Implement system admin views for system information, organization, system roles, module management, flow management entry, dictionary management, work configuration, unified authentication, AI Agent configuration, and log management entry.
- Implement platform system list/detail, create/enable/disable/delete/restore flows, platform health summary entry, platform role list/edit/member assignment, and permission preview entry.
- Implement module configuration screens using TASK-BE-020 concepts: module tree/list, fields, list columns, filters, scenes, page actions, import/export config, print templates, and publish check.
- Keep platform backend and system backend separated by shell and permission scope.

### Do Not

- Do not implement runtime record list/detail pages, workflow canvas internals, OpenAPI detail pages, or AI Agent runtime chat.
- Do not merge platform backend and system backend menus.

## self_check_commands

```powershell
& 'D:\java\nodejs\npm.cmd' --prefix frontend run build
```

## acceptance

- [ ] Platform admin entry is visible only to platform admin roles.
- [ ] System admin entry is visible only to system admin roles.
- [ ] System lifecycle actions show reason, action result, traceId/auditLogId, or async task result.
- [ ] Platform role permissions keep platform scope and do not expose system business data configuration.
- [ ] System module configuration exposes fields, list, filters, scenes, actions, import/export config, print templates, and publish check.
- [ ] `task-accept` verdict is pass.

## integration_test

Admin configuration QA validates platform/system separation and permission controls.
