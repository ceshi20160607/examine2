package com.unique.examine.plat.lifecycle;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.service.AuthzEpochService;
import com.unique.examine.plat.manage.service.ClientRequest;
import com.unique.examine.plat.manage.service.PlatformMutationSupport;
import com.unique.examine.plat.manage.service.SystemAdminMutationSupport;
import com.unique.examine.plat.manage.service.SystemAdminScopeSupport;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.net.IDN;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class JdbcPlatformLifecycleService implements PlatformLifecycleOperations {
    private static final Set<String> QUOTA_KEYS = Set.of(
            "MEMBERS", "MODULES", "FIELDS", "STORAGE_BYTES", "IMPORT_EXPORT_JOBS", "OPENAPI_CALLS");
    private static final Set<String> DEPENDENCY_EXCLUSIONS = Set.of(
            "un_audit_operation", "un_audit_security", "un_sys_outbox_event",
            "un_plat_system_delete_request", "un_plat_tenant_lifecycle_operation");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final IdService ids;
    private final PlatformMutationSupport platformMutations;
    private final SystemAdminMutationSupport systemMutations;
    private final SystemAdminScopeSupport scopes;
    private final AuthzEpochService epochs;
    private final DomainOwnershipVerifier domainOwnership;
    private final TenantDataLifecycleEngine dataLifecycle;

    public JdbcPlatformLifecycleService(
            JdbcTemplate jdbc,
            ObjectMapper json,
            IdService ids,
            PlatformMutationSupport platformMutations,
            SystemAdminMutationSupport systemMutations,
            SystemAdminScopeSupport scopes,
            AuthzEpochService epochs,
            DomainOwnershipVerifier domainOwnership,
            TenantDataLifecycleEngine dataLifecycle
    ) {
        this.jdbc = jdbc;
        this.json = json;
        this.ids = ids;
        this.platformMutations = platformMutations;
        this.systemMutations = systemMutations;
        this.scopes = scopes;
        this.epochs = epochs;
        this.domainOwnership = domainOwnership;
        this.dataLifecycle = dataLifecycle;
    }

    @Override
    @Transactional
    public PlatformLifecycleApi.DeletePreview previewDeletion(
            AuthenticatedSession session, long systemId, ClientRequest request) {
        var system = system(systemId, false);
        var now = LocalDateTime.now();
        jdbc.update("UPDATE un_plat_system_delete_request SET status='EXPIRED', updated_at=?, updated_by=?, version=version+1 "
                        + "WHERE system_id=? AND status='PREVIEWED'",
                now, session.accountId(), systemId);
        var dependencies = systemDependencies(systemId);
        var blockers = deleteBlockers(systemId, text(system, "status"));
        var fingerprint = impactFingerprint(systemId, number(system, "version"), dependencies, blockers);
        var rawToken = token();
        var previewId = ids.nextId();
        var expiresAt = now.plusMinutes(15);
        jdbc.update("""
                INSERT INTO un_plat_system_delete_request(
                  id,system_id,system_version,dependency_json,blocker_json,impact_fingerprint,
                  confirmation_token_hash,status,expires_at,consumed_at,created_at,created_by,
                  updated_at,updated_by,version)
                VALUES(?,?,?,?,?,?,?,'PREVIEWED',?,NULL,?,?,?,?,0)
                """, previewId, systemId, number(system, "version"), write(dependencies), write(blockers),
                fingerprint, hash(rawToken), expiresAt, now, session.accountId(), now, session.accountId());
        var result = new PlatformLifecycleApi.DeletePreview(
                Long.toString(previewId), Long.toString(systemId), Long.toString(number(system, "version")),
                blockers.isEmpty(), dependencies, blockers, fingerprint, rawToken, expiresAt);
        platformMutations.success(session, request, "PLATFORM_SYSTEM", Long.toString(systemId),
                "PLATFORM_SYSTEM_DELETE_PREVIEWED", null,
                Map.of("previewId", result.previewId(), "eligible", result.eligible(),
                        "dependencies", result.dependencies(), "blockers", result.blockers(),
                        "impactFingerprint", result.impactFingerprint()), null);
        return result;
    }

    @Override
    @Transactional
    public PlatformLifecycleApi.DeleteResult deleteSystem(
            AuthenticatedSession session, long systemId, PlatformLifecycleApi.DeleteCommand command,
            String idempotencyKey, ClientRequest request) {
        require(command != null, "delete command is required");
        return platformMutations.idempotent(session.accountId(), "SYSTEM_DELETE", Long.toString(systemId),
                idempotencyKey, command, PlatformLifecycleApi.DeleteResult.class,
                () -> deleteSystemOnce(session, systemId, command, idempotencyKey, request));
    }

    private PlatformLifecycleApi.DeleteResult deleteSystemOnce(
            AuthenticatedSession session, long systemId, PlatformLifecycleApi.DeleteCommand command,
            String idempotencyKey, ClientRequest request) {
        require(command.impactConfirmed(), "impactConfirmed must be true");
        required(command.reason(), "reason", 1000);
        var previewId = positive(command.previewId(), "previewId");
        var expectedVersion = version(command.expectedVersion());
        var system = systemForUpdate(systemId);
        if (number(system, "version") != expectedVersion) throw PlatformMutationSupport.versionConflict();
        var preview = rowForUpdate("SELECT * FROM un_plat_system_delete_request WHERE id=? AND system_id=?",
                previewId, systemId);
        if (!"PREVIEWED".equals(text(preview, "status"))
                || LocalDateTime.now().isAfter(time(preview, "expires_at"))
                || number(preview, "system_version") != expectedVersion
                || !hash(required(command.confirmationToken(), "confirmationToken", 512))
                .equals(text(preview, "confirmation_token_hash"))) {
            throw conflict("SYSTEM_DELETE_CONFIRMATION_INVALID", "delete confirmation is invalid or expired");
        }
        var dependencies = systemDependencies(systemId);
        var blockers = deleteBlockers(systemId, text(system, "status"));
        var fingerprint = impactFingerprint(systemId, expectedVersion, dependencies, blockers);
        if (!blockers.isEmpty() || !fingerprint.equals(text(preview, "impact_fingerprint"))) {
            jdbc.update("UPDATE un_plat_system_delete_request SET status='BLOCKED',blocker_json=?,"
                            + "updated_at=?,updated_by=?,version=version+1 WHERE id=?",
                    write(blockers), LocalDateTime.now(), session.accountId(), previewId);
            throw conflict("SYSTEM_DELETE_IMPACT_CHANGED", "dependencies changed; request a new deletion preview");
        }
        var now = LocalDateTime.now();
        var revoked = jdbc.update("UPDATE un_plat_context_session SET status='REVOKED',revoked_at=?,updated_at=?,"
                        + "version=version+1 WHERE system_id=? AND status='ACTIVE'",
                now, now, systemId);
        var disabled = jdbc.update("UPDATE un_plat_tenant SET is_default=FALSE,status='DISABLED',updated_at=?,updated_by=?,"
                         + "version=version+1 WHERE system_id=? AND deleted_at IS NULL AND status='ACTIVE'",
                 now, session.accountId(), systemId);
        var updated = jdbc.update("UPDATE un_plat_system SET deleted_at=?,deleted_by=?,tombstone_reason=?,"
                        + "updated_at=?,updated_by=?,version=version+1 "
                        + "WHERE id=? AND deleted_at IS NULL AND status='ARCHIVED' AND version=?",
                now, session.accountId(), command.reason().trim(), now, session.accountId(), systemId, expectedVersion);
        if (updated != 1) throw PlatformMutationSupport.versionConflict();
        jdbc.update("UPDATE un_plat_system_delete_request SET status='CONSUMED',consumed_at=?,updated_at=?,"
                        + "updated_by=?,version=version+1 WHERE id=? AND status='PREVIEWED'",
                now, now, session.accountId(), previewId);
        var result = new PlatformLifecycleApi.DeleteResult(
                Long.toString(systemId), "TOMBSTONED", revoked, disabled, Long.toString(previewId), now);
        platformMutations.success(session, request, "PLATFORM_SYSTEM", Long.toString(systemId),
                "PLATFORM_SYSTEM_TOMBSTONED", Map.of("status", "ARCHIVED", "version", expectedVersion),
                Map.of("result", result, "reason", command.reason().trim(),
                        "impactFingerprint", fingerprint, "compensation", "TRANSACTIONAL_ROLLBACK"),
                idempotencyKey);
        return result;
    }

    @Override
    public List<PlatformLifecycleApi.DomainView> domains(
            AuthenticatedSession session, long systemId, long tenantId) {
        tenantScope(session, systemId, tenantId);
        requireTenant(systemId, tenantId, false);
        return jdbc.query("SELECT * FROM un_plat_tenant_domain WHERE system_id=? AND tenant_id=? "
                        + "AND deleted_at IS NULL ORDER BY is_primary DESC,domain_name,id",
                (rs, row) -> domain(rs, null), systemId, tenantId);
    }

    @Override
    @Transactional
    public PlatformLifecycleApi.DomainView addDomain(
            AuthenticatedSession session, long systemId, long tenantId,
            PlatformLifecycleApi.DomainCreate command, String idempotencyKey, ClientRequest request) {
        tenantScope(session, systemId, tenantId);
        require(command != null, "domain command is required");
        return systemMutations.idempotent(systemId + ":tenant:" + tenantId + ":domain:create",
                idempotencyKey, command, PlatformLifecycleApi.DomainView.class,
                () -> addDomainOnce(session, systemId, tenantId, command, idempotencyKey, request));
    }

    private PlatformLifecycleApi.DomainView addDomainOnce(
            AuthenticatedSession session, long systemId, long tenantId,
            PlatformLifecycleApi.DomainCreate command, String idempotencyKey, ClientRequest request) {
        requireTenant(systemId, tenantId, false);
        var domainName = domainName(command.domainName());
        var rawToken = token();
        var now = LocalDateTime.now();
        var id = ids.nextId();
        try {
            jdbc.update("""
                    INSERT INTO un_plat_tenant_domain(
                      id,system_id,tenant_id,domain_name,status,is_primary,verification_token_hash,
                      verified_at,created_at,created_by,updated_at,updated_by,deleted_at,deleted_by,version)
                    VALUES(?,?,?,?,'PENDING',FALSE,?,NULL,?,?,?, ?,NULL,NULL,0)
                    """, id, systemId, tenantId, domainName, hash(rawToken), now, session.accountId(),
                    now, session.accountId());
        } catch (DataIntegrityViolationException duplicate) {
            throw conflict("TENANT_DOMAIN_CONFLICT", "domain is already registered");
        }
        var after = domain(row("SELECT * FROM un_plat_tenant_domain WHERE id=?", id), rawToken);
        tenantSuccess(session, systemId, "TENANT_DOMAIN", after.id(), "TENANT_DOMAIN_CREATED",
                null, publicDomain(after), idempotencyKey, request);
        return after;
    }

    @Override
    @Transactional
    public PlatformLifecycleApi.DomainView verifyDomain(
            AuthenticatedSession session, long systemId, long tenantId, long domainId,
            PlatformLifecycleApi.DomainVerify command, ClientRequest request) {
        tenantScope(session, systemId, tenantId);
        require(command != null, "domain verification is required");
        var domain = domainForUpdate(systemId, tenantId, domainId);
        requireVersion(domain, command.expectedVersion());
        var verificationToken = required(command.verificationToken(), "verificationToken", 512);
        if (!"PENDING".equals(text(domain, "status"))
                || !hash(verificationToken).equals(text(domain, "verification_token_hash"))) {
            throw conflict("TENANT_DOMAIN_VERIFICATION_INVALID", "domain verification token is invalid");
        }
        if (!domainOwnership.hasTxtProof(text(domain, "domain_name"), verificationToken)) {
            throw conflict("TENANT_DOMAIN_DNS_PROOF_MISSING", "required DNS TXT ownership proof is missing");
        }
        var now = LocalDateTime.now();
        updateVersioned("UPDATE un_plat_tenant_domain SET status='VERIFIED',verified_at=?,"
                        + "verification_token_hash=NULL,updated_at=?,updated_by=?,version=version+1 "
                        + "WHERE id=? AND system_id=? AND tenant_id=? AND version=?",
                now, now, session.accountId(), domainId, systemId, tenantId, number(domain, "version"));
        var after = domain(row("SELECT * FROM un_plat_tenant_domain WHERE id=?", domainId), null);
        tenantSuccess(session, systemId, "TENANT_DOMAIN", after.id(), "TENANT_DOMAIN_VERIFIED",
                publicDomain(domain(domain, null)), publicDomain(after), request.requestId(), request);
        return after;
    }

    @Override
    @Transactional
    public PlatformLifecycleApi.DomainView makePrimaryDomain(
            AuthenticatedSession session, long systemId, long tenantId, long domainId,
            PlatformLifecycleApi.VersionCommand command, ClientRequest request) {
        tenantScope(session, systemId, tenantId);
        require(command != null, "domain command is required");
        var domain = domainForUpdate(systemId, tenantId, domainId);
        requireVersion(domain, command.expectedVersion());
        if (!"VERIFIED".equals(text(domain, "status"))) {
            throw state("only a verified domain can be primary");
        }
        var now = LocalDateTime.now();
        jdbc.update("UPDATE un_plat_tenant_domain SET is_primary=FALSE,updated_at=?,updated_by=?,"
                        + "version=version+1 WHERE system_id=? AND tenant_id=? AND is_primary=TRUE AND id<>?",
                now, session.accountId(), systemId, tenantId, domainId);
        updateVersioned("UPDATE un_plat_tenant_domain SET is_primary=TRUE,updated_at=?,updated_by=?,"
                        + "version=version+1 WHERE id=? AND system_id=? AND tenant_id=? AND version=?",
                now, session.accountId(), domainId, systemId, tenantId, number(domain, "version"));
        var after = domain(row("SELECT * FROM un_plat_tenant_domain WHERE id=?", domainId), null);
        tenantSuccess(session, systemId, "TENANT_DOMAIN", after.id(), "TENANT_DOMAIN_PRIMARY_CHANGED",
                publicDomain(domain(domain, null)), publicDomain(after), request.requestId(), request);
        return after;
    }

    @Override
    @Transactional
    public PlatformLifecycleApi.DomainView disableDomain(
            AuthenticatedSession session, long systemId, long tenantId, long domainId,
            PlatformLifecycleApi.VersionCommand command, String idempotencyKey, ClientRequest request) {
        tenantScope(session, systemId, tenantId);
        require(command != null && command.impactConfirmed(), "impactConfirmed must be true");
        required(command.reason(), "reason", 1000);
        return systemMutations.idempotent(systemId + ":tenant:" + tenantId + ":domain:" + domainId + ":disable",
                idempotencyKey, command, PlatformLifecycleApi.DomainView.class, () -> {
                    var domain = domainForUpdate(systemId, tenantId, domainId);
                    requireVersion(domain, command.expectedVersion());
                    if ("DISABLED".equals(text(domain, "status"))) return domain(domain, null);
                    var now = LocalDateTime.now();
                    updateVersioned("UPDATE un_plat_tenant_domain SET status='DISABLED',is_primary=FALSE,"
                                    + "verified_at=NULL,verification_token_hash=NULL,updated_at=?,updated_by=?,"
                                    + "version=version+1 WHERE id=? AND system_id=? AND tenant_id=? AND version=?",
                            now, session.accountId(), domainId, systemId, tenantId, number(domain, "version"));
                    var after = domain(row("SELECT * FROM un_plat_tenant_domain WHERE id=?", domainId), null);
                    tenantSuccess(session, systemId, "TENANT_DOMAIN", after.id(), "TENANT_DOMAIN_DISABLED",
                            publicDomain(domain(domain, null)), Map.of("domain", publicDomain(after),
                                    "reason", command.reason().trim()), idempotencyKey, request);
                    return after;
                });
    }

    @Override
    public List<PlatformLifecycleApi.QuotaView> quotas(
            AuthenticatedSession session, long systemId, long tenantId) {
        tenantScope(session, systemId, tenantId);
        requireTenant(systemId, tenantId, false);
        return jdbc.query("SELECT * FROM un_plat_quota WHERE system_id=? AND tenant_id=? ORDER BY quota_key",
                (rs, row) -> quota(rs), systemId, tenantId);
    }

    @Override
    @Transactional
    public PlatformLifecycleApi.QuotaView setQuota(
            AuthenticatedSession session, long systemId, long tenantId,
            PlatformLifecycleApi.QuotaSet command, String idempotencyKey, ClientRequest request) {
        tenantScope(session, systemId, tenantId);
        require(command != null, "quota command is required");
        return systemMutations.idempotent(systemId + ":tenant:" + tenantId + ":quota:" + command.quotaKey(),
                idempotencyKey, command, PlatformLifecycleApi.QuotaView.class,
                () -> setQuotaOnce(session, systemId, tenantId, command, idempotencyKey, request));
    }

    private PlatformLifecycleApi.QuotaView setQuotaOnce(
            AuthenticatedSession session, long systemId, long tenantId,
            PlatformLifecycleApi.QuotaSet command, String idempotencyKey, ClientRequest request) {
        requireTenant(systemId, tenantId, false);
        var key = quotaKey(command.quotaKey());
        var hard = nonnegative(command.hardLimit(), "hardLimit");
        var soft = command.softLimit() == null ? null : nonnegative(command.softLimit(), "softLimit");
        if (soft != null && soft > hard) require(false, "softLimit must not exceed hardLimit");
        var existing = optionalRow("SELECT * FROM un_plat_quota WHERE system_id=? AND tenant_id=? AND quota_key=? FOR UPDATE",
                systemId, tenantId, key);
        var now = LocalDateTime.now();
        if (existing == null) {
            require(command.expectedVersion() == null || command.expectedVersion().isBlank(),
                    "expectedVersion must be empty when creating quota");
            var id = ids.nextId();
            jdbc.update("""
                    INSERT INTO un_plat_quota(id,scope_type,system_id,tenant_id,quota_key,soft_limit,
                      hard_limit,used_value,status,created_at,created_by,updated_at,updated_by,version)
                    VALUES(?,'TENANT',?,?,?,?,?,0,'ACTIVE',?,?,?,?,0)
                    """, id, systemId, tenantId, key, soft, hard, now, session.accountId(), now, session.accountId());
            var after = quota(row("SELECT * FROM un_plat_quota WHERE id=?", id));
            tenantSuccess(session, systemId, "TENANT_QUOTA", after.id(), "TENANT_QUOTA_CREATED",
                    null, after, idempotencyKey, request);
            return after;
        }
        requireVersion(existing, command.expectedVersion());
        if (number(existing, "used_value") > hard) {
            throw conflict("TENANT_QUOTA_BELOW_USAGE", "hardLimit cannot be below current usage");
        }
        updateVersioned("UPDATE un_plat_quota SET soft_limit=?,hard_limit=?,status='ACTIVE',updated_at=?,"
                        + "updated_by=?,version=version+1 WHERE id=? AND version=?",
                soft, hard, now, session.accountId(), number(existing, "id"), number(existing, "version"));
        var after = quota(row("SELECT * FROM un_plat_quota WHERE id=?", number(existing, "id")));
        tenantSuccess(session, systemId, "TENANT_QUOTA", after.id(), "TENANT_QUOTA_UPDATED",
                quota(existing), after, idempotencyKey, request);
        return after;
    }

    @Override
    @Transactional
    public PlatformLifecycleApi.QuotaView adjustQuota(
            AuthenticatedSession session, long systemId, long tenantId, String quotaKey,
            PlatformLifecycleApi.QuotaAdjust command, String idempotencyKey, ClientRequest request) {
        tenantScope(session, systemId, tenantId);
        require(command != null && command.delta() != 0, "quota delta must not be zero");
        required(command.reason(), "reason", 1000);
        var normalized = quotaKey(quotaKey);
        return systemMutations.idempotent(systemId + ":tenant:" + tenantId + ":quota:" + normalized + ":adjust",
                idempotencyKey, command, PlatformLifecycleApi.QuotaView.class, () -> {
                    var quota = rowForUpdate("SELECT * FROM un_plat_quota WHERE system_id=? AND tenant_id=? "
                            + "AND quota_key=? AND status='ACTIVE'", systemId, tenantId, normalized);
                    requireVersion(quota, command.expectedVersion());
                    var next = number(quota, "used_value") + command.delta();
                    if (next < 0) throw conflict("TENANT_QUOTA_USAGE_INVALID", "quota usage cannot be negative");
                    if (next > number(quota, "hard_limit")) {
                        throw new BusinessException("TENANT_QUOTA_EXCEEDED", "tenant quota hard limit exceeded",
                                HttpStatus.TOO_MANY_REQUESTS);
                    }
                    updateVersioned("UPDATE un_plat_quota SET used_value=?,updated_at=?,updated_by=?,"
                                    + "version=version+1 WHERE id=? AND version=?",
                            next, LocalDateTime.now(), session.accountId(), number(quota, "id"), number(quota, "version"));
                    var after = quota(row("SELECT * FROM un_plat_quota WHERE id=?", number(quota, "id")));
                    tenantSuccess(session, systemId, "TENANT_QUOTA", after.id(), "TENANT_QUOTA_USAGE_ADJUSTED",
                            quota(quota), Map.of("quota", after, "delta", command.delta(),
                                    "reason", command.reason().trim()), idempotencyKey, request);
                    return after;
                });
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public PlatformLifecycleApi.OperationView backupTenant(
            AuthenticatedSession session, long systemId, long tenantId,
            PlatformLifecycleApi.BackupCommand command, String idempotencyKey, ClientRequest request) {
        tenantScope(session, systemId, tenantId);
        require(command != null, "backup command is required");
        required(command.reason(), "reason", 1000);
        return systemMutations.idempotent(systemId + ":tenant:" + tenantId + ":backup",
                idempotencyKey, command, PlatformLifecycleApi.OperationView.class, () -> {
                    var tenant = requireTenant(systemId, tenantId, true);
                    requireVersion(tenant, command.expectedTenantVersion());
                    var operationId = ids.nextId();
                    var artifact = dataLifecycle.backup(operationId, systemId, tenantId);
                    var snapshot = snapshot(systemId, tenantId);
                    snapshot.put("scope", "TENANT_FULL_DATA_PLANE_ENCRYPTED");
                    snapshot.put("dataPlan", planMap(artifact.plan()));
                    var result = new LinkedHashMap<String, Object>();
                    result.put("domainCount", list(snapshot, "domains").size());
                    result.put("quotaCount", list(snapshot, "quotas").size());
                    result.put("tableCount", artifact.plan().tableImpacts().size());
                    result.put("payloadRowCount", artifact.rowCount());
                    result.put("payloadSizeBytes", artifact.payloadSizeBytes());
                    result.put("scope", "TENANT_FULL_DATA_PLANE_ENCRYPTED");
                    result.put("fileHandling", "REFERENCE_AND_OBJECT_OWNERSHIP_ONLY");
                    var operation = insertBackupOperation(session, operationId, systemId, tenantId,
                            command.reason(), snapshot, result, artifact, request);
                    tenantSuccess(session, systemId, "TENANT_LIFECYCLE", operation.id(), "TENANT_BACKUP_CREATED",
                            null, operation, idempotencyKey, request);
                    return operation;
                });
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public PlatformLifecycleApi.PlanView previewTenantRecovery(
            AuthenticatedSession session, long systemId, long tenantId,
            PlatformLifecycleApi.RecoveryPreviewCommand command, ClientRequest request) {
        tenantScope(session, systemId, tenantId);
        require(command != null, "recovery preview command is required");
        var tenant = requireTenant(systemId, tenantId, true);
        requireVersion(tenant, command.expectedTenantVersion());
        var backup = backupRow(systemId, tenantId, positive(command.backupOperationId(), "backupOperationId"));
        var snapshot = readMap(text(backup, "snapshot_json"));
        if (!snapshotChecksum(snapshot).equals(text(backup, "snapshot_checksum"))) {
            throw conflict("TENANT_BACKUP_CHECKSUM_INVALID", "backup checksum validation failed");
        }
        var plan = dataLifecycle.recoveryPlan(systemId, tenantId, storedBackup(backup));
        var blockers = new ArrayList<String>();
        if (!Set.of("DISABLED", "ARCHIVED").contains(text(tenant, "status"))) {
            blockers.add("TARGET_TENANT_MUST_BE_DISABLED_OR_ARCHIVED");
        }
        if (count("SELECT COUNT(*) FROM un_plat_context_session WHERE system_id=? AND tenant_id=? "
                + "AND status='ACTIVE'", systemId, tenantId) > 0) blockers.add("TARGET_TENANT_HAS_ACTIVE_SESSIONS");
        if (count("SELECT COUNT(*) FROM un_sys_job WHERE system_id=? AND tenant_id=? "
                + "AND status IN ('QUEUED','RUNNING')", systemId, tenantId) > 0) {
            blockers.add("TARGET_TENANT_HAS_ACTIVE_JOBS");
        }
        blockers.addAll(controlRecoveryBlockers(systemId, tenantId, snapshot));
        plan = dataLifecycle.withAdditionalBlockers(plan, blockers);
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("backupOperationId", Long.toString(number(backup, "id")));
        metadata.put("expectedSourceVersion", command.expectedTenantVersion());
        metadata.put("dataPlan", planMap(plan));
        var preview = insertPlanOperation(session, systemId, tenantId, null, "RECOVERY_PREVIEW",
                "tenant recovery impact preview", metadata, plan, request);
        tenantSuccess(session, systemId, "TENANT_LIFECYCLE", preview.id(), "TENANT_RECOVERY_PREVIEWED",
                null, publicPlan(preview), request.requestId(), request);
        return preview;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public PlatformLifecycleApi.OperationView recoverTenant(
            AuthenticatedSession session, long systemId, long tenantId,
            PlatformLifecycleApi.RecoveryCommand command, String idempotencyKey, ClientRequest request) {
        tenantScope(session, systemId, tenantId);
        require(command != null && command.impactConfirmed(), "impactConfirmed must be true");
        required(command.reason(), "reason", 1000);
        return systemMutations.idempotent(systemId + ":tenant:" + tenantId + ":recovery",
                idempotencyKey, command, PlatformLifecycleApi.OperationView.class, () -> {
                    var tenant = requireTenant(systemId, tenantId, true);
                    requireVersion(tenant, command.expectedTenantVersion());
                    if (!Set.of("DISABLED", "ARCHIVED").contains(text(tenant, "status"))) {
                        throw state("tenant must be disabled or archived before recovery");
                    }
                    if (count("SELECT COUNT(*) FROM un_plat_context_session WHERE system_id=? AND tenant_id=? "
                            + "AND status='ACTIVE'", systemId, tenantId) > 0) {
                        throw conflict("TENANT_RECOVERY_ACTIVE_SESSION", "target tenant still has active sessions");
                    }
                    if (count("SELECT COUNT(*) FROM un_sys_job WHERE system_id=? AND tenant_id=? "
                            + "AND status IN ('QUEUED','RUNNING')", systemId, tenantId) > 0) {
                        throw conflict("TENANT_RECOVERY_ACTIVE_JOB", "target tenant still has active jobs");
                    }
                    var preview = planForUpdate(systemId, tenantId,
                            positive(command.planOperationId(), "planOperationId"), "RECOVERY_PREVIEW");
                    var metadata = readMap(text(preview, "snapshot_json"));
                    validatePlanConfirmation(preview, command.confirmationToken(), command.expectedTenantVersion(), null);
                    var backup = backupRow(systemId, tenantId,
                            positive(textValue(metadata.get("backupOperationId")), "backupOperationId"));
                    var snapshotText = text(backup, "snapshot_json");
                    var snapshot = readMap(snapshotText);
                    if (!snapshotChecksum(snapshot).equals(text(backup, "snapshot_checksum"))) {
                        throw conflict("TENANT_BACKUP_CHECKSUM_INVALID", "backup checksum validation failed");
                    }
                    var plan = dataLifecycle.withAdditionalBlockers(
                            dataLifecycle.recoveryPlan(systemId, tenantId, storedBackup(backup)),
                            controlRecoveryBlockers(systemId, tenantId, snapshot));
                    if (!plan.fingerprint().equals(text(preview, "plan_fingerprint"))) {
                        throw conflict("TENANT_DATA_IMPACT_CHANGED", "tenant recovery impact changed; preview again");
                    }
                    var execution = dataLifecycle.recover(systemId, tenantId, storedBackup(backup), plan);
                    restoreSnapshot(session, systemId, tenantId, snapshot);
                    syncQuotaUsage(systemId, tenantId, plan.quotaProjection(), session.accountId());
                    consumePlan(preview);
                    var result = new LinkedHashMap<String, Object>();
                    result.put("backupOperationId", Long.toString(number(backup, "id")));
                    result.put("restoredDomainCount", list(snapshot, "domains").size());
                    result.put("restoredQuotaCount", list(snapshot, "quotas").size());
                    result.put("restoredDataRows", execution.totalRows());
                    result.put("tableRows", execution.tableRows());
                    result.put("restoredStatus", "DISABLED");
                    result.put("scope", "TENANT_FULL_DATA_PLANE_ENCRYPTED");
                    result.put("compensation", "TRANSACTIONAL_ROLLBACK");
                    result.put("fileHandling", "REFERENCE_AND_OBJECT_OWNERSHIP_ONLY");
                    var operation = insertExecutionOperation(session, systemId, tenantId, null,
                            number(preview, "id"), "RECOVERY", command.reason(), metadata, result, plan, request);
                    epochs.bumpSystem(systemId, session.accountId());
                    tenantSuccess(session, systemId, "TENANT_LIFECYCLE", operation.id(), "TENANT_RECOVERED",
                            Map.of("status", text(tenant, "status")), operation, idempotencyKey, request);
                    return operation;
                });
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public PlatformLifecycleApi.PlanView previewTenantMigration(
            AuthenticatedSession session, long systemId, long sourceTenantId,
            PlatformLifecycleApi.MigrationPreviewCommand command, ClientRequest request) {
        tenantScope(session, systemId, sourceTenantId);
        require(command != null, "migration preview command is required");
        requireSystemWide(session, systemId);
        var targetTenantId = positive(command.targetTenantId(), "targetTenantId");
        require(targetTenantId != sourceTenantId, "source and target tenant must differ");
        var source = requireTenant(systemId, sourceTenantId, true);
        var target = requireTenant(systemId, targetTenantId, true);
        requireVersion(source, command.expectedSourceVersion());
        requireVersion(target, command.expectedTargetVersion());
        var plan = dataLifecycle.migrationPlan(systemId, sourceTenantId, targetTenantId);
        var blockers = migrationBlockers(systemId, sourceTenantId, targetTenantId, source, target);
        plan = dataLifecycle.withAdditionalBlockers(plan, blockers);
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("expectedSourceVersion", command.expectedSourceVersion());
        metadata.put("expectedTargetVersion", command.expectedTargetVersion());
        metadata.put("dataPlan", planMap(plan));
        var preview = insertPlanOperation(session, systemId, sourceTenantId, targetTenantId, "MIGRATION_PREVIEW",
                "tenant migration impact preview", metadata, plan, request);
        tenantSuccess(session, systemId, "TENANT_LIFECYCLE", preview.id(), "TENANT_MIGRATION_PREVIEWED",
                null, publicPlan(preview), request.requestId(), request);
        return preview;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public PlatformLifecycleApi.OperationView migrateTenantConfiguration(
            AuthenticatedSession session, long systemId, long sourceTenantId,
            PlatformLifecycleApi.MigrationCommand command, String idempotencyKey, ClientRequest request) {
        tenantScope(session, systemId, sourceTenantId);
        require(command != null && command.impactConfirmed(), "impactConfirmed must be true");
        required(command.reason(), "reason", 1000);
        var targetTenantId = positive(command.targetTenantId(), "targetTenantId");
        require(targetTenantId != sourceTenantId, "source and target tenant must differ");
        requireSystemWide(session, systemId);
        return systemMutations.idempotent(systemId + ":tenant:" + sourceTenantId + ":migration:" + targetTenantId,
                idempotencyKey, command, PlatformLifecycleApi.OperationView.class, () -> {
                    var source = requireTenant(systemId, sourceTenantId, true);
                    var target = requireTenant(systemId, targetTenantId, true);
                    requireVersion(source, command.expectedSourceVersion());
                    requireVersion(target, command.expectedTargetVersion());
                    var preview = planForUpdate(systemId, sourceTenantId,
                            positive(command.planOperationId(), "planOperationId"), "MIGRATION_PREVIEW");
                    validatePlanConfirmation(preview, command.confirmationToken(), command.expectedSourceVersion(),
                            command.expectedTargetVersion());
                    if (number(preview, "target_tenant_id") != targetTenantId) {
                        throw conflict("TENANT_DATA_CONFIRMATION_INVALID", "migration target changed");
                    }
                    var blockers = migrationBlockers(systemId, sourceTenantId, targetTenantId, source, target);
                    var plan = dataLifecycle.withAdditionalBlockers(
                            dataLifecycle.migrationPlan(systemId, sourceTenantId, targetTenantId), blockers);
                    if (!plan.fingerprint().equals(text(preview, "plan_fingerprint"))) {
                        throw conflict("TENANT_DATA_IMPACT_CHANGED", "tenant migration impact changed; preview again");
                    }
                    if (!plan.eligible()) throw conflict("TENANT_DATA_PREFLIGHT_BLOCKED",
                            "tenant migration remains blocked: " + String.join(",", plan.blockers()));
                    var snapshot = snapshot(systemId, sourceTenantId);
                    var execution = dataLifecycle.migrate(systemId, sourceTenantId, targetTenantId, plan);
                    var now = LocalDateTime.now();
                    var domains = jdbc.update("UPDATE un_plat_tenant_domain SET tenant_id=?,updated_at=?,updated_by=?,"
                                    + "version=version+1 WHERE system_id=? AND tenant_id=? AND deleted_at IS NULL",
                            targetTenantId, now, session.accountId(), systemId, sourceTenantId);
                    var quotas = jdbc.update("UPDATE un_plat_quota SET tenant_id=?,updated_at=?,updated_by=?,"
                                    + "version=version+1 WHERE system_id=? AND tenant_id=?",
                            targetTenantId, now, session.accountId(), systemId, sourceTenantId);
                    syncQuotaUsage(systemId, targetTenantId, plan.quotaProjection(), session.accountId());
                    updateVersioned("UPDATE un_plat_tenant SET status='ARCHIVED',updated_at=?,updated_by=?,"
                                    + "version=version+1 WHERE id=? AND system_id=? AND version=?",
                            now, session.accountId(), sourceTenantId, systemId, number(source, "version"));
                    updateVersioned("UPDATE un_plat_tenant SET updated_at=?,updated_by=?,version=version+1 "
                                    + "WHERE id=? AND system_id=? AND version=?",
                            now, session.accountId(), targetTenantId, systemId, number(target, "version"));
                    consumePlan(preview);
                    var result = new LinkedHashMap<String, Object>();
                    result.put("movedDomains", domains);
                    result.put("movedQuotas", quotas);
                    result.put("movedDataRows", execution.totalRows());
                    result.put("tableRows", execution.tableRows());
                    result.put("sourceStatus", "ARCHIVED");
                    result.put("scope", "TENANT_FULL_DATA_PLANE_ENCRYPTED");
                    result.put("compensation", "TRANSACTIONAL_ROLLBACK");
                    result.put("fileHandling", "REFERENCE_AND_OBJECT_OWNERSHIP_ONLY");
                    var metadata = readMap(text(preview, "snapshot_json"));
                    var operation = insertExecutionOperation(session, systemId, sourceTenantId, targetTenantId,
                            number(preview, "id"), "MIGRATION", command.reason(), metadata, result, plan, request);
                    epochs.bumpSystem(systemId, session.accountId());
                    tenantSuccess(session, systemId, "TENANT_LIFECYCLE", operation.id(),
                            "TENANT_CONFIGURATION_MIGRATED", Map.of("sourceTenantId", Long.toString(sourceTenantId)),
                            operation, idempotencyKey, request);
                    return operation;
                });
    }

    private void restoreSnapshot(AuthenticatedSession session, long systemId, long tenantId,
                                 Map<String, Object> snapshot) {
        var now = LocalDateTime.now();
        var tenant = map(snapshot, "tenant");
        jdbc.update("UPDATE un_plat_tenant SET name=?,status='DISABLED',updated_at=?,updated_by=?,version=version+1 "
                        + "WHERE id=? AND system_id=?",
                textValue(tenant.get("name")), now, session.accountId(), tenantId, systemId);
        jdbc.update("UPDATE un_plat_tenant_domain SET status='DISABLED',is_primary=FALSE,verified_at=NULL,"
                        + "verification_token_hash=NULL,updated_at=?,updated_by=?,version=version+1 "
                        + "WHERE system_id=? AND tenant_id=? AND deleted_at IS NULL",
                now, session.accountId(), systemId, tenantId);
        var restoredDomains = list(snapshot, "domains").stream().map(JdbcPlatformLifecycleService::objectMap)
                .map(value -> domainName(textValue(value.get("domain_name")))).toList();
        if (restoredDomains.isEmpty()) {
            jdbc.update("UPDATE un_plat_tenant_domain SET deleted_at=?,deleted_by=?,updated_at=?,updated_by=?,"
                            + "version=version+1 WHERE system_id=? AND tenant_id=? AND deleted_at IS NULL",
                    now, session.accountId(), now, session.accountId(), systemId, tenantId);
        } else {
            var placeholders = String.join(",", java.util.Collections.nCopies(restoredDomains.size(), "?"));
            var arguments = new ArrayList<Object>();
            arguments.add(now); arguments.add(session.accountId()); arguments.add(now); arguments.add(session.accountId());
            arguments.add(systemId); arguments.add(tenantId); arguments.addAll(restoredDomains);
            jdbc.update("UPDATE un_plat_tenant_domain SET deleted_at=?,deleted_by=?,updated_at=?,updated_by=?,"
                            + "version=version+1 WHERE system_id=? AND tenant_id=? AND deleted_at IS NULL "
                            + "AND domain_normalized NOT IN (" + placeholders + ")", arguments.toArray());
        }
        for (var value : list(snapshot, "domains")) {
            var domain = objectMap(value);
            var name = domainName(textValue(domain.get("domain_name")));
            var existing = optionalRow("SELECT * FROM un_plat_tenant_domain WHERE system_id=? AND tenant_id=? "
                    + "AND domain_normalized=LOWER(?)", systemId, tenantId, name);
            var status = "VERIFIED".equals(textValue(domain.get("status"))) ? "VERIFIED" : "DISABLED";
            var primary = "VERIFIED".equals(status) && bool(domain, "is_primary");
            var verifiedAt = "VERIFIED".equals(status) ? now : null;
            if (existing == null) {
                jdbc.update("""
                        INSERT INTO un_plat_tenant_domain(id,system_id,tenant_id,domain_name,status,is_primary,
                          verification_token_hash,verified_at,created_at,created_by,updated_at,updated_by,
                          deleted_at,deleted_by,version)
                        VALUES(?,?,?,?,?,?,NULL,?,?,?,?,?,NULL,NULL,0)
                        """, ids.nextId(), systemId, tenantId, name, status, primary, verifiedAt,
                        now, session.accountId(), now, session.accountId());
            } else {
                jdbc.update("UPDATE un_plat_tenant_domain SET status=?,is_primary=?,verification_token_hash=NULL,"
                                + "verified_at=?,deleted_at=NULL,deleted_by=NULL,updated_at=?,updated_by=?,version=version+1 "
                                + "WHERE id=?",
                        status, primary, verifiedAt, now, session.accountId(), number(existing, "id"));
            }
        }
        var restoredQuotaKeys = list(snapshot, "quotas").stream().map(JdbcPlatformLifecycleService::objectMap)
                .map(value -> quotaKey(textValue(value.get("quota_key")))).toList();
        if (restoredQuotaKeys.isEmpty()) {
            jdbc.update("DELETE FROM un_plat_quota WHERE system_id=? AND tenant_id=?", systemId, tenantId);
        } else {
            var placeholders = String.join(",", java.util.Collections.nCopies(restoredQuotaKeys.size(), "?"));
            var arguments = new ArrayList<Object>();
            arguments.add(systemId); arguments.add(tenantId); arguments.addAll(restoredQuotaKeys);
            jdbc.update("DELETE FROM un_plat_quota WHERE system_id=? AND tenant_id=? AND quota_key NOT IN ("
                    + placeholders + ")", arguments.toArray());
        }
        for (var value : list(snapshot, "quotas")) {
            var quota = objectMap(value);
            var key = quotaKey(textValue(quota.get("quota_key")));
            var existing = optionalRow("SELECT * FROM un_plat_quota WHERE system_id=? AND tenant_id=? AND quota_key=?",
                    systemId, tenantId, key);
            var soft = nullableLong(quota.get("soft_limit"));
            var hard = longValue(quota.get("hard_limit"));
            var used = Math.min(longValue(quota.get("used_value")), hard);
            if (existing == null) {
                jdbc.update("""
                        INSERT INTO un_plat_quota(id,scope_type,system_id,tenant_id,quota_key,soft_limit,
                          hard_limit,used_value,status,created_at,created_by,updated_at,updated_by,version)
                        VALUES(?,'TENANT',?,?,?,?,?,?,'ACTIVE',?,?,?,?,0)
                        """, ids.nextId(), systemId, tenantId, key, soft, hard, used,
                        now, session.accountId(), now, session.accountId());
            } else {
                jdbc.update("UPDATE un_plat_quota SET soft_limit=?,hard_limit=?,used_value=?,status='ACTIVE',"
                                + "updated_at=?,updated_by=?,version=version+1 WHERE id=?",
                        soft, hard, used, now, session.accountId(), number(existing, "id"));
            }
        }
    }

    private Map<String, Object> snapshot(long systemId, long tenantId) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("schemaVersion", 1);
        snapshot.put("scope", "TENANT_CONTROL_PLANE");
        snapshot.put("systemId", Long.toString(systemId));
        snapshot.put("tenantId", Long.toString(tenantId));
        snapshot.put("tenant", safeTenant(requireTenant(systemId, tenantId, true)));
        snapshot.put("domains", jdbc.queryForList("SELECT domain_name,status,is_primary,verified_at "
                + "FROM un_plat_tenant_domain WHERE system_id=? AND tenant_id=? AND deleted_at IS NULL ORDER BY id",
                systemId, tenantId));
        snapshot.put("quotas", jdbc.queryForList("SELECT quota_key,soft_limit,hard_limit,used_value,status "
                + "FROM un_plat_quota WHERE system_id=? AND tenant_id=? ORDER BY quota_key", systemId, tenantId));
        snapshot.put("manifest", tenantManifest(systemId, tenantId));
        snapshot.put("capturedAt", LocalDateTime.now().toString());
        return snapshot;
    }

    private Map<String, Long> tenantManifest(long systemId, long tenantId) {
        var result = new TreeMap<String, Long>();
        var tables = jdbc.queryForList("SELECT DISTINCT table_name FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND column_name='tenant_id' ORDER BY table_name", String.class);
        for (var table : tables) {
            if (!table.matches("[a-z0-9_]+")) continue;
            var columns = jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND table_name=?", String.class, table);
            long value = columns.contains("system_id")
                    ? count("SELECT COUNT(*) FROM `" + table + "` WHERE system_id=? AND tenant_id=?", systemId, tenantId)
                    : count("SELECT COUNT(*) FROM `" + table + "` WHERE tenant_id=?", tenantId);
            if (value > 0) result.put(table, value);
        }
        return result;
    }

    private PlatformLifecycleApi.OperationView insertBackupOperation(
            AuthenticatedSession session, long id, long systemId, long tenantId, String reason,
            Map<String, Object> snapshot, Map<String, Object> result,
            TenantDataLifecycleEngine.BackupArtifact artifact, ClientRequest request) {
        var now = LocalDateTime.now();
        var payload = artifact.payload();
        insertLifecycleRow(id, systemId, tenantId, null, null, "BACKUP", "SUCCEEDED", reason,
                snapshot, result, artifact.plan().fingerprint(), null, null, null,
                payload.ciphertext(), payload.ciphertextSha256(), payload.plaintextSha256(),
                payload.keyReference(), payload.keyVersion(), artifact.plan().databaseMigrationVersion(),
                artifact.rowCount(), artifact.payloadSizeBytes(), now, session.accountId(), request);
        return operationView(id, systemId, tenantId, null, null, "BACKUP", "SUCCEEDED", reason,
                snapshot, result, artifact.rowCount(), artifact.payloadSizeBytes(),
                artifact.plan().databaseMigrationVersion(), now);
    }

    private PlatformLifecycleApi.OperationView insertExecutionOperation(
            AuthenticatedSession session, long systemId, long sourceTenantId, Long targetTenantId,
            long planOperationId, String type, String reason, Map<String, Object> snapshot,
            Map<String, Object> result, TenantDataLifecycleEngine.DataPlan plan, ClientRequest request) {
        var now = LocalDateTime.now();
        var id = ids.nextId();
        insertLifecycleRow(id, systemId, sourceTenantId, targetTenantId, planOperationId, type, "SUCCEEDED",
                reason, snapshot, result, plan.fingerprint(), null, null, null,
                null, null, null, null, null, plan.databaseMigrationVersion(),
                plan.rowCount(), plan.estimatedBytes(), now, session.accountId(), request);
        return operationView(id, systemId, sourceTenantId, targetTenantId, planOperationId, type, "SUCCEEDED",
                reason, snapshot, result, plan.rowCount(), plan.estimatedBytes(),
                plan.databaseMigrationVersion(), now);
    }

    private PlatformLifecycleApi.PlanView insertPlanOperation(
            AuthenticatedSession session, long systemId, long sourceTenantId, Long targetTenantId,
            String type, String reason, Map<String, Object> metadata,
            TenantDataLifecycleEngine.DataPlan plan, ClientRequest request) {
        var now = LocalDateTime.now();
        jdbc.update("UPDATE un_plat_tenant_lifecycle_operation SET status='EXPIRED',version=version+1 "
                        + "WHERE system_id=? AND source_tenant_id=? AND operation_type=? AND status='PREVIEWED'",
                systemId, sourceTenantId, type);
        var id = ids.nextId();
        var rawToken = plan.eligible() ? token() : null;
        var status = plan.eligible() ? "PREVIEWED" : "BLOCKED";
        var expiresAt = now.plusMinutes(15);
        var result = Map.<String, Object>of("eligible", plan.eligible(), "blockers", plan.blockers(),
                "scope", "TENANT_FULL_DATA_PLANE_ENCRYPTED");
        insertLifecycleRow(id, systemId, sourceTenantId, targetTenantId, null, type, status, reason,
                metadata, result, plan.fingerprint(), rawToken == null ? null : hash(rawToken), expiresAt, null,
                null, null, null, null, null, plan.databaseMigrationVersion(),
                plan.rowCount(), plan.estimatedBytes(), now, session.accountId(), request);
        return new PlatformLifecycleApi.PlanView(Long.toString(id), Long.toString(systemId),
                Long.toString(sourceTenantId), targetTenantId == null ? null : Long.toString(targetTenantId),
                type, status, plan.eligible(), plan.blockers(), plan.tableImpacts(), plan.quotaProjection(),
                plan.fingerprint(), rawToken, expiresAt, plan.rowCount(), plan.estimatedBytes(),
                plan.databaseMigrationVersion());
    }

    private void insertLifecycleRow(
            long id, long systemId, long sourceTenantId, Long targetTenantId, Long planOperationId,
            String type, String status, String reason, Map<String, Object> snapshot, Map<String, Object> result,
            String planFingerprint, String tokenHash, LocalDateTime expiresAt, LocalDateTime consumedAt,
            byte[] payload, String ciphertextHash, String plaintextHash, String keyReference, String keyVersion,
            String migrationVersion, long rowCount, long payloadSize, LocalDateTime now,
            long accountId, ClientRequest request) {
        jdbc.update("""
                INSERT INTO un_plat_tenant_lifecycle_operation(
                  id,system_id,source_tenant_id,target_tenant_id,plan_operation_id,operation_type,status,reason,
                  snapshot_json,result_json,snapshot_checksum,plan_fingerprint,confirmation_token_hash,
                  expires_at,consumed_at,payload_ciphertext,payload_ciphertext_sha256,
                  payload_plaintext_sha256,encryption_key_ref,encryption_key_version,payload_schema_version,
                  database_migration_version,payload_row_count,payload_size_bytes,requested_at,started_at,
                  finished_at,requested_by,request_id,trace_id,version)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)
                """, id, systemId, sourceTenantId, targetTenantId, planOperationId, type, status, reason.trim(),
                write(snapshot), write(result), snapshotChecksum(snapshot), planFingerprint, tokenHash,
                expiresAt, consumedAt, payload, ciphertextHash, plaintextHash, keyReference, keyVersion,
                TenantDataLifecycleEngine.PAYLOAD_SCHEMA_VERSION, migrationVersion, rowCount, payloadSize,
                now, now, now, accountId, request.requestId(), request.traceId());
    }

    private PlatformLifecycleApi.OperationView operationView(
            long id, long systemId, long sourceTenantId, Long targetTenantId, Long planOperationId,
            String type, String status, String reason, Map<String, Object> snapshot, Map<String, Object> result,
            long rowCount, long payloadSize, String migrationVersion, LocalDateTime now) {
        return new PlatformLifecycleApi.OperationView(Long.toString(id), Long.toString(systemId),
                Long.toString(sourceTenantId), targetTenantId == null ? null : Long.toString(targetTenantId),
                planOperationId == null ? null : Long.toString(planOperationId), type, status, reason.trim(),
                snapshotChecksum(snapshot), result, rowCount, payloadSize, migrationVersion, now, now);
    }

    private Map<String, Object> planMap(TenantDataLifecycleEngine.DataPlan plan) {
        var value = new LinkedHashMap<String, Object>();
        value.put("scope", "TENANT_FULL_DATA_PLANE_ENCRYPTED");
        value.put("schemaFingerprint", plan.schemaFingerprint());
        value.put("databaseMigrationVersion", plan.databaseMigrationVersion());
        value.put("planFingerprint", plan.fingerprint());
        value.put("rowCount", plan.rowCount());
        value.put("estimatedBytes", plan.estimatedBytes());
        value.put("insertOrder", plan.insertOrder());
        value.put("tableImpacts", plan.tableImpacts());
        value.put("quotaProjection", plan.quotaProjection());
        value.put("blockers", plan.blockers());
        value.put("fileHandling", "REFERENCE_AND_OBJECT_OWNERSHIP_ONLY");
        return value;
    }

    private PlatformLifecycleApi.PlanView publicPlan(PlatformLifecycleApi.PlanView plan) {
        return new PlatformLifecycleApi.PlanView(plan.id(), plan.systemId(), plan.sourceTenantId(),
                plan.targetTenantId(), plan.operationType(), plan.status(), plan.eligible(), plan.blockers(),
                plan.tableImpacts(), plan.quotaProjection(), plan.planFingerprint(), null, plan.expiresAt(),
                plan.rowCount(), plan.estimatedBytes(), plan.databaseMigrationVersion());
    }

    private Map<String, Object> backupRow(long systemId, long tenantId, long backupId) {
        return row("SELECT * FROM un_plat_tenant_lifecycle_operation WHERE id=? AND system_id=? "
                        + "AND source_tenant_id=? AND operation_type='BACKUP' AND status='SUCCEEDED'",
                backupId, systemId, tenantId);
    }

    private TenantDataLifecycleEngine.StoredBackup storedBackup(Map<String, Object> backup) {
        var metadata = readMap(text(backup, "snapshot_json"));
        var dataPlan = map(metadata, "dataPlan");
        var quotaLimits = new TreeMap<String, Long>();
        for (var value : list(metadata, "quotas")) {
            var quota = objectMap(value);
            if ("ACTIVE".equals(textValue(quota.get("status")))) {
                quotaLimits.put(quotaKey(textValue(quota.get("quota_key"))),
                        longValue(quota.get("hard_limit")));
            }
        }
        return new TenantDataLifecycleEngine.StoredBackup(number(backup, "id"),
                (byte[]) backup.get("payload_ciphertext"), text(backup, "payload_ciphertext_sha256"),
                text(backup, "payload_plaintext_sha256"), text(backup, "encryption_key_ref"),
                text(backup, "encryption_key_version"), textValue(dataPlan.get("schemaFingerprint")),
                number(backup, "payload_size_bytes"), Map.copyOf(quotaLimits));
    }

    private Map<String, Object> planForUpdate(long systemId, long sourceTenantId, long planId, String type) {
        return rowForUpdate("SELECT * FROM un_plat_tenant_lifecycle_operation WHERE id=? AND system_id=? "
                + "AND source_tenant_id=? AND operation_type=?", planId, systemId, sourceTenantId, type);
    }

    private void validatePlanConfirmation(Map<String, Object> preview, String rawToken,
                                          String expectedSourceVersion, String expectedTargetVersion) {
        var metadata = readMap(text(preview, "snapshot_json"));
        if (!"PREVIEWED".equals(text(preview, "status"))
                || LocalDateTime.now().isAfter(time(preview, "expires_at"))
                || !hash(required(rawToken, "confirmationToken", 512))
                .equals(text(preview, "confirmation_token_hash"))
                || !expectedSourceVersion.equals(textValue(metadata.get("expectedSourceVersion")))
                || (expectedTargetVersion != null
                && !expectedTargetVersion.equals(textValue(metadata.get("expectedTargetVersion"))))) {
            throw conflict("TENANT_DATA_CONFIRMATION_INVALID", "tenant data confirmation is invalid or expired");
        }
    }

    private void consumePlan(Map<String, Object> preview) {
        var now = LocalDateTime.now();
        if (jdbc.update("UPDATE un_plat_tenant_lifecycle_operation SET status='CONSUMED',consumed_at=?,"
                        + "version=version+1 "
                        + "WHERE id=? AND status='PREVIEWED'", now, number(preview, "id")) != 1) {
            throw conflict("TENANT_DATA_CONFIRMATION_INVALID", "tenant data confirmation was already consumed");
        }
    }

    private List<String> migrationBlockers(long systemId, long sourceTenantId, long targetTenantId,
                                           Map<String, Object> source, Map<String, Object> target) {
        var blockers = new ArrayList<String>();
        if (sourceTenantId == targetTenantId) blockers.add("SOURCE_TARGET_SAME");
        if (Boolean.TRUE.equals(source.get("is_default"))) blockers.add("SOURCE_TENANT_IS_DEFAULT");
        if (Boolean.TRUE.equals(target.get("is_default"))) blockers.add("TARGET_TENANT_IS_DEFAULT");
        if (!Set.of("DISABLED", "ARCHIVED").contains(text(source, "status"))) {
            blockers.add("SOURCE_TENANT_MUST_BE_DISABLED_OR_ARCHIVED");
        }
        if (!"DISABLED".equals(text(target, "status"))) blockers.add("TARGET_TENANT_MUST_BE_DISABLED");
        if (count("SELECT COUNT(*) FROM un_plat_context_session WHERE system_id=? AND tenant_id IN (?,?) "
                + "AND status='ACTIVE'", systemId, sourceTenantId, targetTenantId) > 0) {
            blockers.add("MIGRATION_TENANT_HAS_ACTIVE_SESSIONS");
        }
        if (count("SELECT COUNT(*) FROM un_sys_job WHERE system_id=? AND tenant_id IN (?,?) "
                + "AND status IN ('QUEUED','RUNNING')", systemId, sourceTenantId, targetTenantId) > 0) {
            blockers.add("MIGRATION_TENANT_HAS_ACTIVE_JOBS");
        }
        if (count("SELECT COUNT(*) FROM un_plat_tenant_domain WHERE system_id=? AND tenant_id=? "
                + "AND deleted_at IS NULL", systemId, targetTenantId) > 0) blockers.add("TARGET_DOMAIN_NOT_EMPTY");
        if (count("SELECT COUNT(*) FROM un_plat_quota WHERE system_id=? AND tenant_id=?",
                systemId, targetTenantId) > 0) blockers.add("TARGET_QUOTA_NOT_EMPTY");
        return List.copyOf(blockers);
    }

    private List<String> controlRecoveryBlockers(long systemId, long tenantId, Map<String, Object> snapshot) {
        var blockers = new ArrayList<String>();
        for (var value : list(snapshot, "domains")) {
            var domain = domainName(textValue(objectMap(value).get("domain_name")));
            if (count("SELECT COUNT(*) FROM un_plat_tenant_domain WHERE domain_normalized=LOWER(?) "
                    + "AND NOT (system_id=? AND tenant_id=?) AND deleted_at IS NULL", domain, systemId, tenantId) > 0) {
                blockers.add("DOMAIN_OWNED_BY_ANOTHER_TENANT:" + domain);
            }
        }
        return List.copyOf(blockers);
    }

    private void requireSystemWide(AuthenticatedSession session, long systemId) {
        if (!scopes.systemWide(session, systemId, "system.tenant.manage")) {
            throw new BusinessException("PERMISSION_DENIED", "system-wide tenant permission is required",
                    HttpStatus.FORBIDDEN);
        }
    }

    @SuppressWarnings("unchecked")
    private void syncQuotaUsage(long systemId, long tenantId, Map<String, Object> projections, long accountId) {
        var now = LocalDateTime.now();
        projections.forEach((key, raw) -> {
            var projection = (Map<String, Object>) raw;
            var used = longValue(projection.get("used"));
            var updated = jdbc.update("UPDATE un_plat_quota SET used_value=?,updated_at=?,updated_by=?,version=version+1 "
                            + "WHERE system_id=? AND tenant_id=? AND quota_key=? AND status='ACTIVE' AND hard_limit>=?",
                    used, now, accountId, systemId, tenantId, key, used);
            if (updated != 1) throw conflict("TENANT_QUOTA_EXCEEDED",
                    "quota projection is missing or exceeds hard limit: " + key);
        });
    }

    private Map<String, Long> systemDependencies(long systemId) {
        var result = new TreeMap<String, Long>();
        var tables = jdbc.queryForList("SELECT DISTINCT table_name FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND column_name='system_id' ORDER BY table_name", String.class);
        for (var table : tables) {
            if (!table.matches("[a-z0-9_]+") || DEPENDENCY_EXCLUSIONS.contains(table)
                    || "un_plat_system".equals(table)) continue;
            var value = count("SELECT COUNT(*) FROM `" + table + "` WHERE system_id=?", systemId);
            if (value > 0) result.put(table, value);
        }
        return result;
    }

    private List<String> deleteBlockers(long systemId, String status) {
        var blockers = new ArrayList<String>();
        if (!"ARCHIVED".equals(status)) blockers.add("SYSTEM_NOT_ARCHIVED");
        if (count("SELECT COUNT(*) FROM un_sys_job WHERE system_id=? AND status IN ('QUEUED','RUNNING')", systemId) > 0) {
            blockers.add("ACTIVE_DURABLE_JOBS");
        }
        if (count("SELECT COUNT(*) FROM un_module_config_check WHERE system_id=? AND status='RUNNING'", systemId) > 0) {
            blockers.add("RUNNING_CONFIGURATION_CHECK");
        }
        return List.copyOf(blockers);
    }

    private String impactFingerprint(long systemId, long version, Map<String, Long> dependencies,
                                     List<String> blockers) {
        return hash(systemId + "|" + version + "|" + write(new TreeMap<>(dependencies)) + "|"
                + write(blockers.stream().sorted().toList()));
    }

    private void tenantScope(AuthenticatedSession session, long systemId, long tenantId) {
        scopes.requireTenant(session, systemId, tenantId, "system.tenant.manage");
    }

    private void tenantSuccess(AuthenticatedSession session, long systemId, String aggregateType,
                               String aggregateId, String action, Object before, Object after,
                               String dedupe, ClientRequest request) {
        systemMutations.success(session, systemId, aggregateType, aggregateId, action, before, after, request);
        systemMutations.outbox(session, systemId, aggregateType, aggregateId, action, after,
                dedupe == null ? request.requestId() : dedupe, request);
    }

    private Map<String, Object> system(long systemId, boolean includeDeleted) {
        return row("SELECT * FROM un_plat_system WHERE id=?" + (includeDeleted ? "" : " AND deleted_at IS NULL"), systemId);
    }
    private Map<String, Object> systemForUpdate(long systemId) {
        return rowForUpdate("SELECT * FROM un_plat_system WHERE id=? AND deleted_at IS NULL", systemId);
    }
    private Map<String, Object> requireTenant(long systemId, long tenantId, boolean forUpdate) {
        var sql = "SELECT * FROM un_plat_tenant WHERE id=? AND system_id=? AND deleted_at IS NULL"
                + (forUpdate ? " FOR UPDATE" : "");
        return row(sql, tenantId, systemId);
    }
    private Map<String, Object> domainForUpdate(long systemId, long tenantId, long domainId) {
        return rowForUpdate("SELECT * FROM un_plat_tenant_domain WHERE id=? AND system_id=? AND tenant_id=? "
                + "AND deleted_at IS NULL", domainId, systemId, tenantId);
    }
    private Map<String, Object> row(String sql, Object... args) {
        try { return jdbc.queryForMap(sql, args); }
        catch (EmptyResultDataAccessException missing) { throw PlatformMutationSupport.notFound("resource"); }
    }
    private Map<String, Object> rowForUpdate(String sql, Object... args) {
        return row(sql + (sql.toUpperCase(Locale.ROOT).contains(" FOR UPDATE") ? "" : " FOR UPDATE"), args);
    }
    private Map<String, Object> optionalRow(String sql, Object... args) {
        try { return jdbc.queryForMap(sql, args); }
        catch (EmptyResultDataAccessException missing) { return null; }
    }

    private PlatformLifecycleApi.DomainView domain(java.sql.ResultSet rs, String rawToken) throws java.sql.SQLException {
        return new PlatformLifecycleApi.DomainView(Long.toString(rs.getLong("id")),
                Long.toString(rs.getLong("system_id")), Long.toString(rs.getLong("tenant_id")),
                rs.getString("domain_name"), rs.getString("status"), rs.getBoolean("is_primary"), rawToken,
                rs.getTimestamp("verified_at") == null ? null : rs.getTimestamp("verified_at").toLocalDateTime(),
                Long.toString(rs.getLong("version")));
    }
    private PlatformLifecycleApi.DomainView domain(Map<String, Object> row, String rawToken) {
        return new PlatformLifecycleApi.DomainView(Long.toString(number(row, "id")),
                Long.toString(number(row, "system_id")), Long.toString(number(row, "tenant_id")),
                text(row, "domain_name"), text(row, "status"), bool(row, "is_primary"), rawToken,
                nullableTime(row.get("verified_at")), Long.toString(number(row, "version")));
    }
    private Object publicDomain(PlatformLifecycleApi.DomainView value) {
        return new PlatformLifecycleApi.DomainView(value.id(), value.systemId(), value.tenantId(), value.domainName(),
                value.status(), value.primary(), null, value.verifiedAt(), value.version());
    }
    private PlatformLifecycleApi.QuotaView quota(java.sql.ResultSet rs) throws java.sql.SQLException {
        var softObject = rs.getObject("soft_limit");
        var soft = softObject == null ? null : ((Number) softObject).longValue();
        var used = rs.getLong("used_value");
        return new PlatformLifecycleApi.QuotaView(Long.toString(rs.getLong("id")),
                Long.toString(rs.getLong("system_id")), Long.toString(rs.getLong("tenant_id")),
                rs.getString("quota_key"), soft, rs.getLong("hard_limit"), used,
                soft != null && used > soft, rs.getString("status"), Long.toString(rs.getLong("version")));
    }
    private PlatformLifecycleApi.QuotaView quota(Map<String, Object> row) {
        var soft = nullableLong(row.get("soft_limit"));
        var used = number(row, "used_value");
        return new PlatformLifecycleApi.QuotaView(Long.toString(number(row, "id")),
                Long.toString(number(row, "system_id")), Long.toString(number(row, "tenant_id")),
                text(row, "quota_key"), soft, number(row, "hard_limit"), used,
                soft != null && used > soft, text(row, "status"), Long.toString(number(row, "version")));
    }

    private void requireVersion(Map<String, Object> row, String expected) {
        if (number(row, "version") != version(expected)) throw PlatformMutationSupport.versionConflict();
    }
    private void updateVersioned(String sql, Object... args) {
        if (jdbc.update(sql, args) != 1) throw PlatformMutationSupport.versionConflict();
    }
    private long count(String sql, Object... args) {
        var value = jdbc.queryForObject(sql, Long.class, args); return value == null ? 0 : value;
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception failure) { throw new IllegalStateException("lifecycle JSON serialization failed", failure); }
    }
    private Map<String, Object> readMap(String value) {
        try { return json.readValue(value, new TypeReference<>() { }); }
        catch (Exception failure) { throw conflict("TENANT_BACKUP_INVALID", "backup payload is invalid"); }
    }
    private static Map<String, Object> safeTenant(Map<String, Object> row) {
        var value = new LinkedHashMap<String, Object>();
        for (var key : List.of("id", "system_id", "tenant_code", "name", "is_default", "status", "version")) {
            value.put(key, row.get(key));
        }
        return value;
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Object value) { return (Map<String, Object>) value; }
    private static Map<String, Object> map(Map<String, Object> value, String key) { return objectMap(value.get(key)); }
    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<String, Object> value, String key) { return (List<Object>) value.get(key); }
    private static String text(Map<String, Object> row, String key) { return textValue(row.get(key)); }
    private static String textValue(Object value) { return value == null ? null : String.valueOf(value); }
    private static long number(Map<String, Object> row, String key) { return longValue(row.get(key)); }
    private static long longValue(Object value) { return ((Number) value).longValue(); }
    private static Long nullableLong(Object value) { return value == null ? null : ((Number) value).longValue(); }
    private static boolean bool(Map<String, Object> row, String key) {
        var value = row.get(key); return value instanceof Boolean b ? b : ((Number) value).intValue() != 0;
    }
    private static LocalDateTime time(Map<String, Object> row, String key) { return nullableTime(row.get(key)); }
    private static LocalDateTime nullableTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime local) return local;
        return ((java.sql.Timestamp) value).toLocalDateTime();
    }
    private static long positive(String value, String field) {
        var id = PlatformMutationSupport.id(value); if (id <= 0) require(false, field + " must be positive"); return id;
    }
    private static long version(String value) { return PlatformMutationSupport.version(value); }
    private static long nonnegative(Long value, String field) {
        if (value == null || value < 0) require(false, field + " must be nonnegative"); return value;
    }
    private static String required(String value, String field, int max) {
        return PlatformMutationSupport.required(value, field, max);
    }
    private static String quotaKey(String value) {
        var key = required(value, "quotaKey", 96).toUpperCase(Locale.ROOT);
        if (!QUOTA_KEYS.contains(key)) require(false, "unsupported quotaKey"); return key;
    }
    private static String domainName(String value) {
        try {
            var raw = required(value, "domainName", 253);
            if (raw.endsWith(".")) raw = raw.substring(0, raw.length() - 1);
            var ascii = IDN.toASCII(raw, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            if (ascii.length() > 253 || !ascii.contains(".") || ascii.matches("^[0-9.]+$")) throw new IllegalArgumentException();
            for (var label : ascii.split("\\.")) {
                if (label.isBlank() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) {
                    throw new IllegalArgumentException();
                }
            }
            return ascii;
        } catch (Exception invalid) { throw PlatformMutationSupport.validation("domainName is invalid"); }
    }
    private static String token() {
        var bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private String snapshotChecksum(Map<String, Object> snapshot) {
        return hash(write(canonical(snapshot)));
    }
    private static Object canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            var sorted = new TreeMap<String, Object>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), canonical(item)));
            return sorted;
        }
        if (value instanceof List<?> list) return list.stream().map(JdbcPlatformLifecycleService::canonical).toList();
        return value;
    }
    private static String hash(String value) { return SystemAdminMutationSupport.sha256(value); }
    private static void require(boolean condition, String message) {
        if (!condition) throw PlatformMutationSupport.validation(message);
    }
    private static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }
    private static BusinessException state(String message) {
        return new BusinessException("STATE_TRANSITION_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
