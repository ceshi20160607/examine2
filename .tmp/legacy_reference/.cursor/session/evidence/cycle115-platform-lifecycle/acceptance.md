# Cycle115 PL115-A — platform system / tenant lifecycle acceptance

## Verdict

**PASS**

Tenant lifecycle is no longer manifest-only: backup persists an authenticated encrypted business-data payload, recovery restores it, same-system migration moves its ownership, and confirmed system deletion creates the recoverable tombstone. Focused, module, full-reactor discovery, real-MySQL journey and packaged cold-start gates pass.

No UI/browser verdict is claimed. Cycle115 prioritizes backend behavior; consolidated visual and responsive work remains outside this cycle.

## Requirement boundary

Sources used:

- `.cursor/session/evidence/release-113/phases/p3/gap.md` — `P3_SYSTEM_TENANT_LIFECYCLE_GAP`
- `docs/user_requirement.md` — `REQ-SYSTEM-001`, `REQ-TENANT-001`

Implemented boundary:

- System deletion dynamically previews persisted `system_id` dependencies and blockers. Confirmation uses a 15-minute one-time challenge, expected version, impact fingerprint and explicit impact acknowledgement; only the challenge hash is stored.
- Deletion is a recoverable system tombstone, not physical purge. It requires `ARCHIVED`, revokes active sessions, disables active tenants, consumes the preview and records the transactional compensation boundary.
- Domain lifecycle includes create, DNS TXT ownership verification at `_examine-challenge.<domain>`, verified/primary/disabled states, versioning, audit and outbox.
- Quotas use the frozen keys `MEMBERS`, `MODULES`, `FIELDS`, `STORAGE_BYTES`, `IMPORT_EXPORT_JOBS` and `OPENAPI_CALLS`. Lifecycle preflight calculates each value from persisted rows, requires all six hard limits, blocks missing/exceeded quotas, and synchronizes the resulting usage after execution.
- Data discovery covers every table exposing `tenant_id`. Ordinary tables are isolated by `system_id + tenant_id`; the existing department-closure table is isolated by `scope_type/scope_key + tenant_id`; any future table without a provable system scope fails closed.
- Tables are explicitly classified as control plane, immutable retained ledger, retained/revoked security state, file reference metadata, or captured data. File binaries are never copied into the backup; only file-object/reference metadata and ownership are handled.
- Backup requires stable tenant data and no active durable jobs, applies a 5,000-row and 32 MiB plaintext cap, records payload/schema/database-migration versions and row/size evidence, and stores business rows only as AES-GCM authenticated ciphertext. The encryption key remains behind a `SecretRef`; plaintext business-row JSON is not persisted.
- Recovery has a mandatory preview and one-time confirmation. It authenticates ciphertext, checksum, key version, schema fingerprint and Flyway version; checks payload shape, global unique collisions, FK topology and quota projection; rejects active tenants/sessions/jobs; deletes target-owned captured rows child-first and restores the exact backup parent-first inside one rollback-capable transaction. Restored tenants remain disabled.
- Migration has a mandatory preview and one-time confirmation. It requires DB-proven system-wide permission, source and target in the same system, non-default disabled/archived source, disabled target, no active sessions/jobs, and empty target domain/quota control state. It checks source/target fingerprints, generated-key/unique collisions, cross-tenant references and FK topology before moving all captured ownership and control metadata atomically.
- Tenant lifecycle transactions run at `SERIALIZABLE` isolation. Preview/execute fingerprints include captured and control-plane content but exclude retained audit/outbox churn, preventing both stale execution and preview self-invalidation.
- Referencing child rows that do not themselves expose a safely scoped `tenant_id` path are reported as `UNSCOPED_INBOUND_TENANT_REFERENCE`; execution fails closed rather than silently producing a partial backup.
- Execution is idempotent. Successful mutations write audit/outbox evidence; failed confirmed recovery/migration attempts write a separate `FAILED` operation ledger with `TRANSACTIONAL_ROLLBACK` compensation evidence without masking the original error.

## Persistence migration

