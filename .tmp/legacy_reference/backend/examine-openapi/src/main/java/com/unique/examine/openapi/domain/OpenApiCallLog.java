package com.unique.examine.openapi.domain;

import java.time.Instant;
import java.util.Objects;

public record OpenApiCallLog(
        long id,
        Long applicationId,
        String appKeyHash,
        Integer credentialVersion,
        String routeTemplate,
        String requestMethod,
        ResultCategory resultCategory,
        int httpStatus,
        long latencyMs,
        String requestId,
        String traceId,
        String observedIp,
        Instant createdAt
) {
    public OpenApiCallLog {
        if (id <= 0 || applicationId != null && applicationId <= 0) {
            throw new IllegalArgumentException("OpenAPI call-log ids are invalid");
        }
        if (appKeyHash == null || !appKeyHash.matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("OpenAPI app-key hash is invalid");
        }
        if (credentialVersion != null && credentialVersion < 1) {
            throw new IllegalArgumentException("OpenAPI credential version is invalid");
        }
        routeTemplate = required(routeTemplate, "routeTemplate", 255);
        requestMethod = required(requestMethod, "requestMethod", 8);
        resultCategory = Objects.requireNonNull(resultCategory, "resultCategory");
        if (httpStatus < 100 || httpStatus > 599 || latencyMs < 0 || latencyMs > 86_400_000) {
            throw new IllegalArgumentException("OpenAPI call-log result metadata is invalid");
        }
        requestId = required(requestId, "requestId", 64);
        traceId = required(traceId, "traceId", 64);
        observedIp = required(observedIp, "observedIp", 64);
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public enum ResultCategory {
        SUCCESS,
        AUTH_REJECTED,
        SIGNATURE_REJECTED,
        REPLAY_REJECTED,
        SCOPE_REJECTED,
        PERMISSION_REJECTED,
        IP_REJECTED,
        RATE_REJECTED,
        FAILED
    }

    private static String required(String value, String field, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException("OpenAPI call-log " + field + " is invalid");
        }
        return value;
    }
}
