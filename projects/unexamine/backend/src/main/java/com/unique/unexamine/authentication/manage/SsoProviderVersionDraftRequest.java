package com.unique.unexamine.authentication.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record SsoProviderVersionDraftRequest(
        @NotBlank @Size(max = 32) String protocol,
        @NotBlank @Size(max = 500) String issuer,
        @NotBlank @Size(max = 255) String clientId,
        @Size(max = 255) String clientSecretRef,
        @NotNull Map<String, Object> protocolConfig,
        @NotNull List<String> allowedDomains,
        @NotNull Map<String, Object> attributeMapping,
        @NotNull Map<String, Object> jitPolicy,
        @NotNull Map<String, Object> mfaPolicy,
        @NotNull List<String> callbackUris) {
}
