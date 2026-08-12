package com.unique.examine.event.api;

import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.event.domain.EventActor;
import org.springframework.http.HttpStatus;

final class EventRequestSession {
    static final String ACCESS = "event.message.access";

    private EventRequestSession() {
    }

    static EventActor require(Object value, long requestedSystemId) {
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
        if (session.permissions() == null || !session.permissions().contains(ACCESS)) {
            throw new BusinessException("PERMISSION_DENIED", "Message inbox access is denied", HttpStatus.FORBIDDEN);
        }
        return new EventActor(
                requestedSystemId,
                session.tenantId(),
                session.memberId(),
                session.permissions());
    }
}
