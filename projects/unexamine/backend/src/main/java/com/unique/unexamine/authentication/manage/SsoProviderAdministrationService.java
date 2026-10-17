package com.unique.unexamine.authentication.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.platform.base.entity.PlatSsoProvider;
import com.unique.unexamine.platform.base.entity.PlatSsoProviderVersion;
import com.unique.unexamine.platform.base.service.PlatSsoProviderBaseService;
import com.unique.unexamine.platform.base.service.PlatSsoProviderVersionBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SysSsoBinding;
import com.unique.unexamine.system.base.service.SysSsoBindingBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class SsoProviderAdministrationService {
    private static final Set<String> SUPPORTED_PROTOCOLS = Set.of(
            "OIDC", "SAML2", "OAUTH2", "LDAP", "AD", "WECHAT", "DINGTALK");
    private static final Set<String> INTERACTIVE_PROTOCOLS = Set.of(
            "OIDC", "OAUTH2", "WECHAT", "DINGTALK");
    private static final Pattern PROVIDER_CODE = Pattern.compile("[a-z][a-z0-9_-]{2,99}");
    private static final Pattern DOMAIN = Pattern.compile("(?i)[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?");

    private final PlatSsoProviderBaseService providerService;
    private final PlatSsoProviderVersionBaseService versionService;
    private final SysSsoBindingBaseService bindingService;
    private final SecretReferenceResolver secretResolver;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final OidcIdTokenVerifier idTokenVerifier;
    private final SamlResponseVerifier samlResponseVerifier;
    private final LdapDirectoryAuthenticator directoryAuthenticator;
    private final RestClient restClient;
    private final Clock clock = Clock.systemDefaultZone();

    public SsoProviderAdministrationService(
            PlatSsoProviderBaseService providerService,
            PlatSsoProviderVersionBaseService versionService,
            SysSsoBindingBaseService bindingService,
            SecretReferenceResolver secretResolver,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper,
            OidcIdTokenVerifier idTokenVerifier,
            SamlResponseVerifier samlResponseVerifier,
            LdapDirectoryAuthenticator directoryAuthenticator,
            RestClient.Builder restClientBuilder) {
        this.providerService = providerService;
        this.versionService = versionService;
        this.bindingService = bindingService;
        this.secretResolver = secretResolver;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.idTokenVerifier = idTokenVerifier;
        this.samlResponseVerifier = samlResponseVerifier;
        this.directoryAuthenticator = directoryAuthenticator;
        this.restClient = restClientBuilder.build();
    }

    @Transactional(readOnly = true)
    public List<SsoProviderAdminView> list(AuthenticatedContext context) {
        return providerService.selectList(Wrappers.<PlatSsoProvider>lambdaQuery()
                        .eq(PlatSsoProvider::getPlatformId, context.platformId())
                        .orderByAsc(PlatSsoProvider::getName))
                .stream().map(this::view).toList();
    }

    @Transactional
    public SsoProviderAdminView create(
            AuthenticatedContext context,
            SsoProviderDraftRequest input,
            String traceId) {
        String code = input.code().strip().toLowerCase(Locale.ROOT);
        if (!PROVIDER_CODE.matcher(code).matches()) {
            throw new DomainException("SSO_PROVIDER_CODE_INVALID", "身份源编码格式无效", HttpStatus.BAD_REQUEST);
        }
        if (!providerService.selectList(Wrappers.<PlatSsoProvider>lambdaQuery()
                .eq(PlatSsoProvider::getPlatformId, context.platformId())
                .eq(PlatSsoProvider::getCode, code)).isEmpty()) {
            throw new DomainException("SSO_PROVIDER_CODE_EXISTS", "身份源编码已经存在", HttpStatus.CONFLICT);
        }
        String protocol = normalizeProtocol(input.protocol());
        PlatSsoProvider provider = new PlatSsoProvider();
        provider.setPlatformId(context.platformId());
        provider.setCode(code);
        provider.setName(input.name().strip());
        provider.setProtocol(protocol);
        provider.setIssuer(input.issuer().strip());
        provider.setClientId(input.clientId().strip());
        provider.setClientSecretRef(stripNullable(input.clientSecretRef()));
        provider.setMetadataJson("{}");
        provider.setStatus("DRAFT");
        providerService.insert(provider);

        PlatSsoProviderVersion version = newVersion(provider.getId(), 1, protocol,
                input.issuer(), input.clientId(), input.clientSecretRef(), input.protocolConfig(),
                input.allowedDomains(), input.attributeMapping(), input.jitPolicy(), input.mfaPolicy(), input.callbackUris());
        versionService.insert(version);
        auditRecorder.record(traceId, context.accountId(), null, null, null,
                "SSO_PROVIDER_CONFIG_CREATED", "SSO_PROVIDER", provider.getId().toString(), "SUCCESS",
                Map.of("providerCode", code, "version", 1, "protocol", protocol));
        return view(provider);
    }

    @Transactional
    public SsoProviderVersionView createVersion(
            AuthenticatedContext context,
            Long providerId,
            SsoProviderVersionDraftRequest input,
            String traceId) {
        PlatSsoProvider provider = requireProvider(context, providerId);
        String protocol = normalizeProtocol(input.protocol());
        int nextVersion = versions(providerId).stream()
                .map(PlatSsoProviderVersion::getVersionNumber)
                .max(Integer::compareTo).orElse(0) + 1;
        PlatSsoProviderVersion version = newVersion(providerId, nextVersion, protocol,
                input.issuer(), input.clientId(), input.clientSecretRef(), input.protocolConfig(),
                input.allowedDomains(), input.attributeMapping(), input.jitPolicy(), input.mfaPolicy(), input.callbackUris());
        versionService.insert(version);
        auditRecorder.record(traceId, context.accountId(), null, null, null,
                "SSO_PROVIDER_CONFIG_VERSION_CREATED", "SSO_PROVIDER", providerId.toString(), "SUCCESS",
                Map.of("providerCode", provider.getCode(), "version", nextVersion, "protocol", protocol));
        return versionView(version);
    }

    @Transactional
    public SsoProviderTestReport test(
            AuthenticatedContext context,
            Long providerId,
            Long versionId,
            String traceId) {
        PlatSsoProvider provider = requireProvider(context, providerId);
        PlatSsoProviderVersion version = requireVersion(providerId, versionId);
        if (!"DRAFT".equals(version.getStatus())) {
            throw new DomainException("SSO_PROVIDER_VERSION_IMMUTABLE", "已发布身份源版本不可重新检测或修改", HttpStatus.CONFLICT);
        }

        List<SsoProviderTestCheck> checks = runChecks(version);
        boolean passed = checks.stream().allMatch(check -> "PASSED".equals(check.status()));
        String failureCode = checks.stream().filter(check -> "FAILED".equals(check.status()))
                .map(SsoProviderTestCheck::code).findFirst().orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        SsoProviderTestReport report = new SsoProviderTestReport(
                passed ? "PASSED" : "FAILED", traceId, failureCode, now, List.copyOf(checks));
        version.setTestStatus(report.status());
        version.setTestReportJson(toJson(report));
        version.setTestedTime(now);
        version.setTestedByAccountId(context.accountId());
        versionService.updateById(version);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("providerCode", provider.getCode());
        detail.put("version", version.getVersionNumber());
        detail.put("requestId", traceId);
        detail.put("failureCode", failureCode == null ? "NONE" : failureCode);
        if (passed) {
            auditRecorder.record(traceId, context.accountId(), null, null, null,
                    "SSO_PROVIDER_CONFIG_TEST", "SSO_PROVIDER_VERSION", versionId.toString(), "SUCCESS", detail);
        } else {
            auditRecorder.recordFailure(traceId, context.accountId(), null, null, null,
                    "SSO_PROVIDER_CONFIG_TEST", "SSO_PROVIDER_VERSION", versionId.toString(), failureCode, detail);
        }
        return report;
    }

    @Transactional
    public SsoProviderAdminView publish(
            AuthenticatedContext context,
            Long providerId,
            Long versionId,
            String traceId) {
        PlatSsoProvider provider = requireProvider(context, providerId);
        PlatSsoProviderVersion version = requireVersion(providerId, versionId);
        if (!"DRAFT".equals(version.getStatus())) {
            throw new DomainException("SSO_PROVIDER_VERSION_IMMUTABLE", "该身份源版本已经发布", HttpStatus.CONFLICT);
        }
        if (!"PASSED".equals(version.getTestStatus())) {
            throw new DomainException("SSO_PROVIDER_TEST_REQUIRED", "身份源版本必须检测通过后才能发布", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        version.setStatus("PUBLISHED");
        version.setPublishedTime(now);
        version.setPublishedByAccountId(context.accountId());
        versionService.updateById(version);

        provider.setProtocol(version.getProtocol());
        provider.setIssuer(version.getIssuer());
        provider.setClientId(version.getClientId());
        provider.setClientSecretRef(version.getClientSecretRef());
        provider.setMetadataJson(runtimeMetadata(version));
        provider.setStatus("PUBLISHED");
        provider.setPublishedVersionId(version.getId());
        provider.setPublishedVersionNumber(version.getVersionNumber());
        providerService.updateById(provider);

        auditRecorder.record(traceId, context.accountId(), null, null, null,
                "SSO_PROVIDER_PUBLISHED", "SSO_PROVIDER_VERSION", versionId.toString(), "SUCCESS",
                Map.of("providerCode", provider.getCode(), "version", version.getVersionNumber(),
                        "protocol", version.getProtocol(), "secretStorage", "REFERENCE_ONLY"));
        return view(provider);
    }

    /** 停用已发布身份源后不再提供登录入口；草稿与历史版本仍可供管理员审阅。 */
    @Transactional
    public SsoProviderAdminView disable(AuthenticatedContext context, Long providerId, String traceId) {
        PlatSsoProvider provider = requireProvider(context, providerId);
        if (!"PUBLISHED".equals(provider.getStatus())) {
            throw new DomainException("SSO_PROVIDER_NOT_PUBLISHED", "只有已发布身份源可以停用", HttpStatus.CONFLICT);
        }
        // 平台身份源停用时同步解除所有系统生效绑定，历史版本仍供审计读取。
        int disabledBindings = 0;
        for (SysSsoBinding binding : bindingService.selectList(Wrappers.<SysSsoBinding>lambdaQuery()
                .eq(SysSsoBinding::getPlatformId, context.platformId())
                .eq(SysSsoBinding::getProviderId, providerId)
                .eq(SysSsoBinding::getStatus, "ACTIVE"))) {
            binding.setStatus("DISABLED");
            if (bindingService.updateById(binding) != 1) {
                throw new DomainException("SSO_BINDING_CONFLICT", "系统身份源绑定已变化，请刷新后重试", HttpStatus.CONFLICT);
            }
            disabledBindings++;
        }
        provider.setStatus("DISABLED");
        if (providerService.updateById(provider) != 1) {
            throw new DomainException("SSO_PROVIDER_CONFLICT", "身份源状态已变化，请刷新后重试", HttpStatus.CONFLICT);
        }
        auditRecorder.record(traceId, context.accountId(), null, null, null,
                "SSO_PROVIDER_DISABLED", "SSO_PROVIDER", providerId.toString(), "SUCCESS",
                Map.of("providerCode", provider.getCode(), "publishedVersion", provider.getPublishedVersionNumber(),
                        "disabledBindings", disabledBindings));
        return view(provider);
    }

    private List<SsoProviderTestCheck> runChecks(PlatSsoProviderVersion version) {
        List<SsoProviderTestCheck> checks = new ArrayList<>();
        Map<String, Object> config = readMap(version.getProtocolConfigJson());
        List<String> callbacks = readList(version.getCallbackUrisJson());
        Map<String, Object> attributeMapping = readMap(version.getAttributeMappingJson());
        String protocol = version.getProtocol();

        // 只有完成真实登录适配的协议才能通过发布前预检，避免仅保存配置就误报可用。
        checks.add(("OIDC".equals(protocol) || "OAUTH2".equals(protocol) || "SAML2".equals(protocol) || "LDAP".equals(protocol))
                ? passed("PROTOCOL_SUPPORTED", protocol + " 登录适配已接入")
                : failed("PROTOCOL_RUNTIME_NOT_READY", protocol + " 登录适配尚未完成"));
        checkEndpoints(protocol, config, checks);
        checkCallbacks(protocol, config, callbacks, checks);
        if ("SAML2".equals(protocol)) {
            PlatSsoProvider provider = providerService.selectById(version.getProviderId());
            String acs = stringValue(config.get("acsUri"));
            String expectedPath = provider == null ? "" : "/api/auth/sso/" + provider.getCode() + "/saml/acs";
            boolean acsMatched = false;
            try {
                acsMatched = acs != null && expectedPath.equals(URI.create(acs).getPath());
            } catch (IllegalArgumentException ignored) {
                // 无效地址已在端点检查中记录，此处保持 ACS 路由检查失败。
            }
            checks.add(acsMatched ? passed("SAML_ACS_ROUTE", "ACS 地址对应当前身份源的后端验签入口")
                    : failed("SAML_ACS_ROUTE_INVALID", "ACS 地址必须指向当前身份源的后端验签入口"));
        }
        checkSecretReferences(protocol, version.getClientSecretRef(), config, checks);
        checkAttributeMapping(protocol, attributeMapping, readList(version.getAllowedDomainsJson()), checks);
        checkJitPolicy(readMap(version.getJitPolicyJson()), checks);
        if ("LDAP".equals(protocol) && Boolean.TRUE.equals(readMap(version.getMfaPolicyJson()).get("required"))) {
            checks.add(failed("DIRECTORY_MFA_NOT_READY", "目录账号尚未接入多因素验证，不能发布要求 MFA 的版本"));
        }
        checkSignature(protocol, config, checks);
        if ("LDAP".equals(protocol)) {
            try {
                directoryAuthenticator.preflight(config, stringValue(attributeMapping.get("externalUserId")),
                        stringValue(attributeMapping.get("email")));
                checks.add(passed("DIRECTORY_BIND_SEARCH", "LDAPS 证书、服务账号和搜索基准已实际验证"));
            } catch (DomainException exception) {
                checks.add(failed(exception.code(), "目录连接或检索预检失败"));
            }
        } else {
            checkConnection(protocol, config, checks);
        }
        return checks;
    }

    private void checkEndpoints(String protocol, Map<String, Object> config, List<SsoProviderTestCheck> checks) {
        List<String> required = switch (protocol) {
            case "OIDC" -> List.of("authorizationEndpoint", "tokenEndpoint", "userinfoEndpoint", "jwksUri", "redirectUri");
            case "OAUTH2", "WECHAT", "DINGTALK" -> List.of("authorizationEndpoint", "tokenEndpoint", "userinfoEndpoint", "redirectUri");
            case "SAML2" -> List.of("ssoEndpoint", "acsUri");
            case "LDAP", "AD" -> List.of("directoryEndpoint");
            default -> List.of();
        };
        for (String key : required) {
            String value = stringValue(config.get(key));
            boolean valid = ("directoryEndpoint".equals(key) && isSafeDirectoryUri(value))
                    || (!"directoryEndpoint".equals(key) && isSafeHttpUri(value));
            checks.add(valid ? passed("ENDPOINT_" + key.toUpperCase(Locale.ROOT), key + " 配置有效")
                    : failed("ENDPOINT_" + key.toUpperCase(Locale.ROOT), key + " 缺失或地址不安全"));
        }
    }

    private void checkCallbacks(
            String protocol,
            Map<String, Object> config,
            List<String> callbacks,
            List<SsoProviderTestCheck> checks) {
        if ("LDAP".equals(protocol) || "AD".equals(protocol)) {
            checks.add(passed("CALLBACK_MATCH", "目录协议不需要浏览器回调地址"));
            return;
        }
        String configured = stringValue(config.get("SAML2".equals(protocol) ? "acsUri" : "redirectUri"));
        boolean matched = configured != null && callbacks.stream().map(String::strip).anyMatch(configured::equals);
        checks.add(matched ? passed("CALLBACK_MATCH", "协议回调地址已在允许清单中")
                : failed("CALLBACK_MISMATCH", "协议回调地址与允许清单不一致"));
    }

    private void checkSecretReferences(
            String protocol,
            String clientSecretRef,
            Map<String, Object> config,
            List<SsoProviderTestCheck> checks) {
        if (INTERACTIVE_PROTOCOLS.contains(protocol)) {
            resolveReference("CLIENT_SECRET_REF", clientSecretRef, checks);
        }
        if ("SAML2".equals(protocol)) {
            resolveReference("CERTIFICATE_REF", stringValue(config.get("certificateRef")), checks);
        }
        if ("LDAP".equals(protocol) || "AD".equals(protocol)) {
            resolveReference("BIND_SECRET_REF", stringValue(config.get("bindSecretRef")), checks);
        }
    }

    private void resolveReference(String code, String reference, List<SsoProviderTestCheck> checks) {
        if (reference == null || reference.isBlank()) {
            checks.add(failed(code, "SecretRef 未配置"));
            return;
        }
        try {
            secretResolver.resolve(reference);
            checks.add(passed(code, "SecretRef 可用，未读取或返回明文"));
        } catch (DomainException exception) {
            checks.add(failed(exception.code(), code + " 当前不可用"));
        }
    }

    /** 域名白名单需要身份源提供已验证邮箱声明，不能仅凭可改写的邮箱文本准入。 */
    private void checkAttributeMapping(String protocol, Map<String, Object> mapping,
            List<String> allowedDomains, List<SsoProviderTestCheck> checks) {
        String externalUserId = stringValue(mapping.get("externalUserId"));
        checks.add(externalUserId == null
                ? failed("ATTRIBUTE_MAPPING_INVALID", "属性映射缺少 externalUserId")
                : passed("ATTRIBUTE_MAPPING", "外部用户标识属性映射有效"));
        if (("OIDC".equals(protocol) || "OAUTH2".equals(protocol) || "SAML2".equals(protocol))
                && !allowedDomains.isEmpty()) {
            String email = stringValue(mapping.get("email"));
            String verified = "OIDC".equals(protocol) ? "email_verified"
                    : stringValue(mapping.get("emailVerified"));
            checks.add(email != null && verified != null
                    ? passed("VERIFIED_EMAIL_MAPPING", "域名白名单使用已验证邮箱声明")
                    : failed("VERIFIED_EMAIL_MAPPING_INVALID", "域名白名单需要 email 和 emailVerified 属性映射"));
        }
        if ("LDAP".equals(protocol) && !allowedDomains.isEmpty()) {
            checks.add(stringValue(mapping.get("email")) != null
                    ? passed("DIRECTORY_EMAIL_MAPPING", "域名准入读取受信任目录的邮箱属性")
                    : failed("DIRECTORY_EMAIL_MAPPING_INVALID", "目录域名准入需要邮箱属性映射"));
        }
    }

    /** JIT 尚未经过业务验收，发布前明确拒绝启用，避免未验证路径进入运行态。 */
    private void checkJitPolicy(Map<String, Object> policy, List<SsoProviderTestCheck> checks) {
        if (Boolean.TRUE.equals(policy.get("enabled"))) {
            checks.add(failed("JIT_NOT_READY", "即时开通尚未验收，当前版本不能发布启用"));
        } else {
            checks.add(policy.containsKey("enabled") && !Boolean.FALSE.equals(policy.get("enabled"))
                    ? failed("JIT_POLICY_INVALID", "即时开通开关必须为布尔值")
                    : passed("JIT_DISABLED", "未启用即时开通，未绑定身份会明确拒绝"));
        }
    }

    private void checkSignature(String protocol, Map<String, Object> config, List<SsoProviderTestCheck> checks) {
        if ("OIDC".equals(protocol)) {
            String jwksUri = stringValue(config.get("jwksUri"));
            if (!isSafeHttpUri(jwksUri)) {
                checks.add(failed("JWKS_INVALID", "JWKS 地址无效"));
                return;
            }
            try {
                Map<?, ?> response = restClient.get().uri(jwksUri).retrieve().body(Map.class);
                boolean valid = idTokenVerifier.hasUsableSigningKey(response);
                checks.add(valid ? passed("JWKS_VALID", "JWKS 包含可用于 RS256 验签的公钥")
                        : failed("JWKS_INVALID", "JWKS 未包含可用于 RS256 验签的公钥"));
            } catch (RestClientException exception) {
                checks.add(failed("JWKS_UNAVAILABLE", "JWKS 当前不可访问"));
            }
            return;
        }
        if ("SAML2".equals(protocol)) {
            String reference = stringValue(config.get("certificateRef"));
            try {
                String certificate = secretResolver.resolve(reference);
                boolean valid = samlResponseVerifier.validCertificate(certificate, clock);
                checks.add(valid ? passed("CERTIFICATE_VALID", "SAML 签名证书格式有效")
                        : failed("CERTIFICATE_INVALID", "SAML 签名证书格式无效"));
            } catch (DomainException exception) {
                checks.add(failed(exception.code(), "SAML 签名证书不可用"));
            }
            return;
        }
        if ("OAUTH2".equals(protocol)) {
            checks.add(passed("OAUTH2_BEARER_USERINFO", "授权码和 PKCE 交换后使用 Bearer 用户信息确认外部身份"));
            return;
        }
        checks.add(passed("SIGNATURE_POLICY", "协议签名或传输安全策略已完成配置预检"));
    }

    private void checkConnection(String protocol, Map<String, Object> config, List<SsoProviderTestCheck> checks) {
        String healthEndpoint = stringValue(config.get("healthEndpoint"));
        if (healthEndpoint == null) {
            boolean configured = switch (protocol) {
                case "LDAP", "AD" -> isSafeDirectoryUri(stringValue(config.get("directoryEndpoint")));
                case "SAML2" -> isSafeHttpUri(stringValue(config.get("ssoEndpoint")));
                default -> isSafeHttpUri(stringValue(config.get("authorizationEndpoint")))
                        && isSafeHttpUri(stringValue(config.get("tokenEndpoint")));
            };
            checks.add(configured ? passed("CONNECTION_CONFIG", "连接端点配置完整")
                    : failed("CONNECTION_CONFIG_INVALID", "连接端点配置不完整"));
            return;
        }
        if (!isSafeHttpUri(healthEndpoint)) {
            checks.add(failed("CONNECTION_ENDPOINT_INVALID", "探活地址不安全"));
            return;
        }
        try {
            restClient.get().uri(healthEndpoint).retrieve().toBodilessEntity();
            checks.add(passed("CONNECTION_AVAILABLE", "身份源连接探测成功"));
        } catch (RestClientException exception) {
            checks.add(failed("CONNECTION_UNAVAILABLE", "身份源连接探测失败"));
        }
    }

    private PlatSsoProviderVersion newVersion(
            Long providerId,
            int versionNumber,
            String protocol,
            String issuer,
            String clientId,
            String clientSecretRef,
            Map<String, Object> protocolConfig,
            List<String> allowedDomains,
            Map<String, Object> attributeMapping,
            Map<String, Object> jitPolicy,
            Map<String, Object> mfaPolicy,
            List<String> callbackUris) {
        PlatSsoProviderVersion version = new PlatSsoProviderVersion();
        version.setProviderId(providerId);
        version.setVersionNumber(versionNumber);
        version.setProtocol(protocol);
        version.setIssuer(issuer.strip());
        version.setClientId(clientId.strip());
        version.setClientSecretRef(stripNullable(clientSecretRef));
        version.setProtocolConfigJson(toJson(protocolConfig));
        version.setAllowedDomainsJson(toJson(normalizeDomains(allowedDomains)));
        version.setAttributeMappingJson(toJson(attributeMapping));
        version.setJitPolicyJson(toJson(jitPolicy));
        version.setMfaPolicyJson(toJson(mfaPolicy));
        version.setCallbackUrisJson(toJson(normalizeCallbacks(callbackUris)));
        version.setTestStatus("NOT_TESTED");
        version.setStatus("DRAFT");
        return version;
    }

    private String runtimeMetadata(PlatSsoProviderVersion version) {
        Map<String, Object> metadata = new LinkedHashMap<>(readMap(version.getProtocolConfigJson()));
        metadata.put("allowedDomains", readList(version.getAllowedDomainsJson()));
        Map<String, Object> attributes = readMap(version.getAttributeMappingJson());
        String externalUserId = stringValue(attributes.get("externalUserId"));
        String email = stringValue(attributes.get("email"));
        if (externalUserId != null) {
            metadata.put("subjectClaim", externalUserId);
        }
        if (email != null) {
            metadata.put("emailClaim", email);
        }
        String emailVerified = stringValue(attributes.get("emailVerified"));
        if (emailVerified != null) {
            metadata.put("emailVerifiedClaim", emailVerified);
        }
        Map<String, Object> mfa = readMap(version.getMfaPolicyJson());
        if (mfa.containsKey("required")) {
            metadata.put("mfaRequired", mfa.get("required"));
        }
        if (mfa.containsKey("claim")) {
            metadata.put("mfaClaim", mfa.get("claim"));
        }
        metadata.put("attributeMapping", attributes);
        metadata.put("jitPolicy", readMap(version.getJitPolicyJson()));
        metadata.put("callbackUris", readList(version.getCallbackUrisJson()));
        if ("OIDC".equals(version.getProtocol())) {
            // 旧发布版本没有经过 ID Token 验签预检，运行态要求重新建版本、测试并发布。
            metadata.put("idTokenValidation", "RS256_NONCE_V1");
        } else if ("OAUTH2".equals(version.getProtocol())) {
            // 仅允许经过授权码、PKCE、Bearer 用户信息和已验证邮箱预检的新发布版本登录。
            metadata.put("oauth2IdentityValidation", "AUTH_CODE_PKCE_USERINFO_V1");
        } else if ("SAML2".equals(version.getProtocol())) {
            // 只有经 X.509 证书预检的新发布版本才可接收签名 POST 响应。
            metadata.put("samlIdentityValidation", "SIGNED_RESPONSE_POST_V1");
        } else if ("LDAP".equals(version.getProtocol())) {
            // 旧目录配置未完成实际 TLS、Bind 与搜索基准预检，不可自动进入运行态。
            metadata.put("ldapIdentityValidation", "LDAPS_BIND_SEARCH_V1");
        }
        return toJson(metadata);
    }

    private PlatSsoProvider requireProvider(AuthenticatedContext context, Long providerId) {
        PlatSsoProvider provider = providerService.selectById(providerId);
        if (provider == null || !context.platformId().equals(provider.getPlatformId())) {
            throw new DomainException("SSO_PROVIDER_NOT_FOUND", "身份源不存在", HttpStatus.NOT_FOUND);
        }
        return provider;
    }

    private PlatSsoProviderVersion requireVersion(Long providerId, Long versionId) {
        PlatSsoProviderVersion version = versionService.selectById(versionId);
        if (version == null || !providerId.equals(version.getProviderId())) {
            throw new DomainException("SSO_PROVIDER_VERSION_NOT_FOUND", "身份源版本不存在", HttpStatus.NOT_FOUND);
        }
        return version;
    }

    private List<PlatSsoProviderVersion> versions(Long providerId) {
        return versionService.selectList(Wrappers.<PlatSsoProviderVersion>lambdaQuery()
                .eq(PlatSsoProviderVersion::getProviderId, providerId)
                .orderByDesc(PlatSsoProviderVersion::getVersionNumber));
    }

    private SsoProviderAdminView view(PlatSsoProvider provider) {
        Map<String, Object> metadata = readMap(provider.getMetadataJson());
        boolean requiresRevalidation = "PUBLISHED".equals(provider.getStatus())
                && (("OIDC".equals(provider.getProtocol())
                        && !"RS256_NONCE_V1".equals(metadata.get("idTokenValidation")))
                    || ("OAUTH2".equals(provider.getProtocol())
                        && !"AUTH_CODE_PKCE_USERINFO_V1".equals(metadata.get("oauth2IdentityValidation")))
                    || ("SAML2".equals(provider.getProtocol())
                        && !"SIGNED_RESPONSE_POST_V1".equals(metadata.get("samlIdentityValidation")))
                    || ("LDAP".equals(provider.getProtocol())
                        && !"LDAPS_BIND_SEARCH_V1".equals(metadata.get("ldapIdentityValidation"))));
        return new SsoProviderAdminView(provider.getId(), provider.getCode(), provider.getName(), provider.getStatus(),
                provider.getPublishedVersionId(), provider.getPublishedVersionNumber(), requiresRevalidation,
                versions(provider.getId()).stream().map(this::versionView).toList());
    }

    private SsoProviderVersionView versionView(PlatSsoProviderVersion version) {
        return new SsoProviderVersionView(version.getId(), version.getVersionNumber(), version.getProtocol(),
                version.getIssuer(), version.getClientId(), version.getClientSecretRef(),
                readMap(version.getProtocolConfigJson()), readList(version.getAllowedDomainsJson()),
                readMap(version.getAttributeMappingJson()), readMap(version.getJitPolicyJson()),
                readMap(version.getMfaPolicyJson()), readList(version.getCallbackUrisJson()),
                version.getTestStatus(), readReport(version.getTestReportJson()), version.getStatus(),
                version.getCreatedTime(), version.getPublishedTime());
    }

    private String normalizeProtocol(String protocol) {
        String normalized = protocol.strip().toUpperCase(Locale.ROOT).replace(" ", "");
        if ("SAML2.0".equals(normalized)) {
            normalized = "SAML2";
        }
        if (!SUPPORTED_PROTOCOLS.contains(normalized)) {
            throw new DomainException("SSO_PROTOCOL_UNSUPPORTED", "企业身份源协议不受支持", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private List<String> normalizeDomains(List<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String domain = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
            if (!DOMAIN.matcher(domain).matches() || !domain.contains(".")) {
                throw new DomainException("SSO_ALLOWED_DOMAIN_INVALID", "身份源允许域名格式无效", HttpStatus.BAD_REQUEST);
            }
            normalized.add(domain);
        }
        return List.copyOf(normalized);
    }

    private List<String> normalizeCallbacks(List<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String callback = value == null ? "" : value.strip();
            if (!isSafeHttpUri(callback)) {
                throw new DomainException("SSO_CALLBACK_URI_INVALID", "身份源回调地址不安全", HttpStatus.BAD_REQUEST);
            }
            normalized.add(callback);
        }
        return List.copyOf(normalized);
    }

    private boolean isSafeHttpUri(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme())
                    && ("127.0.0.1".equals(uri.getHost()) || "localhost".equalsIgnoreCase(uri.getHost()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isSafeDirectoryUri(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return "ldaps".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getPort() != 0 && uri.getRawUserInfo() == null
                    && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && "".equals(uri.getRawPath());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private SsoProviderTestCheck passed(String code, String message) {
        return new SsoProviderTestCheck(code, "PASSED", message);
    }

    private SsoProviderTestCheck failed(String code, String message) {
        return new SsoProviderTestCheck(code, "FAILED", message);
    }

    private String stripNullable(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String stringValue(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString().strip();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot persist SSO provider configuration", exception);
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new DomainException("SSO_PROVIDER_CONFIGURATION_INVALID", "身份源配置无法读取", HttpStatus.CONFLICT);
        }
    }

    private List<String> readList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new DomainException("SSO_PROVIDER_CONFIGURATION_INVALID", "身份源配置无法读取", HttpStatus.CONFLICT);
        }
    }

    private Map<String, Object> readReport(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new DomainException("SSO_PROVIDER_TEST_REPORT_INVALID", "身份源检测报告无法读取", HttpStatus.CONFLICT);
        }
    }
}
