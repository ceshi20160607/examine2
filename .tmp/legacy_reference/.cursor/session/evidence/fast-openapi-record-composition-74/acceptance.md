# FAST-OPENAPI-RECORD-COMPOSITION-74 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-04T04:51:23+08:00`
- Delivery mode: functionality first; responsive, accessibility, SDK generation
  and exhaustive visual hardening remain deferred.

## Delivered

- Added signed, paged OpenAPI reads for published `RELATION` and `SUBTABLE`
  record fields, delegated to the existing module-runtime composition owner.
- Added idempotent relation and subtable mutation routes with application,
  tenant, member, module, record, composition-kind and field isolation. Exact
  replay returns the prior receipt; changed payload conflicts without a second
  version, history row, audit event, reference recalculation or outbox effect.
- Enforced `record.read` / `record.write`, live `system.runtime.access`, module
  view/update, field read/write, record row scope, relation-target scope and
  subtable operation capabilities without duplicating domain authorization.
- Added stable string-ID projections and receipts, parameterized call-log route
  templates, correlation propagation and payload/credential log sanitization.
- Added four placeholder-only signing examples to the OpenAPI administration
  page: relation read/mutate and subtable read/mutate.

## Integration corrections

- Replaced the oversized composition idempotency scope key with a stable
  `oac:` SHA-256 digest of the complete canonical identity. This remains within
  the existing 128-character persistence column while preserving every
  isolation dimension and leaving existing create/update replay keys unchanged.
- Reconciled the data-source catalog with published subtable schema. Record-level
  fields retain positive logical IDs; unavailable nested columns retain their
  stable non-zero negative runtime identity and are exposed under a parent
  namespace such as `line_items.route`. This prevents valid record/subtable code
  reuse from invalidating the whole module catalog.

## Verification

- Focused backend verification passed `60/60` across module facade/runtime/audit,
  data-source catalog interoperability, OpenAPI route/signature/filter/call-log
  policy and Web controller binding/error contracts.
- Affected backend regression passed Core `31/31`, Module `373/373` and OpenAPI
  `46/46`, totaling `450/450` with no failure, error or skip.
- All `13` Maven child modules test-compiled successfully.
- Real Spring Boot + MySQL 8.0.44 + Redis 7.4 authenticated HMAC journey passed
  `1/1` in `150.5s`, with all `79` existing migrations applied. It proved empty
  reads, relation/subtable writes, exact replay, changed-payload conflict, stable
  projections, singular version/history/audit effects, tenant/row/target hiding,
  permission revocation and sanitized parameterized logs, then completed every
  downstream data-source, dashboard, KPI, report and Flow assertion.
- Frontend targeted verification passed `1` file / `4` tests; full verification
  passed `88` files / `398` tests, typecheck and the production build over
  `5,743` transformed modules.
- Staged and unstaged `git diff --check`, strict UTF-8/mojibake checking, base and
  active framework validators, all `7` cadence validation cases and the VS4
  machine-contract validator passed.

## Demonstrated journey

A signed application reads an empty published relation, adds an authorized
target, replays the same request, rejects a changed replay and reads the stable
projection. It then adds and reads a typed subtable row with the same replay and
conflict guarantees. Hidden records, tenants and relation targets remain hidden;
revoked mutation/read permissions take effect immediately; audit and call logs
contain the stable source and parameterized route without credentials or values.

## Deferred

- bulk composition mutation and relation candidate-search publication
- external direct writes to reference or derived materialized values
- OAuth/OIDC client credentials, callback subscriptions and generated SDKs
- responsive, accessibility, animation and exhaustive visual-state hardening
