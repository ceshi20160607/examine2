package com.unique.unexamine.system.manage;

import java.util.List;

public record SystemIdentityProviderOption(
        Long providerId,
        String code,
        String name,
        String protocol,
        Long publishedVersionId,
        Integer publishedVersionNumber,
        List<String> allowedDomains) {
}
