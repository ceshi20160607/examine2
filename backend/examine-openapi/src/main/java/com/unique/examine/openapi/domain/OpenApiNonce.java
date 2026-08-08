package com.unique.examine.openapi.domain;

import java.time.Instant;
import java.util.Objects;

public record OpenApiNonce(
        long applicationId,
        int credentialVersion,
        String nonce,
        Instant expiresAt,
        Instant createdAt
) {
    public OpenApiNonce {
        if (applicationId <= 0 || credentialVersion < 1) {
            throw new IllegalArgumentException("OpenAPI nonce identity is invalid");
        }
        if (nonce == null || !nonce.matches("^[A-Za-z0-9._~-]{16,128}$")) {
            throw new IllegalArgumentException("OpenAPI nonce is invalid");
        }
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(createdAt, "createdAt");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("OpenAPI nonce expiry is invalid");
        }
    }
}
