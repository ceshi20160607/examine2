package com.unique.unexamine.application.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.application.base.entity.AppCallback;
import com.unique.unexamine.application.base.entity.AppCall;
import com.unique.unexamine.application.base.entity.AppCredential;
import com.unique.unexamine.application.base.entity.AppDefinition;
import com.unique.unexamine.application.base.entity.AppGrant;
import com.unique.unexamine.application.base.entity.AppGrantField;
import com.unique.unexamine.application.base.entity.AppPublication;
import com.unique.unexamine.application.base.entity.AppVersion;
import com.unique.unexamine.application.base.service.AppCallbackBaseService;
import com.unique.unexamine.application.base.service.AppCallBaseService;
import com.unique.unexamine.application.base.service.AppCredentialBaseService;
import com.unique.unexamine.application.base.service.AppDefinitionBaseService;
import com.unique.unexamine.application.base.service.AppGrantBaseService;
import com.unique.unexamine.application.base.service.AppGrantFieldBaseService;
import com.unique.unexamine.application.base.service.AppPublicationBaseService;
import com.unique.unexamine.application.base.service.AppVersionBaseService;
import com.unique.unexamine.audit.base.entity.AuditEvent;
import com.unique.unexamine.audit.base.service.AuditEventBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authentication.manage.Pbkdf2PasswordHasher;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.flow.base.entity.FlowDefinition;
import com.unique.unexamine.flow.base.entity.FlowPublication;
import com.unique.unexamine.flow.base.service.FlowDefinitionBaseService;
import com.unique.unexamine.flow.base.service.FlowPublicationBaseService;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModulePublication;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModulePublicationBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class ApplicationManagementService {
    private static final Set<String> APPLICATION_TYPES = Set.of("SERVICE", "WEBHOOK");
    private static final Set<String> PLATFORM_RESOURCE_TYPES = Set.of("FLOW", "AI");
    private static final Set<String> SYSTEM_RESOURCE_TYPES = Set.of("MODULE", "FLOW");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() { };

    private final AppDefinitionBaseService definitionService;
    private final AppCallbackBaseService callbackService;
    private final AppGrantBaseService grantService;
    private final AppGrantFieldBaseService fieldService;
    private final AppVersionBaseService versionService;
    private final AppPublicationBaseService publicationService;
    private final AppCredentialBaseService credentialService;
    private final AppCallBaseService callService;
    private final AuditEventBaseService auditEventService;
    private final FlowDefinitionBaseService flowDefinitionService;
    private final FlowPublicationBaseService flowPublicationService;
    private final ConfiguredModuleBaseService moduleService;
    private final ConfiguredModulePublicationBaseService modulePublicationService;
    private final PermissionChecker permissionChecker;
    private final Pbkdf2PasswordHasher secretHasher;
    private final ApplicationSecretCipher secretCipher;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApplicationManagementService(
            AppDefinitionBaseService definitionService,
            AppCallbackBaseService callbackService,
            AppGrantBaseService grantService,
            AppGrantFieldBaseService fieldService,
            AppVersionBaseService versionService,
            AppPublicationBaseService publicationService,
            AppCredentialBaseService credentialService,
            AppCallBaseService callService,
            AuditEventBaseService auditEventService,
            FlowDefinitionBaseService flowDefinitionService,
            FlowPublicationBaseService flowPublicationService,
            ConfiguredModuleBaseService moduleService,
            ConfiguredModulePublicationBaseService modulePublicationService,
            PermissionChecker permissionChecker,
            Pbkdf2PasswordHasher secretHasher,
            ApplicationSecretCipher secretCipher,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.definitionService = definitionService;
        this.callbackService = callbackService;
        this.grantService = grantService;
        this.fieldService = fieldService;
        this.versionService = versionService;
        this.publicationService = publicationService;
        this.credentialService = credentialService;
        this.callService = callService;
        this.auditEventService = auditEventService;
        this.flowDefinitionService = flowDefinitionService;
        this.flowPublicationService = flowPublicationService;
        this.moduleService = moduleService;
        this.modulePublicationService = modulePublicationService;
        this.permissionChecker = permissionChecker;
        this.secretHasher = secretHasher;
        this.secretCipher = secretCipher;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ApplicationModels.ApplicationView> list(AuthenticatedContext context) {
        requireAction(context, "VIEW");
        return scopedDefinitions(context).stream()
                .sorted(Comparator.comparing(AppDefinition::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(application -> view(application, false)).toList();
    }

    @Transactional
    public ApplicationModels.CreateResult create(
            AuthenticatedContext context, ApplicationModels.CreateRequest input, String traceId) {
        requireAction(context, "MANAGE");
        String code = input.code().strip().toLowerCase(Locale.ROOT);
        if (scopedDefinitions(context).stream().anyMatch(item -> code.equals(item.getCode()))) {
            throw new DomainException("APPLICATION_CODE_CONFLICT", "当前范围已存在相同应用编码", HttpStatus.CONFLICT);
        }
        AppDefinition application = new AppDefinition();
        bindContext(application, context);
        application.setCode(code);
        application.setName(input.name().strip());
        application.setDescription(blankToNull(input.description()));
        application.setApplicationType(normalizeApplicationType(input.applicationType()));
        application.setDraftRevision(1);
        application.setStatus("DRAFT");
        application.setCreatedByAccountId(context.accountId());
        application.setVersion(0);
        definitionService.insert(application);

        ApplicationModels.CredentialSecret issued = issueCredential(application, context.accountId(), 1);
        audit(context, traceId, "APPLICATION_DRAFT_CREATED", application.getId(), Map.of(
                "code", code,
                "contextType", application.getContextType(),
                "credentialVersion", 1));
        return new ApplicationModels.CreateResult(view(application, true), issued);
    }

    @Transactional(readOnly = true)
    public ApplicationModels.ApplicationView detail(AuthenticatedContext context, Long applicationId) {
        requireAction(context, "VIEW");
        return view(requireOwned(context, applicationId), true);
    }

    @Transactional(readOnly = true)
    public List<ApplicationBridgeModels.CallLogView> calls(AuthenticatedContext context, Long applicationId) {
        requireAction(context, "VIEW");
        requireOwned(context, applicationId);
        return callService.selectList(Wrappers.<AppCall>lambdaQuery()
                        .eq(AppCall::getApplicationId, applicationId)
                        .orderByDesc(AppCall::getCalledAt, AppCall::getId))
                .stream().map(call -> new ApplicationBridgeModels.CallLogView(
                        call.getId(), call.getRequestId(), call.getTraceId(), call.getCredentialVersion(),
                        call.getGrantId(), call.getSourceAddress(), call.getTargetSystemId(), call.getTargetTenantId(),
                        call.getResourceType(), call.getResourceId(), call.getActionCode(), call.getStatus(),
                        call.getResponseCode(), call.getDurationMillis(), call.getErrorMessage(),
                        call.getTargetReference(), call.getReplayCount(), readMap(call.getPermissionSnapshotJson()),
                        readObject(call.getResponseJson()), call.getCalledAt(), call.getFinishedAt())).toList();
    }

    @Transactional
    public ApplicationModels.ApplicationView saveDraft(
            AuthenticatedContext context,
            Long applicationId,
            ApplicationModels.SaveDraftRequest input,
            String traceId) {
        requireAction(context, "MANAGE");
        AppDefinition application = requireOwned(context, applicationId);
        if ("DISABLED".equals(application.getStatus())) {
            throw conflict("APPLICATION_DISABLED", "已停用应用不能继续修改草稿");
        }
        if (!Objects.equals(application.getVersion(), input.expectedVersion())) {
            throw conflict("APPLICATION_VERSION_CONFLICT", "应用已被其他人修改，请刷新后重试");
        }
        List<ApplicationModels.CallbackInput> callbacks = input.callbacks() == null ? List.of() : input.callbacks();
        List<ApplicationModels.GrantInput> grants = input.grants() == null ? List.of() : input.grants();
        validateDraftInputs(context, callbacks, grants);

        replaceCallbacks(applicationId, callbacks);
        replaceGrants(applicationId, grants);
        application.setName(input.name().strip());
        application.setDescription(blankToNull(input.description()));
        application.setApplicationType(normalizeApplicationType(input.applicationType()));
        application.setDraftRevision(application.getDraftRevision() + 1);
        if (definitionService.updateById(application) != 1) {
            throw conflict("APPLICATION_VERSION_CONFLICT", "应用已被其他人修改，请刷新后重试");
        }
        audit(context, traceId, "APPLICATION_DRAFT_SAVED", applicationId, Map.of(
                "draftRevision", application.getDraftRevision(),
                "grantCount", grants.size(),
                "callbackCount", callbacks.size()));
        return view(requireOwned(context, applicationId), true);
    }

    @Transactional(readOnly = true)
    public ApplicationModels.PublicationCheck publicationCheck(AuthenticatedContext context, Long applicationId) {
        requireAction(context, "PUBLISH");
        AppDefinition application = requireOwned(context, applicationId);
        List<ApplicationModels.Issue> issues = publicationIssues(context, application);
        return new ApplicationModels.PublicationCheck(applicationId, application.getDraftRevision(), issues.isEmpty(), issues);
    }

    @Transactional
    public ApplicationModels.PublishResult publish(
            AuthenticatedContext context,
            Long applicationId,
            ApplicationModels.PublishRequest input,
            String traceId) {
        requireAction(context, "PUBLISH");
        AppDefinition application = requireOwned(context, applicationId);
        if ("DISABLED".equals(application.getStatus())) {
            throw conflict("APPLICATION_DISABLED", "已停用应用不能发布新版本");
        }
        if (!Objects.equals(application.getDraftRevision(), input.expectedDraftRevision())) {
            throw conflict("APPLICATION_DRAFT_REVISION_CONFLICT", "应用草稿修订号已变化，请重新检查后发布");
        }
        List<ApplicationModels.Issue> issues = publicationIssues(context, application);
        if (!issues.isEmpty()) {
            throw new DomainException("APPLICATION_PUBLICATION_INVALID", "应用发布检查未通过", HttpStatus.UNPROCESSABLE_ENTITY,
                    Map.of("issues", issues));
        }

        AppPublication current = publication(applicationId);
        Long previousVersionId = current == null ? null : current.getCurrentVersionId();
        int versionNumber = versions(applicationId).stream().map(AppVersion::getVersionNumber)
                .max(Integer::compareTo).orElse(0) + 1;
        Map<String, Object> snapshot = snapshot(application);
        String snapshotJson = writeJson(snapshot);

        AppVersion version = new AppVersion();
        version.setApplicationId(applicationId);
        version.setVersionNumber(versionNumber);
        version.setDraftRevision(application.getDraftRevision());
        version.setSnapshotHash(sha256(snapshotJson));
        version.setSnapshotJson(snapshotJson);
        version.setPublishedByAccountId(context.accountId());
        versionService.insert(version);

        if (current == null) {
            current = new AppPublication();
            current.setApplicationId(applicationId);
            current.setCurrentVersionId(version.getId());
            current.setUpdatedByAccountId(context.accountId());
            current.setVersion(0);
            publicationService.insert(current);
        } else {
            current.setCurrentVersionId(version.getId());
            current.setUpdatedByAccountId(context.accountId());
            if (publicationService.updateById(current) != 1) {
                throw conflict("APPLICATION_PUBLICATION_CONFLICT", "应用发布指针已变化，请重试");
            }
        }
        application.setStatus("ACTIVE");
        if (definitionService.updateById(application) != 1) {
            throw conflict("APPLICATION_VERSION_CONFLICT", "应用状态已变化，请重试");
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("versionId", version.getId());
        detail.put("versionNumber", versionNumber);
        detail.put("snapshotHash", version.getSnapshotHash());
        detail.put("changeSummary", input.changeSummary().strip());
        detail.put("previousVersionId", previousVersionId);
        audit(context, traceId, "APPLICATION_VERSION_PUBLISHED", applicationId, detail);
        return new ApplicationModels.PublishResult(view(requireOwned(context, applicationId), true),
                version.getId(), versionNumber, version.getSnapshotHash(), previousVersionId);
    }

    @Transactional
    public ApplicationModels.RotateResult rotateCredential(
            AuthenticatedContext context, Long applicationId, String traceId) {
        requireAction(context, "ROTATE");
        AppDefinition application = requireOwned(context, applicationId);
        if (!"ACTIVE".equals(application.getStatus()) || publication(applicationId) == null) {
            throw conflict("APPLICATION_NOT_ACTIVE", "只有已发布且启用的应用可以轮换凭证");
        }
        int nextVersion = credentials(applicationId).stream().map(AppCredential::getCredentialVersion)
                .max(Integer::compareTo).orElse(0) + 1;
        ApplicationModels.CredentialSecret issued = issueCredential(application, context.accountId(), nextVersion);
        LocalDateTime now = LocalDateTime.now();
        for (AppCredential credential : credentials(applicationId)) {
            if (!Objects.equals(credential.getId(), issued.credentialId()) && "ACTIVE".equals(credential.getStatus())) {
                credential.setStatus("REVOKED");
                credential.setRevokedAt(now);
                credentialService.updateById(credential);
            }
        }
        audit(context, traceId, "APPLICATION_CREDENTIAL_ROTATED", applicationId, Map.of(
                "credentialVersion", nextVersion,
                "clientId", issued.clientId(),
                "revokedPreviousCredentials", true));
        return new ApplicationModels.RotateResult(view(application, true), issued);
    }

    @Transactional
    public ApplicationModels.ApplicationView disable(
            AuthenticatedContext context,
            Long applicationId,
            ApplicationModels.DisableRequest input,
            String traceId) {
        requireAction(context, "DISABLE");
        AppDefinition application = requireOwned(context, applicationId);
        if ("DISABLED".equals(application.getStatus())) {
            return view(application, true);
        }
        application.setStatus("DISABLED");
        if (definitionService.updateById(application) != 1) {
            throw conflict("APPLICATION_VERSION_CONFLICT", "应用状态已变化，请重试");
        }
        LocalDateTime now = LocalDateTime.now();
        for (AppCredential credential : credentials(applicationId)) {
            if ("ACTIVE".equals(credential.getStatus())) {
                credential.setStatus("REVOKED");
                credential.setRevokedAt(now);
                credentialService.updateById(credential);
            }
        }
        audit(context, traceId, "APPLICATION_DISABLED", applicationId, Map.of(
                "reason", input.reason().strip(),
                "newCallsAllowed", false));
        return view(requireOwned(context, applicationId), true);
    }

    private void validateDraftInputs(
            AuthenticatedContext context,
            List<ApplicationModels.CallbackInput> callbacks,
            List<ApplicationModels.GrantInput> grants) {
        for (ApplicationModels.CallbackInput callback : callbacks) {
            if (!callback.url().strip().toLowerCase(Locale.ROOT).startsWith("https://")) {
                throw invalid("APPLICATION_CALLBACK_HTTPS_REQUIRED", "回调地址必须使用 HTTPS");
            }
        }
        for (ApplicationModels.GrantInput grant : grants) {
            validateGrantContext(context, grant);
            if (grant.dataScope().isEmpty()) {
                throw invalid("APPLICATION_DATA_SCOPE_REQUIRED", "每项授权必须明确数据范围");
            }
            Number maxRequests = number(grant.rateLimit().get("maxRequests"));
            Number windowSeconds = number(grant.rateLimit().get("windowSeconds"));
            if (maxRequests == null || maxRequests.longValue() < 1 || windowSeconds == null || windowSeconds.longValue() < 1) {
                throw invalid("APPLICATION_CALL_CONTROL_REQUIRED", "每项授权必须配置正数限流次数和时间窗口");
            }
            if (!(grant.rateLimit().get("allowedIps") instanceof List<?> allowedIps) || allowedIps.isEmpty()) {
                throw invalid("APPLICATION_IP_ALLOWLIST_REQUIRED", "每项授权必须配置至少一个来源 IP 白名单");
            }
        }
    }

    private void validateGrantContext(AuthenticatedContext context, ApplicationModels.GrantInput grant) {
        String resourceType = grant.resourceType().strip().toUpperCase(Locale.ROOT);
        if (context.systemId() == null) {
            if (!"PLATFORM".equals(grant.targetType()) || grant.targetSystemId() != null || grant.targetTenantId() != null) {
                throw invalid("APPLICATION_PLATFORM_SCOPE_INVALID", "平台应用只能授权平台资源，不能声明系统或租户范围");
            }
            if (!PLATFORM_RESOURCE_TYPES.contains(resourceType)) {
                throw invalid("APPLICATION_PLATFORM_RESOURCE_INVALID", "平台应用只能授权平台 Flow 或 AI 能力");
            }
            return;
        }
        if (!"SYSTEM".equals(grant.targetType())
                || !Objects.equals(context.systemId(), grant.targetSystemId())
                || !Objects.equals(context.tenantId(), grant.targetTenantId())) {
            throw invalid("APPLICATION_SYSTEM_SCOPE_FIXED", "系统应用授权范围必须固定为当前系统和租户");
        }
        if (!SYSTEM_RESOURCE_TYPES.contains(resourceType)) {
            throw invalid("APPLICATION_SYSTEM_RESOURCE_INVALID", "系统应用只能授权当前系统模块或 Flow 能力");
        }
    }

    private List<ApplicationModels.Issue> publicationIssues(AuthenticatedContext context, AppDefinition application) {
        List<ApplicationModels.Issue> issues = new ArrayList<>();
        if (application.getCode() == null || application.getName() == null
                || !APPLICATION_TYPES.contains(application.getApplicationType())) {
            issues.add(new ApplicationModels.Issue("APPLICATION_IDENTITY_INCOMPLETE", "应用身份信息不完整"));
        }
        if (credentials(application.getId()).stream().noneMatch(item ->
                "ACTIVE".equals(item.getStatus()) && item.getSigningSecretRef() != null)) {
            issues.add(new ApplicationModels.Issue("APPLICATION_CREDENTIAL_REQUIRED", "应用没有可验签的有效凭证版本"));
        }
        List<AppGrant> grants = grants(application.getId());
        if (grants.isEmpty()) {
            issues.add(new ApplicationModels.Issue("APPLICATION_GRANT_REQUIRED", "至少配置一项资源动作授权"));
        }
        for (AppGrant grant : grants) {
            try {
                validatePersistedGrant(context, grant);
            } catch (DomainException exception) {
                issues.add(new ApplicationModels.Issue(exception.code(), exception.getMessage()));
            }
        }
        for (AppCallback callback : callbacks(application.getId())) {
            if (callback.getUrl() == null || !callback.getUrl().toLowerCase(Locale.ROOT).startsWith("https://")
                    || callback.getSigningSecretRef() == null) {
                issues.add(new ApplicationModels.Issue("APPLICATION_CALLBACK_INVALID", "回调地址或签名密钥引用不完整"));
            }
        }
        return issues;
    }

    private void validatePersistedGrant(AuthenticatedContext context, AppGrant grant) {
        validateGrantContext(context, new ApplicationModels.GrantInput(
                grant.getTargetType(), grant.getTargetSystemId(), grant.getTargetTenantId(),
                grant.getResourceType(), grant.getResourceId(), grant.getActionCode(),
                readMap(grant.getDataScopeJson()), readMap(grant.getRateLimitJson()), List.of()));
        if (readMap(grant.getDataScopeJson()).isEmpty()) {
            throw invalid("APPLICATION_DATA_SCOPE_REQUIRED", "每项授权必须明确数据范围");
        }
        Map<String, Object> rateLimit = readMap(grant.getRateLimitJson());
        if (number(rateLimit.get("maxRequests")) == null || number(rateLimit.get("windowSeconds")) == null) {
            throw invalid("APPLICATION_CALL_CONTROL_REQUIRED", "每项授权必须配置调用控制");
        }
        if (!(rateLimit.get("allowedIps") instanceof List<?> allowedIps) || allowedIps.isEmpty()) {
            throw invalid("APPLICATION_IP_ALLOWLIST_REQUIRED", "授权来源 IP 白名单不完整");
        }
        validatePublishedTarget(context, grant);
    }

    private void validatePublishedTarget(AuthenticatedContext context, AppGrant grant) {
        String resourceType = grant.getResourceType().toUpperCase(Locale.ROOT);
        if ("FLOW".equals(resourceType)) {
            FlowDefinition flow = flowDefinitionService.selectList(Wrappers.<FlowDefinition>lambdaQuery()
                            .eq(FlowDefinition::getContextType, context.systemId() == null ? "PLATFORM" : "SYSTEM")
                            .eq(FlowDefinition::getPlatformId, context.platformId())
                            .eq(context.systemId() != null, FlowDefinition::getSystemId, context.systemId())
                            .isNull(context.systemId() == null, FlowDefinition::getSystemId)
                            .eq(context.tenantId() != null, FlowDefinition::getOwnerTenantId, context.tenantId())
                            .isNull(context.tenantId() == null, FlowDefinition::getOwnerTenantId)
                            .eq(FlowDefinition::getCode, grant.getResourceId()))
                    .stream().findFirst().orElse(null);
            boolean published = flow != null && "PUBLISHED".equals(flow.getStatus())
                    && !flowPublicationService.selectList(Wrappers.<FlowPublication>lambdaQuery()
                            .eq(FlowPublication::getFlowId, flow.getId())
                            .isNotNull(FlowPublication::getCurrentVersionId)).isEmpty();
            if (!published) {
                throw invalid("APPLICATION_TARGET_RESOURCE_NOT_PUBLISHED",
                        "应用授权引用的 Flow 不存在或尚未发布：" + grant.getResourceId());
            }
            return;
        }
        if ("MODULE".equals(resourceType)) {
            ConfiguredModule module = moduleService.selectList(Wrappers.<ConfiguredModule>lambdaQuery()
                            .eq(ConfiguredModule::getSystemId, context.systemId())
                            .eq(ConfiguredModule::getOwnerTenantId, context.tenantId())
                            .eq(ConfiguredModule::getCode, grant.getResourceId()))
                    .stream().findFirst().orElse(null);
            boolean published = module != null && !modulePublicationService.selectList(
                    Wrappers.<ConfiguredModulePublication>lambdaQuery()
                            .eq(ConfiguredModulePublication::getSystemId, context.systemId())
                            .eq(ConfiguredModulePublication::getOwnerTenantId, context.tenantId())
                            .eq(ConfiguredModulePublication::getModuleId, module.getId())
                            .isNotNull(ConfiguredModulePublication::getCurrentVersionId)).isEmpty();
            if (!published) {
                throw invalid("APPLICATION_TARGET_RESOURCE_NOT_PUBLISHED",
                        "应用授权引用的模块不存在或尚未发布：" + grant.getResourceId());
            }
            return;
        }
        throw invalid("APPLICATION_TARGET_RESOURCE_NOT_PUBLISHED",
                "应用授权引用的 AI 能力尚未形成可发布资源：" + grant.getResourceId());
    }

    private void replaceCallbacks(Long applicationId, List<ApplicationModels.CallbackInput> inputs) {
        for (AppCallback callback : callbacks(applicationId)) {
            callbackService.deleteById(callback.getId());
        }
        for (ApplicationModels.CallbackInput input : inputs) {
            AppCallback callback = new AppCallback();
            callback.setApplicationId(applicationId);
            callback.setCallbackType(input.callbackType());
            callback.setUrl(input.url().strip());
            callback.setEventCodesJson(writeJson(input.eventCodes().stream().map(String::strip).distinct().sorted().toList()));
            callback.setSigningSecretRef(input.signingSecretRef().strip());
            callback.setTimeoutMillis(input.timeoutMillis());
            callback.setMaxAttempts(input.maxAttempts());
            callback.setStatus("ACTIVE");
            callback.setVersion(0);
            callbackService.insert(callback);
        }
    }

    private void replaceGrants(Long applicationId, List<ApplicationModels.GrantInput> inputs) {
        for (AppGrant grant : grants(applicationId)) {
            for (AppGrantField field : fields(grant.getId())) {
                fieldService.deleteById(field.getId());
            }
            grantService.deleteById(grant.getId());
        }
        for (ApplicationModels.GrantInput input : inputs) {
            AppGrant grant = new AppGrant();
            grant.setApplicationId(applicationId);
            grant.setTargetType(input.targetType());
            grant.setTargetSystemId(input.targetSystemId());
            grant.setTargetTenantId(input.targetTenantId());
            grant.setResourceType(input.resourceType().strip().toUpperCase(Locale.ROOT));
            grant.setResourceId(input.resourceId().strip());
            grant.setActionCode(input.actionCode().strip().toUpperCase(Locale.ROOT));
            grant.setDataScopeJson(writeJson(input.dataScope()));
            grant.setRateLimitJson(writeJson(input.rateLimit()));
            grant.setStatus("ACTIVE");
            grant.setVersion(0);
            grantService.insert(grant);
            for (ApplicationModels.FieldInput inputField : input.fields() == null ? List.<ApplicationModels.FieldInput>of() : input.fields()) {
                AppGrantField field = new AppGrantField();
                field.setGrantId(grant.getId());
                field.setFieldCode(inputField.fieldCode().strip());
                field.setReadable(inputField.readable());
                field.setWritable(inputField.writable());
                field.setMaskStrategy(blankToNull(inputField.maskStrategy()));
                fieldService.insert(field);
            }
        }
    }

    private ApplicationModels.CredentialSecret issueCredential(AppDefinition application, Long accountId, int version) {
        byte[] secretBytes = new byte[32];
        secureRandom.nextBytes(secretBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        String clientId = "app_" + UUID.randomUUID().toString().replace("-", "");
        String hint = secret.substring(secret.length() - 6);
        AppCredential credential = new AppCredential();
        credential.setApplicationId(application.getId());
        credential.setCredentialVersion(version);
        credential.setClientId(clientId);
        credential.setSecretHash(secretHasher.hash(secret.toCharArray()));
        credential.setSigningSecretRef(secretCipher.encrypt(secret, application.getId(), version));
        credential.setSecretHint(hint);
        credential.setValidFrom(LocalDateTime.now());
        credential.setStatus("ACTIVE");
        credential.setCreatedByAccountId(accountId);
        credentialService.insert(credential);
        return new ApplicationModels.CredentialSecret(
                credential.getId(), version, clientId, secret,
                secretCipher.displayReference(application.getId(), version), hint, true);
    }

    private Map<String, Object> snapshot(AppDefinition application) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("applicationId", application.getId());
        snapshot.put("contextType", application.getContextType());
        snapshot.put("platformId", application.getPlatformId());
        snapshot.put("ownerSystemId", application.getOwnerSystemId());
        snapshot.put("ownerTenantId", application.getOwnerTenantId());
        snapshot.put("code", application.getCode());
        snapshot.put("name", application.getName());
        snapshot.put("description", application.getDescription());
        snapshot.put("applicationType", application.getApplicationType());
        snapshot.put("draftRevision", application.getDraftRevision());
        snapshot.put("callbacks", callbacks(application.getId()).stream().map(this::callbackSnapshot).toList());
        snapshot.put("grants", grants(application.getId()).stream().map(this::grantSnapshot).toList());
        snapshot.put("credentials", credentials(application.getId()).stream()
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .map(item -> Map.of(
                        "credentialVersion", item.getCredentialVersion(),
                        "clientId", item.getClientId(),
                        "secretHint", item.getSecretHint(),
                        "status", item.getStatus()))
                .toList());
        return snapshot;
    }

    private Map<String, Object> callbackSnapshot(AppCallback callback) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("callbackType", callback.getCallbackType());
        value.put("url", callback.getUrl());
        value.put("eventCodes", readStringList(callback.getEventCodesJson()));
        value.put("signingSecretRef", callback.getSigningSecretRef());
        value.put("timeoutMillis", callback.getTimeoutMillis());
        value.put("maxAttempts", callback.getMaxAttempts());
        return value;
    }

    private Map<String, Object> grantSnapshot(AppGrant grant) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("targetType", grant.getTargetType());
        value.put("targetSystemId", grant.getTargetSystemId());
        value.put("targetTenantId", grant.getTargetTenantId());
        value.put("resourceType", grant.getResourceType());
        value.put("resourceId", grant.getResourceId());
        value.put("actionCode", grant.getActionCode());
        value.put("dataScope", readMap(grant.getDataScopeJson()));
        value.put("rateLimit", readMap(grant.getRateLimitJson()));
        value.put("fields", fields(grant.getId()).stream().map(field -> Map.of(
                "fieldCode", field.getFieldCode(),
                "readable", Boolean.TRUE.equals(field.getReadable()),
                "writable", Boolean.TRUE.equals(field.getWritable()),
                "maskStrategy", field.getMaskStrategy() == null ? "NONE" : field.getMaskStrategy())).toList());
        return value;
    }

    private ApplicationModels.ApplicationView view(AppDefinition application, boolean includeHistory) {
        AppPublication publication = publication(application.getId());
        List<ApplicationModels.GrantView> grants = grants(application.getId()).stream().map(grant ->
                new ApplicationModels.GrantView(grant.getId(), grant.getTargetType(), grant.getTargetSystemId(),
                        grant.getTargetTenantId(), grant.getResourceType(), grant.getResourceId(), grant.getActionCode(),
                        readMap(grant.getDataScopeJson()), readMap(grant.getRateLimitJson()), grant.getStatus(), grant.getVersion(),
                        fields(grant.getId()).stream().map(field -> new ApplicationModels.FieldView(
                                field.getId(), field.getFieldCode(), Boolean.TRUE.equals(field.getReadable()),
                                Boolean.TRUE.equals(field.getWritable()), field.getMaskStrategy())).toList())).toList();
        List<ApplicationModels.VersionView> versions = versions(application.getId()).stream()
                .sorted(Comparator.comparing(AppVersion::getVersionNumber).reversed())
                .map(version -> new ApplicationModels.VersionView(version.getId(), version.getVersionNumber(),
                        version.getDraftRevision(), version.getSnapshotHash(), version.getPublishedByAccountId(),
                        version.getPublishedAt(), publication != null && Objects.equals(publication.getCurrentVersionId(), version.getId())))
                .toList();
        return new ApplicationModels.ApplicationView(
                application.getId(), application.getContextType(), application.getPlatformId(),
                application.getOwnerSystemId(), application.getOwnerTenantId(), application.getCode(),
                application.getName(), application.getDescription(), application.getApplicationType(),
                application.getDraftRevision(), application.getStatus(), application.getVersion(),
                publication == null ? null : publication.getCurrentVersionId(),
                callbacks(application.getId()).stream().map(callback -> new ApplicationModels.CallbackView(
                        callback.getId(), callback.getCallbackType(), callback.getUrl(),
                        readStringList(callback.getEventCodesJson()), callback.getSigningSecretRef(),
                        callback.getTimeoutMillis(), callback.getMaxAttempts(), callback.getStatus(), callback.getVersion())).toList(),
                grants,
                credentials(application.getId()).stream().sorted(Comparator.comparing(AppCredential::getCredentialVersion).reversed())
                        .map(credential -> new ApplicationModels.CredentialView(
                                credential.getId(), credential.getCredentialVersion(), credential.getClientId(),
                                secretCipher.displayReference(application.getId(), credential.getCredentialVersion()),
                                credential.getSecretHint(), credential.getValidFrom(), credential.getExpiresAt(),
                                credential.getStatus(), credential.getRevokedAt(), credential.getCreatedAt())).toList(),
                versions,
                includeHistory ? statusHistory(application.getId()) : List.of(),
                application.getCreatedAt(), application.getUpdatedAt());
    }

    private List<ApplicationModels.StatusEvent> statusHistory(Long applicationId) {
        return auditEventService.selectList(Wrappers.<AuditEvent>lambdaQuery()
                        .eq(AuditEvent::getObjectType, "APPLICATION")
                        .eq(AuditEvent::getObjectId, applicationId.toString()))
                .stream().sorted(Comparator.comparing(AuditEvent::getOccurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(event -> new ApplicationModels.StatusEvent(event.getId(), event.getEventCode(), event.getResultCode(),
                        event.getActorAccountId(), event.getOccurredAt(), readMap(event.getDetailJson()))).toList();
    }

    private List<AppDefinition> scopedDefinitions(AuthenticatedContext context) {
        var query = Wrappers.<AppDefinition>lambdaQuery()
                .eq(AppDefinition::getContextType, context.systemId() == null ? "PLATFORM" : "SYSTEM")
                .eq(AppDefinition::getPlatformId, context.platformId());
        if (context.systemId() == null) {
            query.isNull(AppDefinition::getOwnerSystemId).isNull(AppDefinition::getOwnerTenantId);
        } else {
            query.eq(AppDefinition::getOwnerSystemId, context.systemId())
                    .eq(AppDefinition::getOwnerTenantId, context.tenantId());
        }
        return definitionService.selectList(query);
    }

    private AppDefinition requireOwned(AuthenticatedContext context, Long applicationId) {
        AppDefinition application = definitionService.selectById(applicationId);
        if (application == null || !owned(context, application)) {
            throw new DomainException("APPLICATION_NOT_FOUND", "应用不存在或不在当前上下文", HttpStatus.NOT_FOUND);
        }
        return application;
    }

    private boolean owned(AuthenticatedContext context, AppDefinition application) {
        if (!Objects.equals(context.platformId(), application.getPlatformId())) return false;
        if (context.systemId() == null) {
            return "PLATFORM".equals(application.getContextType())
                    && application.getOwnerSystemId() == null && application.getOwnerTenantId() == null;
        }
        return "SYSTEM".equals(application.getContextType())
                && Objects.equals(context.systemId(), application.getOwnerSystemId())
                && Objects.equals(context.tenantId(), application.getOwnerTenantId());
    }

    private void bindContext(AppDefinition application, AuthenticatedContext context) {
        application.setContextType(context.systemId() == null ? "PLATFORM" : "SYSTEM");
        application.setPlatformId(context.platformId());
        application.setOwnerSystemId(context.systemId());
        application.setOwnerTenantId(context.systemId() == null ? null : context.tenantId());
    }

    private List<AppCallback> callbacks(Long applicationId) {
        return callbackService.selectList(Wrappers.<AppCallback>lambdaQuery()
                .eq(AppCallback::getApplicationId, applicationId).orderByAsc(AppCallback::getId));
    }

    private List<AppGrant> grants(Long applicationId) {
        return grantService.selectList(Wrappers.<AppGrant>lambdaQuery()
                .eq(AppGrant::getApplicationId, applicationId).orderByAsc(AppGrant::getId));
    }

    private List<AppGrantField> fields(Long grantId) {
        return fieldService.selectList(Wrappers.<AppGrantField>lambdaQuery()
                .eq(AppGrantField::getGrantId, grantId).orderByAsc(AppGrantField::getId));
    }

    private List<AppCredential> credentials(Long applicationId) {
        return credentialService.selectList(Wrappers.<AppCredential>lambdaQuery()
                .eq(AppCredential::getApplicationId, applicationId));
    }

    private List<AppVersion> versions(Long applicationId) {
        return versionService.selectList(Wrappers.<AppVersion>lambdaQuery()
                .eq(AppVersion::getApplicationId, applicationId));
    }

    private AppPublication publication(Long applicationId) {
        return publicationService.selectList(Wrappers.<AppPublication>lambdaQuery()
                .eq(AppPublication::getApplicationId, applicationId)).stream().findFirst().orElse(null);
    }

    private String normalizeApplicationType(String value) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        if (!APPLICATION_TYPES.contains(normalized)) {
            throw invalid("APPLICATION_TYPE_INVALID", "应用类型仅支持 SERVICE 或 WEBHOOK");
        }
        return normalized;
    }

    private Number number(Object value) {
        return value instanceof Number number ? number : null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize application configuration", exception);
        }
    }

    private Map<String, Object> readMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid persisted application JSON", exception);
        }
    }

    private Object readObject(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid application call JSON in storage", exception);
        }
    }

    private List<String> readStringList(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return objectMapper.readValue(value, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid persisted application list JSON", exception);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private void requireAction(AuthenticatedContext context, String action) {
        if (context == null || context.platformId() == null) {
            throw new DomainException("AUTHENTICATION_REQUIRED", "请先登录", HttpStatus.UNAUTHORIZED);
        }
        String resourceCode = context.systemId() == null ? "PLATFORM" : "SYSTEM";
        if (!permissionChecker.allows(context, "APPLICATION", resourceCode, action)
                && !permissionChecker.allows(context, "APPLICATION", "*", action)) {
            throw new DomainException("PERMISSION_DENIED", "没有应用 " + action + " 权限", HttpStatus.FORBIDDEN);
        }
    }

    private void audit(
            AuthenticatedContext context, String traceId, String eventCode, Long applicationId, Map<String, ?> detail) {
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                eventCode, "APPLICATION", applicationId.toString(), "SUCCESS", detail);
    }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private DomainException conflict(String code, String message) {
        return new DomainException(code, message, HttpStatus.CONFLICT);
    }
}
