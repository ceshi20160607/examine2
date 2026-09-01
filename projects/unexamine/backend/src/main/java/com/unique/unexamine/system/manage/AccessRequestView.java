package com.unique.unexamine.system.manage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AccessRequestView(
        Long id,
        Long systemId,
        String systemName,
        Long tenantId,
        String tenantName,
        Long accountId,
        String accountDisplayName,
        String identityProvider,
        String externalUserId,
        String requestReason,
        String requestedRole,
        String status,
        Long decidedByMemberId,
        String decisionComment,
        List<Long> approvedRoleIds,
        Map<String, Object> approvedDataScope,
        String requestTraceId,
        String decisionTraceId,
        LocalDateTime createdAt,
        LocalDateTime decidedAt,
        Integer version) {
}
