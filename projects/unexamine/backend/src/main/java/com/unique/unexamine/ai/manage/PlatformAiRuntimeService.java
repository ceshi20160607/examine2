package com.unique.unexamine.ai.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.ai.base.entity.AiAgent;
import com.unique.unexamine.ai.base.entity.AiAgentPublication;
import com.unique.unexamine.ai.base.entity.AiAgentVersion;
import com.unique.unexamine.ai.base.entity.AiConversation;
import com.unique.unexamine.ai.base.entity.AiExecution;
import com.unique.unexamine.ai.base.entity.AiMessage;
import com.unique.unexamine.ai.base.entity.AiModel;
import com.unique.unexamine.ai.base.service.AiAgentBaseService;
import com.unique.unexamine.ai.base.service.AiAgentPublicationBaseService;
import com.unique.unexamine.ai.base.service.AiAgentVersionBaseService;
import com.unique.unexamine.ai.base.service.AiConversationBaseService;
import com.unique.unexamine.ai.base.service.AiExecutionBaseService;
import com.unique.unexamine.ai.base.service.AiMessageBaseService;
import com.unique.unexamine.ai.base.service.AiModelBaseService;
import com.unique.unexamine.audit.base.entity.AuditEvent;
import com.unique.unexamine.audit.base.service.AuditEventBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.flow.manage.FlowDesignService;
import com.unique.unexamine.notification.manage.MessageService;
import com.unique.unexamine.notification.manage.TodoService;
import com.unique.unexamine.operations.base.entity.OpsHealthRun;
import com.unique.unexamine.operations.base.service.OpsHealthRunBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.manage.SystemEntryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 平台首页 Agent 只查询平台资源；系统记录必须切换系统后由系统 Agent 查询。 */
@Service
public class PlatformAiRuntimeService {
    private final AiAgentBaseService agents;
    private final AiAgentPublicationBaseService publications;
    private final AiAgentVersionBaseService versions;
    private final AiModelBaseService models;
    private final AiConversationBaseService conversations;
    private final AiMessageBaseService messages;
    private final AiExecutionBaseService executions;
    private final SystemEntryService systemEntry;
    private final TodoService todos;
    private final MessageService inbox;
    private final FlowDesignService flows;
    private final AuditEventBaseService auditEvents;
    private final OpsHealthRunBaseService healthRuns;
    private final PermissionChecker permissions;
    private final AiModelGateway modelGateway;
    private final AuditRecorder audit;
    private final ObjectMapper json;

    public PlatformAiRuntimeService(AiAgentBaseService agents,
            AiAgentPublicationBaseService publications, AiAgentVersionBaseService versions,
            AiModelBaseService models, AiConversationBaseService conversations,
            AiMessageBaseService messages, AiExecutionBaseService executions,
            SystemEntryService systemEntry, TodoService todos, MessageService inbox,
            FlowDesignService flows, AuditEventBaseService auditEvents,
            OpsHealthRunBaseService healthRuns, PermissionChecker permissions,
            AiModelGateway modelGateway, AuditRecorder audit, ObjectMapper json) {
        this.agents = agents;
        this.publications = publications;
        this.versions = versions;
        this.models = models;
        this.conversations = conversations;
        this.messages = messages;
        this.executions = executions;
        this.systemEntry = systemEntry;
        this.todos = todos;
        this.inbox = inbox;
        this.flows = flows;
        this.auditEvents = auditEvents;
        this.healthRuns = healthRuns;
        this.permissions = permissions;
        this.modelGateway = modelGateway;
        this.audit = audit;
        this.json = json;
    }

