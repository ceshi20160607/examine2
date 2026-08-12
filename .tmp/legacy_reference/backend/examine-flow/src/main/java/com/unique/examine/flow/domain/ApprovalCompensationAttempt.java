package com.unique.examine.flow.domain;

import java.util.Objects;

/** Append-only attempt fact in the compensation idempotency namespace. */
public record ApprovalCompensationAttempt(
        long compensationId,
        ApprovalCompletionAttempt fact
) {
    public ApprovalCompensationAttempt {
        Objects.requireNonNull(fact, "fact");
        if (compensationId <= 0 || fact.executionId() != compensationId) {
            throw new ApprovalDomainException(
                    ApprovalDomainException.Code.COMPLETION_EXECUTION_INVALID,
                    "Compensation attempt does not match its execution");
        }
    }
}
