# R0 Static Product Audit Evidence

Time: 2026-06-27 Asia/Shanghai

## Scope

This is not final product acceptance. It is the first recovery audit pass requested by `docs/recovery/fix-batches.md` R0. The goal is to identify obvious current-state blockers before more coding starts.

## Commands

```powershell
rg -n "window\.prompt|window\.confirm|alert\(|localStorage|fallback|Loading|TODO|mock|sample|stub|placeholder|demo" frontend/src backend -g '!frontend/node_modules/**' -g '!frontend/dist/**'
rg -n "createPagination\(|上一页|下一页|addEventListener\('click'.*page|pageNo" frontend/src/features frontend/src/shared frontend/src/api/liveData.ts
rg -n "createSidebarButton\('([^']+)', '([^']+)'" frontend/src/features/system-admin/systemAdmin.ts frontend/src/features/platform-admin/platformAdmin.ts
rg -n "workspace-shell|workspace-main|page-grid|admin-layout|system-layout|panel|split-grid|tree-table-layout|todo-layout|workbench-page|table-shell" frontend/src/styles.css
```

## Findings

### FE-R0-001 Mixed Admin Panels

Evidence:

- `frontend/src/features/system-admin/systemAdmin.ts`
  - `组织架构` and `角色管理` both target `org-role`.
  - `流程管理` and `字典管理` both target `flow-dict`.
  - `数据源` and `对外应用` both target `integration-config`.
  - `工作配置` and `AI Agent` both target `work-agent`.
  - `统一认证` and `日志管理` both target `sso-log`.
- `frontend/src/features/platform-admin/platformAdmin.ts`
  - `组织架构` and `角色管理` both target `platform-role`.
  - `仪表盘管理` targets `platform-info`.

Impact:

- This supports the user's report that pages/functions are crowded and mixed.
- The current admin UI does not yet satisfy the prototype expectation of clear task-oriented admin surfaces.

Recovery mapping:

- `REC-P0-002`
- `REC-P0-007`
- `REC-P0-009`

### FE-R0-002 Prompt-Driven P0 Actions

Evidence:

- `frontend/src/features/system-admin/systemAdmin.ts` uses `window.prompt` or `window.confirm` for department, member, role, module, field, action, flow, dict, template, OpenAPI, work config, Agent policy, SSO policy, and publish/rollback actions.
- `frontend/src/features/platform-admin/platformAdmin.ts` uses `window.prompt` or `window.confirm` for system lifecycle reason, platform role, identity provider, model authorization, identity provider test/publish.
- `frontend/src/features/system-shell/systemShell.ts` uses `window.prompt` for approve/reject/transfer and work creation inputs.
- `frontend/src/features/platform/platformShell.ts` uses `window.prompt` for password change.
- `frontend/src/features/no-member/noMemberAccess.ts` uses `window.prompt` for request reason.

Impact:

- Browser prompts are not the approved prototype's form/drawer/result interaction model.
- They hide validation, disabled reasons, trace/audit feedback, async task state, and layout hierarchy.

Recovery mapping:

- `REC-P0-006`
- `REC-P0-007`
- `REC-P0-009`

### FE-R0-003 Inert Pagination Outside Runtime Records And System Todos

Evidence:

- `frontend/src/features/platform/platformShell.ts` `createPagination` renders previous/next buttons without handlers.
- `frontend/src/features/platform-admin/platformAdmin.ts` `createPagination` renders previous/next buttons without handlers.
- `frontend/src/features/system-admin/systemAdmin.ts` `createPagination` renders previous/next buttons without handlers.
- `frontend/src/features/system-shell/systemShell.ts` has functional callback pagination for system todo, but work task/report pagination renders inert buttons through another `createPagination(page)`.
- `frontend/src/shared/table.ts` also renders pagination buttons without action wiring.

Impact:

- Users see controls that imply functionality but do not change data.
- This is `STATE_BROKEN` for platform workspace, admin pages, message center, and work management.

Recovery mapping:

- `REC-P0-007`
- `REC-P0-009`

### FE-R0-004 Missing Tenant Switch UI

Evidence:

- `frontend/src/app/routes.ts` has no tenant switch route.
- `frontend/src/features/platform/platformShell.ts` lists systems and can pass `tenantId` to `switchToSystem`, but no dedicated tenant switch control or tenant list UI was found.

Impact:

- Multi-tenant behavior cannot be accepted from UI without further implementation evidence.

Recovery mapping:

- `REC-P0-003`

### RELEASE-R0-001 No Fresh Release Package In Workspace

Evidence:

- `Test-Path release` previously returned false in this workspace.

Impact:

- Prior release evidence cannot prove current workspace deliverability.
- `REC-P0-001` and `REC-P0-008` must rebuild and verify from source.

Recovery mapping:

- `REC-P0-001`
- `REC-P0-008`

## Result

R0 static audit converts the initial P0 rows from `UNKNOWN` to concrete non-pass labels in `docs/recovery/current-product-audit.md`.

The repeatable script `scripts/recovery-r0-static-audit.ps1` generated `docs/evidence/recovery/r0-static-audit-current.json` with current status `FAIL`:

- `promptOrConfirmCount=61`
- `duplicateSidebarTargetCount=8`
- `inertPaginationCount=4`
- `releaseDirectoryExists=false`

Next required work:

1. Runtime/browser audit for R1 shell and auth.
2. Implement `REC-P0-009` cleanup in parallel with R1/R4 as specified by `docs/recovery/fix-batches.md`.
3. Do not close any task until browser/API/database/permission evidence is stored under `docs/evidence/recovery/`.
