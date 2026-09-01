package com.unique.unexamine.authentication.manage;

import java.time.LocalDateTime;
import java.util.List;

public record AccountProfileView(
        Long accountId,
        String username,
        String displayName,
        String email,
        String mobile,
        String locale,
        String timezone,
        String status,
        LocalDateTime lastLoginAt,
        Integer credentialVersion,
        Integer version,
        List<MfaMethodView> mfaMethods,
        List<AccountSessionView> sessions) {
}
