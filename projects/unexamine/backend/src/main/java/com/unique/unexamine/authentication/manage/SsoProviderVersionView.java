package com.unique.unexamine.authentication.manage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record SsoProviderVersionView(
        Long id,
        Integer versionNumber,
        String protocol,
        String issuer,
        String clientId,
        String clientSecretRef,
        Map<String, Object> protocolConfig,
        List<String> allowedDomains,
        Map<String, Object> attributeMapping,
        Map<String, Object> jitPolicy,
        Map<String, Object> mfaPolicy,
        List<String> callbackUris,
        String testStatus,
        Map<String, Object> testReport,
        String status,
        LocalDateTime createdAt,
        LocalDateTime publishedAt) {
}
