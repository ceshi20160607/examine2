package com.unique.unexamine.notification.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.flow.base.entity.FlowInstance;
import com.unique.unexamine.flow.base.service.FlowInstanceBaseService;
import com.unique.unexamine.notification.base.entity.MsgDelivery;
import com.unique.unexamine.notification.base.entity.MsgMessage;
import com.unique.unexamine.notification.base.entity.MsgRecipient;
import com.unique.unexamine.notification.base.entity.MsgTemplate;
import com.unique.unexamine.notification.base.entity.MsgTemplateVersion;
import com.unique.unexamine.notification.base.service.MsgDeliveryBaseService;
import com.unique.unexamine.notification.base.service.MsgMessageBaseService;
import com.unique.unexamine.notification.base.service.MsgRecipientBaseService;
import com.unique.unexamine.notification.base.service.MsgTemplateBaseService;
import com.unique.unexamine.notification.base.service.MsgTemplateVersionBaseService;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.entity.PlatformMember;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.platform.base.service.PlatformMemberBaseService;
import com.unique.unexamine.runtimedata.manage.RuntimeDataService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import com.unique.unexamine.work.base.entity.WorkTask;
import com.unique.unexamine.work.base.service.WorkTaskBaseService;
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
public class MessageService {
    private static final Set<String> INBOX_STATUSES = Set.of("ACTIVE", "UNREAD", "READ", "ARCHIVED", "ALL");
    private static final int MAX_DELIVERY_ATTEMPTS = 3;

