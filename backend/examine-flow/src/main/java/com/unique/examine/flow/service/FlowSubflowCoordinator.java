package com.unique.examine.flow.service;

/** Transaction boundary used by the polling worker. */
public interface FlowSubflowCoordinator {
    boolean launch(long systemId, long tenantId, long executionId);

    boolean reconcile(
            long systemId,
            long tenantId,
            long executionId,
            int attempt
    );
}
