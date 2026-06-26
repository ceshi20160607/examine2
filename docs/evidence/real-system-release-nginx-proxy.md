# REAL-G8 Release Config, Redis, Auth, And Nginx Correction

## Scope

- Backend release defaults now follow `docs/user_setting.md`: port `9999`, MySQL `examine2`, Redis `192.168.0.211:6379/db10`.
- Backend config no longer carries a frontend origin; production frontend calls same-origin `/api/...` and Nginx owns the upstream mapping.
- Redis is required for health and access/refresh token storage.
- Runtime APIs resolve account identity from `Authorization: Bearer ...`; `X-Account-Id` is disabled by default and the frontend no longer sends it.
- Backend startup now bootstraps the real default platform root account `admin / 123123aa` with role `PLATFORM_ROOT`.

## Changed Files

- `backend/examine-web/src/main/resources/application.yml`
- `deploy/templates/backend/application.yml`
- `deploy/templates/backend/server.sh`
- `deploy/templates/nginx/unexamine.conf`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/auth/AuthTokenService.java`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/auth/AuthService.java`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/auth/PlatformRootBootstrapService.java`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/common/CurrentAccountProvider.java`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/tenant/TenantService.java`
- `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/definition/FlowDefinitionService.java`
- `backend/examine-web/src/main/java/com/unique/examine/web/config/RequestContextFilter.java`
- `frontend/src/api/client.ts`
- `frontend/src/api/liveData.ts`
- `frontend/src/features/runtime/records/runtimeData.ts`
- `frontend/src/features/runtime/records/runtimeRecords.ts`
- `frontend/src/features/runtime/import-export/importExportPanel.ts`
- `frontend/src/app/app.ts`
- `frontend/src/features/auth/authPages.ts`
- `frontend/src/features/platform-admin/platformAdmin.ts`
- `frontend/src/features/system-admin/systemAdmin.ts`
- `scripts/package-release.ps1`
- `scripts/local-start-release.ps1`
- `scripts/real-g8-api-e2e.ps1`

## Build And Package

- Command: `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\package-release.ps1`
- Backend Maven reactor: PASS with JDK 21.
- Frontend production build: PASS.
- Release directory: `release/unexamine-0.0.1-SNAPSHOT`.
- Release zip: `release/unexamine-0.0.1-SNAPSHOT.zip`.

## Static Release Checks

- `release/unexamine-0.0.1-SNAPSHOT/backend/application.yml`
  - `server.port=${UNEXAMINE_SERVER_PORT:9999}`
  - `spring.datasource.url` defaults to `jdbc:mysql://192.168.0.211:3306/examine2...`
  - `spring.data.redis.host=${UNEXAMINE_REDIS_HOST:192.168.0.211}`
  - `spring.data.redis.database=${UNEXAMINE_REDIS_DATABASE:10}`
  - `unexamine.security.allow-account-id-header=${UNEXAMINE_ALLOW_ACCOUNT_ID_HEADER:false}`
  - `unexamine.bootstrap.platform-root.account-name=${UNEXAMINE_PLATFORM_ROOT_USERNAME:admin}`
  - `unexamine.bootstrap.platform-root.initial-password=${UNEXAMINE_PLATFORM_ROOT_PASSWORD:123123aa}`
  - `unexamine.bootstrap.platform-root.reset-password=${UNEXAMINE_PLATFORM_ROOT_RESET_PASSWORD:false}`
- `release/unexamine-0.0.1-SNAPSHOT/frontend/config.js`

```javascript
window.__UNEXAMINE_API_BASE_URL__ = '';
```

- `release/unexamine-0.0.1-SNAPSHOT/nginx/unexamine.conf`
  - `proxy_pass http://127.0.0.1:9999;`
- Release/source scan found no backend setting for a frontend URL, no matching frontend URL environment variable, no old local database default, and no obsolete backend/frontend smoke ports in deployable config/source paths.
- Release/frontend source scan found no old vehicle prototype fixture keywords such as `sys_vehicle`, `rec_vehicle`, `vehicle-import`, `车牌`, `驾驶员`, `年检`, `维保`, or `维修`.

