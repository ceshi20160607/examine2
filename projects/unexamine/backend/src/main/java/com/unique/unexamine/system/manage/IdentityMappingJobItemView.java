package com.unique.unexamine.system.manage;

public record IdentityMappingJobItemView(
        int rowNumber,
        String externalUserId,
        String status,
        String action,
        Long accountId,
        Long systemMemberId,
        Long tenantMemberId,
        Long accessRequestId,
        String errorCode,
        String message) {
}
