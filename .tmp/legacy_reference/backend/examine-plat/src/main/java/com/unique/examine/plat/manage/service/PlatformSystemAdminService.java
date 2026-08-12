package com.unique.examine.plat.manage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.base.entity.Member;
import com.unique.examine.plat.base.entity.System;
import com.unique.examine.plat.base.entity.Tenant;
import com.unique.examine.plat.base.mapper.PlatAccountMapper;
import com.unique.examine.plat.base.mapper.PlatMemberMapper;
import com.unique.examine.plat.base.mapper.PlatSystemMapper;
import com.unique.examine.plat.base.mapper.PlatTenantMapper;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.LifecycleCommand;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.PageResult;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.SystemCreate;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.SystemUpdate;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.SystemView;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.SystemTombstoneView;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.TombstoneRestore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Service
public class PlatformSystemAdminService {
    private static final Set<String> STATUSES = Set.of(
            "INITIALIZING", "INIT_FAILED", "ACTIVE", "DISABLED", "ARCHIVED"
    );

    private final PlatSystemMapper systemMapper;
    private final PlatTenantMapper tenantMapper;
    private final PlatMemberMapper memberMapper;
    private final PlatAccountMapper accountMapper;
    private final AuthorizationProvisioningService authorizationProvisioningService;
    private final AuthzEpochService epochService;
    private final IdService idService;
    private final PlatformMutationSupport mutations;
    private final TransactionTemplate nestedInitialization;
    private final JdbcTemplate jdbc;

    public PlatformSystemAdminService(
            PlatSystemMapper systemMapper,
            PlatTenantMapper tenantMapper,
            PlatMemberMapper memberMapper,
            PlatAccountMapper accountMapper,
            AuthorizationProvisioningService authorizationProvisioningService,
            AuthzEpochService epochService,
            IdService idService,
            PlatformMutationSupport mutations,
            PlatformTransactionManager transactionManager,
            JdbcTemplate jdbc
    ) {
        this.systemMapper = systemMapper;
        this.tenantMapper = tenantMapper;
        this.memberMapper = memberMapper;
        this.accountMapper = accountMapper;
        this.authorizationProvisioningService = authorizationProvisioningService;
        this.epochService = epochService;
        this.idService = idService;
        this.mutations = mutations;
        this.nestedInitialization = new TransactionTemplate(transactionManager);
        this.nestedInitialization.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        this.jdbc = jdbc;
    }

    public PageResult<SystemView> list(Integer pageValue, Integer sizeValue, String keyword, String status) {
        var page = PlatformMutationSupport.page(pageValue);
        var size = PlatformMutationSupport.size(sizeValue);
        var normalizedStatus = normalizeStatus(status);
        var query = Wrappers.<System>lambdaQuery()
                .eq(normalizedStatus != null, System::getStatus, normalizedStatus)
                .and(keyword != null && !keyword.isBlank(), value -> value
                        .like(System::getSystemCode, keyword.trim())
                        .or()
                        .like(System::getName, keyword.trim()))
                .orderByDesc(System::getCreatedAt)
                .orderByDesc(System::getId);
        var result = systemMapper.selectPage(new Page<>(page, size), query);
        return new PageResult<>(result.getRecords().stream().map(this::view).toList(), page, size, result.getTotal());
    }

    public PageResult<SystemTombstoneView> tombstones(
            Integer pageValue, Integer sizeValue, String keyword
    ) {
        var page = PlatformMutationSupport.page(pageValue);
        var size = PlatformMutationSupport.size(sizeValue);
        var search = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
        var where = search == null ? " WHERE deleted_at IS NOT NULL"
                : " WHERE deleted_at IS NOT NULL AND (system_code LIKE ? OR name LIKE ?)";
        var parameters = search == null ? new Object[0] : new Object[]{search, search};
        var total = jdbc.queryForObject("SELECT COUNT(*) FROM un_plat_system" + where,
                Long.class, parameters);
        var paged = new java.util.ArrayList<Object>();
        java.util.Collections.addAll(paged, parameters);
        paged.add(size);
        paged.add(Math.multiplyExact(page - 1, size));
        var items = jdbc.query("SELECT * FROM un_plat_system" + where
                        + " ORDER BY deleted_at DESC,id DESC LIMIT ? OFFSET ?",
                (row, ignored) -> new SystemTombstoneView(
                        Long.toString(row.getLong("id")), row.getString("system_code"),
                        row.getString("name"), java.util.Objects.requireNonNullElse(
                                row.getString("description"), ""), row.getString("tenant_mode"),
                        Long.toString(row.getLong("owner_account_id")),
                        Long.toString(row.getLong("version")),
                        row.getTimestamp("deleted_at").toLocalDateTime(),
                        row.getString("tombstone_reason")), paged.toArray());
        return new PageResult<>(items, page, size, total == null ? 0 : total);
    }

