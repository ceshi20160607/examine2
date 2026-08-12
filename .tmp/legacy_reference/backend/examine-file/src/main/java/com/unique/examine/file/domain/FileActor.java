package com.unique.examine.file.domain;

import java.util.Set;

public record FileActor(long systemId, long tenantId, long memberId, Set<String> permissions) {
    public FileActor {
        if (systemId <= 0 || tenantId <= 0 || memberId <= 0) {
            throw new IllegalArgumentException("File actor scope is incomplete");
        }
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public boolean has(String permission) {
        return permissions.contains(permission);
    }
}
