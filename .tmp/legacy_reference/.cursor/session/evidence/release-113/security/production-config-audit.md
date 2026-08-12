# Production configuration and secret-handling audit

No secret value is present in this evidence.

## `docs/user_setting.md` exact remediation map

The file is Git-tracked and currently modified. It is classified as task-only local configuration by `.cursor/INSTANCE.md:33` and `.cursor/README.md:47`. Spring does **not** read this Markdown file. The last packaged Cycle 112 directory contains only `backend`, `data`, `frontend`, `sql`, `manifest.json` and `RUN.md`; the document is not packaged. Runtime reads environment variables in the web module's `application.yml:6-8,15-18,127-128`.

Even though the values are documentation/local-environment examples rather than runtime-linked configuration, plaintext tracked credentials are a repository exposure. Replace them and rotate any value that may have been live.

| Line | Section-qualified key | Safe target text |
|---:|---|---|
| 13 | `database.url` | `${EXAMINE_DB_URL}` |
| 14 | `database.username` | `${EXAMINE_DB_USERNAME}` |
| 15 | `database.password` | `${EXAMINE_DB_PASSWORD}` |
| 17 | `redis.host` | `${EXAMINE_REDIS_HOST}` |
| 18 | `redis.port` | `${EXAMINE_REDIS_PORT}` |
| 19 | `redis.password` | `${EXAMINE_REDIS_PASSWORD}` |
| 20 | `redis.database` | `${EXAMINE_REDIS_DATABASE}` |
| 23 | `bootstrap.root.username` | `${EXAMINE_BOOTSTRAP_ROOT_USERNAME}` |
| 24 | `bootstrap.root.password` | `${EXAMINE_BOOTSTRAP_ROOT_PASSWORD}` |
| 25 | `bootstrap.root.role` | `<BOOTSTRAP_ROLE_DESCRIPTION>` |

Line 13 also demonstrates insecure database transport parameters. Do not retain those parameters in the placeholder/example; production should use authenticated TLS with hostname verification.

## Positive controls observed

- DB username/password have no default in `application.yml:7-8`; Redis host is required at line 15.
- Session cookies use HttpOnly as appropriate, the configured secure flag and SameSite=Lax (`SessionCookieSupport.java:51-55`); `application.yml:133` defaults secure cookies on. The CSRF cookie is intentionally script-readable for double-submit handling.
- CSRF enforcement uses constant-time comparison (`CsrfFilter.java:85`) and exempts only OpenAPI authentication and explicit read-only query POST routes.
- SecretRef environment/file readers restrict schemes, environment names, rooted file paths, symbolic links and size (64 KiB), and wipe resolved bytes on close (`DefaultSecretResolverFacade.java:20,52-55,71,87-110`; OpenAPI equivalent at `DefaultSecretRefResolver.java:14,30-37,51,59-75`).
- Webhook delivery requires HTTPS and public targets in normal operation, rejects credentials/query/fragment and redirects, signs bodies, discards response bodies and allows HTTP only for explicit loopback testing (`SignedWebhookTransport.java:69-71,322-345`).
- Local file storage normalizes under a real root and rejects traversal/symbolic links (`LocalFileContentStore.java:21-27,86-120`).
- Business SMTP resolves tenant SecretRefs, enables/optionally requires STARTTLS, disables mail debug and wipes credential bytes (`SmtpBusinessMessageTransport.java:92-125,154-161,284`).
- Unexpected exceptions return a generic internal error while server-side logging retains the stack; audit facts are method/path/failure code rather than request bodies (`GlobalExceptionHandler.java:110-145,171`).

## Production enforcement gaps and minimal fixes

| Area | Local-development allowance | Missing production enforcement | Minimal fix |
|---|---|---|---|
| Core DB | Loopback Docker gate uses non-TLS for an isolated ephemeral database; acceptable only locally. | DB URL is opaque and startup does not reject non-TLS, `allowPublicKeyRetrieval=true`, or missing hostname verification. | Add a production-profile startup validator requiring authenticated TLS/hostname verification (for MySQL, `sslMode=VERIFY_IDENTITY`) and rejecting `allowPublicKeyRetrieval=true`; keep local override profile separate. |
| Redis | Isolated loopback Redis with empty password is acceptable for the release test harness. | YAML has no TLS binding/gate and password defaults empty. | Bind a TLS flag/URI and fail production startup unless TLS and nonblank authentication are configured; retain an explicit local-test profile only. |
| S3 | HTTP loopback MinIO is valid for deterministic local integration tests. | `FileStorageConfiguration.java:149` accepts both HTTP and HTTPS without a loopback/test-only production gate. | Require HTTPS by default; permit HTTP only when an explicit default-false test flag is set **and** every resolved address is loopback. |
| SMTP | Disabled/local capture SMTP may omit TLS in tests. | Account-recovery SMTP and business SMTP can be configured without production TLS enforcement. | If SMTP is enabled in production, require STARTTLS and `starttls.required`, hostname verification and an approved credential source. |
| Browser edge | Local package runbook serves HTTP for local verification. | No checked production proxy manifest/runbook proves TLS termination or HSTS/CSP/frame-ancestors/nosniff/referrer-policy headers. | Add immutable reverse-proxy/platform policy and a black-box production-header test. Keep application cookies secure. |
| Sensitive key ring | Empty key-ring config causes sensitive operations to fail closed. | Package runbook does not list the key-ring prerequisite; `FileSensitiveKeyProvider.java:36-41` follows a regular file without a no-symlink/root/size policy. | Document required key-ring creation/ACL/backup/rotation; require an absolute approved-root, no-follow file and a size cap at production startup. |

## Leakage and accepted-risk review

- OpenAPI and event-channel persistence stores SecretRefs, not resolved values; privileged projections mask references/targets and focused tests pass.
- `GlobalExceptionHandler.business` returns `BusinessException.getMessage()`, `.data()` and `.errors()` (`GlobalExceptionHandler.java:38-48`). This is a medium evidence gap: replace it with an allowlisted public-error DTO/redactor and add tests that inject secret-shaped values through every exception path.
- Direct deployment environment credentials for S3 and account-recovery SMTP are not tenant SecretRefs. Accept only with secret-manager injection, least-privilege process access, disabled debug logging and rotation procedures.
