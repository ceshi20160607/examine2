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
import com.unique.unexamine.platform.manage.foundation.PlatformDefinitionManager;
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
import java.net.URI;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.Deflater;

@Service
public class EnterpriseSsoService {
    private static final int STATE_MINUTES = 5;
    private final PlatSsoProviderBaseService providerService;
    private final PlatSsoIdentityBaseService identityService;
    private final PlatformAccountBaseService accountService;
    private final PlatformDefinitionManager platformDefinitionManager;
    private final AuthenticationService authenticationService;
    private final TokenFactory tokenFactory;
    private final SecretReferenceResolver secretResolver;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final OidcIdTokenVerifier idTokenVerifier;
    private final SamlResponseVerifier samlResponseVerifier;
    private final LdapDirectoryAuthenticator directoryAuthenticator;
    private final RestClient restClient;
    private final Clock clock = Clock.systemDefaultZone();
    private final Map<String, LoginState> states = new ConcurrentHashMap<>();
    private final Map<String, SamlTicket> samlTickets = new ConcurrentHashMap<>();

    public EnterpriseSsoService(
            PlatSsoProviderBaseService providerService,
            PlatSsoIdentityBaseService identityService,
            PlatformAccountBaseService accountService,
            PlatformDefinitionManager platformDefinitionManager,
            AuthenticationService authenticationService,
            TokenFactory tokenFactory,
            SecretReferenceResolver secretResolver,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper,
            OidcIdTokenVerifier idTokenVerifier,
            SamlResponseVerifier samlResponseVerifier,
            LdapDirectoryAuthenticator directoryAuthenticator,
            RestClient.Builder restClientBuilder) {
        this.providerService = providerService;
        this.identityService = identityService;
        this.accountService = accountService;
        this.platformDefinitionManager = platformDefinitionManager;
        this.authenticationService = authenticationService;
        this.tokenFactory = tokenFactory;
        this.secretResolver = secretResolver;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.idTokenVerifier = idTokenVerifier;
        this.samlResponseVerifier = samlResponseVerifier;
        this.directoryAuthenticator = directoryAuthenticator;
        this.restClient = restClientBuilder.build();
    }

    @Transactional(readOnly = true)
    public List<SsoProviderView> publishedProviders() {
        return providerService.selectList(Wrappers.<PlatSsoProvider>lambdaQuery()
                        .eq(PlatSsoProvider::getPlatformId, platformDefinitionManager.requireDefaultPlatform().getId())
                        .eq(PlatSsoProvider::getStatus, "PUBLISHED")
                        .orderByAsc(PlatSsoProvider::getName)).stream()
                .filter(this::runtimeEligible)
                .map(provider -> new SsoProviderView(provider.getCode(), provider.getName(), provider.getProtocol()))
                .toList();
    }

    @Transactional
    public SsoStartResult start(String providerCode, String traceId) {
        PlatSsoProvider provider = requireProvider(providerCode);
        String protocol = provider.getProtocol().toUpperCase(Locale.ROOT);
        if (!"OIDC".equals(protocol) && !"OAUTH2".equals(protocol) && !"SAML2".equals(protocol)) {
            throw new DomainException("SSO_PROTOCOL_LOGIN_UNAVAILABLE", "该身份源尚未配置交互式登录适配器", HttpStatus.CONFLICT);
        }
        if (!runtimeEligible(provider)) {
            throw new DomainException("SSO_PROVIDER_REVALIDATION_REQUIRED", "身份源安全校验规则已更新，请管理员新建版本并重新发布", HttpStatus.CONFLICT);
        }
        Map<String, Object> metadata = metadata(provider);
        if ("SAML2".equals(protocol)) {
            return startSaml(provider, metadata, traceId);
        }
        String authorizationEndpoint = requiredUri(metadata, "authorizationEndpoint");
        String redirectUri = requiredUri(metadata, "redirectUri");
        String state = tokenFactory.create();
        String verifier = tokenFactory.create();
        String nonce = "OIDC".equals(protocol) ? tokenFactory.create() : null;
        LocalDateTime expireTime = LocalDateTime.now(clock).plusMinutes(STATE_MINUTES);
        removeExpiredStates();
        states.put(state, new LoginState(provider.getId(), verifier, nonce, redirectUri, null, expireTime));

        UriComponentsBuilder authorizationRequest = UriComponentsBuilder.fromUriString(authorizationEndpoint)
                .queryParam("response_type", "code")
                .queryParam("client_id", provider.getClientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", scopes(metadata, protocol))
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge(verifier))
                .queryParam("code_challenge_method", "S256");
        // nonce 只用于 OIDC 身份令牌验签，通用 OAuth2 仅携带授权码和 PKCE 所需参数。
        if ("OIDC".equals(protocol)) {
            authorizationRequest.queryParam("nonce", nonce);
        }
        String authorizationUrl = authorizationRequest.build().encode().toUriString();
        auditRecorder.record(traceId, null, null, null, null, "SSO_LOGIN_STARTED", "SSO_PROVIDER",
                provider.getId().toString(), "SUCCESS", Map.of("identityProvider", provider.getCode(), "protocol", protocol));
        return new SsoStartResult(authorizationUrl, expireTime);
    }

