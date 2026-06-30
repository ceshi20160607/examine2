# Real System Replan

Time: 2026-06-24 16:30 Asia/Shanghai

## Leader Verdict

The previous G0-G10 plan produced contract-first/sample behavior and runnable packaging, but it did not produce the real frontend-backend separated business system required by the prototype.

This plan reopens coding. The project cannot be called complete until backend, frontend, database, permissions, workflow, messages, work management, AI Agent, deployment, and E2E all run against real persisted data.

## Current Correction Batch: REAL-G2

Goal: complete real authentication, platform/system context, and schema readiness.

Current status:

- REAL-G0 persistence-capable generated base layer is complete.
- REAL-G1 database connectivity probe is complete, but the first version only proved `SELECT 1`.
- REAL-G2 auth code has started:
  - Auth login/register no longer returns fixed sample ids.
  - BCrypt password hashing is wired.
  - Login audit persistence is wired.
  - Register-with-system creates account, system, tenant, member, system super admin role, role-member relation, and account-member binding.
  - Health now reports `schema=MISMATCH` when required tables/columns are missing.
- Runtime acceptance is blocked by database schema/permission:
  - Accessible schemas are older than current `sql/init.sql`.
  - The current `examine` DB user can connect but cannot `CREATE DATABASE`, `CREATE TABLE`, or `ALTER TABLE`.
  - Migration SQL is recorded at `sql/migrations/20260624_legacy_identity_schema_compat.sql`.

REAL-G2 is not accepted until register/login/system-switch pass against a current schema database.

## Completed Correction Batch: REAL-G0

Goal: replace the fake generated backend base layer with a real persistence-capable base.

Completed in this batch:

- Added MyBatis-Plus dependency management to `backend/pom.xml`.
- Added MyBatis-Plus Spring dependency for generated base services.
- Added MyBatis-Plus starter and MySQL runtime driver to `backend/examine-web/pom.xml`.
- Added `@MapperScan("com.unique.examine.**.base.mapper")` to the web entry.
- Added `backend/examine-web/src/main/resources/application.yml` with datasource, MyBatis mapper locations, logging, upload, security, async, and AI Agent runtime config.
- Updated release external `backend/application.yml` to expose database settings and make sample mode default to false.
- Changed the generator so generated base files use:
  - `@TableName`
  - `@TableId`
  - `BaseMapper<T>`
  - `IService<T>`
  - `ServiceImpl<Mapper, Entity>`
  - `saveEntity` instead of conflicting with MyBatis-Plus `save(T)`.
- Regenerated all 92 table base layers.
- Verified backend compile with JDK 21 and Maven.

## Reopened Development Phases

1. REAL-G1: database initialization and connectivity
   - Verify target MySQL connection.
   - Create or validate database schema from `sql/init.sql`.
   - Add non-destructive schema check script.
   - Add backend startup evidence against the real database.

2. REAL-G2: authentication and platform/system context
   - Replace auth/account/context sample behavior with persisted account/session/token data.
   - Implement password hashing and login audit persistence.
   - Implement system switch context from persisted account-member bindings.

3. REAL-G3: RBAC and organization
   - Replace role/member/org sample behavior with persisted CRUD.
   - Enforce backend menu/button/action/field/data-scope permission checks.
   - Add permission evidence for four roles.

4. REAL-G4: module configuration and runtime records
   - Replace module config sample returns with persisted module group/module/field/dict/scene/action data.
   - Replace runtime record sample rows with persisted dynamic records, values, relations, attachments, history, drafts, sorting, filtering, pagination, and row detail.

5. REAL-G5: workflow, todo, messages, logs, import/export, upload
   - Persist flow definitions, versions, instances, approval tasks, actions, todos, messages, notification templates, business logs, login logs, async tasks, upload files, and import/export jobs.

6. REAL-G6: work management and AI Agent
   - Persist projects, project tasks, plain tasks, comments, collaborators, events, calendar items, daily reports, report sources, Agent authorizations, policies, sessions, confirmations, and audit logs.

7. REAL-G7: frontend API integration
   - Replace `frontend/src/mocks/**` and hard-coded shells with real API calls.
   - Restore the old frontend's route/store/API structure where useful.
   - Ensure login, register, password reset, platform workspace, system shell, system backend, todos, messages, work management, and runtime module pages are API-bound.

8. REAL-G8: real E2E and release
   - Run backend with real database.
   - Run frontend against backend.
   - Validate ordinary system member, system admin, platform member, and platform admin.
   - Rebuild release package with jar-level `application.yml` and `server.sh`.

## Completion Gate

Do not report "coding complete" until all of the following are true:

- No P0 page is driven by mock/sample data.
- Backend sample methods are either removed or explicitly limited to tests/fixtures.
- Database schema is initialized and backend starts against that database.
- Frontend is API-bound for the primary flows.
- Permission checks are enforced by backend, not only UI hiding.
- E2E evidence covers login, system switching, module list/detail, approval, todo, message, work task/report, admin config, and platform/system boundaries.
- Release package starts, restarts, reports status, and stops through `backend/server.sh` with external config.

## 2026-06-25 Release Audit Correction

- Release config defaults were corrected to `docs/user_setting.md`: backend port `9999`, database `examine2`, Redis `192.168.0.211:6379/db10`.
- Production frontend remains same-origin `/api` and backend config no longer carries a frontend origin.
- Redis is now a verified dependency for health and authentication token storage.
- Access and refresh tokens are Redis-backed; runtime account resolution comes from `Authorization: Bearer ...`, not browser-provided `X-Account-Id`.
- Clean-context audits still found P1 incompleteness: static frontend admin shells, in-memory draft/sequence services, sample tenant behavior, placeholder ops workflows, and `user_script_passed=false`. These keep the project in release-candidate/self-check status, not whole-project completion.

