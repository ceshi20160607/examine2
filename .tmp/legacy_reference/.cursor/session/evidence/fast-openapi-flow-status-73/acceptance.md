# FAST-OPENAPI-FLOW-STATUS-73 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-04T04:13:06+08:00`
- Delivery mode: functionality-first parallel slice; approval mutations, bulk
  status/history, callbacks, relation maintenance and responsive hardening stay
  in separate nodes.

## Delivered

- Added signed `GET /openapi/v1/flow/instances/{instanceId}` with application
  scope `flow.read`, current service-member permission `flow.instance.read`,
  strict positive-long path parsing and parameterized call logging.
- Added a Flow-owned read service that resolves instances through the existing
  system/tenant-scoped `ApprovalWorkflowService`. Record-bound instances also
  pass through the canonical `RuntimeRecordAccessFacade.requireView`, preserving
  current module-view, row-data-scope and tenant hiding semantics.
- Added a stable 13-field external projection containing only instance,
  definition/version, business key, lifecycle/progress timestamps and cursors,
  approval/completion phases, and optional record binding. It excludes approver
  identities, decisions, evidence, comments, completion payloads and
  compensation internals; all exposed IDs are strings.
- Added an independent Web controller with strict authenticated
  application/machine-session binding, current Flow permission enforcement,
  owner-domain error translation and request/trace correlation.
- Added a placeholder-only signed status curl example. Administration examples
  increased from twelve to thirteen and still do not read live app keys or
  secret references.

## Verification

- Focused backend verification passed `43/43`: Flow owner/projection `8`,
  OpenAPI authentication/canonical/filter/route policy `31`, Web controller `4`.
- Full affected backend regression passed Core `31/31`, Flow `379/379` and
  OpenAPI `41/41`, totaling `451/451` with no failure, error or skip.
- All `13` Maven modules production- and test-compiled successfully.
- The real Spring Boot + MySQL 8.0.44 + Redis 7.4 HMAC journey passed `1/1` in
  `147.3s` test time (`2:50` reactor time). It applied all existing `79` Flyway
  migrations; this slice added no schema migration.
- Full frontend verification passed `88` files / `398` tests, Vue typecheck and
  the production build over `5743` transformed modules. Focused application
  page verification passed `1` file / `4` tests.
- Strict UTF-8/mojibake, Active framework, cadence `7/7`, both VS4 machine
  contracts and staged/unstaged diff checks passed; the frozen VS4 endpoint
  ledger remains `43`.

## Demonstrated journey

A SELF-scoped signed application creates an ACTIVE dynamic record, starts a
record-bound approval and queries the new endpoint. The response returns stable
PENDING progress and the exact binding without internal approver or execution
details. A different owner's bound instance is hidden by the canonical record
scope, another tenant's instance is hidden by the Flow repository, and disabling
`flow.instance.read` rejects the same request immediately. Call-log assertions
prove the single parameterized route template and exclude credential, signature,
record-value and idempotency material. The complete existing Flow, record, file,
dashboard, KPI and report journey then succeeds.

## Integration corrections

- Removed `final` from the transactional status service so Spring can apply its
  established CGLIB transaction proxy; production authorization was unchanged.
- Routed controller owner failures through the existing `FlowHttpErrors`
  boundary so cross-tenant not-found remains a stable 404 instead of a 500.
- Used tenant-local approvers and an independent, cleaned-up record for isolation
  fixtures, avoiding pollution of later approval-state version assertions.
- Replaced one pre-existing immediate report-notification assertion with a
  bounded five-second wait because export completion and notification delivery
  are asynchronous; permanent delivery failures still fail the journey.

## Deferred

- external approve/reject/withdraw/terminate and task actions
- external instance list/history, bulk status and callbacks
- external relation/reference/subtable maintenance
- external Flow definition and approval-configuration administration
- OAuth/OIDC client credentials and generated SDK publication
- responsive, accessibility, animation and exhaustive visual-state hardening
