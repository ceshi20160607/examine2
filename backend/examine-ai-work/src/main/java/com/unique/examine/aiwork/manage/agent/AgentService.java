package com.unique.examine.aiwork.manage.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.aiwork.base.entity.AgentAuditLog;
import com.unique.examine.aiwork.base.entity.AgentConfirmation;
import com.unique.examine.aiwork.base.entity.AgentModelAuthorization;
import com.unique.examine.aiwork.base.entity.AgentPolicy;
import com.unique.examine.aiwork.base.entity.AgentSession;
import com.unique.examine.aiwork.base.service.AgentAuditLogBaseService;
import com.unique.examine.aiwork.base.service.AgentConfirmationBaseService;
import com.unique.examine.aiwork.base.service.AgentModelAuthorizationBaseService;
import com.unique.examine.aiwork.base.service.AgentPolicyBaseService;
import com.unique.examine.aiwork.base.service.AgentSessionBaseService;
import com.unique.examine.aiwork.manage.agent.AgentModels.AgentAuditLogQuery;
import com.unique.examine.aiwork.manage.agent.AgentModels.AgentAuditLogVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.AgentBoundaryVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.AgentMessageRequest;
import com.unique.examine.aiwork.manage.agent.AgentModels.AgentMessageResultVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.AgentPolicyPublishCheckVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.AgentPolicySaveRequest;
import com.unique.examine.aiwork.manage.agent.AgentModels.AgentPolicyScopeVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.AgentPolicyVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.AgentSessionCreateRequest;
import com.unique.examine.aiwork.manage.agent.AgentModels.AgentSessionVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.ConfirmationActionRequest;
import com.unique.examine.aiwork.manage.agent.AgentModels.ConfirmationSummaryVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.DataOutboundPolicyVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.DesensitizePolicyVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.FieldDiffVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.GeneratedObjectVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.ImpactRefVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.ModelAuthorizationSaveRequest;
import com.unique.examine.aiwork.manage.agent.AgentModels.ModelAuthorizationVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.OperationResultVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.OutboundLimitVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.PermissionClipVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.PlatformAgentActionDraftVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.PlatformAgentConfirmRequest;
import com.unique.examine.aiwork.manage.agent.AgentModels.PlatformAgentConfirmResultVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.PublishCheckItemVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.PublishStatusVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.QuotaConfigVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.RejectedItemVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.SecretRefVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.SystemWriteConfirmationRequest;
import com.unique.examine.aiwork.manage.agent.AgentModels.SystemWriteConfirmationVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.ToolCallVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.WorkDraftConfirmRequest;
import com.unique.examine.aiwork.manage.agent.AgentModels.WorkDraftConfirmResultVO;
import com.unique.examine.aiwork.manage.agent.AgentModels.WorkDraftSourceVO;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.core.base.entity.SysSecretRef;
import com.unique.examine.core.base.service.SysSecretRefBaseService;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.plat.base.entity.PlatAccount;
import com.unique.examine.plat.manage.common.CurrentAccountProvider;
import com.unique.examine.plat.manage.common.SystemMemberContextResolver;
import com.unique.examine.plat.manage.common.SystemMemberContextResolver.SystemMemberContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * AI Agent service backed by persisted authorization, policy, session, confirmation and audit tables.
 */
@Service
public class AgentService {

    private static final String PLATFORM_SCOPE = "platform";
    private static final String SYSTEM_SCOPE = "system";
    private static final String DEFAULT_MODEL_PROVIDER = "LOCAL";
    private static final String DEFAULT_MODEL_NAME = "Local-Qwen-72B";
    private static final String DEFAULT_PROMPT_VERSION = "agent_prompt_v20260624_001";
    private static final String DEFAULT_POLICY_VERSION = "agent_policy_v20260624_001";
    private static final String PLATFORM_PERMISSION_SNAPSHOT = "platform_permission_snapshot";
    private static final int ENABLED = 1;

    private final AgentModelAuthorizationBaseService authorizationBaseService;
    private final AgentPolicyBaseService policyBaseService;
    private final AgentSessionBaseService sessionBaseService;
    private final AgentAuditLogBaseService auditLogBaseService;
    private final AgentConfirmationBaseService confirmationBaseService;
    private final SysSecretRefBaseService secretRefBaseService;
    private final CurrentAccountProvider currentAccountProvider;
    private final SystemMemberContextResolver systemMemberContextResolver;
    private final ObjectMapper objectMapper;

