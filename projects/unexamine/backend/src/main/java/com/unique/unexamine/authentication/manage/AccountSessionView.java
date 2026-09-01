package com.unique.unexamine.authentication.manage;

import java.time.LocalDateTime;

public record AccountSessionView(
        Long id,
        String contextType,
        Long systemId,
        Long tenantId,
        String mfaLevel,
        LocalDateTime accessExpiresAt,
        LocalDateTime refreshExpiresAt,
        LocalDateTime createdAt,
        boolean current,
        boolean revoked) {
}
