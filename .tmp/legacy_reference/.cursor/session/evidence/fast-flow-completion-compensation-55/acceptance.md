# FAST-FLOW-COMPLETION-COMPENSATION-55 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-07-31T23:20:00+08:00`
- Delivery mode: functionality first; responsive, accessibility and exhaustive
  visual hardening remain deferred to the final unified pass.

## Delivered

- Definitions now freeze `completionFailurePolicy=MANUAL_RETRY|COMPENSATE`.
  `MANUAL_RETRY` remains the compatible default; `COMPENSATE` requires exactly
  one immutable external-task, Webhook or exact-version subflow compensation
  configuration for every completion member.
- A terminal forward failure atomically closes remaining forward work, cancels
  active children, retains successful facts and creates one durable reverse
  compensation plan for succeeded members only.
- Compensation executions run one ordinal at a time in deterministic reverse
  order. External-task leases, Webhook delivery and exact-version subflows use
  separate identities, attempts and child linkage from their forward work.
- Failed compensation remains `PENDING/COMPENSATING`; administrator retry works
  for all three execution kinds while forward retry is rejected after the saga
  changes direction.
- All compensations succeeding produces exactly one sanitized
  `COMPLETION_COMPENSATED` history fact and terminates the parent as
  `TERMINATED/COMPLETED`.
- V8.45 freezes policy on draft/version/instance and adds restrictive,
  tenant-scoped compensation, attempt and subflow-run tables plus worker scan
  indexes and strict state constraints.
- Definition, instance and history APIs expose the sanitized policy and reverse
  progress. The frontend supports policy/config editing, progress inspection,
  child navigation and failed-compensation retry.

## Verification

- Final affected backend regression passed: Core `25/25` and Flow `362/362`,
  totaling `387/387` with no failure, error or skip.
- Batch55 concentrated runtime/API verification passed `10/10`; linear and
  branch ordered-stage compatibility checks passed `9/9` after integration
  correction.
- Backend test compilation passed across all `12` Maven modules.
- Real MySQL 8.4 schema integration passed `1/1`; all `67` Flyway migrations
  applied through V8.45 and the new tables, columns, defaults, checks, indexes
  and restrictive foreign keys matched the frozen contract.
- Real Spring Boot + MySQL 8.0.44 + Redis 7.4 authenticated HTTP journey passed
  `1/1`, including the complete existing Flow journey and the new saga path.
- Frontend targeted verification passed `2` files / `121` tests; the canonical
  full suite passed `54` files / `285` tests. Typecheck and Vite production build
  passed with only the existing non-blocking chunk-size warning.
- Base structure, active instance, VS4 machine contract, strict UTF-8 encoding,
  state JSON parsing and all `7` cadence self-tests passed.

## Demonstrated journey

An administrator publishes a parent containing a parallel external task and
exact-version subflow under `COMPENSATE`. The external member succeeds and the
child is rejected. The parent closes forward work, enters `COMPENSATING` and
creates compensation only for the successful external member. Its first
compensation attempt reaches terminal failure, forward retry is rejected,
administrator retry creates a new attempt, completion terminates the parent and
history contains exactly one sanitized compensation fact. Database readback
confirms the immutable compensation and attempt rows.

## Integration corrections

- Replaced a metadata-generated history check with an explicit MySQL-safe V8.45
  constraint; this avoids invalid `information_schema` assumptions and escaped
  prepared SQL on current MySQL versions.
- Corrected JDBC argument order and completion-phase persistence so the frozen
  failure policy and `COMPENSATING` state survive real database round trips.
- Added production wiring from forward external/Webhook/subflow coordinators to
  the compensation coordinator and made administrator retry execution-kind
  neutral.
- Removed `final` from transactional compensation services so Spring can create
  their production proxies.
- Reset the active evidence policy when ordered linear or branch stages advance,
  preserving the existing decision-evidence journey after the new completion
  state fields were added.

## Deferred

- Human-decision rollback, arbitrary module mutation rollback and distributed
  transactions.
- Quorum/any-success/race completion joins, loops and dynamic graph membership.
- Responsive, accessibility and exhaustive visual-state hardening.
