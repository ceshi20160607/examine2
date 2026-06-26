# Test Fixtures

## Roles

- `admin`: built-in platform super admin account for real deployment, default password `123123aa`, owns platform backend by default.
- `platform_admin_root`: historical prototype fixture name for the same platform-root perspective; do not use it as the real default login account.
- `platform_member`: platform normal member, can create or enter authorized systems but cannot open platform backend unless configured.
- `sys_admin_vehicle`: creator super admin of the vehicle system with full system permissions.
- `che`: normal system member with limited module, field, todo, message, and work permissions.

## Systems And Tenants

- System `sys_vehicle`: multi-tenant vehicle asset management system.
- Tenant `tenant_default`: default tenant used by `sys_admin_vehicle` and `che`.
- Tenant `tenant_branch`: secondary tenant used for tenant-switch tests.

## Members

- Account `acct_root`: platform root.
- Account `acct_boss`: platform member and vehicle system owner.
- Account `acct_che`: normal user mapped to `systemMemberId=sm_che`.
- `sm_che`: has business list/detail permissions for selected modules only.

## Business Runtime

- Module group `grp_assets`: top runtime module group.
- Module `mod_vehicle`: vehicle records.
- Record `rec_vehicle_001`: accessible to `che`.
- Record `rec_vehicle_masked`: accessible with masked fields.
- Record `rec_vehicle_denied`: denied by data scope.

## Work And Workflow

- Project `proj_impl`: sample implementation project.
- Project task `task_proj_001`: assigned to `che`, has comments, due warning, and kanban status.
- Plain task `task_plain_001`: one-line personal task with optional project relation.
- Daily report `report_che_today`: my daily report.
- Approval task `approval_vehicle_001`: pending approval target.

## Async And Logs

- Async task `async_import_001`: `PARTIAL_SUCCESS` with result and error file refs.
- Audit log `log_business_001`: business log with trace ID and audit ID.
- Login log `log_login_001`: SSO login trace.
