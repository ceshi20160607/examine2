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
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModulePage;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleFieldBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModulePageBaseService;
import com.unique.unexamine.moduleconfig.manage.ModuleConfigurationService;
import com.unique.unexamine.moduleconfig.manage.UpdatePageConfigurationRequest;
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
import java.util.stream.Collectors;

/** 配置 Agent 根据当前模块字段提出页面建议，人工确认后只更新模块页面草稿。 */
@Service
public class AiPageDraftService {
    private static final List<String> PAGE_TYPES = List.of("FORM", "LIST", "DETAIL");
    private static final Set<String> DENSITIES = Set.of("comfortable", "compact");

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
    private final ConfiguredModuleFieldBaseService fields;
    private final ConfiguredModulePageBaseService pages;
    private final ModuleConfigurationService moduleConfiguration;
    private final PermissionChecker permissions;
    private final AiModelGateway modelGateway;
    private final AuditRecorder audit;
    private final ObjectMapper json;

    public AiPageDraftService(AiAgentBaseService agents,
            AiAgentPublicationBaseService publications, AiAgentVersionBaseService versions,
            AiConversationBaseService conversations, AiMessageBaseService messages,
            AiExecutionBaseService executions, AiExecutionStepBaseService steps,
            AiPendingWriteBaseService pendingWrites, AiSystemModelGrantBaseService grants,
            AiModelBaseService models, ConfiguredModuleBaseService modules,
            ConfiguredModuleFieldBaseService fields, ConfiguredModulePageBaseService pages,
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
        this.fields = fields;
        this.pages = pages;
        this.moduleConfiguration = moduleConfiguration;
        this.permissions = permissions;
        this.modelGateway = modelGateway;
        this.audit = audit;
        this.json = json;
    }

    /** 只列当前系统、租户、模块可用且模型授权仍有效的页面建议 Agent。 */
    @Transactional(readOnly = true)
    public AiPageDraftModels.Options options(AuthenticatedContext context, Long moduleId) {
        requirePermission(context);
        ConfiguredModule module = requireModule(context, moduleId, false);
        List<AiPageDraftModels.AgentOption> result = new ArrayList<>();
        for (AiAgent agent : agents.selectList(Wrappers.<AiAgent>lambdaQuery()
                .eq(AiAgent::getSystemId, context.systemId())
                .eq(AiAgent::getOwnerTenantId, context.tenantId())
                .eq(AiAgent::getStatus, "PUBLISHED").orderByAsc(AiAgent::getName))) {
            PublishedAgent published = published(context, agent.getId(), false);
            if (published != null && tool(published.snapshot(), module.getCode()) != null
                    && grantAvailable(context, published.version())) {
                result.add(new AiPageDraftModels.AgentOption(agent.getId(), agent.getName()));
            }
        }
        return new AiPageDraftModels.Options(List.copyOf(result), module.getId(), module.getCode(),
                module.getName(), module.getDraftRevision());
    }

