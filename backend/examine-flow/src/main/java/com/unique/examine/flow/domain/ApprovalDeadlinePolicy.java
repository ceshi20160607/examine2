package com.unique.examine.flow.domain;

/**
 * Immutable timing policy for one approval route.
 */
public record ApprovalDeadlinePolicy(
        int timeoutMinutes,
        Integer remindBeforeMinutes,
        TimeoutAction timeoutAction
) {
    public static final int MAX_TIMEOUT_MINUTES = 525_600;

    public ApprovalDeadlinePolicy {
        if (timeoutMinutes < 1 || timeoutMinutes > MAX_TIMEOUT_MINUTES) {
            throw new IllegalArgumentException(
                    "Approval timeout must be between 1 and "
                            + MAX_TIMEOUT_MINUTES + " minutes"
            );
        }
        if (remindBeforeMinutes != null
                && (remindBeforeMinutes < 1 || remindBeforeMinutes >= timeoutMinutes)) {
            throw new IllegalArgumentException(
                    "Approval reminder must be positive and earlier than the timeout"
            );
        }
        if (timeoutAction == null) {
            throw new IllegalArgumentException("Approval timeout action is required");
        }
    }

    public enum TimeoutAction {
        NONE,
        AUTO_APPROVE,
        AUTO_REJECT
    }
}
