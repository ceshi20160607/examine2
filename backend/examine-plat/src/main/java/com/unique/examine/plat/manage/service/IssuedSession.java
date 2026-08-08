package com.unique.examine.plat.manage.service;

import com.unique.examine.plat.manage.vo.AuthResultVo;

import java.time.Duration;

public record IssuedSession(
        AuthResultVo result,
        String accessToken,
        String refreshToken,
        String csrfToken,
        Duration accessTtl,
        Duration refreshTtl
) {
    public IssuedSession withResult(AuthResultVo replacement) {
        return new IssuedSession(replacement, accessToken, refreshToken, csrfToken, accessTtl, refreshTtl);
    }
}