    /** 目录密码只用于本次用户 Bind；会话仍由显式平台身份绑定签发。 */
    @Transactional(noRollbackFor = DomainException.class)
    public SessionTokens loginDirectory(String providerCode, DirectoryLoginRequest request,
            String traceId, String clientIp, String userAgent) {
        PlatSsoProvider provider = requireProvider(providerCode);
        if (!"LDAP".equalsIgnoreCase(provider.getProtocol()) || !runtimeEligible(provider)) {
            throw new DomainException("SSO_PROTOCOL_LOGIN_UNAVAILABLE", "目录身份源尚未通过当前安全预检", HttpStatus.CONFLICT);
        }
        Map<String, Object> configuration = metadata(provider);
        Map<String, Object> attributes;
        try {
            attributes = directoryAuthenticator.authenticate(configuration,
                    optional(configuration, "subjectClaim", "uid"),
                    optional(configuration, "emailClaim", "mail"),
                    request.username(), request.password());
        } catch (DomainException exception) {
            auditFailure(provider, null, traceId, exception.code(), clientIp, userAgent);
            throw exception;
        }
        return finishIdentity(provider, configuration, attributes, traceId, clientIp, userAgent);
    }

    /** SAML2 使用 SP 发起的 Redirect 请求和一次性 RelayState；IdP 以签名 POST 响应回 ACS。 */
    private SsoStartResult startSaml(PlatSsoProvider provider, Map<String, Object> metadata, String traceId) {
        String endpoint = requiredUri(metadata, "ssoEndpoint");
        String acsUri = requiredUri(metadata, "acsUri");
        String state = tokenFactory.create();
        String requestId = "_" + tokenFactory.create();
        LocalDateTime expireTime = LocalDateTime.now(clock).plusMinutes(STATE_MINUTES);
        removeExpiredStates();
        states.put(state, new LoginState(provider.getId(), null, null, acsUri, requestId, expireTime));
        String requestXml = "<samlp:AuthnRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
                + "xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
                + "ID=\"" + requestId + "\" Version=\"2.0\" IssueInstant=\"" + Instant.now(clock) + "\" "
                + "Destination=\"" + xmlEscape(endpoint) + "\" "
                + "ProtocolBinding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\" "
                + "AssertionConsumerServiceURL=\"" + xmlEscape(acsUri) + "\">"
                + "<saml:Issuer>" + xmlEscape(provider.getClientId()) + "</saml:Issuer>"
                + "</samlp:AuthnRequest>";
        String authorizationUrl = UriComponentsBuilder.fromUriString(endpoint)
                .queryParam("SAMLRequest", URLEncoder.encode(deflateBase64(requestXml), StandardCharsets.UTF_8))
                .queryParam("RelayState", state).build(true).toUriString();
        auditRecorder.record(traceId, null, null, null, null, "SSO_LOGIN_STARTED", "SSO_PROVIDER",
                provider.getId().toString(), "SUCCESS", Map.of("identityProvider", provider.getCode(), "protocol", "SAML2"));
        return new SsoStartResult(authorizationUrl, expireTime);
    }

