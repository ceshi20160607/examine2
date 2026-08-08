package com.unique.examine.module.kpi.domain;

public record KpiActor(long systemId, long tenantId, long memberId) {
    public KpiActor {
        if (systemId <= 0 || tenantId <= 0 || memberId <= 0) {
            throw new IllegalArgumentException(
                    "KPI actor scope values must be positive");
        }
    }
}
