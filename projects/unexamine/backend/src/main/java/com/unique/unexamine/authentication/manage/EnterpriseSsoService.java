package com.unique.unexamine.authentication.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.platform.base.entity.PlatSsoIdentity;
import com.unique.unexamine.platform.base.entity.PlatSsoProvider;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.service.PlatSsoIdentityBaseService;
import com.unique.unexamine.platform.base.service.PlatSsoProviderBaseService;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EnterpriseSsoService {
    private static final int STATE_MINUTES = 5;
    private final PlatSsoProviderBaseService providerService;
    private final PlatSsoIdentityBaseService identityService;
    private final PlatformAccountBaseService accountService;
    private final AuthenticationService authenticationService;
    private final TokenFactory tokenFactory;
    private final SecretReferenceResolver secretResolver;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final Clock clock = Clock.systemDefaultZone();
    private final Map<String, LoginState> states = new ConcurrentHashMap<>();

    public EnterpriseSsoService(
            PlatSsoProviderBaseService providerService,
            PlatSsoIdentityBaseService identityService,
            PlatformAccountBaseService accountService,
            AuthenticationService authenticationService,
            TokenFactory tokenFactory,
            SecretReferenceResolver secretResolver,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder) {
        this.providerService = providerService;
        this.identityService = identityService;
        this.accountService = accountService;
        this.authenticationService = authenticationService;
        this.tokenFactory = tokenFactory;
        this.secretResolver = secretResolver;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    @Transactional(readOnly = true)
    public List<SsoProviderView> publishedProviders() {
        return providerService.selectList(Wrappers.<PlatSsoProvider>lambdaQuery()
                        .eq(PlatSsoProvider::getStatus, "PUBLISHED")
                        .orderByAsc(PlatSsoProvider::getName)).stream()
                .map(provider -> new SsoProviderView(provider.getCode(), provider.getName(), provider.getProtocol()))
                .toList();
    }

    @Transactional
    public SsoStartResult start(String providerCode, String traceId) {
        PlatSsoProvider provider = requireProvider(providerCode);
        String protocol = provider.getProtocol().toUpperCase(Locale.ROOT);
        if (!List.of("OIDC", "OAUTH2", "WECHAT", "DINGTALK").contains(protocol)) {
            throw new DomainException("SSO_PROTOCOL_LOGIN_UNAVAILABLE", "该身份源尚未配置交互式登录适配器", HttpStatus.CONFLICT);
        }
        Map<String, Object> metadata = metadata(provider);
        String authorizationEndpoint = requiredUri(metadata, "authorizationEndpoint");
        String redirectUri = requiredUri(metadata, "redirectUri");
        String state = tokenFactory.create();
        String verifier = tokenFactory.create();
        LocalDateTime expiresAt = LocalDateTime.now(clock).plusMinutes(STATE_MINUTES);
        removeExpiredStates();
        states.put(state, new LoginState(provider.getId(), verifier, redirectUri, expiresAt));

        String authorizationUrl = UriComponentsBuilder.fromUriString(authorizationEndpoint)
                .queryParam("response_type", "code")
                .queryParam("client_id", provider.getClientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", scopes(metadata))
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge(verifier))
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUriString();
        auditRecorder.record(traceId, null, null, null, null, "SSO_LOGIN_STARTED", "SSO_PROVIDER",
                provider.getId().toString(), "SUCCESS", Map.of("identityProvider", provider.getCode(), "protocol", protocol));
        return new SsoStartResult(authorizationUrl, expiresAt);
    }

    @Transactional(noRollbackFor = DomainException.class)
    public SessionTokens complete(
            String providerCode,
            SsoCompleteRequest request,
            String traceId,
            String clientIp,
            String userAgent) {
        PlatSsoProvider provider = requireProvider(providerCode);
        LoginState state = states.remove(request.state());
        if (state == null || !provider.getId().equals(state.providerId()) || state.expiresAt().isBefore(LocalDateTime.now(clock))) {
            auditFailure(provider, null, traceId, "SSO_STATE_INVALID", clientIp, userAgent);
            throw new DomainException("SSO_STATE_INVALID", "企业登录状态无效或已过期，请重新发起登录", HttpStatus.UNAUTHORIZED);
        }

        Map<String, Object> metadata = metadata(provider);
        Map<String, Object> tokenResponse = exchangeCode(provider, metadata, request.code(), state);
        String accessToken = stringValue(tokenResponse.get("access_token"));
        if (accessToken == null) {
            auditFailure(provider, null, traceId, "SSO_TOKEN_INVALID", clientIp, userAgent);
            throw new DomainException("SSO_TOKEN_INVALID", "身份源未返回可用访问令牌", HttpStatus.UNAUTHORIZED);
        }
        Map<String, Object> attributes = readUserInfo(metadata, accessToken);
        String subjectClaim = optional(metadata, "subjectClaim", "sub");
        String externalSubject = stringValue(attributes.get(subjectClaim));
        if (externalSubject == null) {
            auditFailure(provider, null, traceId, "SSO_SUBJECT_MISSING", clientIp, userAgent);
            throw new DomainException("SSO_SUBJECT_MISSING", "身份源未返回外部用户标识", HttpStatus.UNAUTHORIZED);
        }
        enforceAllowedDomain(provider, metadata, attributes, externalSubject, traceId, clientIp, userAgent);

        List<PlatSsoIdentity> mappings = identityService.selectList(Wrappers.<PlatSsoIdentity>lambdaQuery()
                .eq(PlatSsoIdentity::getProviderId, provider.getId())
                .eq(PlatSsoIdentity::getExternalSubject, externalSubject));
        if (mappings.isEmpty()) {
            auditFailure(provider, externalSubject, traceId, "SSO_MAPPING_MISSING", clientIp, userAgent);
            throw new DomainException("SSO_MAPPING_MISSING", "企业身份尚未绑定平台账号，请联系管理员或申请访问", HttpStatus.FORBIDDEN);
        }
        PlatSsoIdentity mapping = mappings.getFirst();
        PlatformAccount account = accountService.selectById(mapping.getAccountId());
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            auditFailure(provider, externalSubject, traceId, "ACCOUNT_DISABLED", clientIp, userAgent);
            throw new DomainException("ACCOUNT_DISABLED", "账号已停用", HttpStatus.FORBIDDEN);
        }

        String mfaLevel = mfaLevel(metadata, attributes);
        mapping.setAttributesJson(toJson(attributes));
        mapping.setLastLoginAt(LocalDateTime.now(clock));
        identityService.updateById(mapping);
        account.setLastLoginAt(LocalDateTime.now(clock));
        accountService.updateById(account);
        SessionTokens tokens = authenticationService.createPlatformSession(account.getId(), mfaLevel);
        auditRecorder.record(traceId, account.getId(), null, null, null, "SSO_LOGIN", "SSO_IDENTITY",
                mapping.getId().toString(), "SUCCESS", Map.of(
                        "identityProvider", provider.getCode(),
                        "externalUserId", externalSubject,
                        "accountBinding", "MATCHED",
                        "authenticationMethod", provider.getProtocol(),
                        "mfa", mfaLevel,
                        "ip", safe(clientIp),
                        "device", safe(userAgent),
                        "requestId", traceId));
        return tokens;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> exchangeCode(
            PlatSsoProvider provider,
            Map<String, Object> metadata,
            String code,
            LoginState state) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", provider.getClientId());
        form.add("redirect_uri", state.redirectUri());
        form.add("code_verifier", state.codeVerifier());
        String secret = secretResolver.resolve(provider.getClientSecretRef());
        if (secret != null) {
            form.add("client_secret", secret);
        }
        try {
            Map<String, Object> response = restClient.post()
                    .uri(requiredUri(metadata, "tokenEndpoint"))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            return response == null ? Map.of() : response;
        } catch (RestClientException exception) {
            throw new DomainException("SSO_TOKEN_EXCHANGE_FAILED", "身份源令牌交换失败", HttpStatus.BAD_GATEWAY);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readUserInfo(Map<String, Object> metadata, String accessToken) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(requiredUri(metadata, "userinfoEndpoint"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
            return response == null ? Map.of() : response;
        } catch (RestClientException exception) {
            throw new DomainException("SSO_USERINFO_FAILED", "身份源用户信息读取失败", HttpStatus.BAD_GATEWAY);
        }
    }

    private void enforceAllowedDomain(
            PlatSsoProvider provider,
            Map<String, Object> metadata,
            Map<String, Object> attributes,
            String subject,
            String traceId,
            String clientIp,
            String userAgent) {
        Object configured = metadata.get("allowedDomains");
        if (!(configured instanceof Collection<?> domains) || domains.isEmpty()) {
            return;
        }
        String emailClaim = optional(metadata, "emailClaim", "email");
        String email = stringValue(attributes.get(emailClaim));
        String domain = email == null || !email.contains("@") ? null : email.substring(email.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        boolean allowed = domain != null && domains.stream().map(Object::toString)
                .map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(domain::equals);
        if (!allowed) {
            auditFailure(provider, subject, traceId, "SSO_DOMAIN_NOT_ALLOWED", clientIp, userAgent);
            throw new DomainException("SSO_DOMAIN_NOT_ALLOWED", "企业账号域名不在允许范围", HttpStatus.FORBIDDEN);
        }
    }

    private String mfaLevel(Map<String, Object> metadata, Map<String, Object> attributes) {
        String claim = optional(metadata, "mfaClaim", "mfa");
        Object value = attributes.get(claim);
        boolean satisfied = Boolean.TRUE.equals(value)
                || value instanceof Collection<?> collection && collection.stream()
                .map(Object::toString).anyMatch(item -> item.equalsIgnoreCase("mfa") || item.equalsIgnoreCase("otp"));
        if (Boolean.TRUE.equals(metadata.get("mfaRequired")) && !satisfied) {
            throw new DomainException("SSO_MFA_REQUIRED", "该身份源要求完成多因素认证", HttpStatus.FORBIDDEN);
        }
        return satisfied ? "MFA" : "NONE";
    }

    private PlatSsoProvider requireProvider(String providerCode) {
        List<PlatSsoProvider> providers = providerService.selectList(Wrappers.<PlatSsoProvider>lambdaQuery()
                .eq(PlatSsoProvider::getCode, providerCode.strip().toLowerCase(Locale.ROOT))
                .eq(PlatSsoProvider::getStatus, "PUBLISHED"));
        if (providers.isEmpty()) {
            throw new DomainException("SSO_PROVIDER_UNAVAILABLE", "企业身份源不存在或尚未发布", HttpStatus.NOT_FOUND);
        }
        return providers.getFirst();
    }

    private Map<String, Object> metadata(PlatSsoProvider provider) {
        try {
            return objectMapper.readValue(provider.getMetadataJson(), new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException exception) {
            throw new DomainException("SSO_PROVIDER_CONFIGURATION_INVALID", "企业身份源配置无法读取", HttpStatus.CONFLICT);
        }
    }

    private String requiredUri(Map<String, Object> metadata, String key) {
        String value = stringValue(metadata.get(key));
        if (value == null || !(value.startsWith("https://") || value.startsWith("http://127.0.0.1:") || value.startsWith("http://localhost:"))) {
            throw new DomainException("SSO_PROVIDER_CONFIGURATION_INVALID", "身份源缺少安全的 " + key, HttpStatus.CONFLICT);
        }
        return value;
    }

    private String scopes(Map<String, Object> metadata) {
        Object configured = metadata.get("scopes");
        if (configured instanceof Collection<?> values && !values.isEmpty()) {
            return values.stream().map(Object::toString).reduce((left, right) -> left + " " + right).orElse("openid profile email");
        }
        return "openid profile email";
    }

    private String optional(Map<String, Object> metadata, String key, String fallback) {
        String value = stringValue(metadata.get(key));
        return value == null ? fallback : value;
    }

    private String stringValue(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private String codeChallenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot persist SSO attributes", exception);
        }
    }

    private void auditFailure(
            PlatSsoProvider provider,
            String externalSubject,
            String traceId,
            String failureCode,
            String clientIp,
            String userAgent) {
        auditRecorder.recordFailure(traceId, null, null, null, null, "SSO_LOGIN", "SSO_PROVIDER",
                provider.getId().toString(), failureCode, Map.of(
                        "identityProvider", provider.getCode(),
                        "externalUserId", safe(externalSubject),
                        "authenticationMethod", provider.getProtocol(),
                        "ip", safe(clientIp),
                        "device", safe(userAgent),
                        "requestId", traceId));
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private void removeExpiredStates() {
        LocalDateTime now = LocalDateTime.now(clock);
        states.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private record LoginState(Long providerId, String codeVerifier, String redirectUri, LocalDateTime expiresAt) {
    }
}
