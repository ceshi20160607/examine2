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
import com.unique.unexamine.flow.manage.FlowRuntimeService;
import com.unique.unexamine.foundation.manage.control.BusinessActionApprovalCoordinator;
import com.unique.unexamine.notification.base.entity.MsgDelivery;
import com.unique.unexamine.notification.base.entity.MsgMessage;
import com.unique.unexamine.notification.base.entity.MsgRecipient;
import com.unique.unexamine.notification.base.entity.MsgTemplate;
import com.unique.unexamine.notification.base.entity.MsgTemplateVersion;
import com.unique.unexamine.notification.base.entity.MsgUserPreference;
import com.unique.unexamine.notification.base.service.MsgDeliveryBaseService;
import com.unique.unexamine.notification.base.service.MsgMessageBaseService;
import com.unique.unexamine.notification.base.service.MsgRecipientBaseService;
import com.unique.unexamine.notification.base.service.MsgTemplateBaseService;
import com.unique.unexamine.notification.base.service.MsgTemplateVersionBaseService;
import com.unique.unexamine.notification.base.service.MsgUserPreferenceBaseService;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.entity.PlatformMember;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.platform.base.service.PlatformMemberBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import com.unique.unexamine.work.base.entity.WorkTask;
import com.unique.unexamine.work.base.service.WorkTaskBaseService;
import com.unique.unexamine.work.manage.WorkManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private static final List<String> EXTERNAL_CHANNELS = List.of(
            "EMAIL", "SMS", "WEBHOOK", "WECOM", "FEISHU", "DINGTALK");
    private static final int MAX_DELIVERY_ATTEMPTS = 3;

    private final MsgTemplateBaseService templateService;
    private final MsgTemplateVersionBaseService templateVersionService;
    private final MsgMessageBaseService messageService;
    private final MsgRecipientBaseService recipientService;
    private final MsgDeliveryBaseService deliveryService;
    private final MsgUserPreferenceBaseService preferenceService;
    private final PlatformMessageService platformMessages;
    private final PlatformAccountBaseService accountService;
    private final PlatformMemberBaseService platformMemberService;
    private final SystemMemberBaseService memberService;
    private final SystemTenantMemberBaseService tenantMemberService;
    private final FlowInstanceBaseService flowInstanceService;
    private final WorkTaskBaseService workTaskService;
    private final RuntimeTargetAccessReader runtimeTargetAccessReader;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    /** 延迟获取流程服务，避免运行时发送通知与消息目标校验形成构造循环。 */
    private final ObjectProvider<FlowRuntimeService> flowRuntime;
    private final ObjectProvider<BusinessActionApprovalCoordinator> actionCoordinator;
    /** 消息跳转沿用任务详情的实时权限与参与关系，延迟获取以避免任务通知的构造循环。 */
    private final ObjectProvider<WorkManagementService> workManagement;

    public MessageService(
            MsgTemplateBaseService templateService,
            MsgTemplateVersionBaseService templateVersionService,
            MsgMessageBaseService messageService,
            MsgRecipientBaseService recipientService,
            MsgDeliveryBaseService deliveryService,
            MsgUserPreferenceBaseService preferenceService,
            PlatformMessageService platformMessages,
            PlatformAccountBaseService accountService,
            PlatformMemberBaseService platformMemberService,
            SystemMemberBaseService memberService,
            SystemTenantMemberBaseService tenantMemberService,
            FlowInstanceBaseService flowInstanceService,
            WorkTaskBaseService workTaskService,
            RuntimeTargetAccessReader runtimeTargetAccessReader,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper, ObjectProvider<FlowRuntimeService> flowRuntime,
            ObjectProvider<BusinessActionApprovalCoordinator> actionCoordinator,
            ObjectProvider<WorkManagementService> workManagement) {
        this.templateService = templateService;
        this.templateVersionService = templateVersionService;
        this.messageService = messageService;
        this.recipientService = recipientService;
        this.deliveryService = deliveryService;
        this.preferenceService = preferenceService;
        this.platformMessages = platformMessages;
        this.accountService = accountService;
        this.platformMemberService = platformMemberService;
        this.memberService = memberService;
        this.tenantMemberService = tenantMemberService;
        this.flowInstanceService = flowInstanceService;
        this.workTaskService = workTaskService;
        this.runtimeTargetAccessReader = runtimeTargetAccessReader;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.flowRuntime = flowRuntime;
        this.actionCoordinator = actionCoordinator;
        this.workManagement = workManagement;
    }

    @Transactional(readOnly = true)
    public List<MessageModels.TemplateView> listTemplates(AuthenticatedContext context) {
        require(context, "MANAGE");
        if (context.systemId() == null) return platformMessages.listTemplates(context);
        return templateService.selectList(Wrappers.<MsgTemplate>lambdaQuery()
                        .eq(MsgTemplate::getPlatformId, context.platformId()))
                .stream().filter(item -> inContext(item, context))
                .sorted(Comparator.comparing(MsgTemplate::getUpdateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::templateView).toList();
    }

    @Transactional
    public MessageModels.TemplateView saveTemplate(
            AuthenticatedContext context, MessageModels.SaveTemplateRequest input, String traceId) {
        require(context, "MANAGE");
        if (context.systemId() == null) return platformMessages.saveTemplate(context, input, traceId);
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
        if (context.systemId() == null) return platformMessages.publishTemplate(context, templateId, traceId);
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

    /** 内部摘要继续使用首屏，避免分页参数扩散到仪表盘等只读消费者。 */
    @Transactional(readOnly = true)
    public MessageModels.InboxView list(
            AuthenticatedContext context, String requestedStatus, String requestedSourceType) {
        return list(context, requestedStatus, requestedSourceType, 1, 20);
    }

    @Transactional(readOnly = true)
    public MessageModels.InboxView list(
            AuthenticatedContext context, String requestedStatus, String requestedSourceType,
            int page, int pageSize) {
        require(context, "VIEW");
        if (context.systemId() == null) return platformMessages.list(context, requestedStatus, requestedSourceType, page, pageSize);
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw invalid("MESSAGE_PAGE_INVALID", "消息页码必须从 1 开始，每页最多 100 条");
        }
        String status = requestedStatus.strip().toUpperCase(Locale.ROOT);
        if (!INBOX_STATUSES.contains(status)) throw invalid("MESSAGE_STATUS_INVALID", "消息状态筛选无效");
        String sourceType = requestedSourceType.strip().toUpperCase(Locale.ROOT);
        if (!"ALL".equals(sourceType) && !sourceType.matches("[A-Z][A-Z0-9_]{1,63}")) {
            throw invalid("MESSAGE_SOURCE_TYPE_INVALID", "消息来源筛选无效");
        }
        List<Map.Entry<MsgRecipient, MsgMessage>> matches = ownedRecipients(context).stream()
                .filter(recipient -> matchesStatus(recipient, status))
                .map(recipient -> Map.entry(recipient, messageService.selectById(recipient.getMessageId())))
                .filter(entry -> entry.getValue() != null && inContext(entry.getValue(), context))
                .filter(entry -> "ALL".equals(sourceType) || sourceType.equals(entry.getValue().getSourceType()))
                .sorted(Comparator.comparing((Map.Entry<MsgRecipient, MsgMessage> entry) -> entry.getValue().getCreateTime(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        int from = Math.min((page - 1) * pageSize, matches.size());
        int to = Math.min(from + pageSize, matches.size());
        List<MessageModels.MessageView> views = matches.subList(from, to).stream()
                .map(entry -> messageView(context, entry.getValue(), entry.getKey(), null)).toList();
        return new MessageModels.InboxView(views, countUnread(context), matches.size(), page, pageSize,
                page > 1, to < matches.size());
    }

    @Transactional(readOnly = true)
    public List<MessageModels.PreferenceView> preferences(AuthenticatedContext context) {
        require(context, "VIEW");
        if (context.systemId() == null) return platformMessages.preferences(context);
        Map<String, MsgUserPreference> stored = preferenceRows(context).stream()
                .filter(item -> "*".equals(item.getEventCode()))
                .collect(java.util.stream.Collectors.toMap(MsgUserPreference::getChannel, item -> item,
                        (left, right) -> left, LinkedHashMap::new));
        return EXTERNAL_CHANNELS.stream().map(channel -> {
            MsgUserPreference item = stored.get(channel);
            return item == null ? new MessageModels.PreferenceView(channel, true, null, null, null)
                    : preferenceView(item);
        }).toList();
    }

    @Transactional
    public MessageModels.PreferenceView savePreference(
            AuthenticatedContext context, MessageModels.SavePreferenceRequest input, String traceId) {
        require(context, "VIEW");
        if (context.systemId() == null) return platformMessages.savePreference(context, input, traceId);
        boolean hasStart = input.quietStart() != null && !input.quietStart().isBlank();
        boolean hasEnd = input.quietEnd() != null && !input.quietEnd().isBlank();
        if (hasStart != hasEnd || (hasStart && input.quietStart().equals(input.quietEnd()))) {
            throw invalid("MESSAGE_QUIET_HOURS_INVALID", "免打扰开始和结束时间必须同时填写且不能相同");
        }
        MsgUserPreference item = preferenceRows(context).stream()
                .filter(row -> "*".equals(row.getEventCode()) && input.channel().equals(row.getChannel()))
                .findFirst().orElse(null);
        if (item == null) {
            if (input.expectedVersion() != null) {
                throw conflict("MESSAGE_PREFERENCE_VERSION_CONFLICT", "通知偏好已经变化，请刷新后重试");
            }
            item = new MsgUserPreference();
            item.setAccountId(context.accountId());
            item.setContextType(contextType(context));
            item.setSystemId(context.systemId());
            item.setEventCode("*");
            item.setChannel(input.channel());
            item.setVersion(0);
            item.setEnabled(input.enabled());
            item.setQuietHoursJson(hasStart
                    ? toJson(Map.of("start", input.quietStart(), "end", input.quietEnd())) : "{}");
            preferenceService.insert(item);
        } else {
            if (input.expectedVersion() == null || !input.expectedVersion().equals(item.getVersion())) {
                throw conflict("MESSAGE_PREFERENCE_VERSION_CONFLICT", "通知偏好已经变化，请刷新后重试");
            }
            item.setEnabled(input.enabled());
            item.setQuietHoursJson(hasStart
                    ? toJson(Map.of("start", input.quietStart(), "end", input.quietEnd())) : "{}");
            if (preferenceService.updateById(item) != 1) {
                throw conflict("MESSAGE_PREFERENCE_VERSION_CONFLICT", "通知偏好已经变化，请刷新后重试");
            }
            item = preferenceService.selectById(item.getId());
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MESSAGE_PREFERENCE_SAVED", "MESSAGE_PREFERENCE", item.getId().toString(), "SUCCESS",
                Map.of("channel", item.getChannel(), "enabled", item.getEnabled(), "quietHours", hasStart));
        return preferenceView(item);
    }

    @Transactional(readOnly = true)
    public MessageModels.UnreadCount unreadCount(AuthenticatedContext context) {
        require(context, "VIEW");
        if (context.systemId() == null) return platformMessages.unreadCount(context);
        return new MessageModels.UnreadCount(countUnread(context));
    }

    @Transactional
    public MessageModels.MessageView send(
            AuthenticatedContext context, MessageModels.SendEventRequest input, String traceId) {
        requireAny(context, "SEND", "MANAGE");
        if (context.systemId() == null) return platformMessages.send(context, input, traceId);
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
        message.setCreatedByMemberId(context.memberId());
        messageService.insert(message);
        MsgRecipient first = null;
        for (PlatformAccount account : accounts) {
            MsgRecipient recipient = insertRecipient(context, message, account);
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
        if (context.systemId() == null) {
            platformMessages.notifyEvent(context, "FLOW", eventKey, subject, content, recipientAccountIds,
                    "FLOW_INSTANCE", instanceId.toString(), "/platform/flows?instanceId=" + instanceId);
            return;
        }
        List<Long> ids = distinctRecipients(recipientAccountIds);
        FlowInstance instance = flowInstanceService.selectById(instanceId);
        var original = instance == null || context.systemId() == null ? null
                : actionCoordinator.getObject().notificationAction(instance);
        if (original != null && !Objects.equals(original.input().actor().tenantId(), instance.getTenantId())
                && ids.contains(original.input().actor().accountId())) {
            // 外租户原申请人只收到自己上下文中的通用进度通知，不接收来源节点、错误和业务快照。
            var actor = original.input().actor();
            String target = original.input().moduleCode() + ":" + original.actionId();
            insertFlowNotification(actor, eventKey, "申请进度已更新",
                    "你提交的业务操作审批进度已更新，请查看当前处理结果。", List.of(actor.accountId()),
                    "BUSINESS_ACTION", target, "/systems/" + actor.systemId() + "?workspace=applications&module="
                            + original.input().moduleCode() + "&actionId=" + original.actionId());
            ids = ids.stream().filter(id -> !id.equals(actor.accountId())).toList();
        }
        if (ids.isEmpty()) return;
        insertFlowNotification(context, eventKey, subject, content, ids, "FLOW_INSTANCE", instanceId.toString(),
                context.systemId() == null ? "/platform/flows?instanceId=" + instanceId
                        : "/systems/" + context.systemId() + "?workspace=flow&instanceId=" + instanceId);
    }

    /** 同一运行事件可分别通知来源审批人和外租户原申请人，各自按实际上下文去重。 */
    private void insertFlowNotification(AuthenticatedContext context, String eventKey, String subject,
            String content, List<Long> ids, String targetType, String targetId, String route) {
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
        message.setTargetType(targetType);
        message.setTargetId(targetId);
        message.setTargetRoute(route);
        message.setSensitivity("NORMAL");
        message.setCreatedByAccountId(context.accountId());
        message.setCreatedByMemberId(context.memberId());
        messageService.insert(message);
        for (PlatformAccount account : accounts) {
            MsgRecipient recipient = insertRecipient(context, message, account);
            insertInAppDelivery(message, account);
        }
    }

    @Transactional
    public void notifyWorkTaskEvent(
            AuthenticatedContext context, WorkTask task, String eventKey,
            String subject, String content, List<Long> recipientAccountIds) {
        if (context == null || task == null || eventKey == null || eventKey.isBlank()
                || recipientAccountIds == null || recipientAccountIds.isEmpty()) return;
        if (context.systemId() == null) {
            platformMessages.notifyEvent(context, "WORK_TASK", eventKey, subject, content, recipientAccountIds,
                    "WORK_TASK", task.getId().toString(), "/platform/tasks?taskId=" + task.getId());
            return;
        }
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
        message.setCreatedByMemberId(context.memberId());
        messageService.insert(message);
        for (PlatformAccount account : accounts) {
            MsgRecipient recipient = insertRecipient(context, message, account);
            insertInAppDelivery(message, account);
        }
    }

    /**
     * 业务评论使用来源租户的站内消息，和评论、审计一起提交；不要求发表评论者拥有消息管理权限。
     * 收件人再次按当前账号、系统成员及租户成员状态过滤，防止停用身份收到新消息。
     */
    @Transactional
    public void notifyRuntimeRecordComment(AuthenticatedContext context, String moduleCode, Long recordId,
                                           Long commentId, boolean reply, List<Long> recipientAccountIds) {
        if (context == null || context.systemId() == null || context.tenantId() == null
                || recordId == null || commentId == null || recipientAccountIds == null
                || recipientAccountIds.isEmpty()) return;
        String eventKey = "comment-" + commentId;
        if (findEvent(context, "RUNTIME_RECORD_COMMENT", eventKey) != null) return;
        List<PlatformAccount> accounts = recipientAccountIds.stream().filter(Objects::nonNull).distinct()
                .filter(id -> !id.equals(context.accountId()))
                .map(accountService::selectById)
                .filter(account -> account != null && "ACTIVE".equals(account.getStatus()))
                .filter(account -> {
                    SystemMember member = memberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                                    .eq(SystemMember::getSystemId, context.systemId())
                                    .eq(SystemMember::getAccountId, account.getId())
                                    .eq(SystemMember::getStatus, "ACTIVE"))
                            .stream().findFirst().orElse(null);
                    return member != null && tenantMemberService.selectCount(Wrappers.<SystemTenantMember>lambdaQuery()
                            .eq(SystemTenantMember::getSystemId, context.systemId())
                            .eq(SystemTenantMember::getTenantId, context.tenantId())
                            .eq(SystemTenantMember::getSystemMemberId, member.getId())
                            .eq(SystemTenantMember::getStatus, "ACTIVE")) > 0;
                }).toList();
        if (accounts.isEmpty()) return;
        MsgMessage message = new MsgMessage();
        message.setContextType("SYSTEM");
        message.setPlatformId(context.platformId());
        message.setSystemId(context.systemId());
        message.setTenantId(context.tenantId());
        message.setTemplateVersionId(null);
        message.setSourceType("RUNTIME_RECORD_COMMENT");
        message.setSourceId(eventKey);
        // 成员有效并不代表仍有此记录的详情权限，通知正文不复制记录标题或评论内容。
        message.setSubject(reply ? "业务记录有新回复" : "业务记录有新评论");
        message.setContentText("您参与的业务记录有新" + (reply ? "回复" : "评论") + "，请进入系统查看。");
        message.setTargetType("RUNTIME_RECORD");
        message.setTargetId(moduleCode + ":" + recordId);
        message.setTargetRoute("/systems/" + context.systemId() + "?workspace=runtime&module=" + moduleCode
                + "&recordId=" + recordId);
        message.setSensitivity("NORMAL");
        message.setCreatedByAccountId(context.accountId());
        message.setCreatedByMemberId(context.memberId());
        messageService.insert(message);
        for (PlatformAccount account : accounts) {
            insertRecipient(context, message, account);
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
        message.setCreatedByMemberId(context.memberId());
        messageService.insert(message);
        MsgRecipient recipient = insertRecipient(context, message, account);
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
        message.setCreatedByMemberId(context.memberId());
        messageService.insert(message);
        MsgRecipient recipient = insertRecipient(context, message, account);
        insertInAppDelivery(message, account);
        auditRecorder.record(requestId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "CRITICAL_EXCEPTION_MESSAGE_CREATED", "MESSAGE", message.getId().toString(), "PENDING",
                Map.of("errorCode", errorCode, "requestId", requestId, "recipientScope", "CURRENT_OPERATIONS_ADMIN"));
    }

    @Transactional
    public MessageModels.MessageView notifyKpiUnderTarget(
            AuthenticatedContext context, Long kpiId, Long resultId, String kpiName,
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
        // 接收人的业务数据权限可能小于计算人的权限，通知不携带计算人可见的汇总值。
        message.setContentText("目标未达标；请打开 KPI 查看当前授权口径、实际值和下钻数据。");
        message.setTargetType("SYSTEM_ROUTE");
        message.setTargetId(kpiId.toString());
        message.setTargetRoute("/systems/" + context.systemId() + "?workspace=kpi&kpiId=" + kpiId);
        message.setSensitivity("NORMAL");
        message.setCreatedByAccountId(context.accountId());
        message.setCreatedByMemberId(context.memberId());
        messageService.insert(message);
        MsgRecipient first = null;
        for (PlatformAccount account : accounts) {
            MsgRecipient recipient = insertRecipient(context, message, account);
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
        if (context.systemId() == null) return platformMessages.read(context, messageId, traceId);
        Owned owned = requireOwned(context, messageId, true);
        if (!"ARCHIVED".equals(owned.recipient().getStatus()) && !"READ".equals(owned.recipient().getStatus())) {
            owned.recipient().setStatus("READ");
            owned.recipient().setReadTime(LocalDateTime.now());
            updateRecipient(owned.recipient());
            auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                    "MESSAGE_READ", "MESSAGE", messageId.toString(), "SUCCESS", Map.of());
        }
        return messageView(context, owned.message(), recipientService.selectById(owned.recipient().getId()), traceId);
    }

    @Transactional
    public MessageModels.MessageView archive(AuthenticatedContext context, Long messageId, String traceId) {
        require(context, "VIEW");
        if (context.systemId() == null) return platformMessages.archive(context, messageId, traceId);
        Owned owned = requireOwned(context, messageId, true);
        if (!"ARCHIVED".equals(owned.recipient().getStatus())) {
            owned.recipient().setStatus("ARCHIVED");
            if (owned.recipient().getReadTime() == null) owned.recipient().setReadTime(LocalDateTime.now());
            owned.recipient().setArchiveTime(LocalDateTime.now());
            updateRecipient(owned.recipient());
            auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                    "MESSAGE_ARCHIVED", "MESSAGE", messageId.toString(), "SUCCESS", Map.of());
        }
        return messageView(context, owned.message(), recipientService.selectById(owned.recipient().getId()), traceId);
    }

    @Transactional
    public MessageModels.UnreadCount readAll(AuthenticatedContext context, String traceId) {
        require(context, "VIEW");
        if (context.systemId() == null) return platformMessages.readAll(context, traceId);
        int changed = 0;
        for (MsgRecipient recipient : ownedRecipients(context)) {
            if (!"UNREAD".equals(recipient.getStatus())) continue;
            recipient.setStatus("READ");
            recipient.setReadTime(LocalDateTime.now());
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
        if (context.systemId() == null) return platformMessages.open(context, messageId, traceId);
        Owned owned = requireOwned(context, messageId, false);
        String route = validateTarget(context, owned.message().getTargetType(), owned.message().getTargetId(),
                owned.message().getTargetRoute(), traceId);
        if (route == null) throw conflict("MESSAGE_TARGET_UNAVAILABLE", "消息没有可打开的目标");
        if (owned.recipient().getReadTime() == null) {
            owned.recipient().setStatus("READ");
            owned.recipient().setReadTime(LocalDateTime.now());
            updateRecipient(owned.recipient());
        }
        Map<String, Object> targetAudit = new LinkedHashMap<>();
        targetAudit.put("targetType", owned.message().getTargetType());
        targetAudit.put("targetId", owned.message().getTargetId());
        targetAudit.put("resolvedRoute", route);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MESSAGE_TARGET_OPENED", "MESSAGE", messageId.toString(), "SUCCESS", targetAudit);
        String type = owned.message().getTargetType();
        String id = owned.message().getTargetId();
        // 保留旧通知记录；历史来源实例消息在当前授权下解析为本人原动作入口。
        if ("FLOW_INSTANCE".equals(type) && context.systemId() != null
                && route.startsWith("/systems/" + context.systemId() + "?workspace=applications&")) {
            var original = actionCoordinator.getObject().notificationAction(flowInstanceService.selectById(numericTarget(id)));
            type = "BUSINESS_ACTION";
            id = original.input().moduleCode() + ":" + original.actionId();
        }
        return new MessageModels.OpenResult(messageId, route, context.systemId(), type, id);
    }

    @Transactional
    public MessageModels.MessageView retryDelivery(AuthenticatedContext context, Long deliveryId, String traceId) {
        require(context, "MANAGE");
        if (context.systemId() == null) return platformMessages.retryDelivery(context, deliveryId, traceId);
        MsgDelivery delivery = deliveryService.selectList(Wrappers.<MsgDelivery>lambdaQuery()
                        .eq(MsgDelivery::getId, deliveryId).last("FOR UPDATE"))
                .stream().findFirst().orElseThrow(() -> notFound("MESSAGE_DELIVERY_NOT_FOUND", "消息投递记录不存在"));
        MsgMessage message = messageService.selectById(delivery.getMessageId());
        if (message == null || !inContext(message, context)) {
            throw notFound("MESSAGE_DELIVERY_NOT_FOUND", "消息投递记录不存在");
        }
        if ("IN_APP".equals(delivery.getChannel()) || !Set.of("RETRY_PENDING", "QUIET_PENDING").contains(delivery.getStatus())
                || delivery.getAttemptCount() >= MAX_DELIVERY_ATTEMPTS) {
            throw conflict("MESSAGE_DELIVERY_NOT_RETRYABLE", "该投递记录不需要重试");
        }
        MsgUserPreference preference = preferenceFor(message, delivery.getAccountId(), delivery.getChannel());
        LocalDateTime quietEnd = quietEnd(preference, LocalDateTime.now());
        if (quietEnd != null) {
            delivery.setStatus("QUIET_PENDING");
            delivery.setNextAttemptTime(quietEnd);
            delivery.setUpdateTime(LocalDateTime.now());
            if (deliveryService.updateById(delivery) != 1) {
                throw conflict("MESSAGE_DELIVERY_VERSION_CONFLICT", "投递记录已变化，请刷新后重试");
            }
            auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                    "MESSAGE_DELIVERY_DEFERRED", "MESSAGE_DELIVERY", deliveryId.toString(), "QUIET_PENDING",
                    Map.of("channel", delivery.getChannel(), "nextAttemptAt", quietEnd.toString()));
            MsgRecipient recipient = recipientService.selectList(Wrappers.<MsgRecipient>lambdaQuery()
                            .eq(MsgRecipient::getMessageId, message.getId())
                            .eq(MsgRecipient::getAccountId, delivery.getAccountId()))
                    .stream().findFirst().orElseThrow(() -> conflict("MESSAGE_RECIPIENT_NOT_FOUND", "消息接收记录不存在"));
            return messageView(context, message, recipient, traceId);
        }
        int attempt = delivery.getAttemptCount() + 1;
        delivery.setAttemptCount(attempt);
        delivery.setProviderReceiptJson(toJson(Map.of("result", "PROVIDER_UNAVAILABLE", "attempt", attempt)));
        delivery.setLastError("CHANNEL_PROVIDER_UNAVAILABLE");
        delivery.setUpdateTime(LocalDateTime.now());
        if (attempt >= MAX_DELIVERY_ATTEMPTS) {
            delivery.setStatus("FAILED");
            delivery.setNextAttemptTime(null);
        } else {
            delivery.setStatus("RETRY_PENDING");
            delivery.setNextAttemptTime(LocalDateTime.now().plusMinutes(attempt));
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
        if (context.systemId() == null) return platformMessages.deliveryDiagnostics(context, messageId);
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
                latest == null ? null : latest.getPublishTime(), template.getVersion(), template.getUpdateTime());
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

    /** 系统消息接收人必须同时记录系统、租户和成员，保证收件箱按当前身份隔离。 */
    private MsgRecipient insertRecipient(AuthenticatedContext context, MsgMessage message, PlatformAccount account) {
        SystemMember member = memberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                        .eq(SystemMember::getSystemId, context.systemId())
                        .eq(SystemMember::getAccountId, account.getId())
                        .eq(SystemMember::getStatus, "ACTIVE"))
                .stream().findFirst().orElseThrow(() -> invalid("MESSAGE_RECIPIENT_CONTEXT_INVALID", "接收人不是当前系统成员"));
        MsgRecipient recipient = new MsgRecipient();
        recipient.setMessageId(message.getId());
        recipient.setSystemId(context.systemId());
        recipient.setTenantId(context.tenantId());
        recipient.setAccountId(account.getId());
        recipient.setSystemMemberId(member.getId());
        recipient.setStatus("UNREAD");
        recipient.setVersion(0);
        recipientService.insert(recipient);
        return recipient;
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
        delivery.setSendTime(LocalDateTime.now());
        delivery.setDeliverTime(LocalDateTime.now());
        delivery.setVersion(0);
        deliveryService.insert(delivery);
    }

    private void insertExternalDelivery(MsgMessage message, PlatformAccount account, String channel) {
        MsgUserPreference preference = preferenceFor(message, account.getId(), channel);
        boolean enabled = preference == null || Boolean.TRUE.equals(preference.getEnabled());
        LocalDateTime quietEnd = enabled ? quietEnd(preference, LocalDateTime.now()) : null;
        MsgDelivery delivery = new MsgDelivery();
        delivery.setMessageId(message.getId());
        delivery.setAccountId(account.getId());
        delivery.setChannel(channel);
        delivery.setDestinationMasked(maskDestination(account, channel));
        if (!enabled) {
            delivery.setStatus("SKIPPED");
            delivery.setAttemptCount(0);
            delivery.setProviderReceiptJson(toJson(Map.of("result", "USER_CHANNEL_DISABLED")));
            delivery.setLastError("USER_CHANNEL_DISABLED");
        } else if (quietEnd != null) {
            delivery.setStatus("QUIET_PENDING");
            delivery.setAttemptCount(0);
            delivery.setNextAttemptTime(quietEnd);
            delivery.setProviderReceiptJson(toJson(Map.of("result", "USER_QUIET_HOURS")));
            delivery.setLastError("USER_QUIET_HOURS");
        } else {
            delivery.setStatus("RETRY_PENDING");
            delivery.setAttemptCount(1);
            delivery.setNextAttemptTime(LocalDateTime.now().plusMinutes(1));
            delivery.setProviderReceiptJson(toJson(Map.of("result", "PROVIDER_UNAVAILABLE", "attempt", 1)));
            delivery.setLastError("CHANNEL_PROVIDER_UNAVAILABLE");
        }
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
                if (instance == null) {
                    throw notFound("MESSAGE_TARGET_NOT_FOUND", "消息关联的 Flow 实例不存在");
                }
                if (!sameContext(instance.getContextType(), instance.getPlatformId(),
                        instance.getSystemId(), instance.getTenantId(), context)) {
                    // 旧来源申请消息仍从真实 START/outbox 恢复本人关联，不迁移快照或放宽来源实例权限。
                    var original = actionCoordinator.getObject().notificationAction(instance);
                    if (original == null || !Objects.equals(original.input().actor().accountId(), context.accountId())
                            || !Objects.equals(original.input().actor().tenantId(), context.tenantId())) {
                        throw notFound("MESSAGE_TARGET_NOT_FOUND", "消息关联的 Flow 实例不存在");
                    }
                    flowRuntime.getObject().businessActionProgress(context, original.input().moduleCode(), original.actionId(), traceId);
                    return "/systems/" + context.systemId() + "?workspace=applications&module="
                            + original.input().moduleCode() + "&actionId=" + original.actionId();
                }
                // 使用实际运行时可见性，不能用不存在的 VIEW 动作或旧消息快照代替当前审批权限。
                flowRuntime.getObject().instance(context, instanceId);
                return context.systemId() == null ? "/platform/flows?instanceId=" + instanceId
                        : "/systems/" + context.systemId() + "?workspace=flow&instanceId=" + instanceId;
            }
            case "BUSINESS_ACTION" -> {
                if (context.systemId() == null || targetId == null
                        || !targetId.matches("[a-z][a-z0-9_-]{0,99}:[1-9][0-9]*")) {
                    throw forbidden("MESSAGE_TARGET_CONTEXT_INVALID", "申请消息只能属于当前系统上下文");
                }
                String[] parts = targetId.split(":", 2);
                flowRuntime.getObject().businessActionProgress(context, parts[0], numericTarget(parts[1]), traceId);
                return "/systems/" + context.systemId() + "?workspace=applications&module=" + parts[0]
                        + "&actionId=" + parts[1];
            }
            case "RUNTIME_RECORD" -> {
                if (context.systemId() == null || targetId == null || !targetId.matches("[a-z][a-z0-9_-]{0,99}:[1-9][0-9]*")) {
                    throw forbidden("MESSAGE_TARGET_CONTEXT_INVALID", "业务记录消息只能属于当前系统上下文");
                }
                String[] parts = targetId.split(":", 2);
                runtimeTargetAccessReader.detail(context, parts[0], Long.valueOf(parts[1]), traceId);
                return "/systems/" + context.systemId() + "?workspace=runtime&module=" + parts[0]
                        + "&recordId=" + parts[1];
            }
            case "WORK_TASK" -> {
                Long taskId = numericTarget(targetId);
                WorkTask task = workTaskService.selectById(taskId);
                if (task == null || !sameContext(task.getContextType(), task.getPlatformId(), task.getSystemId(),
                        task.getTenantId(), context)) throw notFound("MESSAGE_TARGET_NOT_FOUND", "消息关联的工作任务不存在");
                // 任务消息只提供入口；当前角色、任务参与关系和关联业务展示由任务详情统一校验。
                workManagement.getObject().task(context, taskId);
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
        String subject = message.getSubject();
        String content = message.getContentText();
        if ("WORK_TASK".equals(message.getTargetType()) && content != null) {
            // 旧任务消息曾附带业务标题；任务收件人未必拥有该记录的当前读取权限。
            int businessIndex = content.indexOf("；关联业务：");
            if (businessIndex >= 0) content = content.substring(0, businessIndex);
        }
        if ("FLOW_INSTANCE".equals(message.getTargetType()) && context.systemId() != null) {
            FlowInstance flow = flowInstanceService.selectById(numericTarget(message.getTargetId()));
            if (flow != null && Objects.equals(flow.getSystemId(), context.systemId())
                    && Objects.equals(flow.getStartedByAccountId(), context.accountId())
                    && !Objects.equals(flow.getTenantId(), message.getTenantId())) {
                // 历史跨租户通知只投影通用进度文本，原来源意见和异常摘要留在存储中，不再回显给申请人。
                subject = "申请进度已更新";
                content = "你提交的业务操作审批进度已更新，请查看当前处理结果。";
            }
        }
        List<MessageModels.RecipientDeliveryView> deliveries = deliveryService.selectList(
                        Wrappers.<MsgDelivery>lambdaQuery().eq(MsgDelivery::getMessageId, message.getId())
                                .eq(MsgDelivery::getAccountId, recipient.getAccountId()).orderByAsc(MsgDelivery::getId))
                .stream().map(item -> new MessageModels.RecipientDeliveryView(item.getChannel(),
                        item.getDestinationMasked(), item.getStatus(), item.getAttemptCount(), item.getNextAttemptTime(),
                        safeDeliveryReason(item.getLastError()))).toList();
        return new MessageModels.MessageView(message.getId(), message.getContextType(), message.getPlatformId(),
                message.getSystemId(), message.getTenantId(), message.getTemplateVersionId(), message.getSourceType(),
                message.getSourceId(), subject, content, message.getTargetType(),
                message.getTargetId(), message.getTargetRoute(), message.getSensitivity(), recipient.getStatus(),
                recipient.getReadTime(), recipient.getArchiveTime(), recipient.getVersion(), message.getCreateTime(),
                actorName(message.getCreatedByAccountId()), targetAccessible, deliveries);
    }

    private List<MsgUserPreference> preferenceRows(AuthenticatedContext context) {
        var query = Wrappers.<MsgUserPreference>lambdaQuery()
                .eq(MsgUserPreference::getAccountId, context.accountId())
                .eq(MsgUserPreference::getContextType, contextType(context));
        if (context.systemId() == null) query.isNull(MsgUserPreference::getSystemId);
        else query.eq(MsgUserPreference::getSystemId, context.systemId());
        return preferenceService.selectList(query);
    }

    private MsgUserPreference preferenceFor(MsgMessage message, Long accountId, String channel) {
        var query = Wrappers.<MsgUserPreference>lambdaQuery()
                .eq(MsgUserPreference::getAccountId, accountId)
                .eq(MsgUserPreference::getContextType, message.getContextType())
                .eq(MsgUserPreference::getChannel, channel)
                .in(MsgUserPreference::getEventCode, message.getSourceType(), "*");
        if (message.getSystemId() == null) query.isNull(MsgUserPreference::getSystemId);
        else query.eq(MsgUserPreference::getSystemId, message.getSystemId());
        return preferenceService.selectList(query).stream()
                .sorted(Comparator.comparing(item -> "*".equals(item.getEventCode()) ? 1 : 0))
                .findFirst().orElse(null);
    }

    private MessageModels.PreferenceView preferenceView(MsgUserPreference item) {
        QuietHours quiet = quietHours(item);
        return new MessageModels.PreferenceView(item.getChannel(), Boolean.TRUE.equals(item.getEnabled()),
                quiet == null ? null : quiet.start().toString(), quiet == null ? null : quiet.end().toString(),
                item.getVersion());
    }

    private QuietHours quietHours(MsgUserPreference item) {
        if (item == null || item.getQuietHoursJson() == null || item.getQuietHoursJson().isBlank()
                || "{}".equals(item.getQuietHoursJson())) return null;
        try {
            Map<String, String> value = objectMapper.readValue(item.getQuietHoursJson(), new TypeReference<>() { });
            if (value.get("start") == null || value.get("end") == null) return null;
            return new QuietHours(LocalTime.parse(value.get("start")), LocalTime.parse(value.get("end")));
        } catch (RuntimeException | JsonProcessingException exception) {
            return null;
        }
    }

    private LocalDateTime quietEnd(MsgUserPreference item, LocalDateTime now) {
        QuietHours quiet = quietHours(item);
        if (quiet == null) return null;
        LocalTime current = now.toLocalTime();
        boolean wraps = quiet.start().isAfter(quiet.end());
        boolean active = wraps ? !current.isBefore(quiet.start()) || current.isBefore(quiet.end())
                : !current.isBefore(quiet.start()) && current.isBefore(quiet.end());
        if (!active) return null;
        return current.isBefore(quiet.end()) ? now.toLocalDate().atTime(quiet.end())
                : now.toLocalDate().plusDays(1).atTime(quiet.end());
    }

    private String safeDeliveryReason(String code) {
        if (code == null) return null;
        return switch (code) {
            case "USER_CHANNEL_DISABLED" -> "已按你的通知偏好关闭该外部渠道";
            case "USER_QUIET_HOURS" -> "当前处于免打扰时段";
            case "CHANNEL_PROVIDER_UNAVAILABLE" -> "外部渠道暂不可用，等待重试";
            default -> "投递未完成，请稍后查看";
        };
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
                delivery.getStatus(), delivery.getAttemptCount(), delivery.getNextAttemptTime(),
                delivery.getProviderMessageId(), delivery.getProviderReceiptJson(), delivery.getLastError(),
                delivery.getSendTime(), delivery.getDeliverTime(), delivery.getVersion());
    }

    private void updateRecipient(MsgRecipient recipient) {
        recipient.setUpdateTime(LocalDateTime.now());
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

    private record QuietHours(LocalTime start, LocalTime end) {
    }

    private record TemplateSnapshot(
            String code, String name, String channel, String subjectTemplate, String contentTemplate,
            List<String> requiredVariables, String contextType, Long platformId, Long systemId, Long tenantId) {
    }
}
