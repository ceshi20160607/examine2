package com.unique.unexamine.application.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.unique.unexamine.application.base.entity.AppCall;
import com.unique.unexamine.application.base.entity.AppCallNonce;
import com.unique.unexamine.application.base.entity.AppBridgeIdentity;
import com.unique.unexamine.application.base.entity.AppCredential;
import com.unique.unexamine.application.base.entity.AppDefinition;
import com.unique.unexamine.application.base.entity.AppPublication;
import com.unique.unexamine.application.base.entity.AppVersion;
import com.unique.unexamine.application.base.service.AppCallBaseService;
import com.unique.unexamine.application.base.service.AppCallNonceBaseService;
import com.unique.unexamine.application.base.service.AppBridgeIdentityBaseService;
import com.unique.unexamine.application.base.service.AppCredentialBaseService;
import com.unique.unexamine.application.base.service.AppDefinitionBaseService;
import com.unique.unexamine.application.base.service.AppPublicationBaseService;
import com.unique.unexamine.application.base.service.AppVersionBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.DataScopeExpression;
import com.unique.unexamine.authorization.manage.DataScopeTerm;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.authorization.manage.PermissionResolver;
import com.unique.unexamine.authorization.manage.PlatformPermissionResolver;
import com.unique.unexamine.authorization.manage.ResolvedPermissions;
import com.unique.unexamine.flow.base.entity.FlowDefinition;
import com.unique.unexamine.flow.base.service.FlowDefinitionBaseService;
import com.unique.unexamine.flow.manage.FlowRuntimeModels;
import com.unique.unexamine.flow.manage.FlowRuntimeService;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleVersion;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleVersionBaseService;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.entity.PlatformMember;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.platform.base.service.PlatformMemberBaseService;
import com.unique.unexamine.runtimedata.manage.CreateRuntimeRecordRequest;
import com.unique.unexamine.runtimedata.manage.RuntimeDataService;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordList;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordView;
import com.unique.unexamine.runtimedata.manage.UpdateRuntimeRecordRequest;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.entity.SystemDefinition;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.system.base.service.SystemDefinitionBaseService;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ApplicationBridgeService {
    private static final long TIMESTAMP_TOLERANCE_MILLIS = 5 * 60 * 1000L;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private final AppDefinitionBaseService definitionService;
    private final AppCredentialBaseService credentialService;
    private final AppPublicationBaseService publicationService;
    private final AppVersionBaseService versionService;
    private final AppCallBaseService callService;
    private final AppCallNonceBaseService nonceService;
    private final AppBridgeIdentityBaseService bridgeIdentityService;
    private final PlatformAccountBaseService accountService;
    private final PlatformMemberBaseService platformMemberService;
    private final SystemMemberBaseService systemMemberService;
    private final SystemDefinitionBaseService systemService;
    private final SystemTenantBaseService tenantService;
    private final SystemTenantMemberBaseService tenantMemberService;
    private final PlatformPermissionResolver platformPermissionResolver;
    private final PermissionResolver permissionResolver;
    private final PermissionChecker permissionChecker;
    private final FlowDefinitionBaseService flowDefinitionService;
    private final ConfiguredModuleBaseService moduleService;
    private final ConfiguredModuleVersionBaseService moduleVersionService;
    private final FlowRuntimeService flowRuntimeService;
    private final RuntimeDataService runtimeDataService;
    private final ApplicationSecretCipher secretCipher;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;

    public ApplicationBridgeService(
            AppDefinitionBaseService definitionService,
            AppCredentialBaseService credentialService,
            AppPublicationBaseService publicationService,
            AppVersionBaseService versionService,
            AppCallBaseService callService,
            AppCallNonceBaseService nonceService,
            AppBridgeIdentityBaseService bridgeIdentityService,
            PlatformAccountBaseService accountService,
            PlatformMemberBaseService platformMemberService,
            SystemMemberBaseService systemMemberService,
            SystemDefinitionBaseService systemService,
            SystemTenantBaseService tenantService,
            SystemTenantMemberBaseService tenantMemberService,
            PlatformPermissionResolver platformPermissionResolver,
            PermissionResolver permissionResolver,
            PermissionChecker permissionChecker,
            FlowDefinitionBaseService flowDefinitionService,
            ConfiguredModuleBaseService moduleService,
            ConfiguredModuleVersionBaseService moduleVersionService,
            FlowRuntimeService flowRuntimeService,
            RuntimeDataService runtimeDataService,
            ApplicationSecretCipher secretCipher,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.definitionService = definitionService;
        this.credentialService = credentialService;
        this.publicationService = publicationService;
        this.versionService = versionService;
        this.callService = callService;
        this.nonceService = nonceService;
        this.bridgeIdentityService = bridgeIdentityService;
        this.accountService = accountService;
        this.platformMemberService = platformMemberService;
        this.systemMemberService = systemMemberService;
        this.systemService = systemService;
        this.tenantService = tenantService;
        this.tenantMemberService = tenantMemberService;
        this.platformPermissionResolver = platformPermissionResolver;
        this.permissionResolver = permissionResolver;
        this.permissionChecker = permissionChecker;
        this.flowDefinitionService = flowDefinitionService;
        this.moduleService = moduleService;
        this.moduleVersionService = moduleVersionService;
        this.flowRuntimeService = flowRuntimeService;
        this.runtimeDataService = runtimeDataService;
        this.secretCipher = secretCipher;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public ApplicationBridgeModels.CallResult call(
            String clientId, String timestampText, String nonce, String idempotencyKey, String signature,
            String rawBody, String sourceAddress, String requestId) {
        long startedNanos = System.nanoTime();
        requireHeader(clientId, "APPLICATION_CLIENT_ID_REQUIRED", "缺少应用 clientId");
        requireHeader(nonce, "APPLICATION_NONCE_REQUIRED", "缺少防重放 nonce");
        requireHeader(idempotencyKey, "APPLICATION_IDEMPOTENCY_KEY_REQUIRED", "缺少幂等键");
        requireHeader(signature, "APPLICATION_SIGNATURE_REQUIRED", "缺少调用签名");
        if (nonce.length() > 128 || idempotencyKey.length() > 255) {
            throw invalid("APPLICATION_CALL_HEADER_INVALID", "nonce 或幂等键超过允许长度");
        }
        long timestamp = parseTimestamp(timestampText);
        LocalDateTime requestTimestamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        AppCredential credential = credentialService.selectList(Wrappers.<AppCredential>lambdaQuery()
                        .eq(AppCredential::getClientId, clientId))
                .stream().findFirst().orElseThrow(() -> unauthorized(
                        "APPLICATION_CREDENTIAL_NOT_FOUND", "应用凭证不存在或已失效"));
        AppDefinition application = definitionService.selectById(credential.getApplicationId());
        if (application == null) throw unauthorized("APPLICATION_NOT_FOUND", "目标应用不存在");
        ApplicationBridgeModels.CallRequest input = parseRequest(rawBody);
        String requestHash = sha256(rawBody);
        PublishedGrant grant = findGrant(application.getId(), input);

        if (Math.abs(System.currentTimeMillis() - timestamp) > TIMESTAMP_TOLERANCE_MILLIS) {
            reject(application, credential, grant, input, requestId, requestTimestamp, nonce, sourceAddress,
                    requestHash, "APPLICATION_TIMESTAMP_EXPIRED", "调用时间戳已超过五分钟有效窗口", startedNanos);
        }
        claimNonce(application, credential, nonce, requestId, requestTimestamp);
        try {
            requireCallable(application, credential);
            verifySignature(credential, timestampText, nonce, idempotencyKey, signature, requestHash);
            if (grant == null) throw forbidden("APPLICATION_GRANT_DENIED", "应用未被授权访问该资源动作");
            requireAccessChannel(grant, "EXTERNAL");
            requireGrantBoundary(grant, input);
            requireSourceAddress(grant, sourceAddress);
            requireDataScope(grant, input.requestedDataScope());
            AuthenticatedContext targetContext = targetContext(application, grant);
            if (!permissionChecker.allows(targetContext, grant.getResourceType(), grant.getResourceId(), grant.getActionCode())) {
                throw forbidden("APPLICATION_TARGET_PERMISSION_DENIED", "应用授权与目标身份当前角色权限没有允许交集");
            }
            requireTargetDataScope(grant, input.requestedDataScope(), targetContext);
            AuthenticatedContext effectiveContext = restrictToApplicationScope(
                    grant, input.requestedDataScope(), targetContext);
            Map<String, Object> permissionSnapshot = permissionSnapshot(application, credential, grant, effectiveContext);
            Prepared prepared = transactions.execute(status -> prepareCall(application, credential, grant, input,
                    idempotencyKey, requestId, requestTimestamp, nonce, sourceAddress, requestHash,
                    permissionSnapshot));
            if (prepared == null) throw new IllegalStateException("Application call transaction returned no result");
            if (prepared.replayed() != null) return prepared.replayed();

            try {
                ApplicationBridgeModels.CallResult result = transactions.execute(status -> executeTarget(
                        application, credential, grant, input, effectiveContext, prepared.callId(), requestId,
                        permissionSnapshot, application.getCreatedByAccountId(), startedNanos));
                if (result == null) throw new IllegalStateException("Application target transaction returned no result");
                return result;
            } catch (DomainException exception) {
                finishFailure(prepared.callId(), exception.code(), exception.getMessage(), startedNanos);
                recordFailure(application, credential, grant, input, effectiveContext, requestId,
                        permissionSnapshot, exception.code(), exception.getMessage());
                throw exception;
            }
        } catch (DomainException exception) {
            if (callService.selectList(Wrappers.<AppCall>lambdaQuery()
                    .eq(AppCall::getApplicationId, application.getId())
                    .eq(AppCall::getRequestId, requestId)).isEmpty()) {
                logRejected(application, credential, grant, input, requestId, requestTimestamp, nonce,
                        sourceAddress, requestHash, exception.code(), exception.getMessage(), startedNanos);
            }
            throw exception;
        }
    }

    public ApplicationBridgeModels.CallResult callInternal(
            AuthenticatedContext context,
            Long applicationId,
            String idempotencyKey,
            ApplicationBridgeModels.CallRequest request,
            String requestId) {
        long startedNanos = System.nanoTime();
        requireHeader(idempotencyKey, "APPLICATION_IDEMPOTENCY_KEY_REQUIRED", "缺少幂等键");
        if (idempotencyKey.length() > 200) {
            throw invalid("APPLICATION_CALL_HEADER_INVALID", "幂等键超过允许长度");
        }
        ApplicationBridgeModels.CallRequest input = parseRequest(writeJson(request));
        AppDefinition application = definitionService.selectById(applicationId);
        if (application == null) throw new DomainException(
                "APPLICATION_NOT_FOUND", "目标应用不存在", HttpStatus.NOT_FOUND);
        requireInternalSourceContext(context, application);
        boolean providerAccess = "PROVIDER".equals(context.mfaLevel());
        String trustedChannel = providerAccess ? "PROVIDER" : "INTERNAL";
        String trustedPrefix = providerAccess ? "provider" : "internal";
        AppCredential internalIdentity = new AppCredential();
        internalIdentity.setApplicationId(applicationId);
        internalIdentity.setCredentialVersion(0);
        internalIdentity.setClientId(trustedPrefix + ":" + context.systemId() + ":" + context.tenantId()
                + ":" + context.tenantMemberId());
        PublishedGrant grant = findGrant(applicationId, input);
        String requestHash = sha256(writeJson(input));
        String durableIdempotencyKey = trustedPrefix + ":" + context.systemId() + ":" + context.tenantId()
                + ":" + context.tenantMemberId() + ":" + idempotencyKey;
        LocalDateTime requestedAt = LocalDateTime.now();
        String nonce = trustedPrefix + ":" + requestId;
        try {
            if (!"ACTIVE".equals(application.getStatus())) {
                throw forbidden("APPLICATION_DISABLED", "应用已停用或尚未发布");
            }
            if (grant == null) throw forbidden("APPLICATION_GRANT_DENIED", "应用未被授权访问该资源动作");
            requireAccessChannel(grant, providerAccess ? "EXTERNAL" : "INTERNAL");
            requireGrantBoundary(grant, input);
            requireDataScope(grant, input.requestedDataScope());
            AuthenticatedContext targetContext = internalTargetContext(application, grant, context);
            if (!permissionChecker.allows(targetContext, grant.getResourceType(), grant.getResourceId(), grant.getActionCode())) {
                throw forbidden("APPLICATION_TARGET_PERMISSION_DENIED", "映射的目标执行身份没有目标资源动作权限");
            }
            requireTargetDataScope(grant, input.requestedDataScope(), targetContext);
            AuthenticatedContext effectiveContext = restrictToApplicationScope(
                    grant, input.requestedDataScope(), targetContext);
            Map<String, Object> permissionSnapshot = permissionSnapshot(
                    application, internalIdentity, grant, effectiveContext);
            permissionSnapshot.put("accessChannel", trustedChannel);
            permissionSnapshot.put("sourceIdentity", sourceIdentitySnapshot(context));
            Prepared prepared = transactions.execute(status -> prepareCall(
                    application, internalIdentity, grant, input, durableIdempotencyKey, requestId,
                    requestedAt, nonce, internalSource(context), requestHash, permissionSnapshot));
            if (prepared == null) throw new IllegalStateException("Internal application call returned no result");
            if (prepared.replayed() != null) return prepared.replayed();
            try {
                ApplicationBridgeModels.CallResult result = transactions.execute(status -> executeTarget(
                        application, internalIdentity, grant, input, effectiveContext, prepared.callId(), requestId,
                        permissionSnapshot, context.accountId(), startedNanos));
                if (result == null) throw new IllegalStateException("Internal application target returned no result");
                return result;
            } catch (DomainException exception) {
                finishFailure(prepared.callId(), exception.code(), exception.getMessage(), startedNanos);
                recordFailure(application, internalIdentity, grant, input, effectiveContext, requestId,
                        permissionSnapshot, exception.code(), exception.getMessage());
                throw exception;
            }
        } catch (DomainException exception) {
            if (callService.selectList(Wrappers.<AppCall>lambdaQuery()
                    .eq(AppCall::getApplicationId, applicationId)
                    .eq(AppCall::getRequestId, requestId)).isEmpty()) {
                logRejected(application, internalIdentity, grant, input, requestId, requestedAt, nonce,
                        internalSource(context), requestHash,
                        exception.code(), exception.getMessage(), startedNanos);
            }
            throw exception;
        }
    }

    private Prepared prepareCall(
            AppDefinition application, AppCredential credential, PublishedGrant grant,
            ApplicationBridgeModels.CallRequest input, String idempotencyKey, String requestId,
            LocalDateTime requestTimestamp, String nonce, String sourceAddress, String requestHash,
            Map<String, Object> permissionSnapshot) {
        AppCall existing = callService.selectList(Wrappers.<AppCall>lambdaQuery()
                        .eq(AppCall::getApplicationId, application.getId())
                        .eq(AppCall::getIdempotencyKey, idempotencyKey))
                .stream().findFirst().orElse(null);
        if (existing != null) {
            if (!Objects.equals(existing.getRequestHash(), requestHash)) {
                throw conflict("APPLICATION_IDEMPOTENCY_CONFLICT", "同一幂等键不能用于不同请求内容");
            }
            if (!"SUCCESS".equals(existing.getStatus()) || existing.getResponseJson() == null) {
                throw conflict("APPLICATION_IDEMPOTENCY_PENDING", "同一幂等请求仍在处理或此前未成功");
            }
            existing.setReplayCount((existing.getReplayCount() == null ? 0 : existing.getReplayCount()) + 1);
            callService.updateById(existing);
            ApplicationBridgeModels.CallResult original = readResult(existing.getResponseJson());
            return new Prepared(null, new ApplicationBridgeModels.CallResult(
                    original.requestId(), true, original.applicationId(), original.grantId(),
                    original.credentialVersion(), original.resourceType(), original.resourceId(),
                    original.actionCode(), original.targetReference(), original.result(), original.source()));
        }
        requireRateLimit(application, grant);
        AppCall call = baseCall(application, credential, grant, input, requestId, requestTimestamp, nonce,
                sourceAddress, requestHash);
        call.setIdempotencyKey(idempotencyKey);
        call.setPermissionSnapshotJson(writeJson(permissionSnapshot));
        call.setStatus("PENDING");
        call.setReplayCount(0);
        callService.insert(call);
        return new Prepared(call.getId(), null);
    }

    private ApplicationBridgeModels.CallResult executeTarget(
            AppDefinition application, AppCredential credential, PublishedGrant grant,
            ApplicationBridgeModels.CallRequest input, AuthenticatedContext targetContext, Long callId,
            String requestId, Map<String, Object> permissionSnapshot, Long actorAccountId, long startedNanos) {
        TargetResult target = switch (grant.getResourceType()) {
            case "FLOW" -> executeFlow(grant, input, targetContext, callId, requestId);
            case "MODULE" -> executeModule(grant, input, targetContext, callId, requestId);
            default -> throw invalid("APPLICATION_RESOURCE_UNSUPPORTED", "当前资源类型尚不支持受控调用");
        };
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("type", "APPLICATION_CALL");
        source.put("applicationId", application.getId());
        source.put("applicationCode", application.getCode());
        source.put("callId", callId);
        source.put("requestId", requestId);
        source.put("accessChannel", permissionSnapshot.getOrDefault("accessChannel",
                credential.getCredentialVersion() == 0 ? "INTERNAL" : "EXTERNAL"));
        source.put("actorAccountId", actorAccountId);
        ApplicationBridgeModels.CallResult result = new ApplicationBridgeModels.CallResult(
                requestId, false, application.getId(), grant.getId(), credential.getCredentialVersion(),
                grant.getResourceType(), grant.getResourceId(), grant.getActionCode(),
                target.reference(), target.value(), source);
        AppCall call = callService.selectById(callId);
        call.setStatus("SUCCESS");
        call.setResponseCode("OK");
        call.setResponseJson(writeJson(result));
        call.setTargetReference(target.reference());
        call.setDurationMillis(elapsedMillis(startedNanos));
        call.setFinishedAt(LocalDateTime.now());
        callService.updateById(call);
        auditRecorder.recordWithPermissionSnapshot(requestId, actorAccountId,
                grant.getTargetSystemId(), grant.getTargetTenantId(), targetContext.memberId(),
                "APPLICATION_CALL_SUCCEEDED", target.objectType(), target.objectId(), "SUCCESS",
                permissionSnapshot, Map.of("applicationId", application.getId(), "callId", callId,
                        "resource", grant.getResourceType() + ":" + grant.getResourceId(),
                        "action", grant.getActionCode(), "targetReference", target.reference()));
        return result;
    }

    private TargetResult executeFlow(
            PublishedGrant grant, ApplicationBridgeModels.CallRequest input, AuthenticatedContext context,
            Long callId, String requestId) {
        if (!"START".equals(grant.getActionCode())) {
            throw invalid("APPLICATION_FLOW_ACTION_UNSUPPORTED", "应用当前只支持发起已授权 Flow");
        }
        if (grant.getResourceObjectId() == null || grant.getResourceVersionId() == null
                || grant.getResourceDefinitionHash() == null) {
            throw conflict("APPLICATION_FLOW_VERSION_REQUIRED", "应用发布版本尚未固定目标 Flow 版本，请重新检查并发布应用");
        }
        FlowDefinition flow = flowDefinitionService.selectList(Wrappers.<FlowDefinition>lambdaQuery()
                        .eq(FlowDefinition::getId, grant.getResourceObjectId())
                        .eq(FlowDefinition::getContextType, context.systemId() == null ? "PLATFORM" : "SYSTEM")
                        .eq(FlowDefinition::getPlatformId, context.platformId())
                        .eq(context.systemId() != null, FlowDefinition::getSystemId, context.systemId())
                        .eq(context.tenantId() != null, FlowDefinition::getOwnerTenantId, context.tenantId())
                        .eq(FlowDefinition::getCode, grant.getResourceId())
                        .eq(FlowDefinition::getStatus, "PUBLISHED"))
                .stream().findFirst().orElseThrow(() -> invalid(
                        "APPLICATION_TARGET_FLOW_NOT_FOUND", "授权的目标 Flow 不存在于固定上下文"));
        Map<String, Object> payload = safe(input.payload());
        String title = string(payload.get("title"));
        if (title == null || title.isBlank()) title = "应用调用 · " + grant.getResourceId();
        Map<String, Object> businessSnapshot = new LinkedHashMap<>();
        businessSnapshot.put("sourceType", "APPLICATION_CALL");
        businessSnapshot.put("applicationCallId", callId);
        businessSnapshot.put("requestId", requestId);
        businessSnapshot.put("applicationVersionId", grant.getApplicationVersionId());
        businessSnapshot.put("flowVersionId", grant.getResourceVersionId());
        FlowRuntimeModels.ActionResult result = flowRuntimeService.startApplication(context,
                new FlowRuntimeModels.StartRequest(flow.getId(), title, "APPLICATION_CALL", requestId,
                        businessSnapshot, map(payload.get("variables")), "app-call:" + callId),
                grant.getResourceVersionId(), grant.getResourceDefinitionHash(), requestId);
        return new TargetResult("FLOW_INSTANCE:" + result.instance().id(), result,
                "FLOW_INSTANCE", result.instance().id().toString());
    }

    private TargetResult executeModule(
            PublishedGrant grant, ApplicationBridgeModels.CallRequest input, AuthenticatedContext context,
            Long callId, String requestId) {
        requireFrozenModule(grant, context);
        Map<String, Object> payload = safe(input.payload());
        Object value;
        String reference;
        String objectId;
        switch (grant.getActionCode()) {
            case "LIST" -> {
                int page = integer(payload.get("page"), 1);
                int pageSize = Math.min(integer(payload.get("pageSize"), 20), 200);
                RuntimeRecordList result = runtimeDataService.list(context, grant.getResourceId(), "ACTIVE", "OWN",
                        stringOrDefault(payload.get("search"), ""), writeJson(payload.getOrDefault("filters", List.of())),
                        stringOrDefault(payload.get("sortField"), "updatedAt"),
                        stringOrDefault(payload.get("sortDirection"), "DESC"), page, pageSize, requestId);
                value = filterReadableFields(grant, result);
                reference = "MODULE:" + grant.getResourceId() + ":LIST";
                objectId = grant.getResourceId();
            }
            case "DETAIL" -> {
                Long recordId = number(payload.get("recordId"));
                if (recordId == null) throw invalid("APPLICATION_RECORD_ID_REQUIRED", "模块详情调用缺少 recordId");
                RuntimeRecordView result = runtimeDataService.detail(context, grant.getResourceId(), recordId, requestId);
                value = filterReadableFields(grant, result);
                reference = "BUSINESS_RECORD:" + result.id();
                objectId = result.id().toString();
            }
            case "CREATE" -> {
                Map<String, Object> fields = map(payload.get("fields"));
                requireWritableFields(grant, fields.keySet());
                Map<String, JsonNode> jsonFields = new LinkedHashMap<>();
                fields.forEach((key, fieldValue) -> jsonFields.put(key, objectMapper.valueToTree(fieldValue)));
                CreateRuntimeRecordRequest request = new CreateRuntimeRecordRequest(
                        stringOrDefault(payload.get("title"), "应用调用创建"), string(payload.get("recordNumber")),
                        string(payload.get("status")), number(payload.get("ownerMemberId")),
                        number(payload.get("departmentId")), numbers(payload.get("participantMemberIds")), jsonFields);
                RuntimeRecordView result = runtimeDataService.create(context, grant.getResourceId(), request, requestId);
                value = filterReadableFields(grant, result);
                reference = "BUSINESS_RECORD:" + result.id();
                objectId = result.id().toString();
            }
            case "UPDATE" -> {
                Long recordId = number(payload.get("recordId"));
                Long expectedVersion = number(payload.get("version"));
                if (recordId == null) throw invalid("APPLICATION_RECORD_ID_REQUIRED", "模块编辑调用缺少业务记录");
                if (expectedVersion == null) throw invalid("APPLICATION_RECORD_VERSION_REQUIRED", "模块编辑调用缺少业务记录版本，请先刷新后重试");
                RuntimeRecordView current = runtimeDataService.detail(context, grant.getResourceId(), recordId, requestId);
                Map<String, Object> submittedFields = map(payload.get("fields"));
                requireWritableFields(grant, submittedFields.keySet());
                Map<String, JsonNode> mergedFields = new LinkedHashMap<>(current.fields());
                submittedFields.forEach((key, fieldValue) -> mergedFields.put(key, objectMapper.valueToTree(fieldValue)));
                UpdateRuntimeRecordRequest request = new UpdateRuntimeRecordRequest(
                        stringOrDefault(payload.get("title"), current.title()),
                        stringOrDefault(payload.get("recordNumber"), current.recordNumber()),
                        stringOrDefault(payload.get("status"), current.status()),
                        number(payload.get("ownerMemberId")) == null ? current.ownerMemberId() : number(payload.get("ownerMemberId")),
                        number(payload.get("departmentId")) == null ? current.departmentId() : number(payload.get("departmentId")),
                        payload.containsKey("participantMemberIds") ? numbers(payload.get("participantMemberIds")) : current.participantMemberIds(),
                        mergedFields, expectedVersion.intValue());
                RuntimeRecordView result = runtimeDataService.update(context, grant.getResourceId(), recordId, request, requestId);
                value = filterReadableFields(grant, result);
                reference = "BUSINESS_RECORD:" + result.id();
                objectId = result.id().toString();
            }
            default -> throw invalid("APPLICATION_MODULE_ACTION_UNSUPPORTED", "当前模块动作尚不支持应用调用");
        }
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put("businessResult", value);
        wrapped.put("source", Map.of("type", "APPLICATION_CALL", "callId", callId, "requestId", requestId));
        return new TargetResult(reference, wrapped, "BUSINESS_RECORD", objectId);
    }

    private Object filterReadableFields(PublishedGrant grant, Object value) {
        Set<String> readable = grant.getFields().stream().filter(PublishedField::readable)
                .map(PublishedField::fieldCode).collect(java.util.stream.Collectors.toSet());
        Object converted = objectMapper.convertValue(value, Object.class);
        filterFields(converted, readable);
        return converted;
    }

    @SuppressWarnings("unchecked")
    private void filterFields(Object value, Set<String> readable) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> map = (Map<String, Object>) raw;
            Object fields = map.get("fields");
            if (fields instanceof Map<?, ?> fieldMap) {
                ((Map<String, Object>) fieldMap).keySet().removeIf(key -> !readable.contains(key));
            }
            new ArrayList<>(map.values()).forEach(item -> filterFields(item, readable));
        } else if (value instanceof List<?> list) {
            list.forEach(item -> filterFields(item, readable));
        }
    }

    private void requireWritableFields(PublishedGrant grant, Set<String> requested) {
        Set<String> writable = grant.getFields().stream().filter(PublishedField::writable)
                .map(PublishedField::fieldCode).collect(java.util.stream.Collectors.toSet());
        if (!writable.containsAll(requested)) {
            throw forbidden("APPLICATION_FIELD_WRITE_DENIED", "请求包含未获应用写权限的字段");
        }
    }

    private AuthenticatedContext targetContext(AppDefinition application, PublishedGrant grant) {
        PlatformAccount account = accountService.selectById(application.getCreatedByAccountId());
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw forbidden("APPLICATION_TARGET_IDENTITY_INACTIVE", "应用绑定的目标执行身份已停用");
        }
        if (grant.getTargetSystemId() == null) {
            PlatformMember member = platformMemberService.selectList(Wrappers.<PlatformMember>lambdaQuery()
                            .eq(PlatformMember::getPlatformId, application.getPlatformId())
                            .eq(PlatformMember::getAccountId, account.getId())
                            .eq(PlatformMember::getStatus, "ACTIVE"))
                    .stream().findFirst().orElseThrow(() -> forbidden(
                            "APPLICATION_TARGET_MEMBER_MISSING", "应用绑定账号不是当前平台有效成员"));
            ResolvedPermissions permissions = platformPermissionResolver.resolve(application.getPlatformId(), account.getId());
            return new AuthenticatedContext(null, account.getId(), application.getPlatformId(), null, null,
                    member.getId(), null, account.getUsername(), account.getDisplayName(), "APPLICATION",
                    permissions.roleIds(), permissions.permissions(), permissions.dataScopes());
        }
        SystemMember member = systemMemberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                        .eq(SystemMember::getSystemId, grant.getTargetSystemId())
                        .eq(SystemMember::getAccountId, account.getId())
                        .eq(SystemMember::getStatus, "ACTIVE"))
                .stream().findFirst().orElseThrow(() -> forbidden(
                        "APPLICATION_TARGET_MEMBER_MISSING", "应用绑定账号不是目标系统有效成员"));
        SystemTenantMember tenantMember = tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                        .eq(SystemTenantMember::getSystemId, grant.getTargetSystemId())
                        .eq(SystemTenantMember::getTenantId, grant.getTargetTenantId())
                        .eq(SystemTenantMember::getSystemMemberId, member.getId())
                        .eq(SystemTenantMember::getStatus, "ACTIVE"))
                .stream().findFirst().orElseThrow(() -> forbidden(
                        "APPLICATION_TARGET_TENANT_MEMBER_MISSING", "应用绑定账号不是目标租户有效成员"));
        ResolvedPermissions permissions = permissionResolver.resolve(
                grant.getTargetSystemId(), grant.getTargetTenantId(), tenantMember.getId());
        return new AuthenticatedContext(null, account.getId(), application.getPlatformId(),
                grant.getTargetSystemId(), grant.getTargetTenantId(), member.getId(), tenantMember.getId(),
                account.getUsername(), member.getDisplayName(), "APPLICATION", permissions.roleIds(),
                permissions.permissions(), permissions.dataScopes());
    }

    private void claimNonce(
            AppDefinition application, AppCredential credential, String nonce, String requestId,
            LocalDateTime requestTimestamp) {
        try {
            transactions.executeWithoutResult(status -> {
                if (!nonceService.selectList(Wrappers.<AppCallNonce>lambdaQuery()
                        .eq(AppCallNonce::getApplicationId, application.getId())
                        .eq(AppCallNonce::getNonce, nonce)).isEmpty()) {
                    throw conflict("APPLICATION_REPLAY_DETECTED", "调用 nonce 已使用，疑似重放请求");
                }
                AppCallNonce accepted = new AppCallNonce();
                accepted.setApplicationId(application.getId());
                accepted.setCredentialVersion(credential.getCredentialVersion());
                accepted.setNonce(nonce);
                accepted.setRequestId(requestId);
                accepted.setRequestTimestamp(requestTimestamp);
                accepted.setExpiresAt(LocalDateTime.now().plus(24, ChronoUnit.HOURS));
                nonceService.insert(accepted);
            });
        } catch (DataIntegrityViolationException exception) {
            throw conflict("APPLICATION_REPLAY_DETECTED", "调用 nonce 已使用，疑似重放请求");
        }
    }

    private void verifySignature(
            AppCredential credential, String timestamp, String nonce, String idempotencyKey,
            String supplied, String requestHash) {
        String canonical = credential.getClientId() + "\n" + timestamp + "\n" + nonce + "\n"
                + idempotencyKey + "\n" + requestHash;
        try {
            String secret = secretCipher.decrypt(credential.getSigningSecretRef(),
                    credential.getApplicationId(), credential.getCredentialVersion());
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            byte[] actual = HexFormat.of().parseHex(supplied.toLowerCase(Locale.ROOT));
            if (!MessageDigest.isEqual(expected, actual)) {
                throw unauthorized("APPLICATION_SIGNATURE_INVALID", "应用调用签名校验失败");
            }
        } catch (DomainException exception) {
            throw exception;
        } catch (GeneralSecurityException | IllegalArgumentException | IllegalStateException exception) {
            throw unauthorized("APPLICATION_SIGNATURE_INVALID", "应用调用签名校验失败");
        }
    }

    private void requireCallable(AppDefinition application, AppCredential credential) {
        LocalDateTime now = LocalDateTime.now();
        if (!"ACTIVE".equals(application.getStatus())) {
            throw forbidden("APPLICATION_DISABLED", "应用已停用或尚未发布");
        }
        if (!"ACTIVE".equals(credential.getStatus()) || credential.getValidFrom().isAfter(now)
                || credential.getExpiresAt() != null && !credential.getExpiresAt().isAfter(now)) {
            throw unauthorized("APPLICATION_CREDENTIAL_INACTIVE", "凭证版本已失效");
        }
        if (credential.getSigningSecretRef() == null) {
            throw unauthorized("APPLICATION_SIGNING_SECRET_UNAVAILABLE", "凭证缺少可验签的密钥引用，请轮换凭证");
        }
    }

    private void requireInternalSourceContext(AuthenticatedContext context, AppDefinition application) {
        if (context == null || !Objects.equals(context.platformId(), application.getPlatformId())) {
            throw forbidden("APPLICATION_INTERNAL_CONTEXT_DENIED", "当前登录身份不在应用所属平台范围");
        }
        if ("PLATFORM".equals(application.getContextType())) {
            if (context.systemId() != null || application.getOwnerSystemId() != null) {
                throw forbidden("APPLICATION_INTERNAL_CONTEXT_DENIED", "平台应用只能从平台上下文访问");
            }
            return;
        }
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null
                || context.tenantMemberId() == null) {
            throw forbidden("APPLICATION_INTERNAL_CONTEXT_DENIED", "系统应用只接受有效系统租户成员调用");
        }
    }

    /**
     * 同系统调用沿用登录身份；跨系统调用只能使用当前发布版本中冻结且尚未停用的 A→B 身份映射。
     */
    private AuthenticatedContext internalTargetContext(
            AppDefinition application, PublishedGrant grant, AuthenticatedContext sourceContext) {
        if ("PLATFORM".equals(application.getContextType())) return sourceContext;
        requireActiveSourceIdentity(sourceContext);
        if (Objects.equals(sourceContext.systemId(), application.getOwnerSystemId())
                && Objects.equals(sourceContext.tenantId(), application.getOwnerTenantId())) {
            return sourceContext;
        }
        PublishedIdentity identity = findPublishedIdentity(grant.getApplicationVersionId(), sourceContext);
        if (identity == null) {
            throw forbidden("APPLICATION_SOURCE_IDENTITY_UNMAPPED", "当前来源成员没有此应用的跨系统身份映射");
        }
        AppBridgeIdentity current = bridgeIdentityService.selectById(identity.mappingId());
        if (current == null || !Objects.equals(current.getApplicationId(), application.getId())
                || !"ACTIVE".equals(current.getStatus())) {
            throw forbidden("APPLICATION_IDENTITY_MAPPING_DISABLED", "跨系统身份映射已停用或撤销");
        }
        if (!Objects.equals(identity.targetSystemId(), application.getOwnerSystemId())
                || !Objects.equals(identity.targetTenantId(), application.getOwnerTenantId())
                || !Objects.equals(identity.targetSystemId(), grant.getTargetSystemId())
                || !Objects.equals(identity.targetTenantId(), grant.getTargetTenantId())) {
            throw forbidden("APPLICATION_IDENTITY_MAPPING_BOUNDARY_INVALID", "发布身份映射超出应用目标系统或租户范围");
        }
        return mappedTargetContext(application, identity);
    }

    private void requireActiveSourceIdentity(AuthenticatedContext context) {
        SystemDefinition system = systemService.selectById(context.systemId());
        SystemTenant tenant = tenantService.selectById(context.tenantId());
        SystemMember member = systemMemberService.selectById(context.memberId());
        SystemTenantMember tenantMember = tenantMemberService.selectById(context.tenantMemberId());
        PlatformAccount account = accountService.selectById(context.accountId());
        if (!active(system) || tenant == null || !"ACTIVE".equals(tenant.getStatus())
                || member == null || !"ACTIVE".equals(member.getStatus())
                || tenantMember == null || !"ACTIVE".equals(tenantMember.getStatus())
                || account == null || !"ACTIVE".equals(account.getStatus())
                || !Objects.equals(system.getPlatformId(), context.platformId())
                || !Objects.equals(tenant.getSystemId(), context.systemId())
                || !Objects.equals(member.getSystemId(), context.systemId())
                || !Objects.equals(member.getAccountId(), context.accountId())
                || !Objects.equals(tenantMember.getSystemId(), context.systemId())
                || !Objects.equals(tenantMember.getTenantId(), context.tenantId())
                || !Objects.equals(tenantMember.getSystemMemberId(), context.memberId())) {
            throw forbidden("APPLICATION_SOURCE_IDENTITY_INACTIVE", "来源系统、租户或成员已停用");
        }
    }

    private PublishedIdentity findPublishedIdentity(Long versionId, AuthenticatedContext sourceContext) {
        AppVersion version = versionService.selectById(versionId);
        if (version == null || version.getSnapshotJson() == null) return null;
        Object raw = readMap(version.getSnapshotJson()).get("bridgeIdentities");
        if (!(raw instanceof List<?> values)) return null;
        for (Object value : values) {
            Map<String, Object> identity = map(value);
            if (Objects.equals(number(identity.get("sourceSystemId")), sourceContext.systemId())
                    && Objects.equals(number(identity.get("sourceTenantId")), sourceContext.tenantId())
                    && Objects.equals(number(identity.get("sourceTenantMemberId")), sourceContext.tenantMemberId())) {
                return new PublishedIdentity(number(identity.get("mappingId")),
                        number(identity.get("sourceSystemId")), number(identity.get("sourceTenantId")),
                        number(identity.get("sourceTenantMemberId")), number(identity.get("targetSystemId")),
                        number(identity.get("targetTenantId")), number(identity.get("targetTenantMemberId")));
            }
        }
        return null;
    }

    private AuthenticatedContext mappedTargetContext(AppDefinition application, PublishedIdentity identity) {
        SystemDefinition system = systemService.selectById(identity.targetSystemId());
        SystemTenant tenant = tenantService.selectById(identity.targetTenantId());
        SystemTenantMember tenantMember = tenantMemberService.selectById(identity.targetTenantMemberId());
        SystemMember member = tenantMember == null ? null
                : systemMemberService.selectById(tenantMember.getSystemMemberId());
        PlatformAccount account = member == null ? null : accountService.selectById(member.getAccountId());
        if (!active(system) || tenant == null || !"ACTIVE".equals(tenant.getStatus())
                || tenantMember == null || !"ACTIVE".equals(tenantMember.getStatus())
                || member == null || !"ACTIVE".equals(member.getStatus())
                || account == null || !"ACTIVE".equals(account.getStatus())
                || !Objects.equals(system.getPlatformId(), application.getPlatformId())
                || !Objects.equals(tenant.getSystemId(), identity.targetSystemId())
                || !Objects.equals(tenantMember.getSystemId(), identity.targetSystemId())
                || !Objects.equals(tenantMember.getTenantId(), identity.targetTenantId())
                || !Objects.equals(member.getSystemId(), identity.targetSystemId())) {
            throw forbidden("APPLICATION_TARGET_IDENTITY_INACTIVE", "映射的目标系统、租户或成员已停用");
        }
        ResolvedPermissions permissions = permissionResolver.resolve(
                identity.targetSystemId(), identity.targetTenantId(), identity.targetTenantMemberId());
        return new AuthenticatedContext(null, account.getId(), application.getPlatformId(),
                identity.targetSystemId(), identity.targetTenantId(), member.getId(), tenantMember.getId(),
                account.getUsername(), member.getDisplayName(), "APPLICATION", permissions.roleIds(),
                permissions.permissions(), permissions.dataScopes());
    }

    private boolean active(SystemDefinition system) {
        return system != null && "ACTIVE".equals(system.getStatus()) && !Boolean.TRUE.equals(system.getDeleted());
    }

    private Map<String, Object> sourceIdentitySnapshot(AuthenticatedContext context) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("accountId", context.accountId());
        source.put("systemId", context.systemId());
        source.put("tenantId", context.tenantId());
        source.put("memberId", context.memberId());
        source.put("tenantMemberId", context.tenantMemberId());
        source.put("roleIds", context.roleIds());
        return source;
    }

    private String internalSource(AuthenticatedContext context) {
        String sourceType = "PROVIDER".equals(context.mfaLevel()) ? "PROVIDER" : "INTERNAL";
        return sourceType + ":" + context.systemId() + ":" + context.tenantId() + ":" + context.tenantMemberId();
    }

    private void requireAccessChannel(PublishedGrant grant, String requiredChannel) {
        Object configured = readMap(grant.getRateLimitJson()).get("accessChannels");
        List<String> channels = configured instanceof List<?> values && !values.isEmpty()
                ? values.stream().map(String::valueOf).map(this::normalize).toList()
                : List.of("EXTERNAL");
        if (!channels.contains(requiredChannel)) {
            throw forbidden("APPLICATION_ACCESS_CHANNEL_DENIED",
                    "当前资源动作未开放" + ("INTERNAL".equals(requiredChannel) ? "系统内访问" : "外部访问"));
        }
    }

    private PublishedGrant findGrant(Long applicationId, ApplicationBridgeModels.CallRequest input) {
        if (input.resourceType() == null || input.resourceId() == null || input.actionCode() == null) return null;
        AppPublication publication = publicationService.selectList(Wrappers.<AppPublication>lambdaQuery()
                        .eq(AppPublication::getApplicationId, applicationId))
                .stream().findFirst().orElse(null);
        if (publication == null || publication.getCurrentVersionId() == null) return null;
        AppVersion version = versionService.selectById(publication.getCurrentVersionId());
        if (version == null || version.getSnapshotJson() == null) return null;
        Object rawGrants = readMap(version.getSnapshotJson()).get("grants");
        if (!(rawGrants instanceof List<?> values)) return null;
        String resourceType = input.resourceType().strip().toUpperCase(Locale.ROOT);
        String resourceId = input.resourceId().strip();
        String actionCode = input.actionCode().strip().toUpperCase(Locale.ROOT);
        for (Object value : values) {
            Map<String, Object> grant = map(value);
            if (!resourceType.equals(normalize(string(grant.get("resourceType"))))
                    || !resourceId.equals(string(grant.get("resourceId")))
                    || !actionCode.equals(normalize(string(grant.get("actionCode"))))) continue;
            List<PublishedField> fields = new ArrayList<>();
            if (grant.get("fields") instanceof List<?> fieldValues) {
                for (Object fieldValue : fieldValues) {
                    Map<String, Object> field = map(fieldValue);
                    String fieldCode = string(field.get("fieldCode"));
                    if (fieldCode != null) fields.add(new PublishedField(fieldCode,
                            Boolean.TRUE.equals(field.get("readable")), Boolean.TRUE.equals(field.get("writable"))));
                }
            }
            return new PublishedGrant(number(grant.get("grantId")), version.getId(), version.getVersionNumber(),
                    version.getSnapshotHash(), string(grant.get("targetType")), number(grant.get("targetSystemId")),
                    number(grant.get("targetTenantId")), resourceType, resourceId, actionCode,
                    writeJson(map(grant.get("dataScope"))), writeJson(map(grant.get("rateLimit"))),
                    number(grant.get("resourceObjectId")), number(grant.get("resourceVersionId")),
                    integer(grant.get("resourceVersionNumber"), 0), string(grant.get("resourceDefinitionHash")), fields);
        }
        return null;
    }

    private void requireGrantBoundary(PublishedGrant grant, ApplicationBridgeModels.CallRequest input) {
        if (!Objects.equals(grant.getTargetSystemId(), input.targetSystemId())
                || !Objects.equals(grant.getTargetTenantId(), input.targetTenantId())) {
            throw forbidden("APPLICATION_TARGET_SCOPE_EXPANSION", "请求不能扩大应用固定的系统或租户范围");
        }
    }

    private void requireDataScope(PublishedGrant grant, Map<String, Object> requested) {
        if (requested == null || requested.isEmpty()) return;
        Map<String, Object> fixed = readMap(grant.getDataScopeJson());
        String fixedType = string(fixed.get("type"));
        String requestedType = string(requested.get("type"));
        if ("PLATFORM".equals(fixedType) || "PLATFORM".equals(requestedType)) {
            if (!Objects.equals(fixedType, requestedType)) {
                throw forbidden("APPLICATION_DATA_SCOPE_EXPANSION", "平台范围不能与系统数据范围互相替换");
            }
            return;
        }
        Map<String, Integer> rank = Map.of("SELF", 1, "DEPARTMENT", 2,
                "DEPARTMENT_AND_DESCENDANTS", 3, "ALL", 4);
        if (fixedType == null || requestedType == null || !rank.containsKey(fixedType)
                || !rank.containsKey(requestedType) || rank.get(requestedType) > rank.get(fixedType)) {
            throw forbidden("APPLICATION_DATA_SCOPE_EXPANSION", "请求数据范围超过应用固定授权范围");
        }
        for (Map.Entry<String, Object> entry : requested.entrySet()) {
            if (!"type".equals(entry.getKey()) && fixed.containsKey(entry.getKey())
                    && !Objects.equals(fixed.get(entry.getKey()), entry.getValue())) {
                throw forbidden("APPLICATION_DATA_SCOPE_EXPANSION", "请求数据范围条件超过应用固定授权范围");
            }
        }
    }

    private void requireTargetDataScope(
            PublishedGrant grant, Map<String, Object> requested, AuthenticatedContext targetContext) {
        if (targetContext.systemId() == null) return;
        Map<String, Object> effective = requested == null || requested.isEmpty()
                ? readMap(grant.getDataScopeJson()) : requested;
        String requestedType = string(effective.get("type"));
        if (requestedType == null || "PLATFORM".equals(requestedType)) {
            throw forbidden("APPLICATION_TARGET_DATA_SCOPE_DENIED", "目标系统身份不能使用平台数据范围");
        }
        List<DataScopeExpression> matching = targetContext.dataScopes().entrySet().stream()
                .filter(entry -> scopeKeyMatches(entry.getKey(), grant))
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .toList();
        boolean allowed = matching.stream().flatMap(expression -> expression.terms().stream())
                .anyMatch(term -> scopeTypeContains(term.type(), requestedType));
        if (!allowed) {
            throw forbidden("APPLICATION_TARGET_DATA_SCOPE_DENIED",
                    "请求数据范围超过目标身份当前角色的数据权限范围");
        }
    }

    /**
     * 模块调用在目标身份现有授权通过后，使用应用版本固定的数据范围执行。
     * 这样应用的 SELF/部门边界不会因目标成员后来获得更宽角色而被绕过。
     */
    private AuthenticatedContext restrictToApplicationScope(
            PublishedGrant grant, Map<String, Object> requested, AuthenticatedContext context) {
        if (context.systemId() == null || !"MODULE".equals(grant.getResourceType())) return context;
        Map<String, Object> effective = requested == null || requested.isEmpty()
                ? readMap(grant.getDataScopeJson()) : requested;
        String type = normalize(string(effective.get("type")));
        DataScopeExpression scope = new DataScopeExpression("SINGLE",
                List.of(new DataScopeTerm(type, NullNode.getInstance(), context.roleIds())));
        return new AuthenticatedContext(context.sessionId(), context.accountId(), context.platformId(),
                context.systemId(), context.tenantId(), context.memberId(), context.tenantMemberId(),
                context.username(), context.displayName(), context.mfaLevel(), context.roleIds(),
                context.permissions(), Map.of("MODULE:" + grant.getResourceId() + ":" + grant.getActionCode(), scope));
    }

    /** 模块调用校验应用发布时固定的物理模块和版本快照，禁止同编码替换或快照损坏。 */
    private void requireFrozenModule(PublishedGrant grant, AuthenticatedContext context) {
        if (grant.getResourceObjectId() == null || grant.getResourceVersionId() == null
                || grant.getResourceDefinitionHash() == null) {
            throw conflict("APPLICATION_MODULE_VERSION_REQUIRED", "应用发布版本尚未固定目标模块版本，请重新检查并发布应用");
        }
        ConfiguredModule module = moduleService.selectById(grant.getResourceObjectId());
        if (module == null || !Objects.equals(module.getSystemId(), context.systemId())
                || !Objects.equals(module.getOwnerTenantId(), context.tenantId())
                || !Objects.equals(module.getCode(), grant.getResourceId())
                || !Set.of("ACTIVE", "PUBLISHED").contains(module.getStatus())) {
            throw conflict("APPLICATION_TARGET_MODULE_NOT_FOUND", "授权的目标模块不存在于固定系统和租户范围");
        }
        ConfiguredModuleVersion version = moduleVersionService.selectById(grant.getResourceVersionId());
        if (version == null || !Objects.equals(version.getModuleId(), module.getId())
                || !Objects.equals(version.getSystemId(), context.systemId())
                || !Objects.equals(version.getOwnerTenantId(), context.tenantId())
                || !Objects.equals(version.getSchemaHash(), grant.getResourceDefinitionHash())) {
            throw conflict("APPLICATION_TARGET_MODULE_SNAPSHOT_INVALID", "应用固定的模块发布快照已损坏或不属于目标范围");
        }
    }

    private boolean scopeKeyMatches(String key, PublishedGrant grant) {
        String[] parts = key.split(":", 3);
        return parts.length == 3
                && ("*".equals(parts[0]) || grant.getResourceType().equals(parts[0]))
                && ("*".equals(parts[1]) || grant.getResourceId().equals(parts[1]))
                && ("*".equals(parts[2]) || grant.getActionCode().equals(parts[2]));
    }

    private boolean scopeTypeContains(String grantedType, String requestedType) {
        if (Objects.equals(grantedType, requestedType) || "ALL".equals(grantedType)) return true;
        Map<String, Integer> rank = Map.of("SELF", 1, "DEPARTMENT", 2,
                "DEPARTMENT_AND_DESCENDANTS", 3);
        return rank.containsKey(grantedType) && rank.containsKey(requestedType)
                && rank.get(requestedType) <= rank.get(grantedType);
    }

    private void requireSourceAddress(PublishedGrant grant, String sourceAddress) {
        Object configured = readMap(grant.getRateLimitJson()).get("allowedIps");
        List<String> allowed = configured instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
        String normalized = "0:0:0:0:0:0:0:1".equals(sourceAddress) ? "127.0.0.1" : sourceAddress;
        if (allowed.isEmpty() || allowed.stream().noneMatch(item -> "*".equals(item) || item.equals(normalized))) {
            throw forbidden("APPLICATION_SOURCE_IP_DENIED", "调用来源 IP 不在应用白名单内");
        }
    }

    private void requireRateLimit(AppDefinition application, PublishedGrant grant) {
        Map<String, Object> limit = readMap(grant.getRateLimitJson());
        long maximum = number(limit.get("maxRequests")) == null ? 0 : number(limit.get("maxRequests")).longValue();
        long seconds = number(limit.get("windowSeconds")) == null ? 0 : number(limit.get("windowSeconds")).longValue();
        if (maximum < 1 || seconds < 1) throw forbidden(
                "APPLICATION_CALL_CONTROL_INVALID", "应用调用控制未完整配置");
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(seconds);
        long count = callService.selectList(Wrappers.<AppCall>lambdaQuery()
                        .eq(AppCall::getApplicationId, application.getId())
                        .eq(AppCall::getResourceType, grant.getResourceType())
                        .eq(AppCall::getResourceId, grant.getResourceId())
                        .eq(AppCall::getActionCode, grant.getActionCode())
                        .ge(AppCall::getCalledAt, cutoff))
                .size();
        if (count >= maximum) throw new DomainException(
                "APPLICATION_RATE_LIMITED", "应用调用频率超过授权限制", HttpStatus.TOO_MANY_REQUESTS);
    }

    private Map<String, Object> permissionSnapshot(
            AppDefinition application, AppCredential credential, PublishedGrant grant, AuthenticatedContext context) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("applicationId", application.getId());
        snapshot.put("credentialVersion", credential.getCredentialVersion());
        snapshot.put("grantId", grant.getId());
        snapshot.put("applicationVersionId", grant.getApplicationVersionId());
        snapshot.put("applicationVersionNumber", grant.getApplicationVersionNumber());
        snapshot.put("applicationVersionHash", grant.getApplicationVersionHash());
        if (grant.getResourceVersionId() != null) {
            Map<String, Object> resourceVersion = new LinkedHashMap<>();
            resourceVersion.put("resourceObjectId", grant.getResourceObjectId());
            resourceVersion.put("versionId", grant.getResourceVersionId());
            resourceVersion.put("versionNumber", grant.getResourceVersionNumber());
            resourceVersion.put("definitionHash", grant.getResourceDefinitionHash());
            snapshot.put("resourceVersion", resourceVersion);
        }
        snapshot.put("grant", Map.of("resourceType", grant.getResourceType(), "resourceId", grant.getResourceId(),
                "actionCode", grant.getActionCode(), "dataScope", readMap(grant.getDataScopeJson())));
        Map<String, Object> targetIdentity = new LinkedHashMap<>();
        targetIdentity.put("accountId", context.accountId());
        targetIdentity.put("systemId", context.systemId());
        targetIdentity.put("tenantId", context.tenantId());
        targetIdentity.put("memberId", context.memberId());
        targetIdentity.put("tenantMemberId", context.tenantMemberId());
        targetIdentity.put("roleIds", context.roleIds());
        targetIdentity.put("permissions", context.permissions());
        targetIdentity.put("dataScopes", context.dataScopes());
        snapshot.put("targetIdentity", targetIdentity);
        snapshot.put("decision", "APPLICATION_GRANT_INTERSECT_TARGET_IDENTITY_ALLOW");
        return snapshot;
    }

    private void reject(
            AppDefinition application, AppCredential credential, PublishedGrant grant,
            ApplicationBridgeModels.CallRequest input, String requestId, LocalDateTime timestamp, String nonce,
            String sourceAddress, String requestHash, String code, String message, long startedNanos) {
        logRejected(application, credential, grant, input, requestId, timestamp, nonce, sourceAddress,
                requestHash, code, message, startedNanos);
        throw new DomainException(code, message, HttpStatus.UNAUTHORIZED);
    }

    private void logRejected(
            AppDefinition application, AppCredential credential, PublishedGrant grant,
            ApplicationBridgeModels.CallRequest input, String requestId, LocalDateTime timestamp, String nonce,
            String sourceAddress, String requestHash, String code, String message, long startedNanos) {
        try {
            transactions.executeWithoutResult(status -> {
                AppCall call = baseCall(application, credential, grant, input, requestId, timestamp, nonce,
                        sourceAddress, requestHash);
                call.setStatus("FAILED");
                call.setResponseCode(code);
                call.setErrorMessage(message);
                call.setReplayCount(0);
                call.setDurationMillis(elapsedMillis(startedNanos));
                call.setFinishedAt(LocalDateTime.now());
                callService.insert(call);
            });
        } catch (DataIntegrityViolationException ignored) {
            // A duplicate nonce is already represented by its original durable call and nonce record.
        }
        recordFailure(application, credential, grant, input, null, requestId, Map.of(), code, message);
    }

    private void finishFailure(Long callId, String code, String message, long startedNanos) {
        transactions.executeWithoutResult(status -> {
            AppCall call = callService.selectById(callId);
            if (call == null) return;
            call.setStatus("FAILED");
            call.setResponseCode(code);
            call.setErrorMessage(message);
            call.setDurationMillis(elapsedMillis(startedNanos));
            call.setFinishedAt(LocalDateTime.now());
            callService.updateById(call);
        });
    }

    private void recordFailure(
            AppDefinition application, AppCredential credential, PublishedGrant grant,
            ApplicationBridgeModels.CallRequest input, AuthenticatedContext context, String requestId,
            Map<String, Object> permissionSnapshot, String code, String message) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("applicationId", application.getId());
        detail.put("credentialVersion", credential.getCredentialVersion());
        detail.put("resourceType", input.resourceType());
        detail.put("resourceId", input.resourceId());
        detail.put("actionCode", input.actionCode());
        detail.put("failureCode", code);
        detail.put("message", message);
        auditRecorder.recordWithPermissionSnapshot(requestId,
                context == null ? application.getCreatedByAccountId() : context.accountId(),
                grant == null ? null : grant.getTargetSystemId(), grant == null ? null : grant.getTargetTenantId(),
                context == null ? null : context.memberId(), "APPLICATION_CALL_REJECTED", "APPLICATION",
                application.getId().toString(), code, permissionSnapshot, detail);
        applyFailureDisablePolicy(application, credential, grant, context, requestId, code);
    }

    private void applyFailureDisablePolicy(
            AppDefinition application,
            AppCredential credential,
            PublishedGrant grant,
            AuthenticatedContext targetContext,
            String requestId,
            String failureCode) {
        // Only failures raised while executing the already-authorized target count toward automatic shutdown.
        // Signature, replay, IP, scope and permission rejections must never let an attacker disable an application.
        if (grant == null || targetContext == null || !"ACTIVE".equals(application.getStatus())) return;
        Map<String, Object> policy = readMap(grant.getRateLimitJson());
        Number configuredThreshold = number(policy.get("failureDisableThreshold"));
        Number configuredWindow = number(policy.get("failureWindowSeconds"));
        long threshold = configuredThreshold == null ? 5 : configuredThreshold.longValue();
        long windowSeconds = configuredWindow == null ? 300 : configuredWindow.longValue();
        if (threshold < 1 || windowSeconds < 10) return;

        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(windowSeconds);
        long failures = callService.selectList(Wrappers.<AppCall>lambdaQuery()
                        .eq(AppCall::getApplicationId, application.getId())
                        .eq(AppCall::getApplicationVersionId, grant.getApplicationVersionId())
                        .eq(AppCall::getResourceType, grant.getResourceType())
                        .eq(AppCall::getResourceId, grant.getResourceId())
                        .eq(AppCall::getActionCode, grant.getActionCode())
                        .eq(AppCall::getStatus, "FAILED")
                        .ge(AppCall::getCalledAt, cutoff))
                .stream().filter(call -> call.getPermissionSnapshotJson() != null
                        && !call.getPermissionSnapshotJson().isBlank()).count();
        if (failures < threshold) return;

        transactions.executeWithoutResult(status -> {
            AppDefinition current = definitionService.selectById(application.getId());
            if (current == null || !"ACTIVE".equals(current.getStatus())) return;
            current.setStatus("DISABLED");
            if (definitionService.updateById(current) != 1) return;
            LocalDateTime now = LocalDateTime.now();
            for (AppCredential active : credentialService.selectList(Wrappers.<AppCredential>lambdaQuery()
                    .eq(AppCredential::getApplicationId, application.getId())
                    .eq(AppCredential::getStatus, "ACTIVE"))) {
                active.setStatus("REVOKED");
                active.setRevokedAt(now);
                credentialService.updateById(active);
            }
            auditRecorder.record(requestId, targetContext.accountId(),
                    grant.getTargetSystemId(), grant.getTargetTenantId(), targetContext.memberId(),
                    "APPLICATION_FAILURE_POLICY_DISABLED", "APPLICATION", application.getId().toString(),
                    "SUCCESS", Map.of(
                            "failureCode", failureCode,
                            "credentialVersion", credential.getCredentialVersion(),
                            "applicationVersionId", grant.getApplicationVersionId(),
                            "resource", grant.getResourceType() + ":" + grant.getResourceId(),
                            "action", grant.getActionCode(),
                            "failureCount", failures,
                            "threshold", threshold,
                            "windowSeconds", windowSeconds,
                            "newCallsAllowed", false));
        });
    }

    private AppCall baseCall(
            AppDefinition application, AppCredential credential, PublishedGrant grant,
            ApplicationBridgeModels.CallRequest input, String requestId, LocalDateTime timestamp, String nonce,
            String sourceAddress, String requestHash) {
        AppCall call = new AppCall();
        call.setApplicationId(application.getId());
        call.setApplicationVersionId(grant == null ? null : grant.getApplicationVersionId());
        call.setCredentialVersion(credential.getCredentialVersion());
        // Draft grant rows are replaceable; immutable application_version_id is the durable authorization link.
        call.setGrantId(null);
        call.setRequestId(requestId);
        call.setTraceId(requestId);
        call.setRequestTimestamp(timestamp);
        call.setNonce(nonce);
        call.setSourceAddress(sourceAddress);
        call.setTargetSystemId(input.targetSystemId());
        call.setTargetTenantId(input.targetTenantId());
        call.setResourceType(normalize(input.resourceType()));
        call.setResourceId(input.resourceId() == null ? "UNKNOWN" : input.resourceId().strip());
        call.setActionCode(normalize(input.actionCode()));
        call.setRequestHash(requestHash);
        call.setCalledAt(LocalDateTime.now());
        return call;
    }

    private ApplicationBridgeModels.CallRequest parseRequest(String rawBody) {
        try {
            ApplicationBridgeModels.CallRequest value = objectMapper.readValue(rawBody, ApplicationBridgeModels.CallRequest.class);
            if (value.resourceType() == null || value.resourceType().isBlank()
                    || value.resourceId() == null || value.resourceId().isBlank()
                    || value.actionCode() == null || value.actionCode().isBlank()) {
                throw invalid("APPLICATION_TARGET_REQUIRED", "调用必须明确资源类型、资源和动作");
            }
            return value;
        } catch (DomainException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            throw invalid("APPLICATION_CALL_BODY_INVALID", "应用调用请求体不是有效 JSON");
        }
    }

    private ApplicationBridgeModels.CallResult readResult(String json) {
        try {
            return objectMapper.readValue(json, ApplicationBridgeModels.CallResult.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored application response is invalid", exception);
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored application JSON is invalid", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Application call JSON could not be serialized", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private long parseTimestamp(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw unauthorized("APPLICATION_TIMESTAMP_INVALID", "应用调用时间戳格式无效");
        }
    }

    private void requireHeader(String value, String code, String message) {
        if (value == null || value.isBlank()) throw unauthorized(code, message);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of();
    }

    private Map<String, Object> safe(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private List<Long> numbers(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(this::number).filter(Objects::nonNull).toList();
    }

    private Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private int integer(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String stringOrDefault(Object value, String fallback) {
        String text = string(value);
        return text == null || text.isBlank() ? fallback : text;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value.strip().toUpperCase(Locale.ROOT);
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private DomainException unauthorized(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNAUTHORIZED);
    }

    private DomainException forbidden(String code, String message) {
        return new DomainException(code, message, HttpStatus.FORBIDDEN);
    }

    private DomainException conflict(String code, String message) {
        return new DomainException(code, message, HttpStatus.CONFLICT);
    }

    /**
     * Runtime authorization is resolved from the immutable current application version. Draft grant rows are only
     * authoring material and may be replaced at any time; they must never change an already published bridge.
     */
    private static final class PublishedGrant {
        private final Long id;
        private final Long applicationVersionId;
        private final Integer applicationVersionNumber;
        private final String applicationVersionHash;
        private final String targetType;
        private final Long targetSystemId;
        private final Long targetTenantId;
        private final String resourceType;
        private final String resourceId;
        private final String actionCode;
        private final String dataScopeJson;
        private final String rateLimitJson;
        private final Long resourceObjectId;
        private final Long resourceVersionId;
        private final Integer resourceVersionNumber;
        private final String resourceDefinitionHash;
        private final List<PublishedField> fields;

        private PublishedGrant(
                Long id, Long applicationVersionId, Integer applicationVersionNumber, String applicationVersionHash,
                String targetType, Long targetSystemId, Long targetTenantId, String resourceType, String resourceId,
                String actionCode, String dataScopeJson, String rateLimitJson,
                Long resourceObjectId, Long resourceVersionId, Integer resourceVersionNumber,
                String resourceDefinitionHash, List<PublishedField> fields) {
            this.id = id;
            this.applicationVersionId = applicationVersionId;
            this.applicationVersionNumber = applicationVersionNumber;
            this.applicationVersionHash = applicationVersionHash;
            this.targetType = targetType;
            this.targetSystemId = targetSystemId;
            this.targetTenantId = targetTenantId;
            this.resourceType = resourceType;
            this.resourceId = resourceId;
            this.actionCode = actionCode;
            this.dataScopeJson = dataScopeJson;
            this.rateLimitJson = rateLimitJson;
            this.resourceObjectId = resourceObjectId;
            this.resourceVersionId = resourceVersionId;
            this.resourceVersionNumber = resourceVersionNumber;
            this.resourceDefinitionHash = resourceDefinitionHash;
            this.fields = List.copyOf(fields);
        }

        Long getId() { return id; }
        Long getApplicationVersionId() { return applicationVersionId; }
        Integer getApplicationVersionNumber() { return applicationVersionNumber; }
        String getApplicationVersionHash() { return applicationVersionHash; }
        String getTargetType() { return targetType; }
        Long getTargetSystemId() { return targetSystemId; }
        Long getTargetTenantId() { return targetTenantId; }
        String getResourceType() { return resourceType; }
        String getResourceId() { return resourceId; }
        String getActionCode() { return actionCode; }
        String getDataScopeJson() { return dataScopeJson; }
        String getRateLimitJson() { return rateLimitJson; }
        Long getResourceObjectId() { return resourceObjectId; }
        Long getResourceVersionId() { return resourceVersionId; }
        Integer getResourceVersionNumber() { return resourceVersionNumber; }
        String getResourceDefinitionHash() { return resourceDefinitionHash; }
        List<PublishedField> getFields() { return fields; }
    }

    private record PublishedField(String fieldCode, boolean readable, boolean writable) {
    }

    /** 发布版本固定的跨系统来源身份和目标执行身份。 */
    private record PublishedIdentity(
            Long mappingId,
            Long sourceSystemId,
            Long sourceTenantId,
            Long sourceTenantMemberId,
            Long targetSystemId,
            Long targetTenantId,
            Long targetTenantMemberId) {
    }

    private record Prepared(Long callId, ApplicationBridgeModels.CallResult replayed) {
    }

    private record TargetResult(String reference, Object value, String objectType, String objectId) {
    }
}
