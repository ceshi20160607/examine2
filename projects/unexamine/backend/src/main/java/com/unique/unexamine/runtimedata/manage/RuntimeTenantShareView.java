package com.unique.unexamine.runtimedata.manage;

import java.time.LocalDateTime;
import java.util.List;

public record RuntimeTenantShareView(
        Long id,
        Long sourceTenantId,
        String sourceTenantName,
        Long targetTenantId,
        String targetTenantName,
        Long recordId,
        String moduleCode,
        List<String> allowedActions,
        String status,
        LocalDateTime effectiveAt,
        LocalDateTime expiresAt,
        LocalDateTime revokedAt,
        Long grantedByMemberId,
        Integer version) {
}
