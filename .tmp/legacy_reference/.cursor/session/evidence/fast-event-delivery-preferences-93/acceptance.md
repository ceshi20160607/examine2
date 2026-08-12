# FAST-EVENT-DELIVERY-PREFERENCES-93 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-05T02:25:30+08:00`
- Delivery mode: function-first parallel Event-owner, frontend and existing
  Import-journey lanes. Responsive, accessibility and exhaustive visual work
  remain deferred.

## Delivered

- Added a native current-member delivery-preference catalog at
  `GET/PUT /api/v1/systems/{systemId}/event/delivery-preferences`. It reuses
  the live SYSTEM context, authenticated tenant/member identity and existing
  `event.message.access` permission; no caller identity or administrator
  override exists.
- Added one explicit owner row per system, tenant, member, stable template and
  fixed INBOX channel. Missing rows resolve to enabled version zero. The first
  state change creates version one, actual changes increment once, same-state
  requests are no-op and stale versions fail without writes.
- Integrated preferences only with the existing template-driven
  `ResultNotificationFacade` delivery path. Disabled recipients keep one
  terminal delivery log with status SKIPPED, null message id and the sanitized
  recipient-disabled reason; exact dedupe replay remains singular. Direct
  member messages retain their existing contract.
- Added a lazy-loaded functional preference drawer inside the existing message
  inbox. It shows only the safe template catalog and fixed INBOX channel,
  performs controlled row-level updates, preserves state on failure, reloads
  conflicts and fences responses across system/tenant/member changes.
- Added `V8_73_0__event_delivery_preference.sql` with exact owner uniqueness,
  member-tenant/template foreign keys and channel/boolean/version checks. No
  new permission, authz-epoch update, channel, retry table, delivery-log
  alteration or second audit store was introduced.

## Verification

- Final Core and Event regression passed `70/70` and `44/44`, totaling
  `114/114`. All `14` Maven modules and their tests compiled successfully.
- The independent MySQL 8.4 historical schema test passed `1/1` in `66.62s`,
  executed `53` migrations after V8.20, validated all `95` migrations through
  V8.73 and proved the repeat migration performs zero work.
- The real Spring Boot + MySQL + Redis Import/Export HTTP journey passed `1/1`
  in `121.4s` test time (`2:23` reactor total).
- Frontend full verification passed `101` files / `497` tests. Typecheck and
  production build passed over `5,766` transformed modules; the only message
  was the existing non-blocking large-chunk warning.
- Strict UTF-8/mojibake, seven cadence cases, reusable base Structure, instance
  Active, canonical progress, both VS4 `43`-endpoint validators and
  staged/unstaged `git diff --check` all passed.
- Exactly three project package checkpoints remain configured with zero
  attempts. This acceptance is not a package checkpoint.

## Demonstrated journey

The journey materializes the existing stable template catalog and proves
repeat preference reads leave template, version, preference, message and
delivery facts unchanged. The current member disables the real export-success
template, executes a successful export and receives one auditable SKIPPED
delivery with no inbox message. Replaying the same dedupe key after re-enable
still returns that terminal SKIPPED row; a distinct future export is DELIVERED
and creates exactly one inbox message.

The same member switches to another tenant and receives an independent
implicit enabled version-zero value; its override cannot change the original
tenant row. Disabling `event.message.access`, bumping the authorization epoch
and refreshing the live session makes both GET and PUT return
`PERMISSION_DENIED` without changing Event facts, after which the permission
is restored.

## Integration corrections

- Preference catalog initialization is allowed to provision the existing
  deterministic template defaults, so the catalog service uses a writable
  transaction; repeat reads remain zero-write.
- PUT resolves authentication, SYSTEM context and permission before parsing
  the exact body, preserving `AUTH_REQUIRED` and permission-error precedence.
- The historical schema assertion advanced from 52 to 53 post-V8.20
  migrations and from 94 to 95 total migrations after V8.73 was added.

## Completion boundary

This accepts only current-member preferences for template-driven INBOX
notifications. It does not add new delivery channels, provider secrets,
retry/backoff/dead-letter processing, direct-message preferences, delivery-log
administration, a package checkpoint, responsive/visual hardening, release
gates or final user acceptance. Runtime analytics Agent reads and the remaining
evidence-ranked owner gaps continue next.
