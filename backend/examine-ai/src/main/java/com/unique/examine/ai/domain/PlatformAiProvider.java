package com.unique.examine.ai.domain;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/** Provider configuration owned by the platform, never by a system tenant. */
public record PlatformAiProvider(
        long id,
        String code,
        String name,
        String baseUrl,
        String model,
        String secretRef,
        int timeoutSeconds,
        boolean enabled,
        long version,
        Instant createdAt,
        long createdBy,
        Instant updatedAt,
        long updatedBy
) {
    public PlatformAiProvider {
        if (id <= 0 || createdBy <= 0 || updatedBy <= 0 || version < 0) {
            throw new IllegalArgumentException("Platform AI provider ids are invalid");
        }
        code = code(code);
        name = text(name, "name", 160);
        baseUrl = baseUrl(baseUrl);
        model = text(model, "model", 128);
        secretRef = AiProvider.secretRef(secretRef);
        if (timeoutSeconds < 1 || timeoutSeconds > 30) {
            throw new IllegalArgumentException(
                    "Platform AI provider timeout must be within 1..30 seconds");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Platform AI provider time is invalid");
        }
    }

    public PlatformAiProvider revise(
            String revisedName, String revisedBaseUrl, String revisedModel,
            String revisedSecretRef, int revisedTimeoutSeconds,
            boolean revisedEnabled, Instant now, long actorId) {
        return new PlatformAiProvider(
                id, code, revisedName, revisedBaseUrl, revisedModel,
                revisedSecretRef, revisedTimeoutSeconds, revisedEnabled,
                version + 1, createdAt, createdBy, now, actorId);
    }

    private static String code(String value) {
        value = text(value, "code", 64);
        if (!value.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw new IllegalArgumentException("Platform AI provider code is invalid");
        }
        return value;
    }

    private static String baseUrl(String value) {
        value = text(value, "baseUrl", 1024);
        final URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(
                    "Platform AI provider baseUrl is invalid", failure);
        }
        var localHttp = "http".equalsIgnoreCase(uri.getScheme())
                && ("localhost".equalsIgnoreCase(uri.getHost())
                || "127.0.0.1".equals(uri.getHost())
                || "::1".equals(uri.getHost()));
        if (uri.getHost() == null
                || !("https".equalsIgnoreCase(uri.getScheme()) || localHttp)
                || uri.getUserInfo() != null || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "Platform AI provider baseUrl must be HTTPS or loopback HTTP");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String text(String value, String field, int maximum) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Platform AI provider " + field + " is required");
        }
        value = value.strip();
        if (value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException(
                    "Platform AI provider " + field + " is too long");
        }
        return value;
    }
}
