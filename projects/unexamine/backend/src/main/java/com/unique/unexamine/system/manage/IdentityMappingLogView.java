package com.unique.unexamine.system.manage;

import java.time.LocalDateTime;

public record IdentityMappingLogView(
        Long id,
        LocalDateTime occurredAt,
        String identityProvider,
        String externalUserId,
        String mfaLevel,
        String device,
        String requestId,
        String traceId,
        String resultCode,
        String failureReason,
        Long systemMemberId,
        Long jobId) {
}
