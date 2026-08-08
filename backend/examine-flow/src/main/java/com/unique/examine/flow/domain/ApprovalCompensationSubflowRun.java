package com.unique.examine.flow.domain;

import java.util.Objects;

/** Independent subflow linkage for a compensation execution. */
public record ApprovalCompensationSubflowRun(
        long compensationId,
        ApprovalSubflowRun run
) {
    public ApprovalCompensationSubflowRun {
        Objects.requireNonNull(run, "run");
        if (compensationId <= 0 || run.executionId() != compensationId) {
            throw new ApprovalDomainException(
                    ApprovalDomainException.Code.SUBFLOW_RUN_CONFLICT,
                    "Compensation subflow run does not match its execution");
        }
    }
}
