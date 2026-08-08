package com.unique.examine.plat.manage.vo;

public record TenantSummaryVo(
        String id,
        String code,
        String name,
        String status,
        boolean isDefault
) {
}
