package com.unique.examine.event.api;

import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

record EventDeliveryAdminSession(long systemId, long tenantId, long memberId) {
    static EventDeliveryAdminSession require(Object value, long requestedSystemId) {
        if (!(value instanceof RequestSession session)) {
            throw new BusinessException("AUTH_REQUIRED", "Authentication is required", HttpStatus.UNAUTHORIZED);
        }
        if (session.contextType() != ContextType.SYSTEM || session.systemId() == null
                || session.systemId() != requestedSystemId || session.tenantId() == null
                || session.memberId() == null) {
            throw new BusinessException("CONTEXT_SYSTEM_MISMATCH",
                    "The current system member context does not match this request", HttpStatus.FORBIDDEN);
        }
        if (session.permissions() == null
                || !session.permissions().contains(MessageTemplateAdminSession.MANAGE)) {
            throw new BusinessException("PERMISSION_DENIED",
                    "Event delivery management permission is required", HttpStatus.FORBIDDEN);
        }
        return new EventDeliveryAdminSession(requestedSystemId, session.tenantId(), session.memberId());
    }
}