    private String deflateBase64(String xml) {
        byte[] input = xml.getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try {
            deflater.setInput(input);
            deflater.finish();
            byte[] output = new byte[input.length + 128];
            int length = deflater.deflate(output);
            if (!deflater.finished()) throw new IllegalStateException("SAML request buffer too small");
            return Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(output, length));
        } finally {
            deflater.end();
        }
    }

    private String xmlEscape(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;").replace("'", "&apos;");
    }

    /** ACS 验签成功后只向浏览器交出一次性码，不把断言或平台令牌放入跳转地址。 */
    @Transactional(noRollbackFor = DomainException.class)
    public String acceptSaml(String providerCode, String encodedResponse, String relayState,
            String traceId, String clientIp, String userAgent) {
        PlatSsoProvider provider = requireProvider(providerCode);
        if (!"SAML2".equalsIgnoreCase(provider.getProtocol()) || !runtimeEligible(provider)) {
            throw new DomainException("SSO_PROVIDER_REVALIDATION_REQUIRED", "SAML 身份源尚未通过当前安全预检", HttpStatus.CONFLICT);
        }
        LoginState state = relayState == null ? null : states.remove(relayState);
        if (state == null || !provider.getId().equals(state.providerId())
                || state.requestId() == null || state.expireTime().isBefore(LocalDateTime.now(clock))) {
            auditFailure(provider, null, traceId, "SSO_STATE_INVALID", clientIp, userAgent);
            throw new DomainException("SSO_STATE_INVALID", "企业登录状态无效或已过期", HttpStatus.UNAUTHORIZED);
        }
        Map<String, Object> metadata = metadata(provider);
        Map<String, Object> attributes;
        try {
            String certificate = secretResolver.resolve(optional(metadata, "certificateRef", ""));
            attributes = samlResponseVerifier.verify(encodedResponse, certificate, provider.getIssuer(),
                    provider.getClientId(), state.redirectUri(), state.requestId(), clock);
        } catch (DomainException exception) {
            auditFailure(provider, null, traceId, exception.code(), clientIp, userAgent);
            throw exception;
        }
        String ticket = tokenFactory.create();
        samlTickets.put(ticket, new SamlTicket(provider.getId(), relayState, attributes,
                LocalDateTime.now(clock).plusMinutes(STATE_MINUTES)));
        URI acs = URI.create(state.redirectUri());
        return UriComponentsBuilder.fromUri(acs).replacePath("/sso/callback").replaceQuery(null)
                .queryParam("code", ticket).queryParam("state", relayState).build().encode().toUriString();
    }

    @Transactional(noRollbackFor = DomainException.class)
    public SessionTokens complete(
            String providerCode,
            SsoCompleteRequest request,
            String traceId,
            String clientIp,
            String userAgent) {
        PlatSsoProvider provider = requireProvider(providerCode);
        if ("SAML2".equalsIgnoreCase(provider.getProtocol())) {
            SamlTicket ticket = samlTickets.remove(request.code());
            if (ticket == null || !provider.getId().equals(ticket.providerId())
                    || !request.state().equals(ticket.relayState())
                    || ticket.expireTime().isBefore(LocalDateTime.now(clock))) {
                auditFailure(provider, null, traceId, "SSO_STATE_INVALID", clientIp, userAgent);
                throw new DomainException("SSO_STATE_INVALID", "SAML 登录交接码无效或已使用", HttpStatus.UNAUTHORIZED);
            }
            return finishIdentity(provider, metadata(provider), ticket.attributes(), traceId, clientIp, userAgent);
        }
        LoginState state = states.remove(request.state());
        if (state == null || !provider.getId().equals(state.providerId()) || state.expireTime().isBefore(LocalDateTime.now(clock))) {
            auditFailure(provider, null, traceId, "SSO_STATE_INVALID", clientIp, userAgent);
            throw new DomainException("SSO_STATE_INVALID", "企业登录状态无效或已过期，请重新发起登录", HttpStatus.UNAUTHORIZED);
        }

        Map<String, Object> metadata = metadata(provider);
        Map<String, Object> tokenResponse;
        try {
            tokenResponse = exchangeCode(provider, metadata, request.code(), state);
        } catch (DomainException exception) {
            auditFailure(provider, null, traceId, exception.code(), clientIp, userAgent);
            throw exception;
        }
        String accessToken = stringValue(tokenResponse.get("access_token"));
        if (accessToken == null) {
            auditFailure(provider, null, traceId, "SSO_TOKEN_INVALID", clientIp, userAgent);
            throw new DomainException("SSO_TOKEN_INVALID", "身份源未返回可用访问令牌", HttpStatus.UNAUTHORIZED);
        }
        if (!"Bearer".equalsIgnoreCase(stringValue(tokenResponse.get("token_type")))) {
            auditFailure(provider, null, traceId, "SSO_TOKEN_TYPE_INVALID", clientIp, userAgent);
            throw new DomainException("SSO_TOKEN_TYPE_INVALID", "身份源未返回 Bearer 访问令牌", HttpStatus.UNAUTHORIZED);
        }
        String verifiedSubject = null;
        if ("OIDC".equalsIgnoreCase(provider.getProtocol())) {
            try {
                verifiedSubject = verifyOidcIdentity(provider, metadata, tokenResponse, state);
            } catch (DomainException exception) {
                auditFailure(provider, null, traceId, exception.code(), clientIp, userAgent);
                throw exception;
            }
        }
        Map<String, Object> attributes;
        try {
            attributes = readUserInfo(metadata, accessToken);
        } catch (DomainException exception) {
            auditFailure(provider, verifiedSubject, traceId, exception.code(), clientIp, userAgent);
            throw exception;
        }
        if (verifiedSubject != null && !verifiedSubject.equals(stringValue(attributes.get("sub")))) {
            auditFailure(provider, verifiedSubject, traceId, "SSO_SUBJECT_MISMATCH", clientIp, userAgent);
            throw new DomainException("SSO_SUBJECT_MISMATCH", "身份令牌与用户信息不一致", HttpStatus.UNAUTHORIZED);
        }
        return finishIdentity(provider, metadata, attributes, traceId, clientIp, userAgent);
    }

    /** 各协议验明身份后共用显式账号绑定、域名准入、MFA 和平台会话边界。 */
    private SessionTokens finishIdentity(PlatSsoProvider provider, Map<String, Object> metadata,
            Map<String, Object> attributes, String traceId, String clientIp, String userAgent) {
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
        if (mappings.size() > 1) {
            auditFailure(provider, externalSubject, traceId, "SSO_MAPPING_CONFLICT", clientIp, userAgent);
            throw new DomainException("SSO_MAPPING_CONFLICT", "企业身份绑定不唯一", HttpStatus.CONFLICT);
        }
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
        String mfaLevel;
        try {
            mfaLevel = mfaLevel(metadata, attributes);
        } catch (DomainException exception) {
            auditFailure(provider, externalSubject, traceId, exception.code(), clientIp, userAgent);
            throw exception;
        }
        mapping.setAttributesJson(toJson(attributes));
        mapping.setLastLoginTime(LocalDateTime.now(clock));
        identityService.updateById(mapping);
        account.setLastLoginTime(LocalDateTime.now(clock));
        accountService.updateById(account);
        AuthenticationService.IssuedSession issued = authenticationService.issuePlatformSession(account.getId(), mfaLevel);
        // 平台登录尚未选择系统；后续进入系统按会话主键写独立结果，不改写不可变登录审计。
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("identityProvider", provider.getCode());
        detail.put("externalUserId", externalSubject);
        detail.put("identityMappingId", mapping.getId());
        detail.put("platformSessionId", issued.sessionId());
        detail.put("accountBinding", "MATCHED");
        detail.put("systemMemberMapping", "NOT_REQUESTED");
        detail.put("authenticationMethod", provider.getProtocol());
        detail.put("mfa", mfaLevel);
        detail.put("ip", safe(clientIp));
        detail.put("device", safe(userAgent));
        detail.put("requestId", traceId);
        auditRecorder.record(traceId, account.getId(), null, null, null, "SSO_LOGIN", "AUTH_SESSION",
                issued.sessionId().toString(), "SUCCESS", detail);
        return issued.tokens();
    }

    /** OIDC 先校验身份令牌，再把同一 subject 的 userinfo 用于额外属性映射。 */
    @SuppressWarnings("unchecked")
    private String verifyOidcIdentity(PlatSsoProvider provider, Map<String, Object> metadata,
            Map<String, Object> tokenResponse, LoginState state) {
        String idToken = stringValue(tokenResponse.get("id_token"));
        if (idToken == null) {
            throw new DomainException("SSO_ID_TOKEN_INVALID", "身份源未返回可验证的身份令牌", HttpStatus.UNAUTHORIZED);
        }
        try {
            Map<String, Object> jwks = restClient.get().uri(requiredUri(metadata, "jwksUri"))
                    .retrieve().body(Map.class);
            return idTokenVerifier.verify(idToken, jwks, provider.getIssuer(), provider.getClientId(), state.nonce());
        } catch (RestClientException exception) {
            throw new DomainException("SSO_JWKS_UNAVAILABLE", "身份源签名公钥暂不可用", HttpStatus.BAD_GATEWAY);
        }
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
        // 企业域名准入只信任身份源声明已验证的邮箱，OAuth2 也不能直接信任邮箱文本。
        Object verifiedClaim = attributes.get(optional(metadata, "emailVerifiedClaim", "email_verified"));
        boolean verifiedEmail = Boolean.TRUE.equals(verifiedClaim)
                || "true".equalsIgnoreCase(String.valueOf(verifiedClaim));
        boolean allowed = verifiedEmail && domain != null && domains.stream().map(Object::toString)
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
                .eq(PlatSsoProvider::getPlatformId, platformDefinitionManager.requireDefaultPlatform().getId())
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

    private boolean runtimeEligible(PlatSsoProvider provider) {
        Map<String, Object> configuration = metadata(provider);
        return ("OIDC".equalsIgnoreCase(provider.getProtocol())
                && "RS256_NONCE_V1".equals(configuration.get("idTokenValidation")))
                || ("OAUTH2".equalsIgnoreCase(provider.getProtocol())
                && "AUTH_CODE_PKCE_USERINFO_V1".equals(configuration.get("oauth2IdentityValidation")))
                || ("SAML2".equalsIgnoreCase(provider.getProtocol())
                && "SIGNED_RESPONSE_POST_V1".equals(configuration.get("samlIdentityValidation")))
                || ("LDAP".equalsIgnoreCase(provider.getProtocol())
                && "LDAPS_BIND_SEARCH_V1".equals(configuration.get("ldapIdentityValidation")));
    }

    private String requiredUri(Map<String, Object> metadata, String key) {
        String value = stringValue(metadata.get(key));
        if (value == null || !(value.startsWith("https://") || value.startsWith("http://127.0.0.1:") || value.startsWith("http://localhost:"))) {
            throw new DomainException("SSO_PROVIDER_CONFIGURATION_INVALID", "身份源缺少安全的 " + key, HttpStatus.CONFLICT);
        }
        return value;
    }

    /** OAuth2 不隐式要求 OIDC 的 openid scope，授权范围由身份源配置决定。 */
    private String scopes(Map<String, Object> metadata, String protocol) {
        Object configured = metadata.get("scopes");
        if (configured instanceof Collection<?> values && !values.isEmpty()) {
            return values.stream().map(Object::toString).reduce((left, right) -> left + " " + right).orElse("");
        }
        return "OIDC".equals(protocol) ? "openid profile email" : "profile email";
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
                        "accountBinding", "UNVERIFIED",
                        "systemMemberMapping", "NOT_APPLICABLE",
                        "authenticationMethod", provider.getProtocol(),
                        "mfa", "UNVERIFIED",
                        "ip", safe(clientIp),
                        "device", safe(userAgent),
                        "requestId", traceId));
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private void removeExpiredStates() {
        LocalDateTime now = LocalDateTime.now(clock);
        states.entrySet().removeIf(entry -> entry.getValue().expireTime().isBefore(now));
        samlTickets.entrySet().removeIf(entry -> entry.getValue().expireTime().isBefore(now));
    }

    private record LoginState(Long providerId, String codeVerifier, String nonce, String redirectUri,
            String requestId, LocalDateTime expireTime) {
    }

    private record SamlTicket(Long providerId, String relayState, Map<String, Object> attributes,
            LocalDateTime expireTime) {
    }
}
