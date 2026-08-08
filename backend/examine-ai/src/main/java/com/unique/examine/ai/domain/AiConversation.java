package com.unique.examine.ai.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class AiConversation {
    private AiConversation() {
    }

    public enum SessionStatus {
        ACTIVE,
        CLOSED
    }

    public enum TurnStatus {
        RUNNING,
        SUCCEEDED,
        FAILED,
        RETRYABLE
    }

    public enum Role {
        USER,
        ASSISTANT
    }

    public record Session(
            long id,
            long systemId,
            long tenantId,
            long memberId,
            long policyVersionId,
            long providerId,
            long providerVersion,
            String model,
            String promptVersion,
            long authorizationEpoch,
            SessionStatus status,
            String titleSummary,
            Instant createdAt,
            Instant updatedAt
    ) {
        public Session {
            if (id <= 0 || systemId <= 0 || tenantId <= 0 || memberId <= 0
                    || policyVersionId <= 0 || providerId <= 0
                    || providerVersion < 0 || authorizationEpoch <= 0) {
                throw new IllegalArgumentException("AI session ids are invalid");
            }
            model = text(model, "model", 128);
            promptVersion = text(promptVersion, "promptVersion", 64);
            status = Objects.requireNonNull(status, "status");
            titleSummary = text(titleSummary, "titleSummary", 200);
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }

    public record Message(
            long id,
            long systemId,
            long tenantId,
            long sessionId,
            Long turnId,
            Role role,
            String redactedSummary,
            String contentHash,
            int characterCount,
            Instant createdAt
    ) {
        public Message {
            if (id <= 0 || systemId <= 0 || tenantId <= 0 || sessionId <= 0
                    || turnId != null && turnId <= 0 || characterCount < 0) {
                throw new IllegalArgumentException("AI message identity is invalid");
            }
            role = Objects.requireNonNull(role, "role");
            redactedSummary = text(redactedSummary, "message summary", 500);
            hash(contentHash, "message content hash");
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    public record Turn(
            long id,
            long systemId,
            long tenantId,
            long sessionId,
            long policyVersionId,
            long providerId,
            long providerVersion,
            long authorizationEpoch,
            TurnStatus status,
            String requestSummary,
            String requestHash,
            String planHash,
            String responseSummary,
            String responseHash,
            int returnedRows,
            String resultCode,
            boolean retryable,
            long latencyMs,
            String requestId,
            String traceId,
            Instant createdAt,
            Instant finishedAt
    ) {
        public Turn {
            if (id <= 0 || systemId <= 0 || tenantId <= 0 || sessionId <= 0
                    || policyVersionId <= 0 || providerId <= 0
                    || providerVersion < 0 || authorizationEpoch <= 0
                    || returnedRows < 0 || latencyMs < 0) {
                throw new IllegalArgumentException("AI turn identity is invalid");
            }
            status = Objects.requireNonNull(status, "status");
            requestSummary = text(requestSummary, "request summary", 500);
            hash(requestHash, "request hash");
            nullableHash(planHash, "plan hash");
            if (responseSummary != null) {
                responseSummary = text(responseSummary, "response summary", 500);
            }
            nullableHash(responseHash, "response hash");
            resultCode = text(resultCode, "result code", 64);
            requestId = text(requestId, "requestId", 64);
            traceId = text(traceId, "traceId", 64);
            Objects.requireNonNull(createdAt, "createdAt");
            if (status != TurnStatus.RUNNING) {
                Objects.requireNonNull(finishedAt, "finishedAt");
            }
        }
    }

    public record ToolCall(
            long id,
            long systemId,
            long tenantId,
            long turnId,
            String toolName,
            TurnStatus status,
            String requestHash,
            String responseHash,
            int resultCount,
            long latencyMs,
            String resultCode,
            Instant createdAt
    ) {
        public ToolCall {
            if (id <= 0 || systemId <= 0 || tenantId <= 0 || turnId <= 0
                    || resultCount < 0 || latencyMs < 0) {
                throw new IllegalArgumentException("AI tool call identity is invalid");
            }
            toolName = text(toolName, "tool name", 64);
            status = Objects.requireNonNull(status, "status");
            hash(requestHash, "tool request hash");
            nullableHash(responseHash, "tool response hash");
            resultCode = text(resultCode, "tool result code", 64);
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    public record Usage(
            long id,
            long systemId,
            long tenantId,
            long turnId,
            int providerCalls,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            long providerLatencyMs,
            Instant createdAt
    ) {
        public Usage {
            if (id <= 0 || systemId <= 0 || tenantId <= 0 || turnId <= 0
                    || providerCalls < 0 || providerCalls > 8
                    || promptTokens < 0 || completionTokens < 0 || totalTokens < 0
                    || totalTokens != promptTokens + completionTokens
                    || providerLatencyMs < 0) {
                throw new IllegalArgumentException("AI usage metrics are invalid");
            }
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    public record Detail(
            Session session,
            List<Message> messages,
            List<Turn> turns
    ) {
        public Detail {
            Objects.requireNonNull(session, "session");
            messages = List.copyOf(messages);
            turns = List.copyOf(turns);
        }
    }

    private static void hash(String value, String field) {
        if (value == null || !value.matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("AI " + field + " is invalid");
        }
    }

    private static void nullableHash(String value, String field) {
        if (value != null) {
            hash(value, field);
        }
    }

    private static String text(String value, String field, int maximum) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AI " + field + " is required");
        }
        value = value.strip();
        if (value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException("AI " + field + " is too long");
        }
        return value;
    }
}
