# P4-B3 Query And Saved Views

## 1. Node Identity

- node: `P4-B3-QUERY-SAVED-VIEW`
- lifecycle: `S3_IMPLEMENTATION`
- delivery_phase: `P4_DYNAMIC_RUNTIME`
- owner: `pm`
- planner: `planner`
- owners: `dba, backend, frontend`
- reviewers: `product, architect, uiux, test`
- verifier: `leader`

Dynamic status, timing and next task are read only from `.cursor/session/state.json`.

## 2. Delivers

An authorized ordinary member can use one published runtime module as a real work list: search readable searchable text fields, build typed filters, apply up to three typed sorts, change page and page size, switch among permitted active/archive/trash scopes, choose visible columns, and share or restore the same list state through the URL. The current member can create, rename, update, apply and delete up to 100 personal saved views. Results remain data-scope and field-permission filtered, report the total for the selected scope, reject invalid query input explicitly, and survive reload and packaged-backend restart.

## 3. Does Not Deliver

- No cross-page selection, batch command, batch snapshot or selection toolbar. Those belong to `P4-D3`.
- No my-drafts inbox, favorites, recent access, global command center or global record search. Those belong to `P4-D4`.
- No unique/auto-number engine, new field-type package, relation, subtable, team, comment, history, file, Flow, task center, report, OpenAPI or AI behavior.
- P4-B3 does not claim complete P4 runtime, release readiness or final-system acceptance.

## 4. Remaining And Deferred

- remaining_goal_items_ref: `state.active.slice.remainingGoalItems`
- deferred_to: `P4-C1`
- long_range_contract: `.cursor/session/rebuild/vs4-task-plan.md`, `.cursor/session/rebuild/vs4-api-contract.schema.json` and `.cursor/session/rebuild/vs4-db-contract.json`
- completion_boundary: this node passing closes only query and personal saved-view behavior; it does not close P4 or the final project goal

## 5. Tasks

| task | kind/risk | estimate | execution | singleOutcome | status_ref |
|---|---|---:|---|---|---|
| P4-B3-01 | implementation/critical | 240m | serial, contract first | typed query executor, search index, scope permissions and strict MySQL tests | `state.active.taskExecution[P4-B3-01]` |
| P4-B3-02 | implementation/critical | 240m | serial, consumes 01 | owner-scoped versioned saved-view persistence and API | `state.active.taskExecution[P4-B3-02]` |
| P4-B3-03 | implementation/standard | 240m | serial, consumes 01/02 | desktop/mobile query builder, URL state, saved-view workflow and restart demo | `state.active.taskExecution[P4-B3-03]` |

Every task is estimated at no more than 240 minutes. An estimate is a planning ceiling, not a kill timer: overrun requires a checkpoint and reforecast, and the active task continues until its outcome passes or is genuinely blocked.

## 6. Query API Contract

The instance keeps its established version prefix. The machine-contract endpoint therefore resolves as:

- `POST /api/v1/systems/{systemId}/runtime/modules/{moduleCode}/records:query`
- body is exactly `{schemaVersionId,page,size,recordScope,q,filter,sort,columns,viewId}`; unknown properties fail with `QUERY_INVALID`
- `page` starts at 1; `size` defaults to 50 and is limited to 200; no cursor pagination is introduced
- `recordScope=active|archived|trash`, default `active`; it maps only to ACTIVE, ARCHIVED or TRASHED respectively
- `q` is null or 2..100 normalized characters and searches only currently readable, published, searchable TEXT/TEXTAREA fields; no leading wildcard or sensitive-field search
- `filter` is null or a typed AST: `PREDICATE`, `AND`, `OR`, `NOT`; maximum depth 5, maximum 20 leaves, AND/OR have 1..20 children, NOT has exactly one child
- `sort` has at most three items and each item is `{fieldCode,direction,nulls}`; only fields advertised as sortable are accepted
- `columns` is unique and contains only readable published fields; an empty array means the schema list defaults
- `viewId` is null or the current member's saved view; explicit non-null q/filter/sort/columns in the request override the saved value
- response extends the existing page projection with `queryHash`, `querySnapshotToken`, `invalidNodes` and request correlation metadata from the API envelope

