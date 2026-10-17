package com.unique.unexamine.authentication.manage;

import java.time.OffsetDateTime;

/** 登录令牌及各自失效时间，返回值按服务时区转换为带偏移时间。 */
public record SessionTokens(
        String accessToken,
        String refreshToken,
        OffsetDateTime accessExpireTime,
        OffsetDateTime refreshExpireTime) {
}
