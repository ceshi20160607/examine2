package com.unique.examine.flow.security;

import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Set;

public record FlowSession(
        long accountId,
        long systemId,
        long tenantId,
        long memberId,
        long authorizationEpoch,
        Set<String> permissions
) {
    public FlowSession {
        if (accountId <= 0 || systemId <= 0 || tenantId <= 0 || memberId <= 0
                || authorizationEpoch <= 0) {
            throw new IllegalArgumentException("Flow session ids must be positive");
        }
        permissions = Set.copyOf(permissions);
    }

    public FlowSession(
            long accountId, long systemId, long tenantId, long memberId,
            Set<String> permissions) {
        this(accountId, systemId, tenantId, memberId, 1, permissions);
    }

    public FlowSession(long systemId, long tenantId, long memberId, Set<String> permissions) {
        this(memberId, systemId, tenantId, memberId, 1, permissions);
    }

    public static FlowSession require(Object value, long requestedSystemId, String permission) {
        return requireAny(value, requestedSystemId, permission);
    }

    public static FlowSession requireAny(
            Object value,
            long requestedSystemId,
            String... permissions
    ) {
        if (!(value instanceof RequestSession session)) {
            throw new BusinessException("AUTH_REQUIRED", "Authentication is required", HttpStatus.UNAUTHORIZED);
        }
        if (session.contextType() != ContextType.SYSTEM
                || session.systemId() == null
                || session.systemId() != requestedSystemId
                || session.tenantId() == null
                || session.memberId() == null) {
            throw new BusinessException(
                    "CONTEXT_SYSTEM_MISMATCH",
                    "The authenticated system member context does not match the request",
                    HttpStatus.FORBIDDEN
            );
        }
        if (permissions == null
                || permissions.length == 0
                || java.util.Arrays.stream(permissions)
                        .noneMatch(session.permissions()::contains)) {
            throw new BusinessException(
                    "PERMISSION_DENIED",
                    "The authenticated member does not have the required flow permission",
                    HttpStatus.FORBIDDEN
            );
        }
        return new FlowSession(
                session.accountId(),
                requestedSystemId,
                session.tenantId(),
                session.memberId(),
                Math.max(1, session.permissionVersion()),
                session.permissions()
        );
    }
}