    private final MsgTemplateBaseService templateService;
    private final MsgTemplateVersionBaseService templateVersionService;
    private final MsgMessageBaseService messageService;
    private final MsgRecipientBaseService recipientService;
    private final MsgDeliveryBaseService deliveryService;
    private final PlatformAccountBaseService accountService;
    private final PlatformMemberBaseService platformMemberService;
    private final SystemMemberBaseService memberService;
    private final SystemTenantMemberBaseService tenantMemberService;
    private final FlowInstanceBaseService flowInstanceService;
    private final WorkTaskBaseService workTaskService;
    private final RuntimeDataService runtimeDataService;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public MessageService(
            MsgTemplateBaseService templateService,
            MsgTemplateVersionBaseService templateVersionService,
            MsgMessageBaseService messageService,
            MsgRecipientBaseService recipientService,
            MsgDeliveryBaseService deliveryService,
            PlatformAccountBaseService accountService,
            PlatformMemberBaseService platformMemberService,
            SystemMemberBaseService memberService,
            SystemTenantMemberBaseService tenantMemberService,
            FlowInstanceBaseService flowInstanceService,
            WorkTaskBaseService workTaskService,
            RuntimeDataService runtimeDataService,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.templateService = templateService;
        this.templateVersionService = templateVersionService;
        this.messageService = messageService;
        this.recipientService = recipientService;
        this.deliveryService = deliveryService;
        this.accountService = accountService;
        this.platformMemberService = platformMemberService;
        this.memberService = memberService;
        this.tenantMemberService = tenantMemberService;
        this.flowInstanceService = flowInstanceService;
        this.workTaskService = workTaskService;
        this.runtimeDataService = runtimeDataService;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<MessageModels.TemplateView> listTemplates(AuthenticatedContext context) {
        require(context, "MANAGE");
        return templateService.selectList(Wrappers.<MsgTemplate>lambdaQuery()
                        .eq(MsgTemplate::getPlatformId, context.platformId()))
                .stream().filter(item -> inContext(item, context))
                .sorted(Comparator.comparing(MsgTemplate::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::templateView).toList();
    }

    @Transactional
    public MessageModels.TemplateView saveTemplate(
            AuthenticatedContext context, MessageModels.SaveTemplateRequest input, String traceId) {
        require(context, "MANAGE");
        List<String> requiredVariables = distinctVariables(input.requiredVariables());
        MsgTemplate template;
        if (input.id() == null) {
            boolean exists = templateService.selectList(Wrappers.<MsgTemplate>lambdaQuery()
                            .eq(MsgTemplate::getPlatformId, context.platformId())
                            .eq(MsgTemplate::getCode, input.code())
                            .eq(MsgTemplate::getChannel, input.channel()))
                    .stream().anyMatch(item -> inContext(item, context));
            if (exists) throw conflict("MESSAGE_TEMPLATE_EXISTS", "当前上下文已存在相同编码和渠道的模板");
            template = new MsgTemplate();
            template.setContextType(contextType(context));
            template.setPlatformId(context.platformId());
            template.setSystemId(context.systemId());
            template.setTenantId(context.tenantId());
            template.setCode(input.code());
            template.setChannel(input.channel());
            template.setDraftRevision(1);
            template.setCreatedByAccountId(context.accountId());
            template.setVersion(0);
        } else {
            template = requireTemplate(context, input.id());
            if (input.expectedVersion() == null || !input.expectedVersion().equals(template.getVersion())) {
                throw conflict("MESSAGE_TEMPLATE_VERSION_CONFLICT", "消息模板已变化，请刷新后重试");
            }
            if (!template.getCode().equals(input.code()) || !template.getChannel().equals(input.channel())) {
                throw invalid("MESSAGE_TEMPLATE_IDENTITY_IMMUTABLE", "模板创建后不能修改编码或渠道");
            }
            template.setDraftRevision(template.getDraftRevision() + 1);
        }
        template.setName(input.name().strip());
        template.setSubjectTemplate(stripToNull(input.subjectTemplate()));
        template.setContentTemplate(input.contentTemplate());
        template.setVariableSchemaJson(toJson(Map.of("required", requiredVariables)));
        template.setStatus("DRAFT");
        if (input.id() == null) templateService.insert(template);
        else if (templateService.updateById(template) != 1) {
            throw conflict("MESSAGE_TEMPLATE_VERSION_CONFLICT", "消息模板已变化，请刷新后重试");
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MESSAGE_TEMPLATE_SAVED", "MESSAGE_TEMPLATE", template.getId().toString(), "SUCCESS",
                Map.of("code", template.getCode(), "channel", template.getChannel(),
                        "draftRevision", template.getDraftRevision()));
        return templateView(templateService.selectById(template.getId()));
    }

    @Transactional
    public MessageModels.TemplateView publishTemplate(AuthenticatedContext context, Long templateId, String traceId) {
        require(context, "MANAGE");
        MsgTemplate template = requireTemplateForUpdate(context, templateId);
        if (template.getContentTemplate() == null || template.getContentTemplate().isBlank()) {
            throw invalid("MESSAGE_TEMPLATE_CONTENT_REQUIRED", "消息模板正文不能为空");
        }
        MsgTemplateVersion latest = latestVersion(template.getId());
        int versionNumber = latest == null ? 1 : latest.getVersionNumber() + 1;
        TemplateSnapshot snapshot = new TemplateSnapshot(template.getCode(), template.getName(), template.getChannel(),
                template.getSubjectTemplate(), template.getContentTemplate(), requiredVariables(template),
                template.getContextType(), template.getPlatformId(), template.getSystemId(), template.getTenantId());
        MsgTemplateVersion version = new MsgTemplateVersion();
        version.setTemplateId(template.getId());
        version.setVersionNumber(versionNumber);
        version.setDraftRevision(template.getDraftRevision());
        version.setSnapshotJson(toJson(snapshot));
        version.setPublishedByAccountId(context.accountId());
        templateVersionService.insert(version);
        template.setStatus("PUBLISHED");
        if (templateService.updateById(template) != 1) {
            throw conflict("MESSAGE_TEMPLATE_VERSION_CONFLICT", "消息模板已变化，请刷新后重试");
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MESSAGE_TEMPLATE_PUBLISHED", "MESSAGE_TEMPLATE_VERSION", version.getId().toString(), "SUCCESS",
                Map.of("templateId", template.getId(), "versionNumber", versionNumber,
                        "draftRevision", template.getDraftRevision()));
        return templateView(templateService.selectById(template.getId()));
    }

    @Transactional(readOnly = true)
    public MessageModels.InboxView list(AuthenticatedContext context, String requestedStatus, String requestedSourceType) {
        require(context, "VIEW");
        String status = requestedStatus.strip().toUpperCase(Locale.ROOT);
        if (!INBOX_STATUSES.contains(status)) throw invalid("MESSAGE_STATUS_INVALID", "消息状态筛选无效");
        String sourceType = requestedSourceType.strip().toUpperCase(Locale.ROOT);
        if (!"ALL".equals(sourceType) && !sourceType.matches("[A-Z][A-Z0-9_]{1,63}")) {
            throw invalid("MESSAGE_SOURCE_TYPE_INVALID", "消息来源筛选无效");
        }
        List<MessageModels.MessageView> views = ownedRecipients(context).stream()
                .filter(recipient -> matchesStatus(recipient, status))
                .map(recipient -> Map.entry(recipient, messageService.selectById(recipient.getMessageId())))
                .filter(entry -> entry.getValue() != null && inContext(entry.getValue(), context))
                .filter(entry -> "ALL".equals(sourceType) || sourceType.equals(entry.getValue().getSourceType()))
                .sorted(Comparator.comparing((Map.Entry<MsgRecipient, MsgMessage> entry) -> entry.getValue().getCreatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(entry -> messageView(context, entry.getValue(), entry.getKey(), null))
                .toList();
        return new MessageModels.InboxView(views, countUnread(context));
    }

    @Transactional(readOnly = true)
    public MessageModels.UnreadCount unreadCount(AuthenticatedContext context) {
        require(context, "VIEW");
        return new MessageModels.UnreadCount(countUnread(context));
    }

    @Transactional
    public MessageModels.MessageView send(
            AuthenticatedContext context, MessageModels.SendEventRequest input, String traceId) {
        requireAny(context, "SEND", "MANAGE");
        MsgTemplate template = publishedTemplate(context, input.templateCode());
        MsgTemplateVersion version = latestVersion(template.getId());
        if (version == null) throw conflict("MESSAGE_TEMPLATE_NOT_PUBLISHED", "消息模板尚未发布");
        TemplateSnapshot snapshot = snapshot(version);
        Map<String, Object> variables = input.variables() == null ? Map.of() : new LinkedHashMap<>(input.variables());
        List<String> missing = snapshot.requiredVariables().stream()
                .filter(key -> !variables.containsKey(key) || variables.get(key) == null).toList();
        if (!missing.isEmpty()) {
            auditRecorder.recordFailure(traceId, context.accountId(), context.systemId(), context.tenantId(),
                    context.memberId(), "MESSAGE_RENDER_FAILED", "MESSAGE_TEMPLATE_VERSION", version.getId().toString(),
                    "MESSAGE_TEMPLATE_VARIABLE_MISSING", Map.of("missingVariables", missing));
            throw invalid("MESSAGE_TEMPLATE_VARIABLE_MISSING", "缺少模板变量：" + String.join("、", missing));
        }
        List<PlatformAccount> accounts = resolveRecipients(context, input.recipients());
        validateTarget(context, input.targetType(), input.targetId(), input.targetRoute(), traceId);
        MsgMessage existing = findEvent(context, input.sourceType(), input.dedupKey());
        if (existing != null) {
            MsgRecipient first = recipientService.selectList(Wrappers.<MsgRecipient>lambdaQuery()
                    .eq(MsgRecipient::getMessageId, existing.getId())).stream().findFirst()
                    .orElseThrow(() -> conflict("MESSAGE_EVENT_INCOMPLETE", "已有消息事件缺少接收人记录"));
            return messageView(context, existing, first, traceId);
        }
        MsgMessage message = new MsgMessage();
        message.setContextType(contextType(context));
        message.setPlatformId(context.platformId());
        message.setSystemId(context.systemId());
        message.setTenantId(context.tenantId());
        message.setTemplateVersionId(version.getId());
        message.setSourceType(input.sourceType());
        message.setSourceId(input.dedupKey());
        message.setSubject(render(snapshot.subjectTemplate(), variables, snapshot.name()));
        message.setContentText(render(snapshot.contentTemplate(), variables, ""));
        message.setTargetType(stripToNull(input.targetType()));
        message.setTargetId(stripToNull(input.targetId()));
        message.setTargetRoute(stripToNull(input.targetRoute()));
        message.setSensitivity(input.sensitivity() == null ? "NORMAL" : input.sensitivity());
        message.setCreatedByAccountId(context.accountId());
        messageService.insert(message);
        MsgRecipient first = null;
        for (PlatformAccount account : accounts) {
            MsgRecipient recipient = new MsgRecipient();
            recipient.setMessageId(message.getId());
            recipient.setAccountId(account.getId());
            recipient.setStatus("UNREAD");
            recipient.setVersion(0);
            recipientService.insert(recipient);
            if (first == null) first = recipient;
            insertInAppDelivery(message, account);
            if (!"IN_APP".equals(snapshot.channel())) insertExternalDelivery(message, account, snapshot.channel());
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MESSAGE_CREATED", "MESSAGE", message.getId().toString(), "SUCCESS",
                Map.of("templateVersionId", version.getId(), "recipientCount", accounts.size(),
                        "channel", snapshot.channel(), "sourceType", input.sourceType()));
        return messageView(context, message, Objects.requireNonNull(first), traceId);
    }

    /**
     * Creates an idempotent in-app Flow notification without requiring a user-maintained template.
     * Flow owns the event transaction; the message center owns read/archive state and target access checks.
     */
    @Transactional
    public void notifyFlowEvent(
            AuthenticatedContext context, Long instanceId, String eventKey,
            String subject, String content, List<Long> recipientAccountIds) {
        if (context == null || instanceId == null || eventKey == null || eventKey.isBlank()) return;
        if (recipientAccountIds == null || recipientAccountIds.isEmpty()) return;
        List<Long> ids = distinctRecipients(recipientAccountIds);
        if (findEvent(context, "FLOW", eventKey) != null) return;
        List<PlatformAccount> accounts = ids.stream().map(id -> requireRecipient(context, id)).toList();
        MsgMessage message = new MsgMessage();
        message.setContextType(contextType(context));
        message.setPlatformId(context.platformId());
        message.setSystemId(context.systemId());
        message.setTenantId(context.tenantId());
        message.setTemplateVersionId(null);
        message.setSourceType("FLOW");
        message.setSourceId(eventKey);
        message.setSubject(subject);
        message.setContentText(content);
        message.setTargetType("FLOW_INSTANCE");
        message.setTargetId(instanceId.toString());
        message.setTargetRoute(context.systemId() == null
                ? "/platform/flows?instanceId=" + instanceId
                : "/systems/" + context.systemId() + "?workspace=flow&instanceId=" + instanceId);
        message.setSensitivity("NORMAL");
        message.setCreatedByAccountId(context.accountId());
        messageService.insert(message);
        for (PlatformAccount account : accounts) {
            MsgRecipient recipient = new MsgRecipient();
            recipient.setMessageId(message.getId());
            recipient.setAccountId(account.getId());
            recipient.setStatus("UNREAD");
            recipient.setVersion(0);
            recipientService.insert(recipient);
            insertInAppDelivery(message, account);
        }
    }

    @Transactional
    public void notifyWorkTaskEvent(
            AuthenticatedContext context, WorkTask task, String eventKey,
            String subject, String content, List<Long> recipientAccountIds) {
        if (context == null || task == null || eventKey == null || eventKey.isBlank()
                || recipientAccountIds == null || recipientAccountIds.isEmpty()) return;
        if (findEvent(context, "WORK_TASK", eventKey) != null) return;
        List<PlatformAccount> accounts = distinctRecipients(recipientAccountIds).stream()
                .map(id -> requireRecipient(context, id)).toList();
        MsgMessage message = new MsgMessage();
        message.setContextType(contextType(context));
        message.setPlatformId(context.platformId());
        message.setSystemId(context.systemId());
        message.setTenantId(context.tenantId());
        message.setTemplateVersionId(null);
        message.setSourceType("WORK_TASK");
        message.setSourceId(eventKey);
        message.setSubject(subject);
        message.setContentText(content);
        message.setTargetType("WORK_TASK");
        message.setTargetId(task.getId().toString());
        message.setTargetRoute(context.systemId() == null
                ? "/platform/tasks?taskId=" + task.getId()
                : "/systems/" + context.systemId() + "?workspace=tasks&taskId=" + task.getId());
        message.setSensitivity("NORMAL");
        message.setCreatedByAccountId(context.accountId());
        messageService.insert(message);
        for (PlatformAccount account : accounts) {
            MsgRecipient recipient = new MsgRecipient();
            recipient.setMessageId(message.getId());
            recipient.setAccountId(account.getId());
            recipient.setStatus("UNREAD");
            recipient.setVersion(0);
            recipientService.insert(recipient);
            insertInAppDelivery(message, account);
        }
    }

    @Transactional
    public void notifyExportResult(
            AuthenticatedContext context, Long batchId, String moduleCode, boolean succeeded,
            long exportedRows, String errorMessage, String traceId) {
        String sourceId = "batch-" + batchId + "-" + (succeeded ? "completed" : "failed");
        if (findEvent(context, "MODULE_EXPORT", sourceId) != null) return;
        PlatformAccount account = requireRecipient(context, context.accountId());
        MsgMessage message = new MsgMessage();
        message.setContextType(contextType(context));
        message.setPlatformId(context.platformId());
        message.setSystemId(context.systemId());
        message.setTenantId(context.tenantId());
        message.setTemplateVersionId(null);
        message.setSourceType("MODULE_EXPORT");
        message.setSourceId(sourceId);
        message.setSubject(moduleCode + " 导出" + (succeeded ? "已完成" : "失败"));
        message.setContentText(succeeded
                ? "导出批次 #" + batchId + " 已生成 " + exportedRows + " 行受控文件，请回到模块导出工作区下载。"
                : "导出批次 #" + batchId + " 未生成文件。失败原因：" + Objects.toString(errorMessage, "权限或任务状态已变化"));
        message.setTargetType("SYSTEM_ROUTE");
        message.setTargetId(null);
        message.setTargetRoute("/systems/" + context.systemId() + "?module=" + moduleCode + "&exportBatch=" + batchId);
        message.setSensitivity("NORMAL");
        message.setCreatedByAccountId(context.accountId());
        messageService.insert(message);
        MsgRecipient recipient = new MsgRecipient();
        recipient.setMessageId(message.getId());
        recipient.setAccountId(context.accountId());
        recipient.setStatus("UNREAD");
        recipient.setVersion(0);
        recipientService.insert(recipient);
        insertInAppDelivery(message, account);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MESSAGE_CREATED", "MESSAGE", message.getId().toString(), "SUCCESS",
                Map.of("sourceType", "MODULE_EXPORT", "batchId", batchId,
                        "exportStatus", succeeded ? "COMPLETED" : "FAILED"));
    }

    @Transactional
    public void notifyCriticalFailure(
            AuthenticatedContext context, String errorCode, String requestId, String summary) {
        if (context == null || context.systemId() == null || context.tenantId() == null || context.accountId() == null) return;
        String sourceId = "request-" + requestId;
        if (findEvent(context, "CRITICAL_EXCEPTION", sourceId) != null) return;
        PlatformAccount account = requireRecipient(context, context.accountId());
        MsgMessage message = new MsgMessage();
        message.setContextType(contextType(context));
        message.setPlatformId(context.platformId());
        message.setSystemId(context.systemId());
        message.setTenantId(context.tenantId());
        message.setTemplateVersionId(null);
        message.setSourceType("CRITICAL_EXCEPTION");
        message.setSourceId(sourceId);
        message.setSubject("系统关键异常待处理");
        message.setContentText("错误摘要：" + Objects.toString(summary, "服务处理失败")
                + "；错误编码：" + errorCode + "；requestId：" + requestId + "；处理状态：待处理。");
        message.setTargetType("SYSTEM_ROUTE");
        message.setTargetId(requestId);
        message.setTargetRoute("/systems/" + context.systemId() + "/admin?section=operations&requestId=" + requestId);
        message.setSensitivity("INTERNAL");
        message.setCreatedByAccountId(context.accountId());
        messageService.insert(message);
        MsgRecipient recipient = new MsgRecipient();
        recipient.setMessageId(message.getId());
        recipient.setAccountId(context.accountId());
        recipient.setStatus("UNREAD");
        recipient.setVersion(0);
        recipientService.insert(recipient);
        insertInAppDelivery(message, account);
        auditRecorder.record(requestId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "CRITICAL_EXCEPTION_MESSAGE_CREATED", "MESSAGE", message.getId().toString(), "PENDING",
                Map.of("errorCode", errorCode, "requestId", requestId, "recipientScope", "CURRENT_OPERATIONS_ADMIN"));
    }

    @Transactional
    public MessageModels.MessageView notifyKpiUnderTarget(
            AuthenticatedContext context, Long kpiId, Long resultId, String kpiName,
            String actualValue, String targetValue, String achievementRate,
            List<MessageModels.RecipientRef> recipients, String traceId) {
        String sourceId = "result-" + resultId;
        MsgMessage existing = findEvent(context, "KPI_ALERT", sourceId);
        if (existing != null) {
            MsgRecipient first = recipientService.selectList(Wrappers.<MsgRecipient>lambdaQuery()
                            .eq(MsgRecipient::getMessageId, existing.getId())).stream().findFirst()
                    .orElseThrow(() -> conflict("MESSAGE_EVENT_INCOMPLETE", "已有 KPI 提醒缺少接收人记录"));
            return messageView(context, existing, first, traceId);
        }
        List<PlatformAccount> accounts = resolveRecipients(context, recipients);
        MsgMessage message = new MsgMessage();
        message.setContextType(contextType(context));
        message.setPlatformId(context.platformId());
        message.setSystemId(context.systemId());
        message.setTenantId(context.tenantId());
        message.setTemplateVersionId(null);
        message.setSourceType("KPI_ALERT");
        message.setSourceId(sourceId);
        message.setSubject(kpiName + " 未达标提醒");
        message.setContentText("当前实际值 " + actualValue + "，目标值 " + targetValue
                + "，达成率 " + achievementRate + "%；请打开 KPI 查看当前授权口径和下钻数据。");
        message.setTargetType("SYSTEM_ROUTE");
        message.setTargetId(kpiId.toString());
        message.setTargetRoute("/systems/" + context.systemId() + "?workspace=kpi&kpiId=" + kpiId);
        message.setSensitivity("NORMAL");
        message.setCreatedByAccountId(context.accountId());
        messageService.insert(message);
        MsgRecipient first = null;
        for (PlatformAccount account : accounts) {
            MsgRecipient recipient = new MsgRecipient();
            recipient.setMessageId(message.getId());
            recipient.setAccountId(account.getId());
            recipient.setStatus("UNREAD");
            recipient.setVersion(0);
            recipientService.insert(recipient);
            if (first == null) first = recipient;
            insertInAppDelivery(message, account);
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MESSAGE_CREATED", "MESSAGE", message.getId().toString(), "SUCCESS",
                Map.of("sourceType", "KPI_ALERT", "kpiId", kpiId, "resultId", resultId,
                        "recipientCount", accounts.size()));
        return messageView(context, message, Objects.requireNonNull(first), traceId);
    }

    @Transactional
    public MessageModels.MessageView read(AuthenticatedContext context, Long messageId, String traceId) {
        require(context, "VIEW");
        Owned owned = requireOwned(context, messageId, true);
        if (!"ARCHIVED".equals(owned.recipient().getStatus()) && !"READ".equals(owned.recipient().getStatus())) {
            owned.recipient().setStatus("READ");
            owned.recipient().setReadAt(LocalDateTime.now());
            updateRecipient(owned.recipient());
            auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                    "MESSAGE_READ", "MESSAGE", messageId.toString(), "SUCCESS", Map.of());
        }
        return messageView(context, owned.message(), recipientService.selectById(owned.recipient().getId()), traceId);
    }

    @Transactional
    public MessageModels.MessageView archive(AuthenticatedContext context, Long messageId, String traceId) {
        require(context, "VIEW");
        Owned owned = requireOwned(context, messageId, true);
        if (!"ARCHIVED".equals(owned.recipient().getStatus())) {
            owned.recipient().setStatus("ARCHIVED");
            if (owned.recipient().getReadAt() == null) owned.recipient().setReadAt(LocalDateTime.now());
            owned.recipient().setArchivedAt(LocalDateTime.now());
            updateRecipient(owned.recipient());
            auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                    "MESSAGE_ARCHIVED", "MESSAGE", messageId.toString(), "SUCCESS", Map.of());
        }
        return messageView(context, owned.message(), recipientService.selectById(owned.recipient().getId()), traceId);
    }

    @Transactional
    public MessageModels.UnreadCount readAll(AuthenticatedContext context, String traceId) {
        require(context, "VIEW");
        int changed = 0;
        for (MsgRecipient recipient : ownedRecipients(context)) {
            if (!"UNREAD".equals(recipient.getStatus())) continue;
            recipient.setStatus("READ");
            recipient.setReadAt(LocalDateTime.now());
            updateRecipient(recipient);
            changed++;
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MESSAGE_READ_ALL", "MESSAGE_INBOX", context.accountId().toString(), "SUCCESS",
                Map.of("changedCount", changed));
        return new MessageModels.UnreadCount(countUnread(context));
    }

    @Transactional
    public MessageModels.OpenResult open(AuthenticatedContext context, Long messageId, String traceId) {
        require(context, "VIEW");
        Owned owned = requireOwned(context, messageId, false);
        String route = validateTarget(context, owned.message().getTargetType(), owned.message().getTargetId(),
                owned.message().getTargetRoute(), traceId);
        if (route == null) throw conflict("MESSAGE_TARGET_UNAVAILABLE", "消息没有可打开的目标");
        if (owned.recipient().getReadAt() == null) {
            owned.recipient().setStatus("READ");
            owned.recipient().setReadAt(LocalDateTime.now());
            updateRecipient(owned.recipient());
        }
        Map<String, Object> targetAudit = new LinkedHashMap<>();
        targetAudit.put("targetType", owned.message().getTargetType());
        targetAudit.put("targetId", owned.message().getTargetId());
        targetAudit.put("resolvedRoute", route);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MESSAGE_TARGET_OPENED", "MESSAGE", messageId.toString(), "SUCCESS", targetAudit);
        return new MessageModels.OpenResult(messageId, route, context.systemId(), owned.message().getTargetType(),
                owned.message().getTargetId());
    }

    @Transactional
    public MessageModels.MessageView retryDelivery(AuthenticatedContext context, Long deliveryId, String traceId) {
        require(context, "MANAGE");
        MsgDelivery delivery = deliveryService.selectList(Wrappers.<MsgDelivery>lambdaQuery()
                        .eq(MsgDelivery::getId, deliveryId).last("FOR UPDATE"))
                .stream().findFirst().orElseThrow(() -> notFound("MESSAGE_DELIVERY_NOT_FOUND", "消息投递记录不存在"));
        MsgMessage message = messageService.selectById(delivery.getMessageId());
        if (message == null || !inContext(message, context)) {
            throw notFound("MESSAGE_DELIVERY_NOT_FOUND", "消息投递记录不存在");
        }
        if ("IN_APP".equals(delivery.getChannel()) || "DELIVERED".equals(delivery.getStatus())
                || "FAILED".equals(delivery.getStatus()) || delivery.getAttemptCount() >= MAX_DELIVERY_ATTEMPTS) {
            throw conflict("MESSAGE_DELIVERY_NOT_RETRYABLE", "该投递记录不需要重试");
        }
        int attempt = delivery.getAttemptCount() + 1;
        delivery.setAttemptCount(attempt);
        delivery.setProviderReceiptJson(toJson(Map.of("result", "PROVIDER_UNAVAILABLE", "attempt", attempt)));
        delivery.setLastError("CHANNEL_PROVIDER_UNAVAILABLE");
        delivery.setUpdatedAt(LocalDateTime.now());
        if (attempt >= MAX_DELIVERY_ATTEMPTS) {
            delivery.setStatus("FAILED");
            delivery.setNextAttemptAt(null);
        } else {
            delivery.setStatus("RETRY_PENDING");
            delivery.setNextAttemptAt(LocalDateTime.now().plusMinutes(attempt));
        }
        if (deliveryService.updateById(delivery) != 1) {
            throw conflict("MESSAGE_DELIVERY_VERSION_CONFLICT", "投递记录已变化，请刷新后重试");
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MESSAGE_DELIVERY_RETRIED", "MESSAGE_DELIVERY", deliveryId.toString(), delivery.getStatus(),
                Map.of("attemptCount", attempt, "channel", delivery.getChannel()));
        MsgRecipient recipient = recipientService.selectList(Wrappers.<MsgRecipient>lambdaQuery()
                        .eq(MsgRecipient::getMessageId, message.getId())
                        .eq(MsgRecipient::getAccountId, delivery.getAccountId()))
                .stream().findFirst().orElseThrow(() -> conflict("MESSAGE_RECIPIENT_NOT_FOUND", "消息接收记录不存在"));
        return messageView(context, message, recipient, traceId);
    }

    @Transactional(readOnly = true)
    public MessageModels.DeliveryDiagnosticsView deliveryDiagnostics(
            AuthenticatedContext context, Long messageId) {
        require(context, "MANAGE");
        MsgMessage message = messageService.selectById(messageId);
        if (message == null || !inContext(message, context)) {
            throw notFound("MESSAGE_NOT_FOUND", "消息不存在");
        }
        List<MessageModels.DeliveryView> deliveries = deliveryService.selectList(
                        Wrappers.<MsgDelivery>lambdaQuery().eq(MsgDelivery::getMessageId, messageId)
                                .orderByAsc(MsgDelivery::getId))
                .stream().map(this::deliveryView).toList();
        return new MessageModels.DeliveryDiagnosticsView(messageId, message.getSubject(), deliveries);
    }

    private MsgTemplate publishedTemplate(AuthenticatedContext context, String code) {
        List<MsgTemplate> matches = templateService.selectList(Wrappers.<MsgTemplate>lambdaQuery()
                        .eq(MsgTemplate::getPlatformId, context.platformId())
                        .eq(MsgTemplate::getCode, code).eq(MsgTemplate::getStatus, "PUBLISHED"))
                .stream().filter(item -> inContext(item, context)).toList();
        if (matches.isEmpty()) throw notFound("MESSAGE_TEMPLATE_NOT_PUBLISHED", "当前上下文没有已发布的消息模板");
        if (matches.size() > 1) throw conflict("MESSAGE_TEMPLATE_CHANNEL_AMBIGUOUS", "同一模板编码存在多个已发布渠道，请使用唯一编码");
        return matches.getFirst();
    }

    private MsgTemplate requireTemplate(AuthenticatedContext context, Long templateId) {
        MsgTemplate template = templateService.selectById(templateId);
        if (template == null || !inContext(template, context)) {
            throw notFound("MESSAGE_TEMPLATE_NOT_FOUND", "消息模板不存在");
        }
        return template;
    }

    private MsgTemplate requireTemplateForUpdate(AuthenticatedContext context, Long templateId) {
        return templateService.selectList(Wrappers.<MsgTemplate>lambdaQuery()
                        .eq(MsgTemplate::getId, templateId).last("FOR UPDATE"))
                .stream().filter(item -> inContext(item, context)).findFirst()
                .orElseThrow(() -> notFound("MESSAGE_TEMPLATE_NOT_FOUND", "消息模板不存在"));
    }

    private MsgTemplateVersion latestVersion(Long templateId) {
        return templateVersionService.selectList(Wrappers.<MsgTemplateVersion>lambdaQuery()
                        .eq(MsgTemplateVersion::getTemplateId, templateId)
                        .orderByDesc(MsgTemplateVersion::getVersionNumber).last("LIMIT 1"))
                .stream().findFirst().orElse(null);
    }

    private MessageModels.TemplateView templateView(MsgTemplate template) {
        MsgTemplateVersion latest = latestVersion(template.getId());
        return new MessageModels.TemplateView(template.getId(), template.getContextType(), template.getPlatformId(),
                template.getSystemId(), template.getTenantId(), template.getCode(), template.getName(),
                template.getChannel(), template.getDraftRevision(), template.getSubjectTemplate(),
                template.getContentTemplate(), requiredVariables(template), template.getStatus(),
                latest == null ? null : latest.getVersionNumber(), latest == null ? null : latest.getId(),
                latest == null ? null : latest.getPublishedAt(), template.getVersion(), template.getUpdatedAt());
    }

    private List<String> requiredVariables(MsgTemplate template) {
        try {
            Map<String, Object> schema = objectMapper.readValue(template.getVariableSchemaJson(), new TypeReference<>() { });
            Object required = schema.get("required");
            if (!(required instanceof List<?> values)) return List.of();
            return values.stream().map(String::valueOf).toList();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot parse message variable schema", exception);
        }
    }

    private TemplateSnapshot snapshot(MsgTemplateVersion version) {
        try {
            return objectMapper.readValue(version.getSnapshotJson(), TemplateSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot parse message template snapshot", exception);
        }
    }

    private MsgMessage findEvent(AuthenticatedContext context, String sourceType, String sourceId) {
        return messageService.selectList(Wrappers.<MsgMessage>lambdaQuery()
                        .eq(MsgMessage::getPlatformId, context.platformId())
                        .eq(MsgMessage::getSourceType, sourceType)
                        .eq(MsgMessage::getSourceId, sourceId))
                .stream().filter(item -> inContext(item, context)).findFirst().orElse(null);
    }

    private PlatformAccount requireRecipient(AuthenticatedContext context, Long accountId) {
        PlatformAccount account = accountService.selectById(accountId);
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw invalid("MESSAGE_RECIPIENT_INVALID", "消息接收账号不存在或已停用");
        }
        if (context.systemId() != null) {
            SystemMember member = memberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                            .eq(SystemMember::getSystemId, context.systemId())
                            .eq(SystemMember::getAccountId, accountId).eq(SystemMember::getStatus, "ACTIVE"))
                    .stream().findFirst().orElseThrow(() -> invalid("MESSAGE_RECIPIENT_CONTEXT_INVALID", "接收人不是当前系统成员"));
            boolean tenantActive = tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                            .eq(SystemTenantMember::getSystemId, context.systemId())
                            .eq(SystemTenantMember::getTenantId, context.tenantId())
                            .eq(SystemTenantMember::getSystemMemberId, member.getId())
                            .eq(SystemTenantMember::getStatus, "ACTIVE"))
                    .stream().findFirst().isPresent();
            if (!tenantActive) throw invalid("MESSAGE_RECIPIENT_CONTEXT_INVALID", "接收人不属于当前租户");
        }
        return account;
    }

    private List<PlatformAccount> resolveRecipients(
            AuthenticatedContext context, List<MessageModels.RecipientRef> recipients) {
        if (recipients == null || recipients.isEmpty() || recipients.size() > 100) {
            throw invalid("MESSAGE_RECIPIENT_INVALID", "单次消息必须包含 1 到 100 个组织成员");
        }
        LinkedHashSet<Long> accountIds = new LinkedHashSet<>();
        for (MessageModels.RecipientRef recipient : recipients) {
            if (recipient == null || recipient.id() == null || recipient.id() <= 0) {
                throw invalid("MESSAGE_RECIPIENT_INVALID", "消息接收成员无效");
            }
            if (context.systemId() == null) {
                if (!"PLATFORM_MEMBER".equals(recipient.type())) {
                    throw invalid("MESSAGE_RECIPIENT_CONTEXT_INVALID", "平台消息只能选择平台组织成员");
                }
                PlatformMember member = platformMemberService.selectById(recipient.id());
                if (member == null || !Objects.equals(member.getPlatformId(), context.platformId())
                        || !"ACTIVE".equals(member.getStatus())) {
                    throw invalid("MESSAGE_RECIPIENT_CONTEXT_INVALID", "接收人不属于当前平台组织");
                }
                accountIds.add(member.getAccountId());
                continue;
            }
            if (!"TENANT_MEMBER".equals(recipient.type())) {
                throw invalid("MESSAGE_RECIPIENT_CONTEXT_INVALID", "系统消息只能选择当前工作空间成员");
            }
            SystemTenantMember tenantMember = tenantMemberService.selectById(recipient.id());
            if (tenantMember == null || !Objects.equals(tenantMember.getSystemId(), context.systemId())
                    || !Objects.equals(tenantMember.getTenantId(), context.tenantId())
                    || !"ACTIVE".equals(tenantMember.getStatus())) {
                throw invalid("MESSAGE_RECIPIENT_CONTEXT_INVALID", "接收人不属于当前工作空间");
            }
            SystemMember member = memberService.selectById(tenantMember.getSystemMemberId());
            if (member == null || !Objects.equals(member.getSystemId(), context.systemId())
                    || !"ACTIVE".equals(member.getStatus())) {
                throw invalid("MESSAGE_RECIPIENT_CONTEXT_INVALID", "接收人的系统成员身份已失效");
            }
            accountIds.add(member.getAccountId());
        }
        return distinctRecipients(new ArrayList<>(accountIds)).stream()
                .map(id -> requireRecipient(context, id)).toList();
    }

    private void insertInAppDelivery(MsgMessage message, PlatformAccount account) {
        MsgDelivery delivery = new MsgDelivery();
        delivery.setMessageId(message.getId());
        delivery.setAccountId(account.getId());
        delivery.setChannel("IN_APP");
        delivery.setDestinationMasked("account:" + account.getId());
        delivery.setStatus("DELIVERED");
        delivery.setAttemptCount(1);
        delivery.setProviderMessageId("in-app:" + message.getId() + ":" + account.getId());
        delivery.setProviderReceiptJson(toJson(Map.of("result", "STORED")));
        delivery.setSentAt(LocalDateTime.now());
        delivery.setDeliveredAt(LocalDateTime.now());
        delivery.setVersion(0);
        deliveryService.insert(delivery);
    }

    private void insertExternalDelivery(MsgMessage message, PlatformAccount account, String channel) {
        MsgDelivery delivery = new MsgDelivery();
        delivery.setMessageId(message.getId());
        delivery.setAccountId(account.getId());
        delivery.setChannel(channel);
        delivery.setDestinationMasked(maskDestination(account, channel));
        delivery.setStatus("RETRY_PENDING");
        delivery.setAttemptCount(1);
        delivery.setNextAttemptAt(LocalDateTime.now().plusMinutes(1));
        delivery.setProviderReceiptJson(toJson(Map.of("result", "PROVIDER_UNAVAILABLE", "attempt", 1)));
        delivery.setLastError("CHANNEL_PROVIDER_UNAVAILABLE");
        delivery.setVersion(0);
        deliveryService.insert(delivery);
    }

    private String validateTarget(
            AuthenticatedContext context, String targetType, String targetId, String targetRoute, String traceId) {
        if (targetType == null || targetType.isBlank()) {
            if (targetId != null || targetRoute != null) throw invalid("MESSAGE_TARGET_INVALID", "目标类型为空时不能携带目标引用");
            return null;
        }
        String route = stripToNull(targetRoute);
        switch (targetType) {
            case "PLATFORM_ROUTE" -> {
                if (context.systemId() != null || route == null
                        || !(route.equals("/profile") || route.equals("/platform") || route.startsWith("/platform/"))) {
                    throw forbidden("MESSAGE_TARGET_CONTEXT_INVALID", "平台消息目标不能跳入系统业务上下文");
                }
                return route;
            }
            case "SYSTEM_ROUTE" -> {
                requireSystemTarget(context, route);
                return route;
            }
            case "FLOW_INSTANCE" -> {
                Long instanceId = numericTarget(targetId);
                FlowInstance instance = flowInstanceService.selectById(instanceId);
                if (instance == null || !sameContext(instance.getContextType(), instance.getPlatformId(),
                        instance.getSystemId(), instance.getTenantId(), context)) {
                    throw notFound("MESSAGE_TARGET_NOT_FOUND", "消息关联的 Flow 实例不存在");
                }
                requireTargetPermission(context, "FLOW", context.systemId() == null ? "PLATFORM" : "SYSTEM", "VIEW");
                return context.systemId() == null ? "/platform/flows?instanceId=" + instanceId
                        : "/systems/" + context.systemId() + "?workspace=flow&instanceId=" + instanceId;
            }
            case "RUNTIME_RECORD" -> {
                if (context.systemId() == null || targetId == null || !targetId.matches("[a-z][a-z0-9_-]{0,99}:[1-9][0-9]*")) {
                    throw forbidden("MESSAGE_TARGET_CONTEXT_INVALID", "业务记录消息只能属于当前系统上下文");
                }
                String[] parts = targetId.split(":", 2);
                runtimeDataService.detail(context, parts[0], Long.valueOf(parts[1]), traceId);
                return "/systems/" + context.systemId() + "?module=" + parts[0] + "&record=" + parts[1];
            }
            case "WORK_TASK" -> {
                Long taskId = numericTarget(targetId);
                WorkTask task = workTaskService.selectById(taskId);
                if (task == null || !sameContext(task.getContextType(), task.getPlatformId(), task.getSystemId(),
                        task.getTenantId(), context)) throw notFound("MESSAGE_TARGET_NOT_FOUND", "消息关联的工作任务不存在");
                requireTargetPermission(context, "WORK", "TASK", "VIEW");
                return context.systemId() == null ? "/platform/tasks?taskId=" + taskId
                        : "/systems/" + context.systemId() + "?workspace=tasks&taskId=" + taskId;
            }
            case "APPLICATION" -> {
                requireTargetPermission(context, "APPLICATION", context.systemId() == null ? "PLATFORM" : "SYSTEM", "VIEW");
                return context.systemId() == null ? "/platform/applications?applicationId=" + numericTarget(targetId)
                        : "/systems/" + context.systemId() + "/admin?section=applications&applicationId=" + numericTarget(targetId);
            }
            case "FILE_OBJECT" -> {
                requireTargetPermission(context, "FILE", "OBJECT", "VIEW");
                numericTarget(targetId);
                if (route == null) throw conflict("MESSAGE_TARGET_UNAVAILABLE", "文件消息缺少受控预览或下载入口");
                if (context.systemId() == null && !route.startsWith("/platform/")) {
                    throw forbidden("MESSAGE_TARGET_CONTEXT_INVALID", "平台文件消息不能跳入系统上下文");
                }
                if (context.systemId() != null) requireSystemTarget(context, route);
                return route;
            }
            default -> throw invalid("MESSAGE_TARGET_INVALID", "消息目标类型无效");
        }
    }

    private void requireSystemTarget(AuthenticatedContext context, String route) {
        if (context.systemId() == null || route == null || !route.startsWith("/systems/" + context.systemId())) {
            throw forbidden("MESSAGE_TARGET_CONTEXT_INVALID", "系统消息目标必须属于当前系统");
        }
    }

    private void requireTargetPermission(AuthenticatedContext context, String type, String code, String action) {
        if (!permissionChecker.allows(context, type, code, action)
                && !permissionChecker.allows(context, type, "*", action)) {
            throw forbidden("MESSAGE_TARGET_PERMISSION_DENIED", "关联目标当前不可访问");
        }
    }

    private long countUnread(AuthenticatedContext context) {
        return ownedRecipients(context).stream().filter(item -> "UNREAD".equals(item.getStatus())).count();
    }

    private List<MsgRecipient> ownedRecipients(AuthenticatedContext context) {
        return recipientService.selectList(Wrappers.<MsgRecipient>lambdaQuery()
                        .eq(MsgRecipient::getAccountId, context.accountId()))
                .stream().filter(recipient -> {
                    MsgMessage message = messageService.selectById(recipient.getMessageId());
                    return message != null && inContext(message, context);
                }).toList();
    }

    private Owned requireOwned(AuthenticatedContext context, Long messageId, boolean allowArchived) {
        MsgRecipient recipient = recipientService.selectList(Wrappers.<MsgRecipient>lambdaQuery()
                        .eq(MsgRecipient::getMessageId, messageId)
                        .eq(MsgRecipient::getAccountId, context.accountId()).last("FOR UPDATE"))
                .stream().findFirst().orElseThrow(() -> notFound("MESSAGE_NOT_FOUND", "消息不存在"));
        MsgMessage message = messageService.selectById(messageId);
        if (message == null || !inContext(message, context) || (!allowArchived && "ARCHIVED".equals(recipient.getStatus()))) {
            throw notFound("MESSAGE_NOT_FOUND", "消息不存在");
        }
        return new Owned(message, recipient);
    }

    private MessageModels.MessageView messageView(
            AuthenticatedContext context, MsgMessage message, MsgRecipient recipient, String traceId) {
        boolean targetAccessible;
        try {
            targetAccessible = validateTarget(context, message.getTargetType(), message.getTargetId(),
                    message.getTargetRoute(), traceId == null ? "message-view" : traceId) != null;
        } catch (DomainException exception) {
            targetAccessible = false;
        }
        return new MessageModels.MessageView(message.getId(), message.getContextType(), message.getPlatformId(),
                message.getSystemId(), message.getTenantId(), message.getTemplateVersionId(), message.getSourceType(),
                message.getSourceId(), message.getSubject(), message.getContentText(), message.getTargetType(),
                message.getTargetId(), message.getTargetRoute(), message.getSensitivity(), recipient.getStatus(),
                recipient.getReadAt(), recipient.getArchivedAt(), recipient.getVersion(), message.getCreatedAt(),
                actorName(message.getCreatedByAccountId()), targetAccessible);
    }

    private String actorName(Long accountId) {
        if (accountId == null) return "系统";
        PlatformAccount account = accountService.selectById(accountId);
        if (account == null) return "系统";
        return account.getDisplayName() == null || account.getDisplayName().isBlank()
                ? account.getUsername() : account.getDisplayName();
    }

    private MessageModels.DeliveryView deliveryView(MsgDelivery delivery) {
        return new MessageModels.DeliveryView(delivery.getId(), delivery.getChannel(), delivery.getDestinationMasked(),
                delivery.getStatus(), delivery.getAttemptCount(), delivery.getNextAttemptAt(),
                delivery.getProviderMessageId(), delivery.getProviderReceiptJson(), delivery.getLastError(),
                delivery.getSentAt(), delivery.getDeliveredAt(), delivery.getVersion());
    }

    private void updateRecipient(MsgRecipient recipient) {
        recipient.setUpdatedAt(LocalDateTime.now());
        if (recipientService.updateById(recipient) != 1) {
            throw conflict("MESSAGE_RECIPIENT_VERSION_CONFLICT", "消息状态已变化，请刷新后重试");
        }
    }

    private boolean matchesStatus(MsgRecipient recipient, String status) {
        return switch (status) {
            case "ACTIVE" -> !"ARCHIVED".equals(recipient.getStatus());
            case "ALL" -> true;
            default -> status.equals(recipient.getStatus());
        };
    }

    private boolean inContext(MsgTemplate item, AuthenticatedContext context) {
        return sameContext(item.getContextType(), item.getPlatformId(), item.getSystemId(), item.getTenantId(), context);
    }

    private boolean inContext(MsgMessage item, AuthenticatedContext context) {
        return sameContext(item.getContextType(), item.getPlatformId(), item.getSystemId(), item.getTenantId(), context);
    }

    private boolean sameContext(
            String type, Long platformId, Long systemId, Long tenantId, AuthenticatedContext context) {
        return Objects.equals(type, contextType(context))
                && Objects.equals(platformId, context.platformId())
                && Objects.equals(systemId, context.systemId())
                && Objects.equals(tenantId, context.tenantId());
    }

    private String contextType(AuthenticatedContext context) {
        return context.systemId() == null ? "PLATFORM" : "SYSTEM";
    }

    private List<String> distinctVariables(List<String> variables) {
        if (variables == null) return List.of();
        if (variables.size() > 50) throw invalid("MESSAGE_TEMPLATE_VARIABLE_LIMIT", "模板变量不能超过 50 个");
        return new ArrayList<>(new LinkedHashSet<>(variables));
    }

    private List<Long> distinctRecipients(List<Long> recipients) {
        List<Long> result = new ArrayList<>(new LinkedHashSet<>(recipients));
        if (result.isEmpty() || result.size() > 100 || result.stream().anyMatch(id -> id == null || id <= 0)) {
            throw invalid("MESSAGE_RECIPIENT_INVALID", "单次消息必须包含 1 到 100 个有效接收账号");
        }
        return result;
    }

    private String render(String template, Map<String, Object> variables, String fallback) {
        if (template == null || template.isBlank()) return fallback;
        String rendered = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return rendered;
    }

    private String maskDestination(PlatformAccount account, String channel) {
        if ("EMAIL".equals(channel) && account.getEmail() != null && account.getEmail().contains("@")) {
            String[] parts = account.getEmail().split("@", 2);
            return parts[0].substring(0, Math.min(2, parts[0].length())) + "***@" + parts[1];
        }
        if ("SMS".equals(channel) && account.getMobile() != null && account.getMobile().length() >= 4) {
            return "***" + account.getMobile().substring(account.getMobile().length() - 4);
        }
        return "account:" + account.getId();
    }

    private Long numericTarget(String targetId) {
        if (targetId == null || !targetId.matches("[1-9][0-9]*")) {
            throw invalid("MESSAGE_TARGET_INVALID", "消息目标引用格式无效");
        }
        return Long.valueOf(targetId);
    }

    private String stripToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.strip();
    }

    private void require(AuthenticatedContext context, String action) {
        String code = context.systemId() == null ? "PLATFORM" : "SYSTEM";
        if (!permissionChecker.allows(context, "MESSAGE", code, action)
                && !permissionChecker.allows(context, "MESSAGE", "*", action)) {
            throw forbidden("MESSAGE_PERMISSION_DENIED", "当前上下文没有消息" + action + "权限");
        }
    }

    private void requireAny(AuthenticatedContext context, String... actions) {
        for (String action : actions) {
            try {
                require(context, action);
                return;
            } catch (DomainException ignored) {
                // Try the next accepted action without persisting a partial result.
            }
        }
        throw forbidden("MESSAGE_PERMISSION_DENIED", "当前上下文没有消息发送权限");
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize message data", exception);
        }
    }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private DomainException forbidden(String code, String message) {
        return new DomainException(code, message, HttpStatus.FORBIDDEN);
    }

    private DomainException notFound(String code, String message) {
        return new DomainException(code, message, HttpStatus.NOT_FOUND);
    }

    private DomainException conflict(String code, String message) {
        return new DomainException(code, message, HttpStatus.CONFLICT);
    }

    private record Owned(MsgMessage message, MsgRecipient recipient) {
    }

    private record TemplateSnapshot(
            String code, String name, String channel, String subjectTemplate, String contentTemplate,
            List<String> requiredVariables, String contextType, Long platformId, Long systemId, Long tenantId) {
    }
}
