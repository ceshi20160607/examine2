package com.unique.examine.plat.manage.service;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.plat.api.AuthenticatedSession;
import org.springframework.http.HttpStatus;

public final class SessionGuard {
    private SessionGuard() {
    }

    public static AuthenticatedSession require(Object value) {
        if (value instanceof AuthenticatedSession session) {
            return session;
        }
        throw new BusinessException("AUTH_REQUIRED", "请先登录", HttpStatus.UNAUTHORIZED);
    }

    public static AuthenticatedSession requirePlatform(Object value, String permission) {
        var session = require(value);
        if (session.contextType() != ContextType.PLATFORM) {
            throw new BusinessException("CONTEXT_PLATFORM_REQUIRED", "请先返回平台上下文", HttpStatus.FORBIDDEN);
        }
        return requirePermission(session, permission);
    }

    public static AuthenticatedSession requireSystem(Object value, long systemId, String permission) {
        var session = require(value);
        if (session.contextType() != ContextType.SYSTEM
                || session.systemId() == null
                || session.systemId() != systemId) {
            throw new BusinessException("CONTEXT_SYSTEM_MISMATCH", "当前系统上下文与请求不一致", HttpStatus.FORBIDDEN);
        }
        return requirePermission(session, permission);
    }

    public static AuthenticatedSession requireTenant(
            Object value,
            long systemId,
            long tenantId,
            String permission
    ) {
        var session = requireSystem(value, systemId, permission);
        if (session.tenantId() == null || session.tenantId() != tenantId) {
            throw new BusinessException("CONTEXT_TENANT_MISMATCH", "当前租户上下文与请求不一致", HttpStatus.FORBIDDEN);
        }
        return session;
    }

    public static AuthenticatedSession requirePermission(AuthenticatedSession session, String permission) {
        if (!session.permissions().contains(permission)) {
            throw new BusinessException("PERMISSION_DENIED", "当前身份没有执行此操作的权限", HttpStatus.FORBIDDEN);
        }
        return session;
    }
}
