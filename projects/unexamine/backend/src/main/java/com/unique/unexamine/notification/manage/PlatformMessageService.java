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
import com.unique.unexamine.notification.base.entity.PlatMsgDelivery;
import com.unique.unexamine.notification.base.entity.PlatMsgMessage;
import com.unique.unexamine.notification.base.entity.PlatMsgRecipient;
import com.unique.unexamine.notification.base.entity.PlatMsgTemplate;
import com.unique.unexamine.notification.base.entity.PlatMsgTemplateVersion;
import com.unique.unexamine.notification.base.entity.PlatMsgUserPreference;
import com.unique.unexamine.notification.base.service.PlatMsgDeliveryBaseService;
import com.unique.unexamine.notification.base.service.PlatMsgMessageBaseService;
import com.unique.unexamine.notification.base.service.PlatMsgRecipientBaseService;
import com.unique.unexamine.notification.base.service.PlatMsgTemplateBaseService;
import com.unique.unexamine.notification.base.service.PlatMsgTemplateVersionBaseService;
import com.unique.unexamine.notification.base.service.PlatMsgUserPreferenceBaseService;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.entity.PlatformMember;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.platform.base.service.PlatformMemberBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.work.base.entity.WorkTask;
import com.unique.unexamine.work.base.service.WorkTaskBaseService;
import com.unique.unexamine.work.manage.WorkManagementService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 平台消息只访问 plat_msg_* 表；入口权限和事务由 MessageService 统一承担。 */
@Service
public class PlatformMessageService {
    private static final List<String> EXTERNAL_CHANNELS = List.of(
            "EMAIL", "SMS", "WEBHOOK", "WECOM", "FEISHU", "DINGTALK");
    private static final Set<String> INBOX_STATUSES = Set.of("ACTIVE", "UNREAD", "READ", "ARCHIVED", "ALL");
    private static final int MAX_DELIVERY_ATTEMPTS = 3;

