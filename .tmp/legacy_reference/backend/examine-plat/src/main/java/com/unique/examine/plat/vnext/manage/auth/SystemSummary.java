package com.unique.examine.plat.vnext.manage.auth;

import java.util.List;

public record SystemSummary(
        String id,
        String code,
        String name,
        String status,
        String memberStatus,
        String defaultTenantId,
        List<String> roleNames,
        String recentEnteredAt
) {
    public SystemSummary {
        roleNames = List.copyOf(roleNames);
    }
}
