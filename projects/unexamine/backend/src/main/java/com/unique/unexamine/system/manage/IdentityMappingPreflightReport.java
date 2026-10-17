package com.unique.unexamine.system.manage;

import java.util.List;
import java.util.Map;

/** 身份映射预检快照；createTime 用于确认草稿预检是否仍有效。 */
public record IdentityMappingPreflightReport(
        String preflightId,
        Long providerId,
        Long providerVersionId,
        String providerCode,
        String tenantDomain,
        String createTime,
        Map<String, Long> summary,
        List<IdentityMappingPreflightItem> items) {
}
