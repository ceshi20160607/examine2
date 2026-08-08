package com.unique.examine.flow.service;

import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.domain.ApprovalInstance;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public final class DefaultFlowSubflowChildLauncher
        implements FlowSubflowChildLauncher {
    private final FlowMutationService mutations;

    public DefaultFlowSubflowChildLauncher(FlowMutationService mutations) {
        this.mutations = Objects.requireNonNull(mutations, "mutations");
    }

    @Override
    public ApprovalInstance launch(
            long systemId,
            long tenantId,
            ApprovalInstance parent,
            ApprovalCompletionStep.Subflow target,
            String launchKey
    ) {
        return mutations.startSubflow(
                systemId, tenantId, parent, target, launchKey);
    }
}
