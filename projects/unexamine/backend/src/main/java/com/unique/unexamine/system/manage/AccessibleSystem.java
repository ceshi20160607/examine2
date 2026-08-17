package com.unique.unexamine.system.manage;

public record AccessibleSystem(
        Long systemId,
        String systemCode,
        String systemName,
        String tenantMode,
        String status,
        Long defaultTenantId,
        String defaultTenantName) {
}
