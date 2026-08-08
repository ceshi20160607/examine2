package com.unique.examine.core.context;

import java.util.Set;

public record RequestContext(
        long sessionId,
        long accountId,
        ContextType type,
        Long systemId,
        Long tenantId,
        Long memberId,
        long permissionVersion,
        Set<String> permissions
) {
    public RequestContext {
        permissions = Set.copyOf(permissions);
    }
}
