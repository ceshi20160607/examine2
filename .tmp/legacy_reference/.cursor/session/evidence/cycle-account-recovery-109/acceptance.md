# CYCLE-ACCOUNT-RECOVERY-109 acceptance

- Verdict: PASS
- functional/package verdict: `passed`
- 240-minute cadence verdict: `passed` — cycle opened at `2026-08-06T12:40:00+08:00`; the single accepted package and package cold-start verification closed before the physical deadline `2026-08-06T16:40:00+08:00`.
- accepted at: `2026-08-06T13:36:00+08:00`
- formal checkpoint impact: none; this is a rolling cycle snapshot under `CP1_FUNCTIONAL_BASELINE`

## Delivered

- Added the complete anonymous account-recovery journey: login entry, account/email/mobile identifier request, non-enumerating response, independent reset page, independent success page, and fixed login success notice.
- Added `AccountRecoveryMailFacade` in Core and a real SMTP implementation in Event. Mail links use a trusted deployment `publicBaseUrl`; arbitrary caller URLs are not accepted. Disabled or incomplete SMTP keeps the application runnable and returns `UNAVAILABLE` without leaking account state.
- Added Plat recovery-token persistence and lifecycle. Only SHA-256 token hashes are stored; raw tokens are never returned by the API, written to the database, persisted in frontend state/storage, or retained in the address bar.
- Added token TTL, one-time atomic consumption, replacement-token revocation, IP and identifier-hash rate limits, enumeration-safe request behavior, disabled-account handling, Argon2 password replacement, all-session revocation, cookie clearing, and audit events.
- Added migration `V8_79_0__plat_password_recovery.sql` and explicit runtime configuration for SMTP, public URL, TTL, and rate limits.
- Added a real HTTP integration journey using MySQL 8, Redis 7, Undertow, and an in-process SMTP protocol server: the test captured and decoded the actual MIME message, followed its recovery link, reset the password, proved old sessions and old password invalid, logged in with the new password, rejected token reuse, and verified audit records.
- Corrected an integration-found mapper SQL defect (`&lt;&gt;` in an annotation) before acceptance, and added stable form field associations found during browser review.

## Verification

| gate | result |
|---|---|
| Backend affected cumulative Core tests | `83 passed`, zero failures |
| Backend affected cumulative Plat tests | `91 passed`, zero failures |
| Backend affected cumulative Event tests | `63 passed`, zero failures |
| Backend affected cumulative total | `237 passed`, zero failures |
| Real account-recovery integration journey | `1 passed`; fresh `101`-migration schema; real SMTP MIME capture and new-password login covered |
| Frontend cumulative tests | `111 files / 588 tests passed`, zero failures |
| Recovery-focused frontend recheck after browser correction | `3 files / 9 tests passed` |
| Frontend typecheck and production build | passed; `5776` modules transformed |
| Backend production package | `15` Maven reactor projects / `14` implementation modules, `BUILD SUCCESS` |
| Browser desktop journey | passed at `1440x900`; login notice, forgot request, non-enumerating response, reset form, query-token removal, success and return links verified; console had zero warnings/errors |
| Final rolling-package cold start | health `UP`; clean database `101/101` migrations; current version `8.79.0`; process command used packaged `backend/examine-web.jar` |

Browser evidence:

- `login-notice.png`
- `forgot.png`
- `forgot-generic-response.png`
- `reset.png`
- `success.png`

## Accepted rolling package

- attempt: `20260806-133047`
- archive: `.cursor/session/packages/CYCLE-ACCOUNT-RECOVERY-109/CYCLE-ACCOUNT-RECOVERY-109-20260806-133047.zip`
- staging: `.cursor/session/packages/CYCLE-ACCOUNT-RECOVERY-109/20260806-133047/`
- files: `261`
- packaged frontend files: `157`
- SHA-256: `aedd38e533cc56d30ff1510ac750cd488984f043fde22040c83801107a511e20`

Exactly one candidate was built and accepted for this cycle. The cold-start database, Redis data, Java processes, and cycle-specific containers were removed after verification and are not recoverable.

## Completed / remaining / deferred

Completed in this cycle:

- `AR109-A` Core mail contract and Event SMTP adapter/configuration.
- `AR109-B` Plat token lifecycle, rate limits, APIs, audits, password change, session revocation, and migration.
- `AR109-C` login entry, recovery API client, three independent public pages, routes, styling, and focused tests.
- `AR109-I` cross-module wiring and real SMTP/MySQL/Redis/HTTP journey.
- `AR109-P` cumulative gates, production builds, browser review, the single accepted package, and clean package cold start.

Remaining outside this cycle:

- Evidence-ranked functional gaps still represented by `P10_EVIDENCE_RANKED_FUNCTION_GAPS`, with file object-storage/multipart/preview selected as the next cohesive module cycle.
- Formal P0-P9 phase acceptance evidence and all formal checkpoints remain not ready; a rolling cycle package does not promote them.
- Release clean-install/upgrade/security gates and final user acceptance remain outstanding.

Deferred by explicit boundary:

- SMS recovery, MFA recovery, enterprise SSO recovery, and channel-management workflows.
- Responsive/mobile breakpoint matrix, exhaustive loading/error/permission screenshots, cross-module visual unification, and the full accessibility audit (`P10_H1_UI_HARDENING`).
