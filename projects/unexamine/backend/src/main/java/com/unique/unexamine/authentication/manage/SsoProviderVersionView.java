package com.unique.unexamine.authentication.manage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 身份源版本；createdTime 与 publishedTime 分别记录草稿建立和发布时点。 */
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
        LocalDateTime createdTime,
        LocalDateTime publishedTime) {
}
