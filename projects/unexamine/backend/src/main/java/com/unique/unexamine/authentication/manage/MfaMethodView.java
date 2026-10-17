package com.unique.unexamine.authentication.manage;

import java.time.LocalDateTime;

/** 多因素认证方式；verifyTime 表示该方式完成验证的时间。 */
public record MfaMethodView(
        Long id,
        String methodType,
        String displayLabel,
        String status,
        LocalDateTime verifyTime) {
}
