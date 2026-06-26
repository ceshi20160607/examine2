# Permission RBAC Schema

## Scope

Fragment: `sql/fragments/002-permission-rbac.sql`

This fragment owns system roles, role members, permission grants, deny policies, data scopes, field permissions, and effective permission snapshots.

## Tables

- `un_plat_role`: configurable platform or system role.
- `un_plat_role_member`: account/member-role binding.
- `un_plat_permission_version`: published permission version.
- `un_plat_role_permission`: menu, module, action, field, data scope, and backend permission grants.
- `un_plat_role_field_permission`: field readable, writable, masked, or hidden rules.
- `un_plat_data_scope_rule`: role data-scope expression.
- `un_plat_deny_policy`: explicit deny rules with priority.
- `un_plat_permission_preview_log`: preview and explanation records.
- `un_plat_effective_permission_snapshot`: calculated permission version used by runtime.

## Constraints

- Built-in system creator super admin is special, but other backend entries are permission-configured.
- Permission snapshots are server-side decisions; frontend must not calculate effective permissions locally.
- Field and action rules must support disabled reason and explain data.

## Acceptance Notes

- Indexes cover role, member, system, tenant, module, field, and snapshot lookups.
- Data scope does not replace tenant isolation; tenant isolation remains enforced by backend/database context.