    /** 普通平台成员只看到已发布的 Agent 和可选平台能力。 */
    @Transactional(readOnly = true)
    public List<PlatformAiRuntimeModels.AgentOption> overview(AuthenticatedContext context) {
        requirePlatform(context);
        return agents.selectList(Wrappers.<AiAgent>lambdaQuery()
                        .eq(AiAgent::getContextType, "PLATFORM")
                        .eq(AiAgent::getPlatformId, context.platformId())
                        .orderByAsc(AiAgent::getCode)).stream()
                .map(agent -> {
                    AiAgentVersion version = currentVersion(agent.getId());
                    return version == null ? null : new PlatformAiRuntimeModels.AgentOption(
                            agent.getId(), agent.getName(), allowedCapabilities(version));
                }).filter(Objects::nonNull).toList();
    }

    /** 先取当前账号可见的来源，再调用模型；提示词不能扩大来源权限。 */
    @Transactional
    public PlatformAiRuntimeModels.QueryResult query(AuthenticatedContext context,
            PlatformAiRuntimeModels.QueryRequest input, String traceId) {
        requirePlatform(context);
        AiAgent agent = agents.selectById(input.agentId());
        if (agent == null || !"PLATFORM".equals(agent.getContextType())
                || !context.platformId().equals(agent.getPlatformId())) {
            throw new DomainException("AI_AGENT_NOT_FOUND", "平台 Agent 不存在", HttpStatus.NOT_FOUND);
        }
        AiAgentVersion version = currentVersion(agent.getId());
        if (version == null) throw new DomainException("AI_AGENT_UNPUBLISHED", "平台 Agent 尚未发布", HttpStatus.CONFLICT);
        String resource = input.resourceType().strip().toUpperCase(Locale.ROOT);
        if (!allowedCapabilities(version).contains(resource)) {
            audit.recordFailure(traceId, context.accountId(), null, null, null,
                    "AI_PLATFORM_QUERY", "AI_AGENT", agent.getId().toString(),
                    "AI_PLATFORM_TOOL_DENIED", Map.of("resourceType", resource));
            throw new DomainException("AI_PLATFORM_TOOL_DENIED", "Agent 未授权该平台资源", HttpStatus.FORBIDDEN);
        }
        String question = input.question().strip();
        boolean switchOnly = requiresSystemSwitch(question);
        List<Map<String, Object>> sources = switchOnly ? List.of() : sources(context, resource);
        AiModel model = models.selectById(version.getModelId());
        if (model == null) throw new DomainException("AI_PLATFORM_MODEL_UNAVAILABLE", "平台模型不存在", HttpStatus.SERVICE_UNAVAILABLE);
        AiConversation conversation = new AiConversation();
        conversation.setContextType("PLATFORM");
        conversation.setPlatformId(context.platformId());
        conversation.setAgentId(agent.getId());
        conversation.setAgentVersionId(version.getId());
        conversation.setAccountId(context.accountId());
        conversation.setPlatformMemberAccountId(context.accountId());
        conversation.setEntryContextJson(writeJson(Map.of("entryType", "PLATFORM_HOME", "resourceType", resource)));
        conversation.setTitle(question.substring(0, Math.min(question.length(), 100)));
        conversation.setStatus("ACTIVE");
        conversation.setVersion(0);
        conversations.insert(conversation);

        AiMessage request = message(conversation.getId(), "USER", question, null, null);
        AiExecution execution = new AiExecution();
        execution.setConversationId(conversation.getId());
        execution.setContextType("PLATFORM");
        execution.setPlatformId(context.platformId());
        execution.setAgentId(agent.getId());
        execution.setRequestMessageId(request.getId());
        execution.setAgentVersionId(version.getId());
        execution.setModelId(model.getId());
        execution.setModelConfigVersion(model.getVersion());
        execution.setCredentialRef(model.getCredentialRef());
        execution.setCredentialVersion(model.getCredentialVersion());
        execution.setPolicyVersion(version.getPolicyVersion());
        execution.setAuthorizationSnapshotJson(writeJson(Map.of("accountId", context.accountId(),
                "resourceType", resource, "sourceCount", sources.size(), "systemBusinessData", false)));
        execution.setPolicySnapshotJson(writeJson(Map.of("contextType", "PLATFORM",
                "denySystemBusinessData", true)));
        execution.setStatus("RUNNING");
        execution.setFallbackUsed(false);
        execution.setVersion(0);
        executions.insert(execution);

        String outcome = "SUCCEEDED";
        String errorCode = null;
        String answer;
        Map<String, Object> usage = new LinkedHashMap<>();
        if (switchOnly) {
            answer = "请先从平台首页进入有权限的目标系统，再在系统内使用 Agent 查询模块和业务记录。";
            usage.put("modelGenerated", false);
        } else {
            try {
                JsonNode snapshot = readJson(version.getSnapshotJson());
                AiModelGateway.Completion completion = modelGateway.completePlatform(context,
                        version.getModelId(), "仅根据提供的平台范围来源回答，不得推断系统模块和业务记录。"
                                + snapshot.path("systemPrompt").asText(),
                        writeJson(Map.of("question", question, "resourceType", resource,
                                "sources", sources)));
                answer = completion.text();
                usage.put("modelGenerated", true);
                usage.put("promptTokens", completion.promptTokens());
                usage.put("completionTokens", completion.completionTokens());
            } catch (DomainException failure) {
                outcome = "DEGRADED";
                errorCode = failure.code();
                answer = "平台模型当前不可用，请稍后重试；现有平台和系统功能仍可正常使用。";
                usage.put("modelGenerated", false);
            }
        }
        message(conversation.getId(), "ASSISTANT", answer,
                writeJson(Map.of("resourceType", resource, "sources", sources)),
                writeJson(usage), errorCode);
        execution.setStatus("SUCCEEDED".equals(outcome) ? "SUCCEEDED" : "FAILED");
        execution.setFallbackUsed("DEGRADED".equals(outcome));
        execution.setFinishTime(LocalDateTime.now());
        execution.setErrorCode(errorCode);
        execution.setErrorMessage(errorCode == null ? null : answer);
        executions.updateById(execution);
        audit.record(traceId, context.accountId(), null, null, null,
                "AI_PLATFORM_QUERY_" + outcome, "AI_EXECUTION", execution.getId().toString(),
                outcome, Map.of("agentId", agent.getId(), "resourceType", resource,
                        "sourceCount", sources.size(), "modelGenerated", usage.get("modelGenerated")));
        return new PlatformAiRuntimeModels.QueryResult(conversation.getId(), execution.getId(),
                outcome, answer, errorCode, resource, sources);
    }

