# TASK-QA-012 Permission Matrix

## Verdict

PASS for G4 scope. Platform/system entry boundaries are represented in backend contracts and frontend shell routes.

## Roles

| Actor | Platform Workbench | Platform Backend | System Runtime | System Backend | Business Data Without System Switch |
|---|---|---|---|---|---|
| `platform_admin_root` | Allowed | Allowed by built-in root | Requires system member mapping | Requires system member role | Denied |
| `platform_member` | Allowed | Hidden or disabled unless role grants backend menu | Requires system switch and mapping | Hidden or disabled unless role grants backend menu | Denied |
| `sys_admin_vehicle` | Allowed if platform account exists | By platform role only | Allowed after `SystemSwitchContext` | Allowed by system admin role | Denied |
| `che` | Allowed if platform account exists | Hidden or disabled | Allowed after `SystemSwitchContext` within data scope | Hidden or disabled | Denied |

## Backend Evidence

- `POST /api/v1/platform/system-switch` returns `systemId`, `tenantId`, `systemMemberId`, roles, data scope, message/todo scope, and permission snapshot.
- `POST /api/v1/systems/{systemId}/tenant-switch` returns tenant role/data scope and switchability.
- `GET /api/v1/systems/{systemId}/permissions/effective` returns action, field, data-scope, deny-policy, and explain metadata.
- SSO no-member APIs return `businessAccessAllowed=false` before approval and system member binding.
- Platform messages use target types such as `system_switch`, `platform_task`, `platform_log`, and do not directly open system business records.

## Frontend Evidence

- Platform admin button is disabled when `canEnterPlatformAdmin()` is false and carries a `disabledReason`.
- System admin button is disabled when `canEnterSystemAdmin()` is false and carries a `disabledReason`.
- System entry is only from system switch cards with `accountMemberBindingId` and `systemMemberId`.
- A system without member mapping is displayed as disabled with an application/mapping reason.

## Residual Risks

- Current implementation is contract-first and mock/shell based. Persistent role membership, field-level enforcement, and route guards must be connected to real auth state in later backend and frontend slices.
