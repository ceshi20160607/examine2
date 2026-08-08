package com.unique.examine.todo.domain;

import java.util.Set;

public record TodoActor(
        long accountId,
        long systemId,
        long tenantId,
        long memberId,
        Set<String> permissions,
        String requestId,
        String traceId
) {
    public TodoActor {
        if (accountId <= 0 || systemId <= 0 || tenantId <= 0 || memberId <= 0) {
            throw new IllegalArgumentException("Todo actor scope values must be positive");
        }
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        requestId = normalize(requestId, "requestId");
        traceId = normalize(traceId, "traceId");
    }

    public TodoActor(long systemId, long tenantId, long memberId,
                     Set<String> permissions, String requestId, String traceId) {
        this(memberId, systemId, tenantId, memberId, permissions, requestId, traceId);
    }

    public boolean has(String permission) {
        return permissions.contains(permission);
    }

    private static String normalize(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(name + " must contain 1 to 128 characters");
        }
        return value.trim();
    }
}
