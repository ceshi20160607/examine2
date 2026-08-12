package com.unique.examine.work.api;

import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.work.domain.WorkActor;
import org.springframework.http.HttpStatus;

final class WorkRequestSession {
    static final String ACCESS = "work.task.access";

    private WorkRequestSession() {
    }

    static WorkActor require(Object value, long requestedSystemId) {
        return require(value, requestedSystemId, ACCESS);
    }

    static WorkActor require(
            Object value,
            long requestedSystemId,
            String accessPermission
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
                    "The current system member context does not match this request",
                    HttpStatus.FORBIDDEN);
        }
        if (accessPermission == null || accessPermission.isBlank()
                || session.permissions() == null
                || !session.permissions().contains(accessPermission)) {
            throw new BusinessException(
                    "PERMISSION_DENIED",
                    "Work access is denied: " + accessPermission,
                    HttpStatus.FORBIDDEN);
        }
        return new WorkActor(
                requestedSystemId,
                session.tenantId(),
                session.memberId(),
                session.permissions());
    }
}
