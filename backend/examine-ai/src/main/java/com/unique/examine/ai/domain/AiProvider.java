package com.unique.examine.ai.domain;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

public record AiProvider(
        long id,
        long systemId,
        long tenantId,
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
    public AiProvider {
        if (id <= 0 || systemId <= 0 || tenantId <= 0
                || createdBy <= 0 || updatedBy <= 0 || version < 0) {
            throw new IllegalArgumentException("AI provider ids are invalid");
        }
        code = code(code, "provider code");
        name = text(name, "provider name", 160);
        baseUrl = baseUrl(baseUrl);
        model = text(model, "provider model", 128);
        secretRef = secretRef(secretRef);
        if (timeoutSeconds < 1 || timeoutSeconds > 30) {
            throw new IllegalArgumentException(
                    "AI provider timeout must be between 1 and 30 seconds");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public AiProvider revise(
            String revisedName,
            String revisedBaseUrl,
            String revisedModel,
            String revisedSecretRef,
            int revisedTimeoutSeconds,
            boolean revisedEnabled,
            Instant now,
            long actorId
    ) {
        return new AiProvider(
                id, systemId, tenantId, code, revisedName, revisedBaseUrl,
                revisedModel, revisedSecretRef, revisedTimeoutSeconds,
                revisedEnabled, version + 1, createdAt, createdBy,
                now, actorId);
    }

    public static String secretRef(String value) {
        value = text(value, "provider secretRef", 512);
        if (!value.matches("^[A-Za-z][A-Za-z0-9+.-]{1,31}://[^\\s]+$")) {
            throw new IllegalArgumentException(
                    "AI provider credentials must use a SecretRef");
        }
        return value;
    }

    private static String baseUrl(String value) {
        value = text(value, "provider baseUrl", 1024);
        final URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("AI provider baseUrl is invalid", failure);
        }
        var scheme = uri.getScheme();
        var host = uri.getHost();
        var localHttp = "http".equalsIgnoreCase(scheme)
                && ("localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host));
        if (host == null
                || !("https".equalsIgnoreCase(scheme) || localHttp)
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "AI provider baseUrl must be HTTPS or loopback HTTP");
        }
        return value.endsWith("/")
                ? value.substring(0, value.length() - 1) : value;
    }

    private static String code(String value, String field) {
        value = text(value, field, 64);
        if (!value.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    private static String text(String value, String field, int maximum) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        value = value.strip();
        if (value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return value;
    }
}
