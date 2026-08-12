package com.unique.examine.openapi.api;

import java.util.List;
import java.util.Set;

public final class OpenApiRequests {
    private OpenApiRequests() {
    }

    public record CreateApplication(
            String tenantId,
            String serviceMemberId,
            String name,
            Set<String> scopes,
            List<String> ipAllowlist,
            Integer rateLimitPerMinute,
            String secretRef
    ) {
    }

    public record UpdatePolicy(
            Set<String> scopes,
            List<String> ipAllowlist,
            Integer rateLimitPerMinute,
            Long version
    ) {
    }

    public record RotateSecretRef(String secretRef, Long version) {
    }

    public record ChangeStatus(Long version, String reason) {
    }
}
