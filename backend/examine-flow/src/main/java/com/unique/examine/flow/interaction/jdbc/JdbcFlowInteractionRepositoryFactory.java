package com.unique.examine.flow.interaction.jdbc;

import com.unique.examine.flow.repository.jdbc.FlowTenantScope;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Objects;

public final class JdbcFlowInteractionRepositoryFactory {
    private final JdbcTemplate jdbc;

    public JdbcFlowInteractionRepositoryFactory(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    public JdbcFlowInteractionRepository forTenant(long systemId, long tenantId) {
        return new JdbcFlowInteractionRepository(jdbc, new FlowTenantScope(systemId, tenantId));
    }
}
