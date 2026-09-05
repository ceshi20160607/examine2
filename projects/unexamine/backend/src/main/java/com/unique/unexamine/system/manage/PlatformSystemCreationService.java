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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class PlatformSystemCreationService {
    private final SystemBootstrapService bootstrapService;
    private final PlatformMemberBaseService platformMemberService;
    private final PlatformMemberRoleBaseService platformMemberRoleService;
    private final PlatformRolePermissionBaseService platformPermissionService;
    private final AuditRecorder auditRecorder;

    public PlatformSystemCreationService(
            SystemBootstrapService bootstrapService,
            PlatformMemberBaseService platformMemberService,
            PlatformMemberRoleBaseService platformMemberRoleService,
            PlatformRolePermissionBaseService platformPermissionService,
            AuditRecorder auditRecorder) {
        this.bootstrapService = bootstrapService;
        this.platformMemberService = platformMemberService;
        this.platformMemberRoleService = platformMemberRoleService;
        this.platformPermissionService = platformPermissionService;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public AccessibleSystem create(AuthenticatedContext context, CreateSystemRequest input, String traceId) {
        requireCreatePermission(context);
        SystemBootstrapResult initialized = bootstrapService.bootstrap(
                context.platformId(), context.accountId(), context.displayName(), input.name(), input.code(),
                input.tenantMode());
        var system = initialized.system();
        var tenant = initialized.tenant();
        var member = initialized.member();

        auditRecorder.record(traceId, context.accountId(), system.getId(), tenant.getId(), member.getId(),
                "SYSTEM_CREATED", "SYSTEM", system.getId().toString(), "SUCCESS",
                Map.of("tenantMode", system.getTenantMode(), "mainTenantId", tenant.getId(),
                        "rootDepartmentId", initialized.rootDepartment().getId(), "creatorGrantedAdmin", true));
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
