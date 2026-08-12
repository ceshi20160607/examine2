package com.unique.examine.flow.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Independent reverse execution linked to one succeeded forward execution. */
public record ApprovalCompletionCompensation(
        long id,
        long instanceId,
        long originalExecutionId,
        int originalOrdinal,
        int reverseOrdinal,
        ApprovalCompletionExecution execution
) {
    public ApprovalCompletionCompensation {
        if (id <= 0 || instanceId <= 0 || originalExecutionId <= 0
                || originalOrdinal < 0
                || originalOrdinal >= ApprovalCompletionStep.MAX_STEPS
                || reverseOrdinal < 0
                || reverseOrdinal >= ApprovalCompletionStep.MAX_STEPS
                || execution == null || execution.id() != id
                || execution.instanceId() != instanceId
                || execution.ordinal() != reverseOrdinal
                || execution.step().parallelGroup() != null
                || execution.step().compensation() != null) {
            throw conflict("Completion compensation identity is invalid");
        }
    }

    public static List<ApprovalCompletionCompensation> materializePlan(
            List<ApprovalCompletionExecution> originals,
            LongSupplier ids,
            Instant createdAt
    ) {
        Objects.requireNonNull(originals, "originals");
        Objects.requireNonNull(ids, "ids");
        Objects.requireNonNull(createdAt, "createdAt");
        var succeeded = originals.stream()
                .filter(value -> value.status()
                        == ApprovalCompletionExecution.Status.SUCCEEDED)
                .sorted(Comparator
                        .comparingInt(ApprovalCompletionExecution::ordinal)
                        .reversed()
                        .thenComparing(
                                ApprovalCompletionExecution::id,
                                Comparator.reverseOrder()))
                .toList();
        var plan = new ArrayList<ApprovalCompletionCompensation>(
                succeeded.size());
        for (var reverseOrdinal = 0;
             reverseOrdinal < succeeded.size(); reverseOrdinal++) {
            var original = succeeded.get(reverseOrdinal);
            var config = original.step().compensation();
            if (config == null) {
                throw conflict("Succeeded execution has no frozen compensation");
            }
            var id = ids.getAsLong();
            var execution = ApprovalCompletionExecution.materialize(
                    id, original.instanceId(), original.definitionId(),
                    original.definitionVersion(), reverseOrdinal,
                    config.asStep(original.step()), original.payloadJson(),
                    createdAt, reverseOrdinal == 0);
            plan.add(new ApprovalCompletionCompensation(
                    id, original.instanceId(), original.id(),
                    original.ordinal(), reverseOrdinal, execution));
        }
        return List.copyOf(plan);
    }

    public ApprovalCompletionExecution.Status status() {
        return execution.status();
    }

    public int attemptCount() {
        return execution.attemptCount();
    }

    public int stateVersion() {
        return execution.stateVersion();
    }

    public ApprovalCompletionStep step() {
        return execution.step();
    }

    public boolean isDueAt(Instant now) {
        return execution.isDueAt(now);
    }

    public ApprovalCompletionCompensation recoverExpiredLease(Instant now) {
        return with(execution.recoverExpiredLease(now));
    }

    public ApprovalCompletionCompensation activate(Instant now) {
        return with(execution.activate(now));
    }

    public ApprovalCompletionCompensation claim(
            String owner, String tokenHash, Instant leaseUntil, Instant now
    ) {
        return with(execution.claim(owner, tokenHash, leaseUntil, now));
    }

    public ApprovalCompletionCompensation heartbeat(
            String tokenHash, Instant leaseUntil, Instant now
    ) {
        return with(execution.heartbeat(tokenHash, leaseUntil, now));
    }

    public ApprovalCompletionCompensation complete(
            String tokenHash, String resultJson, Instant now
    ) {
        return with(execution.complete(tokenHash, resultJson, now));
    }

    public ApprovalCompletionCompensation fail(
            String tokenHash, String code, String message,
            boolean retryable, Instant now
    ) {
        return with(execution.fail(
                tokenHash, code, message, retryable, now));
    }

    public ApprovalCompletionCompensation startSubflow(Instant now) {
        return with(execution.startSubflow(now));
    }

    public ApprovalCompletionCompensation completeSubflow(
            String resultJson, Instant now
    ) {
        return with(execution.completeSubflow(resultJson, now));
    }

    public ApprovalCompletionCompensation failSubflow(
            String code, String message, Instant now
    ) {
        return with(execution.failSubflow(code, message, now));
    }

    public ApprovalCompletionCompensation retry(Instant now) {
        return with(execution.retry(now));
    }

    public ApprovalCompletionCompensation cancel(Instant now) {
        return with(execution.cancel(now));
    }

    private ApprovalCompletionCompensation with(
            ApprovalCompletionExecution next
    ) {
        return new ApprovalCompletionCompensation(
                id, instanceId, originalExecutionId, originalOrdinal,
                reverseOrdinal, next);
    }

    private static ApprovalDomainException conflict(String message) {
        return new ApprovalDomainException(
                ApprovalDomainException.Code.COMPLETION_STATE_CONFLICT,
                message);
    }
}
