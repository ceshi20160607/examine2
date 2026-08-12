package com.unique.examine.plat.vnext.manage.auth;

import java.time.Duration;

public record IssuedSession(
        AccountContext accountContext,
        String accessToken,
        String refreshToken,
        String csrfToken,
        Duration accessTtl,
        Duration refreshTtl
) {
}
