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
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.print.manage.PrintModels;
import com.unique.unexamine.print.manage.PrintService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 系统配置 Agent 根据已发布模块字段提出打印模板，确认后仅创建未发布草稿。 */
@Service
public class AiPrintDraftService {
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
    private final PrintService printService;
    private final PermissionChecker permissions;
    private final AiModelGateway modelGateway;
    private final AuditRecorder audit;
    private final ObjectMapper json;

    public AiPrintDraftService(AiAgentBaseService agents,
            AiAgentPublicationBaseService publications, AiAgentVersionBaseService versions,
            AiConversationBaseService conversations, AiMessageBaseService messages,
            AiExecutionBaseService executions, AiExecutionStepBaseService steps,
            AiPendingWriteBaseService pendingWrites, AiSystemModelGrantBaseService grants,
            AiModelBaseService models, ConfiguredModuleBaseService modules,
            PrintService printService, PermissionChecker permissions,
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
        this.printService = printService;
        this.permissions = permissions;
        this.modelGateway = modelGateway;
        this.audit = audit;
        this.json = json;
    }

    /** 页面目录沿用打印服务的已发布字段边界；草稿模块不提供模板建议。 */
    @Transactional(readOnly = true)
    public AiPrintDraftModels.Options options(AuthenticatedContext context, Long moduleId, String traceId) {
        requirePermission(context);
        ConfiguredModule module = requireModule(context, moduleId, false);
        PrintModels.ModuleOption published = publishedModule(context, moduleId, traceId);
        List<AiPrintDraftModels.AgentOption> agentOptions = new ArrayList<>();
        for (AiAgent agent : agents.selectList(Wrappers.<AiAgent>lambdaQuery()
                .eq(AiAgent::getSystemId, context.systemId())
                .eq(AiAgent::getOwnerTenantId, context.tenantId())
                .eq(AiAgent::getStatus, "PUBLISHED").orderByAsc(AiAgent::getName))) {
            PublishedAgent current = published(context, agent.getId(), false);
            if (current != null && tool(current.snapshot(), module.getCode()) != null
                    && grantAvailable(context, current.version())) {
                agentOptions.add(new AiPrintDraftModels.AgentOption(agent.getId(), agent.getName()));
            }
        }
        List<AiPrintDraftModels.FieldOption> fieldOptions = published.fields().stream()
                .map(field -> new AiPrintDraftModels.FieldOption(field.code(), field.name(), field.fieldType()))
                .toList();
        return new AiPrintDraftModels.Options(List.copyOf(agentOptions), module.getId(), module.getCode(),
                module.getName(), module.getDraftRevision(), published.publishedVersionId(), fieldOptions);
    }