    /** 识别阶段仅保存 AI 候选、对话及执行记录，不修改页面草稿。 */
    @Transactional
    public AiPageDraftModels.CandidateView recognize(AuthenticatedContext context,
            AiPageDraftModels.RecognizeRequest input, String traceId) {
        requirePermission(context);
        ConfiguredModule module = requireModule(context, input.moduleId(), false);
        PublishedAgent published = published(context, input.agentId(), true);
        JsonNode selectedTool = tool(published.snapshot(), module.getCode());
        if (selectedTool == null) throw forbidden("AI_PAGE_TOOL_DENIED", "Agent 未获当前模块页面草稿授权");
        requireGrant(context, published.version());
        List<ConfiguredModuleField> activeFields = activeFields(module.getId());
        Map<String, ConfiguredModulePage> currentPages = requiredPages(module.getId());
        if (activeFields.isEmpty() || currentPages.size() != PAGE_TYPES.size()) {
            throw invalid("AI_PAGE_SOURCE_INVALID", "模块需要有效字段及表单、列表、详情草稿页面");
        }
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
                "moduleId", module.getId(), "moduleCode", module.getCode(), "actionCode", "DESIGN_PAGE")));
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
        step.setStepType("CONFIG_PAGE_RECOGNITION");
        step.setToolId(selectedTool.path("toolId").asLong());
        step.setInputJson(writeJson(Map.of("moduleId", module.getId(), "moduleCode", module.getCode(),
                "instruction", modelGateway.maskSensitive(instruction))));
        step.setOutputJson("{}");
        step.setStatus("RUNNING");
        step.setStartTime(LocalDateTime.now());
        steps.insert(step);

        List<AiPageDraftModels.PageCandidate> suggestions = List.of();
        String outcome = "READY";
        String status = "PENDING_CONFIRMATION";
        String errorCode = null;
        String notice = "已生成页面布局候选；确认前不会保存模块配置。";
        int promptTokens = 0;
        int completionTokens = 0;
        try {
            List<Map<String, Object>> fieldMetadata = activeFields.stream().map(field -> Map.<String, Object>of(
                    "code", field.getCode(), "name", field.getName(), "fieldType", field.getFieldType(),
                    "required", Boolean.TRUE.equals(field.getRequired()),
                    "searchable", Boolean.TRUE.equals(field.getSearchable()))).toList();
            String prompt = "请根据当前模块字段和管理员描述，生成表单、列表、详情页面的字段顺序与布局建议。仅输出 JSON："
                    + "{\"pages\":[{\"pageType\":\"FORM\",\"fieldCodes\":[\"code\"],\"title\":\"主要信息\","
                    + "\"columns\":2,\"density\":\"comfortable\",\"confidence\":0.9},"
                    + "{\"pageType\":\"LIST\",\"fieldCodes\":[\"code\"],\"title\":\"数据列表\","
                    + "\"columns\":1,\"density\":\"compact\",\"confidence\":0.9},"
                    + "{\"pageType\":\"DETAIL\",\"fieldCodes\":[\"code\"],\"title\":\"详细信息\","
                    + "\"columns\":2,\"density\":\"comfortable\",\"confidence\":0.9}]}。"
                    + "只能使用提供的 ACTIVE 主字段编码；每页至少一个、不重复，表单必须包含全部必填字段；"
                    + "columns 为 1 至 3，density 只能为 comfortable 或 compact；不要输出组件代码或其他文字。\n"
                    + "模块：" + module.getName() + "（" + module.getCode() + "）\n字段：" + writeJson(fieldMetadata)
                    + "\n管理员描述：" + instruction;
            AiModelGateway.Completion completion = modelGateway.complete(context,
                    published.version().getModelGrantId(),
                    published.snapshot().path("agent").path("systemPrompt").asText(), prompt, true,
                    published.snapshot().path("contextPolicy").path("allowExternalData").asBoolean(false));
            promptTokens = completion.promptTokens();
            completionTokens = completion.completionTokens();
            suggestions = parseCandidate(completion.text(), activeFields, currentPages);
        } catch (DomainException failure) {
            outcome = "DEGRADED";
            status = "NEEDS_MANUAL_INPUT";
            errorCode = failure.code();
            notice = "模型未生成可用页面候选：" + failure.getMessage();
        }
        double confidence = suggestions.stream().mapToDouble(AiPageDraftModels.PageCandidate::confidence)
                .average().orElse(0);
        LocalDateTime now = LocalDateTime.now();
        LinkedHashMap<String, Object> preview = new LinkedHashMap<>();
        preview.put("draftKind", "PAGE");
        preview.put("agentId", published.agent().getId());
        preview.put("agentVersionId", published.version().getId());
        preview.put("moduleId", module.getId());
        preview.put("moduleCode", module.getCode());
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
        pending.setProposedPayloadJson(writeJson(Map.of("pages", suggestions)));
        pending.setPreviewJson(writeJson(preview));
        pending.setStatus(status);
        pending.setErrorMessage(errorCode == null ? null : notice);
        pending.setCreateTime(now);
        pending.setUpdateTime(now);
        pending.setVersion(0);
        pendingWrites.insert(pending);
        message(conversation.getId(), "ASSISTANT", notice, Map.of("pages", suggestions),
                Map.of("modelGenerated", !suggestions.isEmpty(), "promptTokens", promptTokens,
                        "completionTokens", completionTokens), errorCode);
        step.setOutputJson(writeJson(Map.of("pendingWriteId", pending.getId(),
                "pageCount", suggestions.size(), "confidence", confidence)));
        step.setStatus(suggestions.isEmpty() ? "DEGRADED" : "SUCCEEDED");
        step.setFinishTime(now);
        step.setErrorMessage(errorCode == null ? null : notice);
        steps.updateById(step);
        execution.setStatus(suggestions.isEmpty() ? "DEGRADED" : "WAITING_CONFIRMATION");
        execution.setFallbackUsed(suggestions.isEmpty());
        execution.setFinishTime(suggestions.isEmpty() ? now : null);
        execution.setErrorCode(errorCode);
        execution.setErrorMessage(errorCode == null ? null : notice);
        executions.updateById(execution);
        audit.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "AI_CONFIG_PAGE_CANDIDATE_CREATED", "AI_PENDING_WRITE",
                String.valueOf(pending.getId()), outcome, authorization(context, module, selectedTool),
                Map.of("moduleId", module.getId(), "agentId", published.agent().getId(),
                        "executionId", execution.getId(), "pageCount", suggestions.size(),
                        "businessWritten", false));
        return view(pending);
    }

    @Transactional(readOnly = true)
    public AiPageDraftModels.CandidateView candidate(AuthenticatedContext context, Long pendingWriteId) {
        requirePermission(context);
        return view(requirePending(context, pendingWriteId, false));
    }

    /** 同一事务逐页调用现有页面服务；任一页面或版本失败都会回滚整组草稿。 */
    @Transactional
    public AiPageDraftModels.CandidateView confirm(AuthenticatedContext context, Long pendingWriteId,
            AiPageDraftModels.ConfirmRequest input, String traceId) {
        requirePermission(context);
        AiPendingWrite pending = requirePending(context, pendingWriteId, true);
        if (!"PENDING_CONFIRMATION".equals(pending.getStatus())) {
            throw conflict("AI_PAGE_CANDIDATE_NOT_PENDING", "页面候选已失效或已确认");
        }
        if (!Objects.equals(input.expectedVersion(), pending.getVersion())) {
            throw conflict("AI_PENDING_WRITE_VERSION_CONFLICT", "页面候选版本已变化，请刷新后重试");
        }
        AiExecution execution = executions.selectById(pending.getExecutionId());
        PublishedAgent published = published(context, execution.getAgentId(), true);
        if (!Objects.equals(published.version().getId(), execution.getAgentVersionId())) {
            throw conflict("AI_AGENT_VERSION_CHANGED", "Agent 已重新发布，请重新生成页面候选");
        }
        ConfiguredModule module = requireModule(context, pending.getTargetModuleId(), true);
        if (!Objects.equals(module.getDraftRevision(), pending.getTargetExpectedVersion())) {
            throw conflict("AI_PAGE_MODULE_VERSION_CONFLICT", "模块草稿已变化，请重新生成页面候选");
        }
        JsonNode selectedTool = tool(published.snapshot(), module.getCode());
        if (selectedTool == null) throw forbidden("AI_PAGE_TOOL_DENIED", "Agent 的页面草稿授权已失效");
        requireGrant(context, published.version());
        List<AiPageDraftModels.PageCandidate> proposal = proposedPages(pending);
        Map<String, AiPageDraftModels.PageCandidate> proposed = proposal.stream().collect(Collectors.toMap(
                AiPageDraftModels.PageCandidate::pageType, page -> page));
        Map<String, AiPageDraftModels.ConfirmPage> selected = new LinkedHashMap<>();
        for (AiPageDraftModels.ConfirmPage page : input.pages()) {
            if (!PAGE_TYPES.contains(page.pageType()) || selected.putIfAbsent(page.pageType(), page) != null) {
                throw invalid("AI_PAGE_SELECTION_INVALID", "必须分别确认表单、列表和详情页面");
            }
        }
        if (proposed.size() != PAGE_TYPES.size() || selected.size() != PAGE_TYPES.size()) {
            throw invalid("AI_PAGE_SELECTION_INVALID", "必须分别确认表单、列表和详情页面");
        }
        Set<String> reviewed = new HashSet<>(input.reviewedPageTypes());
        List<String> missingReview = proposal.stream().filter(page -> page.confidence() < 0.7)
                .map(AiPageDraftModels.PageCandidate::pageType)
                .filter(type -> !reviewed.contains(type)).toList();
        if (!missingReview.isEmpty()) {
            throw new DomainException("AI_PAGE_LOW_CONFIDENCE_REVIEW_REQUIRED",
                    "低可信度页面需要逐页补充或确认", HttpStatus.UNPROCESSABLE_ENTITY,
                    Map.of("pageTypes", missingReview));
        }
        List<ConfiguredModuleField> activeFields = activeFields(module.getId());
        Set<String> activeCodes = activeFields.stream().map(ConfiguredModuleField::getCode).collect(Collectors.toSet());
        Set<String> requiredCodes = activeFields.stream().filter(field -> Boolean.TRUE.equals(field.getRequired()))
                .map(ConfiguredModuleField::getCode).collect(Collectors.toSet());
        Map<String, ConfiguredModulePage> currentPages = requiredPages(module.getId());
        List<Long> resultPageIds = new ArrayList<>();
        for (String type : PAGE_TYPES) {
            AiPageDraftModels.ConfirmPage page = selected.get(type);
            ConfiguredModulePage current = currentPages.get(type);
            if (current == null || !Objects.equals(page.pageVersion(), current.getVersion())
                    || !Objects.equals(page.pageVersion(), proposed.get(type).pageVersion())) {
                throw conflict("AI_PAGE_VERSION_CONFLICT", "页面草稿已变化，请重新生成候选");
            }
            validateSelection(page, activeCodes, requiredCodes);
            ConfiguredModulePage saved = moduleConfiguration.updatePage(context, module.getId(), type,
                    new UpdatePageConfigurationRequest(json.valueToTree(layout(page)), page.pageVersion()), traceId);
            resultPageIds.add(saved.getId());
        }
        LocalDateTime now = LocalDateTime.now();
        pending.setStatus("CONFIRMED");
        pending.setConfirmedByAccountId(context.accountId());
        pending.setConfirmedByMemberId(context.memberId());
        pending.setConfirmTime(now);
        pending.setResultJson(writeJson(Map.of("resultPageIds", resultPageIds, "businessWritten", true,
                "confirmedPages", input.pages(), "reviewedPageTypes", input.reviewedPageTypes(),
                "moduleDraftRevision", modules.selectById(module.getId()).getDraftRevision(), "published", false)));
        pending.setErrorMessage(null);
        pending.setUpdateTime(now);
        if (pendingWrites.updateById(pending) != 1) {
            throw conflict("AI_PENDING_WRITE_VERSION_CONFLICT", "页面候选已被其他操作确认");
        }
        AiExecutionStep step = new AiExecutionStep();
        step.setExecutionId(execution.getId());
        step.setAgentId(execution.getAgentId());
        step.setStepNumber(2);
        step.setStepType("CONFIG_PAGE_CONFIRMED");
        step.setToolId(selectedTool.path("toolId").asLong());
        step.setInputJson(writeJson(Map.of("pendingWriteId", pendingWriteId,
                "confirmedPages", input.pages())));
        step.setOutputJson(writeJson(Map.of("resultPageIds", resultPageIds,
                "businessWritten", true, "published", false)));
        step.setStatus("SUCCEEDED");
        step.setStartTime(now);
        step.setFinishTime(now);
        steps.insert(step);
        message(execution.getConversationId(), "ASSISTANT", "已将确认页面保存到模块草稿，尚未发布。",
                Map.of("resultPageIds", resultPageIds), Map.of("modelGenerated", false), null);
        execution.setStatus("SUCCEEDED");
        execution.setFinishTime(now);
        execution.setErrorCode(null);
        execution.setErrorMessage(null);
        execution.setAuthorizationSnapshotJson(writeJson(authorization(context, module, selectedTool)));
        executions.updateById(execution);
        audit.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "AI_CONFIG_PAGE_DRAFT_CONFIRMED", "AI_PENDING_WRITE",
                String.valueOf(pendingWriteId), "SUCCESS", authorization(context, module, selectedTool),
                Map.of("moduleId", module.getId(), "resultPageIds", resultPageIds,
                        "agentId", execution.getAgentId(), "businessWritten", true, "published", false));
        return view(pendingWrites.selectById(pendingWriteId));
    }

    /** 只接受模型从当前 ACTIVE 主字段中挑选的三个标准页面。 */
    private List<AiPageDraftModels.PageCandidate> parseCandidate(String response,
            List<ConfiguredModuleField> activeFields, Map<String, ConfiguredModulePage> currentPages) {
        try {
            String content = response.strip();
            if (content.startsWith("```")) {
                int firstLine = content.indexOf('\n');
                int close = content.lastIndexOf("```");
                if (firstLine < 0 || close <= firstLine) throw new IllegalArgumentException();
                content = content.substring(firstLine + 1, close).strip();
            }
            JsonNode root = json.readTree(content);
            JsonNode suggested = root == null ? null : root.path("pages");
            if (suggested == null || !suggested.isArray() || suggested.size() != PAGE_TYPES.size()) {
                throw new IllegalArgumentException();
            }
            Set<String> activeCodes = activeFields.stream().map(ConfiguredModuleField::getCode).collect(Collectors.toSet());
            Set<String> requiredCodes = activeFields.stream().filter(field -> Boolean.TRUE.equals(field.getRequired()))
                    .map(ConfiguredModuleField::getCode).collect(Collectors.toSet());
            Map<String, AiPageDraftModels.PageCandidate> result = new LinkedHashMap<>();
            for (JsonNode item : suggested) {
                String type = item.path("pageType").asText("").strip().toUpperCase(Locale.ROOT);
                JsonNode codes = item.path("fieldCodes");
                String title = item.path("title").asText("").strip();
                JsonNode columnsNode = item.path("columns");
                String density = item.path("density").asText("").strip();
                double confidence = item.path("confidence").asDouble(0.6);
                if (!PAGE_TYPES.contains(type) || result.containsKey(type) || !codes.isArray()
                        || codes.isEmpty() || codes.size() > 50 || title.isBlank() || title.length() > 200
                        || !columnsNode.isIntegralNumber() || columnsNode.asInt() < 1 || columnsNode.asInt() > 3
                        || !DENSITIES.contains(density) || !Double.isFinite(confidence)
                        || confidence < 0 || confidence > 1) throw new IllegalArgumentException();
                List<String> fieldCodes = new ArrayList<>();
                for (JsonNode code : codes) {
                    if (!code.isTextual() || !activeCodes.contains(code.asText())
                            || fieldCodes.contains(code.asText())) throw new IllegalArgumentException();
                    fieldCodes.add(code.asText());
                }
                if ("FORM".equals(type) && !fieldCodes.containsAll(requiredCodes)) {
                    throw new IllegalArgumentException();
                }
                ConfiguredModulePage page = currentPages.get(type);
                if (page == null) throw new IllegalArgumentException();
                result.put(type, new AiPageDraftModels.PageCandidate(type, List.copyOf(fieldCodes), title,
                        columnsNode.asInt(), density, page.getVersion(), confidence));
            }
            if (result.size() != PAGE_TYPES.size()) throw new IllegalArgumentException();
            return PAGE_TYPES.stream().map(result::get).toList();
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw invalid("AI_PAGE_MODEL_OUTPUT_INVALID", "模型返回的页面字段或布局不符合当前模块配置");
        }
    }

    private void validateSelection(AiPageDraftModels.ConfirmPage page,
            Set<String> activeCodes, Set<String> requiredCodes) {
        if (!DENSITIES.contains(page.density()) || page.columns() < 1 || page.columns() > 3
                || page.fieldCodes().isEmpty() || page.fieldCodes().size() > 50
                || !activeCodes.containsAll(page.fieldCodes())
                || new HashSet<>(page.fieldCodes()).size() != page.fieldCodes().size()
                || "FORM".equals(page.pageType()) && !page.fieldCodes().containsAll(requiredCodes)) {
            throw invalid("AI_PAGE_SELECTION_INVALID", "页面字段需为当前有效字段，表单还须包含全部必填字段");
        }
    }

    private Map<String, Object> layout(AiPageDraftModels.ConfirmPage page) {
        Map<String, Object> component = Map.of("id", "main", "type", "LIST".equals(page.pageType()) ? "TABLE" : "SECTION",
                "title", page.title().strip(), "columns", page.columns(), "fieldCodes", page.fieldCodes(),
                "density", page.density());
        return Map.of("schemaVersion", 1, "layoutType", page.pageType(),
                "fieldCodes", page.fieldCodes(), "components", List.of(component));
    }

    private AiPageDraftModels.CandidateView view(AiPendingWrite pending) {
        JsonNode preview = readTree(pending.getPreviewJson());
        JsonNode result = readTree(pending.getResultJson());
        List<Long> ids = new ArrayList<>();
        result.path("resultPageIds").forEach(item -> ids.add(item.asLong()));
        return new AiPageDraftModels.CandidateView(pending.getId(), pending.getStatus(),
                preview.path("outcome").asText(), preview.path("message").asText(),
                preview.path("errorCode").isNull() ? null : preview.path("errorCode").asText(null),
                proposedPages(pending), preview.path("confidence").asDouble(0),
                "CONFIRMED".equals(pending.getStatus()), pending.getVersion(),
                pending.getTargetModuleId(), pending.getTargetId(), preview.path("agentId").asLong(),
                List.copyOf(ids));
    }

    private List<AiPageDraftModels.PageCandidate> proposedPages(AiPendingWrite pending) {
        List<AiPageDraftModels.PageCandidate> result = new ArrayList<>();
        for (JsonNode item : readTree(pending.getProposedPayloadJson()).path("pages")) {
            try {
                result.add(json.treeToValue(item, AiPageDraftModels.PageCandidate.class));
            } catch (JsonProcessingException failure) {
                throw new IllegalStateException("Cannot read AI page candidate", failure);
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
        if (pending == null || !"CONFIG_DRAFT".equals(pending.getTargetType()) || conversation == null
                || !"PAGE".equals(readTree(pending.getPreviewJson()).path("draftKind").asText())
                || !context.systemId().equals(pending.getSystemId())
                || !context.tenantId().equals(pending.getTenantId())
                || !context.accountId().equals(conversation.getAccountId())) {
            throw new DomainException("AI_PAGE_CANDIDATE_NOT_FOUND", "页面候选不存在", HttpStatus.NOT_FOUND);
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

    private List<ConfiguredModuleField> activeFields(Long moduleId) {
        return fields.selectList(Wrappers.<ConfiguredModuleField>lambdaQuery()
                .eq(ConfiguredModuleField::getModuleId, moduleId)
                .eq(ConfiguredModuleField::getStatus, "ACTIVE")
                .isNull(ConfiguredModuleField::getParentFieldId)
                .orderByAsc(ConfiguredModuleField::getSortOrder, ConfiguredModuleField::getId));
    }

    private Map<String, ConfiguredModulePage> requiredPages(Long moduleId) {
        Map<String, ConfiguredModulePage> result = new LinkedHashMap<>();
        for (ConfiguredModulePage page : pages.selectList(Wrappers.<ConfiguredModulePage>lambdaQuery()
                .eq(ConfiguredModulePage::getModuleId, moduleId)
                .in(ConfiguredModulePage::getPageType, PAGE_TYPES))) {
            if ("ACTIVE".equals(page.getStatus())) result.put(page.getPageType(), page);
        }
        return result;
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
                    && "DESIGN_PAGE".equals(item.path("actionCode").asText())
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
            throw forbidden("AI_PAGE_PERMISSION_DENIED", "当前成员没有配置 Agent 与模块管理权限");
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
        result.put("actionCode", "DESIGN_PAGE");
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
            throw new IllegalStateException("Cannot read AI page snapshot", failure);
        }
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot persist AI page snapshot", failure);
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
