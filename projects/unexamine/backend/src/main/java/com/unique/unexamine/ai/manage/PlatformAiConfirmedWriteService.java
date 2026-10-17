package com.unique.unexamine.ai.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.ai.base.entity.AiAgent;
import com.unique.unexamine.ai.base.entity.AiAgentPublication;
import com.unique.unexamine.ai.base.entity.AiAgentVersion;
import com.unique.unexamine.ai.base.entity.AiConversation;
import com.unique.unexamine.ai.base.entity.AiExecution;
import com.unique.unexamine.ai.base.entity.AiExecutionStep;
import com.unique.unexamine.ai.base.entity.AiMessage;
import com.unique.unexamine.ai.base.entity.AiModel;
import com.unique.unexamine.ai.base.entity.AiPendingWrite;
import com.unique.unexamine.ai.base.service.AiAgentBaseService;
import com.unique.unexamine.ai.base.service.AiAgentPublicationBaseService;
import com.unique.unexamine.ai.base.service.AiAgentVersionBaseService;
import com.unique.unexamine.ai.base.service.AiConversationBaseService;
import com.unique.unexamine.ai.base.service.AiExecutionBaseService;
import com.unique.unexamine.ai.base.service.AiExecutionStepBaseService;
import com.unique.unexamine.ai.base.service.AiMessageBaseService;
import com.unique.unexamine.ai.base.service.AiModelBaseService;
import com.unique.unexamine.ai.base.service.AiPendingWriteBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.notification.base.entity.PlatMsgTemplate;
import com.unique.unexamine.notification.base.service.PlatMsgTemplateBaseService;
import com.unique.unexamine.notification.manage.MessageModels;
import com.unique.unexamine.notification.manage.MessageService;
import com.unique.unexamine.platform.base.entity.PlatformMember;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.platform.base.service.PlatformMemberBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.work.manage.WorkLogModels;
import com.unique.unexamine.work.manage.WorkLogService;
import com.unique.unexamine.work.manage.WorkManagementModels;
import com.unique.unexamine.work.manage.WorkManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 平台 Agent 只提议平台工作数据；用户确认后调用原有业务服务完成保存。 */
@Service
public class PlatformAiConfirmedWriteService {
    private static final Map<String, Set<String>> FIELDS = Map.of(
            "PLATFORM_TASK", Set.of("title", "description", "priority", "ownerAccountId", "dueTime"),
            "PLATFORM_MESSAGE", Set.of("templateCode", "recipientMemberIds", "variables"),
            "PLATFORM_LOG", Set.of("workDate", "title", "completedText", "nextPlanText"));
    private static final Map<String, String> CAPABILITIES = Map.of(
            "PLATFORM_TASK", "PLATFORM_TASK", "PLATFORM_MESSAGE", "PLATFORM_MESSAGE",
            "PLATFORM_LOG", "PLATFORM_LOG");
    private static final Set<String> SYSTEM_PAYLOAD_KEYS = Set.of("systemId", "tenantId",
            "moduleId", "moduleCode", "recordId", "recordNumber", "childRows",
            "businessFields", "systemBusinessData", "targetModuleId", "bizRecord");

    private final AiAgentBaseService agents;
    private final AiAgentPublicationBaseService publications;
    private final AiAgentVersionBaseService versions;
    private final AiModelBaseService models;
    private final AiConversationBaseService conversations;
    private final AiMessageBaseService messages;
    private final AiExecutionBaseService executions;
    private final AiExecutionStepBaseService steps;
    private final AiPendingWriteBaseService pendingWrites;
    private final PlatformMemberBaseService platformMembers;
    private final PlatformAccountBaseService platformAccounts;
    private final PlatMsgTemplateBaseService messageTemplates;
    private final WorkManagementService tasks;
    private final WorkLogService logs;
    private final MessageService inbox;
    private final PermissionChecker permissions;
    private final AiModelGateway modelGateway;
    private final AuditRecorder audit;
    private final ObjectMapper json;

