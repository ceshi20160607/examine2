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
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleField;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleFieldBaseService;
import com.unique.unexamine.moduleconfig.manage.CreateModuleFieldRequest;
import com.unique.unexamine.moduleconfig.manage.ModuleConfigurationService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 配置 Agent 只提议模块字段；确认时复用模块配置服务并在一个事务中写入。 */
@Service
public class AiModuleFieldDraftService {
    private static final Set<String> SAFE_TYPES = Set.of("TEXT", "MULTILINE_TEXT", "NUMBER",
            "DATE", "DATETIME", "BOOLEAN", "EMAIL", "PHONE");

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
    private final ConfiguredModuleFieldBaseService moduleFields;
    private final ModuleConfigurationService moduleConfiguration;
    private final PermissionChecker permissions;
    private final AiModelGateway modelGateway;
    private final AuditRecorder audit;
    private final ObjectMapper json;

    public AiModuleFieldDraftService(AiAgentBaseService agents,
            AiAgentPublicationBaseService publications, AiAgentVersionBaseService versions,
            AiConversationBaseService conversations, AiMessageBaseService messages,
            AiExecutionBaseService executions, AiExecutionStepBaseService steps,
            AiPendingWriteBaseService pendingWrites, AiSystemModelGrantBaseService grants,
            AiModelBaseService models, ConfiguredModuleBaseService modules,
            ConfiguredModuleFieldBaseService moduleFields,
            ModuleConfigurationService moduleConfiguration, PermissionChecker permissions,
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
        this.moduleFields = moduleFields;
        this.moduleConfiguration = moduleConfiguration;
        this.permissions = permissions;
        this.modelGateway = modelGateway;
        this.audit = audit;
        this.json = json;
    }

    /** 只列出已发布且授权到当前模块的配置 Agent。 */
    @Transactional(readOnly = true)
    public AiModuleFieldDraftModels.Options options(AuthenticatedContext context, Long moduleId) {
        requirePermission(context);
        ConfiguredModule module = requireModule(context, moduleId, false);
        List<AiModuleFieldDraftModels.AgentOption> result = new ArrayList<>();
        for (AiAgent agent : agents.selectList(Wrappers.<AiAgent>lambdaQuery()
                .eq(AiAgent::getSystemId, context.systemId())
                .eq(AiAgent::getOwnerTenantId, context.tenantId())
                .eq(AiAgent::getStatus, "PUBLISHED").orderByAsc(AiAgent::getName))) {
            PublishedAgent published = published(context, agent.getId(), false);
            if (published != null && tool(published.snapshot(), module.getCode()) != null
                    && grantAvailable(context, published.version())) {
                result.add(new AiModuleFieldDraftModels.AgentOption(agent.getId(), agent.getName()));
            }
        }
        return new AiModuleFieldDraftModels.Options(List.copyOf(result), module.getId(), module.getCode(),
                module.getName(), module.getDraftRevision());
    }