The existing `GET /records` remains a compatibility adapter for accepted P4-A/P4-B browser paths during this node, but the workbench moves to `records:query`. It may expose only the old fixed ACTIVE/ARCHIVED and metadata-sort subset and is not evidence for P4-B3 completion.

## 7. Typed Filter And Sort Contract

- Server validation resolves every field code against the requested active schema snapshot and its returned capabilities. Unknown, hidden, unreadable, non-filterable, non-sortable or unsupported fields fail before SQL runs.
- TEXT: `EQ`, `CONTAINS`, `PREFIX`, `EMPTY`; TEXTAREA: `CONTAINS`, `EMPTY`; NUMBER: `EQ`, `GT`, `GTE`, `LT`, `LTE`, `BETWEEN`, `EMPTY`; DATE/DATETIME: `EQ`, `BEFORE`, `AFTER`, `BETWEEN`, `EMPTY`; RADIO: `EQ`, `IN`, `EMPTY`; MEMBER/DEPARTMENT: `HAS_ANY`, `EMPTY`.
- Operator value shape is strict: EMPTY takes a boolean, BETWEEN takes exactly two ordered typed values, IN/HAS_ANY take 1..100 unique reference values, and scalar operators take one canonical typed value.
- Filters execute through parameterized `EXISTS` predicates over `un_module_record_index`; search executes through generated `un_module_record_search` tokens. Request values never become SQL identifiers or fragments.
- Sort uses the same typed index and appends stable `record_id` ordering. Null ordering is explicit. The schema advertises sortable only when the field has the required active index capability.
- Data-scope SQL remains part of both row and count queries. Field-read denial removes columns/search candidates and cannot be used as a count or existence side channel.

## 8. Scope And Permission Contract

- Every query requires `module.{moduleCode}.view` plus the effective data scope.
- Archived scope additionally requires `module.{moduleCode}.archive.view`; trash scope additionally requires `module.{moduleCode}.trash.view`. Missing scope permission returns 403 and the frontend does not show that scope.
- Personal views require `runtime.saved_view.manage`, do not bypass module view/data scope, and are owner-only by current member.
- A forward migration registers the saved-view system permission, backfills the two module-scope permissions for published modules and grants new permissions only to the built-in system owner. Ordinary roles remain denied until explicitly configured.
- Existing demo/provisioning roles used for acceptance receive only the permissions needed for their stated journey; root identity is not accepted as ordinary-member evidence.

## 9. Search And Query Persistence Contract

- A forward Flyway migration creates `un_module_saved_view` and the indexes/constraints required by the frozen DB contract. Executed migrations are never edited.
- Search rows are generated in the same transaction as typed values for readable-capable searchable TEXT/TEXTAREA definitions, use deterministic normalized tokens and SHA-256 token hashes, and track the record lifecycle status.
- P4 data already present before this migration receives a bounded, restart-safe backfill; rerun cannot create duplicate tokens.
- Query JSON is canonicalized before SHA-256 `queryHash` generation. The snapshot token is an opaque server value bound to system, tenant, member, module, schema version, query hash and issue time; P4-B3 does not use it for batch mutation.
- Count and row results use the same validated predicate. No in-memory post-filtering, hidden full-table scan or client-side fake total is accepted.

## 10. Saved View Contract

Base path is `/api/v1/systems/{systemId}/runtime/saved-views`.

