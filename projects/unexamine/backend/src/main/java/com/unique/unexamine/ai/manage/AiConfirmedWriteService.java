package com.unique.unexamine.ai.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.TextNode;
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
import com.unique.unexamine.authorization.manage.ChannelFieldPolicyResolver;
import com.unique.unexamine.authorization.manage.FieldAccessDecision;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.moduleconfig.manage.ModulePublicationService;
import com.unique.unexamine.moduleconfig.manage.RuntimeModuleConfiguration;
import com.unique.unexamine.runtimedata.manage.CreateRuntimeRecordRequest;
import com.unique.unexamine.runtimedata.manage.RuntimeDataService;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordView;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class AiConfirmedWriteService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final TypeReference<List<AiConfirmedWriteModels.FieldCandidate>> FIELD_LIST_TYPE =
            new TypeReference<>() { };
    private static final Set<String> OPEN_STATUSES = Set.of(
            "PENDING_CONFIRMATION", "NEEDS_MANUAL_INPUT", "VALIDATION_FAILED");

    private final AiAgentBaseService agentService;
    private final AiAgentPublicationBaseService publicationService;
    private final AiAgentVersionBaseService versionService;
    private final AiConversationBaseService conversationService;
    private final AiMessageBaseService messageService;
    private final AiExecutionBaseService executionService;
    private final AiExecutionStepBaseService stepService;
    private final AiPendingWriteBaseService pendingWriteService;
    private final AiSystemModelGrantBaseService grantService;
    private final AiModelBaseService modelService;
    private final ModulePublicationService modulePublicationService;
    private final RuntimeDataService runtimeDataService;
    private final PermissionChecker permissionChecker;
    private final ChannelFieldPolicyResolver fieldPolicyResolver;
    private final PlatformAiConfigurationService platformConfigurationService;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public AiConfirmedWriteService(
            AiAgentBaseService agentService,
            AiAgentPublicationBaseService publicationService,
            AiAgentVersionBaseService versionService,
            AiConversationBaseService conversationService,
            AiMessageBaseService messageService,
            AiExecutionBaseService executionService,
            AiExecutionStepBaseService stepService,
            AiPendingWriteBaseService pendingWriteService,
            AiSystemModelGrantBaseService grantService,
            AiModelBaseService modelService,
            ModulePublicationService modulePublicationService,
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
        this.pendingWriteService = pendingWriteService;
        this.grantService = grantService;
        this.modelService = modelService;
        this.modulePublicationService = modulePublicationService;
        this.runtimeDataService = runtimeDataService;
        this.permissionChecker = permissionChecker;
        this.fieldPolicyResolver = fieldPolicyResolver;
        this.platformConfigurationService = platformConfigurationService;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AiConfirmedWriteModels.WriteOverview overview(AuthenticatedContext context) {
        requireSystem(context);
        List<AiConfirmedWriteModels.WriteAgentOption> agents = new ArrayList<>();
        for (AiAgent agent : agentService.selectList(Wrappers.<AiAgent>lambdaQuery()
                .eq(AiAgent::getSystemId, context.systemId())
                .eq(AiAgent::getOwnerTenantId, context.tenantId())
                .eq(AiAgent::getStatus, "PUBLISHED").orderByAsc(AiAgent::getName))) {
            AiAgentPublication publication = publicationService.selectList(
                            Wrappers.<AiAgentPublication>lambdaQuery().eq(AiAgentPublication::getAgentId, agent.getId()))
                    .stream().findFirst().orElse(null);
            AiAgentVersion version = publication == null ? null : versionService.selectById(publication.getCurrentVersionId());
            if (version == null) continue;
            List<String> modules = writeTools(readTree(version.getSnapshotJson())).stream()
                    .filter(tool -> "CREATE".equals(tool.actionCode()))
                    .map(WriteTool::moduleCode).distinct().sorted().toList();
            if (!modules.isEmpty()) agents.add(new AiConfirmedWriteModels.WriteAgentOption(
                    agent.getId(), agent.getCode(), agent.getName(), version.getId(),
                    version.getVersionNumber(), modules));
        }
        return new AiConfirmedWriteModels.WriteOverview(List.copyOf(agents));
    }

    @Transactional
    public AiConfirmedWriteModels.CandidateView recognize(
            AuthenticatedContext context, AiConfirmedWriteModels.RecognizeRequest input, String traceId) {
        requireSystem(context);
        String moduleCode = input.moduleCode().strip().toLowerCase(Locale.ROOT);
        String entryType = input.entryType().strip().toUpperCase(Locale.ROOT);
        PublishedAgent published = requirePublishedAgent(context, input.agentId());
        WriteTool tool = writeTools(published.snapshot()).stream()
                .filter(item -> moduleCode.equals(item.moduleCode()) && "CREATE".equals(item.actionCode()))
                .findFirst().orElse(null);

        AiConversation conversation = conversation(context, input, published, moduleCode, entryType);
        AiMessage requestMessage = message(conversation.getId(), "USER", input.inputText().strip(), Map.of(
                "sourceType", input.sourceType(),
                "sourceReference", input.sourceReference() == null ? "" : input.sourceReference(),
                "moduleCode", moduleCode,
                "entryType", entryType), null);
        AiExecution execution = execution(conversation, requestMessage, published.version());
        AiExecutionStep step = recognitionStep(execution, tool, input);

        String refusal = refusal(context, input, published.snapshot(), tool, moduleCode, entryType);
        SourceRecordContext sourceContext = null;
        if (refusal == null && "RECORD_DETAIL".equals(entryType)) {
            sourceContext = parseSourceRecordContext(
                    input.sourceReference() == null ? "" : input.sourceReference().strip(), context.systemId());
            if (sourceContext == null) {
                refusal = "已拒绝：当前业务记录上下文已失效；未识别字段，也未写入业务。";
            } else {
                try {
                    runtimeDataService.detail(context, sourceContext.moduleCode(), sourceContext.recordId(), traceId);
                } catch (DomainException exception) {
                    refusal = "已拒绝：当前业务记录无权访问或已失效；未识别字段，也未写入业务。";
                    sourceContext = null;
                }
            }
        }
        RuntimeModuleConfiguration configuration = null;
        List<FieldDefinition> definitions = List.of();
        if (refusal == null) {
            configuration = modulePublicationService.runtime(context, moduleCode, traceId);
            definitions = fieldDefinitions(context, moduleCode, configuration, tool);
        }
        ModelAvailability availability = refusal == null ? modelAvailability(context, published.snapshot())
                : new ModelAvailability(false, "请求未通过授权边界", "NONE");
        CandidateDraft draft = refusal == null && availability.available()
                ? recognizeDraft(input.inputText(), definitions)
                : manualDraft(input.inputText(), definitions);
        if (refusal == null && sourceContext != null) {
            draft = bindSourceRecord(context, draft, definitions, sourceContext);
        }

        String outcome;
        String status;
        String errorCode;
        String message;
        boolean retryable;
        if (refusal != null) {
            outcome = "REFUSED";
            status = "REFUSED";
            errorCode = "AI_WRITE_SCOPE_DENIED";
            message = refusal;
            retryable = false;
        } else if (!availability.available()) {
            outcome = "DEGRADED";
            status = "NEEDS_MANUAL_INPUT";
            errorCode = "AI_MODEL_UNAVAILABLE";
            message = "模型当前不可用：" + availability.reason()
                    + "。已保留原始输入，未生成候选、未写入业务；可人工填写确认表单。";
            retryable = true;
        } else {
            outcome = "READY";
            status = "PENDING_CONFIRMATION";
            errorCode = null;
            message = "已生成结构化候选；确认前不会写入任何业务数据。";
            retryable = false;
        }

        LocalDateTime now = LocalDateTime.now();
        LinkedHashMap<String, Object> proposed = proposedPayload(draft);
        LinkedHashMap<String, Object> preview = new LinkedHashMap<>();
        preview.put("agentId", published.agent().getId());
        preview.put("agentVersionId", published.version().getId());
        preview.put("conversationId", conversation.getId());
        preview.put("executionId", execution.getId());
        preview.put("moduleCode", moduleCode);
        preview.put("outcome", outcome);
        preview.put("errorCode", errorCode);
        preview.put("message", message);
        preview.put("retryable", retryable);
        preview.put("confirmationRequired", true);
        preview.put("sourceType", input.sourceType());
        preview.put("sourceReference", input.sourceReference() == null ? "" : input.sourceReference());
        preview.put("entryType", entryType);
        preview.put("inputText", input.inputText().strip());
        preview.put("fields", draft.fields());
        preview.put("unknownSegments", draft.unknownSegments());
        preview.put("publishedToolId", tool == null ? null : tool.toolId());
        preview.put("moduleVersionId", configuration == null ? null : configuration.versionId());
        preview.put("modelMode", availability.mode());

        AiPendingWrite pending = new AiPendingWrite();
        pending.setExecutionId(execution.getId());
        pending.setStepId(step.getId());
        pending.setTargetType("BUSINESS_RECORD_CREATE");
        pending.setTargetId(moduleCode);
        pending.setProposedPayloadJson(writeJson(proposed));
        pending.setPreviewJson(writeJson(preview));
        pending.setStatus(status);
        pending.setErrorMessage(errorCode == null ? null : message);
        pending.setCreatedAt(now);
        pending.setUpdatedAt(now);
        pending.setVersion(0);
        pendingWriteService.insert(pending);

        finishRecognition(context, traceId, execution, step, pending, published, tool, moduleCode,
                definitions, outcome, status, errorCode, message, availability);
        return candidateView(pendingWriteService.selectById(pending.getId()));
    }

    @Transactional(readOnly = true)
    public AiConfirmedWriteModels.CandidateView candidate(AuthenticatedContext context, Long pendingWriteId) {
        return candidateView(requirePending(context, pendingWriteId));
    }

    @Transactional
    public AiConfirmedWriteModels.WriteResult confirm(
            AuthenticatedContext context, Long pendingWriteId,
            AiConfirmedWriteModels.ConfirmRequest input, String traceId) {
        requireSystem(context);
        AiPendingWrite pending = requirePending(context, pendingWriteId);
        requireVersion(pending, input.expectedVersion());
        if (!Boolean.TRUE.equals(input.confirmed())) {
            throw new DomainException("AI_WRITE_CONFIRMATION_REQUIRED", "必须明确确认后才能写入",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!OPEN_STATUSES.contains(pending.getStatus())) {
            throw new DomainException("AI_WRITE_ALREADY_HANDLED", "候选已经处理，不能重复确认",
                    HttpStatus.CONFLICT);
        }

        AiExecution execution = executionService.selectById(pending.getExecutionId());
        AiConversation conversation = conversationService.selectById(execution.getConversationId());
        AiAgentVersion version = versionService.selectById(execution.getAgentVersionId());
        JsonNode snapshot = readTree(version.getSnapshotJson());
        String moduleCode = pending.getTargetId();
        WriteTool tool = writeTools(snapshot).stream()
                .filter(item -> moduleCode.equals(item.moduleCode()) && "CREATE".equals(item.actionCode()))
                .findFirst().orElseThrow(() -> new DomainException("AI_WRITE_TOOL_REVOKED",
                        "固定 Agent 版本没有可确认的写入工具", HttpStatus.CONFLICT));
        enforceWriteAuthorization(context, moduleCode, tool, input.fields().keySet(), traceId);
        SourceRecordContext sourceContext = requireSourceRecordContext(context, pending, traceId);

        CreateRuntimeRecordRequest createRequest = new CreateRuntimeRecordRequest(
                input.title().strip(), blankToNull(input.recordNumber()), blankToDefault(input.status(), "ACTIVE"),
                input.ownerMemberId(), input.departmentId(), input.participantMemberIds(), input.fields());
        try {
            runtimeDataService.validateImport(context, moduleCode, null, createRequest, null, traceId);
        } catch (DomainException exception) {
            pending.setStatus("VALIDATION_FAILED");
            pending.setErrorMessage(exception.getMessage());
            pending.setResultJson(writeJson(Map.of(
                    "errorCode", exception.code(), "message", exception.getMessage(),
                    "submittedPayload", confirmedPayload(input), "businessWritten", false)));
            pending.setUpdatedAt(LocalDateTime.now());
            pendingWriteService.updateById(pending);
            auditRecorder.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(),
                    context.tenantId(), context.memberId(), "AI_WRITE_VALIDATION_FAILED", "AI_PENDING_WRITE",
                    pending.getId().toString(), "FAILED", authorizationSnapshot(context, tool, moduleCode), Map.of(
                            "executionId", execution.getId(), "agentVersionId", version.getId(),
                            "moduleCode", moduleCode, "errorCode", exception.code(), "businessWritten", false));
            return new AiConfirmedWriteModels.WriteResult(pending.getId(), "VALIDATION_FAILED", "FAILED",
                    exception.code(), exception.getMessage(), null, null, Map.of(), false,
                    pending.getVersion(), null);
        }

        RuntimeRecordView record = runtimeDataService.create(context, moduleCode, createRequest, traceId);
        LocalDateTime confirmedAt = LocalDateTime.now();
        String recordPath = "/systems/" + context.systemId() + "?workspace=runtime&module=" + moduleCode
                + "&recordId=" + record.id();
        if (sourceContext != null) {
            String targetModuleName = modulePublicationService.published(context, moduleCode)
                    .configuration().path("module").path("name").asText(moduleCode);
            auditRecorder.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(),
                    context.tenantId(), context.memberId(), "AI_CONFIRMED_RELATED_RECORD_CREATED",
                    "BUSINESS_RECORD", sourceContext.recordId().toString(), "SUCCESS",
                    Map.of("sourceModuleCode", sourceContext.moduleCode(), "sourceRecordId", sourceContext.recordId(),
                            "targetModuleCode", moduleCode, "targetRecordId", record.id()),
                    Map.of("targetModuleCode", moduleCode, "targetModuleName", targetModuleName,
                            "targetRecordId", record.id(), "targetRecordTitle", record.title(),
                            "targetRecordPath", recordPath, "pendingWriteId", pending.getId()));
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("recordId", record.id());
        result.put("recordPath", recordPath);
        result.put("record", objectMapper.convertValue(record, MAP_TYPE));
        result.put("submittedPayload", confirmedPayload(input));
        result.put("candidateChanged", !sameCandidate(pending, input));
        result.put("businessWritten", true);
        result.put("confirmedByAccountId", context.accountId());
        result.put("confirmedAt", confirmedAt);
        pending.setStatus("CONFIRMED");
        pending.setConfirmedByAccountId(context.accountId());
        pending.setConfirmedAt(confirmedAt);
        pending.setResultJson(writeJson(result));
        pending.setErrorMessage(null);
        pending.setUpdatedAt(confirmedAt);
        pendingWriteService.updateById(pending);

        confirmationStep(execution, tool, pending, input, record, recordPath, confirmedAt);
        execution.setStatus("SUCCEEDED");
        execution.setFinishedAt(confirmedAt);
        execution.setErrorCode(null);
        execution.setErrorMessage(null);
        execution.setAuthorizationSnapshotJson(writeJson(authorizationSnapshot(context, tool, moduleCode)));
        executionService.updateById(execution);
        message(conversation.getId(), "ASSISTANT", "已由用户确认并写入真实业务记录 #" + record.id(),
                Map.of("pendingWriteId", pending.getId(), "recordId", record.id(), "recordPath", recordPath,
                        "businessWritten", true), null);
        conversation.setStatus("ACTIVE");
        conversationService.updateById(conversation);
        auditRecorder.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(),
                context.tenantId(), context.memberId(), "AI_WRITE_CONFIRMED", "AI_PENDING_WRITE",
                pending.getId().toString(), "SUCCESS", authorizationSnapshot(context, tool, moduleCode), Map.of(
                        "executionId", execution.getId(), "agentVersionId", version.getId(),
                        "moduleCode", moduleCode, "recordId", record.id(), "recordPath", recordPath,
                        "candidateChanged", result.get("candidateChanged"), "businessWritten", true));
        return new AiConfirmedWriteModels.WriteResult(pending.getId(), "CONFIRMED", "SUCCEEDED", null,
                "已确认并写入真实业务，结果可从目标模块读回。", record.id(), recordPath,
                objectMapper.convertValue(record, MAP_TYPE), true, pending.getVersion(), confirmedAt);
    }

    private SourceRecordContext requireSourceRecordContext(
            AuthenticatedContext context, AiPendingWrite pending, String traceId) {
        JsonNode preview = readTree(pending.getPreviewJson());
        if (!"RECORD_DETAIL".equals(preview.path("entryType").asText())) return null;
        SourceRecordContext source = parseSourceRecordContext(
                preview.path("sourceReference").asText("").strip(), context.systemId());
        if (source == null) {
            throw new DomainException("AI_WRITE_SOURCE_CONTEXT_INVALID",
                    "当前记录上下文已失效，请返回业务详情后重新生成候选", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        runtimeDataService.detail(context, source.moduleCode(), source.recordId(), traceId);
        return source;
    }

    private SourceRecordContext parseSourceRecordContext(String reference, Long expectedSystemId) {
        try {
            URI uri = URI.create(reference);
            String[] path = uri.getPath().split("/");
            if (path.length != 3 || !"systems".equals(path[1])
                    || !expectedSystemId.equals(Long.valueOf(path[2]))) return null;
            Map<String, String> query = new LinkedHashMap<>();
            for (String item : uri.getRawQuery() == null ? new String[0] : uri.getRawQuery().split("&")) {
                String[] pair = item.split("=", 2);
                if (pair.length == 2) query.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
            if (!"runtime".equals(query.get("workspace"))) return null;
            String moduleCode = query.get("module");
            String recordId = query.get("recordId");
            if (moduleCode == null || !moduleCode.matches("[a-z][a-z0-9_]{1,99}") || recordId == null) return null;
            return new SourceRecordContext(moduleCode, Long.valueOf(recordId));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Transactional
    public AiConfirmedWriteModels.WriteResult cancel(
            AuthenticatedContext context, Long pendingWriteId,
            AiConfirmedWriteModels.CancelRequest input, String traceId) {
        requireSystem(context);
        AiPendingWrite pending = requirePending(context, pendingWriteId);
        requireVersion(pending, input.expectedVersion());
        if (!OPEN_STATUSES.contains(pending.getStatus())) {
            throw new DomainException("AI_WRITE_ALREADY_HANDLED", "候选已经处理，不能重复取消",
                    HttpStatus.CONFLICT);
        }
        AiExecution execution = executionService.selectById(pending.getExecutionId());
        AiConversation conversation = conversationService.selectById(execution.getConversationId());
        pending.setStatus("CANCELLED");
        pending.setUpdatedAt(LocalDateTime.now());
        pending.setResultJson(writeJson(Map.of("businessWritten", false, "cancelledByAccountId", context.accountId())));
        pending.setErrorMessage(null);
        pendingWriteService.updateById(pending);
        execution.setStatus("CANCELLED");
        execution.setFinishedAt(LocalDateTime.now());
        execution.setErrorCode("AI_WRITE_CANCELLED");
        execution.setErrorMessage("用户取消确认，未写入业务");
        executionService.updateById(execution);
        message(conversation.getId(), "ASSISTANT", "已取消候选，未写入任何业务数据。",
                Map.of("pendingWriteId", pending.getId(), "businessWritten", false), "AI_WRITE_CANCELLED");
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "AI_WRITE_CANCELLED", "AI_PENDING_WRITE", pending.getId().toString(), "CANCELLED",
                Map.of("executionId", execution.getId(), "businessWritten", false));
        return new AiConfirmedWriteModels.WriteResult(pending.getId(), "CANCELLED", "CANCELLED",
                "AI_WRITE_CANCELLED", "已取消候选，未写入任何业务数据。", null, null, Map.of(),
                false, pending.getVersion(), null);
    }

    private void finishRecognition(
            AuthenticatedContext context, String traceId, AiExecution execution, AiExecutionStep step,
            AiPendingWrite pending, PublishedAgent published, WriteTool tool, String moduleCode,
            List<FieldDefinition> definitions, String outcome, String status, String errorCode,
            String responseMessage, ModelAvailability availability) {
        LocalDateTime now = LocalDateTime.now();
        step.setOutputJson(writeJson(Map.of(
                "outcome", outcome, "pendingWriteId", pending.getId(), "businessWritten", false,
                "recognizedFieldCount", definitions.size(), "confirmationRequired", true)));
        step.setStatus("REFUSED".equals(outcome) ? "REFUSED" : "SUCCEEDED");
        step.setFinishedAt(now);
        step.setErrorMessage(errorCode == null ? null : responseMessage);
        stepService.updateById(step);
        execution.setStatus("REFUSED".equals(outcome) ? "REFUSED" : "WAITING_CONFIRMATION");
        execution.setFallbackUsed("DEGRADED".equals(outcome));
        execution.setErrorCode(errorCode);
        execution.setErrorMessage(errorCode == null ? null : responseMessage);
        execution.setAuthorizationSnapshotJson(writeJson(authorizationSnapshot(context, tool, moduleCode)));
        if ("REFUSED".equals(outcome)) execution.setFinishedAt(now);
        executionService.updateById(execution);
        AiConfirmedWriteModels.CandidateView view = candidateView(pending);
        message(execution.getConversationId(), "ASSISTANT", responseMessage, view, errorCode);
        String event = "READY".equals(outcome) ? "AI_WRITE_CANDIDATE_READY"
                : "DEGRADED".equals(outcome) ? "AI_WRITE_CANDIDATE_DEGRADED" : "AI_WRITE_CANDIDATE_REFUSED";
        auditRecorder.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(),
                context.tenantId(), context.memberId(), event, "AI_PENDING_WRITE", pending.getId().toString(),
                outcome, authorizationSnapshot(context, tool, moduleCode), Map.of(
                        "executionId", execution.getId(), "agentVersionId", published.version().getId(),
                        "moduleCode", moduleCode, "status", status, "businessWritten", false,
                        "confirmationRequired", true, "modelMode", availability.mode()));
    }

    private AiConfirmedWriteModels.CandidateView candidateView(AiPendingWrite pending) {
        Map<String, Object> preview = readMap(pending.getPreviewJson());
        Map<String, Object> proposed = readMap(pending.getProposedPayloadJson());
        Map<String, Object> result = readMap(pending.getResultJson());
        List<AiConfirmedWriteModels.FieldCandidate> fields = objectMapper.convertValue(
                preview.getOrDefault("fields", List.of()), FIELD_LIST_TYPE);
        List<String> unknown = objectMapper.convertValue(preview.getOrDefault("unknownSegments", List.of()),
                new TypeReference<List<String>>() { });
        String status = pending.getStatus();
        String outcome = string(preview.get("outcome"));
        String errorCode = stringOrNull(preview.get("errorCode"));
        String responseMessage = string(preview.get("message"));
        boolean retryable = booleanValue(preview.get("retryable"));
        if ("CONFIRMED".equals(status)) {
            outcome = "SUCCEEDED";
            errorCode = null;
            responseMessage = "候选已确认并写入真实业务，结果可从目标模块读回。";
            retryable = false;
        } else if ("CANCELLED".equals(status)) {
            outcome = "CANCELLED";
            errorCode = "AI_WRITE_CANCELLED";
            responseMessage = "候选已取消，未写入任何业务数据。";
            retryable = false;
        } else if ("VALIDATION_FAILED".equals(status)) {
            outcome = "FAILED";
            errorCode = stringOrNull(result.get("errorCode"));
            responseMessage = stringOrNull(result.get("message"));
            retryable = false;
        }
        return new AiConfirmedWriteModels.CandidateView(
                pending.getId(), longValue(preview.get("conversationId")), longValue(preview.get("executionId")),
                longValue(preview.get("agentId")), longValue(preview.get("agentVersionId")),
                string(preview.get("moduleCode")), outcome, status, errorCode, responseMessage,
                retryable, true, "CONFIRMED".equals(status),
                string(preview.get("sourceType")), stringOrNull(preview.get("sourceReference")),
                string(preview.get("inputText")), string(proposed.get("title")),
                stringOrNull(proposed.get("recordNumber")), string(proposed.get("status")), fields, unknown,
                longValue(result.get("recordId")), stringOrNull(result.get("recordPath")), pending.getConfirmedAt(),
                pending.getVersion(), pending.getCreatedAt(), pending.getUpdatedAt());
    }

    private CandidateDraft recognizeDraft(String input, List<FieldDefinition> definitions) {
        Map<String, FieldDefinition> aliases = new LinkedHashMap<>();
        definitions.forEach(field -> {
            aliases.put(normalizeKey(field.code()), field);
            aliases.put(normalizeKey(field.label()), field);
        });
        LinkedHashMap<String, JsonNode> values = new LinkedHashMap<>();
        LinkedHashMap<String, Double> confidences = new LinkedHashMap<>();
        LinkedHashMap<String, String> builtIns = new LinkedHashMap<>();
        List<String> unknown = new ArrayList<>();
        for (String raw : input.split("[\\r\\n;；]+")) {
            String segment = raw.strip();
            if (segment.isBlank()) continue;
            int separator = separator(segment);
            if (separator < 1) {
                unknown.add(segment);
                continue;
            }
            String key = segment.substring(0, separator).strip();
            String value = segment.substring(separator + 1).strip();
            String normalized = normalizeKey(key);
            String builtIn = builtIn(normalized);
            if (builtIn != null) {
                builtIns.put(builtIn, value);
                continue;
            }
            FieldDefinition field = aliases.get(normalized);
            if (field == null || !field.writable()) {
                unknown.add(segment);
                continue;
            }
            values.put(field.code(), typedValue(field.fieldType(), value));
            confidences.put(field.code(), normalizeKey(field.code()).equals(normalized) ? 0.99 : 0.95);
        }
        List<AiConfirmedWriteModels.FieldCandidate> fields = definitions.stream().map(field -> {
            JsonNode value = values.get(field.code());
            boolean recognized = value != null;
            return new AiConfirmedWriteModels.FieldCandidate(field.code(), field.label(), field.fieldType(),
                    field.required(), field.writable(), value, confidences.getOrDefault(field.code(), 0.0), recognized,
                    !field.writable() ? "当前用户不可写" : field.required() && !recognized ? "必填字段待补充"
                            : recognized ? "请确认识别值" : "未从输入识别，可人工填写");
        }).toList();
        String title = builtIns.get("title");
        if (title == null || title.isBlank()) {
            title = fields.stream().filter(AiConfirmedWriteModels.FieldCandidate::recognized)
                    .map(item -> item.value().asText()).findFirst().orElse("AI 待确认记录");
        }
        return new CandidateDraft(title, blankToNull(builtIns.get("recordNumber")),
                blankToDefault(builtIns.get("status"), "ACTIVE"), fields, unknown);
    }

    private CandidateDraft manualDraft(String input, List<FieldDefinition> definitions) {
        List<AiConfirmedWriteModels.FieldCandidate> fields = definitions.stream().map(field ->
                new AiConfirmedWriteModels.FieldCandidate(field.code(), field.label(), field.fieldType(),
                        field.required(), field.writable(), null, 0.0, false,
                        field.writable() ? "模型不可用，请人工填写" : "当前用户不可写")).toList();
        return new CandidateDraft("AI 待人工填写记录", null, "ACTIVE", fields,
                input.isBlank() ? List.of() : List.of(input.strip()));
    }

    private CandidateDraft bindSourceRecord(
            AuthenticatedContext context, CandidateDraft draft,
            List<FieldDefinition> definitions, SourceRecordContext source) {
        Long sourceModuleId = modulePublicationService.published(context, source.moduleCode()).moduleId();
        Map<String, FieldDefinition> byCode = definitions.stream()
                .collect(java.util.stream.Collectors.toMap(FieldDefinition::code, field -> field));
        List<AiConfirmedWriteModels.FieldCandidate> fields = draft.fields().stream().map(field -> {
            FieldDefinition definition = byCode.get(field.code());
            if (definition == null || !definition.writable() || !"REFERENCE".equals(definition.fieldType())
                    || !Objects.equals(sourceModuleId, definition.referenceModuleId())) return field;
            return new AiConfirmedWriteModels.FieldCandidate(field.code(), field.label(), field.fieldType(),
                    field.required(), true, new LongNode(source.recordId()), 1.0, true,
                    "已自动关联当前业务记录");
        }).toList();
        return new CandidateDraft(draft.title(), draft.recordNumber(), draft.status(), fields, draft.unknownSegments());
    }

    private List<FieldDefinition> fieldDefinitions(
            AuthenticatedContext context, String moduleCode, RuntimeModuleConfiguration configuration, WriteTool tool) {
        Map<String, FieldAccessDecision> decisions = fieldPolicyResolver.resolveIntersection(
                context, moduleCode, "CREATE", List.of("PAGE"), tool.fieldCodes());
        List<FieldDefinition> fields = new ArrayList<>();
        for (JsonNode field : configuration.configuration().path("fields")) {
            String code = field.path("code").asText();
            if (!tool.fieldCodes().contains(code) || !"ACTIVE".equals(field.path("status").asText("ACTIVE"))) continue;
            FieldAccessDecision decision = decisions.get(code);
            fields.add(new FieldDefinition(code, field.path("name").asText(code),
                    field.path("fieldType").asText("TEXT"), field.path("required").asBoolean(false),
                    decision != null && decision.writable(),
                    field.path("referenceModuleId").isNumber() ? field.path("referenceModuleId").longValue() : null));
        }
        return List.copyOf(fields);
    }

    private void enforceWriteAuthorization(
            AuthenticatedContext context, String moduleCode, WriteTool tool, Set<String> fields, String traceId) {
        if (!tool.requiresConfirmation()) {
            throw new DomainException("AI_WRITE_CONFIRMATION_POLICY_INVALID", "写入工具未启用逐次人工确认",
                    HttpStatus.CONFLICT);
        }
        if (!permissionChecker.allows(context, "MODULE", moduleCode, "CREATE")) {
            throw new DomainException("AI_WRITE_SCOPE_DENIED", "当前用户没有目标模块新建权限",
                    HttpStatus.FORBIDDEN);
        }
        List<String> excessive = fields.stream().filter(field -> !tool.fieldCodes().contains(field)).sorted().toList();
        if (!excessive.isEmpty()) {
            throw new DomainException("AI_WRITE_FIELD_SCOPE_DENIED", "确认字段超出 Agent 写入范围",
                    HttpStatus.FORBIDDEN, Map.of("fieldCodes", excessive));
        }
        Map<String, FieldAccessDecision> decisions = fieldPolicyResolver.resolveIntersection(
                context, moduleCode, "CREATE", List.of("PAGE"), fields.stream().sorted().toList());
        List<String> denied = fields.stream().filter(field -> {
            FieldAccessDecision decision = decisions.get(field);
            return decision == null || !decision.writable();
        }).sorted().toList();
        if (!denied.isEmpty()) {
            throw new DomainException("AI_WRITE_FIELD_SCOPE_DENIED", "当前用户没有确认字段的写权限",
                    HttpStatus.FORBIDDEN, Map.of("fieldCodes", denied));
        }
    }

    private String refusal(
            AuthenticatedContext context, AiConfirmedWriteModels.RecognizeRequest input, JsonNode snapshot,
            WriteTool tool, String moduleCode, String entryType) {
        if (!context.tenantId().equals(input.requestedTenantId())) {
            return "已拒绝：目标租户不是当前租户；未识别字段，也未写入业务。";
        }
        if (!entryAllowed(snapshot, entryType)) {
            return "已拒绝：当前 Agent 未获权从这个入口创建业务。";
        }
        if (tool == null) return "已拒绝：已发布 Agent 没有目标模块 CREATE 写入工具。";
        if (!tool.requiresConfirmation()
                || !snapshot.path("confirmationPolicy").path("writeActionsRequireConfirmation").asBoolean(false)) {
            return "已拒绝：写入工具或 Agent 未强制逐次人工确认。";
        }
        if (!permissionChecker.allows(context, "MODULE", moduleCode, "CREATE")) {
            return "已拒绝：当前用户没有目标模块新建权限。";
        }
        return null;
    }

    private List<WriteTool> writeTools(JsonNode snapshot) {
        List<WriteTool> result = new ArrayList<>();
        for (JsonNode tool : snapshot.path("tools")) {
            if (!"WRITE".equals(tool.path("toolType").asText())) continue;
            List<String> fields = new ArrayList<>();
            tool.path("fieldAuthorization").fieldNames().forEachRemaining(fields::add);
            result.add(new WriteTool(tool.path("toolId").asLong(), tool.path("resourceId").asText(),
                    tool.path("actionCode").asText(), fields.stream().distinct().sorted().toList(),
                    tool.path("requiresConfirmation").asBoolean(false)));
        }
        return result;
    }

    private PublishedAgent requirePublishedAgent(AuthenticatedContext context, Long agentId) {
        AiAgent agent = agentService.selectById(agentId);
        if (agent == null || !context.systemId().equals(agent.getSystemId())
                || !context.tenantId().equals(agent.getOwnerTenantId()) || !"PUBLISHED".equals(agent.getStatus())) {
            throw new DomainException("AI_AGENT_NOT_AVAILABLE", "当前租户没有这个已发布 Agent", HttpStatus.NOT_FOUND);
        }
        AiAgentPublication publication = publicationService.selectList(
                        Wrappers.<AiAgentPublication>lambdaQuery().eq(AiAgentPublication::getAgentId, agentId))
                .stream().findFirst().orElse(null);
        AiAgentVersion version = publication == null ? null : versionService.selectById(publication.getCurrentVersionId());
        if (version == null || !agentId.equals(version.getAgentId())) {
            throw new DomainException("AI_AGENT_NOT_AVAILABLE", "Agent 没有可执行的已发布版本", HttpStatus.NOT_FOUND);
        }
        return new PublishedAgent(agent, version, readTree(version.getSnapshotJson()));
    }

    private AiPendingWrite requirePending(AuthenticatedContext context, Long pendingWriteId) {
        AiPendingWrite pending = pendingWriteService.selectById(pendingWriteId);
        AiExecution execution = pending == null ? null : executionService.selectById(pending.getExecutionId());
        AiConversation conversation = execution == null ? null : conversationService.selectById(execution.getConversationId());
        if (pending == null || conversation == null || !context.systemId().equals(conversation.getSystemId())
                || !context.tenantId().equals(conversation.getTenantId())
                || !context.accountId().equals(conversation.getAccountId())) {
            throw new DomainException("AI_PENDING_WRITE_NOT_FOUND", "AI 待确认写入不存在", HttpStatus.NOT_FOUND);
        }
        return pending;
    }

    private void requireVersion(AiPendingWrite pending, Integer expectedVersion) {
        if (!Objects.equals(expectedVersion, pending.getVersion())) {
            throw new DomainException("AI_PENDING_WRITE_VERSION_CONFLICT", "待确认候选已被其他操作修改",
                    HttpStatus.CONFLICT);
        }
    }

    private AiConversation conversation(
            AuthenticatedContext context, AiConfirmedWriteModels.RecognizeRequest input,
            PublishedAgent published, String moduleCode, String entryType) {
        AiConversation conversation = new AiConversation();
        conversation.setSystemId(context.systemId());
        conversation.setTenantId(context.tenantId());
        conversation.setAgentId(published.agent().getId());
        conversation.setAgentVersionId(published.version().getId());
        conversation.setAccountId(context.accountId());
        conversation.setEntryContextJson(writeJson(Map.of(
                "entryType", entryType, "moduleCode", moduleCode, "sourceType", input.sourceType(),
                "sourceReference", input.sourceReference() == null ? "" : input.sourceReference(),
                "actionCode", "CREATE")));
        String title = input.inputText().strip();
        conversation.setTitle(title.length() > 120 ? title.substring(0, 120) : title);
        conversation.setStatus("ACTIVE");
        conversation.setVersion(0);
        conversationService.insert(conversation);
        return conversation;
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

    private AiExecutionStep recognitionStep(
            AiExecution execution, WriteTool tool, AiConfirmedWriteModels.RecognizeRequest input) {
        AiExecutionStep step = new AiExecutionStep();
        step.setExecutionId(execution.getId());
        step.setStepNumber(1);
        step.setStepType("STRUCTURED_RECOGNITION");
        step.setToolId(tool == null || tool.toolId() == 0 ? null : tool.toolId());
        step.setInputJson(writeJson(Map.of(
                "sourceType", input.sourceType(),
                "sourceReference", input.sourceReference() == null ? "" : input.sourceReference(),
                "inputText", input.inputText().strip(), "moduleCode", input.moduleCode())));
        step.setOutputJson("{}");
        step.setStatus("RUNNING");
        step.setStartedAt(LocalDateTime.now());
        stepService.insert(step);
        return step;
    }

    private void confirmationStep(
            AiExecution execution, WriteTool tool, AiPendingWrite pending,
            AiConfirmedWriteModels.ConfirmRequest input, RuntimeRecordView record,
            String recordPath, LocalDateTime confirmedAt) {
        AiExecutionStep step = new AiExecutionStep();
        step.setExecutionId(execution.getId());
        step.setStepNumber(2);
        step.setStepType("CONFIRMED_WRITE");
        step.setToolId(tool.toolId() == 0 ? null : tool.toolId());
        step.setInputJson(writeJson(Map.of(
                "pendingWriteId", pending.getId(), "confirmed", true,
                "confirmedPayload", confirmedPayload(input))));
        step.setOutputJson(writeJson(Map.of(
                "recordId", record.id(), "recordPath", recordPath, "businessWritten", true)));
        step.setStatus("SUCCEEDED");
        step.setStartedAt(confirmedAt);
        step.setFinishedAt(confirmedAt);
        stepService.insert(step);
    }

    private AiMessage message(Long conversationId, String role, String content, Object structured, String errorCode) {
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setRoleType(role);
        message.setContentText(content);
        message.setStructuredContentJson(structured == null ? null : writeJson(structured));
        message.setModelUsageJson(writeJson(Map.of("fabricated", false)));
        message.setErrorCode(errorCode);
        message.setCreatedAt(LocalDateTime.now());
        messageService.insert(message);
        return message;
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
                    ? new ModelAvailability(true, null, "LOCAL_SECURE_RECOGNITION")
                    : new ModelAvailability(false, "模型端点未配置", "UNAVAILABLE");
        }
        try {
            URI uri = URI.create(endpoint);
            int port = uri.getPort() > 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(uri.getHost(), port), 700);
            }
            return new ModelAvailability(true, null, "MODEL_GATEWAY_SECURE_RECOGNITION");
        } catch (Exception exception) {
            return new ModelAvailability(false, "模型端点连接失败", "UNAVAILABLE");
        }
    }

    private Map<String, Object> authorizationSnapshot(
            AuthenticatedContext context, WriteTool tool, String moduleCode) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("accountId", context.accountId());
        snapshot.put("memberId", context.memberId());
        snapshot.put("systemId", context.systemId());
        snapshot.put("tenantId", context.tenantId());
        snapshot.put("roleIds", context.roleIds());
        snapshot.put("permissions", context.permissions());
        snapshot.put("dataScopes", context.dataScopes());
        snapshot.put("publishedToolId", tool == null ? null : tool.toolId());
        snapshot.put("target", Map.of("resourceType", "MODULE", "moduleCode", moduleCode,
                "actionCode", "CREATE", "fieldCodes", tool == null ? List.of() : tool.fieldCodes(),
                "requiresConfirmation", tool != null && tool.requiresConfirmation()));
        return snapshot;
    }

    private LinkedHashMap<String, Object> proposedPayload(CandidateDraft draft) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        draft.fields().stream().filter(AiConfirmedWriteModels.FieldCandidate::recognized)
                .forEach(field -> fields.put(field.code(), field.value()));
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", draft.title());
        payload.put("recordNumber", draft.recordNumber());
        payload.put("status", draft.status());
        payload.put("participantMemberIds", List.of());
        payload.put("fields", fields);
        return payload;
    }

    private Map<String, Object> confirmedPayload(AiConfirmedWriteModels.ConfirmRequest input) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", input.title());
        payload.put("recordNumber", input.recordNumber());
        payload.put("status", input.status());
        payload.put("ownerMemberId", input.ownerMemberId());
        payload.put("departmentId", input.departmentId());
        payload.put("participantMemberIds", input.participantMemberIds());
        payload.put("fields", input.fields());
        return payload;
    }

    private boolean sameCandidate(AiPendingWrite pending, AiConfirmedWriteModels.ConfirmRequest input) {
        Map<String, Object> proposed = readMap(pending.getProposedPayloadJson());
        return Objects.equals(string(proposed.get("title")), input.title())
                && Objects.equals(stringOrNull(proposed.get("recordNumber")), blankToNull(input.recordNumber()))
                && Objects.equals(string(proposed.get("status")), blankToDefault(input.status(), "ACTIVE"))
                && Objects.equals(objectMapper.valueToTree(proposed.get("fields")), objectMapper.valueToTree(input.fields()));
    }

    private JsonNode typedValue(String fieldType, String value) {
        try {
            return switch (fieldType) {
                case "NUMBER", "DECIMAL" -> new DoubleNode(Double.parseDouble(value));
                case "INTEGER", "MEMBER", "DEPARTMENT", "RELATION", "CASCADE" ->
                        new LongNode(Long.parseLong(value));
                case "BOOLEAN" -> BooleanNode.valueOf(Set.of("true", "1", "是", "启用").contains(value.toLowerCase(Locale.ROOT)));
                default -> TextNode.valueOf(value);
            };
        } catch (NumberFormatException exception) {
            return TextNode.valueOf(value);
        }
    }

    private int separator(String segment) {
        int result = -1;
        for (char candidate : new char[] {':', '：', '='}) {
            int index = segment.indexOf(candidate);
            if (index > 0 && (result < 0 || index < result)) result = index;
        }
        return result;
    }

    private String builtIn(String key) {
        if (Set.of("title", "标题").contains(key)) return "title";
        if (Set.of("recordnumber", "记录编号", "编号").contains(key)) return "recordNumber";
        if (Set.of("status", "状态").contains(key)) return "status";
        return null;
    }

    private boolean entryAllowed(JsonNode snapshot, String entryType) {
        Set<String> allowed = new LinkedHashSet<>();
        snapshot.path("contextPolicy").path("allowedEntryContexts").forEach(item -> allowed.add(item.asText()));
        if (allowed.contains(entryType)) return true;
        return Set.of("SYSTEM_AI", "RIGHT_ASSISTANT").contains(entryType) && allowed.contains("MODULE_PAGE");
    }

    private void requireSystem(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统租户上下文", HttpStatus.CONFLICT);
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read AI agent version", exception);
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read AI write payload", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot persist AI write payload", exception);
        }
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String stringOrNull(Object value) {
        String text = string(value);
        return text.isBlank() ? null : text;
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private record PublishedAgent(AiAgent agent, AiAgentVersion version, JsonNode snapshot) {
    }

    private record WriteTool(Long toolId, String moduleCode, String actionCode,
                             List<String> fieldCodes, boolean requiresConfirmation) {
    }

    private record FieldDefinition(String code, String label, String fieldType,
                                   boolean required, boolean writable, Long referenceModuleId) {
    }

    private record CandidateDraft(String title, String recordNumber, String status,
                                  List<AiConfirmedWriteModels.FieldCandidate> fields,
                                  List<String> unknownSegments) {
    }

    private record ModelAvailability(boolean available, String reason, String mode) {
    }

    private record SourceRecordContext(String moduleCode, Long recordId) {
    }
}
