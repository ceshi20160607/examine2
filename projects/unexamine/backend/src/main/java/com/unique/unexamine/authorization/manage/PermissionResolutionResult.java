package com.unique.unexamine.authorization.manage;

public record PermissionResolutionResult(
        ResolvedPermissions permissions,
        long epoch,
        String source,
        String cacheKey) {
}
