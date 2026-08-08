package com.unique.examine.openapi.api;

import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Set;

public record OpenApiAdminSession(
        long accountId,
        long systemId,
        long tenantId,
        long memberId,
        Set<String> permissions
) {
    public static final String MANAGE_PERMISSION = "openapi.application.manage";

    public OpenApiAdminSession {
        permissions = Set.copyOf(permissions);
    }

    public static OpenApiAdminSession require(Object value, long requestedSystemId) {
        if (!(value instanceof RequestSession session)) {
            throw new BusinessException(
                    "AUTH_REQUIRED", "Authentication is required", HttpStatus.UNAUTHORIZED);
        }
        if (session.contextType() != ContextType.SYSTEM
                || session.systemId() == null
                || session.systemId() != requestedSystemId
                || session.tenantId() == null
                || session.memberId() == null) {
            throw new BusinessException(
                    "CONTEXT_SYSTEM_MISMATCH",
                    "The authenticated system context does not match the request",
                    HttpStatus.FORBIDDEN
            );
        }
        if (!session.permissions().contains(MANAGE_PERMISSION)) {
            throw new BusinessException(
                    "PERMISSION_DENIED",
                    "OpenAPI application management permission is required",
                    HttpStatus.FORBIDDEN
            );
        }
        return new OpenApiAdminSession(
                session.accountId(),
                requestedSystemId,
                session.tenantId(),
                session.memberId(),
                session.permissions()
        );
    }
}