    @Transactional
    public SystemView restoreTombstone(
            AuthenticatedSession session,
            String systemIdValue,
            TombstoneRestore request,
            String idempotencyKey,
            ClientRequest client
    ) {
        var systemId = PlatformMutationSupport.id(systemIdValue);
        return mutations.idempotent(session.accountId(), "SYSTEM_TOMBSTONE_RESTORE",
                Long.toString(systemId), idempotencyKey, request, SystemView.class,
                () -> restoreTombstoneOnce(session, systemId, request, idempotencyKey, client));
    }

    private SystemView restoreTombstoneOnce(
            AuthenticatedSession session, long systemId, TombstoneRestore request,
            String idempotencyKey, ClientRequest client
    ) {
        var reason = PlatformMutationSupport.required(request.reason(), "reason", 1000);
        var version = PlatformMutationSupport.version(request.version());
        var before = jdbc.queryForMap("SELECT id,status,version,deleted_at,tombstone_reason "
                + "FROM un_plat_system WHERE id=? FOR UPDATE", systemId);
        if (before.get("deleted_at") == null || !"ARCHIVED".equals(before.get("status"))) {
            throw new BusinessException("SYSTEM_TOMBSTONE_INVALID",
                    "system is not a recoverable tombstone", HttpStatus.CONFLICT);
        }
        if (((Number) before.get("version")).longValue() != version) {
            throw PlatformMutationSupport.versionConflict();
        }
        var now = LocalDateTime.now();
        var changed = jdbc.update("UPDATE un_plat_system SET deleted_at=NULL,deleted_by=NULL,"
                        + "tombstone_reason=NULL,updated_at=?,updated_by=?,version=version+1 "
                        + "WHERE id=? AND deleted_at IS NOT NULL AND version=?",
                now, session.accountId(), systemId, version);
        if (changed != 1) throw PlatformMutationSupport.versionConflict();
        var defaultTenant = jdbc.query("SELECT id FROM un_plat_tenant WHERE system_id=? "
                        + "AND deleted_at IS NULL ORDER BY id LIMIT 1",
                (row, ignored) -> row.getLong("id"), systemId).stream().findFirst();
        // Confirmed deletion deliberately disables every active tenant and clears its default flag.
        // A recovered system needs one valid entry tenant; setting only is_default while the row is
        // still DISABLED violates ck_plat_tenant_default_state and leaves the owner unable to enter.
        defaultTenant.ifPresent(tenantId -> jdbc.update("UPDATE un_plat_tenant SET status='ACTIVE',"
                        + "is_default=TRUE,updated_at=?,updated_by=?,version=version+1 "
                        + "WHERE id=? AND system_id=? AND deleted_at IS NULL",
                now, session.accountId(), tenantId, systemId));
        var after = view(requireSystem(systemId));
        mutations.success(session, client, "PLATFORM_SYSTEM", after.id(),
                "PLATFORM_SYSTEM_TOMBSTONE_RESTORED", before,
                java.util.Map.of("system", after, "reason", reason), idempotencyKey);
        return after;
    }

    @Transactional
    public SystemView create(
            AuthenticatedSession session,
            SystemCreate request,
            String idempotencyKey,
            ClientRequest client
    ) {
        return mutations.idempotent(
                session.accountId(),
                "SYSTEM_CREATE",
                "new",
                idempotencyKey,
                request,
                SystemView.class,
                () -> createOnce(session, request, idempotencyKey, client)
        );
    }

