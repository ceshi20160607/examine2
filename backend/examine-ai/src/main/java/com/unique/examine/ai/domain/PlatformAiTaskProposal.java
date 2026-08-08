package com.unique.examine.ai.domain;

import java.time.Instant;
import java.util.Objects;

/** Account/session/turn-bound proposal for one self-assigned platform task. */
public record PlatformAiTaskProposal(
        long id,
        PlatformAiConversation.Scope scope,
        long accountId,
        long sessionId,
        long turnId,
        long policyVersionId,
        long providerId,
        long providerVersion,
        long authorizationEpoch,
        String promptVersion,
        String planHash,
        State state,
        long revision,
        Preview preview,
        double confidence,
        String clarification,
        SealedCommand sealedCommand,
        Instant expiresAt,
        Long actedBy,
        Result result,
        String resultCode,
        String ownerRequestId,
        String ownerTraceId,
        Instant createdAt,
        Instant updatedAt,
        Instant finishedAt
) {
    public PlatformAiTaskProposal {
        positive(id, "id");
        scope = Objects.requireNonNull(scope, "scope");
        positive(accountId, "accountId");
        positive(sessionId, "sessionId");
        positive(turnId, "turnId");
        positive(policyVersionId, "policyVersionId");
        positive(providerId, "providerId");
        if (providerVersion < 0 || authorizationEpoch <= 0 || revision < 0) {
            invalid("snapshot");
        }
        promptVersion = token(promptVersion, "promptVersion", 64);
        hash(planHash, "planHash");
        state = Objects.requireNonNull(state, "state");
        if (!Double.isFinite(confidence) || confidence < 0d || confidence > 1d) {
            invalid("confidence");
        }
        if (clarification != null) clarification = text(
                clarification, "clarification", 500);
        Objects.requireNonNull(expiresAt, "expiresAt");
        resultCode = token(resultCode, "resultCode", 64);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (!expiresAt.isAfter(createdAt) || updatedAt.isBefore(createdAt)) {
            invalid("timestamps");
        }
        if (actedBy != null && actedBy <= 0) invalid("actedBy");
        if (ownerRequestId != null) ownerRequestId = token(
                ownerRequestId, "ownerRequestId", 128);
        if (ownerTraceId != null) ownerTraceId = token(
                ownerTraceId, "ownerTraceId", 128);

        if (state == State.CLARIFICATION_REQUIRED) {
            if (preview != null || sealedCommand != null || clarification == null
                    || actedBy != null || result != null || finishedAt != null) {
                invalid("clarification state");
            }
        } else {
            preview = Objects.requireNonNull(preview, "preview");
            sealedCommand = Objects.requireNonNull(sealedCommand, "sealedCommand");
            if (clarification != null) invalid("actionable clarification");
        }
        if (state == State.PENDING) {
            if (actedBy != null || result != null || finishedAt != null) {
                invalid("pending state");
            }
        } else if (state == State.EXECUTING) {
            positive(Objects.requireNonNull(actedBy, "actedBy"), "actedBy");
            if (result != null || finishedAt != null) invalid("executing state");
        } else if (state != State.CLARIFICATION_REQUIRED && finishedAt == null) {
            invalid("terminal state");
        }
        if (state == State.SUCCEEDED && result == null) invalid("success result");
        if (state != State.SUCCEEDED && result != null) invalid("non-success result");
    }

    public enum State {
        CLARIFICATION_REQUIRED,
        PENDING,
        EXECUTING,
        SUCCEEDED,
        FAILED,
        REJECTED,
        EXPIRED
    }

    public enum Priority { LOW, NORMAL, HIGH, URGENT }

    public record Preview(
            String title,
            String description,
            Instant dueAt,
            Priority priority
    ) {
        public Preview {
            title = text(title, "title", 200);
            if (description != null) description = text(
                    description, "description", 200);
            priority = Objects.requireNonNull(priority, "priority");
        }
    }

    /** Opaque authenticated owner command. */
    public record SealedCommand(
            String ciphertext,
            String keyVersion,
            String commandHash
    ) {
        public SealedCommand {
            ciphertext = text(ciphertext, "ciphertext", 131_072);
            keyVersion = text(keyVersion, "keyVersion", 64);
            hash(commandHash, "commandHash");
        }
    }

    public record Result(
            String taskId,
            String title,
            String description,
            Instant dueAt,
            Priority priority,
            String status,
            String source,
            Instant createdAt
    ) {
        public Result {
            taskId = positiveDecimal(taskId, "taskId");
            title = text(title, "result title", 200);
            if (description != null) description = text(
                    description, "result description", 200);
            priority = Objects.requireNonNull(priority, "result priority");
            status = token(status, "status", 32);
            source = token(source, "source", 32);
            Objects.requireNonNull(createdAt, "result createdAt");
        }
    }

    public record Attempt(
            long id,
            PlatformAiConversation.Scope scope,
            long accountId,
            long sessionId,
            long turnId,
            long policyVersionId,
            long providerId,
            long providerVersion,
            long proposalId,
            String action,
            String requestKey,
            String requestHash,
            State status,
            String resultHash,
            String resultCode,
            Instant createdAt,
            Instant finishedAt
    ) {
        public Attempt {
            positive(id, "attempt id");
            scope = Objects.requireNonNull(scope, "scope");
            positive(accountId, "attempt accountId");
            positive(sessionId, "attempt sessionId");
            positive(turnId, "attempt turnId");
            positive(policyVersionId, "attempt policyVersionId");
            positive(providerId, "attempt providerId");
            if (providerVersion < 0) invalid("attempt providerVersion");
            positive(proposalId, "attempt proposalId");
            action = token(action, "attempt action", 16);
            requestKey = token(requestKey, "requestKey", 128);
            hash(requestHash, "requestHash");
            status = Objects.requireNonNull(status, "status");
            if (resultHash != null) hash(resultHash, "resultHash");
            resultCode = token(resultCode, "attempt resultCode", 64);
            Objects.requireNonNull(createdAt, "attempt createdAt");
            if (status == State.EXECUTING && finishedAt != null) {
                invalid("running attempt");
            }
            if (status != State.EXECUTING && finishedAt == null) {
                invalid("finished attempt");
            }
        }
    }

    public record Event(
            long id,
            PlatformAiConversation.Scope scope,
            long accountId,
            long sessionId,
            long turnId,
            long policyVersionId,
            long providerId,
            long providerVersion,
            long proposalId,
            Long attemptId,
            String eventType,
            State fromState,
            State toState,
            long revision,
            long actorAccountId,
            String requestId,
            String traceId,
            String resultCode,
            String eventHash,
            Instant createdAt
    ) {
        public Event {
            positive(id, "event id");
            scope = Objects.requireNonNull(scope, "scope");
            positive(accountId, "event accountId");
            positive(sessionId, "event sessionId");
            positive(turnId, "event turnId");
            positive(policyVersionId, "event policyVersionId");
            positive(providerId, "event providerId");
            if (providerVersion < 0 || revision < 0) invalid("event version");
            positive(proposalId, "event proposalId");
            if (attemptId != null && attemptId <= 0) invalid("event attemptId");
            eventType = token(eventType, "eventType", 32);
            toState = Objects.requireNonNull(toState, "toState");
            positive(actorAccountId, "actorAccountId");
            requestId = token(requestId, "requestId", 128);
            traceId = token(traceId, "traceId", 128);
            resultCode = token(resultCode, "event resultCode", 64);
            hash(eventHash, "eventHash");
            Objects.requireNonNull(createdAt, "event createdAt");
        }
    }

    private static String positiveDecimal(String value, String field) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) {
                throw new NumberFormatException();
            }
            return value;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(
                    "Platform AI task " + field + " is invalid", failure);
        }
    }

    private static String token(String value, String field, int maximum) {
        value = text(value, field, maximum);
        if (!value.matches("^[A-Za-z0-9][A-Za-z0-9_.:-]{0,"
                + (maximum - 1) + "}$")) invalid(field);
        return value;
    }

    private static String text(String value, String field, int maximum) {
        if (value == null || value.isBlank()) invalid(field);
        value = value.strip();
        if (value.codePointCount(0, value.length()) > maximum) invalid(field);
        return value;
    }

    private static void hash(String value, String field) {
        if (value == null || !value.matches("^[0-9a-f]{64}$")) invalid(field);
    }

    private static void positive(long value, String field) {
        if (value <= 0) invalid(field);
    }

    private static void invalid(String field) {
        throw new IllegalArgumentException(
                "Platform AI task " + field + " is invalid");
    }
}
