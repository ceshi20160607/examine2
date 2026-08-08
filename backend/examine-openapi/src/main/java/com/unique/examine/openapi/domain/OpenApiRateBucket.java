package com.unique.examine.openapi.domain;

import java.time.Instant;
import java.util.Objects;

public record OpenApiRateBucket(
        long applicationId,
        Instant windowStart,
        int requestCount,
        long version
) {
    public OpenApiRateBucket {
        if (applicationId <= 0 || requestCount < 0 || version < 0) {
            throw new IllegalArgumentException("OpenAPI rate bucket is invalid");
        }
        Objects.requireNonNull(windowStart, "windowStart");
    }
}
