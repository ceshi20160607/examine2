package com.unique.unexamine.core.gateway;

import java.util.List;

public record ApplicationConfig(
        String appCode,
        String appName,
        String owner,
        String type,
        List<String> scopes,
        String secretRef,
        String callbackUrl,
        String rateLimit,
        String status,
        List<ApplicationAuthorization> authorizations,
        String updatedAt
) {
}