- `GET ?moduleCode={moduleCode}` returns at most 100 current-member views.
- `POST` with `{moduleCode,name,query,columns}` creates one view and returns 201. It requires CSRF and `Idempotency-Key`.
- `PUT /{viewId}` with `{expectedVersion,name,query,columns}` uses CAS and returns the new version. It requires CSRF and `Idempotency-Key`.
- `DELETE /{viewId}` with `{expectedVersion}` soft-deletes by CAS. It requires CSRF and `Idempotency-Key`.
- Active names are unique per system, tenant, member and module after trim; names are 1..100 characters. A member/module has at most 100 active views.
- Saved JSON retains unknown or retired field nodes. Applying a stale view returns valid query results plus stable JSON-pointer-like `invalidNodes`; invalid nodes are shown and excluded only after explicit user confirmation to repair/save the view. They are never silently erased.
- Another member receives 404 for owner-only view ids, preventing existence disclosure. Reused idempotency keys and stale versions follow the existing conflict contract.

## 11. URL And UI Contract

- Workbench query keys are fixed to `viewId,q,page,size,recordScope,sort,filter,columns,record,mode,draft`. Unknown or invalid query keys are removed by replace-navigation and produce one recoverable `URL_QUERY_INVALID` notice.
- `filter`, `sort` and `columns` use unpadded base64url of canonical JSON. Total URL length is limited to 4096 characters; larger state must be saved as a view before it can be shared.
- Explicit URL q/filter/sort/columns override saved-view values. Changing q/filter/sort/viewId/recordScope resets page to 1. Changing size preserves the original first visible row when possible.
- Desktop/compact use a dense table with stable columns and visible pagination. Mobile uses record cards and a full-screen filter surface; it does not shrink the desktop table.
- Search, filters, sort, columns, scope and saved-view actions have loading, empty, permission-denied, invalid-query, network and retry states. No successful state is shown before server readback.
- Opening and closing detail/create/edit preserves the current list URL, page and focus. Browser back/forward and reload restore the same list/query/detail state.

## 12. Stable Errors

- `QUERY_INVALID` (422): malformed AST, value, field, operator, schema version, page, size or URL-derived query.
- `QUERY_SCOPE_FORBIDDEN` (403): archived/trash scope permission is missing.
- `SAVED_VIEW_LIMIT_REACHED` (409), `SAVED_VIEW_NAME_CONFLICT` (409), `SAVED_VIEW_VERSION_CONFLICT` (409), `SAVED_VIEW_NOT_FOUND` (404).
- Existing `PERMISSION_DENIED`, `RECORD_SCHEMA_STALE`, `IDEMPOTENCY_CONFLICT` and `IDEMPOTENCY_KEY_REQUIRED` remain stable.
- Rejected queries do not execute a fallback broad query and do not report partial totals.

## 13. Acceptance Demo

- ordinary member searches two text fields and receives only scope-authorized rows; an unreadable searchable field neither matches nor leaks a count
- combine nested AND/OR/NOT filters across text, number, date and reference fields; verify typed values, totals, explicit null ordering and stable pagination
- switch active/archive/trash; unauthorized scope is hidden and a forged request is rejected; authorized totals match MySQL state
- create, apply, rename, update and delete an owner-only saved view; stale CAS, duplicate name, 101st view and another-member access all fail correctly
- retire a saved field, apply the old view, display `invalidNodes`, keep valid nodes and repair only after confirmation
- copy URL, reload and use browser back/forward on desktop and mobile; query, columns, scope, page and detail context restore without horizontal page overflow
- restart the packaged backend and log in again as the ordinary member; query and saved views read back from MySQL with the same totals and definitions
- browser evidence has no unexpected console/page errors, HTTP 5xx or client-computed fake totals

## 14. Independent Gate

DBA verifies forward migration, constraints, query plans, token backfill and owner uniqueness. Backend submits strict parser, parameterized SQL, data/field/scope permission negatives, totals, stable ordering, idempotency/CAS and MySQL/restart evidence. Frontend submits real API URL normalization, desktop/mobile query, saved-view and invalid-node repair evidence. Test independently reproduces positive and negative paths. Leader accepts P4-B3 only after all three tasks and API, DB, UI, browser and restart evidence pass; controls without server query semantics, a hard-coded demo set, in-memory filtering or a saved-view stub are not completion.
