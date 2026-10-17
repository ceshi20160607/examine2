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
import com.unique.unexamine.dataexchange.manage.ExportModels;
import com.unique.unexamine.dataexchange.manage.ModuleExportService;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
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

/** 配置 Agent 仅提出导出字段草稿；人工确认后调用现有导出配置服务保存。 */
@Service
public class AiExportConfigDraftService {
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
    private final ModuleExportService exports;
    private final PermissionChecker permissions;
    private final AiModelGateway modelGateway;
    private final AuditRecorder audit;
    private final ObjectMapper json;

    public AiExportConfigDraftService(AiAgentBaseService agents,
            AiAgentPublicationBaseService publications, AiAgentVersionBaseService versions,
            AiConversationBaseService conversations, AiMessageBaseService messages,
            AiExecutionBaseService executions, AiExecutionStepBaseService steps,
            AiPendingWriteBaseService pendingWrites, AiSystemModelGrantBaseService grants,
            AiModelBaseService models, ConfiguredModuleBaseService modules,
            ModuleExportService exports, PermissionChecker permissions,
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
        this.exports = exports;
        this.permissions = permissions;
        this.modelGateway = modelGateway;
        this.audit = audit;
        this.json = json;
    }

    /** 目录只包含当前成员实际可导出的字段和已获授权的配置 Agent。 */
    @Transactional(readOnly = true)
    public AiExportConfigDraftModels.Options options(AuthenticatedContext context, Long moduleId, String traceId) {
        requirePermission(context);
        ConfiguredModule module = requireModule(context, moduleId, false);
        ExportModels.ConfigOverview current = exports.config(context, module.getCode(), traceId);
        List<AiExportConfigDraftModels.AgentOption> result = new ArrayList<>();
        for (AiAgent agent : agents.selectList(Wrappers.<AiAgent>lambdaQuery()
                .eq(AiAgent::getSystemId, context.systemId())
                .eq(AiAgent::getOwnerTenantId, context.tenantId())
                .eq(AiAgent::getStatus, "PUBLISHED").orderByAsc(AiAgent::getName))) {
            PublishedAgent published = published(context, agent.getId(), false);
            if (published != null && tool(published.snapshot(), module.getCode()) != null
                    && grantAvailable(context, published.version())) {
                result.add(new AiExportConfigDraftModels.AgentOption(agent.getId(), agent.getName()));
            }
        }
        return new AiExportConfigDraftModels.Options(List.copyOf(result), module.getId(), module.getCode(),
                module.getName(), module.getDraftRevision(), current.availableFields(),
                current.draft() == null ? null : current.draft().version());
    }

