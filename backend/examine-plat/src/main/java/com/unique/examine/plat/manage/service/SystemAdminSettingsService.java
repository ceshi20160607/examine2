package com.unique.examine.plat.manage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.base.entity.Member;
import com.unique.examine.plat.base.entity.MemberTenant;
import com.unique.examine.plat.base.entity.Tenant;
import com.unique.examine.plat.base.mapper.PlatMemberMapper;
import com.unique.examine.plat.base.mapper.PlatMemberTenantMapper;
import com.unique.examine.plat.base.mapper.PlatSystemMapper;
import com.unique.examine.plat.base.mapper.PlatTenantMapper;
import com.unique.examine.plat.manage.dto.SystemAdminRequests;
import com.unique.examine.plat.manage.vo.SystemAdminViews;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class SystemAdminSettingsService {
    private final PlatSystemMapper systemMapper;
    private final PlatTenantMapper tenantMapper;
    private final PlatMemberMapper memberMapper;
    private final PlatMemberTenantMapper memberTenantMapper;
    private final IdService idService;
    private final AuthzEpochService epochService;
    private final SystemAdminScopeSupport scopeSupport;
    private final SystemAdminMutationSupport mutationSupport;

    public SystemAdminSettingsService(
            PlatSystemMapper systemMapper,
            PlatTenantMapper tenantMapper,
            PlatMemberMapper memberMapper,
            PlatMemberTenantMapper memberTenantMapper,
            IdService idService,
            AuthzEpochService epochService,
            SystemAdminScopeSupport scopeSupport,
            SystemAdminMutationSupport mutationSupport
    ) {
        this.systemMapper = systemMapper;
        this.tenantMapper = tenantMapper;
        this.memberMapper = memberMapper;
        this.memberTenantMapper = memberTenantMapper;
        this.idService = idService;
        this.epochService = epochService;
        this.scopeSupport = scopeSupport;
        this.mutationSupport = mutationSupport;
    }

    public SystemAdminViews.Settings settings(AuthenticatedSession session, long systemId) {
        return settingsView(requireSystem(systemId));
    }

    @Transactional
    public SystemAdminViews.Settings updateSettings(
            AuthenticatedSession session,
            long systemId,
            SystemAdminRequests.UpdateSettings request,
            ClientRequest client
    ) {
        var system = requireSystem(systemId);
        requireVersion(system.getVersion(), request.version());
        if (!Objects.equals(nullToEmpty(system.getDescription()), request.description())
                && !session.permissions().contains("system.settings.description.edit")) {
            throw new BusinessException(
                    "PERMISSION_DENIED", "没有编辑系统说明的权限", HttpStatus.FORBIDDEN
            );
        }
        if ("SINGLE".equals(request.tenantMode())) {
            var activeTenantCount = tenantMapper.selectCount(Wrappers.<Tenant>lambdaQuery()
                    .eq(Tenant::getSystemId, systemId)
                    .ne(Tenant::getStatus, "ARCHIVED"));
            if (activeTenantCount > 1) {
                throw SystemAdminMutationSupport.invalidState("存在多个未归档租户，不能切换为单租户模式");
            }
        }

        var before = settingsView(system);
        system.setName(request.name().trim());
        system.setDescription(request.description().trim());
        system.setTenantMode(request.tenantMode());
        system.setUpdatedAt(LocalDateTime.now());
        system.setUpdatedBy(session.accountId());
        if (systemMapper.updateById(system) != 1) {
            throw SystemAdminMutationSupport.versionConflict();
        }
        var after = settingsView(system);
        mutationSupport.success(
                session, systemId, "SYSTEM", Long.toString(systemId), "SYSTEM_SETTINGS_UPDATE",
                before, after, client
        );
        mutationSupport.outbox(
                session, systemId, "SYSTEM", Long.toString(systemId), "SYSTEM_SETTINGS_UPDATED",
                after, client.requestId(), client
        );
        return after;
    }

    public SystemAdminViews.Page<SystemAdminViews.Tenant> tenants(
            AuthenticatedSession session,
            long systemId,
            int page,
            int size,
            String keyword,
            String status
    ) {
        requireSystem(systemId);
        var currentTenantId = scopeSupport.tenantId(session);
        var systemWide = scopeSupport.systemWide(session, systemId, "system.tenant.manage");
        var query = Wrappers.<Tenant>lambdaQuery()
                .eq(Tenant::getSystemId, systemId)
                .eq(!systemWide, Tenant::getId, currentTenantId)
                .eq(status != null && !status.isBlank(), Tenant::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), nested -> nested
                        .like(Tenant::getName, keyword.trim())
                        .or()
                        .like(Tenant::getTenantCode, keyword.trim()))
                .orderByDesc(Tenant::getIsDefault)
                .orderByAsc(Tenant::getCreatedAt);
        var result = tenantMapper.selectPage(new Page<>(page(page), size(size)), query);
        var items = result.getRecords().stream().map(this::tenantView).toList();
        return new SystemAdminViews.Page<>(items, (int) result.getCurrent(), (int) result.getSize(), result.getTotal());
    }

    @Transactional
    public SystemAdminViews.Tenant createTenant(
            AuthenticatedSession session,
            long systemId,
            SystemAdminRequests.CreateTenant request,
            String idempotencyKey,
            ClientRequest client
    ) {
        if (!scopeSupport.systemWide(session, systemId, "system.tenant.manage")) {
            throw new BusinessException("PERMISSION_DENIED", "只有系统级管理员可以创建租户", HttpStatus.FORBIDDEN);
        }
        return mutationSupport.idempotent(
                systemId + ":tenant:create", idempotencyKey, request,
                SystemAdminViews.Tenant.class,
                () -> createTenantNow(session, systemId, request, idempotencyKey, client)
        );
    }

    private SystemAdminViews.Tenant createTenantNow(
            AuthenticatedSession session,
            long systemId,
            SystemAdminRequests.CreateTenant request,
            String idempotencyKey,
            ClientRequest client
    ) {
        var system = requireSystem(systemId);
        if ("SINGLE".equals(system.getTenantMode())) {
            throw SystemAdminMutationSupport.invalidState("单租户模式不能创建额外租户");
        }
        var now = LocalDateTime.now();
        var tenant = new Tenant();
        tenant.setId(idService.nextId());
        tenant.setSystemId(systemId);
        tenant.setTenantCode(request.code().trim().toLowerCase(Locale.ROOT));
        tenant.setName(request.name().trim());
        tenant.setIsDefault(false);
        tenant.setStatus("ACTIVE");
        tenant.setCreatedAt(now);
        tenant.setCreatedBy(session.accountId());
        tenant.setUpdatedAt(now);
        tenant.setUpdatedBy(session.accountId());
        tenant.setVersion(0L);
        try {
            tenantMapper.insert(tenant);
        } catch (DataIntegrityViolationException exception) {
            throw SystemAdminMutationSupport.conflict("RESOURCE_CONFLICT", "租户编码已存在");
        }
        grantOwnerTenant(system, tenant, session.accountId(), now);
        epochService.bumpSystem(systemId, session.accountId());
        var after = tenantView(tenant);
        mutationSupport.success(
                session, systemId, "TENANT", after.id(), "SYSTEM_TENANT_CREATE",
                null, after, client
        );
        mutationSupport.outbox(
                session, systemId, "TENANT", after.id(), "SYSTEM_TENANT_CREATED",
                after, idempotencyKey, client
        );
        return after;
    }

    @Transactional
    public SystemAdminViews.Tenant updateTenant(
            AuthenticatedSession session,
            long systemId,
            long tenantId,
            SystemAdminRequests.UpdateTenant request,
            ClientRequest client
    ) {
        scopeSupport.requireTenant(session, systemId, tenantId, "system.tenant.manage");
        var tenant = requireTenant(systemId, tenantId);
        requireVersion(tenant.getVersion(), request.version());
        var before = tenantView(tenant);
        tenant.setName(request.name().trim());
        tenant.setUpdatedAt(LocalDateTime.now());
        tenant.setUpdatedBy(session.accountId());
        if (tenantMapper.updateById(tenant) != 1) {
            throw SystemAdminMutationSupport.versionConflict();
        }
        var after = tenantView(tenant);
        mutationSupport.success(
                session, systemId, "TENANT", after.id(), "SYSTEM_TENANT_UPDATE",
                before, after, client
        );
        mutationSupport.outbox(
                session, systemId, "TENANT", after.id(), "SYSTEM_TENANT_UPDATED",
                after, client.requestId(), client
        );
        return after;
    }

    @Transactional
    public SystemAdminViews.Tenant commandTenant(
            AuthenticatedSession session,
            long systemId,
            long tenantId,
            String command,
            SystemAdminRequests.LifecycleCommand request,
            String idempotencyKey,
            ClientRequest client
    ) {
        scopeSupport.requireTenant(session, systemId, tenantId, "system.tenant.manage");
        return mutationSupport.idempotent(
                systemId + ":tenant:" + tenantId + ":" + command,
                idempotencyKey, request, SystemAdminViews.Tenant.class,
                () -> commandTenantNow(
                        session, systemId, tenantId, command, request, idempotencyKey, client
                )
        );
    }

    private SystemAdminViews.Tenant commandTenantNow(
            AuthenticatedSession session,
            long systemId,
            long tenantId,
            String command,
            SystemAdminRequests.LifecycleCommand request,
            String idempotencyKey,
            ClientRequest client
    ) {
        var tenant = requireTenant(systemId, tenantId);
        requireVersion(tenant.getVersion(), request.version());
        if (Boolean.TRUE.equals(tenant.getIsDefault())
                && ("disable".equals(command) || "archive".equals(command))) {
            throw SystemAdminMutationSupport.invalidState("默认租户不能停用或归档");
        }
        if (("disable".equals(command) || "archive".equals(command)) && !request.impactConfirmed()) {
            throw SystemAdminMutationSupport.invalidState("危险状态命令必须确认影响");
        }
        var nextStatus = tenantTransition(tenant.getStatus(), command);
        var before = tenantView(tenant);
        tenant.setStatus(nextStatus);
        tenant.setUpdatedAt(LocalDateTime.now());
        tenant.setUpdatedBy(session.accountId());
        if (tenantMapper.updateById(tenant) != 1) {
            throw SystemAdminMutationSupport.versionConflict();
        }
        epochService.bumpSystem(systemId, session.accountId());
        var after = tenantView(tenant);
        mutationSupport.success(
                session, systemId, "TENANT", after.id(), "SYSTEM_TENANT_" + command.toUpperCase(Locale.ROOT),
                before, after, client
        );
        mutationSupport.outbox(
                session, systemId, "TENANT", after.id(), "SYSTEM_TENANT_STATUS_CHANGED",
                Map.of("tenant", after, "reason", request.reason()), idempotencyKey, client
        );
        return after;
    }

    static String tenantTransition(String status, String command) {
        if ("activate".equals(command) && "DISABLED".equals(status)) {
            return "ACTIVE";
        }
        if ("disable".equals(command) && "ACTIVE".equals(status)) {
            return "DISABLED";
        }
        if ("archive".equals(command) && "DISABLED".equals(status)) {
            return "ARCHIVED";
        }
        if ("restore".equals(command) && "ARCHIVED".equals(status)) {
            return "DISABLED";
        }
        throw SystemAdminMutationSupport.invalidState("租户当前状态不允许执行该命令");
    }

    private void grantOwnerTenant(
            com.unique.examine.plat.base.entity.System system,
            Tenant tenant,
            long actorAccountId,
            LocalDateTime now
    ) {
        var owner = memberMapper.selectOne(Wrappers.<Member>lambdaQuery()
                .eq(Member::getSystemId, system.getId())
                .eq(Member::getAccountId, system.getOwnerAccountId()));
        if (owner == null) {
            throw SystemAdminMutationSupport.notFound();
        }
        var access = new MemberTenant();
        access.setId(idService.nextId());
        access.setSystemId(system.getId());
        access.setMemberId(owner.getId());
        access.setTenantId(tenant.getId());
        access.setStatus("ACTIVE");
        access.setGrantedAt(now);
        access.setGrantedBy(actorAccountId);
        access.setCreatedAt(now);
        access.setCreatedBy(actorAccountId);
        access.setUpdatedAt(now);
        access.setUpdatedBy(actorAccountId);
        access.setVersion(0L);
        memberTenantMapper.insert(access);
    }

    private com.unique.examine.plat.base.entity.System requireSystem(long systemId) {
        var system = systemMapper.selectOne(Wrappers.<com.unique.examine.plat.base.entity.System>lambdaQuery()
                .eq(com.unique.examine.plat.base.entity.System::getId, systemId));
        if (system == null) {
            throw SystemAdminMutationSupport.notFound();
        }
        return system;
    }

    private Tenant requireTenant(long systemId, long tenantId) {
        var tenant = tenantMapper.selectOne(Wrappers.<Tenant>lambdaQuery()
                .eq(Tenant::getId, tenantId)
                .eq(Tenant::getSystemId, systemId));
        if (tenant == null) {
            throw SystemAdminMutationSupport.notFound();
        }
        return tenant;
    }

    private SystemAdminViews.Settings settingsView(com.unique.examine.plat.base.entity.System system) {
        var defaultTenant = tenantMapper.selectOne(Wrappers.<Tenant>lambdaQuery()
                .eq(Tenant::getSystemId, system.getId())
                .eq(Tenant::getIsDefault, true));
        if (defaultTenant == null) {
            throw SystemAdminMutationSupport.notFound();
        }
        return new SystemAdminViews.Settings(
                Long.toString(system.getId()), Long.toString(system.getId()), system.getName(),
                system.getSystemCode(), nullToEmpty(system.getDescription()), system.getTenantMode(),
                Long.toString(defaultTenant.getId()), system.getStatus(), Long.toString(system.getVersion())
        );
    }

    private SystemAdminViews.Tenant tenantView(Tenant tenant) {
        var memberCount = memberTenantMapper.selectCount(Wrappers.<MemberTenant>lambdaQuery()
                .eq(MemberTenant::getSystemId, tenant.getSystemId())
                .eq(MemberTenant::getTenantId, tenant.getId())
                .eq(MemberTenant::getStatus, "ACTIVE"));
        return new SystemAdminViews.Tenant(
                Long.toString(tenant.getId()), Long.toString(tenant.getSystemId()),
                tenant.getTenantCode(), tenant.getName(), tenant.getStatus(),
                Boolean.TRUE.equals(tenant.getIsDefault()), memberCount, Long.toString(tenant.getVersion())
        );
    }

    private static void requireVersion(long actual, String expected) {
        if (actual != SystemAdminMutationSupport.version(expected)) {
            throw SystemAdminMutationSupport.versionConflict();
        }
    }

    private static int page(int value) {
        return Math.max(value, 1);
    }

    private static int size(int value) {
        return Math.min(Math.max(value, 1), 100);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
