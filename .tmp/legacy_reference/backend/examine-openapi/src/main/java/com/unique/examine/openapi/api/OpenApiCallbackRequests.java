package com.unique.examine.openapi.api;

import java.util.Set;

public final class OpenApiCallbackRequests {
    private OpenApiCallbackRequests() { }

    public record Create(
            String name,
            String endpoint,
            Set<String> eventTypes,
            String secretRef,
            Integer maxAttempts,
            Integer baseBackoffSeconds
    ) { }

    public record ReplaceConfiguration(
            String name,
            String endpoint,
            Set<String> eventTypes,
            String secretRef,
            Integer maxAttempts,
            Integer baseBackoffSeconds,
            Long version
    ) { }

    public record RotateSigningSecret(String secretRef, Long version) { }

    public record ChangeStatus(Long version, String reason) { }
}
