package com.unique.examine.plat.manage.tenant;

import java.time.LocalDateTime;

/**
 * Tenant API models.
 */
public final class TenantModels {

    private TenantModels() {
    }

    public record TenantSaveRequest(String tenantCode, String tenantName, String domain, Integer status) {
    }

    public record TenantVO(String tenantId, String systemId, String tenantCode, String tenantName,
                           String domain, Integer status, LocalDateTime updatedAt) {
    }
}
