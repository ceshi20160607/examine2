package com.unique.unexamine.ai.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import com.unique.unexamine.ai.base.entity.AiPendingWrite;
import com.unique.unexamine.ai.base.entity.AiSystemModelGrant;
import com.unique.unexamine.ai.base.service.AiAgentBaseService;
import com.unique.unexamine.ai.base.service.AiAgentPublicationBaseService;
import com.unique.unexamine.ai.base.service.AiAgentVersionBaseService;
import com.unique.unexamine.ai.base.service.AiConversationBaseService;
import com.unique.unexamine.ai.base.service.AiExecutionBaseService;
import com.unique.unexamine.ai.base.service.AiExecutionStepBaseService;
import com.unique.unexamine.ai.base.service.AiMessageBaseService;
import com.unique.unexamine.ai.base.service.AiModelBaseService;
import com.unique.unexamine.ai.base.service.AiPendingWriteBaseService;
import com.unique.unexamine.ai.base.service.AiSystemModelGrantBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.flow.manage.FlowDesignModels;
import com.unique.unexamine.flow.manage.FlowDesignService;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 配置 Agent 仅提出线性审批流程；人工确认后复用 Flow 设计服务保存草稿。 */
@Service
public class AiFlowDraftService {
    private static final Set<String> SAFE_ASSIGNEES = Set.of(
            "INITIATOR_MANAGER", "RECORD_OWNER", "STARTER_SELECTED");

    private final AiAgentBaseService agents;
    private final AiAgentPublicationBaseService publications;
    private final AiAgentVersionBaseService versions;
    private final AiConversationBaseService conversations;
    private final AiMessageBaseService messages;
    private final AiExecutionBaseService executions;
    private final AiExecutionStepBaseService steps;
    private final AiPendingWriteBaseService pendingWrites;
    private final AiSystemModelGrantBaseService grants;
    private final AiModelBaseService models;
    private final ConfiguredModuleBaseService modules;
    private final FlowDesignService flowDesign;
    private final PermissionChecker permissions;
    private final AiModelGateway modelGateway;
    private final AuditRecorder audit;
    private final ObjectMapper json;

    public AiFlowDraftService(AiAgentBaseService agents,
            AiAgentPublicationBaseService publications, AiAgentVersionBaseService versions,
            AiConversationBaseService conversations, AiMessageBaseService messages,
            AiExecutionBaseService executions, AiExecutionStepBaseService steps,
            AiPendingWriteBaseService pendingWrites, AiSystemModelGrantBaseService grants,
            AiModelBaseService models, ConfiguredModuleBaseService modules,
            FlowDesignService flowDesign, PermissionChecker permissions,
            AiModelGateway modelGateway, AuditRecorder audit, ObjectMapper json) {
        this.agents = agents;
        this.publications = publications;
        this.versions = versions;
        this.conversations = conversations;
        this.messages = messages;
        this.executions = executions;
        this.steps = steps;
        this.pendingWrites = pendingWrites;
        this.grants = grants;
        this.models = models;
        this.modules = modules;
        this.flowDesign = flowDesign;
        this.permissions = permissions;
        this.modelGateway = modelGateway;
        this.audit = audit;
        this.json = json;
    }

    /** 仅列出已发布、模型仍获授权且绑定到当前模块的 Flow 草稿 Agent。 */
    @Transactional(readOnly = true)
    public AiFlowDraftModels.Options options(AuthenticatedContext context, Long moduleId) {
        requirePermission(context);
        ConfiguredModule module = requireModule(context, moduleId, false);
        List<AiFlowDraftModels.AgentOption> result = new ArrayList<>();
        for (AiAgent agent : agents.selectList(Wrappers.<AiAgent>lambdaQuery()
                .eq(AiAgent::getSystemId, context.systemId())
                .eq(AiAgent::getOwnerTenantId, context.tenantId())
                .eq(AiAgent::getStatus, "PUBLISHED").orderByAsc(AiAgent::getName))) {
            PublishedAgent published = published(context, agent.getId(), false);
            if (published != null && tool(published.snapshot(), module.getCode()) != null
                    && grantAvailable(context, published.version())) {
                result.add(new AiFlowDraftModels.AgentOption(agent.getId(), agent.getName()));
            }
        }
        return new AiFlowDraftModels.Options(List.copyOf(result), module.getId(), module.getCode(),
                module.getName(), module.getDraftRevision());
    }

