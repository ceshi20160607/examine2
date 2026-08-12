# FAST-OPENAPI-CALL-LOG-READ-92 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-05T01:49:00+08:00`
- Delivery mode: function-first parallel OpenAPI-owner, frontend and focused
  integration lanes. Responsive, accessibility and exhaustive visual work
  remain deferred.

## Delivered

- Added the native read-only endpoint
  `GET /api/v1/systems/{systemId}/admin/openapi/applications/{applicationId}/call-logs`
  over the existing `un_openapi_call_log` owner table. It reuses the current
  SYSTEM context and `openapi.application.manage` permission and resolves the
  application in the authenticated system and tenant before any log query.
- Added exact uppercase result and HTTP-method filters, bounded page/size,
  truthful totals and strict `created_at DESC,id DESC` ordering. Count and page
  SQL always include `application_id`, use parameters only and reuse the
  existing `idx_openapi_call_application` index.
- Returned only the frozen safe projection: id, credential version, route,
  method, result, HTTP status, latency, request/trace ids, canonical observed
  IP and creation time. Application-key hash, key/secret/signature/nonce,
  payloads, headers and identity fields cannot enter the response.
- Added the functional call-log section to the existing OpenAPI application
  detail drawer. It loads only after a real detail selection and supports the
  two filters, paging, refresh, request-race fencing and loading/empty/error
  states. No mutation, export, route, breakpoint or visual redesign was added.
- Added no migration, index, audit store, acknowledgement, deletion, replay,
  retry or retention mutation. Reads do not change call-log, application,
  credential, rate-bucket or nonce facts.

## Verification

- Final Core and OpenAPI regression passed `70/70` and `52/52`, totaling
  `122/122`. All `14` Maven modules and their tests compiled successfully.
- The focused Spring Boot + MySQL + Redis real OpenAPI foundation test passed
  `2/2` in `112.9s` test time (`2:15` reactor total). Flyway validated all
  existing `94` migrations and correctly performed no migration work.
- Frontend full verification passed `101` files / `491` tests. Typecheck and
  production build passed over `5,766` transformed modules; the only message
  was the existing non-blocking large-chunk warning.
- Strict UTF-8/mojibake, seven cadence cases, reusable base Structure, instance
  Active, canonical project progress, both VS4 `43`-endpoint validators and
  staged/unstaged `git diff --check` all passed.
- Exactly three project package checkpoints remain configured with zero
  attempts. This acceptance is not a package checkpoint.

## Demonstrated journey

The existing OpenAPI journey creates a managed application and produces real
successful and signature-rejected signed calls. The native ALL, SUCCESS, POST
and SUCCESS+POST pages are compared row-for-row and total-for-total with the
owner table, including exact ordering, nullable credential versions and safe
field boundaries.

A real application belonging to another tenant in the same system is hidden
with `OPENAPI_APPLICATION_NOT_FOUND`. Disabling the live management permission,
bumping the authorization epoch and refreshing the session produces
`PERMISSION_DENIED`; the permission is then restored. Before/after snapshots
prove that the native reads leave all call-log, application, credential,
rate-bucket and nonce counts and version sums unchanged.

## Completion boundary

This accepts only application-scoped OpenAPI call-log reading. It does not
accept global unauthenticated-attempt search, export, retention controls,
replay, a package checkpoint, responsive/visual hardening, release gates or
final user acceptance. The next function-first wave remains selected from the
evidence-ranked message delivery/preferences and other owner gaps.
