package com.unique.unexamine.system.manage;

import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PlatformPermissionResolver;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** 平台用户创建系统，创建后由当前所属人隐式管理系统。 */
@Service
public class PlatformSystemCreationService {
    private final SystemBootstrapService bootstrapService;
    private final PlatformPermissionResolver permissionResolver;
    private final AuditRecorder auditRecorder;

    public PlatformSystemCreationService(
            SystemBootstrapService bootstrapService,
            PlatformPermissionResolver permissionResolver,
            AuditRecorder auditRecorder) {
        this.bootstrapService = bootstrapService;
        this.permissionResolver = permissionResolver;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public AccessibleSystem create(AuthenticatedContext context, CreateSystemRequest input, String traceId) {
        requireCreatePermission(context);
        SystemBootstrapResult initialized = bootstrapService.bootstrap(
                context.platformId(), context.accountId(), context.displayName(), input.name(), input.code());
        var system = initialized.system();
        var tenant = initialized.tenant();
        var member = initialized.member();

        auditRecorder.record(traceId, context.accountId(), system.getId(), tenant.getId(), member.getId(),
                "SYSTEM_CREATED", "SYSTEM", system.getId().toString(), "SUCCESS",
                Map.of("tenantMode", system.getTenantMode(),
                        "mainTenantId", tenant.getId(),
                        "rootDepartmentId", initialized.rootDepartment().getId(), "ownerImplicitAdmin", true));
        return new AccessibleSystem(system.getId(), system.getCode(), system.getName(), system.getTenantMode(),
                system.getStatus(), tenant.getId(), tenant.getName());
    }

    /** 每次创建都按当前平台成员状态重算授权，不信任旧会话里的角色快照。 */
    private void requireCreatePermission(AuthenticatedContext context) {
        boolean allowed = context.systemId() == null
                && permissionResolver.resolve(context.platformId(), context.accountId()).permissions().stream()
                .anyMatch(permission -> matches(permission.resourceType(), "PLATFORM")
                        && matches(permission.resourceCode(), "SYSTEM")
                        && matches(permission.actionCode(), "CREATE"));
        if (!allowed) {
            throw new DomainException("PLATFORM_PERMISSION_DENIED", "没有创建系统的权限", HttpStatus.FORBIDDEN);
        }
    }

    private boolean matches(String granted, String required) {
        return "*".equals(granted) || required.equals(granted);
    }
}
