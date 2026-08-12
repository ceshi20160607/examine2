package com.unique.examine.plat.vnext.manage.auth;

public record TenantSummary(
        String id,
        String code,
        String name,
        String status,
        boolean isDefault
) {
}