    /** 识别只写 AI 候选、对话及执行记录，不保存导出业务配置。 */
    @Transactional
    public AiExportConfigDraftModels.CandidateView recognize(AuthenticatedContext context,
            AiExportConfigDraftModels.RecognizeRequest input, String traceId) {
        requirePermission(context);
        ConfiguredModule module = requireModule(context, input.moduleId(), false);
        ExportModels.ConfigOverview current = exports.config(context, module.getCode(), traceId);
        if (current.availableFields().isEmpty()) {
            throw invalid("AI_EXPORT_FIELDS_UNAVAILABLE", "当前没有可用于导出配置的授权字段");
        }
        PublishedAgent published = published(context, input.agentId(), true);
        JsonNode selectedTool = tool(published.snapshot(), module.getCode());
        if (selectedTool == null) throw forbidden("AI_EXPORT_TOOL_DENIED", "Agent 未获当前模块导出配置草稿授权");
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
                "moduleId", module.getId(), "moduleCode", module.getCode(), "actionCode", "EXPORT_CONFIG")));
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
        step.setStepType("EXPORT_CONFIG_RECOGNITION");
        step.setToolId(selectedTool.path("toolId").asLong());
        step.setInputJson(writeJson(Map.of("moduleId", module.getId(), "moduleCode", module.getCode(),
                "instruction", modelGateway.maskSensitive(instruction))));
        step.setOutputJson("{}");
        step.setStatus("RUNNING");
        step.setStartTime(LocalDateTime.now());
        steps.insert(step);

        ExportCandidate candidate = null;
        String outcome = "READY";
        String status = "PENDING_CONFIRMATION";
        String errorCode = null;
        String notice = "已生成导出配置候选；确认前不会保存业务配置。";
        int promptTokens = 0;
        int completionTokens = 0;
        try {
            List<Map<String, Object>> fieldMetadata = current.availableFields().stream()
                    .map(field -> Map.<String, Object>of("code", field.code(), "name", field.name(),
                            "fieldType", field.fieldType(), "masked", field.masked()))
                    .toList();
            String prompt = "请根据当前成员可导出的模块字段和管理员描述生成导出字段配置草稿，仅输出 JSON："
                    + "{\"name\":\"导出配置名称\",\"selectedFields\":[\"fieldCode\"],"
                    + "\"relationDisplay\":\"LABEL\",\"confidence\":0.9}。"
                    + "selectedFields 必须是提供字段中的 1 至 50 个编码，按所需列顺序排列且不重复；"
                    + "relationDisplay 只能是 LABEL 或 ID。不要输出即时导出任务、记录内容或其他文字。\n"
                    + "模块：" + module.getName() + "（" + module.getCode() + "）\n可导出字段："
                    + writeJson(fieldMetadata) + "\n管理员描述：" + instruction;
            AiModelGateway.Completion completion = modelGateway.complete(context,
                    published.version().getModelGrantId(),
                    published.snapshot().path("agent").path("systemPrompt").asText(), prompt, true,
                    published.snapshot().path("contextPolicy").path("allowExternalData").asBoolean(false));
            promptTokens = completion.promptTokens();
            completionTokens = completion.completionTokens();
            candidate = parseCandidate(completion.text(), current.availableFields());
        } catch (DomainException failure) {
            outcome = "DEGRADED";
            status = "NEEDS_MANUAL_INPUT";
            errorCode = failure.code();
            notice = "模型未生成可用导出配置候选：" + failure.getMessage();
        }
        double confidence = candidate == null ? 0 : candidate.confidence();
        LocalDateTime now = LocalDateTime.now();
        LinkedHashMap<String, Object> preview = new LinkedHashMap<>();
        preview.put("draftKind", "EXPORT_CONFIG");
        preview.put("agentId", published.agent().getId());
        preview.put("agentVersionId", published.version().getId());
        preview.put("moduleId", module.getId());
        preview.put("moduleCode", module.getCode());
        preview.put("configVersion", current.draft() == null ? null : current.draft().version());
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
                context.memberId(), "AI_EXPORT_CONFIG_CANDIDATE_CREATED", "AI_PENDING_WRITE",
                String.valueOf(pending.getId()), outcome, authorization(context, module, selectedTool),
                Map.of("moduleId", module.getId(), "agentId", published.agent().getId(),
                        "executionId", execution.getId(), "businessWritten", false));
        return view(pending);
    }

    @Transactional(readOnly = true)
    public AiExportConfigDraftModels.CandidateView candidate(AuthenticatedContext context, Long pendingWriteId) {
        requirePermission(context);
        return view(requirePending(context, pendingWriteId, false));
    }

    /** 原候选、模块和导出配置版本都有效时才调用业务服务保存草稿。 */
    @Transactional
    public AiExportConfigDraftModels.CandidateView confirm(AuthenticatedContext context, Long pendingWriteId,
            AiExportConfigDraftModels.ConfirmRequest input, String traceId) {
        requirePermission(context);
        AiPendingWrite pending = requirePending(context, pendingWriteId, true);
        if (!"PENDING_CONFIRMATION".equals(pending.getStatus())) {
            throw conflict("AI_EXPORT_CANDIDATE_NOT_PENDING", "导出配置候选已失效或已确认");
        }
        if (!Objects.equals(input.expectedVersion(), pending.getVersion())) {
            throw conflict("AI_PENDING_WRITE_VERSION_CONFLICT", "导出候选版本已变化，请刷新后重试");
        }
        AiExecution execution = executions.selectById(pending.getExecutionId());
        PublishedAgent published = published(context, execution.getAgentId(), true);
        if (!Objects.equals(published.version().getId(), execution.getAgentVersionId())) {
            throw conflict("AI_AGENT_VERSION_CHANGED", "Agent 已重新发布，请重新生成导出候选");
        }
        ConfiguredModule module = requireModule(context, pending.getTargetModuleId(), true);
        if (!Objects.equals(module.getDraftRevision(), pending.getTargetExpectedVersion())) {
            throw conflict("AI_EXPORT_MODULE_VERSION_CONFLICT", "模块草稿已变化，请重新生成导出候选");
        }
        JsonNode selectedTool = tool(published.snapshot(), module.getCode());
        if (selectedTool == null) throw forbidden("AI_EXPORT_TOOL_DENIED", "Agent 的导出配置授权已失效");
        requireGrant(context, published.version());
        ExportCandidate proposal = proposed(pending);
        if (proposal == null) throw conflict("AI_EXPORT_CANDIDATE_NOT_READY", "导出候选没有可确认内容");
        if (proposal.confidence() < 0.7 && !Boolean.TRUE.equals(input.reviewedLowConfidence())) {
            throw new DomainException("AI_EXPORT_LOW_CONFIDENCE_REVIEW_REQUIRED",
                    "低可信度导出配置需要人工核对", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        JsonNode preview = readTree(pending.getPreviewJson());
        Integer sourceVersion = preview.path("configVersion").isNumber()
                ? preview.path("configVersion").asInt() : null;
        ExportModels.ConfigOverview current = exports.config(context, module.getCode(), traceId);
        Integer currentVersion = current.draft() == null ? null : current.draft().version();
        if (!Objects.equals(input.expectedConfigVersion(), sourceVersion)
                || !Objects.equals(currentVersion, sourceVersion)) {
            throw conflict("AI_EXPORT_CONFIG_VERSION_CONFLICT", "导出配置草稿已变化，请重新生成候选");
        }
        validateFields(input.selectedFields(), current.availableFields());
        ExportModels.ConfigDraftView saved = exports.saveConfig(context, module.getCode(),
                new ExportModels.SaveConfigRequest(input.name(), input.selectedFields(),
                        input.relationDisplay(), input.expectedConfigVersion()), traceId);
        LocalDateTime now = LocalDateTime.now();
        pending.setStatus("CONFIRMED");
        pending.setConfirmedByAccountId(context.accountId());
        pending.setConfirmedByMemberId(context.memberId());
        pending.setConfirmTime(now);
        pending.setResultJson(writeJson(Map.of("configId", saved.id(), "businessWritten", true,
                "published", false, "confirmedConfig", input, "draftRevision", saved.draftRevision())));
        pending.setErrorMessage(null);
        pending.setUpdateTime(now);
        if (pendingWrites.updateById(pending) != 1) {
            throw conflict("AI_PENDING_WRITE_VERSION_CONFLICT", "导出候选已被其他操作确认");
        }
        AiExecutionStep step = new AiExecutionStep();
        step.setExecutionId(execution.getId());
        step.setAgentId(execution.getAgentId());
        step.setStepNumber(2);
        step.setStepType("EXPORT_CONFIG_CONFIRMED");
        step.setToolId(selectedTool.path("toolId").asLong());
        step.setInputJson(writeJson(Map.of("pendingWriteId", pendingWriteId,
                "confirmedConfig", input)));
        step.setOutputJson(writeJson(Map.of("configId", saved.id(), "businessWritten", true,
                "published", false)));
        step.setStatus("SUCCEEDED");
        step.setStartTime(now);
        step.setFinishTime(now);
        steps.insert(step);
        message(execution.getConversationId(), "ASSISTANT", "已将确认字段保存为导出配置草稿，尚未发布。",
                Map.of("configId", saved.id()), Map.of("modelGenerated", false), null);
        execution.setStatus("SUCCEEDED");
        execution.setFinishTime(now);
        execution.setErrorCode(null);
        execution.setErrorMessage(null);
        execution.setAuthorizationSnapshotJson(writeJson(authorization(context, module, selectedTool)));
        executions.updateById(execution);
        audit.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "AI_EXPORT_CONFIG_DRAFT_CONFIRMED", "EXCHANGE_MAPPING",
                String.valueOf(saved.id()), "SUCCESS", authorization(context, module, selectedTool),
                Map.of("moduleId", module.getId(), "pendingWriteId", pendingWriteId,
                        "businessWritten", true, "published", false));
        return view(pendingWrites.selectById(pendingWriteId));
    }

    private ExportCandidate parseCandidate(String response, List<ExportModels.ExportFieldView> available) {
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
            String name = root.path("name").asText("").strip();
            String relationDisplay = root.path("relationDisplay").asText("").strip();
            JsonNode codes = root.path("selectedFields");
            double confidence = root.path("confidence").asDouble(0.6);
            if (name.isBlank() || name.length() > 200 || !Set.of("LABEL", "ID").contains(relationDisplay)
                    || !codes.isArray() || codes.isEmpty() || codes.size() > 50
                    || !Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
                throw new IllegalArgumentException();
            }
            List<String> selected = new ArrayList<>();
            for (JsonNode item : codes) {
                if (!item.isTextual()) throw new IllegalArgumentException();
                selected.add(item.asText());
            }
            validateFields(selected, available);
            return new ExportCandidate(name, List.copyOf(selected), relationDisplay, confidence);
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw invalid("AI_EXPORT_MODEL_OUTPUT_INVALID", "模型返回的导出配置不符合当前授权字段限制");
        }
    }

    private void validateFields(List<String> selected, List<ExportModels.ExportFieldView> available) {
        Set<String> allowed = new HashSet<>();
        available.stream().filter(ExportModels.ExportFieldView::readable)
                .forEach(field -> allowed.add(field.code()));
        if (selected == null || selected.isEmpty() || selected.size() > 50
                || selected.stream().anyMatch(code -> code == null || !allowed.contains(code))
                || new HashSet<>(selected).size() != selected.size()) {
            throw invalid("AI_EXPORT_FIELDS_INVALID", "导出字段必须来自当前授权目录且不可重复");
        }
    }

    private AiExportConfigDraftModels.CandidateView view(AiPendingWrite pending) {
        JsonNode preview = readTree(pending.getPreviewJson());
        JsonNode result = readTree(pending.getResultJson());
        ExportCandidate value = proposed(pending);
        Integer configVersion = preview.path("configVersion").isNumber()
                ? preview.path("configVersion").asInt() : null;
        return new AiExportConfigDraftModels.CandidateView(pending.getId(), pending.getStatus(),
                preview.path("outcome").asText(), preview.path("message").asText(),
                preview.path("errorCode").isNull() ? null : preview.path("errorCode").asText(null),
                value == null ? "" : value.name(), value == null ? List.of() : value.selectedFields(),
                value == null ? "LABEL" : value.relationDisplay(), value == null ? 0 : value.confidence(),
                configVersion, "CONFIRMED".equals(pending.getStatus()), pending.getVersion(),
                pending.getTargetModuleId(), pending.getTargetId(), preview.path("agentId").asLong(),
                result.path("configId").isMissingNode() ? null : result.path("configId").asLong());
    }

    private ExportCandidate proposed(AiPendingWrite pending) {
        JsonNode payload = readTree(pending.getProposedPayloadJson());
        if (!payload.has("name")) return null;
        List<String> codes = new ArrayList<>();
        payload.path("selectedFields").forEach(item -> codes.add(item.asText()));
        return new ExportCandidate(payload.path("name").asText(), List.copyOf(codes),
                payload.path("relationDisplay").asText(), payload.path("confidence").asDouble());
    }

    private AiPendingWrite requirePending(AuthenticatedContext context, Long id, boolean lock) {
        AiPendingWrite pending = pendingWrites.selectList(Wrappers.<AiPendingWrite>lambdaQuery()
                .eq(AiPendingWrite::getId, id).last(lock ? "for update" : "limit 1"))
                .stream().findFirst().orElse(null);
        AiExecution execution = pending == null ? null : executions.selectById(pending.getExecutionId());
        AiConversation conversation = execution == null ? null : conversations.selectById(execution.getConversationId());
        if (pending == null || !"CONFIG_DRAFT".equals(pending.getTargetType()) || conversation == null
                || !"EXPORT_CONFIG".equals(readTree(pending.getPreviewJson()).path("draftKind").asText())
                || !context.systemId().equals(pending.getSystemId())
                || !context.tenantId().equals(pending.getTenantId())
                || !context.accountId().equals(conversation.getAccountId())) {
            throw new DomainException("AI_EXPORT_CANDIDATE_NOT_FOUND", "导出候选不存在", HttpStatus.NOT_FOUND);
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
        if (!snapshot.path("confirmationPolicy").path("writeActionsRequireConfirmation").asBoolean(false)) return null;
        boolean adminEntryAllowed = false;
        for (JsonNode entry : snapshot.path("contextPolicy").path("allowedEntryContexts")) {
            if ("SYSTEM_ADMIN".equals(entry.asText())) adminEntryAllowed = true;
        }
        if (!adminEntryAllowed) return null;
        for (JsonNode item : snapshot.path("tools")) {
            if ("CONFIG_DRAFT".equals(item.path("toolType").asText())
                    && moduleCode.equals(item.path("resourceId").asText())
                    && "EXPORT_CONFIG".equals(item.path("actionCode").asText())
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
            throw forbidden("AI_EXPORT_PERMISSION_DENIED", "当前成员没有配置 Agent 与导出管理权限");
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
        result.put("actionCode", "EXPORT_CONFIG");
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
            throw new IllegalStateException("Cannot read AI export snapshot", failure);
        }
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot persist AI export snapshot", failure);
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
    private record ExportCandidate(String name, List<String> selectedFields,
                                   String relationDisplay, double confidence) { }
}
