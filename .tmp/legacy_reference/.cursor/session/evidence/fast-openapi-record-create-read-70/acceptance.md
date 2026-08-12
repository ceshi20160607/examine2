# FAST-OPENAPI-RECORD-CREATE-READ-70 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-04T02:44:22+08:00`
- Delivery mode: functionality-first parallel slice; responsive/visual
  hardening and external edit, lifecycle and file operations remain separate
  follow-up work.

## Delivered

- Added signed `POST /openapi/v1/modules/{moduleCode}/records`, paged collection
  `GET`, and detail `GET` routes. Route templates require `record.write` or
  `record.read` application scopes plus the service member's current
  `system.runtime.access`; the module owner then enforces dynamic module
  permissions and current data scope.
- Added a narrow module-owned OpenAPI record facade that reuses the published
  runtime schema, writable/readable field projection, canonical value handling,
  row-scope predicates and record views. Requests cannot override tenant,
  system, owner, audit, reserved, unknown or non-writable fields.
- Added DRAFT/ACTIVE external create semantics without weakening the existing
  WEB path. Direct ACTIVE creation performs the current required/unique checks,
  writes one history/audit result with source `OPENAPI`, and emits one
  `RECORD_CREATED` flow event.
- Scoped stable create idempotency by system, tenant, service member,
  application and module. Exact replay with a fresh nonce returns the original
  record; the same key with changed canonical values returns a conflict and
  creates no second record, audit or event.
- Reused current OpenAPI authentication, nonce, rate-limit and call-log
  infrastructure. Logs retain only parameterized route, method, result, status,
  latency, request and trace identities; bodies, signatures, app keys, secrets,
  idempotency keys and business values are absent.
- Added copyable signed curl examples for create, list and detail to the
  application administration page. Examples use static placeholders only and
  never read an application's key or secret reference.

## Verification

- Focused backend verification passed `31/31`: module OpenAPI facade/audit/create
  `8`, OpenAPI canonical authentication/filter/route policy `19`, and web
  controller `4`.
- Full affected backend regression passed Core `31/31`, Platform `43/43`, Module
  `360/360` and OpenAPI `29/29`, totaling `463/463` with no failure, error or
  skip.
- All `13` Maven modules production- and test-compiled successfully.
- The real Spring Boot + MySQL 8.0.44 + Redis 7.4 HMAC journey passed `1/1` in
  `139.1s` test time. It also applied and exercised the existing `79` Flyway
  migrations; this slice added no schema migration.
- Full frontend verification passed `88` files / `398` tests, Vue typecheck and
  the production build over `5743` transformed modules.
- Strict UTF-8/mojibake, Active framework, cadence `7/7`, both VS4 machine
  contracts, and staged/unstaged diff checks passed; the endpoint ledger remains
  `43` because these operational routes are outside the frozen VS4 field API.

## Demonstrated journey

An administrator grants a service member temporary SELF-scoped module create and
view permissions and creates a file-backed-secret OpenAPI application. A signed
ACTIVE create succeeds; an exact replay with a fresh nonce returns the same
record, while changed values under the same idempotency key conflict. Signed
detail and list return only the service member's own row; another owner and a
second tenant are hidden. The journey proves exactly one OPENAPI owner audit and
one history row, sanitized parameterized call logs, immediate read revocation,
then restores the service role and completes the pre-existing long Flow/report
journey without semantic leakage.

## Integration corrections

- Marked the public controller constructor for Spring injection while retaining
  a package-local unit-test seam.
- Made the real journey create its own SELF data scope instead of assuming an
  optional seed row.
- Refreshed authorization snapshots at role-publication boundaries and restored
  the reused service role's original ALL scope and permissions before continuing
  the existing journey.
- Aligned paged assertions with the established runtime `rows` response field.

## Deferred

- signed external record update and lifecycle transitions
- signed external file upload, list and download
- external approval decisions and configuration administration
- OAuth/OIDC client credentials and generated SDK publication
- responsive, accessibility, animation and exhaustive visual-state hardening