## 2026-06-25 Default Platform Root Correction

- Real deployment default account is `admin / 123123aa`; historical prototype fixture `platform_admin_root` must not override the deployable login account.
- Backend startup now ensures the `admin` account, `PLATFORM_ROOT` built-in platform role, and account-role binding exist.
- The seed is configurable through `unexamine.bootstrap.platform-root.*` and environment variables; password reset on every restart is disabled by default.
- Verified from the release package on port `9999`: health returned database/schema/redis `UP`, `admin / 123123aa` login returned `PLATFORM_ROOT`, admin created a platform system, and real G8 API E2E passed.

## 2026-06-25 Tenant Persistence Correction

- `TenantService` no longer returns a hard-coded default tenant or non-persisted create/update responses.
- Tenant list/create/update now read and write `un_plat_tenant`.
- Creating a tenant also creates a tenant-scoped system super admin member, role, role-member relation, and account-member binding for the creator, so the new tenant can be switched into immediately.
- Verified from the release package on port `9999`: admin created a multi-tenant system, created a second tenant, listed both tenants, switched into the second tenant with a new `systemMemberId`, and full real G8 API E2E still passed.

## 2026-06-25 Flow Canvas Validation Correction

- `FlowDefinitionService` no longer auto-fills a sample flow canvas when a flow is created with `canvas=null`; new flows persist an empty draft canvas until the user saves real nodes and edges.
- Publish now depends on real persisted canvas validation. A blank canvas flow returns `FIELD_VALIDATION_FAILED / 流程发布检查未通过`.
- Real G8 API E2E was updated to submit an explicit approval-to-end canvas before publishing, so approval runtime evidence no longer relies on hidden sample data.

## 2026-06-25 Frontend Runtime Demo Cleanup

- The runtime record page no longer falls back to local vehicle prototype rows, vehicle modules, or vehicle import/export demo data.
- `runtimeData.ts` now only provides empty structural placeholders; runtime module navigation, list schema, fields, rows, detail drawer, and batch limits come from real APIs.
- The record table renders dynamic columns from backend `listSchema.columns`, and empty/unavailable systems show a configuration guidance empty state instead of demo records.
- Release/frontend source scan found no old vehicle fixture keywords after rebuilding the package.

## 2026-06-25 Production Naming And Config Cleanup

- Removed unused `unexamine.deploy.sample-mode` from backend source config and deployment templates because no production code reads it.
- Replaced remaining production fallback names `sec_placeholder` and `trace_sample` with neutral runtime names `sec_unconfigured` and `trace_missing`.
- Renamed API model fields with demo-like wording to production-oriented names:
  - `DictTypeVO.sampleItems` -> `previewItems`
  - `DesensitizePolicyVO.sample` -> `preview`
  - `IdentityProviderTestRequest.sampleLoginName` -> `testLoginName`
- Latest release verification after rebuild:
  - release health returned `database/schema/redis UP`.
  - real G8 API E2E passed with default admin `admin / 123123aa` and `PLATFORM_ROOT`.
  - blank flow publish negative smoke passed with `FIELD_VALIDATION_FAILED`.
  - clean release zip was regenerated after stopping the verification process.

## 2026-06-25 Draft/Sequence Persistence And Admin Frontend API Cleanup

- `DraftService` now persists runtime drafts in `un_module_dynamic_draft` and reads them back by system, tenant, module and creator instead of keeping process memory state.
- `SequenceService` now persists runtime automatic numbering in `un_module_dynamic_sequence` and allocates ranges with database atomic `LAST_INSERT_ID(current_no + count)` updates instead of `ConcurrentHashMap` / `AtomicLong`.
- Platform admin and system admin frontend pages were rewritten from static shells to API-bound views:
  - Platform admin loads systems, roles, SSO identity providers, AI Agent model authorizations, health and platform logs.
  - System admin loads departments, members, roles, module groups, modules, dict types, flows, SSO policy, work config, Agent policies and system logs.
  - Platform workbench loads platform todos/messages from APIs and its create-system button calls the real platform system creation API.
- Verification from the release package on port `9999`:
  - health returned database/schema/redis UP.
  - real G8 API E2E passed with default admin `admin / 123123aa` and `PLATFORM_ROOT`.
  - frontend admin API smoke passed for all platform/system admin page dependencies.
  - draft save/read, continuous sequence allocation and blank-flow publish negative verification passed.
  - static scans found no old vehicle fixture keywords, mojibake markers, or production `sample/mock/stub` leakage.

## 2026-06-27 User Runtime Feedback And Recovery Task Cards

User runtime feedback reports that the started product still has crowded pages, confused functions, prototype mismatch, and incomplete frontend-backend integration. This overrides any broad "release-candidate" or "self-checked" completion language.

Next work must not continue broad coding. It must enter recovery task-card mode:

- Recovery entry: `docs/recovery/README.md`
- Current product audit: `docs/recovery/current-product-audit.md`
- P0 task cards: `docs/recovery/p0-task-cards.md`
- Prototype implementation matrix: `docs/recovery/prototype-to-implementation-matrix.md`
- Generated vs coded API boundary: `docs/recovery/generated-vs-coded-api.md`
- Fix batches: `docs/recovery/fix-batches.md`

Recovery rule:

- A task is not complete because a page exists, an API returns 200, generated CRUD exists, or build passes.
- A task is complete only when the assigned role can finish the business action in the running system and the result is visible, persisted, permission-checked, state-handled, and repeatable through a script.
- Further coding is allowed only for tasks listed in `docs/recovery/fix-batches.md`.
