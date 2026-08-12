package com.unique.examine.plat.manage.vo;

public record SystemSummaryVo(
        String id,
        String code,
        String name,
        String status,
        String memberStatus,
        String defaultTenantId
) {
}
