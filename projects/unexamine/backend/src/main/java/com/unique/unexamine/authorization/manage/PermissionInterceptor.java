package com.unique.unexamine.authorization.manage;

import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Component
public class PermissionInterceptor implements HandlerInterceptor {
    private final PermissionChecker checker;
    private final AuditRecorder auditRecorder;

    public PermissionInterceptor(PermissionChecker checker, AuditRecorder auditRecorder) {
        this.checker = checker;
        this.auditRecorder = auditRecorder;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequirePermission required = method.getMethodAnnotation(RequirePermission.class);
        if (required == null) {
            required = method.getBeanType().getAnnotation(RequirePermission.class);
        }
        if (required == null) {
            return true;
        }
        AuthenticatedContext context = AuthenticationContextHolder.require();
        if (!checker.allows(context, required.resourceType(), required.resourceCode(), required.actionCode())) {
            String permissionCode = required.resourceType() + ":" + required.resourceCode() + ":" + required.actionCode();
            auditRecorder.recordPermissionDenied(
                    TraceIdFilter.current(request),
                    context.accountId(),
                    context.systemId(),
                    context.tenantId(),
                    context.memberId(),
                    permissionCode,
                    Map.of("roleIds", context.roleIds(), "permissions", context.permissions(), "dataScopes", context.dataScopes()));
            throw new DomainException("PERMISSION_DENIED", "没有执行该操作的权限", HttpStatus.FORBIDDEN);
        }
        return true;
    }
}
