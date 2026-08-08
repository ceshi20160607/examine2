package com.unique.examine.flow.domain;

import java.util.List;

/** Explicit forward-plan terminal failure behavior. */
public enum CompletionFailurePolicy {
    MANUAL_RETRY,
    COMPENSATE;

    public static CompletionFailurePolicy require(
            CompletionFailurePolicy value,
            List<ApprovalCompletionStep> steps
    ) {
        var policy = value == null ? MANUAL_RETRY : value;
        var snapshot = steps == null ? List.<ApprovalCompletionStep>of() : steps;
        if (policy == COMPENSATE
                && snapshot.stream().anyMatch(step ->
                step.compensation() == null)) {
            throw invalid("COMPENSATE requires compensation on every completion step");
        }
        if (policy == MANUAL_RETRY
                && snapshot.stream().anyMatch(step ->
                step.compensation() != null)) {
            throw invalid("MANUAL_RETRY cannot carry dormant compensation");
        }
        return policy;
    }

    private static ApprovalDomainException invalid(String message) {
        return new ApprovalDomainException(
                ApprovalDomainException.Code.COMPLETION_STEP_INVALID, message);
    }
}
