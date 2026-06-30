# R2 Tenant Switch Smoke Evidence - 2026-06-27

Scope: `REC-P0-003 System And Tenant Switch Context`

Target release: `http://127.0.0.1:18131`

## Result

Status: `PASS` for the positive tenant-switch path.

This is not full R2 acceptance. Normal-member permission negatives and tenant-specific non-empty business-data redraw are still pending.

## Repeatable API Smoke

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\recovery-r2-tenant-switch-smoke.ps1 -BaseUrl http://127.0.0.1:18131
```

Observed result:

```json
{
  "status": "PASS",
  "task": "REC-P0-003",
  "baseUrl": "http://127.0.0.1:18131",
  "systemId": "74",
  "tenantA": "76",
  "tenantB": "77",
  "currentAfterTenantB": {
    "systemId": "74",
    "tenantId": "77",
    "memberId": "78",
    "roles": ["SYSTEM_SUPER_ADMIN"],
    "permissionSnapshot": {
      "snapshotId": "eps_74_77_78",
      "permissionVersion": "perm_live_SYSTEM_SUPER_ADMIN",
      "disabledReason": null
    }
  },
  "currentAfterTenantA": {
    "systemId": "74",
    "tenantId": "76",
    "memberId": "77",
    "roles": ["SYSTEM_SUPER_ADMIN"],
    "permissionSnapshot": {
      "snapshotId": "eps_74_76_77",
      "permissionVersion": "perm_live_SYSTEM_SUPER_ADMIN",
      "disabledReason": null
    }
  },
  "tenantCount": 2,
  "moduleGroupCountAfterTenantB": 0,
  "moduleCountAfterTenantB": 0,
  "todoTotalAfterTenantB": 0,
  "messageTotalAfterTenantB": 0,
  "cleanup": "DELETE"
}
```

The script verifies:

- health is fully `UP` through the standalone frontend/proxy URL;
- default `admin / 123123aa` login returns a Bearer token;
- a fresh multi-tenant system is created;
- default tenant A is switchable;
- tenant B is created and listed;
- switching to tenant B persists through `GET /api/v1/context/current-system`;
- switching back to tenant A persists through `GET /api/v1/context/current-system`;
- module group, module, todo, and message APIs remain readable after tenant switch;
- created test data is deleted by default.

## Browser Evidence

Browser automation used the deployed release and local Chrome.

Temporary browser-audit system:

- `systemId=73`
- tenant A: `74`
- tenant B: `75`
- cleanup result: `DELETE`

Observed browser state:

- before switching, `.tenant-switcher` count was `1`, selected value was tenant A, and options were default tenant plus tenant B;
- after selecting tenant B, `.tenant-switcher` selected value became tenant B;
- `GET /api/v1/context/current-system` returned `systemId=73`, `tenantId=75`, and role `SYSTEM_SUPER_ADMIN`;
- system dashboard rendered only dashboard sections, with no todo/message/admin panels stacked into it;
- system admin rendered one selected admin panel after tenant switch, not all admin sections at once.

Screenshots:

- `docs/evidence/recovery/screenshots/r2-tenant-switch-before.png`
- `docs/evidence/recovery/screenshots/r2-tenant-switch-after.png`
- `docs/evidence/recovery/screenshots/r2-system-admin-after-tenant-switch.png`

## Remaining Gaps

- Normal system-member permission negative evidence is still missing.
- Tenant switch has not yet been proven with non-empty tenant-specific business records showing different visible rows after switching.
- R3 runtime record CRUD and approval/todo/message closure remain open.
