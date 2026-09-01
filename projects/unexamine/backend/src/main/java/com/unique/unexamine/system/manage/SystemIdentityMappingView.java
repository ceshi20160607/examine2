package com.unique.unexamine.system.manage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
        String confirmedAt,
        LocalDateTime updatedAt) {
}