    private final PlatMsgTemplateBaseService templates;
    private final PlatMsgTemplateVersionBaseService versions;
    private final PlatMsgMessageBaseService messages;
    private final PlatMsgRecipientBaseService recipients;
    private final PlatMsgDeliveryBaseService deliveries;
    private final PlatMsgUserPreferenceBaseService preferences;
    private final PlatformAccountBaseService accounts;
    private final PlatformMemberBaseService members;
    private final FlowInstanceBaseService flowInstances;
    private final WorkTaskBaseService workTasks;
    private final ObjectProvider<FlowRuntimeService> flowRuntime;
    /** 平台任务消息跳转沿用任务详情的实时权限，避免独立维护一套资源编码。 */
    private final ObjectProvider<WorkManagementService> workManagement;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public PlatformMessageService(PlatMsgTemplateBaseService templates,
            PlatMsgTemplateVersionBaseService versions, PlatMsgMessageBaseService messages,
            PlatMsgRecipientBaseService recipients, PlatMsgDeliveryBaseService deliveries,
            PlatMsgUserPreferenceBaseService preferences, PlatformAccountBaseService accounts,
            PlatformMemberBaseService members, FlowInstanceBaseService flowInstances,
            WorkTaskBaseService workTasks, ObjectProvider<FlowRuntimeService> flowRuntime,
            ObjectProvider<WorkManagementService> workManagement,
            PermissionChecker permissionChecker, AuditRecorder auditRecorder, ObjectMapper objectMapper) {
        this.templates = templates;
        this.versions = versions;
        this.messages = messages;
        this.recipients = recipients;
        this.deliveries = deliveries;
        this.preferences = preferences;
        this.accounts = accounts;
        this.members = members;
        this.flowInstances = flowInstances;
        this.workTasks = workTasks;
        this.flowRuntime = flowRuntime;
        this.workManagement = workManagement;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    /** 模板编码只在本平台范围内唯一，系统消息模板由另一张表管理。 */
    public List<MessageModels.TemplateView> listTemplates(AuthenticatedContext context) {
        return templates.selectList(Wrappers.<PlatMsgTemplate>lambdaQuery()
                        .eq(PlatMsgTemplate::getPlatformId, context.platformId()))
                .stream().sorted(Comparator.comparing(PlatMsgTemplate::getUpdateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::templateView).toList();
    }

    public MessageModels.TemplateView saveTemplate(
            AuthenticatedContext context, MessageModels.SaveTemplateRequest input, String traceId) {
        List<String> required = input.requiredVariables() == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(input.requiredVariables()));
        if (required.size() > 50) throw invalid("MESSAGE_TEMPLATE_VARIABLE_LIMIT", "模板变量不能超过 50 个");
        PlatMsgTemplate template;
        if (input.id() == null) {
            boolean exists = templates.selectCount(Wrappers.<PlatMsgTemplate>lambdaQuery()
                    .eq(PlatMsgTemplate::getPlatformId, context.platformId())
                    .eq(PlatMsgTemplate::getCode, input.code())
                    .eq(PlatMsgTemplate::getChannel, input.channel())) > 0;
            if (exists) throw conflict("MESSAGE_TEMPLATE_EXISTS", "当前平台已存在相同编码和渠道的模板");
            template = new PlatMsgTemplate();
            template.setPlatformId(context.platformId());
            template.setCode(input.code());
            template.setChannel(input.channel());
            template.setDraftRevision(1);
            template.setCreatedByAccountId(context.accountId());
            template.setVersion(0);
        } else {
            template = requireTemplate(context, input.id(), false);
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
        template.setVariableSchemaJson(toJson(Map.of("required", required)));
        template.setStatus("DRAFT");
        if (input.id() == null) templates.insert(template);
        else if (templates.updateById(template) != 1) {
            throw conflict("MESSAGE_TEMPLATE_VERSION_CONFLICT", "消息模板已变化，请刷新后重试");
        }
        auditRecorder.record(traceId, context.accountId(), null, null, null,
                "MESSAGE_TEMPLATE_SAVED", "MESSAGE_TEMPLATE", template.getId().toString(), "SUCCESS",
                Map.of("code", template.getCode(), "channel", template.getChannel(),
                        "draftRevision", template.getDraftRevision()));
        return templateView(templates.selectById(template.getId()));
    }

    public MessageModels.TemplateView publishTemplate(AuthenticatedContext context, Long templateId, String traceId) {
        PlatMsgTemplate template = requireTemplate(context, templateId, true);
        if (template.getContentTemplate() == null || template.getContentTemplate().isBlank()) {
            throw invalid("MESSAGE_TEMPLATE_CONTENT_REQUIRED", "消息模板正文不能为空");
        }
        PlatMsgTemplateVersion latest = latestVersion(context.platformId(), templateId);
        if (latest != null && Objects.equals(latest.getDraftRevision(), template.getDraftRevision())) {
            throw conflict("MESSAGE_TEMPLATE_ALREADY_PUBLISHED", "当前草稿已经发布");
        }
        int number = latest == null ? 1 : latest.getVersionNumber() + 1;
        TemplateSnapshot snapshot = new TemplateSnapshot(template.getCode(), template.getName(),
                template.getChannel(), template.getSubjectTemplate(), template.getContentTemplate(),
                requiredVariables(template), "PLATFORM", context.platformId(), null, null);
        PlatMsgTemplateVersion version = new PlatMsgTemplateVersion();
        version.setTemplateId(templateId);
        version.setPlatformId(context.platformId());
        version.setVersionNumber(number);
        version.setDraftRevision(template.getDraftRevision());
        version.setSnapshotJson(toJson(snapshot));
        version.setPublishedByAccountId(context.accountId());
        versions.insert(version);
        template.setStatus("PUBLISHED");
        if (templates.updateById(template) != 1) {
            throw conflict("MESSAGE_TEMPLATE_VERSION_CONFLICT", "消息模板已变化，请刷新后重试");
        }
        auditRecorder.record(traceId, context.accountId(), null, null, null,
                "MESSAGE_TEMPLATE_PUBLISHED", "MESSAGE_TEMPLATE_VERSION", version.getId().toString(), "SUCCESS",
                Map.of("templateId", templateId, "versionNumber", number,
                        "draftRevision", template.getDraftRevision()));
        return templateView(templates.selectById(templateId));
    }

    public MessageModels.MessageView send(AuthenticatedContext context,
            MessageModels.SendEventRequest input, String traceId) {
        PlatMsgTemplate template = publishedTemplate(context, input.templateCode());
        PlatMsgTemplateVersion version = latestVersion(context.platformId(), template.getId());
        if (version == null) throw conflict("MESSAGE_TEMPLATE_NOT_PUBLISHED", "消息模板尚未发布");
        TemplateSnapshot snapshot = snapshot(version);
        Map<String, Object> variables = input.variables() == null ? Map.of() : new LinkedHashMap<>(input.variables());
        List<String> missing = snapshot.requiredVariables().stream()
                .filter(key -> !variables.containsKey(key) || variables.get(key) == null).toList();
        if (!missing.isEmpty()) {
            auditRecorder.recordFailure(traceId, context.accountId(), null, null, null,
                    "MESSAGE_RENDER_FAILED", "MESSAGE_TEMPLATE_VERSION", version.getId().toString(),
                    "MESSAGE_TEMPLATE_VARIABLE_MISSING", Map.of("missingVariables", missing));
            throw invalid("MESSAGE_TEMPLATE_VARIABLE_MISSING", "缺少模板变量：" + String.join("、", missing));
        }
        List<PlatformAccount> targets = resolveRecipients(context, input.recipients());
        validateTarget(context, input.targetType(), input.targetId(), input.targetRoute());
        PlatMsgMessage existing = findEvent(context, input.sourceType(), input.dedupKey());
        if (existing != null) return firstMessageView(context, existing, traceId);
        PlatMsgMessage message = newMessage(context, version.getId(), input.sourceType(), input.dedupKey(),
                render(snapshot.subjectTemplate(), variables, snapshot.name()),
                render(snapshot.contentTemplate(), variables, ""), input.targetType(), input.targetId(),
                input.targetRoute(), input.sensitivity() == null ? "NORMAL" : input.sensitivity());
        messages.insert(message);
        PlatMsgRecipient first = null;
        for (PlatformAccount account : targets) {
            PlatMsgRecipient recipient = insertRecipient(context, message, account);
            if (first == null) first = recipient;
            insertDelivery(message, account, "IN_APP");
            if (!"IN_APP".equals(snapshot.channel())) insertDelivery(message, account, snapshot.channel());
        }
        auditRecorder.record(traceId, context.accountId(), null, null, null,
                "MESSAGE_CREATED", "MESSAGE", message.getId().toString(), "SUCCESS",
                Map.of("templateVersionId", version.getId(), "recipientCount", targets.size(),
                        "channel", snapshot.channel(), "sourceType", input.sourceType()));
        return messageView(context, message, Objects.requireNonNull(first), traceId);
    }

    /** 平台内部事件不使用用户模板；V55 允许内部事件的模板版本为空。 */
    public void notifyEvent(AuthenticatedContext context, String sourceType, String sourceId,
            String subject, String content, List<Long> accountIds, String targetType, String targetId, String route) {
        if (findEvent(context, sourceType, sourceId) != null) return;
        List<PlatformAccount> targets = new ArrayList<>();
        for (Long accountId : new LinkedHashSet<>(accountIds)) {
            targets.add(requireRecipient(context, accountId));
        }
        if (targets.isEmpty()) return;
        PlatMsgMessage message = newMessage(context, null, sourceType, sourceId,
                subject, content, targetType, targetId, route, "NORMAL");
        messages.insert(message);
        for (PlatformAccount account : targets) {
            insertRecipient(context, message, account);
            insertDelivery(message, account, "IN_APP");
        }
    }

    public MessageModels.InboxView list(AuthenticatedContext context, String requestedStatus,
            String requestedSourceType, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw invalid("MESSAGE_PAGE_INVALID", "消息页码必须从 1 开始，每页最多 100 条");
        }
        String status = requestedStatus.strip().toUpperCase(java.util.Locale.ROOT);
        String sourceType = requestedSourceType.strip().toUpperCase(java.util.Locale.ROOT);
        if (!INBOX_STATUSES.contains(status)) throw invalid("MESSAGE_STATUS_INVALID", "消息状态筛选无效");
        if (!"ALL".equals(sourceType) && !sourceType.matches("[A-Z][A-Z0-9_]{1,63}")) {
            throw invalid("MESSAGE_SOURCE_TYPE_INVALID", "消息来源筛选无效");
        }
        List<Map.Entry<PlatMsgRecipient, PlatMsgMessage>> matches = ownedRecipients(context).stream()
                .filter(recipient -> matchesStatus(recipient, status))
                .map(recipient -> Map.entry(recipient, messages.selectById(recipient.getMessageId())))
                .filter(entry -> entry.getValue() != null
                        && Objects.equals(entry.getValue().getPlatformId(), context.platformId()))
                .filter(entry -> "ALL".equals(sourceType) || sourceType.equals(entry.getValue().getSourceType()))
                .sorted(Comparator.comparing((Map.Entry<PlatMsgRecipient, PlatMsgMessage> entry)
                                -> entry.getValue().getCreateTime(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        int from = (int) Math.min((long) (page - 1) * pageSize, matches.size());
        int to = Math.min(from + pageSize, matches.size());
        List<MessageModels.MessageView> views = matches.subList(from, to).stream()
                .map(entry -> messageView(context, entry.getValue(), entry.getKey(), null)).toList();
        return new MessageModels.InboxView(views, countUnread(context), matches.size(), page, pageSize,
                page > 1, to < matches.size());
    }

    public MessageModels.UnreadCount unreadCount(AuthenticatedContext context) {
        return new MessageModels.UnreadCount(countUnread(context));
    }

    public MessageModels.MessageView read(AuthenticatedContext context, Long messageId, String traceId) {
        Owned owned = requireOwned(context, messageId, true);
        if (!"ARCHIVED".equals(owned.recipient().getStatus()) && !"READ".equals(owned.recipient().getStatus())) {
            owned.recipient().setStatus("READ");
            owned.recipient().setReadTime(LocalDateTime.now());
            updateRecipient(owned.recipient());
            auditRecorder.record(traceId, context.accountId(), null, null, null,
                    "MESSAGE_READ", "MESSAGE", messageId.toString(), "SUCCESS", Map.of());
        }
        return messageView(context, owned.message(), recipients.selectById(owned.recipient().getId()), traceId);
    }

    public MessageModels.MessageView archive(AuthenticatedContext context, Long messageId, String traceId) {
        Owned owned = requireOwned(context, messageId, true);
        if (!"ARCHIVED".equals(owned.recipient().getStatus())) {
            owned.recipient().setStatus("ARCHIVED");
            if (owned.recipient().getReadTime() == null) owned.recipient().setReadTime(LocalDateTime.now());
            owned.recipient().setArchiveTime(LocalDateTime.now());
            updateRecipient(owned.recipient());
            auditRecorder.record(traceId, context.accountId(), null, null, null,
                    "MESSAGE_ARCHIVED", "MESSAGE", messageId.toString(), "SUCCESS", Map.of());
        }
        return messageView(context, owned.message(), recipients.selectById(owned.recipient().getId()), traceId);
    }

    public MessageModels.UnreadCount readAll(AuthenticatedContext context, String traceId) {
        int changed = 0;
        for (PlatMsgRecipient recipient : ownedRecipients(context)) {
            if (!"UNREAD".equals(recipient.getStatus())) continue;
            recipient.setStatus("READ");
            recipient.setReadTime(LocalDateTime.now());
            updateRecipient(recipient);
            changed++;
        }
        auditRecorder.record(traceId, context.accountId(), null, null, null,
                "MESSAGE_READ_ALL", "MESSAGE_INBOX", context.accountId().toString(), "SUCCESS",
                Map.of("changedCount", changed));
        return unreadCount(context);
    }

    public MessageModels.OpenResult open(AuthenticatedContext context, Long messageId, String traceId) {
        Owned owned = requireOwned(context, messageId, false);
        String route = validateTarget(context, owned.message().getTargetType(), owned.message().getTargetId(),
                owned.message().getTargetRoute());
        if (route == null) throw conflict("MESSAGE_TARGET_UNAVAILABLE", "消息没有可打开的目标");
        if (owned.recipient().getReadTime() == null) {
            owned.recipient().setStatus("READ");
            owned.recipient().setReadTime(LocalDateTime.now());
            updateRecipient(owned.recipient());
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetType", owned.message().getTargetType());
        detail.put("targetId", owned.message().getTargetId());
        detail.put("resolvedRoute", route);
        auditRecorder.record(traceId, context.accountId(), null, null, null,
                "MESSAGE_TARGET_OPENED", "MESSAGE", messageId.toString(), "SUCCESS", detail);
        return new MessageModels.OpenResult(messageId, route, null,
                owned.message().getTargetType(), owned.message().getTargetId());
    }

    public MessageModels.DeliveryDiagnosticsView deliveryDiagnostics(AuthenticatedContext context, Long messageId) {
        PlatMsgMessage message = messages.selectById(messageId);
        if (message == null || !Objects.equals(message.getPlatformId(), context.platformId())) {
            throw notFound("MESSAGE_NOT_FOUND", "消息不存在");
        }
        List<MessageModels.DeliveryView> rows = deliveries.selectList(Wrappers.<PlatMsgDelivery>lambdaQuery()
                        .eq(PlatMsgDelivery::getMessageId, messageId).orderByAsc(PlatMsgDelivery::getId))
                .stream().map(this::deliveryView).toList();
        return new MessageModels.DeliveryDiagnosticsView(messageId, message.getSubject(), rows);
    }

    public MessageModels.MessageView retryDelivery(AuthenticatedContext context, Long deliveryId, String traceId) {
        PlatMsgDelivery delivery = deliveries.selectList(Wrappers.<PlatMsgDelivery>lambdaQuery()
                        .eq(PlatMsgDelivery::getId, deliveryId).last("FOR UPDATE"))
                .stream().findFirst().orElseThrow(() -> notFound("MESSAGE_DELIVERY_NOT_FOUND", "消息投递记录不存在"));
        PlatMsgMessage message = messages.selectById(delivery.getMessageId());
        if (message == null || !Objects.equals(message.getPlatformId(), context.platformId())) {
            throw notFound("MESSAGE_DELIVERY_NOT_FOUND", "消息投递记录不存在");
        }
        if ("IN_APP".equals(delivery.getChannel())
                || !Set.of("RETRY_PENDING", "QUIET_PENDING").contains(delivery.getStatus())
                || delivery.getAttemptCount() >= MAX_DELIVERY_ATTEMPTS) {
            throw conflict("MESSAGE_DELIVERY_NOT_RETRYABLE", "该投递记录不需要重试");
        }
        PlatMsgUserPreference preference = preferenceFor(message, delivery.getAccountId(), delivery.getChannel());
        LocalDateTime quietEnd = quietEnd(preference, LocalDateTime.now());
        if (quietEnd != null) {
            delivery.setStatus("QUIET_PENDING");
            delivery.setNextAttemptTime(quietEnd);
        } else {
            int attempt = delivery.getAttemptCount() + 1;
            delivery.setAttemptCount(attempt);
            delivery.setProviderReceiptJson(toJson(Map.of("result", "PROVIDER_UNAVAILABLE", "attempt", attempt)));
            delivery.setLastError("CHANNEL_PROVIDER_UNAVAILABLE");
            delivery.setStatus(attempt >= MAX_DELIVERY_ATTEMPTS ? "FAILED" : "RETRY_PENDING");
            delivery.setNextAttemptTime(attempt >= MAX_DELIVERY_ATTEMPTS
                    ? null : LocalDateTime.now().plusMinutes(attempt));
        }
        delivery.setUpdateTime(LocalDateTime.now());
        if (deliveries.updateById(delivery) != 1) {
            throw conflict("MESSAGE_DELIVERY_VERSION_CONFLICT", "投递记录已变化，请刷新后重试");
        }
        auditRecorder.record(traceId, context.accountId(), null, null, null,
                quietEnd == null ? "MESSAGE_DELIVERY_RETRIED" : "MESSAGE_DELIVERY_DEFERRED",
                "MESSAGE_DELIVERY", deliveryId.toString(), delivery.getStatus(),
                Map.of("channel", delivery.getChannel(), "attemptCount", delivery.getAttemptCount()));
        PlatMsgRecipient recipient = recipients.selectList(Wrappers.<PlatMsgRecipient>lambdaQuery()
                        .eq(PlatMsgRecipient::getMessageId, message.getId())
                        .eq(PlatMsgRecipient::getAccountId, delivery.getAccountId()))
                .stream().findFirst().orElseThrow(() -> conflict("MESSAGE_RECIPIENT_NOT_FOUND", "消息接收记录不存在"));
        return messageView(context, message, recipient, traceId);
    }

    public List<MessageModels.PreferenceView> preferences(AuthenticatedContext context) {
        Map<String, PlatMsgUserPreference> stored = preferences.selectList(Wrappers.<PlatMsgUserPreference>lambdaQuery()
                        .eq(PlatMsgUserPreference::getPlatformId, context.platformId())
                        .eq(PlatMsgUserPreference::getAccountId, context.accountId())
                        .eq(PlatMsgUserPreference::getEventCode, "*"))
                .stream().collect(java.util.stream.Collectors.toMap(PlatMsgUserPreference::getChannel,
                        item -> item, (left, right) -> left, LinkedHashMap::new));
        return EXTERNAL_CHANNELS.stream().map(channel -> {
            PlatMsgUserPreference preference = stored.get(channel);
            if (preference == null) return new MessageModels.PreferenceView(channel, true, null, null, null);
            QuietHours quiet = quietHours(preference);
            return new MessageModels.PreferenceView(channel, Boolean.TRUE.equals(preference.getEnabled()),
                    quiet == null ? null : quiet.start().toString(),
                    quiet == null ? null : quiet.end().toString(), preference.getVersion());
        }).toList();
    }

    public MessageModels.PreferenceView savePreference(AuthenticatedContext context,
            MessageModels.SavePreferenceRequest input, String traceId) {
        boolean hasStart = input.quietStart() != null && !input.quietStart().isBlank();
        boolean hasEnd = input.quietEnd() != null && !input.quietEnd().isBlank();
        if (hasStart != hasEnd || (hasStart && input.quietStart().equals(input.quietEnd()))) {
            throw invalid("MESSAGE_QUIET_HOURS_INVALID", "免打扰开始和结束时间必须同时填写且不能相同");
        }
        PlatMsgUserPreference preference = preferences.selectList(Wrappers.<PlatMsgUserPreference>lambdaQuery()
                        .eq(PlatMsgUserPreference::getPlatformId, context.platformId())
                        .eq(PlatMsgUserPreference::getAccountId, context.accountId())
                        .eq(PlatMsgUserPreference::getEventCode, "*")
                        .eq(PlatMsgUserPreference::getChannel, input.channel()))
                .stream().findFirst().orElse(null);
        if (preference == null) {
            if (input.expectedVersion() != null) {
                throw conflict("MESSAGE_PREFERENCE_VERSION_CONFLICT", "通知偏好已经变化，请刷新后重试");
            }
            preference = new PlatMsgUserPreference();
            preference.setPlatformId(context.platformId());
            preference.setAccountId(context.accountId());
            preference.setEventCode("*");
            preference.setChannel(input.channel());
            preference.setVersion(0);
        } else if (input.expectedVersion() == null || !input.expectedVersion().equals(preference.getVersion())) {
            throw conflict("MESSAGE_PREFERENCE_VERSION_CONFLICT", "通知偏好已经变化，请刷新后重试");
        }
        preference.setEnabled(input.enabled());
        preference.setQuietHoursJson(hasStart
                ? toJson(Map.of("start", input.quietStart(), "end", input.quietEnd())) : "{}");
        if (preference.getId() == null) preferences.insert(preference);
        else if (preferences.updateById(preference) != 1) {
            throw conflict("MESSAGE_PREFERENCE_VERSION_CONFLICT", "通知偏好已经变化，请刷新后重试");
        }
        auditRecorder.record(traceId, context.accountId(), null, null, null,
                "MESSAGE_PREFERENCE_SAVED", "MESSAGE_PREFERENCE", preference.getId().toString(), "SUCCESS",
                Map.of("channel", input.channel(), "enabled", input.enabled(), "quietHours", hasStart));
        return preferences(context).stream().filter(item -> item.channel().equals(input.channel()))
                .findFirst().orElseThrow();
    }

    private PlatMsgTemplate requireTemplate(AuthenticatedContext context, Long id, boolean forUpdate) {
        PlatMsgTemplate template = forUpdate
                ? templates.selectList(Wrappers.<PlatMsgTemplate>lambdaQuery()
                        .eq(PlatMsgTemplate::getId, id).last("FOR UPDATE")).stream().findFirst().orElse(null)
                : templates.selectById(id);
        if (template == null || !Objects.equals(template.getPlatformId(), context.platformId())) {
            throw notFound("MESSAGE_TEMPLATE_NOT_FOUND", "消息模板不存在");
        }
        return template;
    }

    private PlatMsgTemplate publishedTemplate(AuthenticatedContext context, String code) {
        List<PlatMsgTemplate> found = templates.selectList(Wrappers.<PlatMsgTemplate>lambdaQuery()
                .eq(PlatMsgTemplate::getPlatformId, context.platformId())
                .eq(PlatMsgTemplate::getCode, code).eq(PlatMsgTemplate::getStatus, "PUBLISHED"));
        if (found.isEmpty()) throw notFound("MESSAGE_TEMPLATE_NOT_PUBLISHED", "当前平台没有已发布的消息模板");
        if (found.size() > 1) {
            throw conflict("MESSAGE_TEMPLATE_CHANNEL_AMBIGUOUS", "同一模板编码存在多个已发布渠道，请使用唯一编码");
        }
        return found.getFirst();
    }

    private PlatMsgTemplateVersion latestVersion(Long platformId, Long templateId) {
        return versions.selectList(Wrappers.<PlatMsgTemplateVersion>lambdaQuery()
                        .eq(PlatMsgTemplateVersion::getPlatformId, platformId)
                        .eq(PlatMsgTemplateVersion::getTemplateId, templateId)
                        .orderByDesc(PlatMsgTemplateVersion::getVersionNumber).last("LIMIT 1"))
                .stream().findFirst().orElse(null);
    }

    private MessageModels.TemplateView templateView(PlatMsgTemplate template) {
        PlatMsgTemplateVersion latest = latestVersion(template.getPlatformId(), template.getId());
        return new MessageModels.TemplateView(template.getId(), "PLATFORM", template.getPlatformId(), null, null,
                template.getCode(), template.getName(), template.getChannel(), template.getDraftRevision(),
                template.getSubjectTemplate(), template.getContentTemplate(), requiredVariables(template),
                template.getStatus(), latest == null ? null : latest.getVersionNumber(),
                latest == null ? null : latest.getId(), latest == null ? null : latest.getPublishTime(),
                template.getVersion(), template.getUpdateTime());
    }

    private List<String> requiredVariables(PlatMsgTemplate template) {
        try {
            Map<String, Object> schema = objectMapper.readValue(template.getVariableSchemaJson(), new TypeReference<>() { });
            Object required = schema.get("required");
            if (!(required instanceof List<?> values)) return List.of();
            return values.stream().map(String::valueOf).toList();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot parse platform message variable schema", exception);
        }
    }

    private TemplateSnapshot snapshot(PlatMsgTemplateVersion version) {
        try {
            return objectMapper.readValue(version.getSnapshotJson(), TemplateSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot parse platform message template snapshot", exception);
        }
    }

    private PlatMsgMessage findEvent(AuthenticatedContext context, String sourceType, String sourceId) {
        return messages.selectList(Wrappers.<PlatMsgMessage>lambdaQuery()
                        .eq(PlatMsgMessage::getPlatformId, context.platformId())
                        .eq(PlatMsgMessage::getSourceType, sourceType)
                        .eq(PlatMsgMessage::getSourceId, sourceId))
                .stream().findFirst().orElse(null);
    }

    private PlatMsgMessage newMessage(AuthenticatedContext context, Long versionId, String sourceType,
            String sourceId, String subject, String content, String targetType, String targetId,
            String targetRoute, String sensitivity) {
        PlatMsgMessage message = new PlatMsgMessage();
        message.setPlatformId(context.platformId());
        message.setTemplateVersionId(versionId);
        message.setSourceType(sourceType);
        message.setSourceId(sourceId);
        message.setSubject(subject);
        message.setContentText(content);
        message.setTargetType(stripToNull(targetType));
        message.setTargetId(stripToNull(targetId));
        message.setTargetRoute(stripToNull(targetRoute));
        message.setSensitivity(sensitivity);
        message.setCreatedByAccountId(context.accountId());
        return message;
    }

    private List<PlatformAccount> resolveRecipients(AuthenticatedContext context,
            List<MessageModels.RecipientRef> refs) {
        if (refs == null || refs.isEmpty() || refs.size() > 100) {
            throw invalid("MESSAGE_RECIPIENT_INVALID", "单次消息必须包含 1 到 100 个组织成员");
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (MessageModels.RecipientRef ref : refs) {
            if (ref == null || ref.id() == null || ref.id() <= 0) {
                throw invalid("MESSAGE_RECIPIENT_INVALID", "消息接收成员无效");
            }
            if (!"PLATFORM_MEMBER".equals(ref.type())) {
                throw invalid("MESSAGE_RECIPIENT_CONTEXT_INVALID", "平台消息只能选择平台组织成员");
            }
            PlatformMember member = members.selectById(ref.id());
            if (member == null || !Objects.equals(member.getPlatformId(), context.platformId())
                    || !"ACTIVE".equals(member.getStatus())) {
                throw invalid("MESSAGE_RECIPIENT_CONTEXT_INVALID", "接收人不属于当前平台组织");
            }
            ids.add(member.getAccountId());
        }
        return ids.stream().map(id -> requireRecipient(context, id)).toList();
    }

    private PlatformAccount requireRecipient(AuthenticatedContext context, Long accountId) {
        PlatformAccount account = accounts.selectById(accountId);
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw invalid("MESSAGE_RECIPIENT_INVALID", "消息接收账号不存在或已停用");
        }
        if (members.selectCount(Wrappers.<PlatformMember>lambdaQuery()
                .eq(PlatformMember::getPlatformId, context.platformId())
                .eq(PlatformMember::getAccountId, accountId)
                .eq(PlatformMember::getStatus, "ACTIVE")) == 0) {
            throw invalid("MESSAGE_RECIPIENT_CONTEXT_INVALID", "接收人不属于当前平台组织");
        }
        return account;
    }

    private PlatMsgRecipient insertRecipient(AuthenticatedContext context, PlatMsgMessage message,
            PlatformAccount account) {
        PlatMsgRecipient recipient = new PlatMsgRecipient();
        recipient.setMessageId(message.getId());
        recipient.setPlatformId(context.platformId());
        recipient.setAccountId(account.getId());
        recipient.setStatus("UNREAD");
        recipient.setVersion(0);
        recipients.insert(recipient);
        return recipient;
    }

    private void insertDelivery(PlatMsgMessage message, PlatformAccount account, String channel) {
        PlatMsgDelivery delivery = new PlatMsgDelivery();
        delivery.setMessageId(message.getId());
        delivery.setAccountId(account.getId());
        delivery.setChannel(channel);
        delivery.setDestinationMasked(maskDestination(account, channel));
        delivery.setVersion(0);
        if ("IN_APP".equals(channel)) {
            delivery.setStatus("DELIVERED");
            delivery.setAttemptCount(1);
            delivery.setProviderMessageId("in-app:" + message.getId() + ":" + account.getId());
            delivery.setProviderReceiptJson(toJson(Map.of("result", "STORED")));
            delivery.setSendTime(LocalDateTime.now());
            delivery.setDeliverTime(LocalDateTime.now());
        } else {
            PlatMsgUserPreference preference = preferenceFor(message, account.getId(), channel);
            boolean enabled = preference == null || Boolean.TRUE.equals(preference.getEnabled());
            LocalDateTime quietEnd = enabled ? quietEnd(preference, LocalDateTime.now()) : null;
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
        }
        deliveries.insert(delivery);
    }

    /** 每次打开消息时重新检查目标及当前权限，不能信任发送时保存的路由。 */
    private String validateTarget(AuthenticatedContext context, String type, String id, String targetRoute) {
        if (type == null || type.isBlank()) {
            if (id != null || targetRoute != null) {
                throw invalid("MESSAGE_TARGET_INVALID", "目标类型为空时不能携带目标引用");
            }
            return null;
        }
        String route = stripToNull(targetRoute);
        switch (type) {
            case "PLATFORM_ROUTE" -> {
                if (route == null || !(route.equals("/profile") || route.equals("/platform")
                        || route.equals("/systems") || route.startsWith("/platform/"))) {
                    throw forbidden("MESSAGE_TARGET_CONTEXT_INVALID", "平台消息目标不能跳入系统业务上下文");
                }
                return route;
            }
            case "FLOW_INSTANCE" -> {
                Long flowId = numericTarget(id);
                FlowInstance flow = flowInstances.selectById(flowId);
                if (flow == null || !"PLATFORM".equals(flow.getContextType())
                        || !Objects.equals(flow.getPlatformId(), context.platformId())) {
                    throw notFound("MESSAGE_TARGET_NOT_FOUND", "消息关联的 Flow 实例不存在");
                }
                flowRuntime.getObject().instance(context, flowId);
                return "/platform/flows?instanceId=" + flowId;
            }
            case "WORK_TASK" -> {
                Long taskId = numericTarget(id);
                WorkTask task = workTasks.selectById(taskId);
                if (task == null || !"PLATFORM".equals(task.getContextType())
                        || !Objects.equals(task.getPlatformId(), context.platformId())) {
                    throw notFound("MESSAGE_TARGET_NOT_FOUND", "消息关联的工作任务不存在");
                }
                workManagement.getObject().task(context, taskId);
                return "/platform/tasks?taskId=" + taskId;
            }
            case "APPLICATION" -> {
                requireTargetPermission(context, "APPLICATION", "PLATFORM", "VIEW");
                return "/platform/applications?applicationId=" + numericTarget(id);
            }
            case "FILE_OBJECT" -> {
                requireTargetPermission(context, "FILE", "OBJECT", "VIEW");
                numericTarget(id);
                if (route == null) {
                    throw conflict("MESSAGE_TARGET_UNAVAILABLE", "文件消息缺少受控预览或下载入口");
                }
                if (!route.startsWith("/platform/")) {
                    throw forbidden("MESSAGE_TARGET_CONTEXT_INVALID", "平台文件消息不能跳入系统上下文");
                }
                return route;
            }
            default -> throw invalid("MESSAGE_TARGET_INVALID", "平台消息目标类型无效");
        }
    }

    private void requireTargetPermission(AuthenticatedContext context, String type, String code, String action) {
        if (!permissionChecker.allows(context, type, code, action)
                && !permissionChecker.allows(context, type, "*", action)) {
            throw forbidden("MESSAGE_TARGET_PERMISSION_DENIED", "关联目标当前不可访问");
        }
    }

    private List<PlatMsgRecipient> ownedRecipients(AuthenticatedContext context) {
        return recipients.selectList(Wrappers.<PlatMsgRecipient>lambdaQuery()
                        .eq(PlatMsgRecipient::getPlatformId, context.platformId())
                        .eq(PlatMsgRecipient::getAccountId, context.accountId()))
                .stream().filter(item -> {
                    PlatMsgMessage message = messages.selectById(item.getMessageId());
                    return message != null && Objects.equals(message.getPlatformId(), context.platformId());
                }).toList();
    }

    private Owned requireOwned(AuthenticatedContext context, Long messageId, boolean allowArchived) {
        PlatMsgRecipient recipient = recipients.selectList(Wrappers.<PlatMsgRecipient>lambdaQuery()
                        .eq(PlatMsgRecipient::getMessageId, messageId)
                        .eq(PlatMsgRecipient::getPlatformId, context.platformId())
                        .eq(PlatMsgRecipient::getAccountId, context.accountId()).last("FOR UPDATE"))
                .stream().findFirst().orElseThrow(() -> notFound("MESSAGE_NOT_FOUND", "消息不存在"));
        PlatMsgMessage message = messages.selectById(messageId);
        if (message == null || !Objects.equals(message.getPlatformId(), context.platformId())
                || (!allowArchived && "ARCHIVED".equals(recipient.getStatus()))) {
            throw notFound("MESSAGE_NOT_FOUND", "消息不存在");
        }
        return new Owned(message, recipient);
    }

    private MessageModels.MessageView firstMessageView(
            AuthenticatedContext context, PlatMsgMessage message, String traceId) {
        PlatMsgRecipient first = recipients.selectList(Wrappers.<PlatMsgRecipient>lambdaQuery()
                        .eq(PlatMsgRecipient::getMessageId, message.getId())
                        .orderByAsc(PlatMsgRecipient::getId).last("LIMIT 1"))
                .stream().findFirst().orElseThrow(() -> conflict("MESSAGE_EVENT_INCOMPLETE", "已有消息事件缺少接收人记录"));
        return messageView(context, message, first, traceId);
    }

    private MessageModels.MessageView messageView(AuthenticatedContext context, PlatMsgMessage message,
            PlatMsgRecipient recipient, String traceId) {
        boolean accessible;
        try {
            accessible = validateTarget(context, message.getTargetType(), message.getTargetId(),
                    message.getTargetRoute()) != null;
        } catch (DomainException exception) {
            accessible = false;
        }
        List<MessageModels.RecipientDeliveryView> channelRows = deliveries.selectList(
                        Wrappers.<PlatMsgDelivery>lambdaQuery()
                                .eq(PlatMsgDelivery::getMessageId, message.getId())
                                .eq(PlatMsgDelivery::getAccountId, recipient.getAccountId())
                                .orderByAsc(PlatMsgDelivery::getId))
                .stream().map(item -> new MessageModels.RecipientDeliveryView(item.getChannel(),
                        item.getDestinationMasked(), item.getStatus(), item.getAttemptCount(),
                        item.getNextAttemptTime(), safeDeliveryReason(item.getLastError()))).toList();
        return new MessageModels.MessageView(message.getId(), "PLATFORM", message.getPlatformId(), null, null,
                message.getTemplateVersionId(), message.getSourceType(), message.getSourceId(),
                message.getSubject(), message.getContentText(), message.getTargetType(), message.getTargetId(),
                message.getTargetRoute(), message.getSensitivity(), recipient.getStatus(), recipient.getReadTime(),
                recipient.getArchiveTime(), recipient.getVersion(), message.getCreateTime(),
                actorName(message.getCreatedByAccountId()), accessible, channelRows);
    }

    private long countUnread(AuthenticatedContext context) {
        return ownedRecipients(context).stream().filter(item -> "UNREAD".equals(item.getStatus())).count();
    }

    private void updateRecipient(PlatMsgRecipient recipient) {
        recipient.setUpdateTime(LocalDateTime.now());
        if (recipients.updateById(recipient) != 1) {
            throw conflict("MESSAGE_RECIPIENT_VERSION_CONFLICT", "消息状态已变化，请刷新后重试");
        }
    }

    private boolean matchesStatus(PlatMsgRecipient recipient, String status) {
        return switch (status) {
            case "ACTIVE" -> !"ARCHIVED".equals(recipient.getStatus());
            case "ALL" -> true;
            default -> status.equals(recipient.getStatus());
        };
    }

    private PlatMsgUserPreference preferenceFor(PlatMsgMessage message, Long accountId, String channel) {
        return preferences.selectList(Wrappers.<PlatMsgUserPreference>lambdaQuery()
                        .eq(PlatMsgUserPreference::getPlatformId, message.getPlatformId())
                        .eq(PlatMsgUserPreference::getAccountId, accountId)
                        .eq(PlatMsgUserPreference::getChannel, channel)
                        .in(PlatMsgUserPreference::getEventCode, message.getSourceType(), "*"))
                .stream().sorted(Comparator.comparing(item -> "*".equals(item.getEventCode()) ? 1 : 0))
                .findFirst().orElse(null);
    }

    private QuietHours quietHours(PlatMsgUserPreference preference) {
        if (preference == null || preference.getQuietHoursJson() == null
                || preference.getQuietHoursJson().isBlank() || "{}".equals(preference.getQuietHoursJson())) return null;
        try {
            Map<String, String> value = objectMapper.readValue(preference.getQuietHoursJson(), new TypeReference<>() { });
            if (value.get("start") == null || value.get("end") == null) return null;
            return new QuietHours(LocalTime.parse(value.get("start")), LocalTime.parse(value.get("end")));
        } catch (RuntimeException | JsonProcessingException exception) {
            return null;
        }
    }

    private LocalDateTime quietEnd(PlatMsgUserPreference preference, LocalDateTime now) {
        QuietHours quiet = quietHours(preference);
        if (quiet == null) return null;
        LocalTime current = now.toLocalTime();
        boolean wraps = quiet.start().isAfter(quiet.end());
        boolean active = wraps ? !current.isBefore(quiet.start()) || current.isBefore(quiet.end())
                : !current.isBefore(quiet.start()) && current.isBefore(quiet.end());
        if (!active) return null;
        return current.isBefore(quiet.end()) ? now.toLocalDate().atTime(quiet.end())
                : now.toLocalDate().plusDays(1).atTime(quiet.end());
    }

    private MessageModels.DeliveryView deliveryView(PlatMsgDelivery delivery) {
        return new MessageModels.DeliveryView(delivery.getId(), delivery.getChannel(), delivery.getDestinationMasked(),
                delivery.getStatus(), delivery.getAttemptCount(), delivery.getNextAttemptTime(),
                delivery.getProviderMessageId(), delivery.getProviderReceiptJson(), delivery.getLastError(),
                delivery.getSendTime(), delivery.getDeliverTime(), delivery.getVersion());
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
        PlatformAccount account = accounts.selectById(accountId);
        if (account == null) return "系统";
        return account.getDisplayName() == null || account.getDisplayName().isBlank()
                ? account.getUsername() : account.getDisplayName();
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

    private Long numericTarget(String id) {
        if (id == null || !id.matches("[1-9][0-9]*")) {
            throw invalid("MESSAGE_TARGET_INVALID", "消息目标引用格式无效");
        }
        return Long.valueOf(id);
    }

    private String stripToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize platform message data", exception);
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

    private record Owned(PlatMsgMessage message, PlatMsgRecipient recipient) { }

    private record QuietHours(LocalTime start, LocalTime end) { }

    private record TemplateSnapshot(String code, String name, String channel, String subjectTemplate,
            String contentTemplate, List<String> requiredVariables, String contextType, Long platformId,
            Long systemId, Long tenantId) { }
}