    public PlatformAiConfirmedWriteService(AiAgentBaseService agents,
            AiAgentPublicationBaseService publications, AiAgentVersionBaseService versions,
            AiModelBaseService models, AiConversationBaseService conversations,
            AiMessageBaseService messages, AiExecutionBaseService executions,
            AiExecutionStepBaseService steps, AiPendingWriteBaseService pendingWrites,
            PlatformMemberBaseService platformMembers, PlatformAccountBaseService platformAccounts,
            PlatMsgTemplateBaseService messageTemplates,
            WorkManagementService tasks, WorkLogService logs, MessageService inbox,
            PermissionChecker permissions, AiModelGateway modelGateway,
            AuditRecorder audit, ObjectMapper json) {
        this.agents = agents;
        this.publications = publications;
        this.versions = versions;
        this.models = models;
        this.conversations = conversations;
        this.messages = messages;
        this.executions = executions;
        this.steps = steps;
        this.pendingWrites = pendingWrites;
        this.platformMembers = platformMembers;
        this.platformAccounts = platformAccounts;
        this.messageTemplates = messageTemplates;
        this.tasks = tasks;
        this.logs = logs;
        this.inbox = inbox;
        this.permissions = permissions;
        this.modelGateway = modelGateway;
        this.audit = audit;
        this.json = json;
    }

    /** 消息接收人与模板均来自当前平台，页面无需填写数据库主键或 JSON。 */
    @Transactional(readOnly = true)
    public PlatformAiConfirmedWriteModels.WriteOptions options(AuthenticatedContext context) {
        requirePlatform(context);
        List<PlatformAiConfirmedWriteModels.MemberOption> members = platformMembers.selectList(
                        Wrappers.<PlatformMember>lambdaQuery().eq(PlatformMember::getPlatformId, context.platformId())
                                .eq(PlatformMember::getStatus, "ACTIVE"))
                .stream().map(member -> {
                    var account = platformAccounts.selectById(member.getAccountId());
                    if (account == null || !"ACTIVE".equals(account.getStatus())) return null;
                    return new PlatformAiConfirmedWriteModels.MemberOption(member.getId(), account.getId(),
                            account.getDisplayName() == null || account.getDisplayName().isBlank()
                                    ? account.getUsername() : account.getDisplayName());
                }).filter(Objects::nonNull).toList();
        List<PlatformAiConfirmedWriteModels.TemplateOption> templates = List.of();
        if (permissions.allows(context, "MESSAGE", "PLATFORM", "SEND")
                || permissions.allows(context, "MESSAGE", "PLATFORM", "MANAGE")
                || permissions.allows(context, "MESSAGE", "*", "SEND")
                || permissions.allows(context, "MESSAGE", "*", "MANAGE")) {
            templates = messageTemplates.selectList(Wrappers.<PlatMsgTemplate>lambdaQuery()
                            .eq(PlatMsgTemplate::getPlatformId, context.platformId())
                            .eq(PlatMsgTemplate::getStatus, "PUBLISHED").orderByAsc(PlatMsgTemplate::getCode))
                    .stream().map(template -> new PlatformAiConfirmedWriteModels.TemplateOption(
                            template.getCode(), template.getName(), templateRequiredVariables(template)))
                    .toList();
        }
        Long currentMemberId = members.stream().filter(item -> item.accountId().equals(context.accountId()))
                .map(PlatformAiConfirmedWriteModels.MemberOption::memberId).findFirst().orElse(null);
        return new PlatformAiConfirmedWriteModels.WriteOptions(context.accountId(), currentMemberId,
                members, templates);
    }

