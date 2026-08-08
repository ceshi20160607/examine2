package com.unique.examine.flow.domain;

public record FlowTriggerDispatchInstance(
        long definitionId,
        int definitionVersion,
        long instanceId
) {
    public FlowTriggerDispatchInstance {
        if (definitionId <= 0 || definitionVersion <= 0 || instanceId <= 0) {
            throw new IllegalArgumentException("Triggered Flow dispatch instance is invalid");
        }
    }
}
