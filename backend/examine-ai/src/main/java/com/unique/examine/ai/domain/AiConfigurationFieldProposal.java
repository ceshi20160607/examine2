package com.unique.examine.ai.domain;

import java.time.Instant;
import java.util.Objects;

/** System/session/turn-bound proposal for one scalar field in a configuration draft. */
public record AiConfigurationFieldProposal(
        long id,
        long accountId,
        long systemId,
        long tenantId,
        long memberId,
        long sessionId,
        long turnId,
        long policyVersionId,
        long providerId,
        long providerVersion,
        long authorizationEpoch,
        String promptVersion,
        String moduleCode,
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
    public static final String REDACTED_FIELD_CODE = "[field-code:redacted]";
    public static final String REDACTED_FIELD_NAME = "[field-name:redacted]";
    public static final String REDACTED_CLARIFICATION = "[clarification:redacted]";

    public AiConfigurationFieldProposal {
        positive(id, "id");
        positive(accountId, "accountId");
        positive(systemId, "systemId");
        positive(tenantId, "tenantId");
        positive(memberId, "memberId");
        positive(sessionId, "sessionId");
        positive(turnId, "turnId");
        positive(policyVersionId, "policyVersionId");
        positive(providerId, "providerId");
        if (providerVersion < 0 || authorizationEpoch <= 0 || revision < 0) {
            invalid("snapshot");
        }
        promptVersion = token(promptVersion, "promptVersion", 64);
        if (moduleCode != null) moduleCode = code(moduleCode, "moduleCode");
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
                ownerRequestId, "ownerRequestId", 64);
        if (ownerTraceId != null) ownerTraceId = token(
                ownerTraceId, "ownerTraceId", 64);

        if (state == State.CLARIFICATION_REQUIRED) {
            if (moduleCode != null || preview != null || sealedCommand != null
                    || clarification == null || actedBy != null || result != null
                    || finishedAt != null) invalid("clarification state");
        } else {
            moduleCode = code(moduleCode, "moduleCode");
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

    public record Settings(
            Integer maxLength,
            Integer precision,
            Integer scale,
            String minimum,
            String maximum
    ) { }

    public record Preview(
            String configRootId,
            String moduleId,
            long expectedDraftRevision,
            long nextDraftRevision,
            String moduleCode,
            String fieldCode,
            String fieldName,
            String fieldType,
            boolean required,
            Settings settings
    ) {
        public Preview {
            configRootId = positiveDecimal(configRootId, "configRootId");
            moduleId = positiveDecimal(moduleId, "moduleId");
            if (expectedDraftRevision < 0
                    || nextDraftRevision != expectedDraftRevision + 1) {
                invalid("draft revision");
            }
            moduleCode = code(moduleCode, "preview moduleCode");
            fieldCode = text(fieldCode, "fieldCode", 128);
            fieldName = text(fieldName, "fieldName", 128);
            fieldType = token(fieldType, "fieldType", 32);
            settings = Objects.requireNonNull(settings, "settings");
        }
    }

    public record SealedCommand(
            String ciphertext,
            String keyVersion,
            String commandHash
    ) {
        public SealedCommand {
            ciphertext = text(ciphertext, "ciphertext", 131_072);
            keyVersion = token(keyVersion, "keyVersion", 64);
            hash(commandHash, "commandHash");
        }
    }

    public record Result(
            String configRootId,
            String moduleId,
            long draftRevision,
            String moduleCode,
            String fieldId,
            String fieldCode,
            String fieldName,
            String fieldType,
            boolean required,
            Settings settings,
            int sortOrder,
            long fieldVersion,
            String draftStatus
    ) {
        public Result {
            configRootId = positiveDecimal(configRootId, "result configRootId");
            moduleId = positiveDecimal(moduleId, "result moduleId");
            positive(draftRevision, "draftRevision");
            moduleCode = code(moduleCode, "result moduleCode");
            fieldId = positiveDecimal(fieldId, "fieldId");
            fieldCode = text(fieldCode, "result fieldCode", 128);
            fieldName = text(fieldName, "result fieldName", 128);
            fieldType = token(fieldType, "result fieldType", 32);
            settings = Objects.requireNonNull(settings, "result settings");
            if (sortOrder < 0 || fieldVersion < 0) invalid("result version");
            if (!"DRAFT".equals(draftStatus)) invalid("draftStatus");
        }
    }

    public record Attempt(
            long id,
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
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
            positive(accountId, "attempt accountId");
            positive(systemId, "attempt systemId");
            positive(tenantId, "attempt tenantId");
            positive(memberId, "attempt memberId");
            positive(sessionId, "attempt sessionId");
            positive(turnId, "attempt turnId");
            positive(policyVersionId, "attempt policyVersionId");
            positive(providerId, "attempt providerId");
            if (providerVersion < 0) invalid("attempt providerVersion");
            positive(proposalId, "attempt proposalId");
            action = token(action, "action", 16);
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
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
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
            long actorMemberId,
            String requestId,
            String traceId,
            String resultCode,
            String eventHash,
            Instant createdAt
    ) {
        public Event {
            positive(id, "event id");
            positive(accountId, "event accountId");
            positive(systemId, "event systemId");
            positive(tenantId, "event tenantId");
            positive(memberId, "event memberId");
            positive(sessionId, "event sessionId");
            positive(turnId, "event turnId");
            positive(policyVersionId, "event policyVersionId");
            positive(providerId, "event providerId");
            if (providerVersion < 0 || revision < 0) invalid("event version");
            positive(proposalId, "event proposalId");
            if (attemptId != null && attemptId <= 0) invalid("event attemptId");
            eventType = token(eventType, "eventType", 32);
            toState = Objects.requireNonNull(toState, "toState");
            positive(actorMemberId, "actorMemberId");
            requestId = token(requestId, "requestId", 64);
            traceId = token(traceId, "traceId", 64);
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
                    "AI configuration field " + field + " is invalid", failure);
        }
    }

    private static String code(String value, String field) {
        if (value == null || !value.matches("^[a-z][a-z0-9_]{1,63}$")) {
            invalid(field);
        }
        return value;
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
                "AI configuration field " + field + " is invalid");
    }
}