    /** 识别仅保存待确认候选和 AI 对话，不创建 Flow。 */
    @Transactional
    public AiFlowDraftModels.CandidateView recognize(AuthenticatedContext context,
            AiFlowDraftModels.RecognizeRequest input, String traceId) {
        requirePermission(context);
        ConfiguredModule module = requireModule(context, input.moduleId(), false);
        PublishedAgent published = published(context, input.agentId(), true);
        JsonNode selectedTool = tool(published.snapshot(), module.getCode());
        if (selectedTool == null) throw forbidden("AI_FLOW_TOOL_DENIED", "Agent 未获当前模块 Flow 草稿授权");
        requireGrant(context, published.version());

        String instruction = input.instruction().strip();
        AiConversation conversation = new AiConversation();
        conversation.setContextType("SYSTEM");
        conversation.setPlatformId(context.platformId());
        conversation.setSystemId(context.systemId());
        conversation.setTenantId(context.tenantId());
        conversation.setAgentId(published.agent().getId());
        conversation.setAgentVersionId(published.version().getId());
        conversation.setAccountId(context.accountId());
        conversation.setSystemMemberId(context.memberId());
        conversation.setEntryContextJson(writeJson(Map.of("entryType", "SYSTEM_ADMIN",
                "moduleId", module.getId(), "moduleCode", module.getCode(), "actionCode", "DESIGN")));
        conversation.setTitle(instruction.substring(0, Math.min(120, instruction.length())));
        conversation.setStatus("ACTIVE");
        conversation.setVersion(0);
        conversations.insert(conversation);
        AiMessage request = message(conversation.getId(), "USER", instruction, null, null, null);

        AiExecution execution = new AiExecution();
        execution.setConversationId(conversation.getId());
        execution.setContextType("SYSTEM");
        execution.setPlatformId(context.platformId());
        execution.setSystemId(context.systemId());
        execution.setTenantId(context.tenantId());
        execution.setAgentId(published.agent().getId());
        execution.setRequestMessageId(request.getId());
        execution.setAgentVersionId(published.version().getId());
        execution.setModelId(published.version().getModelId());
        execution.setModelConfigVersion(published.version().getModelConfigVersion());
        execution.setModelGrantId(published.version().getModelGrantId());
        execution.setModelGrantVersionId(published.version().getModelGrantVersionId());
        execution.setCredentialRef(published.version().getCredentialRef());
        execution.setCredentialVersion(published.version().getCredentialVersion());
        execution.setPolicyVersion(published.version().getPolicyVersion());
        execution.setAuthorizationSnapshotJson(writeJson(authorization(context, module, selectedTool)));
        execution.setPolicySnapshotJson(published.version().getPolicySnapshotJson());
        execution.setStatus("RUNNING");
        execution.setFallbackUsed(false);
        execution.setStartTime(LocalDateTime.now());
        execution.setVersion(0);
        executions.insert(execution);

        AiExecutionStep step = new AiExecutionStep();
        step.setExecutionId(execution.getId());
        step.setAgentId(execution.getAgentId());
        step.setStepNumber(1);
        step.setStepType("FLOW_DRAFT_RECOGNITION");
        step.setToolId(selectedTool.path("toolId").asLong());
        step.setInputJson(writeJson(Map.of("moduleId", module.getId(), "moduleCode", module.getCode(),
                "instruction", modelGateway.maskSensitive(instruction))));
        step.setOutputJson("{}");
        step.setStatus("RUNNING");
        step.setStartTime(LocalDateTime.now());
        steps.insert(step);

        FlowCandidate candidate = new FlowCandidate("", "", List.of());
        String outcome = "READY";
        String status = "PENDING_CONFIRMATION";
        String errorCode = null;
        String message = "已生成 Flow 候选；确认前不会创建流程。";
        int promptTokens = 0;
        int completionTokens = 0;
        try {
            String prompt = "请根据管理员描述生成当前业务模块的线性审批 Flow 草稿，仅输出 JSON："
                    + "{\"flowName\":\"流程名称\",\"description\":\"简短描述\","
                    + "\"steps\":[{\"name\":\"审批步骤名称\",\"assigneeType\":\"INITIATOR_MANAGER\","
                    + "\"confidence\":0.9}]}。steps 只能有 1 至 3 个审批步骤；"
                    + "assigneeType 只能为 INITIATOR_MANAGER、RECORD_OWNER、STARTER_SELECTED。"
                    + "不要输出开始和结束节点、条件分支、脚本、外部地址或其他说明。\n"
                    + "模块：" + module.getName() + "（" + module.getCode() + "）\n管理员描述：" + instruction;
            AiModelGateway.Completion completion = modelGateway.complete(context,
                    published.version().getModelGrantId(),
                    published.snapshot().path("agent").path("systemPrompt").asText(), prompt, true,
                    published.snapshot().path("contextPolicy").path("allowExternalData").asBoolean(false));
            promptTokens = completion.promptTokens();
            completionTokens = completion.completionTokens();
            candidate = parseCandidate(completion.text());
        } catch (DomainException failure) {
            outcome = "DEGRADED";
            status = "NEEDS_MANUAL_INPUT";
            errorCode = failure.code();
            message = "模型未生成可用 Flow 候选：" + failure.getMessage();
        }
        double confidence = candidate.steps().stream().mapToDouble(AiFlowDraftModels.StepCandidate::confidence)
                .average().orElse(0);
        LocalDateTime now = LocalDateTime.now();
        LinkedHashMap<String, Object> preview = new LinkedHashMap<>();
        preview.put("draftKind", "FLOW");
        preview.put("agentId", published.agent().getId());
        preview.put("agentVersionId", published.version().getId());
        preview.put("moduleId", module.getId());
        preview.put("moduleCode", module.getCode());
        preview.put("conversationId", conversation.getId());
        preview.put("executionId", execution.getId());
        preview.put("outcome", outcome);
        preview.put("message", message);
        preview.put("errorCode", errorCode);
        preview.put("confidence", confidence);
        preview.put("toolId", selectedTool.path("toolId").asLong());
        AiPendingWrite pending = new AiPendingWrite();
        pending.setExecutionId(execution.getId());
        pending.setStepId(step.getId());
        pending.setContextType("SYSTEM");
        pending.setPlatformId(context.platformId());
        pending.setSystemId(context.systemId());
        pending.setTenantId(context.tenantId());
        pending.setTargetType("CONFIG_DRAFT");
        pending.setTargetId(module.getCode());
        pending.setTargetModuleId(module.getId());
        pending.setTargetExpectedVersion(module.getDraftRevision());
        pending.setProposedPayloadJson(writeJson(candidate));
        pending.setPreviewJson(writeJson(preview));
        pending.setStatus(status);
        pending.setErrorMessage(errorCode == null ? null : message);
        pending.setCreateTime(now);
        pending.setUpdateTime(now);
        pending.setVersion(0);
        pendingWrites.insert(pending);

        message(conversation.getId(), "ASSISTANT", message, candidate,
                Map.of("modelGenerated", !candidate.steps().isEmpty(), "promptTokens", promptTokens,
                        "completionTokens", completionTokens), errorCode);
        step.setOutputJson(writeJson(Map.of("pendingWriteId", pending.getId(),
                "stepCount", candidate.steps().size(), "confidence", confidence)));
        step.setStatus(candidate.steps().isEmpty() ? "DEGRADED" : "SUCCEEDED");
        step.setFinishTime(now);
        step.setErrorMessage(errorCode == null ? null : message);
        steps.updateById(step);
        execution.setStatus(candidate.steps().isEmpty() ? "DEGRADED" : "WAITING_CONFIRMATION");
        execution.setFallbackUsed(candidate.steps().isEmpty());
        execution.setFinishTime(candidate.steps().isEmpty() ? now : null);
        execution.setErrorCode(errorCode);
        execution.setErrorMessage(errorCode == null ? null : message);
        executions.updateById(execution);
        audit.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "AI_FLOW_DRAFT_CANDIDATE_CREATED", "AI_PENDING_WRITE",
                String.valueOf(pending.getId()), outcome, authorization(context, module, selectedTool),
                Map.of("moduleId", module.getId(), "agentId", published.agent().getId(),
                        "executionId", execution.getId(), "stepCount", candidate.steps().size(),
                        "businessWritten", false));
        return view(pending);
    }

    @Transactional(readOnly = true)
    public AiFlowDraftModels.CandidateView candidate(AuthenticatedContext context, Long pendingWriteId) {
        requirePermission(context);
        return view(requirePending(context, pendingWriteId, false));
    }

    /** 锁定候选和模块版本后，在一个事务内创建 Flow 并保存完整线性图。 */
    @Transactional
    public AiFlowDraftModels.CandidateView confirm(AuthenticatedContext context, Long pendingWriteId,
            AiFlowDraftModels.ConfirmRequest input, String traceId) {
        requirePermission(context);
        AiPendingWrite pending = requirePending(context, pendingWriteId, true);
        if (!"PENDING_CONFIRMATION".equals(pending.getStatus())) {
            throw conflict("AI_FLOW_CANDIDATE_NOT_PENDING", "Flow 候选已失效或已确认");
        }
        if (!Objects.equals(input.expectedVersion(), pending.getVersion())) {
            throw conflict("AI_PENDING_WRITE_VERSION_CONFLICT", "Flow 候选版本已变化，请刷新后重试");
        }
        AiExecution execution = executions.selectById(pending.getExecutionId());
        PublishedAgent published = published(context, execution.getAgentId(), true);
        if (!published.version().getId().equals(execution.getAgentVersionId())) {
            throw conflict("AI_AGENT_VERSION_CHANGED", "Agent 已重新发布，请重新生成 Flow 候选");
        }
        ConfiguredModule module = requireModule(context, pending.getTargetModuleId(), true);
        if (!Objects.equals(module.getDraftRevision(), pending.getTargetExpectedVersion())) {
            throw conflict("AI_FLOW_MODULE_VERSION_CONFLICT", "模块草稿已变化，请重新生成 Flow 候选");
        }
        JsonNode selectedTool = tool(published.snapshot(), module.getCode());
        if (selectedTool == null) throw forbidden("AI_FLOW_TOOL_DENIED", "Agent 的 Flow 草稿授权已失效");
        requireGrant(context, published.version());
        FlowCandidate proposal = proposed(pending);
        if (proposal.steps().isEmpty() || proposal.steps().size() != input.steps().size()) {
            throw invalid("AI_FLOW_STEPS_INVALID", "确认审批步骤数量必须与候选一致");
        }
        Set<Integer> reviewed = new HashSet<>(input.reviewedStepIndexes());
        List<Integer> missing = proposal.steps().stream().filter(step -> step.confidence() < 0.7)
                .map(AiFlowDraftModels.StepCandidate::index).filter(index -> !reviewed.contains(index)).toList();
        if (!missing.isEmpty()) {
            throw new DomainException("AI_FLOW_LOW_CONFIDENCE_REVIEW_REQUIRED",
                    "低可信度审批步骤需要逐项补充或确认", HttpStatus.UNPROCESSABLE_ENTITY,
                    Map.of("stepIndexes", missing));
        }
        for (AiFlowDraftModels.ConfirmStep step : input.steps()) {
            if (!SAFE_ASSIGNEES.contains(step.assigneeType())) {
                throw invalid("AI_FLOW_STEPS_INVALID", "审批人来源不在安全白名单中");
            }
        }

        FlowDesignModels.FlowView created = flowDesign.create(context,
                new FlowDesignModels.CreateFlowRequest(null, input.flowName(), input.description()), traceId);
        FlowDesignModels.FlowView saved = flowDesign.saveDraft(context, created.id(),
                new FlowDesignModels.SaveDraftRequest(created.version(), graphNodes(module.getId(), input.steps()),
                        graphEdges(input.steps().size())), traceId);
        LocalDateTime now = LocalDateTime.now();
        String flowPath = "/systems/" + context.systemId() + "/admin?section=flow&flowId=" + saved.id();
        pending.setStatus("CONFIRMED");
        pending.setConfirmedByAccountId(context.accountId());
        pending.setConfirmedByMemberId(context.memberId());
        pending.setConfirmTime(now);
        pending.setResultJson(writeJson(Map.of("flowId", saved.id(), "flowPath", flowPath,
                "flowDraftRevision", saved.draftRevision(), "businessWritten", true,
                "confirmedSteps", input.steps(), "reviewedStepIndexes", input.reviewedStepIndexes(),
                "published", false)));
        pending.setErrorMessage(null);
        pending.setUpdateTime(now);
        if (pendingWrites.updateById(pending) != 1) {
            throw conflict("AI_PENDING_WRITE_VERSION_CONFLICT", "Flow 候选已被其他操作确认");
        }
        AiExecutionStep step = new AiExecutionStep();
        step.setExecutionId(execution.getId());
        step.setAgentId(execution.getAgentId());
        step.setStepNumber(2);
        step.setStepType("FLOW_DRAFT_CONFIRMED");
        step.setToolId(selectedTool.path("toolId").asLong());
        step.setInputJson(writeJson(Map.of("pendingWriteId", pendingWriteId,
                "confirmedSteps", input.steps(), "flowName", input.flowName())));
        step.setOutputJson(writeJson(Map.of("flowId", saved.id(), "businessWritten", true,
                "published", false)));
        step.setStatus("SUCCEEDED");
        step.setStartTime(now);
        step.setFinishTime(now);
        steps.insert(step);
        message(execution.getConversationId(), "ASSISTANT", "已将确认流程保存为 Flow 草稿，尚未发布。",
                Map.of("flowId", saved.id(), "flowPath", flowPath), Map.of("modelGenerated", false), null);
        execution.setStatus("SUCCEEDED");
        execution.setFinishTime(now);
        execution.setErrorCode(null);
        execution.setErrorMessage(null);
        execution.setAuthorizationSnapshotJson(writeJson(authorization(context, module, selectedTool)));
        executions.updateById(execution);
        audit.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "AI_FLOW_DRAFT_CONFIRMED", "FLOW", String.valueOf(saved.id()),
                "SUCCESS", authorization(context, module, selectedTool),
                Map.of("pendingWriteId", pendingWriteId, "moduleId", module.getId(),
                        "agentId", execution.getAgentId(), "stepCount", input.steps().size(),
                        "businessWritten", true, "published", false));
        return view(pendingWrites.selectById(pendingWriteId));
    }

    private List<FlowDesignModels.NodeInput> graphNodes(Long moduleId, List<AiFlowDraftModels.ConfirmStep> steps) {
        List<FlowDesignModels.NodeInput> result = new ArrayList<>();
        result.add(new FlowDesignModels.NodeInput("start", "START", "开始",
                BigDecimal.valueOf(80), BigDecimal.valueOf(160), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of("moduleId", moduleId)));
        for (int index = 0; index < steps.size(); index++) {
            AiFlowDraftModels.ConfirmStep step = steps.get(index);
            result.add(new FlowDesignModels.NodeInput("approval_" + (index + 1), "APPROVAL", step.name(),
                    BigDecimal.valueOf(340L + 260L * index), BigDecimal.valueOf(160),
                    Map.of("type", step.assigneeType()), Map.of(), Map.of(), Map.of(),
                    Map.of("approvalMode", "OR_SIGN")));
        }
        result.add(new FlowDesignModels.NodeInput("end", "END", "结束",
                BigDecimal.valueOf(340L + 260L * steps.size()), BigDecimal.valueOf(160),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of()));
        return List.copyOf(result);
    }

    private List<FlowDesignModels.EdgeInput> graphEdges(int count) {
        List<FlowDesignModels.EdgeInput> result = new ArrayList<>();
        for (int index = 0; index <= count; index++) {
            String source = index == 0 ? "start" : "approval_" + index;
            String target = index == count ? "end" : "approval_" + (index + 1);
            result.add(new FlowDesignModels.EdgeInput("edge_" + (index + 1), source, target,
                    null, index, Map.of()));
        }
        return List.copyOf(result);
    }

    private FlowCandidate parseCandidate(String response) {
        try {
            String content = response.strip();
            if (content.startsWith("```")) {
                int firstLine = content.indexOf('\n');
                int close = content.lastIndexOf("```");
                if (firstLine < 0 || close <= firstLine) throw new IllegalArgumentException();
                content = content.substring(firstLine + 1, close).strip();
            }
            JsonNode root = json.readTree(content);
            if (root == null || !root.isObject()) throw new IllegalArgumentException();
            String flowName = root.path("flowName").asText("").strip();
            String description = root.path("description").asText("").strip();
            JsonNode steps = root.path("steps");
            if (flowName.isBlank() || flowName.length() > 200 || description.length() > 1000
                    || !steps.isArray() || steps.size() < 1 || steps.size() > 3) {
                throw new IllegalArgumentException();
            }
            List<AiFlowDraftModels.StepCandidate> result = new ArrayList<>();
            for (int index = 0; index < steps.size(); index++) {
                JsonNode step = steps.get(index);
                String name = step.path("name").asText("").strip();
                String assigneeType = step.path("assigneeType").asText("").strip().toUpperCase(Locale.ROOT);
                double confidence = step.path("confidence").asDouble(0.6);
                if (name.isBlank() || name.length() > 200 || !SAFE_ASSIGNEES.contains(assigneeType)
                        || !Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
                    throw new IllegalArgumentException();
                }
                result.add(new AiFlowDraftModels.StepCandidate(index, name, assigneeType, confidence));
            }
            return new FlowCandidate(flowName, description, List.copyOf(result));
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw invalid("AI_FLOW_MODEL_OUTPUT_INVALID", "模型返回的流程结构不符合安全节点和数量限制");
        }
    }

    private AiFlowDraftModels.CandidateView view(AiPendingWrite pending) {
        JsonNode preview = readTree(pending.getPreviewJson());
        JsonNode result = readTree(pending.getResultJson());
        FlowCandidate candidate = proposed(pending);
        return new AiFlowDraftModels.CandidateView(pending.getId(), pending.getStatus(),
                preview.path("outcome").asText(), preview.path("message").asText(),
                preview.path("errorCode").asText(null), candidate.flowName(), candidate.description(),
                candidate.steps(), preview.path("confidence").asDouble(0),
                "CONFIRMED".equals(pending.getStatus()), pending.getVersion(),
                pending.getTargetModuleId(), pending.getTargetId(), preview.path("agentId").asLong(),
                result.path("flowId").isMissingNode() ? null : result.path("flowId").asLong(),
                result.path("flowPath").asText(null));
    }

    private FlowCandidate proposed(AiPendingWrite pending) {
        JsonNode payload = readTree(pending.getProposedPayloadJson());
        List<AiFlowDraftModels.StepCandidate> result = new ArrayList<>();
        try {
            for (JsonNode step : payload.path("steps")) {
                result.add(json.treeToValue(step, AiFlowDraftModels.StepCandidate.class));
            }
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot read AI Flow candidate", failure);
        }
        return new FlowCandidate(payload.path("flowName").asText(""),
                payload.path("description").asText(""), List.copyOf(result));
    }

    private AiPendingWrite requirePending(AuthenticatedContext context, Long id, boolean lock) {
        AiPendingWrite pending = pendingWrites.selectList(Wrappers.<AiPendingWrite>lambdaQuery()
                .eq(AiPendingWrite::getId, id).last(lock ? "for update" : "limit 1"))
                .stream().findFirst().orElse(null);
        AiExecution execution = pending == null ? null : executions.selectById(pending.getExecutionId());
        AiConversation conversation = execution == null ? null : conversations.selectById(execution.getConversationId());
        if (pending == null || !"CONFIG_DRAFT".equals(pending.getTargetType()) || conversation == null
                || !"FLOW".equals(readTree(pending.getPreviewJson()).path("draftKind").asText())
                || !context.systemId().equals(pending.getSystemId())
                || !context.tenantId().equals(pending.getTenantId())
                || !context.accountId().equals(conversation.getAccountId())) {
            throw new DomainException("AI_FLOW_CANDIDATE_NOT_FOUND", "Flow 候选不存在", HttpStatus.NOT_FOUND);
        }
        return pending;
    }

    private ConfiguredModule requireModule(AuthenticatedContext context, Long id, boolean lock) {
        ConfiguredModule module = modules.selectList(Wrappers.<ConfiguredModule>lambdaQuery()
                .eq(ConfiguredModule::getId, id).last(lock ? "for update" : "limit 1"))
                .stream().findFirst().orElse(null);
        if (module == null || !context.systemId().equals(module.getSystemId())
                || !context.tenantId().equals(module.getOwnerTenantId())) {
            throw new DomainException("CONFIGURATION_NOT_FOUND", "当前租户模块不存在", HttpStatus.NOT_FOUND);
        }
        return module;
    }

    private PublishedAgent published(AuthenticatedContext context, Long id, boolean required) {
        AiAgent agent = agents.selectById(id);
        if (agent != null && context.systemId().equals(agent.getSystemId())
                && context.tenantId().equals(agent.getOwnerTenantId())
                && "PUBLISHED".equals(agent.getStatus())) {
            AiAgentPublication publication = publications.selectList(Wrappers.<AiAgentPublication>lambdaQuery()
                    .eq(AiAgentPublication::getAgentId, id)).stream().findFirst().orElse(null);
            AiAgentVersion version = publication == null ? null : versions.selectById(publication.getCurrentVersionId());
            if (version != null && id.equals(version.getAgentId())) {
                return new PublishedAgent(agent, version, readTree(version.getSnapshotJson()));
            }
        }
        if (required) throw forbidden("AI_AGENT_NOT_AVAILABLE", "当前租户没有这个已发布 Agent");
        return null;
    }

    private JsonNode tool(JsonNode snapshot, String moduleCode) {
        if (!snapshot.path("confirmationPolicy").path("writeActionsRequireConfirmation").asBoolean(false)) {
            return null;
        }
        boolean adminEntryAllowed = false;
        for (JsonNode entry : snapshot.path("contextPolicy").path("allowedEntryContexts")) {
            if ("SYSTEM_ADMIN".equals(entry.asText())) adminEntryAllowed = true;
        }
        if (!adminEntryAllowed) return null;
        for (JsonNode tool : snapshot.path("tools")) {
            if ("FLOW_DRAFT".equals(tool.path("toolType").asText())
                    && moduleCode.equals(tool.path("resourceId").asText())
                    && "DESIGN".equals(tool.path("actionCode").asText())
                    && tool.path("requiresConfirmation").asBoolean(false)
                    && tool.path("valid").asBoolean(false)
                    && tool.path("fieldAuthorization").isObject()
                    && tool.path("fieldAuthorization").size() == 0
                    && "CURRENT".equals(tool.path("dataAuthorization").path("requestedMode").asText())) {
                return tool;
            }
        }
        return null;
    }

    private boolean grantAvailable(AuthenticatedContext context, AiAgentVersion version) {
        AiSystemModelGrant grant = grants.selectById(version.getModelGrantId());
        AiModel model = grant == null ? null : models.selectById(grant.getModelId());
        return grant != null && model != null && context.systemId().equals(grant.getSystemId())
                && context.platformId().equals(model.getPlatformId())
                && Objects.equals(version.getModelId(), model.getId())
                && "ACTIVE".equals(grant.getStatus()) && "ACTIVE".equals(model.getStatus());
    }

    private void requireGrant(AuthenticatedContext context, AiAgentVersion version) {
        if (!grantAvailable(context, version)) {
            throw forbidden("AI_MODEL_NOT_GRANTED", "Agent 模型授权已撤销或模型已停用");
        }
    }

    private void requirePermission(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null) {
            throw conflict("SYSTEM_CONTEXT_REQUIRED", "请先进入系统租户上下文");
        }
        if (!permissions.allows(context, "FLOW", "SYSTEM", "DESIGN")
                || !permissions.allows(context, "CONFIG", "MODULE", "MANAGE")
                || !permissions.allows(context, "AI", "SYSTEM", "VIEW")) {
            throw forbidden("AI_FLOW_PERMISSION_DENIED", "当前成员没有 Flow 设计和模块配置权限");
        }
    }

    private Map<String, Object> authorization(AuthenticatedContext context, ConfiguredModule module, JsonNode tool) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("accountId", context.accountId());
        result.put("memberId", context.memberId());
        result.put("systemId", context.systemId());
        result.put("tenantId", context.tenantId());
        result.put("roleIds", context.roleIds());
        result.put("permissions", context.permissions());
        result.put("moduleId", module.getId());
        result.put("moduleCode", module.getCode());
        result.put("moduleDraftRevision", module.getDraftRevision());
        result.put("toolId", tool.path("toolId").asLong());
        result.put("actionCode", "DESIGN");
        return result;
    }

    private AiMessage message(Long conversationId, String role, String content, Object structured,
            Object usage, String errorCode) {
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setRoleType(role);
        message.setContentText(content);
        message.setStructuredContentJson(structured == null ? null : writeJson(structured));
        message.setModelUsageJson(writeJson(usage == null ? Map.of("modelGenerated", false) : usage));
        message.setErrorCode(errorCode);
        message.setCreateTime(LocalDateTime.now());
        messages.insert(message);
        return message;
    }

    private JsonNode readTree(String value) {
        try {
            return json.readTree(value == null || value.isBlank() ? "{}" : value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot read AI Flow snapshot", failure);
        }
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot persist AI Flow snapshot", failure);
        }
    }

    private DomainException forbidden(String code, String message) {
        return new DomainException(code, message, HttpStatus.FORBIDDEN);
    }

    private DomainException conflict(String code, String message) {
        return new DomainException(code, message, HttpStatus.CONFLICT);
    }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private record FlowCandidate(String flowName, String description,
                                 List<AiFlowDraftModels.StepCandidate> steps) { }

    private record PublishedAgent(AiAgent agent, AiAgentVersion version, JsonNode snapshot) { }
}
