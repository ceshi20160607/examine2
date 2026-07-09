package com.unique.unexamine.core.gateway;

public record ApplicationAuthorizationRequest(
        String targetType,
        String targetCode,
        String scope
) {
}