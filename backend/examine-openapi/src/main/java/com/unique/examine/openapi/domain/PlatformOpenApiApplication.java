package com.unique.examine.openapi.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record PlatformOpenApiApplication(
        long id, long serviceAccountId, String appKey, String name, Status status,
        Set<String> scopes, List<String> ipAllowlist, int rateLimitPerMinute,
        int currentCredentialVersion, Instant createdAt, long createdBy,
        Instant updatedAt, long updatedBy, long version) {
    public PlatformOpenApiApplication {
        if (id <= 0 || serviceAccountId <= 0 || createdBy <= 0 || updatedBy <= 0)
            throw new IllegalArgumentException("Platform OpenAPI ids are invalid");
        if (appKey == null || !appKey.matches("^[A-Za-z0-9_-]{16,96}$"))
            throw new IllegalArgumentException("Platform OpenAPI app key is invalid");
        if (name == null || name.isBlank() || name.strip().length() > 160)
            throw new IllegalArgumentException("Platform OpenAPI name is invalid");
        name = name.strip();
        status = Objects.requireNonNull(status);
        scopes = Set.copyOf(scopes);
        ipAllowlist = List.copyOf(ipAllowlist);
        if (scopes.isEmpty() || scopes.size() > 64 || scopes.stream().anyMatch(v -> v == null
                || !v.matches("^[a-z][a-z0-9]*(?:[._:-][a-z0-9]+)*$")))
            throw new IllegalArgumentException("Platform OpenAPI scopes are invalid");
        if (ipAllowlist.size() > 128 || rateLimitPerMinute < 1 || rateLimitPerMinute > 60000
                || currentCredentialVersion < 1 || version < 0)
            throw new IllegalArgumentException("Platform OpenAPI policy is invalid");
        Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt);
    }
    public enum Status { ACTIVE, DISABLED }
}
