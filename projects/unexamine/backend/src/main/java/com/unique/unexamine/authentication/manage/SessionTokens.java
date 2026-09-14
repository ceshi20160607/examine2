package com.unique.unexamine.authentication.manage;

import java.time.OffsetDateTime;

public record SessionTokens(
        String accessToken,
        String refreshToken,
        OffsetDateTime accessExpiresAt,
        OffsetDateTime refreshExpiresAt) {
}
