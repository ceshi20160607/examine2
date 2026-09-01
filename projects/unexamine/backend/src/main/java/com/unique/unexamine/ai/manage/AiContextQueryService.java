package com.unique.unexamine.ai.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.ai.base.entity.AiAgent;
import com.unique.unexamine.ai.base.entity.AiAgentPublication;
import com.unique.unexamine.ai.base.entity.AiAgentVersion;
import com.unique.unexamine.ai.base.entity.AiConversation;
import com.unique.unexamine.ai.base.entity.AiExecution;
import com.unique.unexamine.ai.base.entity.AiExecutionStep;
import com.unique.unexamine.ai.base.entity.AiMessage;
import com.unique.unexamine.ai.base.entity.AiModel;
import com.unique.unexamine.ai.base.entity.AiSystemModelGrant;
import com.unique.unexamine.ai.base.service.AiAgentBaseService;
import com.unique.unexamine.ai.base.service.AiAgentPublicationBaseService;
import com.unique.unexamine.ai.base.service.AiAgentVersionBaseService;
import com.unique.unexamine.ai.base.service.AiConversationBaseService;
import com.unique.unexamine.ai.base.service.AiExecutionBaseService;
import com.unique.unexamine.ai.base.service.AiExecutionStepBaseService;
import com.unique.unexamine.ai.base.service.AiMessageBaseService;
import com.unique.unexamine.ai.base.service.AiModelBaseService;
import com.unique.unexamine.ai.base.service.AiSystemModelGrantBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.ChannelFieldPolicyResolver;
import com.unique.unexamine.authorization.manage.DataScopeExpression;
import com.unique.unexamine.authorization.manage.FieldAccessDecision;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.runtimedata.manage.RuntimeDataService;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordList;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordView;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class AiContextQueryService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final Set<String> BUILT_IN_FIELDS = Set.of(
            "title", "recordNumber", "status", "ownerMemberId", "departmentId", "createdAt", "updatedAt",
            "dataTenantName");

    private final AiAgentBaseService agentService;
    private final AiAgentPublicationBaseService publicationService;
    private final AiAgentVersionBaseService versionService;
    private final AiConversationBaseService conversationService;
    private final AiMessageBaseService messageService;
    private final AiExecutionBaseService executionService;
    private final AiExecutionStepBaseService stepService;
    private final AiSystemModelGrantBaseService grantService;
    private final AiModelBaseService modelService;
    private final RuntimeDataService runtimeDataService;
    private final PermissionChecker permissionChecker;
    private final ChannelFieldPolicyResolver fieldPolicyResolver;
    private final PlatformAiConfigurationService platformConfigurationService;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public AiContextQueryService(
            AiAgentBaseService agentService,
            AiAgentPublicationBaseService publicationService,
            AiAgentVersionBaseService versionService,
            AiConversationBaseService conversationService,
            AiMessageBaseService messageService,
            AiExecutionBaseService executionService,
            AiExecutionStepBaseService stepService,
            AiSystemModelGrantBaseService grantService,
            AiModelBaseService modelService,
            RuntimeDataService runtimeDataService,
            PermissionChecker permissionChecker,
            ChannelFieldPolicyResolver fieldPolicyResolver,
            PlatformAiConfigurationService platformConfigurationService,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.agentService = agentService;
        this.publicationService = publicationService;
        this.versionService = versionService;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.executionService = executionService;
        this.stepService = stepService;
        this.grantService = grantService;
        this.modelService = modelService;
        this.runtimeDataService = runtimeDataService;
        this.permissionChecker = permissionChecker;
        this.fieldPolicyResolver = fieldPolicyResolver;
        this.platformConfigurationService = platformConfigurationService;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AiContextQueryModels.Overview overview(AuthenticatedContext context) {
        requireSystem(context);
        List<PublishedAgent> publishedAgents = publishedAgents(context);
        Map<Long, String> names = new LinkedHashMap<>();
        List<AiContextQueryModels.AgentOption> agents = publishedAgents.stream().map(item -> {
            names.put(item.agent().getId(), item.agent().getName());
            return new AiContextQueryModels.AgentOption(item.agent().getId(), item.agent().getCode(),
                    item.agent().getName(), item.version().getId(), item.version().getVersionNumber(),
                    queryTools(item.snapshot()).stream().map(ToolScope::moduleCode).distinct().sorted().toList());
        }).toList();
        List<AiContextQueryModels.ConversationSummary> conversations = conversationService.selectList(
                        Wrappers.<AiConversation>lambdaQuery()
                                .eq(AiConversation::getSystemId, context.systemId())
                                .eq(AiConversation::getTenantId, context.tenantId())
                                .eq(AiConversation::getAccountId, context.accountId())
                                .orderByDesc(AiConversation::getUpdatedAt).last("limit 25"))
                .stream().map(item -> summary(item, names.get(item.getAgentId()))).toList();
        return new AiContextQueryModels.Overview(agents, conversations);
    }

    @Transactional
    public AiContextQueryModels.QueryResult query(
            AuthenticatedContext context, AiContextQueryModels.QueryRequest input, String traceId) {
        requireSystem(context);
        PublishedAgent published = requirePublishedAgent(context, input.agentId());
        String moduleCode = input.entryContext().moduleCode().strip().toLowerCase(Locale.ROOT);
        String actionCode = input.entryContext().recordId() == null ? "LIST" : "DETAIL";
        ToolScope tool = queryTools(published.snapshot()).stream()
                .filter(item -> moduleCode.equals(item.moduleCode()) && actionCode.equals(item.actionCode()))
                .findFirst().orElse(null);
        String entryType = input.entryContext().entryType().strip().toUpperCase(Locale.ROOT);
        List<String> requestedFields = normalizeFields(input.requestedFieldCodes(), tool);
        AiConversation conversation = conversation(context, input, published, moduleCode, entryType);
        AiMessage requestMessage = message(conversation.getId(), "USER", input.question().strip(),
                Map.of("entryContext", entryContext(moduleCode, entryType, input.entryContext()),
                        "requestedFieldCodes", requestedFields), null, null);
        AiExecution execution = execution(conversation, requestMessage, published.version());

        String refusal = refusalReason(context, input, published.snapshot(), tool, moduleCode, entryType,
                actionCode, requestedFields);
        if (refusal != null) {
            return finish(context, traceId, conversation, execution, tool, input, requestedFields,
                    "REFUSED", refusal, "AI_QUERY_SCOPE_DENIED", false, null, List.of(), null);
        }

        ModelAvailability availability = modelAvailability(context, published.snapshot());
        if (!availability.available()) {
            String answer = "模型当前不可用：" + availability.reason()
                    + "。本次未生成或猜测任何业务结果。请检查模型授权、凭证和端点后重试。";
            return finish(context, traceId, conversation, execution, tool, input, requestedFields,
                    "DEGRADED", answer, "AI_MODEL_UNAVAILABLE", true, null, List.of(), availability);
        }

        QueryData data = executeAuthorizedQuery(context, input, moduleCode, actionCode, requestedFields, traceId);
        String metricDefinition = metricDefinition(context, input, moduleCode, actionCode);
        String answer = answer(input.question(), moduleCode, data);
        return finish(context, traceId, conversation, execution, tool, input, requestedFields,
                "SUCCEEDED", answer, null, false, metricDefinition, data.sources(), availability);
    }

    @Transactional(readOnly = true)
    public AiContextQueryModels.ConversationDetail conversation(AuthenticatedContext context, Long conversationId) {
        requireSystem(context);
        AiConversation conversation = requireConversation(context, conversationId);
        AiAgent agent = agentService.selectById(conversation.getAgentId());
        List<AiContextQueryModels.MessageView> messages = messageService.selectList(
                        Wrappers.<AiMessage>lambdaQuery().eq(AiMessage::getConversationId, conversationId)
                                .orderByAsc(AiMessage::getCreatedAt).orderByAsc(AiMessage::getId))
                .stream().map(item -> new AiContextQueryModels.MessageView(item.getId(), item.getRoleType(),
                        item.getContentText(), readMap(item.getStructuredContentJson()), readMap(item.getModelUsageJson()),
                        item.getErrorCode(), item.getCreatedAt())).toList();
        return new AiContextQueryModels.ConversationDetail(
                summary(conversation, agent == null ? null : agent.getName()), messages);
    }

    private AiContextQueryModels.QueryResult finish(
            AuthenticatedContext context,
            String traceId,
            AiConversation conversation,
            AiExecution execution,
            ToolScope tool,
            AiContextQueryModels.QueryRequest input,
            List<String> requestedFields,
            String outcome,
            String answer,
            String errorCode,
            boolean retryable,
            String metricDefinition,
            List<AiContextQueryModels.Source> sources,
            ModelAvailability availability) {
        LocalDateTime finishedAt = LocalDateTime.now();
        AiContextQueryModels.QueryScope scope = scope(context, input, requestedFields);
        AiContextQueryModels.QueryResult result = new AiContextQueryModels.QueryResult(
                conversation.getId(), execution.getId(), outcome, answer, errorCode, retryable, scope,
                metricDefinition, sources, finishedAt);
        step(execution.getId(), tool, input, scope, result, outcome, answer);
        execution.setStatus("SUCCEEDED".equals(outcome) ? "SUCCEEDED" : "REFUSED".equals(outcome) ? "REFUSED" : "FAILED");
        execution.setFallbackUsed("DEGRADED".equals(outcome));
        execution.setFinishedAt(finishedAt);
        execution.setErrorCode(errorCode);
        execution.setErrorMessage(errorCode == null ? null : answer);
        execution.setAuthorizationSnapshotJson(writeJson(authorizationSnapshot(context, tool, scope)));
        executionService.updateById(execution);
        Map<String, Object> modelUsage = new LinkedHashMap<>();
        modelUsage.put("executionMode", availability == null ? "NONE" : availability.mode());
        modelUsage.put("fabricated", false);
        modelUsage.put("sourceCount", sources.size());
        message(conversation.getId(), "ASSISTANT", answer, result, modelUsage, errorCode);
        conversation.setStatus("ACTIVE");
        conversationService.updateById(conversation);
        Map<String, Object> authorization = authorizationSnapshot(context, tool, scope);
        auditRecorder.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "AI_CONTEXT_QUERY_" + outcome, "AI_EXECUTION", String.valueOf(execution.getId()),
                outcome, authorization, Map.of(
                        "conversationId", conversation.getId(), "agentVersionId", execution.getAgentVersionId(),
                        "moduleCode", scope.moduleCode(), "actionCode", scope.actionCode(),
                        "sourceCount", sources.size(), "retryable", retryable));
        return result;
    }

    private QueryData executeAuthorizedQuery(
            AuthenticatedContext context,
            AiContextQueryModels.QueryRequest input,
            String moduleCode,
            String actionCode,
            List<String> fields,
            String traceId) {
        if ("DETAIL".equals(actionCode)) {
            RuntimeRecordView record = runtimeDataService.detail(context, moduleCode,
                    input.entryContext().recordId(), traceId);
            return new QueryData(1, List.of(source(context, moduleCode, record, fields)));
        }
        String filtersJson = writeJson(input.entryContext().filters());
        RuntimeRecordList result = runtimeDataService.list(context, moduleCode,
                defaultValue(input.entryContext().lifecycleState(), "ACTIVE"),
                defaultValue(input.entryContext().tenantScope(), "ALL"),
                defaultValue(input.entryContext().search(), ""), filtersJson,
                defaultValue(input.entryContext().sortField(), "updatedAt"),
                defaultValue(input.entryContext().sortDirection(), "DESC"), 1,
                input.entryContext().pageSize() == null ? 5 : Math.min(input.entryContext().pageSize(), 20), traceId);
        return new QueryData(result.total(), result.records().stream()
                .map(record -> source(context, moduleCode, record, fields)).toList());
    }

    private AiContextQueryModels.Source source(
            AuthenticatedContext context, String moduleCode, RuntimeRecordView record, List<String> fields) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (String field : fields) {
            JsonNode value = record.fields().get(field);
            if (value != null) values.put(field, objectMapper.convertValue(value, Object.class));
        }
        String path = "/systems/" + context.systemId() + "?workspace=runtime&module=" + moduleCode
                + "&recordId=" + record.id();
        return new AiContextQueryModels.Source(record.id(), record.recordNumber(), record.title(), path, values);
    }

    private String refusalReason(
            AuthenticatedContext context,
            AiContextQueryModels.QueryRequest input,
            JsonNode snapshot,
            ToolScope tool,
            String moduleCode,
            String entryType,
            String actionCode,
            List<String> requestedFields) {
        if (!entryAllowed(snapshot, entryType)) {
            return "已拒绝：当前 Agent 未获权从这个入口读取上下文；未读取任何业务数据。";
        }
        if (input.requestedTenantId() != null && !context.tenantId().equals(input.requestedTenantId())) {
            return "已拒绝：请求指向当前租户之外的数据；不会确认该数据是否存在，也不会返回敏感值。";
        }
        if (tool == null) {
            return "已拒绝：当前 Agent 的已发布版本没有该模块和动作的查询工具；未读取任何业务数据。";
        }
        if (!permissionChecker.allows(context, "MODULE", moduleCode, actionCode)) {
            return "已拒绝：当前用户没有该模块动作权限；未读取任何业务数据。";
        }
        Set<String> toolFields = new LinkedHashSet<>(tool.fieldCodes());
        List<String> excessive = requestedFields.stream().filter(field -> !toolFields.contains(field)).toList();
        if (!excessive.isEmpty()) {
            return "已拒绝：问题包含 Agent 未获权字段；不会提示字段值或推断敏感信息。";
        }
        Map<String, FieldAccessDecision> decisions = fieldPolicyResolver.resolveIntersection(
                context, moduleCode, actionCode, List.of("PAGE"), requestedFields);
        if (requestedFields.stream().anyMatch(field -> {
            FieldAccessDecision decision = decisions.get(field);
            return decision == null || !decision.readable();
        })) {
            return "已拒绝：问题包含当前用户不可读字段；不会提示字段值或推断敏感信息。";
        }
        Set<String> allowedFilterFields = new LinkedHashSet<>(requestedFields);
        allowedFilterFields.addAll(BUILT_IN_FIELDS);
        if (input.entryContext().filters().stream().anyMatch(filter -> !allowedFilterFields.contains(filter.fieldCode()))) {
            return "已拒绝：筛选条件引用了不可读或 Agent 未获权字段；未执行查询。";
        }
        String sortField = defaultValue(input.entryContext().sortField(), "updatedAt");
        if (!allowedFilterFields.contains(sortField)) {
            return "已拒绝：排序条件引用了不可读或 Agent 未获权字段；未执行查询。";
        }
        return null;
    }

    private ModelAvailability modelAvailability(AuthenticatedContext context, JsonNode snapshot) {
        long grantId = snapshot.path("modelBinding").path("modelGrantId").asLong(0);
        AiSystemModelGrant grant = grantId == 0 ? null : grantService.selectById(grantId);
        AiModel model = grant == null ? null : modelService.selectById(grant.getModelId());
        if (grant == null || !context.systemId().equals(grant.getSystemId()) || !"ACTIVE".equals(grant.getStatus())) {
            return new ModelAvailability(false, "模型授权已撤销或不属于当前系统", "UNAVAILABLE");
        }
        if (model == null || !context.platformId().equals(model.getPlatformId()) || !"ACTIVE".equals(model.getStatus())) {
            return new ModelAvailability(false, "模型已停用或不存在", "UNAVAILABLE");
        }
        if (!platformConfigurationService.credentialAvailable(model.getCredentialRef())) {
            return new ModelAvailability(false, "模型凭证引用不可解析", "UNAVAILABLE");
        }
        String endpoint = model.getEndpointUrl();
        if (endpoint == null || endpoint.isBlank()) {
            return "LOCAL".equalsIgnoreCase(model.getProvider())
                    ? new ModelAvailability(true, null, "LOCAL_SECURE_QUERY")
                    : new ModelAvailability(false, "模型端点未配置", "UNAVAILABLE");
        }
        try {
            URI uri = URI.create(endpoint);
            int port = uri.getPort() > 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(uri.getHost(), port), 700);
            }
            return new ModelAvailability(true, null, "MODEL_GATEWAY_SECURE_QUERY");
        } catch (Exception exception) {
            return new ModelAvailability(false, "模型端点连接失败", "UNAVAILABLE");
        }
    }

    private AiConversation conversation(
            AuthenticatedContext context,
            AiContextQueryModels.QueryRequest input,
            PublishedAgent published,
            String moduleCode,
            String entryType) {
        if (input.conversationId() != null) {
            AiConversation existing = requireConversation(context, input.conversationId());
            if (!published.agent().getId().equals(existing.getAgentId())
                    || !published.version().getId().equals(existing.getAgentVersionId())) {
                throw new DomainException("AI_CONVERSATION_AGENT_MISMATCH", "会话绑定的 Agent 版本不一致",
                        HttpStatus.CONFLICT);
            }
            Map<String, Object> storedContext = readMap(existing.getEntryContextJson());
            if (!moduleCode.equals(storedContext.get("moduleCode"))) {
                throw new DomainException("AI_CONVERSATION_CONTEXT_MISMATCH", "会话上下文模块不一致",
                        HttpStatus.CONFLICT);
            }
            return existing;
        }
        AiConversation created = new AiConversation();
        created.setSystemId(context.systemId());
        created.setTenantId(context.tenantId());
        created.setAgentId(published.agent().getId());
        created.setAgentVersionId(published.version().getId());
        created.setAccountId(context.accountId());
        created.setEntryContextJson(writeJson(entryContext(moduleCode, entryType, input.entryContext())));
        String title = input.question().strip();
        created.setTitle(title.length() > 120 ? title.substring(0, 120) : title);
        created.setStatus("ACTIVE");
        created.setVersion(0);
        conversationService.insert(created);
        return created;
    }

    private AiExecution execution(AiConversation conversation, AiMessage request, AiAgentVersion version) {
        AiExecution execution = new AiExecution();
        execution.setConversationId(conversation.getId());
        execution.setRequestMessageId(request.getId());
        execution.setAgentVersionId(version.getId());
        execution.setAuthorizationSnapshotJson("{}");
        execution.setStatus("RUNNING");
        execution.setFallbackUsed(false);
        execution.setStartedAt(LocalDateTime.now());
        execution.setVersion(0);
        executionService.insert(execution);
        return execution;
    }

    private void step(
            Long executionId,
            ToolScope tool,
            AiContextQueryModels.QueryRequest input,
            AiContextQueryModels.QueryScope scope,
            AiContextQueryModels.QueryResult result,
            String outcome,
            String errorMessage) {
        AiExecutionStep step = new AiExecutionStep();
        step.setExecutionId(executionId);
        step.setStepNumber(1);
        step.setStepType("AUTHORIZED_QUERY");
        step.setToolId(null);
        step.setInputJson(writeJson(Map.of(
                "question", input.question().strip(), "publishedToolId", tool == null ? 0 : tool.toolId(),
                "retrievalScope", scope)));
        step.setOutputJson(writeJson(Map.of(
                "outcome", outcome, "sourceCount", result.sources().size(), "fabricated", false)));
        step.setStatus("SUCCEEDED".equals(outcome) ? "SUCCEEDED" : outcome);
        step.setStartedAt(executionStarted(executionId));
        step.setFinishedAt(LocalDateTime.now());
        step.setErrorMessage("SUCCEEDED".equals(outcome) ? null : errorMessage);
        stepService.insert(step);
    }

    private LocalDateTime executionStarted(Long executionId) {
        AiExecution execution = executionService.selectById(executionId);
        return execution == null ? LocalDateTime.now() : execution.getStartedAt();
    }

    private AiMessage message(
            Long conversationId, String role, String content, Object structured, Object modelUsage, String errorCode) {
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setRoleType(role);
        message.setContentText(content);
        message.setStructuredContentJson(structured == null ? null : writeJson(structured));
        message.setModelUsageJson(modelUsage == null ? null : writeJson(modelUsage));
        message.setErrorCode(errorCode);
        message.setCreatedAt(LocalDateTime.now());
        messageService.insert(message);
        return message;
    }

    private AiContextQueryModels.QueryScope scope(
            AuthenticatedContext context, AiContextQueryModels.QueryRequest input, List<String> fields) {
        String moduleCode = input.entryContext().moduleCode().strip().toLowerCase(Locale.ROOT);
        String actionCode = input.entryContext().recordId() == null ? "LIST" : "DETAIL";
        Map<String, Object> matching = new LinkedHashMap<>();
        context.dataScopes().entrySet().stream().filter(entry -> scopeKeyMatches(entry.getKey(), moduleCode, actionCode))
                .forEach(entry -> matching.put(entry.getKey(), entry.getValue()));
        return new AiContextQueryModels.QueryScope(context.systemId(), context.tenantId(), moduleCode, actionCode,
                defaultValue(input.entryContext().lifecycleState(), "ACTIVE"),
                defaultValue(input.entryContext().tenantScope(), "ALL"),
                defaultValue(input.entryContext().search(), ""), input.entryContext().filters(), fields, matching,
                "/systems/" + context.systemId() + "?workspace=runtime&module=" + moduleCode);
    }

    private Map<String, Object> authorizationSnapshot(
            AuthenticatedContext context, ToolScope tool, AiContextQueryModels.QueryScope scope) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("accountId", context.accountId());
        snapshot.put("memberId", context.memberId());
        snapshot.put("systemId", context.systemId());
        snapshot.put("tenantId", context.tenantId());
        snapshot.put("roleIds", context.roleIds());
        snapshot.put("permissions", context.permissions());
        snapshot.put("dataScopes", context.dataScopes());
        snapshot.put("publishedToolId", tool == null ? null : tool.toolId());
        snapshot.put("retrievalScope", scope);
        return snapshot;
    }

    private List<PublishedAgent> publishedAgents(AuthenticatedContext context) {
        List<AiAgent> agents = agentService.selectList(Wrappers.<AiAgent>lambdaQuery()
                .eq(AiAgent::getSystemId, context.systemId())
                .eq(AiAgent::getOwnerTenantId, context.tenantId())
                .eq(AiAgent::getStatus, "PUBLISHED").orderByAsc(AiAgent::getName));
        List<PublishedAgent> result = new ArrayList<>();
        for (AiAgent agent : agents) {
            AiAgentPublication publication = publicationService.selectList(
                            Wrappers.<AiAgentPublication>lambdaQuery().eq(AiAgentPublication::getAgentId, agent.getId()))
                    .stream().findFirst().orElse(null);
            AiAgentVersion version = publication == null ? null : versionService.selectById(publication.getCurrentVersionId());
            if (version != null && agent.getId().equals(version.getAgentId())) {
                result.add(new PublishedAgent(agent, version, readTree(version.getSnapshotJson())));
            }
        }
        return result;
    }

    private PublishedAgent requirePublishedAgent(AuthenticatedContext context, Long agentId) {
        return publishedAgents(context).stream().filter(item -> agentId.equals(item.agent().getId()))
                .findFirst().orElseThrow(() -> new DomainException("AI_AGENT_NOT_AVAILABLE",
                        "当前租户没有这个已发布 Agent", HttpStatus.NOT_FOUND));
    }

    private List<ToolScope> queryTools(JsonNode snapshot) {
        List<ToolScope> result = new ArrayList<>();
        for (JsonNode tool : snapshot.path("tools")) {
            if (!"QUERY".equals(tool.path("toolType").asText())) continue;
            List<String> fields = new ArrayList<>();
            tool.path("fieldAuthorization").fieldNames().forEachRemaining(fields::add);
            result.add(new ToolScope(tool.path("toolId").asLong(), tool.path("resourceId").asText(),
                    tool.path("actionCode").asText(), fields.stream().distinct().sorted().toList()));
        }
        return result;
    }

    private List<String> normalizeFields(List<String> requested, ToolScope tool) {
        List<String> normalized = requested.stream().map(value -> value.strip().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank()).distinct().sorted().toList();
        return normalized.isEmpty() && tool != null ? tool.fieldCodes() : normalized;
    }

    private boolean entryAllowed(JsonNode snapshot, String entryType) {
        Set<String> allowed = new LinkedHashSet<>();
        snapshot.path("contextPolicy").path("allowedEntryContexts").forEach(item -> allowed.add(item.asText()));
        if (allowed.contains(entryType)) return true;
        return Set.of("SYSTEM_AI", "RIGHT_ASSISTANT").contains(entryType) && allowed.contains("MODULE_PAGE");
    }

    private AiConversation requireConversation(AuthenticatedContext context, Long id) {
        AiConversation conversation = conversationService.selectById(id);
        if (conversation == null || !context.systemId().equals(conversation.getSystemId())
                || !context.tenantId().equals(conversation.getTenantId())
                || !context.accountId().equals(conversation.getAccountId())) {
            throw new DomainException("AI_CONVERSATION_NOT_FOUND", "AI 会话不存在", HttpStatus.NOT_FOUND);
        }
        return conversation;
    }

    private AiContextQueryModels.ConversationSummary summary(AiConversation conversation, String agentName) {
        return new AiContextQueryModels.ConversationSummary(conversation.getId(), conversation.getAgentId(),
                conversation.getAgentVersionId(), agentName, conversation.getTitle(), conversation.getStatus(),
                readMap(conversation.getEntryContextJson()), conversation.getUpdatedAt());
    }

    private Map<String, Object> entryContext(
            String moduleCode, String entryType, AiContextQueryModels.EntryContextRequest input) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("entryType", entryType);
        result.put("moduleCode", moduleCode);
        result.put("recordId", input.recordId());
        result.put("sourcePath", input.sourcePath() == null ? "" : input.sourcePath());
        return result;
    }

    private String metricDefinition(
            AuthenticatedContext context, AiContextQueryModels.QueryRequest input, String moduleCode, String actionCode) {
        if ("DETAIL".equals(actionCode)) {
            return "当前系统、当前租户及当前账号数据权限交集内，可读取的单条 " + moduleCode + " 记录。";
        }
        return "按普通业务列表同一套权限、租户范围、生命周期、搜索和组合筛选计算；总数为筛选后的可见记录数，来源最多展示 "
                + (input.entryContext().pageSize() == null ? 5 : Math.min(input.entryContext().pageSize(), 20)) + " 条。";
    }

    private String answer(String question, String moduleCode, QueryData data) {
        boolean listIntent = question.contains("哪些") || question.contains("列出")
                || question.toLowerCase(Locale.ROOT).contains("list");
        if (data.total() == 0) return "在已说明的权限与筛选范围内，没有找到可见的 " + moduleCode + " 记录。";
        if (listIntent) {
            String titles = data.sources().stream().map(AiContextQueryModels.Source::title)
                    .filter(Objects::nonNull).limit(5).reduce((left, right) -> left + "、" + right).orElse("（无标题）");
            return "在已说明的权限与筛选范围内共找到 " + data.total() + " 条；当前列出：" + titles + "。";
        }
        return "在已说明的权限与筛选范围内，共有 " + data.total() + " 条可见的 " + moduleCode + " 记录。";
    }

    private boolean scopeKeyMatches(String key, String resourceId, String actionCode) {
        String[] parts = key.split(":", 3);
        return parts.length == 3
                && ("*".equals(parts[0]) || "MODULE".equals(parts[0]))
                && ("*".equals(parts[1]) || resourceId.equals(parts[1]))
                && ("*".equals(parts[2]) || actionCode.equals(parts[2]));
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private void requireSystem(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统和租户", HttpStatus.CONFLICT);
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read published Agent snapshot", exception);
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read AI conversation data", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot write AI conversation data", exception);
        }
    }

    private record PublishedAgent(AiAgent agent, AiAgentVersion version, JsonNode snapshot) {
    }

    private record ToolScope(long toolId, String moduleCode, String actionCode, List<String> fieldCodes) {
    }

    private record QueryData(long total, List<AiContextQueryModels.Source> sources) {
    }

    private record ModelAvailability(boolean available, String reason, String mode) {
    }
}
