package com.unique.examine.flow.repository.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Objects;

public final class JdbcApprovalRepositoryFactory {
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;

    public JdbcApprovalRepositoryFactory(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
    }

    public JdbcApprovalRepository forTenant(long systemId, long tenantId) {
        return new JdbcApprovalRepository(jdbc, transactionManager, new FlowTenantScope(systemId, tenantId));
    }
}
