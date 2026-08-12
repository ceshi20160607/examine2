# FAST-OPENAPI-RECORD-UPDATE-LIFECYCLE-71 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-04T03:07:24+08:00`
- Delivery mode: functionality-first parallel slice; batch mutations, external
  files and responsive/visual hardening remain separate follow-up work.

## Delivered

- Added signed optimistic `PUT /openapi/v1/modules/{moduleCode}/records/{recordId}`
  with only `expectedVersion` and writable business `values`. It reuses the
  current module schema, field capability, canonical value, version-conflict,
  row-scope and transactional record update owner.
- Added signed `:activate`, `:archive`, `:unarchive`, `:trash` and
  `:restore-from-trash` actions. Each delegates to the established runtime
  state machine and its current update, delete and published action permissions.
- Added application/record-scoped idempotency for every mutation. The identity
  includes action, expected version and sorted update values, so fresh-nonce
  exact replay returns the original response while changed values or actions
  conflict before another history, audit or event.
- Preserved WEB behavior while making owner audit source `OPENAPI` for external
  updates and transitions. Existing runtime update/status/delete events remain
  transactionally singular.
- Extended route policy with parameterized PUT and lifecycle action templates,
  requiring application `record.write` plus current service-member
  `system.runtime.access`; the module owner enforces the corresponding dynamic
  module permission and data scope.
- Extended the application page from three to nine placeholder-only copyable
  signed curl examples. Update includes version plus values; lifecycle bodies
  include only version, and no example reads live app keys or secret references.

## Verification

- Focused backend verification passed `44/44`: module create/update/lifecycle
  facade and owner contracts `16`, OpenAPI authentication/canonical/filter/route
  policy `22`, and web controller `6`.
- Full affected backend regression passed Core `31/31`, Platform `43/43`, Module
  `368/368` and OpenAPI `32/32`, totaling `474/474` with no failure, error or
  skip.
- All `13` Maven modules production- and test-compiled successfully.
- The real Spring Boot + MySQL 8.0.44 + Redis 7.4 HMAC journey passed `1/1` in
  `145.6s` test time. It applied all existing `79` Flyway migrations; this slice
  added no schema migration.
- Full frontend verification passed `88` files / `398` tests, Vue typecheck and
  the production build over `5743` transformed modules. Focused OpenAPI page
  verification remained `1` file / `4` tests.
- Strict UTF-8/mojibake, Active framework, cadence `7/7`, both VS4 machine
  contracts, and staged/unstaged diff checks passed; the frozen VS4 endpoint
  ledger remains `43`.

## Demonstrated journey

A SELF-scoped service application updates its ACTIVE record. A fresh-nonce exact
replay remains at version 1, changed values under the same key conflict, and a
new-key stale version is rejected. The record archives, replays the archive,
unarchives, moves to trash and restores to ACTIVE with versions 2..5; a second
DRAFT record activates through the signed action route. Another owner and tenant
remain hidden. The journey proves six successful OPENAPI audits and history rows
for the first record, all five parameterized action log templates, credential/
body/idempotency-value log absence, immediate write revocation, immediate read
revocation, role restoration and successful continuation of the pre-existing
long Flow/report journey.

## Integration corrections

- Used the existing published action permission model instead of inventing
  module-level archive/delete aliases. The journey publishes `archive`,
  `unarchive` and `restore_trash` actions, then grants their generated permission
  codes alongside update/delete.
- Kept mutation authorization before the owner mutation and idempotency replay;
  disabling a current role therefore revokes even a previously valid client
  immediately.
- Reused one application/module/record idempotency scope while placing the exact
  action in the canonical identity, preventing cross-action key reuse.

## Deferred

- signed external batch edit/transfer/archive/trash
- signed external relation, reference and subtable maintenance
- signed external file upload, list and download
- external approval decisions and configuration administration
- OAuth/OIDC client credentials and generated SDK publication
- responsive, accessibility, animation and exhaustive visual-state hardening
