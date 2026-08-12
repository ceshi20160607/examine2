package com.unique.examine.work.domain;

import java.util.Set;

public record WorkActor(long systemId, long tenantId, long memberId, Set<String> permissions) {
    public WorkActor {
        if (systemId <= 0 || tenantId <= 0 || memberId <= 0) {
            throw new IllegalArgumentException("Work actor scope is incomplete");
        }
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public boolean has(String permission) {
        return permissions.contains(permission);
    }
}
