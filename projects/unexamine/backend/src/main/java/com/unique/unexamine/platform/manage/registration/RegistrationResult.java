package com.unique.unexamine.platform.manage.registration;

import com.unique.unexamine.authentication.manage.SessionTokens;

/** 注册结果；仅注册平台账号时系统、租户与系统会话为空。 */
public record RegistrationResult(
        Long accountId,
        Long systemId,
        String systemCode,
        String systemName,
        Long tenantId,
        String tenantName,
        String tenantMode,
        SessionTokens tokens,
        SessionTokens platformTokens) {
}
