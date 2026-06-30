# R2 Module Publish Smoke - 2026-06-27

## Scope

Verify `REC-P0-004 System Admin Initializes A Usable Module` against the current standalone release.

This evidence proves the core module publish path: a system administrator creates a module group, module, dictionary-backed select field, required field set, list scene, row action, publish check, publish result, and then reads the published module schema from the runtime business shell.

It does not close all of Batch R2. Tenant switching, stale-data browser redraw, and normal-member permission negative checks remain pending.

## Environment

- Release frontend/local same-origin proxy: `http://127.0.0.1:18131/`
- Release backend: `http://127.0.0.1:9999`
- Redis: `192.168.0.211:6379`
- Script: `scripts/recovery-r2-module-publish-smoke.ps1`

## Task Card Mapping

- Task id: `REC-P0-004`
- User role: system administrator
- Prototype reference: system admin module configuration in `docs/design/prototypes/index.html`
- Frontend scope: system admin module configuration and system business module navigation
- Backend scope: module group, module, field, dictionary, scene, action, publish-check, publish, runtime schema APIs
- Data scope: module group, module, fields, dictionary, scene, action, publish version, runtime schema

## API Acceptance

Command:

```text
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\recovery-r2-module-publish-smoke.ps1 -BaseUrl http://127.0.0.1:18131
```

Result:

```json
{
  "status": "PASS",
  "task": "REC-P0-004",
  "systemId": "68",
  "tenantId": "69",
  "moduleGroupId": "34",
  "moduleId": "34",
  "moduleCode": "asset_0627174147",
  "fieldTypes": [
    "assetName:TEXT",
    "purchaseDate:DATE",
    "assetStatus:SELECT",
    "assetFiles:ATTACHMENT",
    "assetNo:AUTO_NUMBER"
  ],
  "sceneId": "52",
  "actionCode": "record.submitApproval",
  "publishCheckPassed": true,
  "publishVersion": "MODULE_v1782553309857",
  "adminListColumns": [
    "title",
    "status",
    "ownerDept",
    "assetName",
    "purchaseDate",
    "assetStatus",
    "assetFiles",
    "assetNo"
  ],
  "runtimeListColumns": [
    "title",
    "status",
    "ownerDept",
    "assetName",
    "purchaseDate",
    "assetStatus",
    "assetFiles",
    "assetNo"
  ],
  "runtimeSearchTotal": 0,
  "cleanup": "DELETE"
}
```

## Browser Evidence

The script was run once with `-KeepCreatedData` to leave a system for browser verification:

```json
{
  "status": "PASS",
  "systemId": "69",
  "tenantId": "70",
  "moduleId": "35",
  "moduleCode": "asset_0627174214",
  "publishVersion": "MODULE_v1782553337068",
  "cleanup": "SKIPPED"
}
```

Browser login:

- Login account: `r2_module_admin_0627174214`
- Route after login: `http://127.0.0.1:18131/#/systems/69/dashboard`
- Runtime route checked: `http://127.0.0.1:18131/#/systems/69/modules`

Runtime page check:

```json
{
  "url": "http://127.0.0.1:18131/#/systems/69/modules",
  "loading": false,
  "bodyHasAsset": true,
  "headings": [
    "Asset Ledger 0627174214",
    "暂无业务数据"
  ],
  "panels": [
    {
      "active": true,
      "text": "Asset Ledger 06271742140 条 / MODULE_v1782553337068"
    }
  ]
}
```

Screenshot:

- `docs/evidence/recovery/screenshots/r2-module-publish-runtime.png`

The browser evidence system was cleaned up after screenshot capture:

```json
{
  "result": "DELETE",
  "systemId": "69",
  "traceId": "trc_967f09f1-9319-4dfc-9e3b-000a9f45cad6",
  "auditLogId": "aud_trc_967f09f1-9319-4dfc-9e3b-000a9f45cad6"
}
```

## Result

PASS for the `REC-P0-004` core module publish path:

- The module is created through coded business APIs, not generated CRUD alone.
- Required field coverage includes text, date, select, attachment, and auto-number.
- Publish check passes before publish.
- Publish returns a version.
- Admin and runtime schemas both contain the configured fields.
- The running browser shows the published module in the system business shell.

Remaining R2 work:

- `REC-P0-003` tenant switch UI/data redraw is still open.
- `REC-P0-004` still needs normal-member permission negative evidence before it can be accepted as fully closed.

