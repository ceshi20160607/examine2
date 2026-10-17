package com.unique.unexamine.system.manage;

import java.time.LocalDateTime;

/** 身份映射登录日志；occurTime 为审计事件实际发生时间。 */
public record IdentityMappingLogView(
        Long id,
        LocalDateTime occurTime,
        String eventCode,
        String identityProvider,
        String externalUserId,
        String accountBinding,
        String systemMemberMapping,
        String authenticationMethod,
        String mfaLevel,
        String ip,
        String device,
        String requestId,
        String traceId,
        String loginTraceId,
        String resultCode,
        String failureReason,
        Long systemMemberId,
        Long jobId) {
}
