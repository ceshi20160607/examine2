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
    private final SecretReferenceResolver secretResolver;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final Clock clock = Clock.systemDefaultZone();

    public SsoProviderAdministrationService(
            PlatSsoProviderBaseService providerService,
            PlatSsoProviderVersionBaseService versionService,
            SecretReferenceResolver secretResolver,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder) {
        this.providerService = providerService;
        this.versionService = versionService;
        this.secretResolver = secretResolver;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    @Transactional(readOnly = true)
    public List<SsoProviderAdminView> list() {
        return providerService.selectList(Wrappers.<PlatSsoProvider>lambdaQuery()
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
                .eq(PlatSsoProvider::getCode, code)).isEmpty()) {
            throw new DomainException("SSO_PROVIDER_CODE_EXISTS", "身份源编码已经存在", HttpStatus.CONFLICT);
        }
        String protocol = normalizeProtocol(input.protocol());
        PlatSsoProvider provider = new PlatSsoProvider();
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
        PlatSsoProvider provider = requireProvider(providerId);
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
        PlatSsoProvider provider = requireProvider(providerId);
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
        version.setTestedAt(now);
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
        PlatSsoProvider provider = requireProvider(providerId);
        PlatSsoProviderVersion version = requireVersion(providerId, versionId);
        if (!"DRAFT".equals(version.getStatus())) {
            throw new DomainException("SSO_PROVIDER_VERSION_IMMUTABLE", "该身份源版本已经发布", HttpStatus.CONFLICT);
        }
        if (!"PASSED".equals(version.getTestStatus())) {
            throw new DomainException("SSO_PROVIDER_TEST_REQUIRED", "身份源版本必须检测通过后才能发布", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        version.setStatus("PUBLISHED");
        version.setPublishedAt(now);
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

    private List<SsoProviderTestCheck> runChecks(PlatSsoProviderVersion version) {
        List<SsoProviderTestCheck> checks = new ArrayList<>();
        Map<String, Object> config = readMap(version.getProtocolConfigJson());
        List<String> callbacks = readList(version.getCallbackUrisJson());
        Map<String, Object> attributeMapping = readMap(version.getAttributeMappingJson());
        String protocol = version.getProtocol();

        checks.add(passed("PROTOCOL_SUPPORTED", "协议 " + protocol + " 已支持"));
        checkEndpoints(protocol, config, checks);
        checkCallbacks(protocol, config, callbacks, checks);
        checkSecretReferences(protocol, version.getClientSecretRef(), config, checks);
        checkAttributeMapping(attributeMapping, checks);
        checkSignature(protocol, config, checks);
        checkConnection(protocol, config, checks);
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

    private void checkAttributeMapping(Map<String, Object> mapping, List<SsoProviderTestCheck> checks) {
        String externalUserId = stringValue(mapping.get("externalUserId"));
        checks.add(externalUserId == null
                ? failed("ATTRIBUTE_MAPPING_INVALID", "属性映射缺少 externalUserId")
                : passed("ATTRIBUTE_MAPPING", "外部用户标识属性映射有效"));
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
                Object keys = response == null ? null : response.get("keys");
                boolean valid = keys instanceof Collection<?> collection && !collection.isEmpty();
                checks.add(valid ? passed("JWKS_VALID", "JWKS 可访问且包含签名密钥")
                        : failed("JWKS_INVALID", "JWKS 未包含可用签名密钥"));
            } catch (RestClientException exception) {
                checks.add(failed("JWKS_UNAVAILABLE", "JWKS 当前不可访问"));
            }
            return;
        }
        if ("SAML2".equals(protocol)) {
            String reference = stringValue(config.get("certificateRef"));
            try {
                String certificate = secretResolver.resolve(reference);
                boolean valid = certificate != null && certificate.contains("BEGIN CERTIFICATE")
                        && certificate.contains("END CERTIFICATE");
                checks.add(valid ? passed("CERTIFICATE_VALID", "SAML 签名证书格式有效")
                        : failed("CERTIFICATE_INVALID", "SAML 签名证书格式无效"));
            } catch (DomainException exception) {
                checks.add(failed(exception.code(), "SAML 签名证书不可用"));
            }
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
        return toJson(metadata);
    }

    private PlatSsoProvider requireProvider(Long providerId) {
        PlatSsoProvider provider = providerService.selectById(providerId);
        if (provider == null) {
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
        return new SsoProviderAdminView(provider.getId(), provider.getCode(), provider.getName(), provider.getStatus(),
                provider.getPublishedVersionId(), provider.getPublishedVersionNumber(),
                versions(provider.getId()).stream().map(this::versionView).toList());
    }

    private SsoProviderVersionView versionView(PlatSsoProviderVersion version) {
        return new SsoProviderVersionView(version.getId(), version.getVersionNumber(), version.getProtocol(),
                version.getIssuer(), version.getClientId(), version.getClientSecretRef(),
                readMap(version.getProtocolConfigJson()), readList(version.getAllowedDomainsJson()),
                readMap(version.getAttributeMappingJson()), readMap(version.getJitPolicyJson()),
                readMap(version.getMfaPolicyJson()), readList(version.getCallbackUrisJson()),
                version.getTestStatus(), readReport(version.getTestReportJson()), version.getStatus(),
                version.getCreatedAt(), version.getPublishedAt());
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
            return "ldaps".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
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
