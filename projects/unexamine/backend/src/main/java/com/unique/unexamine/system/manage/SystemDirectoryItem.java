package com.unique.unexamine.system.manage;

public record SystemDirectoryItem(
        Long systemId,
        String systemCode,
        String systemName,
        String tenantMode,
        Long defaultTenantId,
        String defaultTenantName,
        boolean accessible,
        Long accessRequestId,
        String accessRequestStatus) {
}
