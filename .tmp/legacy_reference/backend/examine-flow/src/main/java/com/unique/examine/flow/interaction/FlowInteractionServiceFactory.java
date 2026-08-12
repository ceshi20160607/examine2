package com.unique.examine.flow.interaction;

public interface FlowInteractionServiceFactory {
    FlowInteractionService forTenant(long systemId, long tenantId);
}
