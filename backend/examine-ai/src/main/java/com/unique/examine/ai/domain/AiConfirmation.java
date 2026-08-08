package com.unique.examine.ai.domain;

import com.unique.examine.core.ai.AiRecordMutationFacade;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Tenant/member-bound mutation proposal whose command is always owner-sealed. */
public record AiConfirmation(
        long id,
        long systemId,
        long tenantId,
        long memberId,
        long sessionId,
        long turnId,
        long policyVersionId,
        long providerId,
        long providerVersion,
        String promptVersion,
        long authorizationEpoch,
        AiRecordMutationFacade.Operation operation,
        String moduleCode,
        String schemaVersionId,
        String recordId,
        Long expectedRecordVersion,
        String planHash,
        Preview preview,
        List<Confidence> confidence,
        List<String> clarifications,
        AiRecordMutationFacade.SealedCommand sealedCommand,
        State state,
        long revision,
        Instant expiresAt,
        Long confirmedBy,
        Result result,
        String resultCode,
        String ownerRequestId,
        String ownerTraceId,
        Instant createdAt,
        Instant updatedAt,
        Instant finishedAt
) {
    public AiConfirmation {
        positive(id, "id");
        positive(systemId, "systemId");
        positive(tenantId, "tenantId");
        positive(memberId, "memberId");
        positive(sessionId, "sessionId");
        positive(turnId, "turnId");
        positive(policyVersionId, "policyVersionId");
        positive(providerId, "providerId");
        if (providerVersion < 0 || authorizationEpoch <= 0 || revision < 0) {
            throw new IllegalArgumentException("AI confirmation snapshot is invalid");
        }
        promptVersion = token(promptVersion, "promptVersion", 64);
        operation = Objects.requireNonNull(operation, "operation");
        moduleCode = code(moduleCode, "moduleCode");
        schemaVersionId = positiveDecimal(schemaVersionId, "schemaVersionId");
        if (operation == AiRecordMutationFacade.Operation.RECORD_CREATE) {
            if (recordId != null || expectedRecordVersion != null) {
                throw new IllegalArgumentException(
                        "AI create confirmation cannot contain record identity");
            }
        } else {
            recordId = positiveDecimal(recordId, "recordId");
            if (expectedRecordVersion == null || expectedRecordVersion < 0) {
                throw new IllegalArgumentException(
                        "AI update confirmation version is invalid");
            }
        }
        hash(planHash, "planHash");
        preview = Objects.requireNonNull(preview, "preview");
        confidence = List.copyOf(Objects.requireNonNull(confidence, "confidence"));
        clarifications = List.copyOf(Objects.requireNonNull(
                clarifications, "clarifications"));
        sealedCommand = Objects.requireNonNull(sealedCommand, "sealedCommand");
        state = Objects.requireNonNull(state, "state");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt) || expiresAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("AI confirmation timestamps are invalid");
        }
        if (confirmedBy != null && confirmedBy <= 0) {
            throw new IllegalArgumentException("AI confirmation actor is invalid");
        }
        resultCode = token(resultCode, "resultCode", 64);
        if (state == State.PENDING) {
            if (confirmedBy != null || result != null || finishedAt != null) {
                throw new IllegalArgumentException("AI pending confirmation has final data");
            }
        } else if (state == State.EXECUTING) {
            positive(Objects.requireNonNull(confirmedBy, "confirmedBy"), "confirmedBy");
            if (finishedAt != null) {
                throw new IllegalArgumentException("AI executing confirmation is finished");
            }
        } else if (finishedAt == null) {
            throw new IllegalArgumentException("AI terminal confirmation is unfinished");
        }
        if (state == State.SUCCEEDED && result == null) {
            throw new IllegalArgumentException("AI successful confirmation has no result");
        }
        if (state != State.SUCCEEDED && result != null) {
            throw new IllegalArgumentException("AI non-success confirmation has a result");
        }
    }

    public enum State {
        PENDING,
        EXECUTING,
        SUCCEEDED,
        FAILED,
        REJECTED,
        EXPIRED
    }

    public record Preview(
            String beforeTitle,
            String afterTitle,
            List<FieldChange> fields
    ) {
        public Preview {
            fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        }
    }

    public record FieldChange(
            String fieldCode,
            String fieldName,
            String type,
            String beforeDisplay,
            String afterDisplay,
            boolean masked
    ) {
        public FieldChange {
            fieldCode = code(fieldCode, "fieldCode");
            fieldName = required(fieldName, "fieldName", 160);
            type = required(type, "type", 32);
        }
    }

    public record Confidence(String fieldCode, double value) {
        public Confidence {
            fieldCode = code(fieldCode, "fieldCode");
            if (!Double.isFinite(value) || value < 0d || value > 1d) {
                throw new IllegalArgumentException("AI confidence is invalid");
            }
        }
    }

    public record Result(
            String recordId,
            String recordNo,
            long recordVersion,
            String status,
            String title,
            String schemaVersionId,
            List<DisplayValue> values,
            Instant executedAt
    ) {
        public Result {
            recordId = positiveDecimal(recordId, "recordId");
            recordNo = required(recordNo, "recordNo", 128);
            if (recordVersion < 0) {
                throw new IllegalArgumentException("AI result recordVersion is invalid");
            }
            status = required(status, "status", 32);
            schemaVersionId = positiveDecimal(schemaVersionId, "schemaVersionId");
            values = List.copyOf(Objects.requireNonNull(values, "values"));
            Objects.requireNonNull(executedAt, "executedAt");
        }
    }

    public record DisplayValue(
            String fieldCode,
            String fieldName,
            String type,
            String displayValue,
            boolean masked
    ) {
        public DisplayValue {
            fieldCode = code(fieldCode, "fieldCode");
            fieldName = required(fieldName, "fieldName", 160);
            type = required(type, "type", 32);
        }
    }

    public record Attempt(
            long id,
            long systemId,
            long tenantId,
            long confirmationId,
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
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(confirmationId, "confirmationId");
            requestKey = token(requestKey, "requestKey", 128);
            hash(requestHash, "requestHash");
            status = Objects.requireNonNull(status, "status");
            if (resultHash != null) hash(resultHash, "resultHash");
            resultCode = token(resultCode, "resultCode", 64);
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    public record Event(
            long id,
            long systemId,
            long tenantId,
            long confirmationId,
            String eventType,
            State fromState,
            State toState,
            long revision,
            long actorMemberId,
            String requestId,
            String traceId,
            String resultCode,
            String eventHash,
            Instant createdAt
    ) {
        public Event {
            positive(id, "event id");
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(confirmationId, "confirmationId");
            eventType = token(eventType, "eventType", 32);
            toState = Objects.requireNonNull(toState, "toState");
            if (revision < 0) {
                throw new IllegalArgumentException("AI event revision is invalid");
            }
            positive(actorMemberId, "actorMemberId");
            requestId = required(requestId, "requestId", 128);
            traceId = required(traceId, "traceId", 128);
            resultCode = token(resultCode, "resultCode", 64);
            hash(eventHash, "eventHash");
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    private static String required(String value, String field, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException("AI confirmation " + field + " is invalid");
        }
        return value;
    }

    private static String token(String value, String field, int maximum) {
        value = required(value, field, maximum);
        if (!value.matches("^[A-Za-z0-9][A-Za-z0-9_.:-]{0," + (maximum - 1) + "}$")) {
            throw new IllegalArgumentException("AI confirmation " + field + " is invalid");
        }
        return value;
    }

    private static String code(String value, String field) {
        if (value == null || !value.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw new IllegalArgumentException("AI confirmation " + field + " is invalid");
        }
        return value;
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
                    "AI confirmation " + field + " is invalid", failure);
        }
    }

    private static void positive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException("AI confirmation " + field + " is invalid");
        }
    }

    private static void hash(String value, String field) {
        if (value == null || !value.matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("AI confirmation " + field + " is invalid");
        }
    }
}
