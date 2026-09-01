package com.unique.unexamine.system.manage;

import java.util.List;
import java.util.Map;

public record IdentityMappingPreflightReport(
        String preflightId,
        Long providerId,
        Long providerVersionId,
        String providerCode,
        String tenantDomain,
        String createdAt,
        Map<String, Long> summary,
        List<IdentityMappingPreflightItem> items) {
}
