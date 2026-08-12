package com.unique.examine.ai.domain;

import com.unique.examine.core.ai.AiFieldFillFacade;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record AiFillProposal(
        long id,
        long systemId,
        long tenantId,
        long memberId,
        String moduleCode,
        String recordId,
        String fieldId,
        String fieldCode,
        String fieldName,
        AiFieldFillFacade.ResultSchema resultSchema,
        long expectedRecordVersion,
        String schemaVersionId,
        String sourceVersionHash,
        long policyVersionId,
        long providerId,
        long providerVersion,
        String model,
        String promptVersion,
        long authorizationEpoch,
        List<SourceSummary> sources,
        String beforeDisplayValue,
        String afterDisplayValue,
        double confidence,
        String clarificationSummary,
        boolean overwrite,
        String resultHash,
        AiFieldFillFacade.SealedCommand sealedCommand,
        State state,
        long revision,
        Instant expiresAt,
        Long actedBy,
        FillResult result,
        String resultCode,
        String ownerRequestId,
        String ownerTraceId,
        int promptTokens,
        int completionTokens,
        long providerLatencyMs,
        Instant createdAt,
        Instant updatedAt,
        Instant finishedAt
) {
    public AiFillProposal {
        positive(id, "id");
        positive(systemId, "systemId");
        positive(tenantId, "tenantId");
        positive(memberId, "memberId");
        moduleCode = code(moduleCode, "moduleCode");
        recordId = positiveDecimal(recordId, "recordId");
        fieldId = positiveDecimal(fieldId, "fieldId");
        fieldCode = code(fieldCode, "fieldCode");
        fieldName = required(fieldName, "fieldName", 160);
        resultSchema = Objects.requireNonNull(resultSchema, "resultSchema");
        if (expectedRecordVersion < 0 || providerVersion < 0
                || authorizationEpoch <= 0 || revision < 0
                || promptTokens < 0 || completionTokens < 0
                || providerLatencyMs < 0) {
            throw new IllegalArgumentException("AI fill proposal versions are invalid");
        }
        schemaVersionId = positiveDecimal(schemaVersionId, "schemaVersionId");
        hash(sourceVersionHash, "sourceVersionHash");
        positive(policyVersionId, "policyVersionId");
        positive(providerId, "providerId");
        model = required(model, "model", 160);
        promptVersion = required(promptVersion, "promptVersion", 64);
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        if (!Double.isFinite(confidence) || confidence < 0d || confidence > 1d) {
            throw new IllegalArgumentException("AI fill proposal confidence is invalid");
        }
        if (resultHash != null) hash(resultHash, "resultHash");
        state = Objects.requireNonNull(state, "state");
        if (state == State.PENDING || state == State.EXECUTING
                || state == State.SUCCEEDED || state == State.REJECTED) {
            sealedCommand = Objects.requireNonNull(sealedCommand, "sealedCommand");
        }
        if (state == State.CLARIFICATION_REQUIRED && sealedCommand != null) {
            throw new IllegalArgumentException("AI clarification cannot contain a command");
        }
        Objects.requireNonNull(expiresAt, "expiresAt");
        resultCode = token(resultCode, "resultCode", 64);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt) || !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("AI fill proposal timestamps are invalid");
        }
        if (actedBy != null && actedBy <= 0) {
            throw new IllegalArgumentException("AI fill proposal actor is invalid");
        }
        if (state == State.PENDING || state == State.CLARIFICATION_REQUIRED) {
            if (actedBy != null || result != null || finishedAt != null) {
                throw new IllegalArgumentException("AI fill proposal pending state is invalid");
            }
        } else if (state == State.EXECUTING) {
            positive(Objects.requireNonNull(actedBy, "actedBy"), "actedBy");
            if (result != null || finishedAt != null) {
                throw new IllegalArgumentException("AI fill proposal executing state is invalid");
            }
        } else if (finishedAt == null) {
            throw new IllegalArgumentException("AI fill terminal state is unfinished");
        }
        if (state == State.SUCCEEDED && result == null) {
            throw new IllegalArgumentException("AI fill success has no readback");
        }
        if (state != State.SUCCEEDED && result != null) {
            throw new IllegalArgumentException("AI fill non-success has a readback");
        }
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

    public record SourceSummary(
            String fieldId,
            String fieldCode,
            String fieldName,
            String fieldType,
            String displaySummary
    ) {
        public SourceSummary {
            fieldId = positiveDecimal(fieldId, "source fieldId");
            fieldCode = code(fieldCode, "source fieldCode");
            fieldName = required(fieldName, "source fieldName", 160);
            fieldType = required(fieldType, "source fieldType", 32);
            displaySummary = required(displaySummary, "displaySummary", 200);
        }
    }

    public record FillResult(
            String historyId,
            String recordId,
            long recordVersion,
            String schemaVersionId,
            String fieldId,
            String fieldCode,
            AiFieldFillFacade.ResultSchema resultSchema,
            String displayValue,
            double confidence,
            long materializationVersion,
            String outcome,
            Instant executedAt
    ) {
        public FillResult {
            historyId = positiveDecimal(historyId, "historyId");
            recordId = positiveDecimal(recordId, "result recordId");
            if (recordVersion < 0 || materializationVersion < 0) {
                throw new IllegalArgumentException("AI fill result versions are invalid");
            }
            schemaVersionId = positiveDecimal(schemaVersionId, "result schemaVersionId");
            fieldId = positiveDecimal(fieldId, "result fieldId");
            fieldCode = code(fieldCode, "result fieldCode");
            resultSchema = Objects.requireNonNull(resultSchema, "resultSchema");
            if (!Double.isFinite(confidence) || confidence < 0d || confidence > 1d) {
                throw new IllegalArgumentException("AI fill result confidence is invalid");
            }
            outcome = token(outcome, "outcome", 32);
            Objects.requireNonNull(executedAt, "executedAt");
        }
    }

    public record Attempt(
            long id, long systemId, long tenantId, long memberId,
            long proposalId, String action, String requestKey,
            String requestHash, State status, String resultCode,
            Instant createdAt, Instant finishedAt
    ) {
        public Attempt {
            positive(id, "attempt id");
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(memberId, "memberId");
            positive(proposalId, "proposalId");
            action = token(action, "action", 16);
            requestKey = token(requestKey, "requestKey", 128);
            hash(requestHash, "requestHash");
            status = Objects.requireNonNull(status, "status");
            resultCode = token(resultCode, "resultCode", 64);
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    public record Event(
            long id, long systemId, long tenantId, long proposalId,
            String eventType, State fromState, State toState, long revision,
            long actorMemberId, String requestId, String traceId,
            String resultCode, String eventHash, Instant createdAt
    ) {
        public Event {
            positive(id, "event id");
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(proposalId, "proposalId");
            eventType = token(eventType, "eventType", 32);
            toState = Objects.requireNonNull(toState, "toState");
            if (revision < 0) throw new IllegalArgumentException("revision is invalid");
            positive(actorMemberId, "actorMemberId");
            requestId = required(requestId, "requestId", 128);
            traceId = required(traceId, "traceId", 128);
            resultCode = token(resultCode, "resultCode", 64);
            hash(eventHash, "eventHash");
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    private static String required(String value, String name, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException("AI fill " + name + " is invalid");
        }
        return value;
    }

    private static String token(String value, String name, int maximum) {
        value = required(value, name, maximum);
        if (!value.matches("^[A-Za-z0-9][A-Za-z0-9_.:-]{0," + (maximum - 1) + "}$")) {
            throw new IllegalArgumentException("AI fill " + name + " is invalid");
        }
        return value;
    }

    private static String code(String value, String name) {
        if (value == null || !value.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw new IllegalArgumentException("AI fill " + name + " is invalid");
        }
        return value;
    }

    private static String positiveDecimal(String value, String name) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) throw new NumberFormatException();
            return value;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("AI fill " + name + " is invalid", failure);
        }
    }

    private static void positive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException("AI fill " + name + " is invalid");
    }

    private static void hash(String value, String name) {
        if (value == null || !value.matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("AI fill " + name + " is invalid");
        }
    }
}
