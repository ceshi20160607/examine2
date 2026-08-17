package com.unique.unexamine.system.manage;

public record SystemSettingsView(
        Long systemId,
        String code,
        String name,
        String tenantMode,
        String status,
        Integer version) {
}
