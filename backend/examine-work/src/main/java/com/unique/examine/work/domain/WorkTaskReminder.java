package com.unique.examine.work.domain;

import java.time.Instant;
import java.util.Objects;

/** One immutable scheduled generation of a task reminder. */
public record WorkTaskReminder(
        long systemId,
        long tenantId,
        long taskId,
        int generation,
        Instant scheduledAt,
        Status status,
        int attemptCount,
        Lease lease,
        Instant createdAt,
        Instant updatedAt,
        Instant sentAt,
        Instant failedAt,
        Instant cancelledAt,
        String failureCode,
        String failureMessage,
        long version
) {
    public static final int MAX_FAILURE_MESSAGE_CHARACTERS = 500;

    public enum Status {
        PENDING,
        PROCESSING,
        SENT,
        CANCELLED,
        FAILED
    }

    public record Lease(String owner, String tokenHash, Instant expiresAt) {
        public Lease {
            if (owner == null || owner.isBlank() || owner.length() > 160) {
                throw new IllegalArgumentException("Lease owner must contain 1 to 160 characters");
            }
            owner = owner.trim();
            if (tokenHash == null || !tokenHash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Lease token hash must be lowercase SHA-256 hex");
            }
            Objects.requireNonNull(expiresAt, "Lease expiry is required");
        }
    }

    public WorkTaskReminder {
        if (systemId <= 0 || tenantId <= 0 || taskId <= 0 || generation <= 0) {
            throw new IllegalArgumentException("Reminder identity and scope values must be positive");
        }
        if (scheduledAt == null || status == null || createdAt == null || updatedAt == null
                || attemptCount < 0 || version <= 0 || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Reminder state is incomplete");
        }
        failureCode = normalizeFailureCode(failureCode);
        failureMessage = normalizeFailureMessage(failureMessage);
        switch (status) {
            case PENDING -> requireState(lease == null && sentAt == null && failedAt == null
                    && cancelledAt == null && failureCode == null && failureMessage == null);
            case PROCESSING -> requireState(lease != null && attemptCount > 0
                    && sentAt == null && failedAt == null && cancelledAt == null
                    && failureCode == null && failureMessage == null);
            case SENT -> requireState(lease == null && sentAt != null
                    && failedAt == null && cancelledAt == null
                    && failureCode == null && failureMessage == null);
            case CANCELLED -> requireState(lease == null && sentAt == null
                    && failedAt == null && cancelledAt != null
                    && failureCode == null && failureMessage == null);
            case FAILED -> requireState(lease == null && sentAt == null
                    && failedAt != null && cancelledAt == null
                    && failureCode != null && failureMessage != null);
        }
        if (sentAt != null && (sentAt.isBefore(createdAt) || sentAt.isAfter(updatedAt))
                || failedAt != null && (failedAt.isBefore(createdAt) || failedAt.isAfter(updatedAt))
                || cancelledAt != null
                && (cancelledAt.isBefore(createdAt) || cancelledAt.isAfter(updatedAt))) {
            throw new IllegalArgumentException("Reminder state timestamps are inconsistent");
        }
    }

    public static WorkTaskReminder pending(
            long systemId,
            long tenantId,
            long taskId,
            int generation,
            Instant scheduledAt,
            Instant now
    ) {
        Objects.requireNonNull(now, "Current time is required");
        if (scheduledAt == null || !scheduledAt.isAfter(now)) {
            throw invalid("WORK_TASK_REMINDER_INVALID",
                    "Task reminder must be in the future");
        }
        return new WorkTaskReminder(systemId, tenantId, taskId, generation,
                scheduledAt, Status.PENDING, 0, null, now, now,
                null, null, null, null, null, 1);
    }

    public boolean isDueAt(Instant now) {
        Objects.requireNonNull(now, "Current time is required");
        return status == Status.PENDING && !scheduledAt.isAfter(now)
                || status == Status.PROCESSING && !lease.expiresAt().isAfter(now);
    }

    public boolean isLive() {
        return status == Status.PENDING
                || status == Status.PROCESSING
                || status == Status.FAILED;
    }

    public WorkTaskReminder claim(
            String owner,
            String tokenHash,
            Instant leaseUntil,
            Instant now
    ) {
        if (status != Status.PENDING || scheduledAt.isAfter(now)) {
            throw invalid("WORK_TASK_REMINDER_NOT_CLAIMABLE",
                    "Reminder is not due and pending");
        }
        var nextLease = new Lease(owner, tokenHash, leaseUntil);
        if (!leaseUntil.isAfter(now)) {
            throw invalid("WORK_TASK_REMINDER_LEASE_INVALID",
                    "Reminder lease must expire in the future");
        }
        return copy(Status.PROCESSING, attemptCount + 1, nextLease, now,
                null, null, null, null, null);
    }

    public WorkTaskReminder renew(String tokenHash, Instant leaseUntil, Instant now) {
        requireLease(tokenHash, now, true);
        if (!leaseUntil.isAfter(now) || leaseUntil.isBefore(lease.expiresAt())) {
            throw invalid("WORK_TASK_REMINDER_LEASE_INVALID",
                    "Renewed lease must extend the active lease");
        }
        return copy(Status.PROCESSING, attemptCount,
                new Lease(lease.owner(), tokenHash, leaseUntil), now,
                null, null, null, null, null);
    }

    public WorkTaskReminder recoverExpiredLease(Instant now) {
        if (status != Status.PROCESSING || lease.expiresAt().isAfter(now)) {
            throw invalid("WORK_TASK_REMINDER_LEASE_ACTIVE",
                    "Reminder lease has not expired");
        }
        return copy(Status.PENDING, attemptCount, null, now,
                null, null, null, null, null);
    }

    public WorkTaskReminder sent(String tokenHash, Instant now) {
        requireLease(tokenHash, now, true);
        return copy(Status.SENT, attemptCount, null, now,
                now, null, null, null, null);
    }

    public WorkTaskReminder fail(
            String tokenHash,
            String nextFailureCode,
            String nextFailureMessage,
            Instant now
    ) {
        requireLease(tokenHash, now, true);
        return copy(Status.FAILED, attemptCount, null, now,
                null, now, null, nextFailureCode, nextFailureMessage);
    }

    public WorkTaskReminder cancel(Instant now) {
        if (status != Status.PENDING && status != Status.FAILED) {
            throw invalid("WORK_TASK_REMINDER_NOT_CANCELLABLE",
                    "Only pending or failed reminders can be cancelled");
        }
        return copy(Status.CANCELLED, attemptCount, null, now,
                null, null, now, null, null);
    }

    /** Cancels a live generation during atomic rescheduling or task invalidation. */
    public WorkTaskReminder cancelLive(Instant now) {
        if (!isLive()) {
            throw invalid("WORK_TASK_REMINDER_NOT_CANCELLABLE",
                    "Only a live reminder can be cancelled");
        }
        return copy(Status.CANCELLED, attemptCount, null, now,
                null, null, now, null, null);
    }

    public WorkTaskReminder retry(Instant now) {
        if (status != Status.FAILED) {
            throw invalid("WORK_TASK_REMINDER_STATE_INVALID",
                    "Only a failed reminder can be retried");
        }
        return copy(Status.PENDING, attemptCount, null, now,
                null, null, null, null, null);
    }

    public String deliveryKey() {
        return "work-task-reminder:" + systemId + ':' + tenantId + ':'
                + taskId + ':' + generation;
    }

    private WorkTaskReminder copy(
            Status nextStatus,
            int nextAttemptCount,
            Lease nextLease,
            Instant now,
            Instant nextSentAt,
            Instant nextFailedAt,
            Instant nextCancelledAt,
            String nextFailureCode,
            String nextFailureMessage
    ) {
        requireMonotonic(now);
        return new WorkTaskReminder(systemId, tenantId, taskId, generation,
                scheduledAt, nextStatus, nextAttemptCount, nextLease,
                createdAt, now, nextSentAt, nextFailedAt, nextCancelledAt,
                nextFailureCode, nextFailureMessage, version + 1);
    }

    private void requireLease(String tokenHash, Instant now, boolean requireUnexpired) {
        if (status != Status.PROCESSING || lease == null
                || !Objects.equals(lease.tokenHash(), tokenHash)) {
            throw invalid("WORK_TASK_REMINDER_LEASE_MISMATCH",
                    "Reminder lease ownership does not match");
        }
        if (requireUnexpired && !lease.expiresAt().isAfter(now)) {
            throw invalid("WORK_TASK_REMINDER_LEASE_EXPIRED",
                    "Reminder lease has expired");
        }
    }

    private void requireMonotonic(Instant now) {
        if (now == null || now.isBefore(updatedAt)) {
            throw invalid("WORK_TASK_REMINDER_TIME_INVALID",
                    "Reminder update time cannot move backwards");
        }
    }

    private static String normalizeFailureCode(String value) {
        if (value == null) {
            return null;
        }
        value = value.trim();
        if (!value.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("Reminder failure code is invalid");
        }
        return value;
    }

    private static String normalizeFailureMessage(String value) {
        if (value == null) {
            return null;
        }
        value = value.strip();
        if (value.isEmpty() || value.codePointCount(0, value.length())
                > MAX_FAILURE_MESSAGE_CHARACTERS) {
            throw new IllegalArgumentException("Reminder failure message is invalid");
        }
        return value;
    }

    private static void requireState(boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException("Reminder status facts are inconsistent");
        }
    }

    private static WorkDomainException invalid(String code, String message) {
        return new WorkDomainException(code, message);
    }
}
