package com.unique.examine.ai.domain;

import java.time.Instant;
import java.util.Objects;

public final class PlatformAiConversation {
    private PlatformAiConversation() { }

    public enum Scope { PLATFORM }
    public enum SessionStatus { ACTIVE, CLOSED }
    public enum Role { USER, ASSISTANT }
    public enum TurnStatus { RUNNING, SUCCEEDED, FAILED, RETRYABLE }

    public record Session(
            long id,
            Scope scope,
            long accountId,
            String titleSummary,
            SessionStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        public Session {
            positive(id, "session id");
            scope = Objects.requireNonNull(scope, "scope");
            positive(accountId, "accountId");
            titleSummary = text(titleSummary, "titleSummary", 200);
            status = Objects.requireNonNull(status, "status");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
            if (updatedAt.isBefore(createdAt)) invalid("session time");
        }
    }

    public record Message(
            long id,
            Scope scope,
            long accountId,
            long sessionId,
            long turnId,
            Role role,
            String redactedSummary,
            String contentHash,
            int contentLength,
            Instant createdAt
    ) {
        public Message {
            positive(id, "message id");
            scope = Objects.requireNonNull(scope, "scope");
            positive(accountId, "accountId");
            positive(sessionId, "sessionId");
            positive(turnId, "turnId");
            role = Objects.requireNonNull(role, "role");
            redactedSummary = text(redactedSummary, "redactedSummary", 200);
            hash(contentHash, "contentHash");
            if (contentLength < 0 || contentLength > 128_000) {
                invalid("message contentLength");
            }
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    public record Turn(
            long id,
            Scope scope,
            long accountId,
            long sessionId,
            long policyVersionId,
            long providerId,
            long providerVersion,
            long authorizationEpoch,
            String operation,
            TurnStatus status,
            String requestSummary,
            String requestHash,
            String planHash,
            String responseSummary,
            String responseHash,
            int returnedSystems,
            String resultCode,
            boolean retryable,
            int reservedTokens,
            long latencyMs,
            String requestId,
            String traceId,
            Instant createdAt,
            Instant finishedAt
    ) {
        public Turn {
            positive(id, "turn id");
            scope = Objects.requireNonNull(scope, "scope");
            positive(accountId, "accountId");
            positive(sessionId, "sessionId");
            positive(policyVersionId, "policyVersionId");
            positive(providerId, "providerId");
            if (providerVersion < 0 || authorizationEpoch <= 0
                    || returnedSystems < 0 || returnedSystems > 100
                    || reservedTokens < 0 || latencyMs < 0) {
                invalid("turn numeric state");
            }
            operation = validatedOperation(operation);
            status = Objects.requireNonNull(status, "status");
            requestSummary = text(requestSummary, "requestSummary", 200);
            hash(requestHash, "requestHash");
            if (planHash != null) hash(planHash, "planHash");
            if (responseSummary != null) {
                responseSummary = text(responseSummary, "responseSummary", 200);
            }
            if (responseHash != null) hash(responseHash, "responseHash");
            resultCode = token(resultCode, "resultCode", 64);
            requestId = token(requestId, "requestId", 128);
            traceId = token(traceId, "traceId", 128);
            Objects.requireNonNull(createdAt, "createdAt");
            if (status == TurnStatus.RUNNING) {
                if (finishedAt != null || retryable || responseHash != null) {
                    invalid("running turn state");
                }
            } else if (finishedAt == null || finishedAt.isBefore(createdAt)) {
                invalid("terminal turn state");
            }
            if (status != TurnStatus.RETRYABLE && retryable) {
                invalid("turn retry state");
            }
        }
    }

    public record Usage(
            long id,
            Scope scope,
            long accountId,
            long turnId,
            long policyVersionId,
            long providerId,
            int callCount,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            long latencyMs,
            Instant createdAt
    ) {
        public Usage {
            positive(id, "usage id");
            scope = Objects.requireNonNull(scope, "scope");
            positive(accountId, "accountId");
            positive(turnId, "turnId");
            positive(policyVersionId, "policyVersionId");
            positive(providerId, "providerId");
            if (callCount < 0 || callCount > 2 || promptTokens < 0
                    || completionTokens < 0 || totalTokens < 0 || latencyMs < 0
                    || totalTokens != Math.addExact(
                    promptTokens, completionTokens)) {
                invalid("usage totals");
            }
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    public record Evidence(
            long id,
            Scope scope,
            long accountId,
            long turnId,
            String evidenceType,
            String planHash,
            String projectionJson,
            String projectionHash,
            int returnedSystems,
            String resultCode,
            Instant createdAt
    ) {
        public Evidence {
            positive(id, "evidence id");
            scope = Objects.requireNonNull(scope, "scope");
            positive(accountId, "accountId");
            positive(turnId, "turnId");
            evidenceType = token(evidenceType, "evidenceType", 32);
            if (!java.util.Set.of(
                    "AUTHORIZED_SYSTEMS", "SWITCH_GUIDANCE", "PERSONAL_TASKS",
                    "AI_QUOTA", "SERVICE_HEALTH", "AGENT_ACTIVITY",
                    "OPERATIONS_CLARIFICATION").contains(evidenceType)) {
                invalid("evidence type");
            }
            hash(planHash, "planHash");
            projectionJson = text(projectionJson, "projectionJson", 65_535);
            hash(projectionHash, "projectionHash");
            if (returnedSystems < 0 || returnedSystems > 100) {
                invalid("evidence returnedSystems");
            }
            resultCode = token(resultCode, "resultCode", 64);
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    public record AuditEvent(
            long id,
            Scope scope,
            long accountId,
            String aggregateType,
            long aggregateId,
            String eventType,
            String resultCode,
            String requestId,
            String traceId,
            String eventHash,
            Instant createdAt
    ) {
        public AuditEvent {
            positive(id, "audit id");
            scope = Objects.requireNonNull(scope, "scope");
            positive(accountId, "accountId");
            aggregateType = token(aggregateType, "aggregateType", 32);
            positive(aggregateId, "aggregateId");
            eventType = token(eventType, "eventType", 32);
            resultCode = token(resultCode, "resultCode", 64);
            requestId = token(requestId, "requestId", 128);
            traceId = token(traceId, "traceId", 128);
            hash(eventHash, "eventHash");
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    public record QuotaReservation(
            long accountId,
            long policyVersionId,
            Instant periodStart,
            int requestLimit,
            int tokenLimit,
            int concurrencyLimit,
            int reservedTokens
    ) {
        public QuotaReservation {
            positive(accountId, "accountId");
            positive(policyVersionId, "policyVersionId");
            Objects.requireNonNull(periodStart, "periodStart");
            if (requestLimit < 1 || tokenLimit < 1 || concurrencyLimit < 1
                    || reservedTokens < 1 || reservedTokens > tokenLimit) {
                invalid("quota reservation");
            }
        }
    }

    private static String validatedOperation(String value) {
        value = token(value, "operation", 32);
        if (!java.util.Set.of(
                "UNRESOLVED", "AUTHORIZED_SYSTEMS_QUERY",
                "SYSTEM_SWITCH_GUIDANCE", "PLATFORM_TASK_DRAFT",
                "PLATFORM_OPERATIONS_QUERY").contains(value)) {
            invalid("operation");
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
        throw new IllegalArgumentException("Platform AI " + field + " is invalid");
    }
}
