# P9 OpenAPI and integration phase gap report

- outcomeId: `P9_PHASE_ACCEPTANCE`
- proposed verdict: `NOT_PROVEN`
- promotion safety: `DO_NOT_PROMOTE`

## Frozen requirements

P9 owns applications, credentials, scopes, callbacks, rate limiting and call logs. `REQ-OPENAPI-001` requires application-scoped record/file/Flow access with SecretRef/version/rotation, signatures, replay defense, idempotency, allowlists, rate limits and audit.

## Evidence audit

| requirement | evidence | assessment |
|---|---|---|
| Application lifecycle, SecretRef credential versions, HMAC, nonce, timestamp, IP allowlist, rate bucket and sanitized logs | fast acceptance 25 | `PROVEN` |
| Signed Flow start/status | fast acceptances 26 and 73 | `PROVEN` |
| Signed record create/read/update/lifecycle and relation/subtable composition | fast acceptances 70, 71 and 74 | `PROVEN` |
| Signed record file upload/list/download | fast acceptance 72 | `PROVEN` |
| Native application-scoped call-log administration | fast acceptance 92 | `PROVEN` |
| Callback subscription, signed delivery, retry/dedupe and callback audit | roadmap P9; integration requirements; Batch74 Deferred | `CONTRADICTED`: callback subscriptions are explicitly deferred and no OpenAPI callback owner/evidence exists. |
| OAuth/OIDC client credentials and generated SDK/docs | multiple OpenAPI acceptance Deferred sections | `MISSING/DEFERRED`; these are secondary to the callback blocker but remain unresolved where required by delivery docs. |

## Blocking gap

External callers can securely invoke the implemented API, but the formal P9 phase explicitly includes callbacks. Event Webhook delivery is a message-channel transport and is not an OpenAPI application callback subscription contract.

## Required closure evidence

Implement application-owned callback subscriptions, SecretRef signing/rotation, event selection, bounded retry/dedupe, SSRF/redaction controls and call/delivery audit; verify real signed callback delivery, replay, failure and tenant/application isolation.