## Runtime Verification

- Backend started from release jar on port `9999`.
- Direct backend health:
  - `GET http://127.0.0.1:9999/api/v1/health`
  - Result: `status=UP`, `database=UP`, `schema=UP`, `redis=UP`.
- Real API E2E:
  - Command: `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\real-g8-api-e2e.ps1`
  - Result: PASS.
  - Default admin login: `admin / 123123aa` returned `PLATFORM_ROOT`.
  - Flow publishing used an explicit approval-to-end canvas in the E2E script; backend no longer auto-fills a sample canvas when `canvas=null`.
  - Latest run after frontend dynamic runtime cleanup: PASS with `systemId=34`, `moduleId=18`, `flowId=16`, `recordId=17`, `todoTotal=1`, `messageTotal=1`.
  - Raw result: `docs/evidence/real-g8-api-e2e-result.json`.
- Blank flow negative smoke:
  - Created a flow with `canvas=null`.
  - `POST /api/v1/systems/{systemId}/flows/{flowId}/publish` returned `FIELD_VALIDATION_FAILED / 流程发布检查未通过`.
  - Latest run after frontend dynamic runtime cleanup: PASS with `systemId=35`, `flowId=17`.
  - Raw result: `docs/evidence/real-g8-flow-blank-publish-negative.json`.
- Platform root action smoke:
  - `admin / 123123aa` logged in with Bearer token.
  - `POST /api/v1/platform/systems` returned PASS.
  - Created system: `admin_verify_0625130709`, owner account id `25`.
- Tenant persistence smoke:
  - `admin / 123123aa` created system `28`.
  - `POST /api/v1/systems/28/tenants` created tenant `29` with code `branch_0625131340`.
  - `GET /api/v1/systems/28/tenants` returned two persisted tenants.
  - `POST /api/v1/platform/system-switch` switched into tenant `29` with system member `30`.
- Auth boundary:
  - No token `POST /api/v1/platform/system-switch` returns `AUTH_UNAUTHORIZED`.
  - Forged `X-Account-Id` without Bearer token also returns `AUTH_UNAUTHORIZED`.

## Remaining Risk

- `nginx` is not installed on this Windows workstation, so `nginx -t` was not run locally.
- `gates.user_script_passed=false`; the user's final acceptance script/signoff is still pending.
- Destructive lifecycle and operations actions still require confirmation/modal UX before they should be exposed as one-click actions in production.

## Cleanup Requirement

- Local verification starts a Java process and creates runtime logs/pid files under `release/.../backend`.
- Stop the verification process and rerun `scripts/package-release.ps1` before handing over a clean final zip.

## 2026-06-25 Production Naming And Config Cleanup

Additional changed files in this cleanup:

- `backend/examine-web/src/main/resources/application.yml`
- `deploy/templates/backend/application.yml`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/secret/SecretService.java`
- `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/todo/TodoService.java`
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/config/ModuleConfigModels.java`
- `backend/examine-ai-work/src/main/java/com/unique/examine/aiwork/manage/agent/AgentModels.java`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/sso/SsoModels.java`

Cleanup result:

- Removed the unused backend `unexamine.deploy.sample-mode` setting from source and release templates.
- Replaced production fallback IDs `sec_placeholder` and `trace_sample` with neutral `sec_unconfigured` and `trace_missing`.
- Renamed API model fields that carried demo-like wording:
  - `DictTypeVO.sampleItems` -> `previewItems`
  - `DesensitizePolicyVO.sample` -> `preview`
  - `IdentityProviderTestRequest.sampleLoginName` -> `testLoginName`

Latest verification after rebuild and release restart:

- Backend release started on port `9999`.
- `GET http://127.0.0.1:9999/api/v1/health` returned `status=UP`, `database=UP`, `schema=UP`, `redis=UP`.
- `release/.../backend/application.yml` defaults still match `docs/user_setting.md`: port `9999`, database `examine2`, Redis `192.168.0.211:6379/db10`, platform root `admin / 123123aa`, and `allow-account-id-header=false`.
- Real API E2E PASS: default admin `admin / 123123aa` returned `PLATFORM_ROOT`; latest IDs were `systemId=39`, `moduleId=20`, `flowId=21`, `recordId=19`, `todoTotal=1`, `messageTotal=1`.
- Blank flow negative smoke PASS: latest IDs were `systemId=40`, `flowId=22`, publish returned `FIELD_VALIDATION_FAILED / 流程发布检查未通过`.
- Static scan: old vehicle/prototype business keywords had no matches in production backend/frontend/release paths.
- Static scan: `sample/mock/stub` production leakage had no matches except normal frontend DOM `placeholder` property names.
- Static scan: hard-coded backend host had no matches in frontend assets; backend host appears only in Nginx template and deployment README examples.
- Clean package regenerated after stopping local verification: `release/unexamine-0.0.1-SNAPSHOT.zip`, size `34551616`, timestamp `2026-06-25 14:00:21`.
- `release/unexamine-0.0.1-SNAPSHOT/backend` has no `powershell.pid` after cleanup.

