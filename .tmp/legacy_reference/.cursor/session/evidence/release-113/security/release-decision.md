# REL113-D/T security release decision

- Decision: **REPLAN_REQUIRED / BLOCK / do not sign the release**.
- Audit time: 2026-08-06 (Asia/Shanghai).
- Scope: Java 21/Maven backend, npm frontend, current backend fat JAR, production-required configuration and representative authorization regressions.
- Mutation boundary: this audit changed only `.cursor/session/evidence/release-113/security/**`; it did not change application code, dependency manifests/locks, shared state, credentials, containers, or formal checkpoint state.

## Release blockers

1. The current backend fat JAR has five OSV `HIGH` matches in Netty 4.1.135.Final. OSV reports 4.1.136.Final as the fixed 4.1 line. The artifact also has ten `MODERATE` matches. See `dependency-audit.md`.
2. The only available backend release JAR was last written at 2026-08-06 16:58:05 +08:00 and has SHA-256 `8ba532aded499283eb417962d972f97e6ba5b0fb8a6a6970ce4d211205749ccc`; it predates the fresh validation work and must not be treated as a verified package of the final worktree.
3. Git tracks `docs/user_setting.md`, whose current worktree contains plaintext local connection/account values. No values are reproduced here. Exact lines and safe replacement targets are in `production-config-audit.md`. If these values may ever have been live, they must also be rotated.
4. Production fail-closed enforcement is absent for core DB TLS, Redis TLS/authentication and S3 HTTPS. Loopback-only Docker/MinIO/Redis allowances are acceptable for isolated local tests, but there is no equivalent production profile/validator preventing insecure deployment. See `production-config-audit.md`.
5. No deployable reverse-proxy/TLS evidence establishes global HSTS/CSP/frame-ancestors/nosniff/referrer-policy behavior, and the package runbook does not document the sensitive key-ring prerequisite. These are production evidence gaps that require closure or an explicit risk acceptance before release.
6. Phase P2-P9 closure remains incomplete according to the release phase-gap evidence. Release scope must be replanned and closed rather than treating the current package as final.

## Non-blockers / accepted risk candidates

- Frontend dependency audit is green after the dependency owner updated the npm overrides: production and full-tree audits both report zero vulnerabilities; selected security-related UI tests pass.
- The project correctly fails Maven Enforcer on an unsupported Java runtime; Java 21.0.10 with Maven 3.8.5 passes `validate`. The default shell still resolving Java 8 is an operational setup risk, not a product defect.
- SecretRef resolution, session cookie flags, CSRF comparison, webhook target policy, local file containment and business SMTP secret handling have positive controls and passing focused tests.
- S3 and account-recovery SMTP obtain credentials from deployment environment configuration rather than tenant SecretRef storage. This can be accepted only if deployment injects them through an approved secret manager and logs/process inspection are controlled.
- `GlobalExceptionHandler` returns `BusinessException` message/data/errors to clients. No known secret-bearing regression failed, but the absence of a central public-error allowlist/redaction layer is a medium hardening gap.

## Required closeout

1. Upgrade/remediate backend dependency findings, rebuild with Java 21, rescan the resulting JAR and record complete inventory coverage.
2. Preserve the now-green focused regression fix, then rerun the full reactor and package the exact tested commit/worktree.
3. Replace tracked values in `docs/user_setting.md` with placeholders and rotate potentially live credentials.
4. Add a production profile/startup validator (or supply equivalent immutable deployment policy evidence) for TLS/auth/cookie/key-ring/header requirements.
5. Re-run this audit against the final package; do not carry this decision forward as approval.

## Post-audit integration update

After this audit snapshot, integration owner replaced both hard-coded Flyway-count assertions with migration-directory-derived expectations. A Java 21/Testcontainers rerun of `FileStorageJourneyIntegrationTest` and `P4A1SchemaIntegrationTest` passed `2/2` and validated all `104` migrations. Frontend full regression also passed `119` files / `656` tests, production build, and zero-vulnerability full/production npm audits. These close blocker 2 and the frontend audit portion, but do not change the overall `BLOCK` decision because backend dependency, production fail-closed, final-artifact and P2-P9 functional gates remain open.
