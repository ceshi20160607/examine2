package com.unique.examine.module.datasource.domain;

public record DataSourceActor(
        long systemId,
        long tenantId,
        long memberId
) {
    public DataSourceActor {
        if (systemId <= 0 || tenantId <= 0 || memberId <= 0) {
            throw new IllegalArgumentException(
                    "Data source actor scope values must be positive");
        }
    }
}
