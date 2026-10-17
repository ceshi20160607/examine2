package com.unique.unexamine.authentication.manage;

import java.time.LocalDateTime;

/** 会话列表项；两个 expireTime 分别控制访问令牌与刷新令牌有效期。 */
public record AccountSessionView(
        Long id,
        String contextType,
        Long systemId,
        Long tenantId,
        String mfaLevel,
        LocalDateTime accessExpireTime,
        LocalDateTime refreshExpireTime,
        LocalDateTime createTime,
        boolean current,
        boolean revoked) {
}
