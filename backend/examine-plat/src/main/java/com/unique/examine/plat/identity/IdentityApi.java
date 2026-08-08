package com.unique.examine.plat.identity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class IdentityApi {
    private IdentityApi() { }

    public enum Protocol { OIDC, OAUTH2, SAML2, LDAP, AD, WECOM, DINGTALK }
    public enum Status { DRAFT, PUBLISHED, DISABLED }
    public enum MfaPolicy { DISABLED, OPTIONAL, REQUIRED }

    public record ProviderCommand(
            String providerCode,
            String name,
            Protocol protocol,
            String issuerUri,
            String authorizationEndpoint,
            String tokenEndpoint,
            String jwksUri,
            String directoryEndpoint,
            String clientId,
            String secretRef,
            String secretVersion,
            String callbackUri,
            String scopes,
            List<String> allowedDomains,
            Map<String, String> attributeMapping,
            boolean jitAccount,
            boolean jitSystemMember,
            String systemId,
            String tenantId,
            MfaPolicy mfaPolicy,
            Long expectedVersion
    ) { }

    public record ProviderView(
            String id,
            String providerCode,
            String name,
            Protocol protocol,
            String issuerUri,
            String authorizationEndpoint,
            String tokenEndpoint,
            String jwksUri,
            String directoryEndpoint,
            String clientId,
            String secretRefMasked,
            String secretVersion,
            String callbackUri,
            String scopes,
            List<String> allowedDomains,
            Map<String, String> attributeMapping,
            boolean jitAccount,
            boolean jitSystemMember,
            String systemId,
            String tenantId,
            MfaPolicy mfaPolicy,
            Status status,
            String preflightStatus,
            Long preflightVersion,
            String preflightFailureCode,
            Instant preflightAt,
            Instant publishedAt,
            long version
    ) { }

    public record PreflightView(
            String providerId,
            String protocol,
            boolean successful,
            String failureCode,
            List<String> checks,
            Instant checkedAt,
            long providerVersion
    ) { }

    public record LoginStart(
            String authorizationUrl,
            String state,
            Instant expiresAt
    ) { }

    public record CallbackCommand(String state, String code) { }

    public record DirectoryLoginCommand(
            String providerCode,
            String systemId,
            String tenantId,
            String username,
            String password
    ) {
        @Override public String toString() {
            return "DirectoryLoginCommand[providerCode=" + providerCode + ", password=[redacted]]";
        }
    }

    public record LoginCompletion(
            String status,
            String challenge,
            boolean enrollmentRequired,
            Object session
    ) { }

    public record MfaEnrollmentCommand(
            String challenge,
            String secretRef,
            String secretVersion,
            String totpCode
    ) { }

    public record MfaEnrollmentView(
            String status,
            String secretRefMasked,
            String secretVersion,
            List<String> recoveryCodes,
            Instant verifiedAt
    ) { }

    public record MfaVerifyCommand(
            String challenge,
            String totpCode,
            String recoveryCode
    ) { }

    record Provider(
            long id,
            String providerCode,
            String name,
            Protocol protocol,
            String issuerUri,
            String authorizationEndpoint,
            String tokenEndpoint,
            String jwksUri,
            String directoryEndpoint,
            String clientId,
            String secretRef,
            String secretVersion,
            String callbackUri,
            String scopes,
            List<String> allowedDomains,
            Map<String, String> attributeMapping,
            boolean jitAccount,
            boolean jitSystemMember,
            Long systemId,
            Long tenantId,
            MfaPolicy mfaPolicy,
            Status status,
            String preflightStatus,
            Long preflightVersion,
            String preflightFailureCode,
            Instant preflightAt,
            Instant publishedAt,
            long version
    ) { }

    record AuthState(
            long id,
            Provider provider,
            Long systemId,
            Long tenantId,
            String redirectUri,
            String nonce,
            String codeVerifier,
            Instant expiresAt
    ) { }

    record ExternalIdentity(
            String subject,
            String email,
            String displayName,
            String phone,
            String employeeNo,
            String departmentId,
            Map<String, Object> claims
    ) { }

    record MfaEnrollment(
            long id,
            long accountId,
            Long systemId,
            Long tenantId,
            String secretRef,
            String secretVersion,
            Long lastUsedStep,
            Instant verifiedAt,
            long version
    ) { }

    record MfaChallenge(
            long id,
            Provider provider,
            long accountId,
            Long systemId,
            Long tenantId,
            Instant expiresAt
    ) { }
}
