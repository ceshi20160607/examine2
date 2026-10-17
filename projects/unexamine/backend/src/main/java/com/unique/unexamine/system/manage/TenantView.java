package com.unique.unexamine.system.manage;

public record TenantView(
        Long id,
        String code,
        String name,
        String loginTitle,
        String loginSubtitle,
        boolean main,
        String status,
        boolean current,
        Integer version) {
}