    /** 识别仅写 AI 待确认区、对话和执行日志，不创建打印模板。 */
    @Transactional
    public AiPrintDraftModels.CandidateView recognize(AuthenticatedContext context,
            AiPrintDraftModels.RecognizeRequest input, String traceId) {
        requirePermission(context);
        ConfiguredModule module = requireModule(context, input.moduleId(), false);
        PrintModels.ModuleOption publishedModule = publishedModule(context, module.getId(), traceId);
        PublishedAgent published = published(context, input.agentId(), true);
        JsonNode selectedTool = tool(published.snapshot(), module.getCode());
        if (selectedTool == null) throw forbidden("AI_PRINT_TOOL_DENIED", "Agent 未获当前模块打印模板草稿授权");
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
                "moduleId", module.getId(), "moduleCode", module.getCode(), "actionCode", "PRINT_TEMPLATE")));
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
        step.setStepType("PRINT_TEMPLATE_RECOGNITION");
        step.setToolId(selectedTool.path("toolId").asLong());
        step.setInputJson(writeJson(Map.of("moduleId", module.getId(), "moduleCode", module.getCode(),
                "instruction", modelGateway.maskSensitive(instruction))));
        step.setOutputJson("{}");
        step.setStatus("RUNNING");
        step.setStartTime(LocalDateTime.now());
        steps.insert(step);

        PrintCandidate candidate = null;
        String outcome = "READY";
        String status = "PENDING_CONFIRMATION";
        String errorCode = null;
        String notice = "已生成打印模板候选；确认前不会创建模板。";
        int promptTokens = 0;
        int completionTokens = 0;
        try {
            List<Map<String, String>> fields = publishedModule.fields().stream().map(field -> Map.of(
                    "code", field.code(), "name", field.name(), "fieldType", field.fieldType())).toList();
            String prompt = "请根据当前已发布模块字段和管理员描述生成打印模板草稿，仅输出 JSON："
                    + "{\"code\":\"example_print\",\"name\":\"模板名称\",\"pageSize\":\"A4\","
                    + "\"orientation\":\"PORTRAIT\",\"layout\":{\"header\":\"页眉\","
                    + "\"footer\":\"页脚\",\"signatureLabel\":\"签名\","
                    + "\"fieldCodes\":[\"fieldCode\"],\"detailFieldCodes\":[],"
                    + "\"rowsPerPage\":12,\"offsetXmm\":0,\"offsetYmm\":0},\"confidence\":0.9}。"
                    + "code 以小写英文字母开头，仅含小写字母、数字、下划线，长度 2 至 100；"
                    + "pageSize 只用 A4/A5，orientation 只用 PORTRAIT/LANDSCAPE；"
                    + "普通字段 1 至 40 个，明细字段不超过 20 个，两组不重复；"
                    + "rowsPerPage 为 4 至 24，水平偏移为 -14 至 14 毫米，垂直偏移为 -12 至 12 毫米。"
                    + "字段编码只能从提供的已发布字段选择，不要输出其他文字。\n"
                    + "模块：" + module.getName() + "（" + module.getCode() + "）\n字段：" + writeJson(fields)
                    + "\n管理员描述：" + instruction;
            AiModelGateway.Completion completion = modelGateway.complete(context,
                    published.version().getModelGrantId(),
                    published.snapshot().path("agent").path("systemPrompt").asText(), prompt, true,
                    published.snapshot().path("contextPolicy").path("allowExternalData").asBoolean(false));
            promptTokens = completion.promptTokens();
            completionTokens = completion.completionTokens();
            candidate = parseCandidate(completion.text(), publishedModule);
        } catch (DomainException failure) {
            outcome = "DEGRADED";
            status = "NEEDS_MANUAL_INPUT";
            errorCode = failure.code();
            notice = "模型未生成可用打印模板候选：" + failure.getMessage();
        }
        double confidence = candidate == null ? 0 : candidate.confidence();
        LocalDateTime now = LocalDateTime.now();
        LinkedHashMap<String, Object> preview = new LinkedHashMap<>();
        preview.put("draftKind", "PRINT");
        preview.put("agentId", published.agent().getId());
        preview.put("agentVersionId", published.version().getId());
        preview.put("moduleId", module.getId());
        preview.put("moduleCode", module.getCode());
        preview.put("publishedVersionId", publishedModule.publishedVersionId());
        preview.put("conversationId", conversation.getId());
        preview.put("executionId", execution.getId());
        preview.put("outcome", outcome);
        preview.put("message", notice);
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
        pending.setProposedPayloadJson(writeJson(candidate == null ? Map.of() : candidate));
        pending.setPreviewJson(writeJson(preview));
        pending.setStatus(status);
        pending.setErrorMessage(errorCode == null ? null : notice);
        pending.setCreateTime(now);
        pending.setUpdateTime(now);
        pending.setVersion(0);
        pendingWrites.insert(pending);
        message(conversation.getId(), "ASSISTANT", notice, candidate == null ? Map.of() : candidate,
                Map.of("modelGenerated", candidate != null, "promptTokens", promptTokens,
                        "completionTokens", completionTokens), errorCode);
        step.setOutputJson(writeJson(Map.of("pendingWriteId", pending.getId(),
                "candidateAvailable", candidate != null, "confidence", confidence)));
        step.setStatus(candidate == null ? "DEGRADED" : "SUCCEEDED");
        step.setFinishTime(now);
        step.setErrorMessage(errorCode == null ? null : notice);
        steps.updateById(step);
        execution.setStatus(candidate == null ? "DEGRADED" : "WAITING_CONFIRMATION");
        execution.setFallbackUsed(candidate == null);
        execution.setFinishTime(candidate == null ? now : null);
        execution.setErrorCode(errorCode);
        execution.setErrorMessage(errorCode == null ? null : notice);
        executions.updateById(execution);
        audit.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "AI_PRINT_CANDIDATE_CREATED", "AI_PENDING_WRITE",
                String.valueOf(pending.getId()), outcome, authorization(context, module, selectedTool),
                Map.of("moduleId", module.getId(), "agentId", published.agent().getId(),
                        "executionId", execution.getId(), "businessWritten", false));
        return view(pending);
    }

    @Transactional(readOnly = true)
    public AiPrintDraftModels.CandidateView candidate(AuthenticatedContext context, Long pendingWriteId) {
        requirePermission(context);
        return view(requirePending(context, pendingWriteId, false));
    }

    /** 候选、模块草稿和发布字段重新核对后才复用打印服务创建模板草稿。 */
    @Transactional
    public AiPrintDraftModels.CandidateView confirm(AuthenticatedContext context, Long pendingWriteId,
            AiPrintDraftModels.ConfirmRequest input, String traceId) {
        requirePermission(context);
        AiPendingWrite pending = requirePending(context, pendingWriteId, true);
        if (!"PENDING_CONFIRMATION".equals(pending.getStatus())) {
            throw conflict("AI_PRINT_CANDIDATE_NOT_PENDING", "打印候选已失效或已确认");
        }
        if (!Objects.equals(input.expectedVersion(), pending.getVersion())) {
            throw conflict("AI_PENDING_WRITE_VERSION_CONFLICT", "打印候选版本已变化，请刷新后重试");
        }
        AiExecution execution = executions.selectById(pending.getExecutionId());
        PublishedAgent published = published(context, execution.getAgentId(), true);
        if (!Objects.equals(published.version().getId(), execution.getAgentVersionId())) {
            throw conflict("AI_AGENT_VERSION_CHANGED", "Agent 已重新发布，请重新生成打印候选");
        }
        ConfiguredModule module = requireModule(context, pending.getTargetModuleId(), true);
        if (!Objects.equals(module.getDraftRevision(), pending.getTargetExpectedVersion())) {
            throw conflict("AI_PRINT_MODULE_VERSION_CONFLICT", "模块草稿已变化，请重新生成打印候选");
        }
        JsonNode selectedTool = tool(published.snapshot(), module.getCode());
        if (selectedTool == null) throw forbidden("AI_PRINT_TOOL_DENIED", "Agent 的打印模板授权已失效");
        requireGrant(context, published.version());
        PrintModels.ModuleOption current = publishedModule(context, module.getId(), traceId);
        if (!Objects.equals(current.publishedVersionId(),
                readTree(pending.getPreviewJson()).path("publishedVersionId").asLong())) {
            throw conflict("AI_PRINT_PUBLISHED_VERSION_CONFLICT", "模块发布字段已变化，请重新生成打印候选");
        }
        PrintCandidate proposal = proposed(pending);
        if (proposal == null) throw conflict("AI_PRINT_CANDIDATE_NOT_READY", "打印候选没有可确认内容");
        if (proposal.confidence() < 0.7 && !Boolean.TRUE.equals(input.reviewedLowConfidence())) {
            throw new DomainException("AI_PRINT_LOW_CONFIDENCE_REVIEW_REQUIRED",
                    "低可信度打印模板需要人工核对", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        validateFields(input.layout(), current);
        PrintModels.TemplateView saved = printService.create(context,
                new PrintModels.SaveTemplateRequest(module.getId(), input.code(), input.name(),
                        input.pageSize(), input.orientation(), input.layout(), null), traceId);
        LocalDateTime now = LocalDateTime.now();
        pending.setStatus("CONFIRMED");
        pending.setConfirmedByAccountId(context.accountId());
        pending.setConfirmedByMemberId(context.memberId());
        pending.setConfirmTime(now);
        pending.setResultJson(writeJson(Map.of("templateId", saved.id(), "businessWritten", true,
                "published", false, "confirmedTemplate", input, "draftRevision", saved.draftRevision())));
        pending.setErrorMessage(null);
        pending.setUpdateTime(now);
        if (pendingWrites.updateById(pending) != 1) {
            throw conflict("AI_PENDING_WRITE_VERSION_CONFLICT", "打印候选已被其他操作确认");
        }
        AiExecutionStep step = new AiExecutionStep();
        step.setExecutionId(execution.getId());
        step.setAgentId(execution.getAgentId());
        step.setStepNumber(2);
        step.setStepType("PRINT_TEMPLATE_CONFIRMED");
        step.setToolId(selectedTool.path("toolId").asLong());
        step.setInputJson(writeJson(Map.of("pendingWriteId", pendingWriteId,
                "confirmedTemplate", input)));
        step.setOutputJson(writeJson(Map.of("templateId", saved.id(), "businessWritten", true,
                "published", false)));
        step.setStatus("SUCCEEDED");
        step.setStartTime(now);
        step.setFinishTime(now);
        steps.insert(step);
        message(execution.getConversationId(), "ASSISTANT", "已将确认模板保存为打印草稿，尚未发布。",
                Map.of("templateId", saved.id()), Map.of("modelGenerated", false), null);
        execution.setStatus("SUCCEEDED");
        execution.setFinishTime(now);
        execution.setErrorCode(null);
        execution.setErrorMessage(null);
        execution.setAuthorizationSnapshotJson(writeJson(authorization(context, module, selectedTool)));
        executions.updateById(execution);
        audit.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "AI_PRINT_DRAFT_CONFIRMED", "PRINT_TEMPLATE",
                String.valueOf(saved.id()), "SUCCESS", authorization(context, module, selectedTool),
                Map.of("moduleId", module.getId(), "pendingWriteId", pendingWriteId,
                        "businessWritten", true, "published", false));
        return view(pendingWrites.selectById(pendingWriteId));
    }

    private PrintCandidate parseCandidate(String response, PrintModels.ModuleOption module) {
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
            String code = root.path("code").asText("").strip();
            String name = root.path("name").asText("").strip();
            String pageSize = root.path("pageSize").asText("").strip();
            String orientation = root.path("orientation").asText("").strip();
            double confidence = root.path("confidence").asDouble(0.6);
            if (!code.matches("[a-z][a-z0-9_]{1,99}") || name.isBlank() || name.length() > 200
                    || !Set.of("A4", "A5").contains(pageSize)
                    || !Set.of("PORTRAIT", "LANDSCAPE").contains(orientation)
                    || !Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
                throw new IllegalArgumentException();
            }
            PrintModels.Layout layout = json.treeToValue(root.path("layout"), PrintModels.Layout.class);
            validateFields(layout, module);
            return new PrintCandidate(code, name, pageSize, orientation, layout, confidence);
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw invalid("AI_PRINT_MODEL_OUTPUT_INVALID", "模型返回的打印模板不符合当前已发布字段及安全布局限制");
        }
    }

    private void validateFields(PrintModels.Layout layout, PrintModels.ModuleOption module) {
        Set<String> allowed = new HashSet<>();
        module.fields().forEach(field -> allowed.add(field.code()));
        List<String> normal = layout == null ? null : layout.fieldCodes();
        List<String> detail = layout == null ? null : layout.detailFieldCodes();
        if (normal == null || normal.isEmpty() || normal.size() > 40
                || detail != null && detail.size() > 20
                || layout.rowsPerPage() == null || layout.rowsPerPage() < 4 || layout.rowsPerPage() > 24
                || layout.offsetXmm() != null && (layout.offsetXmm() < -14 || layout.offsetXmm() > 14)
                || layout.offsetYmm() != null && (layout.offsetYmm() < -12 || layout.offsetYmm() > 12)
                || layout.header() != null && layout.header().length() > 200
                || layout.footer() != null && layout.footer().length() > 500
                || layout.signatureLabel() != null && layout.signatureLabel().length() > 200) {
            throw invalid("AI_PRINT_FIELDS_INVALID", "打印模板字段或布局参数不符合限制");
        }
        List<String> combined = new ArrayList<>(normal);
        if (detail != null) combined.addAll(detail);
        if (combined.stream().anyMatch(code -> code == null || !allowed.contains(code))
                || new HashSet<>(combined).size() != combined.size()) {
            throw invalid("AI_PRINT_FIELDS_INVALID", "模板只能引用当前已发布字段且不能重复");
        }
    }

    private AiPrintDraftModels.CandidateView view(AiPendingWrite pending) {
        JsonNode preview = readTree(pending.getPreviewJson());
        JsonNode result = readTree(pending.getResultJson());
        PrintCandidate value = proposed(pending);
        return new AiPrintDraftModels.CandidateView(pending.getId(), pending.getStatus(),
                preview.path("outcome").asText(), preview.path("message").asText(),
                preview.path("errorCode").isNull() ? null : preview.path("errorCode").asText(null),
                value == null ? "" : value.code(), value == null ? "" : value.name(),
                value == null ? "" : value.pageSize(), value == null ? "" : value.orientation(),
                value == null ? null : value.layout(), value == null ? 0 : value.confidence(),
                "CONFIRMED".equals(pending.getStatus()), pending.getVersion(), pending.getTargetModuleId(),
                pending.getTargetId(), preview.path("agentId").asLong(),
                result.path("templateId").isMissingNode() ? null : result.path("templateId").asLong());
    }

    private PrintCandidate proposed(AiPendingWrite pending) {
        JsonNode payload = readTree(pending.getProposedPayloadJson());
        if (!payload.has("code")) return null;
        try {
            return new PrintCandidate(payload.path("code").asText(), payload.path("name").asText(),
                    payload.path("pageSize").asText(), payload.path("orientation").asText(),
                    json.treeToValue(payload.path("layout"), PrintModels.Layout.class),
                    payload.path("confidence").asDouble());
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot read AI print candidate", failure);
        }
    }

    private AiPendingWrite requirePending(AuthenticatedContext context, Long id, boolean lock) {
        AiPendingWrite pending = pendingWrites.selectList(Wrappers.<AiPendingWrite>lambdaQuery()
                .eq(AiPendingWrite::getId, id).last(lock ? "for update" : "limit 1"))
                .stream().findFirst().orElse(null);
        AiExecution execution = pending == null ? null : executions.selectById(pending.getExecutionId());
        AiConversation conversation = execution == null ? null : conversations.selectById(execution.getConversationId());
        if (pending == null || !"CONFIG_DRAFT".equals(pending.getTargetType()) || conversation == null
                || !"PRINT".equals(readTree(pending.getPreviewJson()).path("draftKind").asText())
                || !context.systemId().equals(pending.getSystemId())
                || !context.tenantId().equals(pending.getTenantId())
                || !context.accountId().equals(conversation.getAccountId())) {
            throw new DomainException("AI_PRINT_CANDIDATE_NOT_FOUND", "打印候选不存在", HttpStatus.NOT_FOUND);
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

    private PrintModels.ModuleOption publishedModule(AuthenticatedContext context, Long moduleId, String traceId) {
        return printService.adminOverview(context, traceId).modules().stream()
                .filter(item -> Objects.equals(item.id(), moduleId))
                .findFirst().orElseThrow(() -> conflict("AI_PRINT_MODULE_NOT_PUBLISHED",
                        "当前模块尚未发布，无法生成打印模板建议"));
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
        if (!snapshot.path("confirmationPolicy").path("writeActionsRequireConfirmation").asBoolean(false)) return null;
        boolean adminEntryAllowed = false;
        for (JsonNode entry : snapshot.path("contextPolicy").path("allowedEntryContexts")) {
            if ("SYSTEM_ADMIN".equals(entry.asText())) adminEntryAllowed = true;
        }
        if (!adminEntryAllowed) return null;
        for (JsonNode item : snapshot.path("tools")) {
            if ("CONFIG_DRAFT".equals(item.path("toolType").asText())
                    && moduleCode.equals(item.path("resourceId").asText())
                    && "PRINT_TEMPLATE".equals(item.path("actionCode").asText())
                    && item.path("requiresConfirmation").asBoolean(false)
                    && item.path("valid").asBoolean(false)
                    && item.path("fieldAuthorization").isObject()
                    && item.path("fieldAuthorization").size() == 0
                    && "CURRENT".equals(item.path("dataAuthorization").path("requestedMode").asText())) {
                return item;
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
        if (!permissions.allows(context, "CONFIG", "MODULE", "MANAGE")
                || !permissions.allows(context, "AI", "SYSTEM", "VIEW")) {
            throw forbidden("AI_PRINT_PERMISSION_DENIED", "当前成员没有配置 Agent 与打印模板管理权限");
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
        result.put("actionCode", "PRINT_TEMPLATE");
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
            throw new IllegalStateException("Cannot read AI print snapshot", failure);
        }
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot persist AI print snapshot", failure);
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

    private record PublishedAgent(AiAgent agent, AiAgentVersion version, JsonNode snapshot) { }
    private record PrintCandidate(String code, String name, String pageSize, String orientation,
                                  PrintModels.Layout layout, double confidence) { }
}
