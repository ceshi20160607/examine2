package com.unique.examine.module.dashboard.domain;

public record DashboardActor(
        long systemId,
        long tenantId,
        long memberId
) {
    public DashboardActor {
        if (systemId <= 0 || tenantId <= 0 || memberId <= 0) {
            throw new IllegalArgumentException(
                    "Dashboard actor scope values must be positive");
        }
    }
}
