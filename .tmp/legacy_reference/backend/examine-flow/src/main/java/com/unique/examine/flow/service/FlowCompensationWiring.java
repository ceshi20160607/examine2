package com.unique.examine.flow.service;

import org.springframework.stereotype.Component;

/** Explicit acyclic wiring from forward executors to the failure coordinator. */
@Component
final class FlowCompensationWiring {
    FlowCompensationWiring(
            FlowCompensationCoordinator coordinator,
            FlowCompletionExecutionService externalTasks,
            FlowWebhookWorker webhooks,
            FlowSubflowRuntimeService subflows
    ) {
        externalTasks.configureCompensations(coordinator);
        webhooks.configureCompensations(coordinator);
        subflows.configureCompensations(coordinator);
    }
}
