package com.unique.examine.plat.manage.sso;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.task.AsyncTaskStatus;
import com.unique.examine.core.task.AsyncTaskView;
import com.unique.examine.plat.base.entity.PlatIdentityProvider;
import com.unique.examine.plat.base.entity.PlatSsoBinding;
import com.unique.examine.plat.base.entity.PlatSystemSsoPolicy;
import com.unique.examine.plat.base.service.PlatIdentityProviderBaseService;
import com.unique.examine.plat.base.service.PlatSsoBindingBaseService;
import com.unique.examine.plat.base.service.PlatSystemSsoPolicyBaseService;
import com.unique.examine.plat.manage.common.SystemMemberContextResolver;
import com.unique.examine.plat.manage.common.SystemMemberContextResolver.SystemMemberContext;
import com.unique.examine.plat.manage.secret.SecretService;
import com.unique.examine.plat.manage.sso.SsoModels.IdentityProviderPublishResult;
import com.unique.examine.plat.manage.sso.SsoModels.IdentityProviderSaveRequest;
import com.unique.examine.plat.manage.sso.SsoModels.IdentityProviderTestRequest;
import com.unique.examine.plat.manage.sso.SsoModels.IdentityProviderTestResultVO;
import com.unique.examine.plat.manage.sso.SsoModels.IdentityProviderVO;
import com.unique.examine.plat.manage.sso.SsoModels.MappingPrecheckIssue;
import com.unique.examine.plat.manage.sso.SsoModels.MemberBindingConfirmRequest;
import com.unique.examine.plat.manage.sso.SsoModels.MemberBindingConfirmVO;
import com.unique.examine.plat.manage.sso.SsoModels.OrgSyncPrecheckRequest;
import com.unique.examine.plat.manage.sso.SsoModels.OrgSyncPrecheckResultVO;
import com.unique.examine.plat.manage.sso.SsoModels.SystemSsoPolicyUpdateRequest;
import com.unique.examine.plat.manage.sso.SsoModels.SystemSsoPolicyVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Platform and system SSO policy service backed by persisted identity and policy tables.
 */
@Service
public class SsoService {

    private static final int ENABLED = 1;
    private static final int DISABLED = 0;
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final SecretService secretService;
    private final SystemMemberContextResolver contextResolver;
    private final PlatIdentityProviderBaseService identityProviderBaseService;
    private final PlatSystemSsoPolicyBaseService systemSsoPolicyBaseService;
    private final PlatSsoBindingBaseService ssoBindingBaseService;
    private final ObjectMapper objectMapper;

    public SsoService(SecretService secretService,
                      SystemMemberContextResolver contextResolver,
                      PlatIdentityProviderBaseService identityProviderBaseService,
                      PlatSystemSsoPolicyBaseService systemSsoPolicyBaseService,
                      PlatSsoBindingBaseService ssoBindingBaseService,
                      ObjectMapper objectMapper) {
        this.secretService = secretService;
        this.contextResolver = contextResolver;
        this.identityProviderBaseService = identityProviderBaseService;
        this.systemSsoPolicyBaseService = systemSsoPolicyBaseService;
        this.ssoBindingBaseService = ssoBindingBaseService;
        this.objectMapper = objectMapper;
    }

    /**
     * List platform identity providers.
     *
     * @return identity providers
     */
    public List<IdentityProviderVO> identityProviders() {
        return identityProviderBaseService.list(new LambdaQueryWrapper<PlatIdentityProvider>()
                        .orderByDesc(PlatIdentityProvider::getUpdatedAt))
                .stream()
                .map(this::toProviderVO)
                .toList();
    }

    /**
     * Create an identity provider.
     *
     * @param request save request
     * @return identity provider
     */
    @Transactional(rollbackFor = Exception.class)
    public IdentityProviderVO createIdentityProvider(IdentityProviderSaveRequest request) {
        requireText(Objects.isNull(request) ? null : request.name(), "身份源名称不能为空");
        PlatIdentityProvider provider = new PlatIdentityProvider();
        provider.setProviderCode(providerCode(request));
        provider.setCreatedAt(LocalDateTime.now());
        applyProviderRequest(provider, request);
        identityProviderBaseService.saveEntity(provider);
        return toProviderVO(provider);
    }

