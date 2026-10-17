package com.unique.unexamine.system.manage;

/** 系统和工作空间专属登录地址的公开展示信息；不授予任何业务权限。 */
public record SystemAccessEntryView(
        Long systemId,
        String systemCode,
        String systemName,
        String tenantMode,
        Long tenantId,
        String tenantCode,
        String tenantName,
        String loginTitle,
        String loginSubtitle) {
}