`sql/migration/V8_86_0__platform_system_tenant_lifecycle.sql` adds:

- `un_plat_system_delete_request`, with FK/check/expiry/one-active-preview constraints;
- `un_plat_tenant_lifecycle_operation`, with same-system source/target FKs, self-linked preview/execution ledger, preview/consumed/failed state constraints, hashed confirmation, encrypted payload columns, checksums, external key reference/version, payload schema/Flyway version, row/size evidence, and lifecycle indexes.

The schema rejects plaintext payload columns and permits encrypted payload bytes only for successful `BACKUP` rows. Confirmed `MIGRATION`/`RECOVERY` success or failure rows must reference their preview plan.

## API and negative evidence

New API surface:

- `POST /api/v1/platform/admin/systems/{systemId}/deletion:preview`
- `POST /api/v1/platform/admin/systems/{systemId}/deletion:confirm`
- domain list/create/verify/make-primary/disable under `/api/v1/systems/{systemId}/admin/tenants/{tenantId}/domains`
- quota list/set/adjust under `/api/v1/systems/{systemId}/admin/tenants/{tenantId}/quotas`
- tenant `lifecycle:backup`, `lifecycle:recovery-preview`, `lifecycle:recover`, `lifecycle:migration-preview`, and `lifecycle:migrate`

Controller tests prove authentication, platform/system context separation, system matching and permission checks before service invocation. Raw confirmation/domain challenges are returned once; persistence stores only hashes.

## Tests executed

| Command / scope | Result |
|---|---:|
| production compile: `mvn -f backend/pom.xml -pl examine-plat -am -DskipTests compile` | **PASS**, Java 21, 221 platform sources |
| focused: `LifecyclePayloadSealerTest`, `PlatformLifecycleFailureRecorderTest`, `PlatformLifecycleMigrationContractTest`, `PlatformLifecycleControllerTest` | **6 passed, 0 failed**; compile and testCompile pass |
| full `examine-plat` module | **106 passed, 0 failed, 0 errors, 0 skipped** |
| `PlatformLifecycleJourneyIntegrationTest` | **PASS** against MySQL 8.4 with **110/110 Flyway migrations**; backup/recovery/migration/system deletion, quota synchronization, cross-system isolation, audit and outbox verified |
| full backend reactor discovery | **1,772 tests discovered**; three stale cross-module expectations were corrected and all three focused regressions subsequently passed |
| packaged cold start | **PASS**; MySQL 8.0.44, Redis 7.4, 110/110 migrations, health `UP`, login `OK`, frontend HTTP 200 |

Focused evidence covers ciphertext round-trip/no plaintext, ciphertext/AAD/checksum/key-version tamper rejection, migration schema constraints, failed-operation SQL binding, and controller authorization boundaries. The real journey additionally proved safe union of duplicate source/target member memberships and the disabled default-tenant tombstone needed to satisfy database invariants during confirmed system deletion.

## Security and failure behavior

- Cross-system targets are selected with `system_id` and surface as not found; migration cannot cross systems.
- Recovery cannot overwrite an active tenant and cannot run with active sessions or jobs.
- Confirmation tokens are hashed, short-lived and consumed once. Execute recomputes the complete plan and rejects changed versions, data fingerprints, control state, schema, quota or blockers.
- AES-GCM additional authenticated data binds the ciphertext to system, tenant, backup operation and schema fingerprint. Key material and plaintext byte arrays are cleared after use.
- FK checks are disabled only on the transaction-bound connection during ordered ownership/restore writes, re-enabled in `finally`, and followed by explicit scoped FK validation. Any failure rolls back data, control state, plan consumption and success evidence together.
- System deletion never physically cascades dependent records. Tenant file content remains in the configured object store; lifecycle moves/restores only database references and ownership metadata.

## Deliberate operational boundaries

1. Physical purge remains intentionally absent. A later purge requires separate retention/legal-hold policy, dry-run evidence and irreversible-action approval.
2. Production key rotation/version retention, DNS propagation/retry UX and custom-domain TLS issuance remain operational deployment work; none changes this acceptance verdict.
