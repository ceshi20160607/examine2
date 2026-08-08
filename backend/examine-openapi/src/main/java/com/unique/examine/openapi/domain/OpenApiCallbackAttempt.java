package com.unique.examine.openapi.domain;

import java.time.Instant;
import java.util.Objects;

public record OpenApiCallbackAttempt(
        long id,
        long deliveryId,
        int attemptNo,
        Outcome outcome,
        Integer httpStatus,
        long durationMs,
        String failureCode,
        Instant startedAt,
        Instant completedAt
) {
    public OpenApiCallbackAttempt {
        if (id <= 0 || deliveryId <= 0 || attemptNo < 1 || attemptNo > 10
                || durationMs < 0 || durationMs > 86_400_000) {
            throw new IllegalArgumentException("OpenAPI callback attempt metadata is invalid");
        }
        outcome = Objects.requireNonNull(outcome, "outcome");
        if (httpStatus != null && (httpStatus < 100 || httpStatus > 599)) {
            throw new IllegalArgumentException("OpenAPI callback attempt HTTP status is invalid");
        }
        if (failureCode != null && !failureCode.matches("^[A-Z][A-Z0-9_]{1,63}$")) {
            throw new IllegalArgumentException("OpenAPI callback attempt failure code is invalid");
        }
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(completedAt, "completedAt");
    }

    public enum Outcome { SUCCEEDED, RETRYABLE_FAILURE, TERMINAL_FAILURE }
}
