package com.unique.examine.flow.service;

import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.repository.jdbc.JdbcApprovalRepositoryFactory;

import java.time.Clock;
import java.util.Objects;

public final class JdbcFlowRequestServiceFactory implements FlowRequestServiceFactory {
    private final JdbcApprovalRepositoryFactory repositories;
    private final IdService ids;
    private final Clock clock;

    public JdbcFlowRequestServiceFactory(
            JdbcApprovalRepositoryFactory repositories,
            IdService ids,
            Clock clock
    ) {
        this.repositories = Objects.requireNonNull(repositories, "repositories");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ApprovalWorkflowService forTenant(long systemId, long tenantId) {
        return new ApprovalWorkflowService(
                repositories.forTenant(systemId, tenantId),
                ids,
                clock,
                systemId,
                tenantId
        );
    }
}
