package com.unique.unexamine.system.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.platform.base.entity.PlatformMember;
import com.unique.unexamine.platform.base.entity.PlatformMemberRole;
import com.unique.unexamine.platform.base.entity.PlatformRolePermission;
import com.unique.unexamine.platform.base.service.PlatformMemberBaseService;
import com.unique.unexamine.platform.base.service.PlatformMemberRoleBaseService;
import com.unique.unexamine.platform.base.service.PlatformRolePermissionBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemDefinition;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.entity.SystemMemberRole;
import com.unique.unexamine.system.base.entity.SystemRole;
import com.unique.unexamine.system.base.entity.SystemRolePermission;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.system.base.service.SystemDefinitionBaseService;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.system.base.service.SystemMemberRoleBaseService;
import com.unique.unexamine.system.base.service.SystemRoleBaseService;
import com.unique.unexamine.system.base.service.SystemRolePermissionBaseService;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PlatformSystemCreationService {
    private final SystemDefinitionBaseService systemService;
    private final SystemTenantBaseService tenantService;
    private final SystemMemberBaseService memberService;
    private final SystemTenantMemberBaseService tenantMemberService;
    private final SystemRoleBaseService roleService;
    private final SystemMemberRoleBaseService memberRoleService;
    private final SystemRolePermissionBaseService rolePermissionService;
    private final PlatformMemberBaseService platformMemberService;
    private final PlatformMemberRoleBaseService platformMemberRoleService;
    private final PlatformRolePermissionBaseService platformPermissionService;
    private final AuditRecorder auditRecorder;

    public PlatformSystemCreationService(
            SystemDefinitionBaseService systemService,
            SystemTenantBaseService tenantService,
            SystemMemberBaseService memberService,
            SystemTenantMemberBaseService tenantMemberService,
            SystemRoleBaseService roleService,
            SystemMemberRoleBaseService memberRoleService,
            SystemRolePermissionBaseService rolePermissionService,
            PlatformMemberBaseService platformMemberService,
            PlatformMemberRoleBaseService platformMemberRoleService,
            PlatformRolePermissionBaseService platformPermissionService,
            AuditRecorder auditRecorder) {
        this.systemService = systemService;
        this.tenantService = tenantService;
        this.memberService = memberService;
        this.tenantMemberService = tenantMemberService;
        this.roleService = roleService;
        this.memberRoleService = memberRoleService;
        this.rolePermissionService = rolePermissionService;
        this.platformMemberService = platformMemberService;
        this.platformMemberRoleService = platformMemberRoleService;
        this.platformPermissionService = platformPermissionService;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public AccessibleSystem create(AuthenticatedContext context, CreateSystemRequest input, String traceId) {
        requireCreatePermission(context);
        String code = input.code().strip().toLowerCase(Locale.ROOT);
        if (!systemService.selectList(Wrappers.<SystemDefinition>lambdaQuery()
                .eq(SystemDefinition::getPlatformId, context.platformId())
                .eq(SystemDefinition::getCode, code)).isEmpty()) {
            throw new DomainException("SYSTEM_CODE_EXISTS", "系统编码已经存在", HttpStatus.CONFLICT);
        }

        SystemDefinition system = new SystemDefinition();
        system.setPlatformId(context.platformId());
        system.setCode(code);
        system.setName(input.name().strip());
        system.setCreatorAccountId(context.accountId());
        system.setTenantMode(input.tenantMode());
        system.setStatus("ACTIVE");
        system.setDeleted(false);
        systemService.insert(system);

        SystemTenant tenant = new SystemTenant();
        tenant.setSystemId(system.getId());
        tenant.setCode("main");
        tenant.setName("默认主租户");
        tenant.setMain(true);
        tenant.setMainMarker("MAIN");
        tenant.setCreatorAccountId(context.accountId());
        tenant.setStatus("ACTIVE");
        tenantService.insert(tenant);

        SystemMember member = new SystemMember();
        member.setSystemId(system.getId());
        member.setAccountId(context.accountId());
        member.setDisplayName(context.displayName());
        member.setStatus("ACTIVE");
        memberService.insert(member);

        SystemTenantMember tenantMember = new SystemTenantMember();
        tenantMember.setSystemId(system.getId());
        tenantMember.setTenantId(tenant.getId());
        tenantMember.setSystemMemberId(member.getId());
        tenantMember.setTenantAdmin(true);
        tenantMember.setStatus("ACTIVE");
        tenantMemberService.insert(tenantMember);

        SystemRole role = new SystemRole();
        role.setSystemId(system.getId());
        role.setTenantId(tenant.getId());
        role.setCode("SYSTEM_SUPER_ADMIN");
        role.setName("系统超级管理员");
        role.setBuiltIn(true);
        role.setStatus("ACTIVE");
        roleService.insert(role);

        SystemRolePermission permission = new SystemRolePermission();
        permission.setSystemId(system.getId());
        permission.setTenantId(tenant.getId());
        permission.setRoleId(role.getId());
        permission.setResourceType("*");
        permission.setResourceCode("*");
        permission.setActionCode("*");
        permission.setDataScopeType("ALL");
        rolePermissionService.insert(permission);

        SystemMemberRole assignment = new SystemMemberRole();
        assignment.setTenantId(tenant.getId());
        assignment.setTenantMemberId(tenantMember.getId());
        assignment.setRoleId(role.getId());
        memberRoleService.insert(assignment);

        auditRecorder.record(traceId, context.accountId(), system.getId(), tenant.getId(), member.getId(),
                "SYSTEM_CREATED", "SYSTEM", system.getId().toString(), "SUCCESS",
                Map.of("tenantMode", system.getTenantMode(), "mainTenantId", tenant.getId(), "creatorGrantedAdmin", true));
        return new AccessibleSystem(system.getId(), system.getCode(), system.getName(), system.getTenantMode(),
                system.getStatus(), tenant.getId(), tenant.getName());
    }

    private void requireCreatePermission(AuthenticatedContext context) {
        PlatformMember platformMember = platformMemberService.selectList(Wrappers.<PlatformMember>lambdaQuery()
                        .eq(PlatformMember::getPlatformId, context.platformId())
                        .eq(PlatformMember::getAccountId, context.accountId())
                        .eq(PlatformMember::getStatus, "ACTIVE"))
                .stream().findFirst().orElse(null);
        if (platformMember == null) {
            throw new DomainException("PLATFORM_PERMISSION_DENIED", "没有创建系统的权限", HttpStatus.FORBIDDEN);
        }
        List<Long> roleIds = platformMemberRoleService.selectList(Wrappers.<PlatformMemberRole>lambdaQuery()
                        .eq(PlatformMemberRole::getPlatformId, context.platformId())
                        .eq(PlatformMemberRole::getMemberId, platformMember.getId()))
                .stream().map(PlatformMemberRole::getRoleId).toList();
        boolean allowed = !roleIds.isEmpty() && platformPermissionService.selectList(
                        Wrappers.<PlatformRolePermission>lambdaQuery()
                                .eq(PlatformRolePermission::getPlatformId, context.platformId())
                                .in(PlatformRolePermission::getRoleId, roleIds))
                .stream().anyMatch(permission -> !"DENY".equals(permission.getEffect())
                        && matches(permission.getResourceType(), "PLATFORM")
                        && matches(permission.getResourceCode(), "SYSTEM")
                        && matches(permission.getActionCode(), "CREATE"));
        if (!allowed) {
            throw new DomainException("PLATFORM_PERMISSION_DENIED", "没有创建系统的权限", HttpStatus.FORBIDDEN);
        }
    }

    private boolean matches(String granted, String required) {
        return "*".equals(granted) || required.equals(granted);
    }
}
