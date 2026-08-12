# P9 OpenAPI and integration phase acceptance

- phase: `P9_OPENAPI`
- predecessor: `P8_PHASE_ACCEPTANCE`
- acceptedAt: `2026-08-07T17:16:00+08:00`
- verdict: `PASS`
- successor: `RELEASE_CLEAN_INSTALL_UPGRADE_SECURITY_GATES`

## Frozen requirement audit

Completed P9 outcomes cover application lifecycle/credentials, signed request
verification, nonce/replay/rate-limit, permission and row-scope enforcement,
record CRUD/lifecycle, Flow start/status, import/export, dashboard/report and
operational call-log access.

The final application-owned callback gap is proved by
`.cursor/session/evidence/release-114/openapi-callback.md` and the integrated
Cycle114 acceptance:

- immutable application-owned subscriptions and SecretRef signing rotation;
- real record/Flow event publication, deterministic dedupe and durable work;
- HMAC-SHA256 delivery, bounded retry/backoff and sanitized attempt audit;
- HTTPS public-target/SSRF enforcement, connected-address recheck, TLS hostname
  pinning, no redirects and bounded responses;
- real MySQL uniqueness, isolation, version replacement and attempt history.

## Integration gates

The current package passed the complete `1,786`-test backend reactor, `687`-test
frontend suite/build, all `112` migrations, packaged cold start and npm audit
with zero vulnerabilities. The P2→P9 predecessor chain is now explicit and
green.

This phase acceptance promotes the project to the release-gate lane. It does
not itself assert clean-upgrade/security release acceptance, engineering-final
PASS or user sign-off. CP1/CP2/CP3 attempts remain `0/3`.

