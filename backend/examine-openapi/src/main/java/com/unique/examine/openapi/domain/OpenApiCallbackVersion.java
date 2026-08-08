package com.unique.examine.openapi.domain;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record OpenApiCallbackVersion(
        long id,
        long subscriptionId,
        int configVersion,
        URI endpoint,
        Set<String> eventTypes,
        String secretRef,
        int signingSecretVersion,
        int maxAttempts,
        int baseBackoffSeconds,
        Status status,
        Instant activatedAt,
        Instant retiredAt,
        Instant createdAt,
        long createdBy
) {
    public OpenApiCallbackVersion {
        if (id <= 0 || subscriptionId <= 0 || configVersion < 1
                || signingSecretVersion < 1 || maxAttempts < 1 || maxAttempts > 10
                || baseBackoffSeconds < 1 || baseBackoffSeconds > 3600 || createdBy <= 0) {
            throw new IllegalArgumentException("OpenAPI callback version metadata is invalid");
        }
        endpoint = Objects.requireNonNull(endpoint, "endpoint");
        eventTypes = Set.copyOf(Objects.requireNonNull(eventTypes, "eventTypes"));
        if (eventTypes.isEmpty() || eventTypes.size() > 32
                || eventTypes.stream().anyMatch(value -> value == null
                || !value.matches("^[A-Z][A-Z0-9_]{1,63}$"))) {
            throw new IllegalArgumentException("OpenAPI callback event selection is invalid");
        }
        if (secretRef == null || secretRef.isBlank() || secretRef.length() > 512) {
            throw new IllegalArgumentException("OpenAPI callback SecretRef is invalid");
        }
        secretRef = secretRef.strip();
        status = Objects.requireNonNull(status, "status");
        Objects.requireNonNull(activatedAt, "activatedAt");
        Objects.requireNonNull(createdAt, "createdAt");
        if (status == Status.ACTIVE && retiredAt != null
                || status == Status.RETIRED && retiredAt == null) {
            throw new IllegalArgumentException("OpenAPI callback version lifecycle is invalid");
        }
    }

    public enum Status { ACTIVE, RETIRED }
}
