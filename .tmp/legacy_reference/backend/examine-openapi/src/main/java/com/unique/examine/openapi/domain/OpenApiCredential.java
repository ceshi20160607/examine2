package com.unique.examine.openapi.domain;

import java.time.Instant;
import java.util.Objects;

public record OpenApiCredential(
        long id,
        long applicationId,
        int credentialVersion,
        String secretRef,
        Status status,
        Instant activatedAt,
        Instant revokedAt,
        Instant createdAt,
        long createdBy
) {
    public OpenApiCredential {
        if (id <= 0 || applicationId <= 0 || credentialVersion < 1 || createdBy <= 0) {
            throw new IllegalArgumentException("OpenAPI credential ids and version must be positive");
        }
        if (secretRef == null || secretRef.isBlank() || secretRef.length() > 512) {
            throw new IllegalArgumentException("OpenAPI secretRef is invalid");
        }
        secretRef = secretRef.strip();
        status = Objects.requireNonNull(status, "status");
        Objects.requireNonNull(activatedAt, "activatedAt");
        Objects.requireNonNull(createdAt, "createdAt");
        if (status == Status.ACTIVE && revokedAt != null) {
            throw new IllegalArgumentException("Active OpenAPI credential cannot be revoked");
        }
        if (status == Status.REVOKED && revokedAt == null) {
            throw new IllegalArgumentException("Revoked OpenAPI credential requires revokedAt");
        }
    }

    public enum Status {
        ACTIVE,
        REVOKED
    }
}
