package com.unique.unexamine.authentication.manage;

import java.time.LocalDateTime;
import java.util.List;

/** 账号资料；lastLoginTime 是最近一次成功登录时间。 */
public record AccountProfileView(
        Long accountId,
        String username,
        String displayName,
        String email,
        String mobile,
        String locale,
        String timezone,
        String status,
        LocalDateTime lastLoginTime,
        Integer credentialVersion,
        Integer version,
        List<MfaMethodView> mfaMethods,
        List<AccountSessionView> sessions) {
}
