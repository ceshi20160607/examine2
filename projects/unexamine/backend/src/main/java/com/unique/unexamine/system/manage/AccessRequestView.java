package com.unique.unexamine.system.manage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 系统访问申请；createTime 和 decideTime 对应提交与审批决定。 */
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
        LocalDateTime createTime,
        LocalDateTime decideTime,
        Integer version) {
}
