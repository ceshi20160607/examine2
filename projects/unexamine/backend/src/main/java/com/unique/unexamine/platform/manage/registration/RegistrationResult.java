package com.unique.unexamine.platform.manage.registration;

import com.unique.unexamine.authentication.manage.SessionTokens;

public record RegistrationResult(
        Long accountId,
        Long systemId,
        String systemCode,
        String systemName,
        Long tenantId,
        String tenantName,
        String tenantMode,
        SessionTokens tokens) {
}
