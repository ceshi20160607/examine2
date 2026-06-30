# R2 Tenant Business Redraw Evidence

Time: 2026-06-27 Asia/Shanghai

Base URL: `http://127.0.0.1:18131`

## Scope

This evidence closes the previously missing non-empty tenant redraw check for `REC-P0-003`.

The check proves that tenant switching changes the actual backend member context used by module configuration, runtime module navigation, and runtime record queries. It does not accept an empty-list check as sufficient evidence.

## Fix

- `ModuleSystemContextResolver` now prefers the Redis-backed current `accountMemberBindingId` for the current account and route system before falling back to the first binding.
- `SystemMemberContextResolver` now uses the same active binding rule, so permission snapshots and admin/module APIs resolve the selected tenant/member context.
- The active binding is validated against account id, system id, and binding status before use.

## Repeatable Script

`scripts/recovery-r2-tenant-business-redraw-smoke.ps1`

Latest API run:

```json
{
  "status": "PASS",
  "generatedAt": "2026-06-27T20:21:35.3056637+08:00",
  "systemId": "120",
  "tenantA": {
    "tenantId": "126",
    "contextMemberId": "146",
    "moduleId": "53",
    "recordId": "38",
    "recordTitle": "Tenant A Record 0627202127",
    "hiddenOtherTenantModuleId": "54",
    "searchTotal": 1
  },
  "tenantB": {
    "tenantId": "127",
    "contextMemberId": "147",
    "moduleId": "54",
    "recordId": "39",
    "recordTitle": "Tenant B Record 0627202127",
    "hiddenOtherTenantModuleId": "53",
    "searchTotal": 1
  },
  "contextBackA": {
    "tenantId": "126",
    "systemMemberId": "146",
    "permissionSnapshotId": "eps_120_126_146"
  },
  "cleanup": "DELETE"
}
```

## Browser Evidence

Screenshots:

- `docs/evidence/recovery/screenshots/r2-tenant-business-redraw/tenant-a-modules.png`
- `docs/evidence/recovery/screenshots/r2-tenant-business-redraw/tenant-b-modules.png`

Browser result:

```json
{
  "status": "PASS",
  "systemId": "121",
  "tenantA": "128",
  "tenantB": "129",
  "tenantARecordVisible": true,
  "tenantBRecordHiddenFromA": true,
  "tenantBRecordVisible": true,
  "tenantARecordHiddenFromB": true,
  "currentTenantAfterSwitch": "129",
  "consoleErrorCount": 0,
  "failedRequestCount": 0,
  "cleanup": "DELETE"
}
```

## Regression

- `scripts/recovery-r2-module-publish-smoke.ps1 -BaseUrl http://127.0.0.1:18131`: PASS
- `scripts/recovery-r2-tenant-switch-smoke.ps1 -BaseUrl http://127.0.0.1:18131`: PASS
- `scripts/recovery-r2-system-member-permission-smoke.ps1 -BaseUrl http://127.0.0.1:18131`: PASS
- `scripts/recovery-r3-runtime-approval-smoke.ps1 -BaseUrl http://127.0.0.1:18131`: PASS
- `scripts/verify-release.ps1 -BaseUrl http://127.0.0.1:18131 -CheckDeployedFrontend`: PASS
- `scripts/recovery-r0-static-audit.ps1`: PASS
- Frontend `npm run typecheck`: PASS
- Backend Maven package: PASS
- `git diff --check`: PASS, only Windows LF/CRLF warnings.

## Result

`REC-P0-003` now has non-empty tenant-specific business data redraw evidence across API and browser.
