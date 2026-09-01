package com.unique.unexamine.system.manage;

import java.time.LocalDateTime;

public record SystemDomainView(
        Long id,
        String domainType,
        String host,
        String basePath,
        boolean tlsRequired,
        String status,
        LocalDateTime verifiedAt,
        String verificationToken,
        String verificationUrl,
        Integer version) {
}