    @Transactional
    public SystemView update(
            AuthenticatedSession session,
            String systemId,
            SystemUpdate request,
            ClientRequest client
    ) {
        var system = requireSystem(PlatformMutationSupport.id(systemId));
        requireVersion(system.getVersion(), request.version());
        var before = view(system);
        system.setName(PlatformMutationSupport.required(request.name(), "name", 160));
        system.setDescription(PlatformMutationSupport.optional(request.description(), "description", 1000));
        system.setUpdatedAt(LocalDateTime.now());
        system.setUpdatedBy(session.accountId());
        if (systemMapper.updateById(system) != 1) {
            throw PlatformMutationSupport.versionConflict();
        }
        var after = view(system);
        mutations.success(
                session, client, "PLATFORM_SYSTEM", after.id(), "PLATFORM_SYSTEM_UPDATED",
                before, after, null
        );
        return after;
    }

    @Transactional
    public SystemView command(
            AuthenticatedSession session,
            String systemId,
            String command,
            LifecycleCommand request,
            String idempotencyKey,
            ClientRequest client
    ) {
        var targetId = PlatformMutationSupport.id(systemId);
        return mutations.idempotent(
                session.accountId(),
                "SYSTEM_" + command.toUpperCase(Locale.ROOT),
                Long.toString(targetId),
                idempotencyKey,
                request,
                SystemView.class,
                () -> commandOnce(session, targetId, command, request, idempotencyKey, client)
        );
    }

    private SystemView createOnce(
            AuthenticatedSession session,
            SystemCreate request,
            String idempotencyKey,
            ClientRequest client
    ) {
        var code = normalizeCode(request.code());
        var name = PlatformMutationSupport.required(request.name(), "name", 160);
        var description = PlatformMutationSupport.optional(request.description(), "description", 1000);
        var tenantMode = normalizeTenantMode(request.tenantMode());
        if (jdbc.queryForObject("SELECT COUNT(*) FROM un_plat_system WHERE system_code=?",
                Long.class, code) > 0) {
            throw new BusinessException("DATA_CONFLICT", "系统编码已存在", HttpStatus.CONFLICT);
        }
        var owner = accountMapper.selectById(session.accountId());
        if (owner == null) {
            throw PlatformMutationSupport.notFound("账号");
        }

        var now = LocalDateTime.now();
        var systemId = idService.nextId();
        var system = new System();
        system.setId(systemId);
        system.setSystemCode(code);
        system.setName(name);
        system.setDescription(description);
        system.setStatus("INITIALIZING");
        system.setTenantMode(tenantMode);
        system.setOwnerAccountId(session.accountId());
        system.setPermissionVersion(1L);
        system.setCreatedAt(now);
        system.setCreatedBy(session.accountId());
        system.setUpdatedAt(now);
        system.setUpdatedBy(session.accountId());
        system.setVersion(0L);
        systemMapper.insert(system);

        try {
            nestedInitialization.executeWithoutResult(ignored -> initializeSystem(
                    system, owner, session.accountId(), now));
        } catch (RuntimeException failure) {
            markInitializationFailed(system, session.accountId(), failure);
        }

        var after = view(system);
        mutations.success(
                session, client, "PLATFORM_SYSTEM", after.id(),
                "ACTIVE".equals(after.status())
                        ? "PLATFORM_SYSTEM_CREATED" : "PLATFORM_SYSTEM_INITIALIZATION_FAILED",
                null, after, idempotencyKey
        );
        return after;
    }

