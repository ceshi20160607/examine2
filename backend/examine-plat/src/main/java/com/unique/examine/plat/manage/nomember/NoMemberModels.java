package com.unique.examine.plat.manage.nomember;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * No-member access request API models.
 */
public final class NoMemberModels {

    private NoMemberModels() {
    }

    public record NoMemberAccessRequestCreateRequest(String identityProvider, String externalUserId,
                                                     String tenantId, String requestRole, String requestReason,
                                                     String idempotencyKey) {
    }

    public record NoMemberAccessRequestQuery(String status, String identityProvider, String externalUserId,
                                             String tenantId, String keyword) {
    }

    public record NoMemberApproveRequest(String approverId, String accountId, String systemMemberId,
                                         List<String> roleIds, Map<String, Object> dataScope,
                                         String approveComment, String idempotencyKey) {
    }

    public record NoMemberRejectRequest(String approverId, String rejectReason, String idempotencyKey) {
    }

    public record NoMemberAccessRequestVO(String requestId, String status, String identityProvider,
                                          String externalUserId, String targetSystemId, String tenantId,
                                          String requestRole, String approverId, String approveResult,
                                          List<String> roleIds, Map<String, Object> dataScope,
                                          String rejectReason, String traceId, String disabledReason,
                                          boolean businessAccessAllowed, String accountMemberBindingId,
                                          String systemMemberId, LocalDateTime createdAt,
                                          LocalDateTime updatedAt) {
    }
}