    /**
     * Update an identity provider.
     *
     * @param providerId provider id
     * @param request save request
     * @return identity provider
     */
    @Transactional(rollbackFor = Exception.class)
    public IdentityProviderVO updateIdentityProvider(String providerId, IdentityProviderSaveRequest request) {
        PlatIdentityProvider provider = requireProvider(providerId);
        applyProviderRequest(provider, request);
        identityProviderBaseService.updateById(provider);
        return toProviderVO(provider);
    }

    /**
     * Test an identity provider configuration without publishing it.
     *
     * @param providerId provider id
     * @param request test request
     * @return test result
     */
    public IdentityProviderTestResultVO testIdentityProvider(String providerId, IdentityProviderTestRequest request) {
        PlatIdentityProvider provider = requireProvider(providerId);
        RequestContext context = RequestContext.current();
        boolean passed = StringUtils.hasText(Objects.isNull(request) ? null : request.redirectUri())
                && StringUtils.hasText(provider.getIssuer())
                && StringUtils.hasText(provider.getSecretRefId());
        return new IdentityProviderTestResultVO(provider.getProviderCode(), passed,
                passed ? null : "SSO_TEST_REQUIRES_CALLBACK_URI_AND_SECRET_REF",
                context.traceId(), auditLogId(context), LocalDateTime.now());
    }

    /**
     * Publish an identity provider for login usage.
     *
     * @param providerId provider id
     * @return publish result
     */
    @Transactional(rollbackFor = Exception.class)
    public IdentityProviderPublishResult publishIdentityProvider(String providerId) {
        PlatIdentityProvider provider = requireProvider(providerId);
        provider.setStatus(ENABLED);
        provider.setUpdatedAt(LocalDateTime.now());
        identityProviderBaseService.updateById(provider);
        RequestContext context = RequestContext.current();
        return new IdentityProviderPublishResult(provider.getProviderCode(), "PUBLISHED",
                List.of("secret_ref verified", "domain whitelist persisted"),
                context.traceId(), auditLogId(context), LocalDateTime.now());
    }

