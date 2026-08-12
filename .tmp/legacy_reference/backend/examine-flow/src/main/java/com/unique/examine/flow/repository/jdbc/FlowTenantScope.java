package com.unique.examine.flow.repository.jdbc;

public record FlowTenantScope(long systemId, long tenantId) {
    public FlowTenantScope {
        if (systemId <= 0 || tenantId <= 0) {
            throw new IllegalArgumentException("Flow system and tenant ids must be positive");
        }
    }
}
