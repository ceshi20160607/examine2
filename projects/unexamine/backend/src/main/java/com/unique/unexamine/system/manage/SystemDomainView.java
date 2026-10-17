package com.unique.unexamine.system.manage;

import java.time.LocalDateTime;

/** 系统域名验证结果；verifyTime 仅在验证成功后有值。 */
public record SystemDomainView(
        Long id,
        Long tenantId,
        String tenantName,
        String domainType,
        String host,
        String basePath,
        boolean tlsRequired,
        String status,
        LocalDateTime verifyTime,
        String verificationToken,
        String verificationUrl,
        Integer version) {
}
