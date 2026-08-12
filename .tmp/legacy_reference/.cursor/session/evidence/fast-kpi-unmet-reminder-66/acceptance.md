# FAST-KPI-UNMET-REMINDER-66 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-04T00:07:49+08:00`
- Delivery mode: small functionality-first slice; scheduled recalculation,
  repeated/escalated reminders, external channels and responsive/visual
  hardening remain separate later work.

## Delivered

- Added narrow core contracts for idempotent member-message delivery and active
  KPI reminder recipient lookup. KPI remains independent of event and platform
  persistence details.
- Added the event adapter that creates an inbox fact with a deterministic
  calculation/recipient delivery key under the caller's mandatory transaction.
  Exact and concurrent replay therefore reuse the existing inbox fact.
- Added a strict platform recipient bridge for department targets. It resolves
  only current active, non-deleted, non-expired members inside the exact system
  and tenant, then returns a bounded sorted/deduplicated set.
- Added KPI reminder status gating: only newly persisted `AT_RISK` and `MISSED`
  calculations notify. `ACHIEVED`, `CALCULATION_FAILED`, exact command replay
  and concurrent insertion replay do not create another message.
- MEMBER recipients use the active subject, ROLE recipients use the immutable
  calculation membership snapshot, and DEPARTMENT recipients use the bounded
  current directory result. Empty recipient sets succeed without delivery.
- Reminder identity, title/body and `/systems/{systemId}/kpis` link are derived
  from persisted KPI/calculation snapshots. A notifier failure escapes the
  calculation fallback and rolls back the new transactional calculation fact.

## Verification

- Focused cross-module verification passed `22/22`: Core `3`, Platform `3`,
  Module `12` and Event `4` tests covering delivery replay, recipient lookup,
  status gating, snapshot recipients and failure propagation.
- Final affected backend regression passed Core `31/31`, Platform `43/43`,
  Module `289/289` and Event `30/30`, totaling `393/393` with no failure,
  error or skip.
- The real Spring Boot + MySQL 8.0.44 + Redis 7.4 authenticated HTTP journey
  passed `1/1`. It calculated a missed target, replayed the same command,
  observed exactly one unread `KPI_UNMET` inbox item with the expected target
  link, resolved the linked KPI runtime period and proved tenant hiding.
- No schema change was needed; the real journey applied the existing `76`
  Flyway migrations. The journey reactor also production/test-compiled all
  `13` child Maven modules.
- Batch 66 changes no frontend files, so it reuses the immediately preceding
  unchanged green frontend baseline: `79` files / `376` tests, typecheck and
  Vite production build over `5729` transformed modules.
- Staged and unstaged `git diff --check`, strict UTF-8/mojibake validation, the
  instance framework structure validator and both VS4 machine-contract
  validators passed.

## Demonstrated journey

An administrator updates an existing member KPI target below threshold and
performs an explicit calculation. The calculation is persisted as `MISSED` and
the represented member receives one unread system inbox reminder in the same
transaction. Replaying the command returns the same calculation and message;
the message link opens the current-system KPI page at the calculation period,
while another tenant can read neither the KPI nor the reminder.

## Integration corrections

- Kept existing five-argument `KpiService` construction compatible for focused
  legacy tests while Spring uses the new recipient and notifier collaborators.
- Distinguished a newly inserted calculation from an idempotent repository
  replay before delivering, preventing notification duplication without a
  second transaction or client action.
- Kept reminder delivery outside the calculation-failure fallback catch so a
  delivery exception cannot be converted into a committed failed-calculation
  row that has lost its required inbox messages.

## Deferred

- scheduled or periodic KPI recalculation
- repeated reminders, escalation, quiet hours and configurable policies
- email, SMS, Webhook or template administration
- report definitions, manual/scheduled exports and deliveries
- responsive, accessibility, animation and exhaustive visual-state hardening
