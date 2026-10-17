package com.unique.unexamine.system.manage;

/** 系统后台基础设置；ownerAccountId 为当前所属人账号，而非不可变的最初创建人。 */
public record SystemSettingsView(
        Long systemId,
        String code,
        String name,
        String tenantMode,
        Long ownerAccountId,
        String status,
        Integer version) {
}
