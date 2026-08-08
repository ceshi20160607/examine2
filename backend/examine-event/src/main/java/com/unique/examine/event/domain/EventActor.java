package com.unique.examine.event.domain;

import java.util.Set;

public record EventActor(long systemId, long tenantId, long memberId, Set<String> permissions) {
    public EventActor {
        if (systemId <= 0 || tenantId <= 0 || memberId <= 0) {
            throw new IllegalArgumentException("Event actor scope is incomplete");
        }
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public boolean has(String permission) {
        return permissions.contains(permission);
    }
}
