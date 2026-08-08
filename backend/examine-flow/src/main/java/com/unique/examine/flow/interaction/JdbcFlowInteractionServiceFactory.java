package com.unique.examine.flow.interaction;

import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.interaction.jdbc.JdbcFlowInteractionRepositoryFactory;
import com.unique.examine.flow.service.FlowRequestServiceFactory;

import java.time.Clock;
import java.util.Objects;

public final class JdbcFlowInteractionServiceFactory implements FlowInteractionServiceFactory {
    private final FlowRequestServiceFactory workflows;
    private final JdbcFlowInteractionRepositoryFactory repositories;
    private final IdService ids;
    private final Clock clock;

    public JdbcFlowInteractionServiceFactory(
            FlowRequestServiceFactory workflows,
            JdbcFlowInteractionRepositoryFactory repositories,
            IdService ids,
            Clock clock
    ) {
        this.workflows = Objects.requireNonNull(workflows, "workflows");
        this.repositories = Objects.requireNonNull(repositories, "repositories");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public FlowInteractionService forTenant(long systemId, long tenantId) {
        return new FlowInteractionService(
                workflows.forTenant(systemId, tenantId),
                repositories.forTenant(systemId, tenantId),
                ids,
                clock
        );
    }
}
