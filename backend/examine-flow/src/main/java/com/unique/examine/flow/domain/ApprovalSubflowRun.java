package com.unique.examine.flow.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Durable link between one parent completion attempt and exactly one child.
 * A terminal run whose result has not been applied is also the durable result
 * fact consumed by propagation and reconciliation workers.
 */
public record ApprovalSubflowRun(
        long id,
        long executionId,
        int attemptNumber,
        String launchKey,
        long childInstanceId,
        long targetDefinitionId,
        int targetVersion,
        long rootInstanceId,
        int depth,
        Status status,
        Instant launchedAt,
        Instant terminalAt,
        String resultCode,
        Instant resultAppliedAt,
        int stateVersion
) {
    private static final String LAUNCH_KEY_PATTERN = "^[0-9a-f]{64}$";

    public ApprovalSubflowRun {
        if (id <= 0 || executionId <= 0 || attemptNumber < 1
                || launchKey == null || !launchKey.matches(LAUNCH_KEY_PATTERN)
                || childInstanceId <= 0 || targetDefinitionId <= 0
                || targetVersion < 1 || rootInstanceId <= 0
                || depth < 1 || depth > ApprovalStartContext.MAX_SUBFLOW_DEPTH
                || stateVersion < 0) {
            throw invalid("Subflow run identity or hierarchy is invalid");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(launchedAt, "launchedAt");
        if (status == Status.RUNNING) {
            require(terminalAt == null && resultCode == null
                            && resultAppliedAt == null,
                    "Running subflow contains terminal result state");
        } else {
            require(terminalAt != null && !terminalAt.isBefore(launchedAt)
                            && status.name().equals(resultCode),
                    "Terminal subflow result is invalid");
            require(resultAppliedAt == null
                            || !resultAppliedAt.isBefore(terminalAt),
                    "Subflow result application time is invalid");
        }
    }

    public static ApprovalSubflowRun launch(
            long id,
            long executionId,
            int attemptNumber,
            String launchKey,
            long childInstanceId,
            ApprovalCompletionStep.Subflow target,
            long rootInstanceId,
            int depth,
            Instant launchedAt
    ) {
        Objects.requireNonNull(target, "target");
        return new ApprovalSubflowRun(
                id, executionId, attemptNumber, launchKey, childInstanceId,
                target.definitionId(), target.version(), rootInstanceId, depth,
                Status.RUNNING, launchedAt, null, null, null, 0);
    }

    public ApprovalSubflowRun observeTerminal(Status result, Instant occurredAt) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (status == result && terminal()) {
            return this;
        }
        if (status != Status.RUNNING || result == Status.RUNNING) {
            throw conflict("Subflow run cannot accept this terminal result");
        }
        return new ApprovalSubflowRun(
                id, executionId, attemptNumber, launchKey, childInstanceId,
                targetDefinitionId, targetVersion, rootInstanceId, depth,
                result, launchedAt, occurredAt, result.name(), null,
                stateVersion + 1);
    }

    public ApprovalSubflowRun markResultApplied(Instant appliedAt) {
        Objects.requireNonNull(appliedAt, "appliedAt");
        if (resultAppliedAt != null) {
            return this;
        }
        if (!terminal()) {
            throw conflict("Subflow result is not pending application");
        }
        return new ApprovalSubflowRun(
                id, executionId, attemptNumber, launchKey, childInstanceId,
                targetDefinitionId, targetVersion, rootInstanceId, depth,
                status, launchedAt, terminalAt, resultCode, appliedAt,
                stateVersion + 1);
    }

    public boolean terminal() {
        return status != Status.RUNNING;
    }

    public boolean pendingResult() {
        return terminal() && resultAppliedAt == null;
    }

    public enum Status {
        RUNNING,
        APPROVED_COMPLETED,
        REJECTED,
        WITHDRAWN,
        TERMINATED
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw invalid(message);
        }
    }

    private static ApprovalDomainException invalid(String message) {
        return new ApprovalDomainException(
                ApprovalDomainException.Code.SUBFLOW_TARGET_INVALID, message);
    }

    private static ApprovalDomainException conflict(String message) {
        return new ApprovalDomainException(
                ApprovalDomainException.Code.SUBFLOW_RUN_CONFLICT, message);
    }
}