    private List<Map<String, Object>> sources(AuthenticatedContext context, String resource) {
        return switch (resource) {
            case "PLATFORM_SYSTEM" -> systemEntry.listAccessible(context.accountId()).stream().limit(20)
                    .map(item -> source(item.systemId(), item.systemName(), item.status())).toList();
            case "PLATFORM_TASK" -> todos.list(context, "ALL", "ALL").stream().limit(20)
                    .map(item -> source(item.id(), item.title(), item.status())).toList();
            case "PLATFORM_MESSAGE" -> inbox.list(context, "ALL", "ALL", 1, 20).messages().stream()
                    .map(item -> source(item.id(), item.subject(), item.recipientStatus())).toList();
            case "PLATFORM_FLOW" -> flows.list(context).stream().limit(20)
                    .map(item -> source(item.id(), item.name(), item.status())).toList();
            case "PLATFORM_LOG" -> {
                requirePermission(context, "AUDIT", "EVENT", "VIEW");
                yield auditEvents.selectList(Wrappers.<AuditEvent>lambdaQuery()
                                .eq(AuditEvent::getPlatformId, context.platformId())
                                .isNull(AuditEvent::getSystemId)
                                .eq(AuditEvent::getActorAccountId, context.accountId())
                                .orderByDesc(AuditEvent::getId).last("limit 20")).stream()
                        .map(item -> source(item.getId(), item.getEventCode(), item.getResultCode())).toList();
            }
            case "PLATFORM_HEALTH" -> {
                requirePermission(context, "PLATFORM", "CONFIGURATION", "MANAGE");
                yield healthRuns.selectList(Wrappers.<OpsHealthRun>lambdaQuery()
                                .eq(OpsHealthRun::getPlatformId, context.platformId())
                                .eq(OpsHealthRun::getContextType, "PLATFORM")
                                .orderByDesc(OpsHealthRun::getId).last("limit 10")).stream()
                        .map(item -> source(item.getId(), item.getRunType(), item.getStatus())).toList();
            }
            case "MODEL_QUOTA" -> {
                requirePermission(context, "PLATFORM", "AI", "MANAGE");
                yield models.selectList(Wrappers.<AiModel>lambdaQuery()
                                .eq(AiModel::getPlatformId, context.platformId())
                                .eq(AiModel::getScopeType, "PLATFORM")
                                .orderByAsc(AiModel::getCode).last("limit 20")).stream()
                        .map(item -> {
                            Map<String, Object> value = source(item.getId(), item.getName(), item.getStatus());
                            value.put("limits", readJson(item.getLimitPolicyJson()));
                            return value;
                        }).toList();
            }
            default -> throw new DomainException("AI_PLATFORM_TOOL_DENIED",
                    "不支持的平台资源", HttpStatus.FORBIDDEN);
        };
    }

