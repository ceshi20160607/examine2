# Security regression evidence

## Backend full reactor

- Existing run reused: `mvn -f backend/pom.xml test` under Java 21.
- Fresh reports written 2026-08-06 21:20:00 through 21:42:54 +08:00.
- Historical aggregate before correction: 496 Surefire suites, 1,684 tests, 2 failures, 0 errors, 0 skipped.
- Root cause: two migration-count assertions were stale; they expected fixed historical counts instead of deriving the count from current migrations.
- Correction verification: the two affected Java 21/Testcontainers tests were rerun after changing the assertions to dynamic counts; result **2/2 PASS**, with **104 migrations** observed.
- These two assertions are therefore **fixed and are not current product blockers**. A final full-reactor run is still required to attest the exact release worktree/package.

## Representative backend authorization/security journeys

| Journey | Tests | Failures/errors | Coverage evidence |
|---|---:|---:|---|
| `Vs1JourneyIntegrationTest` | 2 | 0/0 | cross-system membership denied (`:105`); missing CSRF denied (`:121,198`); session/password lifecycle |
| `Vs2JourneyIntegrationTest` | 1 | 0/0 | ordinary member denied platform operations (`:94`); member denied system settings (`:513`); cross-system context denied (`:518`) |
| `FeatureApiJourneyIntegrationTest` | 1 | 0/0 | anonymous denied (`:139`); platform context rejected (`:140`); second-tenant isolation and switch-back (`:467-510`) |
| `OpenApiFoundationJourneyIntegrationTest` | 2 | 0/0 | anonymous/signature/replay/scope/IP/rate-limit/rotation/permission and cross-system/cross-tenant controls; representative denial refs `:224,353,426,556-661,1027` |
| `EventDeliveryChannelsJourneyIntegrationTest` | 1 | 0/0 | local SMTP plus signed webhook, SecretRef resolution and redacted privileged-view evidence |
| `AccountRecoveryJourneyIntegrationTest` | 1 | 0/0 | recovery token/session behavior and SMTP integration |
| `FileStorageJourneyIntegrationTest` | 1 focused rerun | 0/0 | S3 preview/multipart/reference/tenant-isolation path passes after dynamic migration-count correction |

This set exercises anonymous, platform-root, system-owner, ordinary-member, no-permission, cross-system and cross-tenant behavior. The focused correction is green; final release acceptance still requires a green reactor against the exact package source.

## Frontend focused rerun after dependency overrides

Command selected eight security/configuration suites:

`npm test -- tests/unit/access-policy.spec.ts tests/unit/session.spec.ts tests/unit/account-security-dialog.spec.ts tests/unit/password-recovery-flow.spec.ts tests/unit/data-source-editor-model.spec.ts tests/unit/openapi-applications-view.spec.ts tests/unit/event-channel-manager.spec.ts tests/unit/file-assets-view.spec.ts`

Result: 8 files / 39 tests / all passed, duration 5.82 seconds, start 2026-08-06 21:40:56 +08:00.

## Evidence limitations

- No production environment or TLS edge was available, so production transport/header findings are static/configuration conclusions.
- No new backend tests were launched by this audit; it reused the already-running Java 21 full reactor to avoid process/container conflicts.
- The current fat JAR predates the fresh tests and therefore cannot be attested as the exact tested artifact.
- This audit created no process/container and has no process/container cleanup debt.
