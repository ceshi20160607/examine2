package com.unique.examine.openapi.domain;

import java.time.Instant;
import java.util.Objects;

public record OpenApiCallbackSubscription(
        long id,
        long systemId,
        long tenantId,
        long applicationId,
        String name,
        Status status,
        int currentConfigVersion,
        Instant createdAt,
        long createdBy,
        Instant updatedAt,
        long updatedBy,
        long version
) {
    public OpenApiCallbackSubscription {
        if (id <= 0 || systemId <= 0 || tenantId <= 0 || applicationId <= 0
                || createdBy <= 0 || updatedBy <= 0 || currentConfigVersion < 1
                || version < 0) {
            throw new IllegalArgumentException("OpenAPI callback subscription identity is invalid");
        }
        if (name == null || name.isBlank() || name.length() > 160) {
            throw new IllegalArgumentException("OpenAPI callback subscription name is invalid");
        }
        name = name.strip();
        status = Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public enum Status { ACTIVE, DISABLED }
}