    private void initializeSystem(
            System system, Account owner, long actorAccountId, LocalDateTime now
    ) {
        var tenantId = idService.nextId();
        var memberId = idService.nextId();
        var roleId = idService.nextId();

        var tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setSystemId(system.getId());
        tenant.setTenantCode("default");
        tenant.setName(defaultTenantName(system.getName()));
        tenant.setIsDefault(true);
        tenant.setStatus("ACTIVE");
        tenant.setCreatedAt(now);
        tenant.setCreatedBy(actorAccountId);
        tenant.setUpdatedAt(now);
        tenant.setUpdatedBy(actorAccountId);
        tenant.setVersion(0L);
        tenantMapper.insert(tenant);

        var member = new Member();
        member.setId(memberId);
        member.setSystemId(system.getId());
        member.setAccountId(actorAccountId);
        member.setMemberCode("OWNER_" + memberId);
        member.setDisplayName(owner.getDisplayName());
        member.setDefaultTenantId(tenantId);
        member.setStatus("ACTIVE");
        member.setJoinedAt(now);
        member.setCreatedAt(now);
        member.setCreatedBy(actorAccountId);
        member.setUpdatedAt(now);
        member.setUpdatedBy(actorAccountId);
        member.setVersion(0L);
        memberMapper.insert(member);

        authorizationProvisioningService.provisionSystemOwner(
                actorAccountId, system.getId(), tenantId, memberId, roleId, now
        );

        system.setStatus("ACTIVE");
        system.setInitializedAt(now);
        system.setInitFailureCode(null);
        system.setInitFailedAt(null);
        system.setUpdatedAt(now);
        system.setUpdatedBy(actorAccountId);
        if (systemMapper.updateById(system) != 1) {
            throw PlatformMutationSupport.versionConflict();
        }
    }

    private void markInitializationFailed(System system, long actorAccountId, RuntimeException failure) {
        var now = LocalDateTime.now();
        system.setStatus("INIT_FAILED");
        system.setInitializedAt(null);
        system.setInitFailureCode(initializationFailureCode(failure));
        system.setInitFailedAt(now);
        system.setUpdatedAt(now);
        system.setUpdatedBy(actorAccountId);
        if (systemMapper.updateById(system) != 1) throw PlatformMutationSupport.versionConflict();
    }

    private SystemView commandOnce(
            AuthenticatedSession session,
            long systemId,
            String command,
            LifecycleCommand request,
            String idempotencyKey,
            ClientRequest client
    ) {
        PlatformMutationSupport.required(request.reason(), "reason", 1000);
        if (!Boolean.TRUE.equals(request.impactConfirmed())) {
            throw PlatformMutationSupport.validation("impactConfirmed 必须为 true");
        }
        var system = requireSystem(systemId);
        requireVersion(system.getVersion(), request.version());
        if ("retry-initialization".equals(command)) {
            return retryInitialization(session, system, request.reason(), idempotencyKey, client);
        }
        var before = view(system);
        var nextStatus = nextStatus(system.getStatus(), command);
        var now = LocalDateTime.now();
        system.setStatus(nextStatus);
        system.setUpdatedAt(now);
        system.setUpdatedBy(session.accountId());
        if ("ARCHIVED".equals(nextStatus)) {
            system.setArchivedAt(now);
        }
        if (systemMapper.updateById(system) != 1) {
            throw PlatformMutationSupport.versionConflict();
        }
        if ("restore".equals(command)) {
            systemMapper.update(null, Wrappers.<System>lambdaUpdate()
                    .eq(System::getId, systemId)
                    .set(System::getArchivedAt, null));
        }

        epochService.bumpSystem(systemId, session.accountId());
        var refreshed = requireSystem(systemId);
        var after = view(refreshed);
        mutations.success(
                session, client, "PLATFORM_SYSTEM", after.id(),
                "PLATFORM_SYSTEM_" + command.toUpperCase(Locale.ROOT),
                before,
                java.util.Map.of("system", after, "reason", request.reason().trim(), "impactConfirmed", true),
                idempotencyKey
        );
        return after;
    }