    private Map<String, Object> source(Long id, String name, String status) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id);
        value.put("name", name == null ? "" : name);
        value.put("status", status == null ? "" : status);
        return value;
    }

    private boolean requiresSystemSwitch(String question) {
        return question.matches("(?s).*(系统|模块).*(业务数据|业务记录|字段值|明细|订单|库存).*"
                ) || question.matches("(?s).*(业务数据|业务记录|字段值|明细|订单|库存).*(系统|模块).*"
                );
    }

    private void requirePermission(AuthenticatedContext context, String type, String code, String action) {
        if (!permissions.allows(context, type, code, action)) {
            throw new DomainException("AI_PLATFORM_SOURCE_DENIED", "没有查看该平台来源的权限", HttpStatus.FORBIDDEN);
        }
    }

    private AiAgentVersion currentVersion(Long agentId) {
        AiAgentPublication publication = publications.selectList(Wrappers.<AiAgentPublication>lambdaQuery()
                .eq(AiAgentPublication::getAgentId, agentId)).stream().findFirst().orElse(null);
        return publication == null ? null : versions.selectById(publication.getCurrentVersionId());
    }

    private List<String> allowedCapabilities(AiAgentVersion version) {
        List<String> allowed = new ArrayList<>();
        readJson(version.getSnapshotJson()).path("capabilities").forEach(item -> allowed.add(item.asText()));
        return allowed;
    }

    private AiMessage message(Long conversationId, String role, String content,
            String structured, String usage) {
        return message(conversationId, role, content, structured, usage, null);
    }

    private AiMessage message(Long conversationId, String role, String content,
            String structured, String usage, String errorCode) {
        AiMessage item = new AiMessage();
        item.setConversationId(conversationId);
        item.setRoleType(role);
        item.setContentText(content);
        item.setStructuredContentJson(structured);
        item.setModelUsageJson(usage);
        item.setErrorCode(errorCode);
        messages.insert(item);
        return item;
    }

    private JsonNode readJson(String value) {
        try {
            return json.readTree(value);
        } catch (Exception failure) {
            throw new IllegalStateException("Invalid platform Agent configuration", failure);
        }
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot serialize platform Agent result", failure);
        }
    }

    private void requirePlatform(AuthenticatedContext context) {
        if (context == null || context.platformId() == null || context.systemId() != null) {
            throw new DomainException("AI_PLATFORM_CONTEXT_REQUIRED",
                    "请先返回平台工作区", HttpStatus.FORBIDDEN);
        }
    }
}
