package com.unique.examine.plat.manage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "examine.security")
public record SecurityProperties(
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        boolean secureCookies,
        int loginMaxFailures,
        Duration loginLockDuration
) {
    public SecurityProperties {
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
            accessTokenTtl = Duration.ofMinutes(30);
        }
        if (refreshTokenTtl == null || refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()) {
            refreshTokenTtl = Duration.ofDays(7);
        }
        if (loginMaxFailures < 1) {
            loginMaxFailures = 5;
        }
        if (loginLockDuration == null || loginLockDuration.isNegative() || loginLockDuration.isZero()) {
            loginLockDuration = Duration.ofMinutes(15);
        }
    }
}