    /** 识别仅创建 AI 候选、对话和执行日志，不改模块草稿。 */
    @Transactional
    public AiModuleFieldDraftModels.CandidateView recognize(AuthenticatedContext context,
            AiModuleFieldDraftModels.RecognizeRequest input, String traceId) {
        requirePermission(context);
        ConfiguredModule module = requireModule(context, input.moduleId(), false);
        PublishedAgent published = published(context, input.agentId(), true);
        JsonNode selectedTool = tool(published.snapshot(), module.getCode());
        if (selectedTool == null) throw forbidden("AI_CONFIG_TOOL_DENIED", "Agent 未获当前模块字段草稿授权");
        requireGrant(context, published.version());

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
                "moduleId", module.getId(), "moduleCode", module.getCode(), "actionCode", "CONFIGURE")));
        String instruction = input.instruction().strip();
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
        step.setStepType("CONFIG_FIELD_RECOGNITION");
        step.setToolId(selectedTool.path("toolId").asLong());
        step.setInputJson(writeJson(Map.of("moduleId", module.getId(), "moduleCode", module.getCode(),
                "instruction", modelGateway.maskSensitive(instruction))));
        step.setOutputJson("{}");
        step.setStatus("RUNNING");
        step.setStartTime(LocalDateTime.now());
        steps.insert(step);

        List<AiModuleFieldDraftModels.FieldCandidate> fields = List.of();
        String outcome = "READY";
        String status = "PENDING_CONFIRMATION";
        String errorCode = null;
        String message = "已生成字段候选；确认前不会保存模块配置。";
        int promptTokens = 0;
        int completionTokens = 0;
        try {
            List<String> existingCodes = moduleFields.selectList(Wrappers.<ConfiguredModuleField>lambdaQuery()
                    .eq(ConfiguredModuleField::getModuleId, module.getId()))
                    .stream().map(ConfiguredModuleField::getCode).toList();
            String prompt = "请根据管理员描述生成当前模块的字段草稿，仅输出 JSON："
                    + "{\"fields\":[{\"code\":\"英文编码\",\"name\":\"中文名称\","
                    + "\"fieldType\":\"TEXT\",\"required\":false,\"uniqueValue\":false,"
                    + "\"searchable\":false,\"confidence\":0.9}]}。"
                    + "fields 必须为 1 至 5 项；fieldType 只能是 TEXT、MULTILINE_TEXT、NUMBER、DATE、DATETIME、BOOLEAN、EMAIL、PHONE。"
                    + "编码以英文字母开头，只包含英文字母、数字、下划线，长度 2 至 50。"
                    + "不得生成现有字段，不得输出其他说明或复杂字段配置。现有字段编码：" + existingCodes + "。\n"
                    + "模块：" + module.getName() + "（" + module.getCode() + "）\n管理员描述：" + instruction;
            AiModelGateway.Completion completion = modelGateway.complete(context,
                    published.version().getModelGrantId(),
                    published.snapshot().path("agent").path("systemPrompt").asText(),
                    prompt, true,
                    published.snapshot().path("contextPolicy").path("allowExternalData").asBoolean(false));
            promptTokens = completion.promptTokens();
            completionTokens = completion.completionTokens();
            fields = parseCandidates(completion.text());
            if (fields.stream().anyMatch(field -> existingCodes.stream()
                    .anyMatch(code -> code.equalsIgnoreCase(field.code())))) {
                throw invalid("AI_CONFIG_MODEL_OUTPUT_INVALID", "模型生成了模块中已有字段");
            }
        } catch (DomainException failure) {
            outcome = "DEGRADED";
            status = "NEEDS_MANUAL_INPUT";
            errorCode = failure.code();
            message = "模型未生成可用字段候选：" + failure.getMessage();
        }
        double confidence = fields.stream().mapToDouble(AiModuleFieldDraftModels.FieldCandidate::confidence)
                .average().orElse(0);
        LocalDateTime now = LocalDateTime.now();
        LinkedHashMap<String, Object> preview = new LinkedHashMap<>();
        preview.put("draftKind", "FIELD");
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
        pending.setProposedPayloadJson(writeJson(Map.of("fields", fields)));
        pending.setPreviewJson(writeJson(preview));
        pending.setStatus(status);
        pending.setErrorMessage(errorCode == null ? null : message);
        pending.setCreateTime(now);
        pending.setUpdateTime(now);
        pending.setVersion(0);
        pendingWrites.insert(pending);

        message(conversation.getId(), "ASSISTANT", message, Map.of("fields", fields),
                Map.of("modelGenerated", !fields.isEmpty(), "promptTokens", promptTokens,
                        "completionTokens", completionTokens), errorCode);
        step.setOutputJson(writeJson(Map.of("pendingWriteId", pending.getId(), "fieldCount", fields.size(),
                "confidence", confidence)));
        step.setStatus(fields.isEmpty() ? "DEGRADED" : "SUCCEEDED");
        step.setFinishTime(now);
        step.setErrorMessage(errorCode == null ? null : message);
        steps.updateById(step);
        execution.setStatus(fields.isEmpty() ? "DEGRADED" : "WAITING_CONFIRMATION");
        execution.setFallbackUsed(fields.isEmpty());
        execution.setFinishTime(fields.isEmpty() ? now : null);
        execution.setErrorCode(errorCode);
        execution.setErrorMessage(errorCode == null ? null : message);
        executions.updateById(execution);
        audit.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "AI_CONFIG_FIELD_CANDIDATE_CREATED", "AI_PENDING_WRITE",
                String.valueOf(pending.getId()), outcome, authorization(context, module, selectedTool),
                Map.of("moduleId", module.getId(), "agentId", published.agent().getId(),
                        "executionId", execution.getId(), "fieldCount", fields.size(), "businessWritten", false));
        return view(pending);
    }

    @Transactional(readOnly = true)
    public AiModuleFieldDraftModels.CandidateView candidate(AuthenticatedContext context, Long pendingWriteId) {
        requirePermission(context);
        return view(requirePending(context, pendingWriteId, false));
    }

    /** 确认写入锁定候选与模块，任何字段失败都会回滚整批。 */
    @Transactional
    public AiModuleFieldDraftModels.CandidateView confirm(AuthenticatedContext context, Long pendingWriteId,
            AiModuleFieldDraftModels.ConfirmRequest input, String traceId) {
        requirePermission(context);
        AiPendingWrite pending = requirePending(context, pendingWriteId, true);
        if (!"PENDING_CONFIRMATION".equals(pending.getStatus())) {
            throw conflict("AI_CONFIG_CANDIDATE_NOT_PENDING", "字段候选已失效或已确认");
        }
        if (!Objects.equals(input.expectedVersion(), pending.getVersion())) {
            throw conflict("AI_PENDING_WRITE_VERSION_CONFLICT", "字段候选版本已变化，请刷新后重试");
        }
        AiExecution execution = executions.selectById(pending.getExecutionId());
        PublishedAgent published = published(context, execution.getAgentId(), true);
        if (!published.version().getId().equals(execution.getAgentVersionId())) {
            throw conflict("AI_AGENT_VERSION_CHANGED", "Agent 已重新发布，请重新生成字段候选");
        }
        ConfiguredModule module = requireModule(context, pending.getTargetModuleId(), true);
        if (!Objects.equals(module.getDraftRevision(), pending.getTargetExpectedVersion())) {
            throw conflict("AI_CONFIG_DRAFT_VERSION_CONFLICT", "模块草稿已变化，请重新生成字段候选");
        }
        JsonNode selectedTool = tool(published.snapshot(), module.getCode());
        if (selectedTool == null) throw forbidden("AI_CONFIG_TOOL_DENIED", "Agent 的模块字段草稿授权已失效");
        requireGrant(context, published.version());
        List<AiModuleFieldDraftModels.FieldCandidate> candidates = proposedFields(pending);
        if (candidates.isEmpty() || candidates.size() != input.fields().size()) {
            throw invalid("AI_CONFIG_FIELDS_INVALID", "确认字段数量必须与候选一致");
        }
        Set<String> reviewed = new HashSet<>(input.reviewedFieldCodes());
        List<String> missingReview = candidates.stream().filter(field -> field.confidence() < 0.7)
                .map(AiModuleFieldDraftModels.FieldCandidate::code)
                .filter(code -> !reviewed.contains(code)).toList();
        if (!missingReview.isEmpty()) {
            throw new DomainException("AI_CONFIG_LOW_CONFIDENCE_REVIEW_REQUIRED",
                    "低可信度字段需要逐项补充或确认", HttpStatus.UNPROCESSABLE_ENTITY,
                    Map.of("fieldCodes", missingReview));
        }
        Set<String> codes = new HashSet<>();
        for (AiModuleFieldDraftModels.ConfirmField field : input.fields()) {
            if (!SAFE_TYPES.contains(field.fieldType().toUpperCase(Locale.ROOT))
                    || !codes.add(field.code().toLowerCase(Locale.ROOT))
                    || !field.config().isObject()) {
                throw invalid("AI_CONFIG_FIELDS_INVALID", "字段类型、编码或配置无效");
            }
        }
        List<Long> fieldIds = new ArrayList<>();
        for (AiModuleFieldDraftModels.ConfirmField field : input.fields()) {
            ConfiguredModuleField created = moduleConfiguration.createField(context, module.getId(),
                    new CreateModuleFieldRequest(field.code(), field.name(), field.fieldType(), field.required(),
                            field.uniqueValue(), field.searchable(), null, null, 0, field.config()), traceId);
            fieldIds.add(created.getId());
        }
        LocalDateTime now = LocalDateTime.now();
        pending.setStatus("CONFIRMED");
        pending.setConfirmedByAccountId(context.accountId());
        pending.setConfirmedByMemberId(context.memberId());
        pending.setConfirmTime(now);
        pending.setResultJson(writeJson(Map.of("resultFieldIds", fieldIds, "businessWritten", true,
                "confirmedFields", input.fields(), "reviewedFieldCodes", input.reviewedFieldCodes(), "moduleDraftRevision",
                modules.selectById(module.getId()).getDraftRevision())));
        pending.setErrorMessage(null);
        pending.setUpdateTime(now);
        if (pendingWrites.updateById(pending) != 1) {
            throw conflict("AI_PENDING_WRITE_VERSION_CONFLICT", "字段候选已被其他操作确认");
        }
        AiExecutionStep step = new AiExecutionStep();
        step.setExecutionId(execution.getId());
        step.setAgentId(execution.getAgentId());
        step.setStepNumber(2);
        step.setStepType("CONFIG_FIELD_CONFIRMED");
        step.setToolId(selectedTool.path("toolId").asLong());
        step.setInputJson(writeJson(Map.of("pendingWriteId", pendingWriteId,
                "confirmedFields", input.fields())));
        step.setOutputJson(writeJson(Map.of("resultFieldIds", fieldIds, "businessWritten", true)));
        step.setStatus("SUCCEEDED");
        step.setStartTime(now);
        step.setFinishTime(now);
        steps.insert(step);
        message(execution.getConversationId(), "ASSISTANT", "已将确认字段保存到模块草稿，尚未发布。",
                Map.of("resultFieldIds", fieldIds), Map.of("modelGenerated", false), null);
        execution.setStatus("SUCCEEDED");
        execution.setFinishTime(now);
        execution.setErrorCode(null);
        execution.setErrorMessage(null);
        execution.setAuthorizationSnapshotJson(writeJson(authorization(context, module, selectedTool)));
        executions.updateById(execution);
        audit.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "AI_CONFIG_FIELD_DRAFT_CONFIRMED", "AI_PENDING_WRITE",
                String.valueOf(pendingWriteId), "SUCCESS", authorization(context, module, selectedTool),
                Map.of("moduleId", module.getId(), "fieldIds", fieldIds,
                        "agentId", execution.getAgentId(), "businessWritten", true, "published", false));
        return view(pendingWrites.selectById(pendingWriteId));
    }

    private List<AiModuleFieldDraftModels.FieldCandidate> parseCandidates(String response) {
        try {
            String content = response.strip();
            if (content.startsWith("```")) {
                int firstLine = content.indexOf('\n');
                int close = content.lastIndexOf("```");
                if (firstLine < 0 || close <= firstLine) throw new IllegalArgumentException();
                content = content.substring(firstLine + 1, close).strip();
            }
            JsonNode root = json.readTree(content);
            if (root == null) throw new IllegalArgumentException();
            JsonNode fields = root.path("fields");
            if (!fields.isArray() || fields.size() < 1 || fields.size() > 5) throw new IllegalArgumentException();
            List<AiModuleFieldDraftModels.FieldCandidate> result = new ArrayList<>();
            Set<String> codes = new HashSet<>();
            for (JsonNode field : fields) {
                String code = field.path("code").asText("").strip();
                String name = field.path("name").asText("").strip();
                String type = field.path("fieldType").asText("").strip().toUpperCase(Locale.ROOT);
                double confidence = field.path("confidence").asDouble(0.6);
                if (!code.matches("[A-Za-z][A-Za-z0-9_]{1,49}") || name.isBlank() || name.length() > 200
                        || !SAFE_TYPES.contains(type) || !codes.add(code.toLowerCase(Locale.ROOT))
                        || !Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
                    throw new IllegalArgumentException();
                }
                result.add(new AiModuleFieldDraftModels.FieldCandidate(code, name, type,
                        field.path("required").asBoolean(false), field.path("uniqueValue").asBoolean(false),
                        field.path("searchable").asBoolean(false), json.createObjectNode(), confidence));
            }
            return List.copyOf(result);
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw invalid("AI_CONFIG_MODEL_OUTPUT_INVALID", "模型返回的字段结构不符合安全类型和数量限制");
        }
    }

    private AiModuleFieldDraftModels.CandidateView view(AiPendingWrite pending) {
        JsonNode preview = readTree(pending.getPreviewJson());
        JsonNode result = readTree(pending.getResultJson());
        List<Long> fieldIds = new ArrayList<>();
        result.path("resultFieldIds").forEach(node -> fieldIds.add(node.asLong()));
        return new AiModuleFieldDraftModels.CandidateView(pending.getId(), pending.getStatus(),
                preview.path("outcome").asText(), preview.path("message").asText(),
                preview.path("errorCode").isNull() ? null : preview.path("errorCode").asText(null),
                proposedFields(pending), preview.path("confidence").asDouble(0),
                "CONFIRMED".equals(pending.getStatus()), pending.getVersion(), List.copyOf(fieldIds),
                pending.getTargetModuleId(), pending.getTargetId(), preview.path("agentId").asLong());
    }

    private List<AiModuleFieldDraftModels.FieldCandidate> proposedFields(AiPendingWrite pending) {
        List<AiModuleFieldDraftModels.FieldCandidate> result = new ArrayList<>();
        for (JsonNode field : readTree(pending.getProposedPayloadJson()).path("fields")) {
            try {
                result.add(json.treeToValue(field, AiModuleFieldDraftModels.FieldCandidate.class));
            } catch (JsonProcessingException failure) {
                throw new IllegalStateException("Cannot read AI configuration candidate", failure);
            }
        }
        return List.copyOf(result);
    }

    private AiPendingWrite requirePending(AuthenticatedContext context, Long id, boolean lock) {
        AiPendingWrite pending = pendingWrites.selectList(Wrappers.<AiPendingWrite>lambdaQuery()
                .eq(AiPendingWrite::getId, id).last(lock ? "for update" : "limit 1"))
                .stream().findFirst().orElse(null);
        AiExecution execution = pending == null ? null : executions.selectById(pending.getExecutionId());
        AiConversation conversation = execution == null ? null : conversations.selectById(execution.getConversationId());
        String draftKind = pending == null ? "" : readTree(pending.getPreviewJson()).path("draftKind").asText("");
        boolean fieldDraft = "FIELD".equals(draftKind)
                || draftKind.isEmpty() && pending != null
                && readTree(pending.getProposedPayloadJson()).path("fields").isArray();
        if (pending == null || !"CONFIG_DRAFT".equals(pending.getTargetType()) || conversation == null
                || !fieldDraft
                || !context.systemId().equals(pending.getSystemId())
                || !context.tenantId().equals(pending.getTenantId())
                || !context.accountId().equals(conversation.getAccountId())) {
            throw new DomainException("AI_CONFIG_CANDIDATE_NOT_FOUND", "字段候选不存在", HttpStatus.NOT_FOUND);
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
            if ("CONFIG_DRAFT".equals(tool.path("toolType").asText())
                    && moduleCode.equals(tool.path("resourceId").asText())
                    && "CONFIGURE".equals(tool.path("actionCode").asText())
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
        if (!permissions.allows(context, "CONFIG", "MODULE", "MANAGE")
                || !permissions.allows(context, "AI", "SYSTEM", "VIEW")) {
            throw forbidden("AI_CONFIG_PERMISSION_DENIED", "当前成员没有配置 Agent 与模块管理权限");
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
        result.put("actionCode", "CONFIGURE");
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
            throw new IllegalStateException("Cannot read AI configuration snapshot", failure);
        }
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot persist AI configuration snapshot", failure);
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
}