    public AgentService(AgentModelAuthorizationBaseService authorizationBaseService,
                        AgentPolicyBaseService policyBaseService,
                        AgentSessionBaseService sessionBaseService,
                        AgentAuditLogBaseService auditLogBaseService,
                        AgentConfirmationBaseService confirmationBaseService,
                        SysSecretRefBaseService secretRefBaseService,
                        CurrentAccountProvider currentAccountProvider,
                        SystemMemberContextResolver systemMemberContextResolver,
                        ObjectMapper objectMapper) {
        this.authorizationBaseService = authorizationBaseService;
        this.policyBaseService = policyBaseService;
        this.sessionBaseService = sessionBaseService;
        this.auditLogBaseService = auditLogBaseService;
        this.confirmationBaseService = confirmationBaseService;
        this.secretRefBaseService = secretRefBaseService;
        this.currentAccountProvider = currentAccountProvider;
        this.systemMemberContextResolver = systemMemberContextResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * Query platform model authorizations from persisted configuration.
     *
     * @param pageRequest page request
     * @return model authorization page
     */
    public PageResult<ModelAuthorizationVO> modelAuthorizations(PageRequest pageRequest) {
        List<ModelAuthorizationVO> records = authorizationBaseService.list(new LambdaQueryWrapper<AgentModelAuthorization>()
                        .orderByDesc(AgentModelAuthorization::getCreatedAt)
                        .orderByDesc(AgentModelAuthorization::getId))
                .stream()
                .map(entity -> toModelAuthorizationVO(entity, null))
                .toList();
        return page(records, pageRequest);
    }

    /**
     * Create or update a platform model authorization and its SecretRef metadata.
     *
     * @param authorizationId authorization id for update
     * @param request save request
     * @param headerIdempotencyKey idempotency key from request header
     * @return saved authorization
     */
    @Transactional(rollbackFor = Exception.class)
    public ModelAuthorizationVO saveModelAuthorization(String authorizationId, ModelAuthorizationSaveRequest request,
                                                       String headerIdempotencyKey) {
        LocalDateTime now = LocalDateTime.now();
        String code = normalizeCode(Objects.isNull(request) ? null : request.authorizationCode(),
                "model_auth_" + shortCode(RequestContext.current().traceId()));
        AgentModelAuthorization entity = resolveAuthorizationForSave(authorizationId, code);
        boolean created = Objects.isNull(entity.getId());
        String secretRefId = resolveModelSecretRefId(entity, request, code);
        SysSecretRef secretRef = saveSecretRef(secretRefId, "MODEL",
                safeText(Objects.isNull(request) ? null : request.modelName(), DEFAULT_MODEL_NAME)
                        + " credential", now);

        entity.setAuthorizationCode(code);
        entity.setModelProvider(safeText(Objects.isNull(request) ? null : request.modelProvider(),
                DEFAULT_MODEL_PROVIDER));
        entity.setModelName(safeText(Objects.isNull(request) ? null : request.modelName(), DEFAULT_MODEL_NAME));
        entity.setModelCredentialRefId(secretRef.getSecretRefId());
        entity.setQuotaConfig(writeJson(Objects.isNull(request) || Objects.isNull(request.quota())
                ? defaultQuota() : request.quota()));
        entity.setDataOutboundPolicy(writeJson(Objects.isNull(request) || Objects.isNull(request.dataOutboundPolicy())
                ? defaultOutboundPolicy() : request.dataOutboundPolicy()));
        entity.setStatus(Objects.isNull(request) || Objects.isNull(request.status()) ? ENABLED : request.status());
        entity.setVersionNo("model_auth_v" + now.toLocalDate());
        if (created) {
            entity.setCreatedAt(now);
            authorizationBaseService.saveEntity(entity);
        } else {
            authorizationBaseService.updateById(entity);
        }
        return toModelAuthorizationVO(entity, operation(created ? "CREATE_MODEL_AUTHORIZATION"
                : "UPDATE_MODEL_AUTHORIZATION", resolveIdempotencyKey(headerIdempotencyKey,
                Objects.isNull(request) ? null : request.idempotencyKey(), "model_authorization"),
                created ? "CREATED" : "UPDATED", null, RequestContext.current().auditLogId()));
    }

    /**
     * Query system Agent policies from persisted configuration.
     *
     * @param systemId system id
     * @param pageRequest page request
     * @return policy page
     */
    public PageResult<AgentPolicyVO> policies(String systemId, PageRequest pageRequest) {
        Long resolvedSystemId = parseRequiredId(systemId, "System id must be numeric.");
        List<AgentPolicyVO> records = policyBaseService.list(new LambdaQueryWrapper<AgentPolicy>()
                        .eq(AgentPolicy::getSystemId, resolvedSystemId)
                        .orderByDesc(AgentPolicy::getCreatedAt)
                        .orderByDesc(AgentPolicy::getId))
                .stream()
                .map(entity -> toPolicyVO(entity, null))
                .toList();
        return page(records, pageRequest);
    }

    /**
     * Create or update one system Agent policy.
     *
     * @param systemId system id
     * @param policyId policy id for update
     * @param request save request
     * @param headerIdempotencyKey idempotency key from request header
     * @return saved policy
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentPolicyVO savePolicy(String systemId, String policyId, AgentPolicySaveRequest request,
                                    String headerIdempotencyKey) {
        SystemMemberContext context = systemMemberContextResolver.resolve(systemId);
        LocalDateTime now = LocalDateTime.now();
        AgentPolicyScopeVO scope = Objects.isNull(request) || Objects.isNull(request.scope())
                ? defaultPolicyScope() : request.scope();
        String policyCode = normalizeCode(Objects.isNull(request) ? null : request.policyCode(),
                "agent_policy_" + shortCode(RequestContext.current().traceId()));
        String policyVersion = safeText(scope.policyVersion(), DEFAULT_POLICY_VERSION);
        AgentPolicy entity = resolvePolicyForSave(policyId, context.systemId(), context.tenantId(),
                policyCode, policyVersion);
        boolean created = Objects.isNull(entity.getId());

        entity.setSystemId(context.systemId());
        entity.setTenantId(context.tenantId());
        entity.setPolicyCode(policyCode);
        entity.setModuleScope(writeJson(safeList(scope.moduleScope(), List.of())));
        entity.setFieldScope(writeJson(Objects.isNull(scope.fieldScope()) ? Map.of() : scope.fieldScope()));
        entity.setActionScope(writeJson(safeList(scope.actionScope(), List.of())));
        entity.setDataScopeExpression(writeJson(Map.of("expression", safeText(scope.dataScopeExpression(),
                "currentMemberAuthorizedData"))));
        entity.setOutboundLimit(writeJson(Objects.isNull(scope.outboundLimit())
                ? defaultOutboundLimit() : scope.outboundLimit()));
        entity.setDesensitizePolicy(writeJson(Objects.isNull(scope.desensitizePolicy())
                ? defaultDesensitizePolicy() : scope.desensitizePolicy()));
        entity.setPolicyVersion(policyVersion);
        entity.setStatus(Objects.isNull(request) || Objects.isNull(request.status()) ? ENABLED : request.status());
        if (created) {
            entity.setCreatedAt(now);
            policyBaseService.saveEntity(entity);
        } else {
            policyBaseService.updateById(entity);
        }
        AgentAuditLogVO audit = saveAuditLog(SYSTEM_SCOPE, context.systemId(), context.tenantId(), null, null,
                modelVersion(resolveFirstAuthorization(null)), DEFAULT_PROMPT_VERSION, policyVersion,
                permissionSnapshotId(context), Map.of("operation", created ? "create policy" : "update policy",
                        "policyCode", policyCode), List.of(), defaultDesensitizeSnapshot(), defaultOutboundSnapshot());
        return toPolicyVO(entity, operation(created ? "CREATE_AGENT_POLICY" : "UPDATE_AGENT_POLICY",
                resolveIdempotencyKey(headerIdempotencyKey, Objects.isNull(request) ? null : request.idempotencyKey(),
                        "agent_policy"), created ? "CREATED" : "UPDATED", null, audit.auditLogId()));
    }

    /**
     * Run publish checks for a persisted Agent policy.
     *
     * @param systemId system id
     * @param policyId policy id
     * @return publish check result
     */
    public AgentPolicyPublishCheckVO publishCheck(String systemId, String policyId) {
        Long resolvedSystemId = parseRequiredId(systemId, "System id must be numeric.");
        AgentPolicy policy = requirePolicy(resolvedSystemId, policyId);
        AgentPolicyScopeVO scope = toPolicyScope(policy);
        boolean moduleReady = !safeList(scope.moduleScope(), List.of()).isEmpty();
        boolean fieldReady = Objects.nonNull(scope.fieldScope()) && !scope.fieldScope().isEmpty();
        boolean actionReady = !safeList(scope.actionScope(), List.of()).isEmpty();
        boolean passed = moduleReady && fieldReady && actionReady && Objects.equals(policy.getStatus(), ENABLED);
        saveAuditLog(SYSTEM_SCOPE, policy.getSystemId(), policy.getTenantId(), null, null,
                modelVersion(resolveFirstAuthorization(null)), DEFAULT_PROMPT_VERSION, policy.getPolicyVersion(),
                null, Map.of("operation", "publish check", "policyId", policy.getId(), "passed", passed),
                List.of(tool("agent_policy_publish_check", passed ? "PASS" : "BLOCKED", Map.of("policyId",
                        String.valueOf(policy.getId())), Map.of("moduleReady", moduleReady,
                        "fieldReady", fieldReady, "actionReady", actionReady))),
                defaultDesensitizeSnapshot(), defaultOutboundSnapshot());
        return new AgentPolicyPublishCheckVO(String.valueOf(policy.getId()), passed, policy.getPolicyVersion(),
                List.of(new PublishCheckItemVO("MODULE_SCOPE_READY", "Module scope", moduleReady ? "PASS" : "BLOCK",
                                moduleReady, moduleReady ? "Module scope is configured."
                                : "At least one accessible module is required."),
                        new PublishCheckItemVO("FIELD_SCOPE_READY", "Field scope", fieldReady ? "PASS" : "BLOCK",
                                fieldReady, fieldReady ? "Field read/write scope is configured."
                                : "Readable and writable fields must be configured."),
                        new PublishCheckItemVO("ACTION_SCOPE_READY", "Action scope", actionReady ? "PASS" : "BLOCK",
                                actionReady, actionReady ? "Allowed actions are configured."
                                : "At least one Agent action is required."),
                        new PublishCheckItemVO("POLICY_ENABLED", "Policy status",
                                Objects.equals(policy.getStatus(), ENABLED) ? "PASS" : "BLOCK",
                                Objects.equals(policy.getStatus(), ENABLED),
                                Objects.equals(policy.getStatus(), ENABLED) ? "Policy is enabled."
                                        : "Policy is disabled.")),
                impactRefs(policy, scope), RequestContext.current().traceId());
    }

    /**
     * Create a platform Agent session.
     *
     * @param request create request
     * @return session view
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentSessionVO createPlatformSession(AgentSessionCreateRequest request) {
        PlatAccount account = currentAccountProvider.currentAccount();
        AgentModelAuthorization authorization = resolveFirstAuthorization(Objects.isNull(request)
                ? null : request.authorizationCode());
        AgentSession session = new AgentSession();
        LocalDateTime now = LocalDateTime.now();
        session.setSessionId("agent_session_platform_" + shortCode(RequestContext.current().traceId()));
        session.setScope(PLATFORM_SCOPE);
        session.setAuthorizationCode(authorization.getAuthorizationCode());
        session.setModelVersion(modelVersion(authorization));
        session.setPromptVersion(safeText(Objects.isNull(request) ? null : request.promptVersion(),
                DEFAULT_PROMPT_VERSION));
        session.setPermissionSnapshotId(PLATFORM_PERMISSION_SNAPSHOT);
        session.setStatus("ACTIVE");
        session.setCreatedBy(account.getId());
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessionBaseService.saveEntity(session);
        saveAuditLog(PLATFORM_SCOPE, null, null, session.getSessionId(), null, session.getModelVersion(),
                session.getPromptVersion(), "platform_agent_policy", session.getPermissionSnapshotId(),
                Map.of("openingQuestion", safeText(Objects.isNull(request) ? null : request.openingQuestion(), "")),
                List.of(), defaultDesensitizeSnapshot(), Map.of("allowSystemBusinessData", false));
        return toSessionVO(session, "platform_agent_policy");
    }

    /**
     * Create a system Agent session inside the current SystemSwitchContext.
     *
     * @param systemId system id
     * @param request create request
     * @return session view
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentSessionVO createSystemSession(String systemId, AgentSessionCreateRequest request) {
        SystemMemberContext context = systemMemberContextResolver.resolve(systemId);
        AgentModelAuthorization authorization = resolveFirstAuthorization(Objects.isNull(request)
                ? null : request.authorizationCode());
        AgentPolicy policy = resolveFirstPolicy(context, Objects.isNull(request) ? null : request.policyCode());
        AgentSession session = new AgentSession();
        LocalDateTime now = LocalDateTime.now();
        session.setSessionId("agent_session_system_" + shortCode(RequestContext.current().traceId()));
        session.setScope(SYSTEM_SCOPE);
        session.setSystemId(context.systemId());
        session.setTenantId(context.tenantId());
        session.setSystemMemberId(context.systemMemberId());
        session.setAuthorizationCode(authorization.getAuthorizationCode());
        session.setModelVersion(modelVersion(authorization));
        session.setPromptVersion(safeText(Objects.isNull(request) ? null : request.promptVersion(),
                DEFAULT_PROMPT_VERSION));
        session.setPermissionSnapshotId(permissionSnapshotId(context));
        session.setStatus("ACTIVE");
        session.setCreatedBy(context.accountId());
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessionBaseService.saveEntity(session);
        saveAuditLog(SYSTEM_SCOPE, context.systemId(), context.tenantId(), session.getSessionId(), null,
                session.getModelVersion(), session.getPromptVersion(), policy.getPolicyVersion(),
                session.getPermissionSnapshotId(), Map.of("openingQuestion",
                        safeText(Objects.isNull(request) ? null : request.openingQuestion(), "")),
                List.of(), defaultDesensitizeSnapshot(), defaultOutboundSnapshot());
        return toSessionVO(session, policy.getPolicyVersion());
    }

    /**
     * Send one platform Agent message and persist the audit and pending confirmation.
     *
     * @param sessionId session id
     * @param request message request
     * @return message result
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentMessageResultVO sendPlatformMessage(String sessionId, AgentMessageRequest request) {
        AgentSession session = requireSession(sessionId, PLATFORM_SCOPE);
        List<ToolCallVO> tools = platformTools(request);
        AgentAuditLogVO audit = saveAuditLog(PLATFORM_SCOPE, null, null, session.getSessionId(), null,
                session.getModelVersion(), session.getPromptVersion(), "platform_agent_policy",
                session.getPermissionSnapshotId(), Map.of("message", safeText(Objects.isNull(request)
                        ? null : request.message(), "")), tools, defaultDesensitizeSnapshot(),
                Map.of("allowSystemBusinessData", false));
        AgentConfirmation confirmation = upsertConfirmation(PLATFORM_SCOPE, null, null,
                platformConfirmationId(session.getSessionId()), "PLATFORM_AGENT_CONFIRM",
                "WAITING_HUMAN_CONFIRM", Map.of("message", safeText(Objects.isNull(request)
                        ? null : request.message(), "")), Map.of("scope", PLATFORM_SCOPE,
                        "allowedTargetTypes", List.of("PLATFORM_TASK", "PLATFORM_MESSAGE", "PLATFORM_LOG")),
                session.getPermissionSnapshotId(), null, audit.auditLogId());
        touchSession(session);
        return new AgentMessageResultVO(session.getSessionId(), "assistant",
                "Platform Agent checked model authorization, quota and platform-only action boundaries.",
                tools, List.of(toConfirmationSummary(confirmation, true)), audit,
                operation("PLATFORM_AGENT_MESSAGE", Objects.isNull(request) ? null : request.idempotencyKey(),
                        "REPLIED", null, audit.auditLogId()));
    }

    /**
     * Send one system Agent message and persist write/work confirmations for manual review.
     *
     * @param systemId system id
     * @param sessionId session id
     * @param request message request
     * @return message result
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentMessageResultVO sendSystemMessage(String systemId, String sessionId, AgentMessageRequest request) {
        SystemMemberContext context = systemMemberContextResolver.resolve(systemId);
        AgentSession session = requireSession(sessionId, SYSTEM_SCOPE);
        verifySessionSystem(session, context.systemId());
        AgentPolicy policy = resolveFirstPolicy(context, null);
        List<ToolCallVO> tools = systemTools(context, request);
        AgentAuditLogVO audit = saveAuditLog(SYSTEM_SCOPE, context.systemId(), context.tenantId(),
                session.getSessionId(), null, session.getModelVersion(), session.getPromptVersion(),
                policy.getPolicyVersion(), session.getPermissionSnapshotId(), Map.of("message",
                        safeText(Objects.isNull(request) ? null : request.message(), "")),
                tools, defaultDesensitizeSnapshot(), defaultOutboundSnapshot());
        AgentConfirmation writeConfirmation = upsertConfirmation(SYSTEM_SCOPE, context.systemId(),
                context.tenantId(), systemWriteConfirmationId(session.getSessionId()),
                "SYSTEM_AGENT_WRITE_CONFIRM", "WAITING_HUMAN_CONFIRM",
                Map.of("message", safeText(Objects.isNull(request) ? null : request.message(), "")),
                Map.of("scope", SYSTEM_SCOPE, "systemId", context.systemId(), "fieldDiffs",
                        inferFieldDiffs(Objects.isNull(request) ? null : request.message()), "approvalRequired", true,
                        "compensationPlan", "Rollback the current Agent write and keep business audit evidence."),
                session.getPermissionSnapshotId(), null, audit.auditLogId());
        AgentConfirmation workConfirmation = upsertConfirmation(SYSTEM_SCOPE, context.systemId(),
                context.tenantId(), workConfirmationId(session.getSessionId()),
                "WORK_AGENT_DRAFT_CONFIRM", "WAITING_HUMAN_CONFIRM",
                Map.of("message", safeText(Objects.isNull(request) ? null : request.message(), "")),
                Map.of("scope", SYSTEM_SCOPE, "systemId", context.systemId(), "draftType",
                        "DAILY_REPORT_DRAFT", "sourceSnapshot", defaultWorkSources()),
                session.getPermissionSnapshotId(), null, audit.auditLogId());
        touchSession(session);
        return new AgentMessageResultVO(session.getSessionId(), "assistant",
                "System Agent prepared authorized data analysis. Business writes and work drafts require manual confirmation.",
                tools, List.of(toConfirmationSummary(writeConfirmation, true),
                toConfirmationSummary(workConfirmation, true)), audit,
                operation("SYSTEM_AGENT_MESSAGE", Objects.isNull(request) ? null : request.idempotencyKey(),
                        "REPLIED", null, audit.auditLogId()));
    }

    /**
     * Confirm or reject platform-only Agent generated actions.
     *
     * @param request confirmation request
     * @param headerIdempotencyKey idempotency key from request header
     * @return platform confirmation result
     */
    @Transactional(rollbackFor = Exception.class)
    public PlatformAgentConfirmResultVO platformConfirm(PlatformAgentConfirmRequest request,
                                                       String headerIdempotencyKey) {
        String targetScope = safeText(Objects.isNull(request) ? null : request.targetScope(), PLATFORM_SCOPE);
        boolean allowed = PLATFORM_SCOPE.equalsIgnoreCase(targetScope);
        boolean confirmed = allowed && Boolean.TRUE.equals(Objects.isNull(request) ? null : request.humanConfirmed());
        String confirmationId = platformConfirmationId(Objects.isNull(request) ? null : request.sessionId());
        String status = allowed ? (confirmed ? "CONFIRMED" : "WAITING_HUMAN_CONFIRM") : "REJECTED_BY_SCOPE";
        String disabledReason = allowed ? null
                : "Platform Agent cannot open or write system business data before system switch.";
        AgentAuditLogVO audit = saveAuditLog(PLATFORM_SCOPE, null, null,
                safeText(Objects.isNull(request) ? null : request.sessionId(), "platform_confirmation"),
                confirmationId, modelVersion(resolveFirstAuthorization(null)), DEFAULT_PROMPT_VERSION,
                "platform_agent_policy", PLATFORM_PERMISSION_SNAPSHOT,
                Map.of("sourceConversation", safeText(Objects.isNull(request)
                        ? null : request.sourceConversation(), "")),
                List.of(tool("platform_agent_confirm", status, Map.of("targetScope", targetScope),
                        Map.of("allowed", allowed))), defaultDesensitizeSnapshot(),
                Map.of("allowSystemBusinessData", false));
        AgentConfirmation confirmation = upsertConfirmation(PLATFORM_SCOPE, null, null, confirmationId,
                "PLATFORM_AGENT_CONFIRM", status, Map.of("sourceConversation", safeText(Objects.isNull(request)
                        ? null : request.sourceConversation(), "")),
                Map.of("scope", PLATFORM_SCOPE, "targetScope", targetScope, "platformActions",
                        safeList(Objects.isNull(request) ? null : request.platformActions(), List.of()),
                        "idempotencyKey", resolveIdempotencyKey(headerIdempotencyKey,
                                Objects.isNull(request) ? null : request.idempotencyKey(),
                                "platform_agent_confirm"), "disabledReason", safeText(disabledReason, "")),
                PLATFORM_PERMISSION_SNAPSHOT, confirmed ? currentAccountProvider.currentAccount().getId() : null,
                audit.auditLogId());
        List<GeneratedObjectVO> generatedObjects = confirmed ? generatedPlatformObjects(request) : List.of();
        return new PlatformAgentConfirmResultVO(toConfirmationVO(confirmation, audit,
                operation("PLATFORM_AGENT_CONFIRM", resolveIdempotencyKey(headerIdempotencyKey,
                        Objects.isNull(request) ? null : request.idempotencyKey(), "platform_agent_confirm"),
                        status, disabledReason, audit.auditLogId())), allowed,
                List.of("PLATFORM_TASK", "PLATFORM_MESSAGE", "PLATFORM_LOG"),
                allowed ? List.of() : List.of(new RejectedItemVO("system_business_payload",
                        "SYSTEM_BUSINESS_DATA", disabledReason)), generatedObjects);
    }

    /**
     * Create a system Agent write confirmation.
     *
     * @param systemId system id
     * @param request confirmation request
     * @param headerIdempotencyKey idempotency key from request header
     * @return write confirmation view
     */
    @Transactional(rollbackFor = Exception.class)
    public SystemWriteConfirmationVO createSystemWriteConfirmation(String systemId,
                                                                   SystemWriteConfirmationRequest request,
                                                                   String headerIdempotencyKey) {
        SystemMemberContext context = systemMemberContextResolver.resolve(systemId);
        String confirmationId = systemWriteConfirmationId(Objects.isNull(request) ? null : request.sessionId());
        List<FieldDiffVO> diffs = safeList(Objects.isNull(request) ? null : request.fieldDiffs(),
                inferFieldDiffs(Objects.isNull(request) ? null : request.sourceConversation()));
        String permissionSnapshotId = safeText(Objects.isNull(request) ? null : request.permissionSnapshotId(),
                permissionSnapshotId(context));
        AgentAuditLogVO audit = saveAuditLog(SYSTEM_SCOPE, context.systemId(), context.tenantId(),
                safeText(Objects.isNull(request) ? null : request.sessionId(), "system_write_confirmation"),
                confirmationId, modelVersion(resolveFirstAuthorization(null)), DEFAULT_PROMPT_VERSION,
                policyVersionForContext(context), permissionSnapshotId,
                Map.of("sourceConversation", safeText(Objects.isNull(request)
                        ? null : request.sourceConversation(), "")),
                List.of(tool("system_write_preview", "WAITING_HUMAN_CONFIRM", Map.of("systemId",
                        context.systemId()), Map.of("fieldDiffCount", diffs.size()))),
                defaultDesensitizeSnapshot(), defaultOutboundSnapshot());
        AgentConfirmation confirmation = upsertConfirmation(SYSTEM_SCOPE, context.systemId(), context.tenantId(),
                confirmationId, "SYSTEM_AGENT_WRITE_CONFIRM", "WAITING_HUMAN_CONFIRM",
                Map.of("sourceConversation", safeText(Objects.isNull(request)
                        ? null : request.sourceConversation(), "")),
                Map.of("scope", SYSTEM_SCOPE, "systemId", context.systemId(), "moduleId",
                        safeText(Objects.isNull(request) ? null : request.moduleId(), ""), "recordId",
                        safeText(Objects.isNull(request) ? null : request.recordId(), ""), "fieldDiffs", diffs,
                        "permissionClips", permissionClips(diffs), "approvalRequired",
                        Boolean.TRUE.equals(Objects.isNull(request) ? null : request.approvalRequired()),
                        "compensationPlan", safeText(Objects.isNull(request) ? null : request.compensationPlan(),
                                "Rollback the current Agent write and keep business audit evidence."),
                        "idempotencyKey", resolveIdempotencyKey(headerIdempotencyKey,
                                Objects.isNull(request) ? null : request.idempotencyKey(),
                                "system_write_confirm_create")),
                permissionSnapshotId, null, audit.auditLogId());
        return toSystemWriteConfirmationVO(confirmation, audit, operation("CREATE_SYSTEM_AGENT_WRITE_CONFIRMATION",
                resolveIdempotencyKey(headerIdempotencyKey, Objects.isNull(request) ? null : request.idempotencyKey(),
                        "system_write_confirm_create"), "WAITING_HUMAN_CONFIRM", null, audit.auditLogId()));
    }

    /**
     * Confirm one system Agent write confirmation.
     *
     * @param systemId system id
     * @param confirmationId confirmation id
     * @param request action request
     * @param headerIdempotencyKey idempotency key from request header
     * @return write confirmation view
     */
    @Transactional(rollbackFor = Exception.class)
    public SystemWriteConfirmationVO confirmSystemWrite(String systemId, String confirmationId,
                                                        ConfirmationActionRequest request,
                                                        String headerIdempotencyKey) {
        return handleSystemWriteConfirmation(systemId, confirmationId, request, headerIdempotencyKey,
                "CONFIRMED", "CONFIRM_SYSTEM_AGENT_WRITE");
    }

    /**
     * Reject one system Agent write confirmation.
     *
     * @param systemId system id
     * @param confirmationId confirmation id
     * @param request action request
     * @param headerIdempotencyKey idempotency key from request header
     * @return write confirmation view
     */
    @Transactional(rollbackFor = Exception.class)
    public SystemWriteConfirmationVO rejectSystemWrite(String systemId, String confirmationId,
                                                       ConfirmationActionRequest request,
                                                       String headerIdempotencyKey) {
        return handleSystemWriteConfirmation(systemId, confirmationId, request, headerIdempotencyKey,
                "REJECTED", "REJECT_SYSTEM_AGENT_WRITE");
    }

    /**
     * Confirm or save a work draft generated by system Agent.
     *
     * @param systemId system id
     * @param request confirmation request
     * @param headerIdempotencyKey idempotency key from request header
     * @return work draft confirmation result
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkDraftConfirmResultVO workDraftConfirm(String systemId, WorkDraftConfirmRequest request,
                                                     String headerIdempotencyKey) {
        SystemMemberContext context = systemMemberContextResolver.resolve(systemId);
        boolean confirmed = Boolean.TRUE.equals(Objects.isNull(request) ? null : request.humanConfirmed());
        String draftType = safeText(Objects.isNull(request) ? null : request.draftType(), "DAILY_REPORT_DRAFT");
        Map<String, Object> draftPayload = Objects.isNull(request) || Objects.isNull(request.draftPayload())
                ? defaultDraftPayload() : request.draftPayload();
        List<WorkDraftSourceVO> sources = safeList(Objects.isNull(request) ? null : request.sourceSnapshot(),
                defaultWorkSources());
        String confirmationId = workConfirmationId(Objects.isNull(request) ? null : request.sessionId());
        String status = confirmed ? "CONFIRMED" : "WAITING_HUMAN_CONFIRM";
        AgentAuditLogVO audit = saveAuditLog(SYSTEM_SCOPE, context.systemId(), context.tenantId(),
                safeText(Objects.isNull(request) ? null : request.sessionId(), "work_draft_confirmation"),
                confirmationId, modelVersion(resolveFirstAuthorization(null)), DEFAULT_PROMPT_VERSION,
                policyVersionForContext(context), permissionSnapshotId(context), Map.of("sourceConversation",
                        safeText(Objects.isNull(request) ? null : request.sourceConversation(), "")),
                List.of(tool("work_draft_confirm", status, Map.of("draftType", draftType),
                        Map.of("sourceCount", sources.size()))), defaultDesensitizeSnapshot(),
                defaultOutboundSnapshot());
        AgentConfirmation confirmation = upsertConfirmation(SYSTEM_SCOPE, context.systemId(), context.tenantId(),
                confirmationId, "WORK_AGENT_DRAFT_CONFIRM", status,
                Map.of("sourceConversation", safeText(Objects.isNull(request)
                        ? null : request.sourceConversation(), "")),
                Map.of("scope", SYSTEM_SCOPE, "systemId", context.systemId(), "draftType", draftType,
                        "draftPayload", draftPayload, "sourceSnapshot", sources, "idempotencyKey",
                        resolveIdempotencyKey(headerIdempotencyKey, Objects.isNull(request)
                                ? null : request.idempotencyKey(), "work_draft_confirm")),
                permissionSnapshotId(context), confirmed ? context.accountId() : null, audit.auditLogId());
        return new WorkDraftConfirmResultVO(toConfirmationVO(confirmation, audit,
                operation("WORK_AGENT_DRAFT_CONFIRM", resolveIdempotencyKey(headerIdempotencyKey,
                        Objects.isNull(request) ? null : request.idempotencyKey(), "work_draft_confirm"),
                        status, null, audit.auditLogId())), draftType, draftPayload, sources, true);
    }

    /**
     * Query persisted system Agent audit logs.
     *
     * @param systemId system id
     * @param pageRequest page request
     * @param query query object
     * @return audit log page
     */
    public PageResult<AgentAuditLogVO> auditLogs(String systemId, PageRequest pageRequest, AgentAuditLogQuery query) {
        Long resolvedSystemId = parseRequiredId(systemId, "System id must be numeric.");
        List<AgentAuditLogVO> records = auditLogBaseService.list(new LambdaQueryWrapper<AgentAuditLog>()
                        .eq(AgentAuditLog::getSystemId, resolvedSystemId)
                        .orderByDesc(AgentAuditLog::getCreatedAt)
                        .orderByDesc(AgentAuditLog::getId))
                .stream()
                .filter(log -> matchesAuditQuery(log, query))
                .map(this::toAuditLogVO)
                .toList();
        return page(records, pageRequest);
    }

    private SystemWriteConfirmationVO handleSystemWriteConfirmation(String systemId, String confirmationId,
                                                                    ConfirmationActionRequest request,
                                                                    String headerIdempotencyKey, String status,
                                                                    String operation) {
        SystemMemberContext context = systemMemberContextResolver.resolve(systemId);
        AgentConfirmation confirmation = requireConfirmation(confirmationId, "SYSTEM_AGENT_WRITE_CONFIRM");
        ensureConfirmationSystem(confirmation, context.systemId());
        AgentAuditLogVO audit = saveAuditLog(SYSTEM_SCOPE, context.systemId(), context.tenantId(), "system_write_action",
                confirmationId, modelVersion(resolveFirstAuthorization(null)), DEFAULT_PROMPT_VERSION,
                policyVersionForContext(context), confirmation.getPermissionSnapshotId(),
                Map.of("reason", safeText(Objects.isNull(request) ? null : request.reason(), "")),
                List.of(tool(operation, status, Map.of("confirmationId", confirmationId),
                        Map.of("status", status))), defaultDesensitizeSnapshot(), defaultOutboundSnapshot());
        confirmation.setStatus(status);
        confirmation.setConfirmedBy(context.accountId());
        confirmation.setConfirmedAt(LocalDateTime.now());
        confirmation.setAuditLogId(audit.auditLogId());
        confirmation.setTraceId(RequestContext.current().traceId());
        confirmationBaseService.updateById(confirmation);
        return toSystemWriteConfirmationVO(confirmation, audit, operation(operation,
                resolveIdempotencyKey(headerIdempotencyKey, Objects.isNull(request) ? null : request.idempotencyKey(),
                        operation.toLowerCase()), status, null, audit.auditLogId()));
    }

    private AgentModelAuthorization resolveAuthorizationForSave(String authorizationId, String code) {
        if (StringUtils.hasText(authorizationId)) {
            AgentModelAuthorization existing = findAuthorization(authorizationId);
            if (Objects.isNull(existing)) {
                throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED,
                        "Model authorization does not exist.");
            }
            return existing;
        }
        AgentModelAuthorization existing = authorizationBaseService.getOne(
                new LambdaQueryWrapper<AgentModelAuthorization>()
                        .eq(AgentModelAuthorization::getAuthorizationCode, code)
                        .last("LIMIT 1"), false);
        return Objects.nonNull(existing) ? existing : new AgentModelAuthorization();
    }

