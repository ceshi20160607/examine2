package com.unique.unexamine.authentication.manage;

import java.time.LocalDateTime;

public record MfaMethodView(
        Long id,
        String methodType,
        String displayLabel,
        String status,
        LocalDateTime verifiedAt) {
}