    /** 识别只写 AI 候选和日志，不创建任务、消息或工作日志。 */
    @Transactional
    public PlatformAiConfirmedWriteModels.CandidateView recognize(AuthenticatedContext context,
            PlatformAiConfirmedWriteModels.RecognizeRequest input, String traceId) {
        requirePlatform(context);
        String target = target(input.targetType());
        AiAgentVersion version = published(context, input.agentId(), target);
        requireBusinessPermission(context, target);
        String source = input.inputText().strip();
        if (mentionsSystemBusiness(source)) {
            audit.recordFailure(traceId, context.accountId(), null, null, null,
                    "AI_PLATFORM_WRITE_RECOGNIZE", "AI_AGENT", input.agentId().toString(),
                    "AI_PLATFORM_SYSTEM_WRITE_DENIED", Map.of("targetType", target));
            throw invalid("AI_PLATFORM_SYSTEM_WRITE_DENIED", "系统业务请先切换到目标系统处理", HttpStatus.FORBIDDEN);
        }
        AiModel model = models.selectById(version.getModelId());
        if (model == null || !"PLATFORM".equals(model.getScopeType())
                || !Objects.equals(model.getPlatformId(), context.platformId())) {
            throw invalid("AI_PLATFORM_MODEL_UNAVAILABLE", "平台模型不可用", HttpStatus.SERVICE_UNAVAILABLE);
        }
        AiConversation conversation = new AiConversation();
        conversation.setContextType("PLATFORM");
        conversation.setPlatformId(context.platformId());
        conversation.setAgentId(input.agentId());
        conversation.setAgentVersionId(version.getId());
        conversation.setAccountId(context.accountId());
        conversation.setPlatformMemberAccountId(context.accountId());
        conversation.setEntryContextJson(writeJson(Map.of("entryType", "PLATFORM_HOME", "targetType", target)));
        conversation.setTitle(source.substring(0, Math.min(source.length(), 100)));
        conversation.setStatus("ACTIVE");
        conversation.setVersion(0);
        conversations.insert(conversation);
        AiMessage userMessage = message(conversation.getId(), "USER", source, null, null);
        AiExecution execution = new AiExecution();
        execution.setConversationId(conversation.getId());
        execution.setContextType("PLATFORM");
        execution.setPlatformId(context.platformId());
        execution.setAgentId(input.agentId());
        execution.setRequestMessageId(userMessage.getId());
        execution.setAgentVersionId(version.getId());
        execution.setModelId(model.getId());
        execution.setModelConfigVersion(model.getVersion());
        execution.setCredentialRef(model.getCredentialRef());
        execution.setCredentialVersion(model.getCredentialVersion());
        execution.setPolicyVersion(version.getPolicyVersion());
        execution.setAuthorizationSnapshotJson(writeJson(Map.of("accountId", context.accountId(),
                "targetType", target, "systemBusinessData", false)));
        execution.setPolicySnapshotJson(writeJson(Map.of("requiresConfirmation", true,
                "denySystemBusinessData", true, "agentVersionId", version.getId())));
        execution.setStatus("RUNNING");
        execution.setFallbackUsed(false);
        execution.setVersion(0);
        executions.insert(execution);

        AiExecutionStep step = new AiExecutionStep();
        step.setExecutionId(execution.getId());
        step.setAgentId(input.agentId());
        step.setStepNumber(1);
        step.setStepType("RECOGNIZE");
        step.setInputJson(writeJson(Map.of("targetType", target, "inputLength", source.length())));
        step.setStatus("RUNNING");
        steps.insert(step);
        ObjectNode fields = json.createObjectNode();
        double confidence = 0;
        String outcome = "READY";
        String errorCode = null;
        int promptTokens = 0;
        int completionTokens = 0;
        try {
            AiModelGateway.Completion completion = modelGateway.completePlatform(context, model.getId(),
                    "仅提取平台" + target + "的候选字段。只输出 JSON 对象：{\"fields\":{...},\"confidence\":0到1}。"
                            + "允许字段：" + String.join(",", FIELDS.get(target))
                            + "。缺失字段保持缺失，不得猜测账号、模板、日期或系统模块。"
                            + readJson(version.getSnapshotJson()).path("systemPrompt").asText(), source);
            JsonNode extracted = readJson(stripCodeFence(completion.text()));
            JsonNode proposed = extracted.path("fields");
            if (!proposed.isObject()) throw new IllegalArgumentException("No fields object");
            requireAllowedFields(target, proposed);
            proposed.fields().forEachRemaining(entry -> fields.set(entry.getKey(), entry.getValue()));
            confidence = Math.max(0, Math.min(1, extracted.path("confidence").asDouble(0)));
            promptTokens = completion.promptTokens();
            completionTokens = completion.completionTokens();
            if (fields.isEmpty() || confidence < 0.7) outcome = "NEEDS_MANUAL_INPUT";
        } catch (DomainException failure) {
            outcome = "NEEDS_MANUAL_INPUT";
            errorCode = failure.code();
        } catch (RuntimeException failure) {
            outcome = "NEEDS_MANUAL_INPUT";
            errorCode = "AI_PLATFORM_RECOGNITION_INVALID";
        }
        String message = "READY".equals(outcome)
                ? "已生成候选。请检查每个字段，再明确确认保存。"
                : "识别结果不足，请手动补全字段后再确认；当前没有写入业务数据。";
        AiPendingWrite pending = new AiPendingWrite();
        pending.setExecutionId(execution.getId());
        pending.setStepId(step.getId());
        pending.setContextType("PLATFORM");
        pending.setPlatformId(context.platformId());
        pending.setTargetType(target);
        pending.setProposedPayloadJson(writeJson(fields));
        pending.setPreviewJson(writeJson(Map.of("confidence", confidence,
                "outcome", outcome, "message", message, "agentVersionId", version.getId())));
        pending.setStatus("READY".equals(outcome) ? "PENDING_CONFIRMATION" : "NEEDS_MANUAL_INPUT");
        pending.setErrorMessage(errorCode == null ? null : message);
        pending.setVersion(0);
        pendingWrites.insert(pending);
        message(conversation.getId(), "ASSISTANT", message, writeJson(fields), errorCode);
        step.setOutputJson(writeJson(Map.of("pendingWriteId", pending.getId(), "confidence", confidence,
                "outcome", outcome, "promptTokens", promptTokens, "completionTokens", completionTokens)));
        step.setStatus("READY".equals(outcome) ? "SUCCEEDED" : "FAILED");
        step.setFinishTime(LocalDateTime.now());
        step.setErrorMessage(errorCode);
        steps.updateById(step);
        execution.setStatus("READY".equals(outcome) ? "SUCCEEDED" : "FAILED");
        execution.setFallbackUsed(!"READY".equals(outcome));
        execution.setFinishTime(LocalDateTime.now());
        execution.setErrorCode(errorCode);
        execution.setErrorMessage(errorCode == null ? null : message);
        executions.updateById(execution);
        audit.record(traceId, context.accountId(), null, null, null,
                "AI_PLATFORM_WRITE_RECOGNIZED", "AI_PENDING_WRITE", pending.getId().toString(),
                outcome, Map.of("targetType", target, "agentId", input.agentId(),
                        "agentVersionId", version.getId(), "businessWritten", false));
        return view(pending);
    }

