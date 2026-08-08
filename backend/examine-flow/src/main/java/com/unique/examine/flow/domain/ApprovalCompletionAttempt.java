package com.unique.examine.flow.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.COMPLETION_EXECUTION_INVALID;

/**
 * Append-only sanitized execution transition fact.
 */
public record ApprovalCompletionAttempt(
        long id,
        long executionId,
        int attemptNumber,
        int eventSequence,
        Event event,
        Long actorMemberId,
        String leaseOwner,
        String idempotencyKeyHash,
        String resultJson,
        String failureCode,
        String failureMessage,
        Integer httpStatus,
        Long durationMs,
        String responseSha256,
        Instant startedAt,
        Instant completedAt,
        Instant occurredAt
) {
    private static final ObjectMapper JSON = new ObjectMapper();

    public ApprovalCompletionAttempt(
            long id,
            long executionId,
            int attemptNumber,
            int eventSequence,
            Event event,
            Long actorMemberId,
            String leaseOwner,
            String idempotencyKeyHash,
            String resultJson,
            String failureCode,
            String failureMessage,
            Instant occurredAt
    ) {
        this(
                id, executionId, attemptNumber, eventSequence, event,
                actorMemberId, leaseOwner, idempotencyKeyHash, resultJson,
                failureCode, failureMessage, null, null, null, null, null,
                occurredAt
        );
    }

    public ApprovalCompletionAttempt {
        if (id <= 0 || executionId <= 0 || attemptNumber < 0
                || eventSequence < 1) {
            throw invalid("Completion attempt identity is invalid");
        }
        Objects.requireNonNull(event, "event");
        if (actorMemberId != null && actorMemberId <= 0) {
            throw invalid("Completion attempt actor is invalid");
        }
        if (leaseOwner != null) {
            leaseOwner = bounded(leaseOwner, "Lease owner", 160);
        }
        if (idempotencyKeyHash != null
                && !idempotencyKeyHash.matches("^[0-9a-f]{64}$")) {
            throw invalid("Completion idempotency hash is invalid");
        }
        resultJson = canonicalObject(resultJson, 8192);
        if (failureCode != null) {
            failureCode = bounded(
                    failureCode, "Completion failure code", 64);
            if (!failureCode.matches("^[A-Z][A-Z0-9_]{0,63}$")) {
                throw invalid("Completion failure code is invalid");
            }
        }
        if (failureMessage != null) {
            failureMessage = bounded(
                    failureMessage, "Completion failure message", 500);
        }
        if ((failureCode == null) != (failureMessage == null)) {
            throw invalid(
                    "Completion failure code and message must occur together");
        }
        if (event == Event.SUCCEEDED && resultJson == null
                || event == Event.FAILED && failureCode == null) {
            throw invalid("Completion attempt outcome facts are incomplete");
        }
        if (httpStatus != null
                && (httpStatus < 100 || httpStatus > 599)) {
            throw invalid("Completion HTTP status is invalid");
        }
        if (durationMs != null
                && (durationMs < 0 || durationMs > 86_400_000L)) {
            throw invalid("Completion duration is invalid");
        }
        if (responseSha256 != null
                && !responseSha256.matches("^[0-9a-f]{64}$")) {
            throw invalid("Completion response hash is invalid");
        }
        if ((startedAt == null) != (completedAt == null)
                || startedAt != null && completedAt.isBefore(startedAt)) {
            throw invalid("Completion attempt timing facts are invalid");
        }
        var hasDeliveryFacts = httpStatus != null
                || durationMs != null
                || responseSha256 != null
                || startedAt != null;
        if (hasDeliveryFacts
                && (durationMs == null
                || startedAt == null
                || event != Event.SUCCEEDED
                && event != Event.FAILED
                && event != Event.RETRIED)) {
            throw invalid(
                    "Completion delivery diagnostics require an outcome event and timing");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public enum Event {
        ACTIVATED,
        STARTED,
        STAGE_JOINED,
        CLAIMED,
        LEASE_EXPIRED,
        RETRIED,
        SUCCEEDED,
        FAILED,
        CANCELLED
    }

    static String canonicalObject(String value, int maximumBytes) {
        if (value == null) {
            return null;
        }
        try {
            var parsed = JSON.readTree(value);
            if (parsed == null || !parsed.isObject()) {
                throw invalid("Completion JSON must be an object");
            }
            var canonical = parsed.toString();
            if (canonical.getBytes(StandardCharsets.UTF_8).length
                    > maximumBytes) {
                throw invalid("Completion JSON exceeds its byte limit");
            }
            return canonical;
        } catch (JsonProcessingException exception) {
            throw invalid("Completion JSON must be valid");
        }
    }

    private static String bounded(
            String value,
            String label,
            int maximum
    ) {
        return ApprovalCompletionStep.bounded(value, label, maximum);
    }

    private static ApprovalDomainException invalid(String message) {
        return new ApprovalDomainException(
                COMPLETION_EXECUTION_INVALID, message);
    }
}
