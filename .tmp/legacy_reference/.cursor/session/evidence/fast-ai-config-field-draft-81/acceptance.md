# FAST-AI-CONFIG-FIELD-DRAFT-81 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-04T16:35:13+08:00`
- Delivery mode: functionality first; broad responsive, accessibility and
  exhaustive visual adaptation remain deferred to release hardening.

## Delivered

- Added the Core-owned `AiConfigurationFieldFacade` and Module-owned sealed
  prepare/execute boundary for adding exactly one scalar field to an existing
  module draft. Prepare performs no write; execute rechecks live identity,
  authorization, module and draft revision before the owner mutation.
- Added strict `CONFIG_FIELD_DRAFT` planning for `TEXT`, `LONG_TEXT`, `INTEGER`,
  `DECIMAL`, `BOOLEAN`, `DATE` and `DATETIME`, with type-specific bounded
  settings, clarification handling and fail-closed unknown keys/types.
- Added account/system/tenant/member/session/turn/policy/provider/prompt-bound
  proposal, attempt and event state machines. Owner commands are AES-GCM sealed;
  stored previews and results redact field code/name and never persist command
  plaintext.
- Added nested proposal detail, confirm and reject APIs. Confirm requires an
  idempotency key; exact replay returns the same result, changed replay conflicts,
  and a stale draft fails terminally without adding a field.
- Added function-first frontend preview, confidence, clarification, confirm,
  reject, terminal error and draft readback states. Responsive and exhaustive
  visual hardening remains deferred.
- Added `V8_64_0` with isolated configuration proposal, attempt and immutable
  event tables and their identity, state, payload, JSON and referential checks.
- Fixed nullable non-numeric field settings so TEXT and other non-numeric plans
  retain nullable precision/scale without Java auto-unboxing, with a regression
  test. Unexpected guarded Agent failures now emit server-side correlation-safe
  diagnostics while business denials remain normal controlled outcomes.

## Verification

- Affected backend modules passed Core `47/47`, Module `412/412` and AI `58/58`,
  totaling `517/517` with no failure, error or skip.
- Web Agent controller contract tests passed `3/3`.
- Focused configuration tests passed the owner/parser/proposal/facade suites,
  including all seven scalar types, nullable TEXT metadata, sealing, replay,
  authorization and stale revision behavior.
- The MySQL 8.4 schema test passed `1/1`, applied all `86` migrations through
  `V8_64_0`, and validated the three new tables and their no-plaintext contract.
- The real Spring Boot + MySQL + Redis + local OpenAI-compatible HTTP-provider
  journey passed `1/1` in `187.7s`, including the complete existing Flow
  regression.
- All `14` Maven child modules test-compiled successfully.
- Full frontend verification passed `98` files / `445` tests; focused
  configuration verification passed `4` files / `22` tests. Typecheck and the
  production build passed over `5,761` transformed modules.
- Staged and unstaged `git diff --check`, strict UTF-8/mojibake checking, all
  `7` cadence cases and both VS4 contract validators passed; compatibility
  remains at `43` endpoints.

## Demonstrated journey

The published system policy allows `CONFIG_FIELD_DRAFT`. An administrator asks
for INTEGER and TEXT fields, receives two strict previews while the field count
is unchanged, then explicitly confirms the INTEGER proposal. The owner creates
exactly one `NUMBER` draft field with precision `38` and scale `0`; exact replay
returns the same field. The second proposal fails with a stale-draft result after
the first mutation and creates no field. Active configuration version/checksum,
publish count and runtime records remain unchanged, and stored AI evidence
contains neither prompts, field codes/names nor command plaintext.

## Deferred

- dictionary/select options and page section/layout configuration drafts
- Flow, report and print configuration drafts where required
- record-context and work task/daily-report Agent journeys
- platform task lifecycle/message writes if required
- responsive, accessibility, animation and exhaustive visual-state hardening
- clean-environment release packaging and user final acceptance
