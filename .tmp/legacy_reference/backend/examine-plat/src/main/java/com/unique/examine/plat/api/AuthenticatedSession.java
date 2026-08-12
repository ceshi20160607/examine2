package com.unique.examine.plat.api;

import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;

import java.util.Set;

public record AuthenticatedSession(
        long sessionId,
        long accountId,
        ContextType contextType,
        Long systemId,
        Long tenantId,
        Long memberId,
        long permissionVersion,
        Set<String> permissions
) implements RequestSession {
    public static final String REQUEST_ATTRIBUTE = RequestSession.REQUEST_ATTRIBUTE;

    public AuthenticatedSession {
        permissions = Set.copyOf(permissions);
    }
}
