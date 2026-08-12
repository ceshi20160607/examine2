# FAST-OPENAPI-RECORD-FILE-72 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-04T03:34:41+08:00`
- Delivery mode: functionality-first parallel slice; detach, bundle, large or
  resumable uploads and responsive/visual hardening remain separate work.

## Delivered

- Added signed JSON upload at `POST
  /openapi/v1/modules/{moduleCode}/records/{recordId}/files` with canonical
  Base64 validation and a decoded-content limit of 512 KiB, preserving the
  existing 1 MiB HMAC request-buffer boundary.
- Added signed bounded metadata paging and verified binary download. Responses
  expose stable file/reference facts, safe disposition, media type, length and
  SHA-256, but never expose a storage object key or encoded content.
- Kept the file domain as the only asset/reference/content owner. The OpenAPI
  facade delegates current module view, row data scope, tenant, exact reference,
  `file.create`, `file.reference` and `file.read` checks to the established
  runtime file service; the owner now verifies stored length and SHA-256 on
  every direct download.
- Added application/record-scoped upload idempotency. Current authorization is
  revalidated before replay; an exact fresh-nonce replay returns the original
  file while changed name, media type or bytes conflict before another asset,
  reference, audit or outbox event.
- Added singular `OPENAPI` operation audit and
  `RUNTIME_RECORD_FILE_ATTACHED` outbox facts. Neither payload contains object
  keys, file bytes, Base64, signatures or idempotency keys.
- Added strict parameterized OpenAPI routes: upload requires `file.write`, list
  and content require `file.read`, and all three require the current service
  member's `system.runtime.access` before the file owner applies its rules.
- Added an independent Web controller for file transport, keeping existing
  record routing untouched, plus upload/list/download placeholder-only curl
  examples. Administration examples increased from nine to twelve and do not
  read live app keys or secret references.

## Verification

- Focused backend verification passed `50/50`: file facade and owner `15`,
  OpenAPI authentication/canonical/filter/route policy `26`, and record/file
  Web controllers `9`.
- Full affected backend regression passed Core `31/31`, Module `368/368`, File
  `40/40` and OpenAPI `36/36`, totaling `475/475` with no failure, error or
  skip.
- All `13` Maven modules production- and test-compiled successfully.
- The real Spring Boot + MySQL 8.0.44 + Redis 7.4 HMAC journey passed `1/1` in
  `144.7s` test time. It applied all existing `79` Flyway migrations; this
  slice added no schema migration.
- Full frontend verification passed `88` files / `398` tests, Vue typecheck and
  the production build over `5743` transformed modules. Focused application
  page verification passed `1` file / `4` tests.
- Strict UTF-8/mojibake, Active framework, cadence `7/7`, both VS4 machine
  contracts and staged/unstaged diff checks passed; the frozen VS4 endpoint
  ledger remains `43`.

## Demonstrated journey

A SELF-scoped external application creates an ACTIVE record, uploads one signed
text file, replays the upload with a fresh nonce, and receives the same file id.
Changing bytes under the same key returns `IDEMPOTENCY_CONFLICT`. Metadata
paging returns the single reference and binary download returns exact bytes,
length, disposition and SHA-256. Database assertions prove one asset, one
record reference, one OPENAPI audit and one outbox event. Another owner, another
tenant and an unreferenced file remain hidden. Removing file-write permission
immediately rejects upload; removing module/file-read permission immediately
rejects metadata access. Sanitized call logs contain only parameterized route
templates and no credential, filename, Base64 or idempotency material, after
which the complete existing Flow/report journey still succeeds.

## Integration correction

- The first extended long-journey run reached the new file assertions but one
  extra role-publication login later consumed the test environment's existing
  authentication limit. File-write revocation was merged into the already
  required record-mutation permission publication, removing the redundant
  login without changing or relaxing production rate limits. The complete
  journey then passed.

## Deferred

- external file detach/delete, ZIP bundle and large/resumable upload
- signed flow status reads and external relation/reference/subtable maintenance
- external approval decisions and configuration administration
- OAuth/OIDC client credentials and generated SDK publication
- responsive, accessibility, animation and exhaustive visual-state hardening
