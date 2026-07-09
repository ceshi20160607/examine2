package com.unique.unexamine.core.gateway;

public record ApplicationAuthorization(
        String authorizationId,
        String targetType,
        String targetCode,
        String scope,
        String status,
        String createdAt
) {
}