package com.unique.examine.flow.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Runtime snapshot for a route deadline. Absolute timestamps make the timing
 * contract restart-safe and independent from later definition changes.
 */
public record ApprovalDeadlineState(
        ApprovalDeadlinePolicy policy,
        Instant remindAt,
        Instant dueAt,
        Instant remindedAt,
        Instant processedAt
) {
    public ApprovalDeadlineState {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(dueAt, "dueAt");
        if (remindAt != null && !remindAt.isBefore(dueAt)) {
            throw new IllegalArgumentException("Reminder time must be before the due time");
        }
        if ((policy.remindBeforeMinutes() == null) != (remindAt == null)) {
            throw new IllegalArgumentException(
                    "Reminder timestamp must match the deadline policy"
            );
        }
        if (remindedAt != null && remindAt == null) {
            throw new IllegalArgumentException(
                    "A deadline without a reminder cannot be marked reminded"
            );
        }
        if (processedAt != null && processedAt.isBefore(dueAt)) {
            throw new IllegalArgumentException(
                    "A deadline cannot be processed before it is due"
            );
        }
    }

    public static ApprovalDeadlineState start(
            ApprovalDeadlinePolicy policy,
            Instant startedAt
    ) {
        if (policy == null) {
            return null;
        }
        Objects.requireNonNull(startedAt, "startedAt");
        var dueAt = startedAt.plus(Duration.ofMinutes(policy.timeoutMinutes()));
        var remindAt = policy.remindBeforeMinutes() == null
                ? null
                : dueAt.minus(Duration.ofMinutes(policy.remindBeforeMinutes()));
        return new ApprovalDeadlineState(policy, remindAt, dueAt, null, null);
    }

    public ApprovalDeadlineState markReminded(Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (remindAt == null) {
            throw new IllegalStateException("This approval deadline has no reminder");
        }
        if (remindedAt != null) {
            return this;
        }
        if (occurredAt.isBefore(remindAt)) {
            throw new IllegalArgumentException("Approval reminder is not due");
        }
        return new ApprovalDeadlineState(policy, remindAt, dueAt, occurredAt, processedAt);
    }

    public ApprovalDeadlineState markProcessed(Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (processedAt != null) {
            return this;
        }
        if (occurredAt.isBefore(dueAt)) {
            throw new IllegalArgumentException("Approval deadline is not due");
        }
        return new ApprovalDeadlineState(policy, remindAt, dueAt, remindedAt, occurredAt);
    }

    public boolean overdue(Instant now) {
        Objects.requireNonNull(now, "now");
        return !now.isBefore(dueAt);
    }
}
