package com.unique.examine.openapi.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record OpenApiApplication(
        long id,
        long systemId,
        long tenantId,
        long serviceMemberId,
        String appKey,
        String name,
        Status status,
        Set<String> scopes,
        List<String> ipAllowlist,
        int rateLimitPerMinute,
        int currentCredentialVersion,
        Instant createdAt,
        long createdBy,
        Instant updatedAt,
        long updatedBy,
        long version
) {
    public OpenApiApplication {
        if (id <= 0 || systemId <= 0 || tenantId <= 0 || serviceMemberId <= 0
                || createdBy <= 0 || updatedBy <= 0) {
            throw new IllegalArgumentException("OpenAPI application ids must be positive");
        }
        appKey = required(appKey, "appKey", 96);
        name = required(name, "name", 160);
        status = Objects.requireNonNull(status, "status");
        scopes = Set.copyOf(Objects.requireNonNull(scopes, "scopes"));
        ipAllowlist = List.copyOf(Objects.requireNonNull(ipAllowlist, "ipAllowlist"));
        if (scopes.isEmpty() || scopes.size() > 64 || ipAllowlist.size() > 128) {
            throw new IllegalArgumentException("OpenAPI policy cardinality is invalid");
        }
        if (scopes.stream().anyMatch(value -> value == null
                || !value.matches("^[a-z][a-z0-9]*(?:[._:-][a-z0-9]+)*$"))) {
            throw new IllegalArgumentException("OpenAPI application scopes are invalid");
        }
        if (rateLimitPerMinute < 1 || rateLimitPerMinute > 60_000) {
            throw new IllegalArgumentException("OpenAPI rate limit must be between 1 and 60000");
        }
        if (currentCredentialVersion < 1 || version < 0) {
            throw new IllegalArgumentException("OpenAPI application versions are invalid");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public enum Status {
        ACTIVE,
        DISABLED
    }

    private static String required(String value, String field, int maximum) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OpenAPI " + field + " is required");
        }
        value = value.strip();
        if (value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException("OpenAPI " + field + " is too long");
        }
        return value;
    }
}
