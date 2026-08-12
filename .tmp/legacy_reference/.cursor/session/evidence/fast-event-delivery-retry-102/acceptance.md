# FAST-EVENT-DELIVERY-RETRY-102 Acceptance

- Verdict: PASS
- Accepted at: 2026-08-05T13:02:00+08:00
- Scope: bounded durable recovery for temporary Event INBOX delivery failures.

## Delivered behavior

- Preserved the existing synchronous first delivery attempt, public receipt and
  delivery dedupe behavior.
- Added strict temporary-failure classification. Business validation,
  unpublished/disabled templates, disabled recipient preferences and invalid
  recipients remain permanent outcomes and never enqueue retry.
- Unexpected runtime failures persist only
  `MESSAGE_DELIVERY_TEMPORARY_FAILURE` and the fixed safe message
  `Notification delivery failed safely`; raw exception text is not stored.
- The first non-replay temporary failure creates exactly one existing
  `DurableJobFacade` job with type `EVENT_DELIVERY_RETRY`, owner
  `EVENT_DELIVERY/{deliveryId}` and two compensation attempts.
- Added a closed, bounded payload codec containing only delivery id plus the
  already-validated notification command. Decode requires the exact key set and
  reconstructs `AggregateRef` and `ResultNotificationFacade.Command` so all
  existing scope/size/path validation runs again.
- Added repository lookup and a single SQL CAS that reopens only an exact
  temporary `FAILED` delivery at the expected attempt, increments the count and
  refuses delivered, skipped, permanent, stale, exhausted or missing rows.
- Retry uses the delivery's recorded immutable template version, re-checks the
  current recipient preference and owner eligibility, and continues to use
  `deliveryId` as the inbox message id.
- Added a scheduled worker with a 30-second lease and fixed five-second
  backoff. Its durable result contains only delivery id, status, message id and
  attempt count.
- Isolated `createForDelivery` with `REQUIRES_NEW`. A failed inbox statement can
  now roll back independently while the outer delivery failure and retry job
  commit; idempotent message creation safely handles ambiguous recovery.
- No schema change was required: existing `un_event_message_delivery_log` and
  `un_sys_job` contain the necessary state.

## Verification

- Focused repository CAS tests: `3/3` passed.
- Focused payload and worker tests: `6/6` passed.
- Combined retry/service focused tests: `13/13` passed before the final added
  exhaustion assertion.
- Full final Core module: `81/81` passed.
- Full final Event module: `58/58` passed.
- Real Spring/MySQL/Redis `ImportJourneyIntegrationTest`: `1/1` passed, zero
  failures/errors/skips (`172.2s` test time; `3:48` Maven total).
- The real journey applied `100/100` Flyway migrations, installed a temporary
  target-scoped one-shot MySQL trigger, forced the first inbox INSERT to fail,
  and proved the unique durable job recovered on total attempt `2` with the
  same delivery/message id, one inbox row and terminal `SUCCEEDED` job. The
  trigger and MyISAM gate table were removed in `finally`.
- The same real journey retained all existing import, export, print, template,
  preference, skipped-replay and delivered-dedupe assertions.
- Unit coverage proves two further temporary failures stop at total attempt `3`,
  keep safe failure text and create no inbox message.
- Unit coverage also republishes a newer template between failure and recovery
  and proves retry renders the original recorded version.
- Production Spring context construction passed after explicitly identifying
  the worker's injectable constructor.
- Project progress, active framework, strict UTF-8, cadence, VS4 machine/API
  contracts and `git diff --check`: PASS. Only existing line-ending conversion
  warnings were emitted.

## Boundaries

- No EMAIL, SMS, webhook or provider transport.
- No generic Outbox dispatcher, producer redesign, manual retry/list API,
  operations UI, new table/column/index or frontend change.
- No responsive work and no package/install/jar command. Project package
  attempts remain `0/3`.
