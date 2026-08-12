package com.unique.examine.module.runtime.flow;

import com.unique.examine.core.runtime.RuntimeRecordFlowFacade;

import java.time.Instant;

public final class RecordFlowViews {
    private RecordFlowViews() {
    }

    public record RecordFlowState(
            String instanceId,
            RuntimeRecordFlowFacade.FlowStatus status,
            long version,
            Instant updatedAt
    ) {
        static RecordFlowState from(RuntimeRecordFlowFacade.RecordFlowState state) {
            return new RecordFlowState(
                    Long.toString(state.instanceId()),
                    state.status(),
                    state.version(),
                    state.updatedAt());
        }
    }
}
