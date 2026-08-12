package com.unique.examine.flow.service;

import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.domain.ApprovalInstance;

/** Port that starts an exact frozen child without record-flow projection. */
@FunctionalInterface
public interface FlowSubflowChildLauncher {
    ApprovalInstance launch(
            long systemId,
            long tenantId,
            ApprovalInstance parent,
            ApprovalCompletionStep.Subflow target,
            String launchKey
    );
}
