# R1 Permission Smoke Evidence

Time: 2026-06-27 Asia/Shanghai

## Scope

This evidence covers R1 permission boundaries for:

- unauthenticated API access
- non-platform-admin access to platform administration APIs
- normal account access to allowed system switch options
- platform root access to platform administration APIs

## Bug Found And Fixed

Before the fix:

- A normal registered account with no platform role could call `GET /api/v1/platform/systems`.
- The frontend hid the platform admin entry, but the backend did not enforce the same platform administration boundary.

Fix:

- Added `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/common/PlatformAccessGuard.java`.
- Updated `backend/examine-web/src/main/java/com/unique/examine/web/config/RequestContextFilter.java`.
- Platform administration paths under `/api/v1/platform/**` now require `PLATFORM_ADMIN` or `PLATFORM_ROOT`.
- Allowed platform workbench paths remain accessible to normal authenticated users:
  - `/api/v1/platform/system-switch/**`
  - `/api/v1/platform/todos/**`
  - `/api/v1/platform/messages/**`

## Repeatable Script

Added:

```powershell
scripts/recovery-r1-permission-smoke.ps1
```

## Verification

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\recovery-r1-permission-smoke.ps1
```

Result:

```json
{
  "status": "PASS",
  "unauthenticatedAccountMe": {
    "ok": true,
    "http": 401,
    "code": "AUTH_UNAUTHORIZED"
  },
  "normalPlatformSystems": {
    "ok": true,
    "http": 403,
    "code": "PERMISSION_DENIED"
  },
  "normalPlatformHealth": {
    "ok": true,
    "http": 403,
    "code": "PERMISSION_DENIED"
  },
  "normalSwitchOptions": {
    "ok": true,
    "code": "SUCCESS",
    "count": 1
  },
  "adminPlatformSystems": {
    "ok": true,
    "code": "SUCCESS"
  },
  "registeredNormalAccount": {
    "code": "SUCCESS",
    "platformPermissions": [],
    "systemCount": 1
  }
}
```

## Acceptance Status

Accepted for R1 API permission smoke:

- Unauthenticated API access is rejected.
- Normal authenticated accounts without platform roles are rejected from platform administration APIs.
- Normal authenticated accounts can still read their own system switch options.
- `admin / 123123aa` with `PLATFORM_ROOT` can still access platform administration APIs.

Not final acceptance:

- Broader system-level permission negatives are part of R2/R3 runtime and admin flow evidence.
- Default production Redis remains unreachable, so default-config release acceptance is still blocked.