    @Transactional(readOnly = true)
    public PlatformAiConfirmedWriteModels.CandidateView candidate(AuthenticatedContext context, Long id) {
        return view(owned(context, id));
    }

    /** 确认时重新检查发布版本和本人权限，再由已有 manage 业务服务保存。 */
    @Transactional
    public PlatformAiConfirmedWriteModels.CandidateView confirm(AuthenticatedContext context,
            Long id, PlatformAiConfirmedWriteModels.ConfirmRequest input, String traceId) {
        AiPendingWrite pending = owned(context, id);
        if (!Boolean.TRUE.equals(input.confirmed())) {
            throw invalid("AI_WRITE_CONFIRMATION_REQUIRED", "请明确确认本次平台写入", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!Set.of("PENDING_CONFIRMATION", "NEEDS_MANUAL_INPUT").contains(pending.getStatus())) {
            throw invalid("AI_WRITE_ALREADY_HANDLED", "候选已经处理，请重新识别", HttpStatus.CONFLICT);
        }
        if (!Objects.equals(input.expectedVersion(), pending.getVersion())) {
            throw invalid("AI_WRITE_VERSION_CONFLICT", "候选已变化，请刷新后确认", HttpStatus.CONFLICT);
        }
        String target = target(pending.getTargetType());
        JsonNode fields = input.fields();
        requireAllowedFields(target, fields);
        // 低置信度候选必须由用户重新核对并选择，不能仅凭一次普通确认直接落库。
        if (readJson(pending.getPreviewJson()).path("confidence").asDouble(0) < 0.7
                && !Boolean.TRUE.equals(input.reviewedLowConfidence())) {
            throw invalid("AI_WRITE_LOW_CONFIDENCE_REVIEW_REQUIRED", "请重新核对低置信度字段后再确认",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        AiExecution execution = executions.selectById(pending.getExecutionId());
        AiAgentVersion version = published(context, execution.getAgentId(), target);
        if (!Objects.equals(version.getId(), execution.getAgentVersionId())) {
            throw invalid("AI_AGENT_PUBLICATION_CHANGED", "Agent 发布版本已变化，请重新识别", HttpStatus.CONFLICT);
        }
        requireBusinessPermission(context, target);
        Long resultId;
        String resultPath;
        try {
            switch (target) {
                case "PLATFORM_TASK" -> {
                    WorkManagementModels.TaskView result = tasks.createOrdinaryTask(context,
                            new WorkManagementModels.CreateTaskRequest(null, null,
                                    requiredText(fields, "title", 500), optionalText(fields, "description", 20000),
                                    optionalText(fields, "priority", 32) == null ? "NORMAL"
                                            : optionalText(fields, "priority", 32),
                                    null, optionalLong(fields, "ownerAccountId", context.accountId()),
                                    null, optionalDateTime(fields, "dueTime"), List.of(), List.of(), List.of(),
                                    List.of(), null, List.of(), List.of(), null, null, null, Map.of()), traceId);
                    resultId = result.id();
                    resultPath = "/platform/tasks?taskId=" + resultId;
                }
                case "PLATFORM_MESSAGE" -> {
                    List<Long> recipients = requiredLongList(fields, "recipientMemberIds");
                    MessageModels.MessageView result = inbox.send(context,
                            new MessageModels.SendEventRequest(requiredText(fields, "templateCode", 100),
                                    "AI_AGENT", "AI_PENDING_" + id,
                                    recipients.stream().map(accountId -> new MessageModels.RecipientRef(
                                            "PLATFORM_MEMBER", accountId)).toList(),
                                    variables(fields), null, null, null, "NORMAL"), traceId);
                    resultId = result.id();
                    resultPath = "/platform/messages?messageId=" + resultId;
                }
                case "PLATFORM_LOG" -> {
                    WorkLogModels.LogView result = logs.create(context,
                            new WorkLogModels.CreateLogRequest(requiredDate(fields, "workDate"),
                                    requiredText(fields, "title", 500),
                                    optionalText(fields, "completedText", 20000), null,
                                    optionalText(fields, "nextPlanText", 20000), null, null,
                                    null, null, List.of(), null, null, null, "SELF", List.of(),
                                    List.of(), Map.of()), traceId);
                    resultId = result.id();
                    resultPath = "/platform/tasks?workTab=logs";
                }
                default -> throw invalid("AI_PLATFORM_WRITE_TARGET_DENIED", "不支持的平台写入", HttpStatus.FORBIDDEN);
            }
        } catch (DomainException failure) {
            audit.recordFailure(traceId, context.accountId(), null, null, null,
                    "AI_PLATFORM_WRITE_CONFIRM_FAILED", "AI_PENDING_WRITE", id.toString(),
                    failure.code(), Map.of("targetType", target, "businessWritten", false));
            throw failure;
        }
        LocalDateTime now = LocalDateTime.now();
        pending.setStatus("CONFIRMED");
        pending.setConfirmedByAccountId(context.accountId());
        pending.setConfirmedByPlatformMemberAccountId(context.accountId());
        pending.setConfirmTime(now);
        pending.setResultJson(writeJson(Map.of("resultId", resultId, "resultPath", resultPath,
                "fields", fields, "confirmedByAccountId", context.accountId(), "businessWritten", true)));
        pending.setErrorMessage(null);
        if (pendingWrites.updateById(pending) != 1) {
            throw invalid("AI_WRITE_VERSION_CONFLICT", "候选已变化，请刷新后确认", HttpStatus.CONFLICT);
        }
        AiExecutionStep step = new AiExecutionStep();
        step.setExecutionId(execution.getId());
        step.setAgentId(execution.getAgentId());
        step.setStepNumber(2);
        step.setStepType("CONFIRM_WRITE");
        step.setInputJson(writeJson(Map.of("targetType", target, "confirmedByAccountId", context.accountId())));
        step.setOutputJson(writeJson(Map.of("resultId", resultId, "resultPath", resultPath)));
        step.setStatus("SUCCEEDED");
        step.setFinishTime(now);
        steps.insert(step);
        execution.setStatus("SUCCEEDED");
        execution.setFinishTime(now);
        execution.setErrorCode(null);
        execution.setErrorMessage(null);
        executions.updateById(execution);
        message(execution.getConversationId(), "ASSISTANT", "已确认并创建平台数据 #" + resultId,
                writeJson(Map.of("pendingWriteId", id, "resultPath", resultPath, "businessWritten", true)), null);
        audit.record(traceId, context.accountId(), null, null, null,
                "AI_PLATFORM_WRITE_CONFIRMED", target, resultId.toString(), "SUCCESS",
                Map.of("pendingWriteId", id, "executionId", execution.getId(),
                        "agentVersionId", version.getId(), "resultPath", resultPath,
                        "businessWritten", true));
        return view(pending);
    }

    private AiPendingWrite owned(AuthenticatedContext context, Long id) {
        requirePlatform(context);
        AiPendingWrite pending = pendingWrites.selectById(id);
        if (pending == null || !"PLATFORM".equals(pending.getContextType())
                || !Objects.equals(pending.getPlatformId(), context.platformId())
                || pending.getSystemId() != null || pending.getTargetModuleId() != null) {
            throw invalid("AI_PLATFORM_WRITE_NOT_FOUND", "平台候选不存在", HttpStatus.NOT_FOUND);
        }
        AiExecution execution = executions.selectById(pending.getExecutionId());
        AiConversation conversation = execution == null ? null : conversations.selectById(execution.getConversationId());
        if (conversation == null || !Objects.equals(conversation.getAccountId(), context.accountId())
                || !"PLATFORM".equals(conversation.getContextType())) {
            throw invalid("AI_PLATFORM_WRITE_NOT_FOUND", "平台候选不存在", HttpStatus.NOT_FOUND);
        }
        return pending;
    }

    private AiAgentVersion published(AuthenticatedContext context, Long agentId, String target) {
        AiAgent agent = agents.selectById(agentId);
        if (agent == null || !"PLATFORM".equals(agent.getContextType())
                || !Objects.equals(agent.getPlatformId(), context.platformId())) {
            throw invalid("AI_AGENT_NOT_FOUND", "平台 Agent 不存在", HttpStatus.NOT_FOUND);
        }
        AiAgentPublication publication = publications.selectList(Wrappers.<AiAgentPublication>lambdaQuery()
                .eq(AiAgentPublication::getAgentId, agentId)).stream().findFirst().orElse(null);
        AiAgentVersion version = publication == null ? null : versions.selectById(publication.getCurrentVersionId());
        if (version == null || !"PLATFORM".equals(version.getContextType())
                || !Objects.equals(version.getPlatformId(), context.platformId())) {
            throw invalid("AI_AGENT_UNPUBLISHED", "平台 Agent 尚未发布", HttpStatus.CONFLICT);
        }
        boolean allowed = false;
        for (JsonNode capability : readJson(version.getSnapshotJson()).path("capabilities")) {
            if (CAPABILITIES.get(target).equals(capability.asText())) allowed = true;
        }
        if (!allowed) throw invalid("AI_PLATFORM_TOOL_DENIED", "Agent 未授权该平台资源", HttpStatus.FORBIDDEN);
        return version;
    }

    private void requireBusinessPermission(AuthenticatedContext context, String target) {
        boolean allowed = switch (target) {
            case "PLATFORM_TASK" -> permissions.allows(context, "WORK", "PLATFORM", "CREATE_TASK")
                    || permissions.allows(context, "WORK", "*", "CREATE_TASK");
            case "PLATFORM_LOG" -> permissions.allows(context, "WORK", "PLATFORM", "CREATE_LOG")
                    || permissions.allows(context, "WORK", "*", "CREATE_LOG");
            case "PLATFORM_MESSAGE" -> permissions.allows(context, "MESSAGE", "PLATFORM", "SEND")
                    || permissions.allows(context, "MESSAGE", "PLATFORM", "MANAGE")
                    || permissions.allows(context, "MESSAGE", "*", "SEND")
                    || permissions.allows(context, "MESSAGE", "*", "MANAGE");
            default -> false;
        };
        if (!allowed) throw invalid("AI_PLATFORM_WRITE_PERMISSION_DENIED", "没有创建该平台数据的权限", HttpStatus.FORBIDDEN);
    }

    private void requireAllowedFields(String target, JsonNode fields) {
        if (fields == null || !fields.isObject()) {
            throw invalid("AI_PLATFORM_WRITE_FIELDS_INVALID", "候选字段格式无效", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        fields.fieldNames().forEachRemaining(key -> {
            if (!FIELDS.get(target).contains(key)) {
                throw invalid("AI_PLATFORM_SYSTEM_WRITE_DENIED", "平台写入不能携带系统或未授权字段",
                        HttpStatus.FORBIDDEN);
            }
        });
        if (fields.size() > FIELDS.get(target).size()) {
            throw invalid("AI_PLATFORM_WRITE_FIELDS_INVALID", "候选字段格式无效", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        rejectSystemPayload(fields);
    }

    private void rejectSystemPayload(JsonNode node) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (SYSTEM_PAYLOAD_KEYS.contains(entry.getKey())) {
                    throw invalid("AI_PLATFORM_SYSTEM_WRITE_DENIED", "平台写入不能携带系统业务模块内容",
                            HttpStatus.FORBIDDEN);
                }
                rejectSystemPayload(entry.getValue());
            });
        } else if (node.isArray()) {
            node.forEach(this::rejectSystemPayload);
        }
    }

    private PlatformAiConfirmedWriteModels.CandidateView view(AiPendingWrite pending) {
        JsonNode preview = readJson(pending.getPreviewJson());
        JsonNode result = pending.getResultJson() == null ? json.createObjectNode() : readJson(pending.getResultJson());
        AiExecution execution = executions.selectById(pending.getExecutionId());
        return new PlatformAiConfirmedWriteModels.CandidateView(pending.getId(), execution.getConversationId(),
                execution.getId(), pending.getTargetType(), pending.getStatus(),
                preview.path("outcome").asText(), preview.path("message").asText(),
                execution.getErrorCode(), readJson(pending.getProposedPayloadJson()),
                preview.path("confidence").asDouble(0), "CONFIRMED".equals(pending.getStatus()),
                pending.getVersion(), result.path("resultId").isNumber() ? result.path("resultId").asLong() : null,
                result.path("resultPath").asText(null), pending.getConfirmTime());
    }

    private AiMessage message(Long conversationId, String role, String text, String structured, String errorCode) {
        AiMessage item = new AiMessage();
        item.setConversationId(conversationId);
        item.setRoleType(role);
        item.setContentText(text);
        item.setStructuredContentJson(structured);
        item.setErrorCode(errorCode);
        messages.insert(item);
        return item;
    }

    private String target(String raw) {
        String target = raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT);
        if (!FIELDS.containsKey(target)) throw invalid("AI_PLATFORM_WRITE_TARGET_DENIED",
                "平台 Agent 只可创建任务、消息和工作日志", HttpStatus.FORBIDDEN);
        return target;
    }

    private void requirePlatform(AuthenticatedContext context) {
        if (context == null || context.platformId() == null || context.systemId() != null) {
            throw invalid("AI_PLATFORM_CONTEXT_REQUIRED", "请返回平台工作区", HttpStatus.FORBIDDEN);
        }
    }

    private boolean mentionsSystemBusiness(String text) {
        return text.matches("(?s).*(系统|业务模块).*(业务数据|业务记录|字段|订单|库存|创建|新增|写入|保存|录入).*")
                || text.matches("(?s).*(业务数据|业务记录|订单|库存).*(系统|业务模块).*");
    }

    private String requiredText(JsonNode fields, String key, int max) {
        String value = optionalText(fields, key, max);
        if (value == null) throw invalid("AI_PLATFORM_WRITE_FIELD_REQUIRED", "请补全" + key,
                HttpStatus.UNPROCESSABLE_ENTITY);
        return value;
    }

    private String optionalText(JsonNode fields, String key, int max) {
        JsonNode node = fields.path(key);
        if (node.isMissingNode() || node.isNull()) return null;
        if (!node.isTextual() || node.asText().strip().length() > max) {
            throw invalid("AI_PLATFORM_WRITE_FIELD_INVALID", key + "格式无效", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String value = node.asText().strip();
        return value.isBlank() ? null : value;
    }

    private Long optionalLong(JsonNode fields, String key, Long fallback) {
        JsonNode node = fields.path(key);
        if (node.isMissingNode() || node.isNull()) return fallback;
        if (!node.isIntegralNumber() || node.asLong() <= 0) {
            throw invalid("AI_PLATFORM_WRITE_FIELD_INVALID", key + "格式无效", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return node.asLong();
    }

    private LocalDate requiredDate(JsonNode fields, String key) {
        try {
            String value = requiredText(fields, key, 10);
            return LocalDate.parse(value);
        } catch (DateTimeParseException failure) {
            throw invalid("AI_PLATFORM_WRITE_FIELD_INVALID", key + "须为 YYYY-MM-DD",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private LocalDateTime optionalDateTime(JsonNode fields, String key) {
        String value = optionalText(fields, key, 30);
        if (value == null) return null;
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException failure) {
            throw invalid("AI_PLATFORM_WRITE_FIELD_INVALID", key + "须为本地日期时间",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private List<Long> requiredLongList(JsonNode fields, String key) {
        JsonNode node = fields.path(key);
        if (!node.isArray() || node.isEmpty() || node.size() > 20) {
            throw invalid("AI_PLATFORM_WRITE_FIELD_INVALID", "请选择 1 至 20 个平台消息接收人",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        List<Long> result = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isIntegralNumber() || item.asLong() <= 0) {
                throw invalid("AI_PLATFORM_WRITE_FIELD_INVALID", "接收人账号无效", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (!result.contains(item.asLong())) result.add(item.asLong());
        }
        return result;
    }

    private Map<String, Object> variables(JsonNode fields) {
        JsonNode node = fields.path("variables");
        if (node.isMissingNode() || node.isNull()) return Map.of();
        if (!node.isObject() || node.size() > 30) {
            throw invalid("AI_PLATFORM_WRITE_FIELD_INVALID", "模板变量无效", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            if (!entry.getKey().matches("[a-z][a-zA-Z0-9_]{0,99}")
                    || !entry.getValue().isValueNode() || entry.getValue().asText().length() > 2000) {
                throw invalid("AI_PLATFORM_WRITE_FIELD_INVALID", "模板变量无效", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            result.put(entry.getKey(), entry.getValue().asText());
        });
        return result;
    }

    private List<String> templateRequiredVariables(PlatMsgTemplate template) {
        JsonNode required = readJson(template.getVariableSchemaJson()).path("required");
        if (!required.isArray()) return List.of();
        List<String> names = new ArrayList<>();
        required.forEach(value -> names.add(value.asText()));
        return names;
    }

    private JsonNode readJson(String value) {
        try {
            return json.readTree(value);
        } catch (Exception failure) {
            throw new IllegalStateException("Invalid platform Agent JSON", failure);
        }
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot serialize platform Agent JSON", failure);
        }
    }

    private String stripCodeFence(String value) {
        String text = value.strip();
        if (text.startsWith("```")) {
            int firstLine = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) text = text.substring(firstLine + 1, lastFence).strip();
        }
        return text;
    }

    private DomainException invalid(String code, String message, HttpStatus status) {
        return new DomainException(code, message, status);
    }
}
