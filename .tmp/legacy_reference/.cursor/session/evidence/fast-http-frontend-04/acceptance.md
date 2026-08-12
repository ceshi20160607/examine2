# Fast HTTP and frontend batch 04 acceptance

## Verdict

`PASS_REAL_HTTP_AND_FIRST_FRONTEND_ROUTES`

P4-C5 and the Work/Event/File APIs now have real-port, authenticated MySQL/Redis journeys. Work, Event and File also
have built frontend functional routes. Responsive and exhaustive visual hardening remain intentionally deferred.

## Delivered

- P4-C5 real HTTP journey publishes TENANT, AUTO_NUMBER, CREATED_BY, CREATED_AT, UPDATED_BY and UPDATED_AT fields,
  rejects client spoofing, writes canonical values, increments auto numbers and reads the values back after updates.
- Work real HTTP journey covers create, assign, complete, reopen, tenant-scoped list and cross-tenant hiding.
- Event real HTTP journey covers inbox list, unread count, read-all, archive and cross-tenant hiding.
- File real HTTP journey covers multipart upload, metadata, binary download, reference protection, unreference,
  delete, persistent local storage and cross-tenant hiding.
- Frontend routes:
  - `/systems/:systemId/tasks`
  - `/systems/:systemId/messages`
  - `/systems/:systemId/files`
- The shared API client now supports FormData envelopes and binary downloads while preserving CSRF, request IDs,
  idempotency metadata and stable JSON errors.
- Work/Event/File pages reload when the active tenant changes; no previous-tenant list state is retained.

## Verification

- Combined real HTTP suite: `FeatureApiJourneyIntegrationTest` and `SystemFieldJourneyIntegrationTest`, 2/2 passed
  with RANDOM_PORT Undertow, MySQL 8.0.44, Redis 7.4 and all 23 migrations.
- Frontend unit suite: 13 files, 44 tests passed.
- Frontend production build: typecheck and Vite build passed; all three feature views were emitted as lazy chunks.
- Previous batch baseline remains: 72 affected backend tests, 23-migration MySQL 8.4 contract test and 11/11 backend
  Reactor projects passed.

## Honest boundary

- Work and Collab still require raw member IDs because ordinary-member directory search is not exposed.
- Event inbox delivery is proven using a database fixture; production event/outbox producers still need to call the
  internal message creation service.
- File has no list-by-reference endpoint, so the first UI opens the current upload or an explicit file ID.
- Flow has no definition/instance list endpoint, so a refresh-safe Flow frontend must wait for those read APIs.
- Collab and Flow frontend routes, browser E2E, mobile/breakpoint adaptation, accessibility and visual-state matrices
  remain pending.
