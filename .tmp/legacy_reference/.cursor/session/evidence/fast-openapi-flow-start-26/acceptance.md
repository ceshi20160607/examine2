# FAST-OPENAPI-FLOW-START-26 Acceptance

## Verdict

PASS at 2026-07-29T01:01:22+08:00.

Batch 26 exposes the accepted Flow start transaction as the first signed
external business mutation. It reuses Batch 25 machine authentication and the
existing Flow runtime; no cookie-session or parallel workflow path was added.

## Delivered behavior

- `POST /openapi/v1/flow/definitions/{definitionId}/instances` is the only new
  external business route. Path matching accepts only a positive long ID and
  records the parameterized route template.
- Authentication requires application scope and current service-member
  permission `flow.instance.start` in addition to signature, timestamp, nonce,
  IP and rate policy.
- Machine system, tenant, account and requester member are authoritative.
  Request JSON cannot override identity or tenancy.
- Omitted definition version selects the latest published snapshot; an explicit
  version starts that immutable published version.
- The signed `Idempotency-Key` is reused by Flow and isolated by system, tenant,
  service member, application, definition and external-start action. Exact
  replay uses a fresh nonce and returns the stored 201 response; changed payload
  returns `IDEMPOTENCY_CONFLICT` without another instance.
- External record bindings use `BindingSource.OPENAPI`, while retaining the
  manual path's record-view authorization and ACTIVE-record requirement.
- Instance/history/task creation, optional projection, idempotency completion
  and success operation audit remain transactional. Projection failure rolls
  everything back and permits retry with the original key.
- Success audits use source `OPENAPI`; sanitized call logs retain application,
  credential and parameterized route evidence without body, signature, app key
  or secret material.

## Verification

- `mvn -pl examine-flow -am test`
  - Core: 16 passed
  - Flow: 172 passed
- `mvn -pl examine-module -am test`
  - Core: 16 passed
  - Module: 152 passed
- `mvn -pl examine-openapi -am test`
  - OpenAPI route/security module: 23 passed
  - Batch 26 unique affected-module total: 363 passed.
- `mvn -pl examine-web -am
  -Dtest=OpenApiFoundationJourneyIntegrationTest
  -Dsurefire.failIfNoSpecifiedTests=false test`
  - Real MySQL/Redis HTTP journeys: 2 passed.
  - Proves no-cookie/no-CSRF signed start, scope rejection, live permission
    invalidation, latest/explicit versions, exact replay with a fresh nonce,
    changed-payload conflict, application-isolated idempotency, cross-tenant
    definition rejection, two durable instances, OPENAPI operation audits and
    sanitized parameterized call logs.
  - The second ordered test rebuilds Spring and keeps Batch 25 durable replay
    protection green after the added business route.
- `npm.cmd test`
  - 42 files, 178 tests passed.
- `mvn -DskipTests test-compile`
  - All 12 backend reactor modules passed.
- Scoped `git diff --check`
  - Passed.

## Demo path

Publish a Flow definition -> sign the external POST -> reject missing scope ->
disable the live member permission and reject -> restore permission -> start
latest -> replay with a fresh nonce and the same business key -> change payload
and receive conflict -> explicitly start version 1 -> try another tenant's
definition and receive not-found -> inspect application-scoped idempotency,
OPENAPI audits and sanitized route-template logs.

## Deferred

- import, scheduled, webhook and anomaly Flow triggers
- external approval decisions and definition administration
- external record/file CRUD beyond Flow start
- responsive, accessibility and exhaustive visual hardening
