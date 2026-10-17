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
import com.unique.unexamine.analytics.base.entity.AnaDataSource;
import com.unique.unexamine.analytics.base.entity.AnaDataSourceVersion;
import com.unique.unexamine.analytics.base.service.AnaDataSourceBaseService;
import com.unique.unexamine.analytics.base.service.AnaDataSourceVersionBaseService;
import com.unique.unexamine.analytics.manage.ReportModels;
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
            "id", "title", "recordNumber", "status", "ownerMemberId", "departmentId", "createdAt", "updatedAt",
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
    private final AnaDataSourceBaseService dataSourceService;
    private final AnaDataSourceVersionBaseService dataSourceVersionService;
    private final AiAuthorizedQueryExecutor authorizedQueryExecutor;
    private final PermissionChecker permissionChecker;
    private final ChannelFieldPolicyResolver fieldPolicyResolver;
    private final PlatformAiConfigurationService platformConfigurationService;
    private final AiModelGateway modelGateway;
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
            AnaDataSourceBaseService dataSourceService,
            AnaDataSourceVersionBaseService dataSourceVersionService,
            AiAuthorizedQueryExecutor authorizedQueryExecutor,
            PermissionChecker permissionChecker,
            ChannelFieldPolicyResolver fieldPolicyResolver,
            PlatformAiConfigurationService platformConfigurationService,
            AiModelGateway modelGateway,
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
        this.dataSourceService = dataSourceService;
        this.dataSourceVersionService = dataSourceVersionService;
        this.authorizedQueryExecutor = authorizedQueryExecutor;
        this.permissionChecker = permissionChecker;
        this.fieldPolicyResolver = fieldPolicyResolver;
        this.platformConfigurationService = platformConfigurationService;
        this.modelGateway = modelGateway;
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
                                .orderByDesc(AiConversation::getUpdateTime).last("limit 25"))
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
        List<String> requestedFields = normalizeFields(context, moduleCode, actionCode,
                input.requestedFieldCodes(), tool);
        AiConversation conversation = conversation(context, input, published, moduleCode, entryType);
        Map<String, Object> requestContent = new LinkedHashMap<>();
        requestContent.put("entryContext", entryContext(moduleCode, entryType, input.entryContext()));
        requestContent.put("requestedFieldCodes", requestedFields);
        if (input.dataSourceId() != null) requestContent.put("dataSourceId", input.dataSourceId());
        AiMessage requestMessage = message(conversation.getId(), "USER", input.question().strip(),
                requestContent, null, null);
        AiExecution execution = execution(conversation, requestMessage, published.version());

        String refusal = refusalReason(context, input, published.snapshot(), tool, moduleCode, entryType,
                actionCode, requestedFields);
        if (refusal != null) {
            return finish(context, traceId, conversation, execution, tool, input, requestedFields,
                    "REFUSED", refusal, "AI_QUERY_SCOPE_DENIED", false, null, List.of(), null);
        }

        if (input.dataSourceId() != null) {
            return queryReport(context, input, traceId, published, conversation, execution, tool,
                    moduleCode, requestedFields);
        }

        ModelAvailability availability = modelAvailability(context, published.snapshot());
        if (!availability.available()) {
            String answer = "模型当前不可用：" + availability.reason()
                    + "。本次未生成或猜测任何业务结果。请检查模型授权、凭证和端点后重试。";
            return finish(context, traceId, conversation, execution, tool, input, requestedFields,
                    "DEGRADED", answer, "AI_MODEL_UNAVAILABLE", true, null, List.of(), availability);
        }

        try {
            QueryData data = executeAuthorizedQuery(context, input, moduleCode, actionCode, requestedFields, traceId);
            String metricDefinition = metricDefinition(context, input, moduleCode, actionCode);
            AiModelGateway.Completion completion;
            try {
                completion = modelGateway.complete(context,
                        published.snapshot().path("modelBinding").path("modelGrantId").asLong(),
                        "只能根据本次提供的授权来源回答；没有来源时说明没有可见数据，不得编造记录。\n"
                                + published.snapshot().path("agent").path("systemPrompt").asText(),
                        writeJson(Map.of("question", input.question(), "moduleCode", moduleCode,
                                "total", data.total(), "sources", data.sources())),
                        true,
                        published.snapshot().path("contextPolicy").path("allowExternalData").asBoolean(false));
            } catch (DomainException failure) {
                return finish(context, traceId, conversation, execution, tool, input, requestedFields,
                        "DEGRADED", "模型调用失败：" + failure.getMessage() + "。未生成业务答案，请稍后重试。",
                        failure.code(), true, null, List.of(), availability);
            }
            return finish(context, traceId, conversation, execution, tool, input, requestedFields,
                    "SUCCEEDED", completion.text(), null, false, metricDefinition, data.sources(),
                    availability.withUsage(completion));
        } catch (DomainException exception) {
            String answer = "当前业务上下文读取失败，未生成或猜测任何业务结果；正常业务页面不受影响。"
                    + (exception.status().is5xxServerError() ? "请稍后重试。" : "请返回业务页面确认记录与权限。" );
            return finish(context, traceId, conversation, execution, tool, input, requestedFields,
                    "DEGRADED", answer, "AI_TOOL_EXECUTION_FAILED", exception.status().is5xxServerError(),
                    null, List.of(), availability);
        } catch (RuntimeException exception) {
            return finish(context, traceId, conversation, execution, tool, input, requestedFields,
                    "DEGRADED", "上下文工具暂时不可用，未生成或猜测任何业务结果；正常业务页面不受影响，请稍后重试。",
                    "AI_TOOL_EXECUTION_FAILED", true, null, List.of(), availability);
        }
    }

    @Transactional(readOnly = true)
    public AiContextQueryModels.ConversationDetail conversation(AuthenticatedContext context, Long conversationId) {
        requireSystem(context);
        AiConversation conversation = requireConversation(context, conversationId);
        AiAgent agent = agentService.selectById(conversation.getAgentId());
        PublishedAgent currentAgent = publishedAgents(context).stream()
                .filter(item -> Objects.equals(item.agent().getId(), conversation.getAgentId())
                        && Objects.equals(item.version().getId(), conversation.getAgentVersionId()))
                .findFirst().orElse(null);
        List<AiContextQueryModels.MessageView> messages = messageService.selectList(
                        Wrappers.<AiMessage>lambdaQuery().eq(AiMessage::getConversationId, conversationId)
                                .orderByAsc(AiMessage::getCreateTime).orderByAsc(AiMessage::getId))
                .stream().map(item -> {
                    Map<String, Object> structured = readMap(item.getStructuredContentJson());
                    if (structured.get("report") != null
                            && !historyReportAllowed(context, conversation, currentAgent, structured)) {
                        // 历史数值不能在成员撤权、来源换版或 Agent 撤销后继续从会话读回。
                        return new AiContextQueryModels.MessageView(item.getId(), item.getRoleType(),
                                "此历史统计已失去当前授权或来源版本已变化，请重新查询。", Map.of(), Map.of(),
                                "AI_QUERY_SCOPE_DENIED", item.getCreateTime());
                    }
                    return new AiContextQueryModels.MessageView(item.getId(), item.getRoleType(),
                            item.getContentText(), structured, readMap(item.getModelUsageJson()),
                            item.getErrorCode(), item.getCreateTime());
                }).toList();
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
        return finish(context, traceId, conversation, execution, tool, input, requestedFields, outcome,
                answer, errorCode, retryable, metricDefinition, sources, availability, null);
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
            ModelAvailability availability,
            AiContextQueryModels.ReportFact report) {
        LocalDateTime finishedAt = LocalDateTime.now();
        AiContextQueryModels.QueryScope scope = scope(context, input, requestedFields);
        AiContextQueryModels.QueryResult result = new AiContextQueryModels.QueryResult(
                conversation.getId(), execution.getId(), outcome, answer, errorCode, retryable, scope,
                metricDefinition, sources, finishedAt, report);
        step(execution.getId(), tool, input, scope, result, outcome, answer);
        execution.setStatus("SUCCEEDED".equals(outcome) ? "SUCCEEDED" : "REFUSED".equals(outcome) ? "REFUSED" : "FAILED");
        execution.setFallbackUsed("DEGRADED".equals(outcome));
        execution.setFinishTime(finishedAt);
        execution.setErrorCode(errorCode);
        execution.setErrorMessage(errorCode == null ? null : answer);
        execution.setAuthorizationSnapshotJson(writeJson(authorizationSnapshot(context, tool, scope)));
        executionService.updateById(execution);
        Map<String, Object> modelUsage = new LinkedHashMap<>();
        modelUsage.put("executionMode", availability == null ? "NONE" : availability.mode());
        modelUsage.put("modelGenerated", availability != null && availability.promptTokens() != null);
        modelUsage.put("sourceCount", sources.size());
        if (availability != null && availability.promptTokens() != null) {
            modelUsage.put("promptTokens", availability.promptTokens());
            modelUsage.put("completionTokens", availability.completionTokens());
        }
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

    /** 只回答所选已发布报表的固定口径，数值和趋势均来自当前成员权限下的真实聚合。 */
    private AiContextQueryModels.QueryResult queryReport(
            AuthenticatedContext context, AiContextQueryModels.QueryRequest input, String traceId,
            PublishedAgent published, AiConversation conversation, AiExecution execution, ToolScope tool,
            String moduleCode, List<String> requestedFields) {
        if (!reportGrantActive(context, published.snapshot())) {
            return finish(context, traceId, conversation, execution, tool, input, requestedFields,
                    "REFUSED", "当前 Agent 的模型授权已失效，不能继续使用报表查询。",
                    "AI_QUERY_SCOPE_DENIED", false, null, List.of(), null);
        }
        Long authorizedVersion;
        try {
            authorizedVersion = authorizedReportVersion(context, input, published.snapshot(), moduleCode);
        } catch (RuntimeException exception) {
            return finish(context, traceId, conversation, execution, tool, input, requestedFields,
                    "DEGRADED", "统计来源暂时无法核验，未读取或猜测任何业务结果，请稍后重试。",
                    "AI_TOOL_EXECUTION_FAILED", true, null, List.of(), null);
        }
        if (authorizedVersion == null) {
            return finish(context, traceId, conversation, execution, tool, input, requestedFields,
                    "REFUSED", "当前 Agent 无权使用该已发布统计来源，或请求条件与来源固定口径不一致。",
                    "AI_QUERY_SCOPE_DENIED", false, null, List.of(), null);
        }
        try {
            ReportModels.Result result = authorizedQueryExecutor.report(context, input.dataSourceId(), traceId);
            // 发布指针并发切换时，不能把未经过 Agent 工具授权复核的新版本结果返回给会话。
            if (!authorizedVersion.equals(result.dataSourceVersionId())) {
                return finish(context, traceId, conversation, execution, tool, input, requestedFields,
                        "REFUSED", "统计来源的发布版本已变化，请重新选择后查询。",
                        "AI_QUERY_SCOPE_DENIED", true, null, List.of(), null);
            }
            AiContextQueryModels.ReportFact fact = new AiContextQueryModels.ReportFact(
                    input.dataSourceId(), result.value(), result.groups(), result.metricDefinition(),
                    result.dataSourceVersionId(), result.dataSourceVersionNumber());
            String value = result.value() == null ? "暂无数值" : String.valueOf(result.value());
            String answer = "已按所选已发布数据源的固定口径统计，结果为 " + value
                    + "。口径：" + result.metricDefinition();
            return finish(context, traceId, conversation, execution, tool, input, requestedFields,
                    "SUCCEEDED", answer, null, false, result.metricDefinition(), List.of(),
                    new ModelAvailability(false, null, "DETERMINISTIC_REPORT"), fact);
        } catch (DomainException exception) {
            boolean denied = exception.status().is4xxClientError()
                    && !HttpStatus.UNPROCESSABLE_ENTITY.equals(exception.status());
            return finish(context, traceId, conversation, execution, tool, input, requestedFields,
                    denied ? "REFUSED" : "DEGRADED",
                    denied ? "当前成员无权读取该统计来源或来源已失效。"
                            : "当前统计来源无法按已发布口径完成计算，请检查来源配置后重试。",
                    denied ? "AI_QUERY_SCOPE_DENIED" : "AI_TOOL_EXECUTION_FAILED",
                    !denied, null, List.of(), null);
        } catch (RuntimeException exception) {
            return finish(context, traceId, conversation, execution, tool, input, requestedFields,
                    "DEGRADED", "统计查询暂时不可用，未生成或猜测任何业务结果，请稍后重试。",
                    "AI_TOOL_EXECUTION_FAILED", true, null, List.of(), null);
        }
    }

    private boolean reportGrantActive(AuthenticatedContext context, JsonNode agentSnapshot) {
        long grantId = agentSnapshot.path("modelBinding").path("modelGrantId").asLong(0);
        AiSystemModelGrant grant = grantId == 0 ? null : grantService.selectById(grantId);
        AiModel model = grant == null || grant.getModelId() == null ? null
                : modelService.selectById(grant.getModelId());
        return grant != null && context.platformId().equals(grant.getPlatformId())
                && context.systemId().equals(grant.getSystemId())
                && "ACTIVE".equals(grant.getStatus()) && model != null
                && context.platformId().equals(model.getPlatformId()) && "ACTIVE".equals(model.getStatus());
    }

    private boolean historyReportAllowed(AuthenticatedContext context, AiConversation conversation,
                                         PublishedAgent currentAgent, Map<String, Object> structured) {
        if (currentAgent == null || !reportGrantActive(context, currentAgent.snapshot())) return false;
        JsonNode report = objectMapper.valueToTree(structured.get("report"));
        long sourceId = report.path("dataSourceId").asLong(0);
        long versionId = report.path("dataSourceVersionId").asLong(0);
        long executionId = objectMapper.valueToTree(structured.get("executionId")).asLong(0);
        if (sourceId < 1 || versionId < 1 || executionId < 1) return false;
        AiExecution execution = executionService.selectById(executionId);
        if (execution == null || !Objects.equals(execution.getConversationId(), conversation.getId())) return false;
        JsonNode authorization = readTree(execution.getAuthorizationSnapshotJson());
        // 两侧都先经过 JSON 解析；直接比较 LongNode 与反序列化后的 IntNode 会误遮蔽刚保存的结果。
        if (authorization.path("accountId").asLong(-1) != context.accountId()
                || authorization.path("memberId").asLong(-1) != context.memberId()
                || !readTree(writeJson(context.roleIds())).equals(authorization.path("roleIds"))
                || !readTree(writeJson(context.permissions())).equals(authorization.path("permissions"))
                || !readTree(writeJson(context.dataScopes())).equals(authorization.path("dataScopes"))) {
            return false;
        }
        String moduleCode = String.valueOf(readMap(conversation.getEntryContextJson()).get("moduleCode"));
        try {
            return Objects.equals(versionId, authorizedReportVersion(context, sourceId,
                    currentAgent.snapshot(), moduleCode, List.of()));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** 来源快照只用于前置核验 Agent 工具；正式统计仍由 ReportService 重新校验当前权限。 */
    private Long authorizedReportVersion(AuthenticatedContext context, AiContextQueryModels.QueryRequest input,
                                         JsonNode agentSnapshot, String moduleCode) {
        var entry = input.entryContext();
        if (entry.recordId() != null || !"ACTIVE".equals(defaultValue(entry.lifecycleState(), "ACTIVE"))
                || !"ALL".equals(defaultValue(entry.tenantScope(), "ALL"))
                || !defaultValue(entry.search(), "").isBlank() || !entry.filters().isEmpty()) return null;
        return authorizedReportVersion(context, input.dataSourceId(), agentSnapshot, moduleCode,
                input.requestedFieldCodes());
    }

    private Long authorizedReportVersion(AuthenticatedContext context, Long dataSourceId,
                                         JsonNode agentSnapshot, String moduleCode, List<String> requestedFields) {
        AnaDataSource source = dataSourceService.selectById(dataSourceId);
        if (source == null || source.getCurrentVersionId() == null || !"SYSTEM".equals(source.getContextType())
                || !Objects.equals(context.platformId(), source.getPlatformId())
                || !Objects.equals(context.systemId(), source.getSystemId())
                || !Objects.equals(context.tenantId(), source.getOwnerTenantId())) return null;
        AnaDataSourceVersion version = dataSourceVersionService.selectById(source.getCurrentVersionId());
        if (version == null || !Objects.equals(source.getId(), version.getDataSourceId())
                || !Objects.equals(source.getScopeType(), version.getScopeType())
                || !Objects.equals(source.getScopeOwnerId(), version.getScopeOwnerId())) return null;
        JsonNode snapshot = readTree(version.getSnapshotJson());
        if (!"SYSTEM".equals(snapshot.path("contextType").asText())
                || snapshot.path("platformId").asLong(-1) != context.platformId()
                || snapshot.path("systemId").asLong(-1) != context.systemId()
                || snapshot.path("ownerTenantId").asLong(-1) != context.tenantId()
                || !"MODULE_REPORT".equals(snapshot.path("sourceType").asText())) return null;
        JsonNode definition = snapshot.path("definition");
        JsonNode modules = definition.path("modules");
        if (!modules.isArray() || modules.isEmpty()
                || !moduleCode.equals(modules.get(0).path("moduleCode").asText())) return null;
        Map<String, ToolScope> toolsByAlias = new LinkedHashMap<>();
        List<ToolScope> tools = queryTools(agentSnapshot);
        for (JsonNode module : modules) {
            String alias = module.path("alias").asText();
            String code = module.path("moduleCode").asText();
            ToolScope match = tools.stream().filter(item -> code.equals(item.moduleCode())
                    && "LIST".equals(item.actionCode())).findFirst().orElse(null);
            if (alias.isBlank() || code.isBlank() || toolsByAlias.containsKey(alias) || match == null
                    || !permissionChecker.allows(context, "MODULE", code, "LIST")) return null;
            toolsByAlias.put(alias, match);
        }
        return reportFieldsAuthorized(context, definition, toolsByAlias, requestedFields)
                ? version.getId() : null;
    }

    private boolean reportFieldsAuthorized(AuthenticatedContext context, JsonNode definition,
                                           Map<String, ToolScope> toolsByAlias,
                                           List<String> requestedFields) {
        for (String section : List.of("outputFields", "filters")) {
            JsonNode fields = definition.path(section);
            if (!fields.isArray()) return false;
            for (JsonNode field : fields) {
                if (!reportFieldAuthorized(context, field.path("alias").asText(), field.path("fieldCode").asText(),
                        toolsByAlias, requestedFields)) return false;
            }
        }
        JsonNode metric = definition.path("metric");
        if (!"COUNT".equalsIgnoreCase(metric.path("operation").asText("COUNT"))
                && !reportFieldAuthorized(context, metric.path("alias").asText(), metric.path("fieldCode").asText(),
                toolsByAlias, requestedFields)) return false;
        for (String section : List.of("dimension", "sort", "timeField")) {
            JsonNode field = definition.path(section);
            if (field.isObject() && !field.isEmpty()
                    && !reportFieldAuthorized(context, field.path("alias").asText(), field.path("fieldCode").asText(),
                    toolsByAlias, requestedFields)) return false;
        }
        JsonNode relations = definition.path("relations");
        if (!relations.isArray()) return false;
        for (JsonNode relation : relations) {
            if (!reportFieldAuthorized(context, relation.path("leftAlias").asText(), relation.path("leftField").asText(),
                    toolsByAlias, requestedFields)
                    || !reportFieldAuthorized(context, relation.path("rightAlias").asText(), relation.path("rightField").asText(),
                    toolsByAlias, requestedFields)) return false;
        }
        return true;
    }

    private boolean reportFieldAuthorized(AuthenticatedContext context, String alias, String fieldCode,
                                          Map<String, ToolScope> toolsByAlias,
                                          List<String> requestedFields) {
        ToolScope tool = toolsByAlias.get(alias);
        if (tool == null || fieldCode.isBlank()) return false;
        if (BUILT_IN_FIELDS.contains(fieldCode)) return true;
        if (!tool.fieldCodes().contains(fieldCode)
                || (!requestedFields.isEmpty() && !requestedFields.contains(fieldCode))) return false;
        FieldAccessDecision decision = fieldPolicyResolver.resolveIntersection(
                context, tool.moduleCode(), "LIST", List.of("PAGE"), List.of(fieldCode)).get(fieldCode);
        return decision != null && decision.readable()
                && (decision.maskStrategy() == null || decision.maskStrategy().isBlank());
    }

    private QueryData executeAuthorizedQuery(
            AuthenticatedContext context,
            AiContextQueryModels.QueryRequest input,
            String moduleCode,
            String actionCode,
            List<String> fields,
            String traceId) {
        if ("DETAIL".equals(actionCode)) {
            RuntimeRecordView record = authorizedQueryExecutor.detail(context, moduleCode,
                    input.entryContext().recordId(), traceId);
            return new QueryData(1, List.of(source(context, moduleCode, record, fields)));
        }
        String filtersJson = writeJson(input.entryContext().filters());
        RuntimeRecordList result = authorizedQueryExecutor.list(context, moduleCode,
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
        LinkedHashMap<String, AiContextQueryModels.SourceField> presentation = new LinkedHashMap<>();
        for (String field : fields) {
            JsonNode value = record.fields().get(field);
            if (value != null) {
                values.put(field, objectMapper.convertValue(value, Object.class));
                var visible = record.fieldPresentation().get(field);
                if (visible != null) {
                    presentation.put(field, new AiContextQueryModels.SourceField(
                            visible.name(), visible.displayValue()));
                }
            }
        }
        String path = "/systems/" + context.systemId() + "?workspace=runtime&module=" + moduleCode
                + "&recordId=" + record.id();
        return new AiContextQueryModels.Source(record.id(), record.recordNumber(), record.title(), path,
                values, presentation);
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
        return model.getEndpointUrl() == null || model.getEndpointUrl().isBlank()
                ? new ModelAvailability(false, "模型端点未配置", "UNAVAILABLE")
                : new ModelAvailability(true, null, "MODEL_COMPLETION");
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
        created.setContextType("SYSTEM");
        created.setPlatformId(context.platformId());
        created.setSystemId(context.systemId());
        created.setTenantId(context.tenantId());
        created.setAgentId(published.agent().getId());
        created.setAgentVersionId(published.version().getId());
        created.setAccountId(context.accountId());
        created.setSystemMemberId(context.memberId());
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
        execution.setContextType("SYSTEM");
        execution.setPlatformId(conversation.getPlatformId());
        execution.setSystemId(conversation.getSystemId());
        execution.setTenantId(conversation.getTenantId());
        execution.setAgentId(conversation.getAgentId());
        execution.setRequestMessageId(request.getId());
        execution.setAgentVersionId(version.getId());
        execution.setModelId(version.getModelId());
        execution.setModelConfigVersion(version.getModelConfigVersion());
        execution.setModelGrantId(version.getModelGrantId());
        execution.setModelGrantVersionId(version.getModelGrantVersionId());
        execution.setCredentialRef(version.getCredentialRef());
        execution.setCredentialVersion(version.getCredentialVersion());
        execution.setPolicyVersion(version.getPolicyVersion());
        execution.setAuthorizationSnapshotJson("{}");
        execution.setPolicySnapshotJson(version.getPolicySnapshotJson());
        execution.setStatus("RUNNING");
        execution.setFallbackUsed(false);
        execution.setStartTime(LocalDateTime.now());
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
        step.setAgentId(executionService.selectById(executionId).getAgentId());
        step.setStepNumber(1);
        step.setStepType(input.dataSourceId() == null ? "AUTHORIZED_QUERY" : "AUTHORIZED_REPORT_QUERY");
        step.setToolId(tool == null || tool.toolId() == 0 ? null : tool.toolId());
        Map<String, Object> stepInput = new LinkedHashMap<>();
        stepInput.put("question", modelGateway.maskSensitive(input.question().strip()));
        stepInput.put("publishedToolId", tool == null ? 0 : tool.toolId());
        stepInput.put("retrievalScope", scope);
        if (input.dataSourceId() != null) stepInput.put("dataSourceId", input.dataSourceId());
        step.setInputJson(writeJson(stepInput));
        step.setOutputJson(writeJson(Map.of(
                "outcome", outcome, "sourceCount", result.sources().size(),
                "modelGenerated", "SUCCEEDED".equals(outcome) && result.report() == null)));
        step.setStatus("SUCCEEDED".equals(outcome) ? "SUCCEEDED" : outcome);
        step.setStartTime(executionStarted(executionId));
        step.setFinishTime(LocalDateTime.now());
        step.setErrorMessage("SUCCEEDED".equals(outcome) ? null : errorMessage);
        stepService.insert(step);
    }

    private LocalDateTime executionStarted(Long executionId) {
        AiExecution execution = executionService.selectById(executionId);
        return execution == null ? LocalDateTime.now() : execution.getStartTime();
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
        message.setCreateTime(LocalDateTime.now());
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

    private List<String> normalizeFields(AuthenticatedContext context, String moduleCode, String actionCode,
            List<String> requested, ToolScope tool) {
        List<String> normalized = requested.stream().map(String::strip)
                .filter(value -> !value.isBlank()).distinct().sorted().toList();
        if (!normalized.isEmpty() || tool == null) return normalized;
        // 页面没有点名字段时，仅使用 Agent 与当前成员同时可读的字段；点名越权字段仍交给拒绝逻辑处理。
        Map<String, FieldAccessDecision> decisions = fieldPolicyResolver.resolveIntersection(
                context, moduleCode, actionCode, List.of("PAGE"), tool.fieldCodes());
        return tool.fieldCodes().stream().filter(field -> {
            FieldAccessDecision decision = decisions.get(field);
            return decision != null && decision.readable();
        }).toList();
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
                readMap(conversation.getEntryContextJson()), conversation.getUpdateTime());
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

    private record ModelAvailability(boolean available, String reason, String mode,
                                     Integer promptTokens, Integer completionTokens) {
        private ModelAvailability(boolean available, String reason, String mode) {
            this(available, reason, mode, null, null);
        }

        private ModelAvailability withUsage(AiModelGateway.Completion value) {
            return new ModelAvailability(true, null, mode, value.promptTokens(), value.completionTokens());
        }
    }
}
