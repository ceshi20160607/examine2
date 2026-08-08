package com.unique.examine.flow.domain;

import java.time.Instant;
import java.util.Objects;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.COMPLETION_LEASE_INVALID;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.COMPLETION_STATE_CONFLICT;

/**
 * Immutable runtime snapshot and monotonic state machine for one ordered
 * completion step.
 */
public record ApprovalCompletionExecution(
        long id,
        long instanceId,
        long definitionId,
        int definitionVersion,
        int ordinal,
        ApprovalCompletionStep step,
        String payloadJson,
        Status status,
        int attemptCount,
        int stateVersion,
        Instant availableAt,
        Lease lease,
        Instant createdAt,
        Instant startedAt,
        Instant terminalAt,
        String resultJson,
        Failure failure
) {
    public static final int MAX_PAYLOAD_BYTES = 65_535;

    public ApprovalCompletionExecution {
        if (id <= 0 || instanceId <= 0 || definitionId <= 0
                || definitionVersion < 1 || ordinal < 0
                || ordinal >= ApprovalCompletionStep.MAX_STEPS
                || attemptCount < 0 || stateVersion < 0) {
            throw invalid("Completion execution identity is invalid");
        }
        Objects.requireNonNull(step, "step");
        payloadJson = ApprovalCompletionAttempt.canonicalObject(
                payloadJson, MAX_PAYLOAD_BYTES);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (startedAt != null && startedAt.isBefore(createdAt)
                || terminalAt != null && terminalAt.isBefore(createdAt)) {
            throw invalid("Completion execution timestamps are invalid");
        }
        if (status == Status.WAITING) {
            require(availableAt == null && lease == null
                    && startedAt == null && terminalAt == null
                    && resultJson == null && failure == null,
                    "Waiting completion execution contains active state");
        } else if (status == Status.AVAILABLE
                || status == Status.RETRYING) {
            require(availableAt != null && lease == null
                    && terminalAt == null && resultJson == null,
                    "Available completion execution state is invalid");
        } else if (status == Status.LEASED) {
            require(availableAt != null && lease != null
                    && startedAt != null && terminalAt == null
                    && resultJson == null && failure == null
                    && attemptCount > 0,
                    "Leased completion execution state is invalid");
        } else if (status == Status.RUNNING) {
            require(step.type() == ApprovalCompletionStep.Type.SUBFLOW
                    && availableAt != null && lease == null
                    && startedAt != null && terminalAt == null
                    && resultJson == null && failure == null
                    && attemptCount > 0,
                    "Running subflow completion execution is invalid");
        } else if (status == Status.SUCCEEDED) {
            require(lease == null && terminalAt != null
                    && resultJson != null && failure == null,
                    "Succeeded completion execution is incomplete");
        } else if (status == Status.FAILED) {
            require(lease == null && terminalAt != null
                    && resultJson == null && failure != null
                    && attemptCount > 0,
                    "Failed completion execution is incomplete");
        } else {
            require(lease == null && terminalAt != null
                    && resultJson == null,
                    "Cancelled completion execution is incomplete");
        }
        resultJson = ApprovalCompletionAttempt.canonicalObject(
                resultJson, resultLimit(step));
    }

    public static ApprovalCompletionExecution materialize(
            long id,
            long instanceId,
            long definitionId,
            int definitionVersion,
            int ordinal,
            ApprovalCompletionStep step,
            String payloadJson,
            Instant createdAt
    ) {
        return materialize(
                id, instanceId, definitionId, definitionVersion, ordinal,
                step, payloadJson, createdAt, ordinal == 0);
    }

    public static ApprovalCompletionExecution materialize(
            long id,
            long instanceId,
            long definitionId,
            int definitionVersion,
            int ordinal,
            ApprovalCompletionStep step,
            String payloadJson,
            Instant createdAt,
            boolean initiallyActive
    ) {
        return new ApprovalCompletionExecution(
                id, instanceId, definitionId, definitionVersion, ordinal,
                step, payloadJson,
                initiallyActive ? Status.AVAILABLE : Status.WAITING,
                0, 0, initiallyActive ? createdAt : null, null,
                createdAt, null, null, null, null);
    }

    public boolean isDueAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return ((status == Status.AVAILABLE || status == Status.RETRYING)
                && !availableAt.isAfter(now))
                || (status == Status.LEASED
                && !lease.expiresAt().isAfter(now));
    }

    public ApprovalCompletionExecution activate(Instant now) {
        requireStatus(Status.WAITING, "Only a waiting completion step activates");
        return copy(
                Status.AVAILABLE, attemptCount, now, null,
                startedAt, null, null, null);
    }

    ApprovalCompletionExecution initiallyActivate(Instant now) {
        Objects.requireNonNull(now, "now");
        requireStatus(Status.WAITING,
                "Only an initially waiting completion step activates");
        if (stateVersion != 0 || attemptCount != 0) {
            throw conflict("Only an unpersisted completion step activates initially");
        }
        return new ApprovalCompletionExecution(
                id, instanceId, definitionId, definitionVersion, ordinal,
                step, payloadJson, Status.AVAILABLE, 0, 0, now, null,
                createdAt, null, null, null, null);
    }

    public ApprovalCompletionExecution recoverExpiredLease(Instant now) {
        Objects.requireNonNull(now, "now");
        requireStatus(Status.LEASED, "Only a leased completion step expires");
        if (lease.expiresAt().isAfter(now)) {
            throw conflict("Completion lease has not expired");
        }
        if (attemptCount >= step.maxAttempts()) {
            return copy(
                    Status.FAILED, attemptCount, availableAt, null, startedAt,
                    now, null,
                    new Failure(
                            "LEASE_ATTEMPTS_EXHAUSTED",
                            "Completion lease expired after maximum attempts",
                            false));
        }
        return copy(
                Status.AVAILABLE, attemptCount, now, null,
                startedAt, null, null, null);
    }

    public ApprovalCompletionExecution claim(
            String leaseOwner,
            String leaseTokenHash,
            Instant leaseUntil,
            Instant now
    ) {
        Objects.requireNonNull(now, "now");
        if (step.type() == ApprovalCompletionStep.Type.SUBFLOW) {
            throw conflict("Subflow completion execution cannot be leased");
        }
        if (status != Status.AVAILABLE && status != Status.RETRYING) {
            throw conflict("Completion execution is not available");
        }
        if (availableAt.isAfter(now)) {
            throw conflict("Completion execution is not due");
        }
        var nextLease = new Lease(
                leaseOwner, leaseTokenHash, leaseUntil);
        if (!leaseUntil.isAfter(now)) {
            throw leaseInvalid("Completion lease expiry must be in the future");
        }
        return copy(
                Status.LEASED, attemptCount + 1, availableAt, nextLease,
                startedAt == null ? now : startedAt,
                null, null, null);
    }

    /** Starts one deterministic child attempt. Launch idempotency is durable in
     * {@link ApprovalSubflowRun}; this transition only advances the execution. */
    public ApprovalCompletionExecution startSubflow(Instant now) {
        Objects.requireNonNull(now, "now");
        if (step.type() != ApprovalCompletionStep.Type.SUBFLOW) {
            throw conflict("Only a subflow completion step may start a child");
        }
        if (status != Status.AVAILABLE && status != Status.RETRYING) {
            throw conflict("Subflow completion execution is not available");
        }
        if (availableAt.isAfter(now)) {
            throw conflict("Subflow completion execution is not due");
        }
        return copy(
                Status.RUNNING, attemptCount + 1, availableAt, null,
                startedAt == null ? now : startedAt,
                null, null, null);
    }

    public ApprovalCompletionExecution completeSubflow(
            String resultJson,
            Instant now
    ) {
        Objects.requireNonNull(now, "now");
        requireSubflowRunning();
        var canonical = ApprovalCompletionAttempt.canonicalObject(
                resultJson == null ? "{}" : resultJson,
                resultLimit(step));
        return copy(
                Status.SUCCEEDED, attemptCount, availableAt, null,
                startedAt, now, canonical, null);
    }

    public ApprovalCompletionExecution failSubflow(
            String failureCode,
            String failureMessage,
            Instant now
    ) {
        Objects.requireNonNull(now, "now");
        requireSubflowRunning();
        return copy(
                Status.FAILED, attemptCount, availableAt, null,
                startedAt, now, null,
                new Failure(failureCode, failureMessage, false));
    }

    public ApprovalCompletionExecution heartbeat(
            String leaseTokenHash,
            Instant leaseUntil,
            Instant now
    ) {
        requireLease(leaseTokenHash, now);
        if (!leaseUntil.isAfter(now)) {
            throw leaseInvalid("Completion heartbeat must extend into the future");
        }
        return copy(
                Status.LEASED, attemptCount, availableAt,
                new Lease(lease.owner(), leaseTokenHash, leaseUntil),
                startedAt, null, null, null);
    }

    public ApprovalCompletionExecution complete(
            String leaseTokenHash,
            String resultJson,
            Instant now
    ) {
        requireLease(leaseTokenHash, now);
        var canonical = ApprovalCompletionAttempt.canonicalObject(
                resultJson == null ? "{}" : resultJson,
                resultLimit(step));
        return copy(
                Status.SUCCEEDED, attemptCount, availableAt, null,
                startedAt, now, canonical, null);
    }

    public ApprovalCompletionExecution fail(
            String leaseTokenHash,
            String failureCode,
            String failureMessage,
            boolean retryable,
            Instant now
    ) {
        requireLease(leaseTokenHash, now);
        var nextFailure = new Failure(
                failureCode, failureMessage, retryable);
        if (retryable && attemptCount < step.maxAttempts()) {
            return copy(
                    Status.RETRYING, attemptCount, retryAt(now), null,
                    startedAt, null, null, nextFailure);
        }
        return copy(
                Status.FAILED, attemptCount, availableAt, null,
                startedAt, now, null, nextFailure);
    }

    public ApprovalCompletionExecution retry(Instant now) {
        requireStatus(Status.FAILED, "Only a failed completion step may retry");
        return copy(
                Status.AVAILABLE, attemptCount, now, null,
                startedAt, null, null, null);
    }

    public ApprovalCompletionExecution cancel(Instant now) {
        Objects.requireNonNull(now, "now");
        if (status == Status.SUCCEEDED || status == Status.CANCELLED) {
            throw conflict(
                    "Completed or cancelled execution cannot be cancelled");
        }
        return copy(
                Status.CANCELLED, attemptCount, availableAt, null,
                startedAt, now, null, failure);
    }

    private Instant retryAt(Instant now) {
        return switch (step.type()) {
            case EXTERNAL_TASK, SUBFLOW -> now;
            case WEBHOOK -> {
                var exponent = Math.max(0, attemptCount - 1);
                var seconds = Math.min(
                        3600L,
                        (long) step.webhook().baseBackoffSeconds()
                                * (1L << Math.min(exponent, 20)));
                yield now.plusSeconds(seconds);
            }
        };
    }

    private void requireLease(String tokenHash, Instant now) {
        Objects.requireNonNull(now, "now");
        requireStatus(Status.LEASED, "Completion execution is not leased");
        if (!lease.tokenHash().equals(tokenHash)
                || !lease.expiresAt().isAfter(now)) {
            throw leaseInvalid("Completion lease is stale or invalid");
        }
    }

    private void requireStatus(Status expected, String message) {
        if (status != expected) {
            throw conflict(message);
        }
    }

    private void requireSubflowRunning() {
        if (step.type() != ApprovalCompletionStep.Type.SUBFLOW
                || status != Status.RUNNING) {
            throw conflict("Subflow completion execution is not running");
        }
    }

    private ApprovalCompletionExecution copy(
            Status nextStatus,
            int nextAttemptCount,
            Instant nextAvailableAt,
            Lease nextLease,
            Instant nextStartedAt,
            Instant nextTerminalAt,
            String nextResultJson,
            Failure nextFailure
    ) {
        return new ApprovalCompletionExecution(
                id, instanceId, definitionId, definitionVersion, ordinal,
                step, payloadJson, nextStatus, nextAttemptCount,
                stateVersion + 1,
                nextAvailableAt, nextLease, createdAt, nextStartedAt,
                nextTerminalAt, nextResultJson, nextFailure);
    }

    private static int resultLimit(ApprovalCompletionStep step) {
        return step.type() == ApprovalCompletionStep.Type.EXTERNAL_TASK
                ? step.externalTask().resultJsonLimitBytes()
                : 8192;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw invalid(message);
        }
    }

    private static ApprovalDomainException invalid(String message) {
        return new ApprovalDomainException(
                ApprovalDomainException.Code.COMPLETION_EXECUTION_INVALID,
                message);
    }

    private static ApprovalDomainException conflict(String message) {
        return new ApprovalDomainException(COMPLETION_STATE_CONFLICT, message);
    }

    private static ApprovalDomainException leaseInvalid(String message) {
        return new ApprovalDomainException(COMPLETION_LEASE_INVALID, message);
    }

    public enum Status {
        WAITING,
        AVAILABLE,
        LEASED,
        RETRYING,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCELLED
    }

    public record Lease(
            String owner,
            String tokenHash,
            Instant expiresAt
    ) {
        public Lease {
            owner = ApprovalCompletionStep.bounded(
                    owner, "Completion lease owner", 160);
            if (tokenHash == null
                    || !tokenHash.matches("^[0-9a-f]{64}$")) {
                throw leaseInvalid("Completion lease token hash is invalid");
            }
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }

    public record Failure(
            String code,
            String message,
            boolean retryable
    ) {
        public Failure {
            code = ApprovalCompletionStep.bounded(
                    code, "Completion failure code", 64);
            if (!code.matches("^[A-Z][A-Z0-9_]{0,63}$")) {
                throw invalid("Completion failure code is invalid");
            }
            message = ApprovalCompletionStep.bounded(
                    message, "Completion failure message", 500);
        }
    }
}
