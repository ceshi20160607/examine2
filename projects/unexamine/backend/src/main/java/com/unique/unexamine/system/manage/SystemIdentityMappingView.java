package com.unique.unexamine.system.manage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 系统继承平台身份源的配置；confirmTime 和 updateTime 分别表示同步确认与配置更新。 */
public record SystemIdentityMappingView(
        Long settingId,
        Integer version,
        Long systemId,
        Long tenantId,
        Long providerId,
        Long providerVersionId,
        String providerCode,
        String providerName,
        String tenantDomain,
        Map<String, Long> departmentMappings,
        List<String> matchOrder,
        String jitPolicy,
        IdentityMappingPreflightReport latestPreflight,
        Long confirmedJobId,
        String confirmTime,
        LocalDateTime updateTime) {
}