    private SystemView retryInitialization(
            AuthenticatedSession session,
            System system,
            String reason,
            String idempotencyKey,
            ClientRequest client
    ) {
        if (!"INIT_FAILED".equals(system.getStatus())) {
            throw new BusinessException("STATE_TRANSITION_INVALID",
                    "only INIT_FAILED systems can retry initialization", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        var owner = accountMapper.selectById(system.getOwnerAccountId());
        if (owner == null) throw PlatformMutationSupport.notFound("account");
        var before = view(system);
        var now = LocalDateTime.now();
        system.setStatus("INITIALIZING");
        system.setInitFailureCode(null);
        system.setInitFailedAt(null);
        system.setUpdatedAt(now);
        system.setUpdatedBy(session.accountId());
        if (systemMapper.updateById(system) != 1) throw PlatformMutationSupport.versionConflict();
        try {
            nestedInitialization.executeWithoutResult(ignored -> initializeSystem(
                    system, owner, session.accountId(), LocalDateTime.now()));
        } catch (RuntimeException failure) {
            markInitializationFailed(system, session.accountId(), failure);
        }
        var after = view(system);
        mutations.success(session, client, "PLATFORM_SYSTEM", after.id(),
                "ACTIVE".equals(after.status())
                        ? "PLATFORM_SYSTEM_INITIALIZATION_RECOVERED"
                        : "PLATFORM_SYSTEM_INITIALIZATION_RETRY_FAILED",
                before, java.util.Map.of("system", after, "reason", reason.trim()), idempotencyKey);
        return after;
    }

    static String nextStatus(String current, String command) {
        if ("activate".equals(command)
                && ("INITIALIZING".equals(current) || "DISABLED".equals(current))) {
            return "ACTIVE";
        }
        if ("disable".equals(command) && "ACTIVE".equals(current)) {
            return "DISABLED";
        }
        if ("archive".equals(command) && "DISABLED".equals(current)) {
            return "ARCHIVED";
        }
        if ("restore".equals(command) && "ARCHIVED".equals(current)) {
            return "DISABLED";
        }
        throw new BusinessException(
                "STATE_TRANSITION_INVALID",
                "系统当前状态不允许执行该操作",
                HttpStatus.UNPROCESSABLE_ENTITY
        );
    }

    private System requireSystem(long id) {
        var system = systemMapper.selectById(id);
        if (system == null) {
            throw PlatformMutationSupport.notFound("系统");
        }
        return system;
    }

    private void requireVersion(long actual, String requested) {
        if (actual != PlatformMutationSupport.version(requested)) {
            throw PlatformMutationSupport.versionConflict();
        }
    }

    private SystemView view(System system) {
        return new SystemView(
                Long.toString(system.getId()),
                system.getSystemCode(),
                system.getName(),
                system.getDescription() == null ? "" : system.getDescription(),
                system.getStatus(),
                system.getTenantMode(),
                Long.toString(system.getOwnerAccountId()),
                Long.toString(system.getVersion()),
                system.getCreatedAt(),
                system.getInitFailureCode(),
                system.getInitFailedAt()
        );
    }

    static String initializationFailureCode(RuntimeException failure) {
        if (failure instanceof BusinessException business) return boundedCode(business.code());
        if (failure instanceof org.springframework.dao.DataIntegrityViolationException) {
            return "INITIALIZATION_DATA_CONFLICT";
        }
        return boundedCode("INITIALIZATION_" + failure.getClass().getSimpleName()
                .replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT));
    }

    private static String boundedCode(String value) {
        var normalized = value == null ? "INITIALIZATION_FAILED"
                : value.replaceAll("[^A-Za-z0-9_]", "_").toUpperCase(Locale.ROOT);
        return normalized.substring(0, Math.min(normalized.length(), 64));
    }

    private static String normalizeCode(String value) {
        var code = PlatformMutationSupport.required(value, "code", 64);
        code = Normalizer.normalize(code, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        if (!code.matches("[a-z][a-z0-9_-]{0,63}")) {
            throw PlatformMutationSupport.validation("code 格式不合法");
        }
        return code;
    }

    private static String normalizeTenantMode(String value) {
        var mode = PlatformMutationSupport.required(value, "tenantMode", 16).toUpperCase(Locale.ROOT);
        if (!Set.of("SINGLE", "MULTI").contains(mode)) {
            throw PlatformMutationSupport.validation("tenantMode 必须为 SINGLE 或 MULTI");
        }
        return mode;
    }

    private static String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var status = value.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) {
            throw PlatformMutationSupport.validation("status 不合法");
        }
        return status;
    }

    private static String defaultTenantName(String systemName) {
        var suffix = " 默认租户";
        return systemName.length() + suffix.length() <= 160
                ? systemName + suffix
                : systemName.substring(0, 160 - suffix.length()) + suffix;
    }
}
