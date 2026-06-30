# R2 System Member Permission Smoke Evidence - 2026-06-27

Scope: `REC-P0-003 System And Tenant Switch Context`

Target release: `http://127.0.0.1:18131`

## Result

Status: `PASS` for normal-member permission negatives.

This is not full product acceptance. Runtime record CRUD, approval/todo/message closure, and non-empty tenant-specific business-data redraw are still pending.

## Backend Change

Added backend enforcement for system administration APIs:

- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/common/SystemAccessGuard.java`
- `backend/examine-web/src/main/java/com/unique/examine/web/config/RequestContextFilter.java`

The guard requires `SYSTEM_ADMIN` or `SYSTEM_SUPER_ADMIN` for system-admin configuration surfaces such as members, roles, permissions, org, module configuration, flows, SSO, OpenAPI, notification templates, logs, Agent policies, work config, and ops checks.

Runtime paths remain available to normal system members, including runtime records, todos, messages, workflow instances, and module navigation reads.

## Repeatable API Smoke

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\recovery-r2-system-member-permission-smoke.ps1 -BaseUrl http://127.0.0.1:18131
```

Observed result:

```json
{
  "status": "PASS",
  "task": "REC-P0-003",
  "baseUrl": "http://127.0.0.1:18131",
  "targetSystemId": "75",
  "targetTenantId": "78",
  "normalAccount": "r2_normal_0627183110",
  "normalOwnedSystemId": "76",
  "normalMemberId": "81",
  "normalBindingId": "81",
  "normalEffectiveRoles": ["SYSTEM_MEMBER"],
  "allowedNavigationGroupCount": 0,
  "forbidden": {
    "memberList": 403,
    "moduleCreate": 403,
    "roleCreate": 403
  },
  "cleanup": ["75:DELETE", "76:DELETE"]
}
```

The script verifies:

- health is fully `UP`;
- admin creates a target system;
- a separate normal account is registered;
- admin creates a `SYSTEM_MEMBER` role and member in the target system;
- admin binds the normal account to that member;
- the normal account switches into the target system with only `SYSTEM_MEMBER`;
- normal member can still read module navigation;
- normal member receives HTTP `403` for member list, module creation, and role creation;
- all created systems are deleted by default.

## Browser Evidence

Browser automation used a kept evidence run, then deleted the created systems.

Temporary browser-audit data:

- target system: `77`
- normal owned system: `78`
- normal account: `r2_normal_0627183132`
- cleanup result: `77:DELETE`, `78:DELETE`

Observed browser state:

- normal member direct navigation to `#/systems/77/admin` rendered the no-permission page;
- `.admin-layout` count was `0`;
- visible heading was `无权限访问系统后台`.

Screenshot:

- `docs/evidence/recovery/screenshots/r2-normal-member-admin-denied.png`

## Remaining Gaps

- Tenant switching has not yet been proven with non-empty tenant-specific business records showing different visible rows after switching.
- R3 runtime record CRUD and approval/todo/message closure remain open.
- Broader module configuration UI interaction coverage remains open.
