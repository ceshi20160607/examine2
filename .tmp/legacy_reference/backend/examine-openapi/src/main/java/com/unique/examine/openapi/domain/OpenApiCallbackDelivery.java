package com.unique.examine.openapi.domain;

import java.time.Instant;
import java.util.Objects;

public record OpenApiCallbackDelivery(
        long id,
        long systemId,
        long tenantId,
        long applicationId,
        long subscriptionId,
        long callbackVersionId,
        String eventId,
        String eventType,
        String payloadJson,
        String payloadHash,
        Status status,
        int attemptCount,
        Integer lastHttpStatus,
        String failureCode,
        String requestId,
        String traceId,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        long version
) {
    public OpenApiCallbackDelivery {
        if (id <= 0 || systemId <= 0 || tenantId <= 0 || applicationId <= 0
                || subscriptionId <= 0 || callbackVersionId <= 0
                || attemptCount < 0 || attemptCount > 10 || version < 0) {
            throw new IllegalArgumentException("OpenAPI callback delivery identity is invalid");
        }
        eventId = required(eventId, "eventId", 160);
        eventType = required(eventType, "eventType", 64);
        payloadJson = required(payloadJson, "payloadJson", 65_536);
        if (payloadHash == null || !payloadHash.matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("OpenAPI callback payload hash is invalid");
        }
        status = Objects.requireNonNull(status, "status");
        if (lastHttpStatus != null && (lastHttpStatus < 100 || lastHttpStatus > 599)) {
            throw new IllegalArgumentException("OpenAPI callback HTTP status is invalid");
        }
        if (failureCode != null && !failureCode.matches("^[A-Z][A-Z0-9_]{1,63}$")) {
            throw new IllegalArgumentException("OpenAPI callback failure code is invalid");
        }
        requestId = required(requestId, "requestId", 64);
        traceId = required(traceId, "traceId", 64);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (status.terminal() != (completedAt != null)) {
            throw new IllegalArgumentException("OpenAPI callback completion state is invalid");
        }
    }

    public enum Status {
        PENDING, RETRYING, SUCCEEDED, FAILED;
        public boolean terminal() { return this == SUCCEEDED || this == FAILED; }
    }

    private static String required(String value, String field, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException("OpenAPI callback " + field + " is invalid");
        }
        return value;
    }
}
