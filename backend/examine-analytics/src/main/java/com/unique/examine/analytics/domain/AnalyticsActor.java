package com.unique.examine.analytics.domain;

import java.util.Set;

public record AnalyticsActor(
        long accountId,
        long systemId,
        long tenantId,
        long memberId,
        Set<String> permissions
) {
    public AnalyticsActor {
        if (accountId <= 0 || systemId <= 0 || tenantId <= 0 || memberId <= 0) {
            throw new IllegalArgumentException("Analytics actor ids must be positive");
        }
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public boolean has(String permission) {
        return permissions.contains(permission);
    }
}
