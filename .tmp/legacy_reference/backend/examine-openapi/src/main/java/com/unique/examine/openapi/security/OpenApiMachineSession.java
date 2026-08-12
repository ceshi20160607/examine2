package com.unique.examine.openapi.security;

import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;

import java.util.Set;

public record OpenApiMachineSession(
        long sessionId,
        long accountId,
        Long systemId,
        Long tenantId,
        Long memberId,
        long permissionVersion,
        Set<String> permissions
) implements RequestSession {
    public OpenApiMachineSession {
        if (sessionId <= 0 || accountId <= 0 || systemId == null || systemId <= 0
                || tenantId == null || tenantId <= 0 || memberId == null || memberId <= 0
                || permissionVersion < 0) {
            throw new IllegalArgumentException("OpenAPI machine session is invalid");
        }
        permissions = Set.copyOf(permissions);
    }

    @Override
    public ContextType contextType() {
        return ContextType.SYSTEM;
    }
}
