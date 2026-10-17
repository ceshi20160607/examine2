package com.unique.unexamine.application.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.application.base.entity.AppCall;
import com.unique.unexamine.application.base.entity.AppCallNonce;
import com.unique.unexamine.application.base.entity.AppCredential;
import com.unique.unexamine.application.base.entity.AppDefinition;
import com.unique.unexamine.application.base.entity.AppPublication;
import com.unique.unexamine.application.base.entity.AppVersion;
import com.unique.unexamine.application.base.service.AppCallBaseService;
import com.unique.unexamine.application.base.service.AppCallNonceBaseService;
import com.unique.unexamine.application.base.service.AppCredentialBaseService;
import com.unique.unexamine.application.base.service.AppDefinitionBaseService;
import com.unique.unexamine.application.base.service.AppPublicationBaseService;
import com.unique.unexamine.application.base.service.AppVersionBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.DataScopeExpression;
import com.unique.unexamine.authorization.manage.DataScopeTerm;
import com.unique.unexamine.authorization.manage.PermissionGrant;
import com.unique.unexamine.flow.base.entity.FlowDefinition;
import com.unique.unexamine.flow.base.service.FlowDefinitionBaseService;
import com.unique.unexamine.flow.manage.FlowDesignModels;
import com.unique.unexamine.flow.manage.FlowDesignService;
import com.unique.unexamine.flow.manage.FlowRuntimeModels;
import com.unique.unexamine.flow.manage.FlowRuntimeService;
import com.unique.unexamine.foundation.base.entity.CoreQuotaPolicy;
import com.unique.unexamine.foundation.base.service.CoreQuotaPolicyBaseService;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleVersion;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleVersionBaseService;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.entity.PlatformDepartment;
import com.unique.unexamine.platform.base.entity.PlatformMember;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.platform.base.service.PlatformDepartmentBaseService;
import com.unique.unexamine.platform.base.service.PlatformMemberBaseService;
import com.unique.unexamine.runtimedata.manage.CreateRuntimeRecordRequest;
import com.unique.unexamine.runtimedata.manage.RuntimeDataService;
import com.unique.unexamine.runtimedata.manage.RuntimeListQueryField;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordList;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordView;
import com.unique.unexamine.runtimedata.manage.UpdateRuntimeRecordRequest;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.entity.SystemDefinition;
import com.unique.unexamine.system.base.entity.SystemDepartment;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.system.base.service.SystemDefinitionBaseService;
import com.unique.unexamine.system.base.service.SystemDepartmentBaseService;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.validation.Validator;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
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
    private static final Set<String> MODULE_BASE_QUERY_FIELDS = Set.of("title", "recordNumber", "status",
            "ownerMemberId", "departmentId", "createdAt", "updatedAt", "dataTenantName");
    private final AppDefinitionBaseService definitionService;
    private final AppCredentialBaseService credentialService;
    private final AppPublicationBaseService publicationService;
    private final AppVersionBaseService versionService;
    private final AppCallBaseService callService;
    private final AppCallNonceBaseService nonceService;
    private final CoreQuotaPolicyBaseService quotaPolicyService;
    private final PlatformAccountBaseService accountService;
    private final PlatformMemberBaseService platformMemberService;
    private final SystemMemberBaseService systemMemberService;
    private final SystemDefinitionBaseService systemService;
    private final SystemTenantBaseService tenantService;
    private final SystemTenantMemberBaseService tenantMemberService;
    private final SystemDepartmentBaseService systemDepartmentService;
    private final PlatformDepartmentBaseService platformDepartmentService;
    private final FlowDefinitionBaseService flowDefinitionService;
    private final ConfiguredModuleBaseService moduleService;
    private final ConfiguredModuleVersionBaseService moduleVersionService;
    private final FlowRuntimeService flowRuntimeService;
    private final FlowDesignService flowDesignService;
    private final RuntimeDataService runtimeDataService;
    private final ApplicationSecretCipher secretCipher;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final TransactionTemplate transactions;

    public ApplicationBridgeService(
            AppDefinitionBaseService definitionService,
            AppCredentialBaseService credentialService,
            AppPublicationBaseService publicationService,
            AppVersionBaseService versionService,
            AppCallBaseService callService,
            AppCallNonceBaseService nonceService,
            CoreQuotaPolicyBaseService quotaPolicyService,
            PlatformAccountBaseService accountService,
            PlatformMemberBaseService platformMemberService,
            SystemMemberBaseService systemMemberService,
            SystemDefinitionBaseService systemService,
            SystemTenantBaseService tenantService,
            SystemTenantMemberBaseService tenantMemberService,
            SystemDepartmentBaseService systemDepartmentService,
            PlatformDepartmentBaseService platformDepartmentService,
            FlowDefinitionBaseService flowDefinitionService,
            ConfiguredModuleBaseService moduleService,
            ConfiguredModuleVersionBaseService moduleVersionService,
            FlowRuntimeService flowRuntimeService,
            FlowDesignService flowDesignService,
            RuntimeDataService runtimeDataService,
            ApplicationSecretCipher secretCipher,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper,
            Validator validator,
            PlatformTransactionManager transactionManager) {
        this.definitionService = definitionService;
        this.credentialService = credentialService;
        this.publicationService = publicationService;
        this.versionService = versionService;
        this.callService = callService;
        this.nonceService = nonceService;
        this.quotaPolicyService = quotaPolicyService;
        this.accountService = accountService;
        this.platformMemberService = platformMemberService;
        this.systemMemberService = systemMemberService;
        this.systemService = systemService;
        this.tenantService = tenantService;
        this.tenantMemberService = tenantMemberService;
        this.systemDepartmentService = systemDepartmentService;
        this.platformDepartmentService = platformDepartmentService;
        this.flowDefinitionService = flowDefinitionService;
        this.moduleService = moduleService;
        this.moduleVersionService = moduleVersionService;
        this.flowRuntimeService = flowRuntimeService;
        this.flowDesignService = flowDesignService;
        this.runtimeDataService = runtimeDataService;
        this.secretCipher = secretCipher;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.validator = validator;
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
            AuthenticatedContext effectiveContext = targetContext(application, grant);
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
            requireInternalSourceIdentity(context);
            AuthenticatedContext effectiveContext = targetContext(application, grant);
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
        requireTenantCallQuota(grant);
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
            case "FLOW" -> executeFlow(application, credential, grant, input, targetContext, callId, requestId);
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
        call.setFinishTime(LocalDateTime.now());
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
            AppDefinition application, AppCredential credential, PublishedGrant grant,
            ApplicationBridgeModels.CallRequest input, AuthenticatedContext context,
            Long callId, String requestId) {
        if ("__flow_design__".equals(grant.getResourceId())) {
            return executeFlowDesign(application, grant, input, context, requestId);
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
        String externalPrincipalId = string(payload.get("externalPrincipalId"));
        if (!"START".equals(grant.getActionCode())
                && (externalPrincipalId == null || externalPrincipalId.isBlank())) {
            throw invalid("FLOW_EXTERNAL_PRINCIPAL_REQUIRED", "外部 Flow 调用必须提供审批人标识");
        }
        // 每项动作独立授权；外部身份只在当前应用命名空间内与任务匹配。
        if ("VIEW_INSTANCE".equals(grant.getActionCode())) {
            Long instanceId = number(payload.get("instanceId"));
            if (instanceId == null || instanceId < 1) throw invalid("FLOW_INSTANCE_ID_REQUIRED", "请提供流程实例编号");
            Map<String, Object> result = flowRuntimeService.applicationInstance(context, flow.getId(),
                    application.getId(), externalPrincipalId, instanceId);
            return new TargetResult("FLOW_INSTANCE:" + instanceId, result, "FLOW_INSTANCE", instanceId.toString());
        }
        if ("LIST_TASKS".equals(grant.getActionCode())) {
            List<Map<String, Object>> tasks = flowRuntimeService.applicationTasks(context, flow.getId(),
                    application.getId(), externalPrincipalId);
            return new TargetResult("FLOW_TASK_LIST:" + externalPrincipalId,
                    Map.of("items", tasks, "total", tasks.size()), "FLOW_TASK_LIST", externalPrincipalId);
        }
        if ("HANDLE".equals(grant.getActionCode())) {
            Long taskId = number(payload.get("taskId"));
            if (taskId == null || taskId < 1) throw invalid("FLOW_TASK_ID_REQUIRED", "请提供外部待办编号");
            String decision = string(payload.get("decision"));
            if (decision == null || !Set.of("APPROVE", "REJECT").contains(decision.toUpperCase(Locale.ROOT))) {
                throw invalid("FLOW_EXTERNAL_ACTION_INVALID", "外部审批动作只能为同意或拒绝");
            }
            FlowRuntimeModels.ActionResult handled = flowRuntimeService.handleApplication(context, flow.getId(),
                    application.getId(), externalPrincipalId, taskId,
                    new FlowRuntimeModels.HandleTaskRequest(decision.toUpperCase(Locale.ROOT),
                            string(payload.get("comment")), "app-call:" + callId,
                            map(payload.get("variables")), null, Map.of(), null), requestId);
            Map<String, Object> result = Map.of("actionId", handled.actionId(),
                    "instanceId", handled.instance().id(), "status", handled.instance().status(),
                    "taskId", taskId, "decision", decision.toUpperCase(Locale.ROOT));
            return new TargetResult("FLOW_TASK:" + taskId, result, "FLOW_TASK", taskId.toString());
        }
        if (!"START".equals(grant.getActionCode())) {
            throw invalid("APPLICATION_FLOW_ACTION_UNSUPPORTED", "应用 Flow 动作尚不支持受控调用");
        }
        String title = string(payload.get("title"));
        if (title == null || title.isBlank()) title = "应用调用 · " + grant.getResourceId();
        Map<String, Object> businessSnapshot = new LinkedHashMap<>();
        businessSnapshot.put("sourceType", "APPLICATION_CALL");
        businessSnapshot.put("applicationId", application.getId());
        businessSnapshot.put("applicationCallId", callId);
        businessSnapshot.put("requestId", requestId);
        businessSnapshot.put("credentialVersion", credential.getCredentialVersion());
        businessSnapshot.put("clientId", credential.getClientId());
        businessSnapshot.put("applicationVersionId", grant.getApplicationVersionId());
        businessSnapshot.put("flowVersionId", grant.getResourceVersionId());
        // 应用调用本身不是模块业务对象，来源放入快照，避免误按模块详情权限校验。
        FlowRuntimeModels.ActionResult result = flowRuntimeService.startApplication(context,
                new FlowRuntimeModels.StartRequest(flow.getId(), title, null, null,
                        businessSnapshot, map(payload.get("variables")), "app-call:" + callId),
                grant.getResourceVersionId(), grant.getResourceDefinitionHash(), application.getId(),
                externalPrincipalId, requestId);
        return new TargetResult("FLOW_INSTANCE:" + result.instance().id(), result,
                "FLOW_INSTANCE", result.instance().id().toString());
    }

    /** 草稿的每次操作先经当前发布的应用动作授权，再按应用归属与固定上下文取 Flow。 */
    private TargetResult executeFlowDesign(AppDefinition application, PublishedGrant grant,
            ApplicationBridgeModels.CallRequest input, AuthenticatedContext context, String requestId) {
        Map<String, Object> payload = safe(input.payload());
        if ("CREATE_DRAFT".equals(grant.getActionCode())) {
            FlowDesignModels.CreateFlowRequest request = flowInput(payload, FlowDesignModels.CreateFlowRequest.class);
            FlowDesignModels.FlowView flow = flowDesignService.createApplication(
                    context, request, application.getId(), requestId);
            return new TargetResult("FLOW:" + flow.id(), flow, "FLOW", flow.id().toString());
        }
        Long flowId = number(payload.get("flowId"));
        if (flowId == null || flowId < 1) {
            throw invalid("FLOW_ID_REQUIRED", "请提供当前应用的流程编号");
        }
        FlowDefinition flow = flowDefinitionService.selectById(flowId);
        if (flow == null || !Objects.equals(flow.getOwnerApplicationId(), application.getId())
                || !Objects.equals(flow.getPlatformId(), context.platformId())
                || !Objects.equals(flow.getSystemId(), context.systemId())
                || !Objects.equals(flow.getOwnerTenantId(), context.tenantId())) {
            throw new DomainException("FLOW_NOT_FOUND", "Flow 不存在或不属于当前应用", HttpStatus.NOT_FOUND);
        }
        Object result = switch (grant.getActionCode()) {
            case "VIEW_DRAFT" -> flowDesignService.detail(context, flowId);
            case "SAVE_DRAFT" -> flowDesignService.saveDraft(context, flowId,
                    flowInput(payload, FlowDesignModels.SaveDraftRequest.class), requestId);
            case "RENAME_DRAFT" -> flowDesignService.rename(context, flowId,
                    flowInput(payload, FlowDesignModels.RenameFlowRequest.class), requestId);
            case "VALIDATE" -> flowDesignService.publicationCheck(context, flowId);
            case "PUBLISH" -> flowDesignService.publish(context, flowId,
                    flowInput(payload, FlowDesignModels.PublishRequest.class), requestId);
            default -> throw invalid("APPLICATION_FLOW_ACTION_UNSUPPORTED", "Flow 配置动作未开放");
        };
        return new TargetResult("FLOW:" + flowId, result, "FLOW", flowId.toString());
    }

    private <T> T flowInput(Map<String, Object> payload, Class<T> type) {
        try {
            T input = objectMapper.convertValue(payload, type);
            var issues = validator.validate(input);
            if (!issues.isEmpty()) {
                throw invalid("FLOW_INPUT_INVALID", issues.stream().map(issue ->
                        issue.getPropertyPath() + " " + issue.getMessage()).sorted().findFirst().orElse("流程参数无效"));
            }
            return input;
        } catch (IllegalArgumentException exception) {
            throw invalid("FLOW_INPUT_INVALID", "流程参数格式无效");
        }
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
                requireReadableListQuery(grant, payload, context, requestId);
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
                if (number(payload.get("ownerMemberId")) == null) {
                    DataScopeExpression scope = context.dataScopes().get(
                            "MODULE:" + grant.getResourceId() + ":CREATE");
                    boolean defaultOwnerAllowed = scope != null && scope.terms().stream()
                            .anyMatch(term -> term.condition().path("memberIds").isArray()
                                    && java.util.stream.StreamSupport.stream(
                                    term.condition().path("memberIds").spliterator(), false)
                                    .anyMatch(id -> id.asLong() == context.memberId()));
                    if (!defaultOwnerAllowed) {
                        throw forbidden("APPLICATION_DEFAULT_OWNER_OUT_OF_SCOPE",
                                "未传负责人时会使用当前系统所属人；请将其加入应用范围，或传入范围内的负责人");
                    }
                }
                Map<String, JsonNode> jsonFields = new LinkedHashMap<>();
                fields.forEach((key, fieldValue) -> jsonFields.put(key, objectMapper.valueToTree(fieldValue)));
                CreateRuntimeRecordRequest request = new CreateRuntimeRecordRequest(
                        stringOrDefault(payload.get("title"), "应用调用创建"), string(payload.get("recordNumber")),
                        string(payload.get("status")), number(payload.get("ownerMemberId")),
                        number(payload.get("departmentId")), numbers(payload.get("participantMemberIds")), jsonFields,
                        null, number(payload.get("createdByMemberId")));
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

    /** 应用按发布字段读取列表；全文搜索可能命中隐藏字段，字段未完整授权时拒绝搜索。 */
    private void requireReadableListQuery(PublishedGrant grant, Map<String, Object> payload,
                                          AuthenticatedContext context, String requestId) {
        Set<String> readable = grant.getFields().stream().filter(PublishedField::readable)
                .map(PublishedField::fieldCode).collect(java.util.stream.Collectors.toSet());
        if (payload.get("filters") instanceof List<?> filters) {
            for (Object filter : filters) {
                if (filter instanceof Map<?, ?> values) {
                    String code = string(values.get("fieldCode"));
                    if (code != null && !MODULE_BASE_QUERY_FIELDS.contains(code) && !readable.contains(code)) {
                        throw forbidden("APPLICATION_FIELD_QUERY_DENIED", "应用不能按未授权字段筛选");
                    }
                }
            }
        }
        String sortField = stringOrDefault(payload.get("sortField"), "updatedAt");
        if (!MODULE_BASE_QUERY_FIELDS.contains(sortField) && !readable.contains(sortField)) {
            throw forbidden("APPLICATION_FIELD_QUERY_DENIED", "应用不能按未授权字段排序");
        }
        String search = string(payload.get("search"));
        if (search != null && !search.isBlank()) {
            List<RuntimeListQueryField> fields = runtimeDataService.queryFields(context, grant.getResourceId(),
                    "ACTIVE", "OWN", requestId);
            if (fields.stream().anyMatch(field -> field.searchable()
                    && !MODULE_BASE_QUERY_FIELDS.contains(field.code()) && !readable.contains(field.code()))) {
                throw forbidden("APPLICATION_FIELD_QUERY_DENIED", "应用不能通过全文搜索命中未授权字段");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void filterFields(Object value, Set<String> readable) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> map = (Map<String, Object>) raw;
            Object fields = map.get("fields");
            if (fields instanceof Map<?, ?> fieldMap) {
                ((Map<String, Object>) fieldMap).keySet().removeIf(key -> !readable.contains(key));
            }
            // 字段展示信息可能包含格式化后的业务值，也必须与发布授权使用同一字段范围。
            Object presentation = map.get("fieldPresentation");
            if (presentation instanceof Map<?, ?> presentationMap) {
                ((Map<String, Object>) presentationMap).keySet().removeIf(key -> !readable.contains(key));
            }
            // 查询目录中的字段名称和选项也属于字段信息，须与业务值使用同一发布授权。
            Object queryFields = map.get("queryFields");
            if (queryFields instanceof List<?> queryFieldList) {
                ((List<Object>) queryFieldList).removeIf(field -> field instanceof Map<?, ?> descriptor
                        && !MODULE_BASE_QUERY_FIELDS.contains(descriptor.get("code"))
                        && !readable.contains(descriptor.get("code")));
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



    /** 技术执行人只承载归属与审计，动作和数据范围完全来自当前发布授权。 */
    private AuthenticatedContext targetContext(AppDefinition application, PublishedGrant grant) {
        EffectiveScope scope = effectiveScope(application, grant);
        List<PermissionGrant> permissions = new ArrayList<>();
        permissions.add(new PermissionGrant(grant.getResourceType(), grant.getResourceId(), grant.getActionCode(), List.of()));
        Map<String, DataScopeExpression> dataScopes = new LinkedHashMap<>();
        dataScopes.put(grant.getResourceType() + ":" + grant.getResourceId() + ":" + grant.getActionCode(), scope.expression());
        // 编辑内部需读取旧字段；外部仍只可调用发布的 UPDATE 动作。
        if ("MODULE".equals(grant.getResourceType()) && "UPDATE".equals(grant.getActionCode())) {
            permissions.add(new PermissionGrant("MODULE", grant.getResourceId(), "DETAIL", List.of()));
            dataScopes.put("MODULE:" + grant.getResourceId() + ":DETAIL", scope.expression());
        }
        if ("FLOW".equals(grant.getResourceType())
                && Set.of("START", "HANDLE").contains(grant.getActionCode())) {
            permissions.add(new PermissionGrant("FLOW", grant.getTargetSystemId() == null ? "PLATFORM" : "SYSTEM",
                    grant.getActionCode(), List.of()));
        }
        if ("FLOW".equals(grant.getResourceType()) && "__flow_design__".equals(grant.getResourceId())) {
            String designAction = switch (grant.getActionCode()) {
                case "CREATE_DRAFT", "SAVE_DRAFT", "RENAME_DRAFT" -> "DESIGN";
                case "VIEW_DRAFT" -> "VIEW";
                case "VALIDATE", "PUBLISH" -> "PUBLISH";
                default -> null;
            };
            if (designAction != null) {
                permissions.add(new PermissionGrant("FLOW",
                        grant.getTargetSystemId() == null ? "PLATFORM" : "SYSTEM", designAction, List.of()));
            }
        }
        if (grant.getTargetSystemId() == null) {
            // 平台 Flow 用应用创建人满足现有发起人字段；所选范围人员绝不作为执行身份。
            Long creatorId = application.getCreatedByAccountId();
            if (creatorId == null) throw forbidden("APPLICATION_CREATOR_MISSING", "应用缺少创建人，无法归档 Flow 发起记录");
            PlatformAccount creator = accountService.selectById(creatorId);
            PlatformMember creatorMember = platformMemberService.selectList(Wrappers.<PlatformMember>lambdaQuery()
                    .eq(PlatformMember::getPlatformId, application.getPlatformId())
                    .eq(PlatformMember::getAccountId, creatorId)).stream().findFirst().orElse(null);
            return new AuthenticatedContext(null, creatorId, application.getPlatformId(), null, null,
                    creatorMember == null ? null : creatorMember.getId(), null,
                    creator == null ? application.getCode() : creator.getUsername(), application.getName(), "APPLICATION",
                    List.of(), permissions, dataScopes);
        }
        SystemDefinition system = systemService.selectById(grant.getTargetSystemId());
        SystemTenant tenant = tenantService.selectById(grant.getTargetTenantId());
        if (!active(system) || tenant == null || !"ACTIVE".equals(tenant.getStatus())
                || !Objects.equals(system.getPlatformId(), application.getPlatformId())
                || !Objects.equals(tenant.getSystemId(), system.getId())
                || "SINGLE".equals(system.getTenantMode()) && !Boolean.TRUE.equals(tenant.getMain())) {
            throw forbidden("APPLICATION_TARGET_TENANT_INACTIVE", "应用目标系统或租户当前不可访问");
        }
        PlatformAccount owner = accountService.selectById(system.getCreatorAccountId());
        if (owner == null || !"ACTIVE".equals(owner.getStatus())) {
            throw forbidden("APPLICATION_SYSTEM_OWNER_INACTIVE", "当前系统所属人不可用，请先修正系统所属权");
        }
        SystemMember member = systemMemberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                .eq(SystemMember::getSystemId, system.getId())
                .eq(SystemMember::getAccountId, owner.getId())
                .eq(SystemMember::getStatus, "ACTIVE")).stream().findFirst().orElseThrow(() ->
                forbidden("APPLICATION_SYSTEM_OWNER_MEMBER_MISSING", "当前系统所属人缺少有效系统成员映射"));
        SystemTenantMember tenantMember = tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                .eq(SystemTenantMember::getSystemId, system.getId())
                .eq(SystemTenantMember::getTenantId, tenant.getId())
                .eq(SystemTenantMember::getSystemMemberId, member.getId())
                .eq(SystemTenantMember::getStatus, "ACTIVE")).stream().findFirst().orElseThrow(() ->
                forbidden("APPLICATION_SYSTEM_OWNER_TENANT_MEMBER_MISSING", "当前系统所属人缺少目标租户成员映射"));
        return new AuthenticatedContext(null, owner.getId(), application.getPlatformId(), system.getId(), tenant.getId(),
                member.getId(), tenantMember.getId(), owner.getUsername(), member.getDisplayName(), "APPLICATION",
                List.of(), permissions, dataScopes);
    }

    /** 人员异常时排除其数据；部门异常时管理员必须先修正应用配置。 */
    private EffectiveScope effectiveScope(AppDefinition application, PublishedGrant grant) {
        Map<String, Object> configured = readMap(grant.getDataScopeJson());
        if (!"RESPONSIBLE".equals(string(configured.get("type")))) {
            throw forbidden("APPLICATION_DATA_SCOPE_INVALID", "应用发布授权缺少明确的负责人或部门范围，请重新发布");
        }
        List<Long> selectedMembers = numbers(configured.get("memberIds"));
        List<Long> selectedDepartments = numbers(configured.get("departmentIds"));
        List<Long> validMembers = new ArrayList<>();
        List<Long> validDepartments = new ArrayList<>();
        if (grant.getTargetSystemId() == null) {
            for (Long id : selectedDepartments) {
                PlatformDepartment department = platformDepartmentService.selectById(id);
                if (department == null || !Objects.equals(department.getPlatformId(), application.getPlatformId())
                        || !"ACTIVE".equals(department.getStatus())) {
                    throw forbidden("APPLICATION_SCOPE_DEPARTMENT_INACTIVE", "应用所选平台部门已停用或删除，请管理员修正应用配置");
                }
                validDepartments.add(id);
            }
            for (PlatformMember member : platformMemberService.selectList(Wrappers.<PlatformMember>lambdaQuery()
                    .eq(PlatformMember::getPlatformId, application.getPlatformId()))) {
                if (!"ACTIVE".equals(member.getStatus()) || !activeAccount(member.getAccountId())) continue;
                if (selectedMembers.contains(member.getId())
                        || validDepartments.contains(member.getDepartmentId())) validMembers.add(member.getId());
            }
        } else {
            for (Long id : selectedDepartments) {
                SystemDepartment department = systemDepartmentService.selectById(id);
                if (department == null || !Objects.equals(department.getSystemId(), grant.getTargetSystemId())
                        || !Objects.equals(department.getTenantId(), grant.getTargetTenantId())
                        || !"ACTIVE".equals(department.getStatus())) {
                    throw forbidden("APPLICATION_SCOPE_DEPARTMENT_INACTIVE", "应用所选系统部门已停用或删除，请管理员修正应用配置");
                }
                validDepartments.add(id);
            }
            for (SystemTenantMember tenantMember : tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                    .eq(SystemTenantMember::getSystemId, grant.getTargetSystemId())
                    .eq(SystemTenantMember::getTenantId, grant.getTargetTenantId())
                    .eq(SystemTenantMember::getStatus, "ACTIVE"))) {
                SystemMember member = systemMemberService.selectById(tenantMember.getSystemMemberId());
                if (member == null || !"ACTIVE".equals(member.getStatus())
                        || !Objects.equals(member.getSystemId(), grant.getTargetSystemId())
                        || !activeAccount(member.getAccountId())) continue;
                if (selectedMembers.contains(member.getId())
                        || validDepartments.contains(tenantMember.getDepartmentId())) validMembers.add(member.getId());
            }
        }
        if (validMembers.isEmpty()) {
            throw forbidden("APPLICATION_SCOPE_EMPTY", "应用配置范围内没有有效负责人，请管理员修正应用配置");
        }
        // 每次调用按有效账号与组织成员展开负责人，避免部门条件重新放行已停用人员负责的数据。
        DataScopeTerm owners = new DataScopeTerm("SPECIFIED_MEMBERS",
                objectMapper.valueToTree(Map.of("memberIds", validMembers.stream().distinct().toList())), List.of());
        return new EffectiveScope(new DataScopeExpression("UNION", List.of(owners)));
    }

    private boolean activeAccount(Long accountId) {
        PlatformAccount account = accountService.selectById(accountId);
        return account != null && "ACTIVE".equals(account.getStatus());
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
                accepted.setRequestTime(requestTimestamp);
                accepted.setExpireTime(LocalDateTime.now().plus(24, ChronoUnit.HOURS));
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
        if (!"ACTIVE".equals(credential.getStatus()) || credential.getValidStartTime().isAfter(now)
                || credential.getExpireTime() != null && !credential.getExpireTime().isAfter(now)) {
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

    /** 内部调用要先确认真实登录来源有效，来源身份不参与目标授权。 */
    private void requireInternalSourceIdentity(AuthenticatedContext context) {
        if (context.systemId() == null) {
            PlatformAccount account = accountService.selectById(context.accountId());
            // 平台登录会话不保存系统成员 ID；按平台与账号查平台成员，避免误拒绝有效来源。
            PlatformMember member = platformMemberService.selectList(Wrappers.<PlatformMember>lambdaQuery()
                    .eq(PlatformMember::getPlatformId, context.platformId())
                    .eq(PlatformMember::getAccountId, context.accountId())
                    .eq(PlatformMember::getStatus, "ACTIVE")).stream().findFirst().orElse(null);
            if (account == null || !"ACTIVE".equals(account.getStatus())
                    || member == null || !"ACTIVE".equals(member.getStatus())
                    || !Objects.equals(member.getPlatformId(), context.platformId())
                    || !Objects.equals(member.getAccountId(), context.accountId())) {
                throw forbidden("APPLICATION_SOURCE_IDENTITY_INACTIVE", "来源平台成员已停用");
            }
            return;
        }
        SystemDefinition system = systemService.selectById(context.systemId());
        SystemTenant tenant = tenantService.selectById(context.tenantId());
        SystemMember member = systemMemberService.selectById(context.memberId());
        SystemTenantMember tenantMember = tenantMemberService.selectById(context.tenantMemberId());
        PlatformAccount account = accountService.selectById(context.accountId());
        if (!active(system) || tenant == null || !"ACTIVE".equals(tenant.getStatus())
                || "SINGLE".equals(system.getTenantMode()) && !Boolean.TRUE.equals(tenant.getMain())
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
        if (!Objects.equals(readMap(grant.getDataScopeJson()), requested)) {
            throw forbidden("APPLICATION_DATA_SCOPE_EXPANSION", "调用方不能修改应用已发布的负责人或部门范围");
        }
    }

    /** 模块调用校验发布时固定的物理模块和版本快照。 */
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
                        .ge(AppCall::getCallTime, cutoff))
                .size();
        if (count >= maximum) throw new DomainException(
                "APPLICATION_RATE_LIMITED", "应用调用频率超过授权限制", HttpStatus.TOO_MANY_REQUESTS);
    }

    /** 已授权的新调用按目标租户计次；策略行锁保证并发请求不会同时占用最后一个名额。 */
    private void requireTenantCallQuota(PublishedGrant grant) {
        if (grant.getTargetSystemId() == null || grant.getTargetTenantId() == null) return;
        CoreQuotaPolicy policy = quotaPolicyService.selectOne(Wrappers.<CoreQuotaPolicy>lambdaQuery()
                .eq(CoreQuotaPolicy::getContextType, "SYSTEM")
                .eq(CoreQuotaPolicy::getSystemId, grant.getTargetSystemId())
                .eq(CoreQuotaPolicy::getTenantId, grant.getTargetTenantId())
                .eq(CoreQuotaPolicy::getQuotaCode, "APPLICATION_CALL_COUNT")
                .eq(CoreQuotaPolicy::getStatus, "ACTIVE")
                .last("FOR UPDATE"));
        if (policy == null) return;

        LocalDateTime periodStart = null;
        LocalDateTime periodEnd = null;
        if ("DAY".equals(policy.getPeriodType())) {
            periodStart = LocalDate.now().atStartOfDay();
            periodEnd = periodStart.plusDays(1);
        } else if ("MONTH".equals(policy.getPeriodType())) {
            periodStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
            periodEnd = periodStart.plusMonths(1);
        } else if (!"TOTAL".equals(policy.getPeriodType())) {
            throw conflict("TENANT_APPLICATION_CALL_QUOTA_INVALID", "应用调用配额周期配置无效");
        }

        // 前面已经读取过幂等日志；锁定读取才能看到等待策略锁期间刚提交的调用。
        long used = callService.selectList(Wrappers.<AppCall>lambdaQuery()
                .select(AppCall::getId)
                .eq(AppCall::getTargetSystemId, grant.getTargetSystemId())
                .eq(AppCall::getTargetTenantId, grant.getTargetTenantId())
                .isNotNull(AppCall::getIdempotencyKey)
                .isNotNull(AppCall::getPermissionSnapshotJson)
                .ge(periodStart != null, AppCall::getCallTime, periodStart)
                .lt(periodEnd != null, AppCall::getCallTime, periodEnd)
                .last("FOR UPDATE")).size();
        if (used >= policy.getHardLimit()) {
            throw new DomainException("TENANT_APPLICATION_CALL_QUOTA_EXCEEDED",
                    "当前工作空间应用调用次数已达到上限：" + policy.getHardLimit(),
                    HttpStatus.TOO_MANY_REQUESTS);
        }
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
        snapshot.put("decision", "APPLICATION_PUBLISHED_GRANT_ALLOW");
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
                call.setFinishTime(LocalDateTime.now());
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
            call.setFinishTime(LocalDateTime.now());
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
                        .ge(AppCall::getCallTime, cutoff))
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
                active.setRevokeTime(now);
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
        call.setRequestTime(timestamp);
        call.setNonce(nonce);
        call.setSourceAddress(sourceAddress);
        call.setTargetSystemId(input.targetSystemId());
        call.setTargetTenantId(input.targetTenantId());
        call.setResourceType(normalize(input.resourceType()));
        call.setResourceId(input.resourceId() == null ? "UNKNOWN" : input.resourceId().strip());
        call.setActionCode(normalize(input.actionCode()));
        call.setRequestHash(requestHash);
        call.setCallTime(LocalDateTime.now());
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

    /** 本次调用按当前有效组织状态收敛出的负责人范围。 */
    private record EffectiveScope(DataScopeExpression expression) {
    }

    private record Prepared(Long callId, ApplicationBridgeModels.CallResult replayed) {
    }

    private record TargetResult(String reference, Object value, String objectType, String objectId) {
    }
}