    private AgentPolicy resolvePolicyForSave(String policyId, Long systemId, Long tenantId, String policyCode,
                                             String policyVersion) {
        if (StringUtils.hasText(policyId)) {
            AgentPolicy existing = findPolicy(systemId, policyId);
            if (Objects.isNull(existing)) {
                throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "Agent policy does not exist.");
            }
            return existing;
        }
        AgentPolicy existing = policyBaseService.getOne(new LambdaQueryWrapper<AgentPolicy>()
                .eq(AgentPolicy::getSystemId, systemId)
                .eq(AgentPolicy::getTenantId, tenantId)
                .eq(AgentPolicy::getPolicyCode, policyCode)
                .eq(AgentPolicy::getPolicyVersion, policyVersion)
                .last("LIMIT 1"), false);
        return Objects.nonNull(existing) ? existing : new AgentPolicy();
    }

    private AgentModelAuthorization resolveFirstAuthorization(String requestedCode) {
        LambdaQueryWrapper<AgentModelAuthorization> wrapper = new LambdaQueryWrapper<AgentModelAuthorization>()
                .eq(AgentModelAuthorization::getStatus, ENABLED);
        if (StringUtils.hasText(requestedCode)) {
            wrapper.eq(AgentModelAuthorization::getAuthorizationCode, requestedCode);
        }
        AgentModelAuthorization authorization = authorizationBaseService.getOne(wrapper
                .orderByDesc(AgentModelAuthorization::getCreatedAt)
                .orderByDesc(AgentModelAuthorization::getId)
                .last("LIMIT 1"), false);
        if (Objects.isNull(authorization)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED,
                    "Please configure an enabled model authorization first.");
        }
        return authorization;
    }

    private AgentPolicy resolveFirstPolicy(SystemMemberContext context, String requestedCode) {
        LambdaQueryWrapper<AgentPolicy> wrapper = new LambdaQueryWrapper<AgentPolicy>()
                .eq(AgentPolicy::getSystemId, context.systemId())
                .eq(AgentPolicy::getTenantId, context.tenantId())
                .eq(AgentPolicy::getStatus, ENABLED);
        if (StringUtils.hasText(requestedCode)) {
            wrapper.eq(AgentPolicy::getPolicyCode, requestedCode);
        }
        AgentPolicy policy = policyBaseService.getOne(wrapper
                .orderByDesc(AgentPolicy::getCreatedAt)
                .orderByDesc(AgentPolicy::getId)
                .last("LIMIT 1"), false);
        if (Objects.isNull(policy)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED,
                    "Please configure an enabled system Agent policy first.");
        }
        return policy;
    }

    private AgentModelAuthorization findAuthorization(String authorizationId) {
        Long id = parseNullableId(authorizationId);
        LambdaQueryWrapper<AgentModelAuthorization> wrapper = new LambdaQueryWrapper<>();
        if (Objects.nonNull(id)) {
            wrapper.eq(AgentModelAuthorization::getId, id);
        } else {
            wrapper.eq(AgentModelAuthorization::getAuthorizationCode, authorizationId);
        }
        return authorizationBaseService.getOne(wrapper.last("LIMIT 1"), false);
    }

    private AgentPolicy findPolicy(Long systemId, String policyId) {
        Long id = parseNullableId(policyId);
        LambdaQueryWrapper<AgentPolicy> wrapper = new LambdaQueryWrapper<AgentPolicy>()
                .eq(AgentPolicy::getSystemId, systemId);
        if (Objects.nonNull(id)) {
            wrapper.eq(AgentPolicy::getId, id);
        } else {
            wrapper.eq(AgentPolicy::getPolicyCode, policyId);
        }
        return policyBaseService.getOne(wrapper.last("LIMIT 1"), false);
    }

    private AgentPolicy requirePolicy(Long systemId, String policyId) {
        AgentPolicy policy = findPolicy(systemId, policyId);
        if (Objects.isNull(policy)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "Agent policy does not exist.");
        }
        return policy;
    }

    private AgentSession requireSession(String sessionId, String scope) {
        AgentSession session = sessionBaseService.getOne(new LambdaQueryWrapper<AgentSession>()
                .eq(AgentSession::getSessionId, sessionId)
                .eq(AgentSession::getScope, scope)
                .last("LIMIT 1"), false);
        if (Objects.isNull(session)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "Agent session does not exist.");
        }
        return session;
    }

    private AgentConfirmation requireConfirmation(String confirmationId, String confirmType) {
        AgentConfirmation confirmation = confirmationBaseService.getOne(new LambdaQueryWrapper<AgentConfirmation>()
                .eq(AgentConfirmation::getConfirmationId, confirmationId)
                .eq(AgentConfirmation::getConfirmType, confirmType)
                .last("LIMIT 1"), false);
        if (Objects.isNull(confirmation)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED,
                    "Agent confirmation does not exist.");
        }
        return confirmation;
    }

    private AgentConfirmation upsertConfirmation(String scope, Long systemId, Long tenantId, String confirmationId,
                                                 String confirmType, String status, Map<String, Object> source,
                                                 Map<String, Object> diffPayload, String permissionSnapshotId,
                                                 Long confirmedBy, String auditLogId) {
        AgentConfirmation confirmation = confirmationBaseService.getOne(new LambdaQueryWrapper<AgentConfirmation>()
                .eq(AgentConfirmation::getConfirmationId, confirmationId)
                .last("LIMIT 1"), false);
        boolean created = Objects.isNull(confirmation);
        if (created) {
            confirmation = new AgentConfirmation();
            confirmation.setConfirmationId(confirmationId);
            confirmation.setConfirmType(confirmType);
            confirmation.setCreatedAt(LocalDateTime.now());
        }
        Map<String, Object> enrichedDiff = new LinkedHashMap<>(Objects.isNull(diffPayload) ? Map.of() : diffPayload);
        enrichedDiff.put("scope", scope);
        if (Objects.nonNull(systemId)) {
            enrichedDiff.put("systemId", systemId);
        }
        if (Objects.nonNull(tenantId)) {
            enrichedDiff.put("tenantId", tenantId);
        }
        confirmation.setStatus(status);
        confirmation.setSourceConversation(writeJson(Objects.isNull(source) ? Map.of() : source));
        confirmation.setDiffPayload(writeJson(enrichedDiff));
        confirmation.setPermissionSnapshotId(permissionSnapshotId);
        confirmation.setConfirmedBy(confirmedBy);
        confirmation.setConfirmedAt(Objects.nonNull(confirmedBy) ? LocalDateTime.now() : null);
        confirmation.setAuditLogId(auditLogId);
        confirmation.setTraceId(RequestContext.current().traceId());
        if (created) {
            confirmationBaseService.saveEntity(confirmation);
        } else {
            confirmationBaseService.updateById(confirmation);
        }
        return confirmation;
    }

    private AgentAuditLogVO saveAuditLog(String scope, Long systemId, Long tenantId, String sessionId,
                                         String confirmationId, String modelVersion, String promptVersion,
                                         String policyVersion, String permissionSnapshotId,
                                         Map<String, Object> conversationSnapshot, List<ToolCallVO> tools,
                                         Map<String, Object> desensitizeSnapshot,
                                         Map<String, Object> outboundSnapshot) {
        AgentAuditLog audit = new AgentAuditLog();
        audit.setSessionId(safeText(sessionId, "agent_session_event_" + shortCode(RequestContext.current().traceId())));
        audit.setConfirmationId(confirmationId);
        audit.setScope(scope);
        audit.setSystemId(systemId);
        audit.setTenantId(tenantId);
        audit.setModelVersion(safeText(modelVersion, "model_version_unresolved"));
        audit.setPromptVersion(safeText(promptVersion, DEFAULT_PROMPT_VERSION));
        audit.setPolicyVersion(policyVersion);
        audit.setPermissionSnapshotId(permissionSnapshotId);
        audit.setConversationSnapshot(writeJson(conversationSnapshot));
        audit.setToolCallSnapshot(writeJson(Map.of("tools", safeList(toToolMaps(tools), List.of()),
                "toolCallCount", Objects.isNull(tools) ? 0 : tools.size())));
        audit.setDesensitizeResult(writeJson(desensitizeSnapshot));
        audit.setOutboundSnapshot(writeJson(outboundSnapshot));
        audit.setTraceId(RequestContext.current().traceId());
        audit.setAuditLogId("audit_agent_" + shortCode(RequestContext.current().traceId() + System.nanoTime()));
        audit.setCreatedAt(LocalDateTime.now());
        auditLogBaseService.saveEntity(audit);
        return toAuditLogVO(audit);
    }

    private SysSecretRef saveSecretRef(String secretRefId, String refType, String displayName, LocalDateTime now) {
        SysSecretRef secretRef = secretRefBaseService.getOne(new LambdaQueryWrapper<SysSecretRef>()
                .eq(SysSecretRef::getSecretRefId, secretRefId)
                .last("LIMIT 1"), false);
        boolean created = Objects.isNull(secretRef);
        if (created) {
            secretRef = new SysSecretRef();
            secretRef.setSecretRefId(secretRefId);
            secretRef.setRefType(refType);
            secretRef.setCreatedAt(now);
        }
        secretRef.setDisplayName(safeText(displayName, refType + " credential"));
        secretRef.setVersionNo("v1");
        secretRef.setExpiresAt(now.plusDays(90));
        secretRef.setRotationStatus("ACTIVE");
        secretRef.setStorageRef("secret://" + refType.toLowerCase() + "/" + secretRefId + "/v1");
        secretRef.setUpdatedAt(now);
        if (created) {
            secretRefBaseService.saveEntity(secretRef);
        } else {
            secretRefBaseService.updateById(secretRef);
        }
        return secretRef;
    }

    private ModelAuthorizationVO toModelAuthorizationVO(AgentModelAuthorization entity, OperationResultVO operation) {
        SecretRefVO secretRef = secretRef(entity.getModelCredentialRefId());
        return new ModelAuthorizationVO(String.valueOf(entity.getId()), entity.getAuthorizationCode(),
                entity.getModelProvider(), entity.getModelName(), secretRef,
                readObject(entity.getQuotaConfig(), QuotaConfigVO.class, defaultQuota()),
                readObject(entity.getDataOutboundPolicy(), DataOutboundPolicyVO.class, defaultOutboundPolicy()),
                entity.getStatus(), entity.getVersionNo(), operation, entity.getCreatedAt());
    }

    private AgentPolicyVO toPolicyVO(AgentPolicy entity, OperationResultVO operation) {
        return new AgentPolicyVO(String.valueOf(entity.getId()), String.valueOf(entity.getSystemId()),
                String.valueOf(entity.getTenantId()), entity.getPolicyCode(), toPolicyScope(entity),
                entity.getStatus(), new PublishStatusVO(Objects.equals(entity.getStatus(), ENABLED)
                ? "PUBLISHED" : "DISABLED", entity.getPolicyVersion(), Objects.equals(entity.getStatus(), ENABLED),
                entity.getCreatedAt()), operation, entity.getCreatedAt());
    }

    private AgentPolicyScopeVO toPolicyScope(AgentPolicy entity) {
        return new AgentPolicyScopeVO(readStringList(entity.getModuleScope()),
                readMapStringList(entity.getFieldScope()), readStringList(entity.getActionScope()),
                readDataScopeExpression(entity.getDataScopeExpression()),
                readObject(entity.getOutboundLimit(), OutboundLimitVO.class, defaultOutboundLimit()),
                readObject(entity.getDesensitizePolicy(), DesensitizePolicyVO.class, defaultDesensitizePolicy()),
                entity.getPolicyVersion());
    }

    private AgentSessionVO toSessionVO(AgentSession session, String policyVersion) {
        return new AgentSessionVO(session.getSessionId(), session.getScope(),
                Objects.isNull(session.getSystemId()) ? null : String.valueOf(session.getSystemId()),
                Objects.isNull(session.getTenantId()) ? null : String.valueOf(session.getTenantId()),
                Objects.isNull(session.getSystemMemberId()) ? null : String.valueOf(session.getSystemMemberId()),
                session.getModelVersion(), session.getPromptVersion(), policyVersion,
                session.getPermissionSnapshotId(), session.getStatus(), boundary(session.getScope()),
                session.getCreatedAt());
    }

    private AgentAuditLogVO toAuditLogVO(AgentAuditLog audit) {
        return new AgentAuditLogVO(String.valueOf(audit.getId()), audit.getSessionId(), audit.getConfirmationId(),
                audit.getScope(), Objects.isNull(audit.getSystemId()) ? null : String.valueOf(audit.getSystemId()),
                Objects.isNull(audit.getTenantId()) ? null : String.valueOf(audit.getTenantId()),
                audit.getModelVersion(), audit.getPromptVersion(), audit.getPolicyVersion(),
                audit.getPermissionSnapshotId(), readMap(audit.getConversationSnapshot()),
                readMap(audit.getToolCallSnapshot()), readMap(audit.getDesensitizeResult()),
                readMap(audit.getOutboundSnapshot()), audit.getTraceId(), audit.getAuditLogId(),
                audit.getCreatedAt());
    }

    private AgentModels.AgentConfirmationVO toConfirmationVO(AgentConfirmation confirmation, AgentAuditLogVO audit,
                                                             OperationResultVO operation) {
        return new AgentModels.AgentConfirmationVO(confirmation.getConfirmationId(),
                confirmation.getConfirmType(), confirmation.getStatus(),
                textFromJson(confirmation.getSourceConversation(), "sourceConversation",
                        textFromJson(confirmation.getSourceConversation(), "message", "")),
                confirmation.getPermissionSnapshotId(), Objects.isNull(confirmation.getConfirmedBy())
                ? null : String.valueOf(confirmation.getConfirmedBy()), confirmation.getConfirmedAt(),
                audit, operation);
    }

    private SystemWriteConfirmationVO toSystemWriteConfirmationVO(AgentConfirmation confirmation,
                                                                  AgentAuditLogVO audit,
                                                                  OperationResultVO operation) {
        Map<String, Object> payload = readMap(confirmation.getDiffPayload());
        List<FieldDiffVO> diffs = readFieldDiffs(payload.get("fieldDiffs"));
        return new SystemWriteConfirmationVO(toConfirmationVO(confirmation, audit, operation), diffs,
                permissionClips(diffs), Boolean.TRUE.equals(payload.get("approvalRequired")),
                safeText(Objects.toString(payload.get("compensationPlan"), null),
                        "Rollback the current Agent write and keep business audit evidence."),
                "biz_log_" + confirmation.getConfirmationId());
    }

    private SecretRefVO secretRef(String secretRefId) {
        SysSecretRef secretRef = secretRefBaseService.getOne(new LambdaQueryWrapper<SysSecretRef>()
                .eq(SysSecretRef::getSecretRefId, secretRefId)
                .last("LIMIT 1"), false);
        if (Objects.isNull(secretRef)) {
            return new SecretRefVO(secretRefId, "MODEL", "v1", LocalDateTime.now().plusDays(90),
                    "MISSING", null, "Model credential");
        }
        return new SecretRefVO(secretRef.getSecretRefId(), secretRef.getRefType(), secretRef.getVersionNo(),
                secretRef.getExpiresAt(), secretRef.getRotationStatus(), secretRef.getLastUsedAt(),
                secretRef.getDisplayName());
    }

    private AgentBoundaryVO boundary(String scope) {
        if (PLATFORM_SCOPE.equals(scope)) {
            return new AgentBoundaryVO(List.of("PLATFORM_AUTHORIZATION", "PLATFORM_TASK", "PLATFORM_LOG",
                    "MODEL_QUOTA", "SYSTEM_HEALTH", "SYSTEM_SWITCH_GUIDE"),
                    List.of("SYSTEM_BUSINESS_RECORD", "SYSTEM_MODULE_WRITE", "SYSTEM_APPROVAL_WRITE"),
                    "Platform Agent cannot open or write system business data.");
        }
        return new AgentBoundaryVO(List.of("SYSTEM_RECORD_QUERY", "SYSTEM_WRITE_CONFIRM",
                "WORK_DRAFT_CONFIRM", "STATISTICS_QUERY"),
                List.of("PLATFORM_ADMIN_WRITE", "OTHER_SYSTEM_DATA"),
                "System Agent must run inside SystemSwitchContext and effective permission snapshot.");
    }

    private List<ToolCallVO> platformTools(AgentMessageRequest request) {
        List<String> hints = Objects.isNull(request) ? List.of() : safeList(request.toolHints(), List.of());
        List<ToolCallVO> tools = new ArrayList<>();
        tools.add(tool("platform_model_quota_check", "SUCCESS", Map.of("scope", PLATFORM_SCOPE),
                Map.of("remainingTokens", 120000)));
        tools.add(tool("platform_agent_boundary_check", "SUCCESS", Map.of("toolHints", hints),
                Map.of("allowSystemBusinessData", false)));
        return tools;
    }

    private List<ToolCallVO> systemTools(SystemMemberContext context, AgentMessageRequest request) {
        List<String> hints = Objects.isNull(request) ? List.of() : safeList(request.toolHints(), List.of());
        return List.of(tool("effective_permission_snapshot", "SUCCESS", Map.of("systemId", context.systemId(),
                        "tenantId", context.tenantId(), "systemMemberId", context.systemMemberId()),
                        Map.of("permissionSnapshotId", permissionSnapshotId(context))),
                tool("agent_tool_boundary_check", "SUCCESS", Map.of("toolHints", hints),
                        Map.of("allowPlatformWrite", false, "maxRows", 200)),
                tool("desensitize_preview", "SUCCESS", Map.of("fields", List.of("mobile", "idNo", "secret")),
                        Map.of("masked", true)));
    }

    private ToolCallVO tool(String toolName, String status, Map<String, Object> input, Map<String, Object> output) {
        return new ToolCallVO(toolName, status, input, output);
    }

    private List<Map<String, Object>> toToolMaps(List<ToolCallVO> tools) {
        if (Objects.isNull(tools) || tools.isEmpty()) {
            return List.of();
        }
        return tools.stream()
                .map(tool -> Map.<String, Object>of("toolName", tool.toolName(), "status", tool.status(),
                        "inputSnapshot", tool.inputSnapshot(), "outputSnapshot", tool.outputSnapshot()))
                .toList();
    }

    private List<FieldDiffVO> inferFieldDiffs(String message) {
        String text = safeText(message, "");
        if (text.contains("status") || text.contains("状态")) {
            return List.of(new FieldDiffVO("status", "Status", "DRAFT", "IN_PROGRESS", true, null));
        }
        if (text.contains("日报") || text.contains("report")) {
            return List.of(new FieldDiffVO("dailyReportSummary", "Daily report summary", "", text, true, null));
        }
        return List.of(new FieldDiffVO("agentNote", "Agent note", "", safeText(text, "Generated by Agent"),
                true, null));
    }

    private List<PermissionClipVO> permissionClips(List<FieldDiffVO> diffs) {
        return safeList(diffs, List.<FieldDiffVO>of()).stream()
                .filter(diff -> !diff.writable())
                .map(diff -> new PermissionClipVO(diff.fieldCode(), "WRITE_DENIED", diff.disabledReason()))
                .toList();
    }

    private List<WorkDraftSourceVO> defaultWorkSources() {
        return List.of(new WorkDraftSourceVO("TODO", "current_member_todo", "Current member pending todo",
                        "CURRENT_MEMBER_AUTHORIZED_ONLY", true),
                new WorkDraftSourceVO("BUSINESS_LOG", "current_member_business_log",
                        "Current member business activity", "CURRENT_MEMBER_AUTHORIZED_ONLY", true));
    }

    private Map<String, Object> defaultDraftPayload() {
        return Map.of("title", "Today's work draft", "content",
                "Generated from authorized tasks, todos, messages, business logs and approval records.");
    }

    private AgentPolicyScopeVO defaultPolicyScope() {
        return new AgentPolicyScopeVO(List.of("runtime_records", "work_management"),
                Map.of("readable", List.of("title", "status", "owner", "updatedAt"),
                        "writable", List.of("status", "description", "dailyReportSummary")),
                List.of("query", "statistics", "requestWriteConfirmation", "createWorkDraft"),
                "currentMemberAuthorizedData", defaultOutboundLimit(), defaultDesensitizePolicy(),
                DEFAULT_POLICY_VERSION);
    }

    private OutboundLimitVO defaultOutboundLimit() {
        return new OutboundLimitVO(200, false, false);
    }

    private DesensitizePolicyVO defaultDesensitizePolicy() {
        return new DesensitizePolicyVO("MASK_SENSITIVE_FIELDS", List.of("mobile", "idNo", "secret"),
                Map.of("mobile", "138****0001"));
    }

    private QuotaConfigVO defaultQuota() {
        return new QuotaConfigVO(1_000_000, 20_000, 500, "DAILY");
    }

    private DataOutboundPolicyVO defaultOutboundPolicy() {
        return new DataOutboundPolicyVO(false, List.of("CN"), List.of("maskedBusinessData", "statistics"), 180);
    }

    private Map<String, Object> defaultDesensitizeSnapshot() {
        return Map.of("mode", "MASK_SENSITIVE_FIELDS", "maskedFields", List.of("mobile", "idNo", "secret"));
    }

    private Map<String, Object> defaultOutboundSnapshot() {
        return Map.of("allowExternalModel", false, "maxRows", 200,
                "outboundFields", List.of("summary", "statistics", "maskedBusinessData"));
    }

    private List<ImpactRefVO> impactRefs(AgentPolicy policy, AgentPolicyScopeVO scope) {
        List<ImpactRefVO> refs = new ArrayList<>();
        refs.add(new ImpactRefVO("AGENT_POLICY", String.valueOf(policy.getId()), policy.getPolicyCode()));
        for (String moduleCode : safeList(scope.moduleScope(), List.of())) {
            refs.add(new ImpactRefVO("MODULE_SCOPE", moduleCode, moduleCode));
        }
        return refs;
    }

    private List<GeneratedObjectVO> generatedPlatformObjects(PlatformAgentConfirmRequest request) {
        List<PlatformAgentActionDraftVO> actions = safeList(Objects.isNull(request) ? null : request.platformActions(),
                List.of(new PlatformAgentActionDraftVO("PLATFORM_TASK", "Agent generated platform task",
                        Map.of("priority", "MEDIUM"))));
        return actions.stream()
                .filter(action -> List.of("PLATFORM_TASK", "PLATFORM_MESSAGE", "PLATFORM_LOG")
                        .contains(safeText(action.actionType(), "PLATFORM_TASK")))
                .map(action -> new GeneratedObjectVO(action.actionType(),
                        "generated_" + shortCode(action.title() + RequestContext.current().traceId()),
                        safeText(action.title(), "Agent generated platform object"), "CREATED"))
                .toList();
    }

    private ConfirmationSummaryVO toConfirmationSummary(AgentConfirmation confirmation, boolean manualRequired) {
        return new ConfirmationSummaryVO(confirmation.getConfirmationId(), confirmation.getConfirmType(),
                confirmation.getStatus(), manualRequired);
    }

    private void touchSession(AgentSession session) {
        session.setUpdatedAt(LocalDateTime.now());
        sessionBaseService.updateById(session);
    }

    private void verifySessionSystem(AgentSession session, Long systemId) {
        if (!Objects.equals(session.getSystemId(), systemId)) {
            throw new BusinessException(CommonErrorCode.PERMISSION_DENIED,
                    "Agent session does not belong to the current system.");
        }
    }

    private void ensureConfirmationSystem(AgentConfirmation confirmation, Long systemId) {
        Map<String, Object> payload = readMap(confirmation.getDiffPayload());
        Long payloadSystemId = parseNullableId(Objects.toString(payload.get("systemId"), null));
        if (Objects.nonNull(payloadSystemId) && !Objects.equals(payloadSystemId, systemId)) {
            throw new BusinessException(CommonErrorCode.PERMISSION_DENIED,
                    "Agent confirmation does not belong to the current system.");
        }
    }

    private boolean matchesAuditQuery(AgentAuditLog log, AgentAuditLogQuery query) {
        if (Objects.isNull(query)) {
            return true;
        }
        if (StringUtils.hasText(query.scope()) && !query.scope().equalsIgnoreCase(log.getScope())) {
            return false;
        }
        if (StringUtils.hasText(query.sessionId()) && !query.sessionId().equals(log.getSessionId())) {
            return false;
        }
        if (StringUtils.hasText(query.confirmationId())
                && !query.confirmationId().equals(log.getConfirmationId())) {
            return false;
        }
        if (StringUtils.hasText(query.modelVersion()) && !query.modelVersion().equals(log.getModelVersion())) {
            return false;
        }
        if (StringUtils.hasText(query.policyVersion()) && !query.policyVersion().equals(log.getPolicyVersion())) {
            return false;
        }
        if (StringUtils.hasText(query.permissionSnapshotId())
                && !query.permissionSnapshotId().equals(log.getPermissionSnapshotId())) {
            return false;
        }
        if (StringUtils.hasText(query.traceId()) && !query.traceId().equals(log.getTraceId())) {
            return false;
        }
        return !StringUtils.hasText(query.keyword())
                || safeText(log.getConversationSnapshot(), "").contains(query.keyword())
                || safeText(log.getToolCallSnapshot(), "").contains(query.keyword());
    }

    private String resolveModelSecretRefId(AgentModelAuthorization entity, ModelAuthorizationSaveRequest request,
                                           String code) {
        if (Objects.nonNull(request) && Objects.nonNull(request.modelCredentialRef())
                && StringUtils.hasText(request.modelCredentialRef().secretRefId())) {
            return request.modelCredentialRef().secretRefId();
        }
        if (StringUtils.hasText(entity.getModelCredentialRefId())) {
            return entity.getModelCredentialRefId();
        }
        return "sec_model_" + code + "_" + shortCode(RequestContext.current().traceId());
    }

    private String modelVersion(AgentModelAuthorization authorization) {
        if (Objects.isNull(authorization)) {
            return "model_version_unresolved";
        }
        return authorization.getModelProvider() + ":" + authorization.getModelName() + "@"
                + authorization.getVersionNo();
    }

    private String policyVersionForContext(SystemMemberContext context) {
        AgentPolicy policy = resolveFirstPolicy(context, null);
        return policy.getPolicyVersion();
    }

    private String permissionSnapshotId(SystemMemberContext context) {
        return "eps_" + context.systemId() + "_" + context.tenantId() + "_" + context.systemMemberId();
    }

    private String platformConfirmationId(String sessionId) {
        return "confirm_platform_" + shortCode(safeText(sessionId, RequestContext.current().traceId()));
    }

    private String systemWriteConfirmationId(String sessionId) {
        return "confirm_write_" + shortCode(safeText(sessionId, RequestContext.current().traceId()));
    }

    private String workConfirmationId(String sessionId) {
        return "confirm_work_" + shortCode(safeText(sessionId, RequestContext.current().traceId()));
    }

    private <T> PageResult<T> page(List<T> records, PageRequest pageRequest) {
        int pageNo = Objects.isNull(pageRequest) || pageRequest.pageNo() <= 0 ? 1 : pageRequest.pageNo();
        int pageSize = Objects.isNull(pageRequest) || pageRequest.pageSize() <= 0 ? 20 : pageRequest.pageSize();
        int from = Math.min((pageNo - 1) * pageSize, records.size());
        int to = Math.min(from + pageSize, records.size());
        return new PageResult<>(records.subList(from, to), pageNo, pageSize, records.size(), to < records.size());
    }

    private OperationResultVO operation(String operation, String idempotencyKey, String result,
                                        String disabledReason, String auditLogId) {
        RequestContext context = RequestContext.current();
        return new OperationResultVO(operation, safeText(idempotencyKey, operation + "_" + shortCode(context.traceId())),
                result, context.traceId(), safeText(auditLogId, context.auditLogId()), disabledReason,
                LocalDateTime.now());
    }

    private String resolveIdempotencyKey(String headerIdempotencyKey, String bodyIdempotencyKey, String operation) {
        if (StringUtils.hasText(headerIdempotencyKey)) {
            return headerIdempotencyKey;
        }
        if (StringUtils.hasText(bodyIdempotencyKey)) {
            return bodyIdempotencyKey;
        }
        return operation + "_" + shortCode(RequestContext.current().traceId());
    }

    private String normalizeCode(String value, String fallback) {
        String text = safeText(value, fallback).trim().toLowerCase();
        return text.replaceAll("[^a-z0-9_\\-]", "_");
    }

    private Long parseRequiredId(String value, String message) {
        Long id = parseNullableId(value);
        if (Objects.isNull(id)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, message);
        }
        return id;
    }

    private Long parseNullableId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private List<String> readStringList(String value) {
        return readObject(value, new TypeReference<List<String>>() {
        }, List.of());
    }

    private Map<String, List<String>> readMapStringList(String value) {
        return readObject(value, new TypeReference<Map<String, List<String>>>() {
        }, Map.of());
    }

    private Map<String, Object> readMap(String value) {
        return readObject(value, new TypeReference<Map<String, Object>>() {
        }, Map.of());
    }

    private List<FieldDiffVO> readFieldDiffs(Object value) {
        if (Objects.isNull(value)) {
            return List.of();
        }
        return objectMapper.convertValue(value, new TypeReference<List<FieldDiffVO>>() {
        });
    }

    private String readDataScopeExpression(String value) {
        if (!StringUtils.hasText(value)) {
            return "currentMemberAuthorizedData";
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            if (node.isTextual()) {
                return node.asText();
            }
            JsonNode expression = node.get("expression");
            return Objects.isNull(expression) ? "currentMemberAuthorizedData" : expression.asText();
        } catch (Exception ex) {
            return "currentMemberAuthorizedData";
        }
    }

    private String textFromJson(String json, String field, String fallback) {
        if (!StringUtils.hasText(json)) {
            return fallback;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode value = node.get(field);
            return Objects.isNull(value) ? fallback : value.asText(fallback);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private <T> T readObject(String value, Class<T> type, T fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private <T> T readObject(String value, TypeReference<T> type, T fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(Objects.isNull(value) ? Map.of() : value);
        } catch (Exception ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "Failed to serialize Agent payload.");
        }
    }

    private <T> List<T> safeList(List<T> value, List<T> fallback) {
        return Objects.isNull(value) || value.isEmpty() ? fallback : value;
    }

    private String shortCode(String value) {
        String text = safeText(value, "agent").replaceAll("[^A-Za-z0-9]", "");
        if (!StringUtils.hasText(text)) {
            text = Integer.toHexString(safeText(value, "agent").hashCode()).replace("-", "");
        }
        if (text.length() <= 12) {
            return text.toLowerCase();
        }
        return (text.substring(0, 6) + text.substring(text.length() - 6)).toLowerCase();
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
