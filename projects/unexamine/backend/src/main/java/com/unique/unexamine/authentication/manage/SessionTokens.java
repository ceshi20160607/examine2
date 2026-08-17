package com.unique.unexamine.authentication.manage;

import java.time.LocalDateTime;

public record SessionTokens(
        String accessToken,
        String refreshToken,
        LocalDateTime accessExpiresAt,
        LocalDateTime refreshExpiresAt) {
}