## 2026-06-25 Draft/Sequence Persistence And Admin Frontend API Cleanup

Additional changed files in this cleanup:

- `backend/examine-module/src/main/java/com/unique/examine/module/manage/draft/DraftService.java`
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/sequence/SequenceService.java`
- `backend/examine-module/pom.xml`
- `frontend/src/api/liveData.ts`
- `frontend/src/features/platform/platformShell.ts`
- `frontend/src/features/platform-admin/platformAdmin.ts`
- `frontend/src/features/system-admin/systemAdmin.ts`

Cleanup result:

- Runtime form drafts now persist to `un_module_dynamic_draft` and are read back from the database.
- Runtime automatic numbers now persist to `un_module_dynamic_sequence` and allocate ranges through database atomic `LAST_INSERT_ID` updates.
- Platform admin no longer renders hard-coded systems; it reads platform systems, roles, identity providers, AI Agent model authorizations, health and logs from APIs.
- System admin no longer renders static placeholder panels; it reads departments, members, roles, module groups, modules, dictionaries, flows, SSO policy, work config, Agent policies and system logs from APIs.
- Platform workbench no longer renders static platform todo/message cards; it reads platform todo and message APIs. The create-system entry now calls `POST /api/v1/platform/systems`.

Latest verification after rebuild and release restart:

- Package rebuild: PASS with Maven reactor and frontend production build.
- Backend release started on port `9999`.
- `GET http://127.0.0.1:9999/api/v1/health` returned `status=UP`, `database=UP`, `schema=UP`, `redis=UP`.
- Real API E2E PASS: default admin `admin / 123123aa` returned `PLATFORM_ROOT`; latest IDs were `systemId=53`, `moduleId=31`, `flowId=29`, `recordId=22`, `todoTotal=1`, `messageTotal=1`.
- Frontend admin API smoke PASS:
  - Platform admin dependencies: systems, roles, identity providers, model authorizations, logs, todos, messages, health.
  - System admin dependencies: org departments, members, roles, module groups, modules, dict types, flows, SSO policy, work config, Agent policies, logs.
  - Raw result: `docs/evidence/real-g8-frontend-admin-api-smoke.json`.
- Draft/sequence/blank-flow verification PASS:
  - Draft saved and read back with `draftId=draft_verify_0625143303`.
  - Sequence allocated continuous ranges `1-2` then `3-5` with `DB_ATOMIC_UPDATE_LAST_INSERT_ID`.
  - Blank flow publish returned `FIELD_VALIDATION_FAILED / 流程发布检查未通过`.
  - Raw result: `docs/evidence/real-g8-draft-sequence-blank-flow-verification.json`.
- Static scan: old vehicle/prototype business keywords and mojibake markers had no matches in production source or release frontend paths.
- Static scan: `sample/mock/stub` production leakage had no matches in backend/frontend/release paths.
- Frontend API host scan: release frontend uses `window.__UNEXAMINE_API_BASE_URL__ = ''`; backend host appears only in Nginx proxy config, not in frontend request code.
