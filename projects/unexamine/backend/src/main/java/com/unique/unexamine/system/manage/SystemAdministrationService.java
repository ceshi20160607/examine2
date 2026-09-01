package com.unique.unexamine.system.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.unexamine.system.base.entity.SystemDefinition;
import com.unique.unexamine.authentication.base.entity.AuthenticationSession;
import com.unique.unexamine.system.base.entity.SystemMemberRole;
import com.unique.unexamine.system.base.entity.SystemRole;
import com.unique.unexamine.system.base.entity.SystemRolePermission;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.system.base.service.SystemDefinitionBaseService;
import com.unique.unexamine.authentication.base.service.AuthenticationSessionBaseService;
import com.unique.unexamine.system.base.service.SystemMemberRoleBaseService;
import com.unique.unexamine.system.base.service.SystemRoleBaseService;
import com.unique.unexamine.system.base.service.SystemRolePermissionBaseService;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SystemAdministrationService {
    private final SystemDefinitionBaseService systemService;
    private final AuthenticationSessionBaseService sessionService;
    private final SystemTenantBaseService tenantService;
    private final SystemTenantMemberBaseService tenantMemberService;
    private final SystemRoleBaseService roleService;
    private final SystemRolePermissionBaseService permissionService;
    private final SystemMemberRoleBaseService memberRoleService;
    private final AuditRecorder auditRecorder;

    public SystemAdministrationService(
            SystemDefinitionBaseService systemService,
            AuthenticationSessionBaseService sessionService,
            SystemTenantBaseService tenantService,
            SystemTenantMemberBaseService tenantMemberService,
            SystemRoleBaseService roleService,
            SystemRolePermissionBaseService permissionService,
            SystemMemberRoleBaseService memberRoleService,
            AuditRecorder auditRecorder) {
        this.systemService = systemService;
        this.sessionService = sessionService;
        this.tenantService = tenantService;
        this.tenantMemberService = tenantMemberService;
        this.roleService = roleService;
        this.permissionService = permissionService;
        this.memberRoleService = memberRoleService;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public SystemSettingsView settings(AuthenticatedContext context) {
        SystemDefinition system = requireSystem(context);
        return view(system);
    }

    @Transactional
    public SystemSettingsView updateSettings(
            AuthenticatedContext context,
            UpdateSystemSettingsRequest request,
            String traceId) {
        SystemDefinition system = requireSystem(context);
        String previousName = system.getName();
        String previousMode = system.getTenantMode();
        if (!previousMode.equals(request.tenantMode())) {
            throw new DomainException("TENANT_MODE_MIGRATION_REQUIRED",
                    "租户模式不能直接修改，请先执行发布检查、审批和后台迁移任务", HttpStatus.CONFLICT);
        }
        system.setName(request.name().strip());
        system.setTenantMode(request.tenantMode());
        if (systemService.updateById(system) != 1) {
            throw new DomainException("CONCURRENT_MODIFICATION", "系统设置已被其他操作修改，请刷新后重试", HttpStatus.CONFLICT);
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "SYSTEM_SETTINGS_UPDATED", "SYSTEM", context.systemId().toString(), "SUCCESS",
                Map.of("previousName", previousName, "name", system.getName(),
                        "previousTenantMode", previousMode, "tenantMode", system.getTenantMode()));
        return view(systemService.selectById(context.systemId()));
    }

    @Transactional(readOnly = true)
    public List<TenantView> tenants(AuthenticatedContext context) {
        requireSystem(context);
        return tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                        .eq(SystemTenant::getSystemId, context.systemId()))
                .stream()
                .sorted(Comparator.comparing(SystemTenant::getMain).reversed()
                        .thenComparing(SystemTenant::getId))
                .map(tenant -> view(tenant, context.tenantId()))
                .toList();
    }

    @Transactional
    public TenantView createTenant(AuthenticatedContext context, CreateTenantRequest request, String traceId) {
        SystemDefinition system = requireSystem(context);
        if (!"MULTI".equals(system.getTenantMode())) {
            throw new DomainException("MULTI_TENANT_NOT_ENABLED", "请先启用多租户模式", HttpStatus.CONFLICT);
        }
        String code = request.code().strip().toLowerCase(Locale.ROOT);
        SystemTenant tenant = new SystemTenant();
        tenant.setSystemId(context.systemId());
        tenant.setCode(code);
        tenant.setName(request.name().strip());
        tenant.setMain(false);
        tenant.setMainMarker(null);
        tenant.setCreatorAccountId(context.accountId());
        tenant.setStatus("ACTIVE");
        tenantService.insert(tenant);

        SystemTenantMember tenantMember = new SystemTenantMember();
        tenantMember.setSystemId(context.systemId());
        tenantMember.setTenantId(tenant.getId());
        tenantMember.setSystemMemberId(context.memberId());
        tenantMember.setTenantAdmin(true);
        tenantMember.setStatus("ACTIVE");
        tenantMemberService.insert(tenantMember);

        SystemRole role = new SystemRole();
        role.setSystemId(context.systemId());
        role.setTenantId(tenant.getId());
        role.setCode("TENANT_SUPER_ADMIN");
        role.setName("租户超级管理员");
        role.setStatus("ACTIVE");
        roleService.insert(role);

        SystemRolePermission permission = new SystemRolePermission();
        permission.setSystemId(context.systemId());
        permission.setTenantId(tenant.getId());
        permission.setRoleId(role.getId());
        permission.setResourceType("*");
        permission.setResourceCode("*");
        permission.setActionCode("*");
        permission.setDataScopeType("ALL");
        permissionService.insert(permission);

        SystemMemberRole memberRole = new SystemMemberRole();
        memberRole.setTenantId(tenant.getId());
        memberRole.setTenantMemberId(tenantMember.getId());
        memberRole.setRoleId(role.getId());
        memberRoleService.insert(memberRole);

        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "TENANT_CREATED", "TENANT", tenant.getId().toString(), "SUCCESS",
                Map.of("code", tenant.getCode(), "name", tenant.getName(), "creatorGrantedAdmin", true));
        return view(tenant, context.tenantId());
    }

    @Transactional
    public TenantView updateTenantStatus(
            AuthenticatedContext context,
            Long tenantId,
            UpdateTenantStatusRequest request,
            String traceId) {
        requireSystem(context);
        SystemTenant tenant = requireTenant(context.systemId(), tenantId);
        if (Boolean.TRUE.equals(tenant.getMain()) && "DISABLED".equals(request.status())) {
            throw new DomainException("MAIN_TENANT_PROTECTED", "默认主租户不能停用", HttpStatus.CONFLICT);
        }
        String previousStatus = tenant.getStatus();
        tenant.setStatus(request.status());
        if (tenantService.updateById(tenant) != 1) {
            throw new DomainException("CONCURRENT_MODIFICATION", "租户已被其他操作修改，请刷新后重试", HttpStatus.CONFLICT);
        }
        int revokedSessions = 0;
        if ("DISABLED".equals(tenant.getStatus())) {
            List<AuthenticationSession> sessions = sessionService.selectList(Wrappers.<AuthenticationSession>lambdaQuery()
                    .eq(AuthenticationSession::getSystemId, context.systemId())
                    .eq(AuthenticationSession::getTenantId, tenantId)
                    .eq(AuthenticationSession::getRevoked, false));
            for (AuthenticationSession session : sessions) {
                session.setRevoked(true);
                revokedSessions += sessionService.updateById(session);
            }
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "TENANT_STATUS_UPDATED", "TENANT", tenantId.toString(), "SUCCESS",
                Map.of("previousStatus", previousStatus, "status", tenant.getStatus(),
                        "revokedSessions", revokedSessions));
        return view(tenantService.selectById(tenantId), context.tenantId());
    }

    private SystemDefinition requireSystem(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统", HttpStatus.BAD_REQUEST);
        }
        SystemDefinition system = systemService.selectById(context.systemId());
        if (system == null || Boolean.TRUE.equals(system.getDeleted())) {
            throw new DomainException("SYSTEM_NOT_FOUND", "系统不存在", HttpStatus.NOT_FOUND);
        }
        return system;
    }

    private SystemTenant requireTenant(Long systemId, Long tenantId) {
        SystemTenant tenant = tenantService.selectById(tenantId);
        if (tenant == null || !systemId.equals(tenant.getSystemId())) {
            throw new DomainException("TENANT_NOT_FOUND", "租户不存在", HttpStatus.NOT_FOUND);
        }
        return tenant;
    }

    private SystemSettingsView view(SystemDefinition system) {
        return new SystemSettingsView(system.getId(), system.getCode(), system.getName(), system.getTenantMode(),
                system.getStatus(), system.getVersion());
    }

    private TenantView view(SystemTenant tenant, Long currentTenantId) {
        return new TenantView(tenant.getId(), tenant.getCode(), tenant.getName(), Boolean.TRUE.equals(tenant.getMain()),
                tenant.getStatus(), tenant.getId().equals(currentTenantId), tenant.getVersion());
    }
}