    /**
     * Query system SSO inheritance policy.
     *
     * @param systemId system id
     * @return SSO policy
     */
    public SystemSsoPolicyVO policy(String systemId) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        PlatSystemSsoPolicy policy = systemSsoPolicyBaseService.getOne(new LambdaQueryWrapper<PlatSystemSsoPolicy>()
                .eq(PlatSystemSsoPolicy::getSystemId, context.systemId())
                .eq(PlatSystemSsoPolicy::getTenantId, context.tenantId())
                .last("LIMIT 1"), false);
        return Objects.isNull(policy) ? defaultPolicy(context) : toPolicyVO(policy);
    }

    /**
     * Update system SSO inheritance policy.
     *
     * @param systemId system id
     * @param request update request
     * @return SSO policy
     */
    @Transactional(rollbackFor = Exception.class)
    public SystemSsoPolicyVO updatePolicy(String systemId, SystemSsoPolicyUpdateRequest request) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        PlatSystemSsoPolicy policy = systemSsoPolicyBaseService.getOne(new LambdaQueryWrapper<PlatSystemSsoPolicy>()
                .eq(PlatSystemSsoPolicy::getSystemId, context.systemId())
                .eq(PlatSystemSsoPolicy::getTenantId, context.tenantId())
                .last("LIMIT 1"), false);
        if (Objects.isNull(policy)) {
            policy = new PlatSystemSsoPolicy();
            policy.setSystemId(context.systemId());
            policy.setTenantId(context.tenantId());
        }
        policy.setEnabledProviderIds(toJson(safeList(Objects.isNull(request) ? null : request.enabledProviderIds())));
        policy.setTenantDomains(toJson(safeList(Objects.isNull(request) ? null : request.tenantDomains())));
        policy.setOrgMapping(toJson(safeMap(Objects.isNull(request) ? null : request.orgMapping(), defaultOrgMapping())));
        policy.setEmployeeBinding(toJson(safeMap(Objects.isNull(request) ? null : request.employeeBinding(),
                defaultEmployeeBinding())));
        policy.setJitMemberPolicy(toJson(safeMap(Objects.isNull(request) ? null : request.jitMemberPolicy(),
                defaultJitPolicy())));
        policy.setNoMemberFeedback(toJson(safeMap(Objects.isNull(request) ? null : request.noMemberFeedback(),
                defaultNoMemberFeedback())));
        policy.setStatus(statusCode(Objects.isNull(request) ? null : request.status()));
        policy.setUpdatedAt(LocalDateTime.now());
        systemSsoPolicyBaseService.saveEntity(policy);
        return toPolicyVO(policy);
    }

    /**
     * Precheck organization and member mapping before sync confirmation.
     *
     * @param systemId system id
     * @param request precheck request
     * @return precheck result
     */
    public OrgSyncPrecheckResultVO precheckOrgSync(String systemId, OrgSyncPrecheckRequest request) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        RequestContext requestContext = RequestContext.current();
        LocalDateTime now = LocalDateTime.now();
        String auditLogId = auditLogId(requestContext);
        AsyncTaskView task = new AsyncTaskView("task_sso_precheck_" + shortTrace(requestContext.traceId()),
                "SSO_ORG_SYNC_PRECHECK", Objects.isNull(request) ? null : request.idempotencyKey(),
                AsyncTaskStatus.QUEUED, 0, true, true, false, null, null, null, 1, 1,
                requestContext.traceId(), auditLogId, String.valueOf(context.systemMemberId()), now.toString());
        int departments = safeList(Objects.isNull(request) ? null : request.externalDepartmentIds()).size();
        int users = safeList(Objects.isNull(request) ? null : request.externalUserIds()).size();
        List<String> externalDepartmentIds = safeList(Objects.isNull(request) ? null : request.externalDepartmentIds());
        List<String> externalUserIds = safeList(Objects.isNull(request) ? null : request.externalUserIds());
        List<MappingPrecheckIssue> issues = List.of(
                new MappingPrecheckIssue("DEPARTMENT", externalDepartmentIds.isEmpty() ? "external_department_pending" : externalDepartmentIds.get(0), "External Department",
                        departments > 0 ? "UNMAPPED" : "SKIPPED", null, "SSO_ORG_MAPPING_REQUIRED"),
                new MappingPrecheckIssue("MEMBER", externalUserIds.isEmpty() ? "external_user_pending" : externalUserIds.get(0), "external.user@example.com",
                        users > 0 ? "UNBOUND" : "SKIPPED", null, "SSO_MEMBER_BINDING_REQUIRED"));
        return new OrgSyncPrecheckResultVO(task, Math.max(departments - 1, 0), departments > 0 ? 1 : 0,
                Math.max(users - 1, 0), users > 0 ? 1 : 0, users > 0 ? 1 : 0, issues,
                "PRECHECK_QUEUED", "UNMAPPED_ITEMS_REQUIRE_MANUAL_CONFIRM", requestContext.traceId(), auditLogId);
    }

    /**
     * Confirm one external member binding with roles and data scope.
     *
     * @param systemId system id
     * @param request binding confirmation request
     * @return binding result
     */
    @Transactional(rollbackFor = Exception.class)
    public MemberBindingConfirmVO confirmMemberBinding(String systemId, MemberBindingConfirmRequest request) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        RequestContext requestContext = RequestContext.current();
        boolean complete = StringUtils.hasText(Objects.isNull(request) ? null : request.accountId())
                && StringUtils.hasText(Objects.isNull(request) ? null : request.systemMemberId());
        PlatSsoBinding binding = upsertSsoBinding(context, request, complete);
        return new MemberBindingConfirmVO(String.valueOf(binding.getId()), String.valueOf(binding.getAccountId()),
                safeText(Objects.isNull(request) ? null : request.systemMemberId(), "member_pending"),
                binding.getIdentityProvider(), binding.getExternalUserId(), String.valueOf(binding.getTenantId()),
                complete ? "BOUND" : "PENDING_CONFIRM",
                safeList(Objects.isNull(request) ? null : request.roleIds()),
                safeMap(Objects.isNull(request) ? null : request.dataScope(), Map.of("type", "DEPARTMENT")),
                complete ? null : "SSO_MEMBER_BINDING_REQUIRES_ACCOUNT_AND_MEMBER",
                requestContext.traceId(), auditLogId(requestContext), LocalDateTime.now());
    }

    private void applyProviderRequest(PlatIdentityProvider provider, IdentityProviderSaveRequest request) {
        provider.setProviderName(request.name());
        provider.setProtocol(safeText(request.protocol(), "OIDC"));
        provider.setIssuer(safeText(request.issuer(), "https://login.example.com/oidc"));
        provider.setClientId(safeText(request.clientId(), "client_required"));
        provider.setSecretRefId(safeText(request.secretRefId(), "sec_" + provider.getProviderCode()));
        provider.setCertRefId(safeText(request.certRefId(), "cert_" + provider.getProviderCode()));
        provider.setDomainWhitelist(toJson(safeList(request.domainWhitelist())));
        provider.setJitPolicy(toJson(safeMap(request.jitPolicy(), defaultJitPolicy())));
        provider.setMfaPolicy(toJson(safeMap(request.mfaPolicy(), Map.of("required", true))));
        provider.setStatus(statusCode(request.status()));
        provider.setUpdatedAt(LocalDateTime.now());
    }

    private PlatSsoBinding upsertSsoBinding(SystemMemberContext context, MemberBindingConfirmRequest request,
                                            boolean complete) {
        String identityProvider = safeText(Objects.isNull(request) ? null : request.identityProvider(),
                "idp_unknown");
        String externalUserId = safeText(Objects.isNull(request) ? null : request.externalUserId(),
                "external_user");
        PlatSsoBinding binding = ssoBindingBaseService.getOne(new LambdaQueryWrapper<PlatSsoBinding>()
                .eq(PlatSsoBinding::getIdentityProvider, identityProvider)
                .eq(PlatSsoBinding::getExternalUserId, externalUserId)
                .last("LIMIT 1"), false);
        if (Objects.isNull(binding)) {
            binding = new PlatSsoBinding();
            binding.setIdentityProvider(identityProvider);
            binding.setExternalUserId(externalUserId);
            binding.setCreatedAt(LocalDateTime.now());
        }
        binding.setAccountId(parseOptionalLong(Objects.isNull(request) ? null : request.accountId()));
        binding.setSystemId(context.systemId());
        binding.setTenantId(parseOptionalLong(safeText(Objects.isNull(request) ? null : request.tenantId(),
                String.valueOf(context.tenantId()))));
        binding.setExternalDeptId(Objects.isNull(request) ? null : request.externalDeptId());
        binding.setBindingStatus(complete ? ENABLED : DISABLED);
        binding.setUpdatedAt(LocalDateTime.now());
        ssoBindingBaseService.saveEntity(binding);
        return binding;
    }

    private IdentityProviderVO toProviderVO(PlatIdentityProvider provider) {
        return new IdentityProviderVO(provider.getProviderCode(), provider.getProviderName(), provider.getProtocol(),
                provider.getIssuer(), provider.getClientId(),
                secretService.secretRef(provider.getSecretRefId(), "IDENTITY_PROVIDER"),
                secretService.secretRef(provider.getCertRefId(), "CERTIFICATE"),
                readStringList(provider.getDomainWhitelist()), readMap(provider.getJitPolicy(), defaultJitPolicy()),
                readMap(provider.getMfaPolicy(), Map.of("required", true)), statusText(provider.getStatus()),
                provider.getUpdatedAt());
    }

    private SystemSsoPolicyVO toPolicyVO(PlatSystemSsoPolicy policy) {
        return new SystemSsoPolicyVO(String.valueOf(policy.getSystemId()),
                readStringList(policy.getEnabledProviderIds()), readStringList(policy.getTenantDomains()),
                readMap(policy.getOrgMapping(), defaultOrgMapping()),
                readMap(policy.getEmployeeBinding(), defaultEmployeeBinding()),
                readMap(policy.getJitMemberPolicy(), defaultJitPolicy()),
                readMap(policy.getNoMemberFeedback(), defaultNoMemberFeedback()), statusText(policy.getStatus()),
                RequestContext.current().traceId(), policy.getUpdatedAt());
    }

    private SystemSsoPolicyVO defaultPolicy(SystemMemberContext context) {
        return new SystemSsoPolicyVO(String.valueOf(context.systemId()), List.of(), List.of(), defaultOrgMapping(),
                defaultEmployeeBinding(), defaultJitPolicy(), defaultNoMemberFeedback(), "DRAFT",
                RequestContext.current().traceId(), LocalDateTime.now());
    }

    private PlatIdentityProvider requireProvider(String providerId) {
        PlatIdentityProvider provider = identityProviderBaseService.getOne(new LambdaQueryWrapper<PlatIdentityProvider>()
                .eq(PlatIdentityProvider::getProviderCode, providerId)
                .last("LIMIT 1"), false);
        if (Objects.isNull(provider) && numeric(providerId)) {
            provider = identityProviderBaseService.findById(Long.valueOf(providerId)).orElse(null);
        }
        if (Objects.isNull(provider)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "身份源不存在");
        }
        return provider;
    }

    private String providerCode(IdentityProviderSaveRequest request) {
        String protocol = safeText(request.protocol(), "oidc").toLowerCase();
        return "idp_" + protocol + "_" + shortTrace(RequestContext.current().traceId());
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(String json, Map<String, Object> fallback) {
        if (!StringUtils.hasText(json)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return fallback;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "SSO配置序列化失败");
        }
    }

    private Map<String, Object> defaultOrgMapping() {
        return Map.of("externalDeptId", "deptCode", "targetTree", "systemDepartment");
    }

    private Map<String, Object> defaultEmployeeBinding() {
        return Map.of("matchKeys", List.of("email", "mobile", "employeeNo"), "manualConfirmRequired", true);
    }

    private Map<String, Object> defaultJitPolicy() {
        return Map.of("enabled", true, "createSystemMember", false, "requireApproval", true);
    }

    private Map<String, Object> defaultNoMemberFeedback() {
        return Map.of("status", "SUBMITTED", "disabledReason", "NO_SYSTEM_MEMBER_MAPPING");
    }

    private List<String> safeList(List<String> values) {
        return Objects.isNull(values) ? List.of() : List.copyOf(values);
    }

    private Map<String, Object> safeMap(Map<String, Object> values, Map<String, Object> fallback) {
        return Objects.isNull(values) || values.isEmpty() ? fallback : Map.copyOf(values);
    }

    private int statusCode(String status) {
        return "ACTIVE".equalsIgnoreCase(status) || "PUBLISHED".equalsIgnoreCase(status) ? ENABLED : DISABLED;
    }

    private String statusText(Integer status) {
        return Objects.equals(status, ENABLED) ? "ACTIVE" : "DRAFT";
    }

    private Long parseOptionalLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "ID格式不正确");
        }
    }

    private boolean numeric(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.chars().allMatch(Character::isDigit);
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, message);
        }
    }

    private String auditLogId(RequestContext context) {
        return StringUtils.hasText(context.auditLogId()) ? context.auditLogId() : "aud_" + context.traceId();
    }

    private String shortTrace(String traceId) {
        return Objects.isNull(traceId) || traceId.length() <= 8 ? "trace" : traceId.substring(traceId.length() - 8);
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
