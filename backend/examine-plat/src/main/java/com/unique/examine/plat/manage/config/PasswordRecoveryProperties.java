package com.unique.examine.plat.manage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "examine.security.password-recovery")
public record PasswordRecoveryProperties(
        Duration tokenTtl,
        int ipRequestsPerMinute,
        int accountRequestsPerHour
) {
    public PasswordRecoveryProperties {
        if (tokenTtl == null || tokenTtl.isNegative() || tokenTtl.isZero()) {
            tokenTtl = Duration.ofMinutes(30);
        }
        if (ipRequestsPerMinute < 1) {
            ipRequestsPerMinute = 5;
        }
        if (accountRequestsPerHour < 1) {
            accountRequestsPerHour = 3;
        }
    }
}
