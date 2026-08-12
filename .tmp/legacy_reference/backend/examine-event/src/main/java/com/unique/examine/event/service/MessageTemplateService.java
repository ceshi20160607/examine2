package com.unique.examine.event.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.ResultNotificationFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.job.DurableJobFacade;
import com.unique.examine.event.adapter.jdbc.JdbcMessageTemplateRepository;
import com.unique.examine.event.domain.DeliveryChannel;
import com.unique.examine.event.domain.EventActor;
import com.unique.examine.event.port.DeliveryPreferenceRepository;
import com.unique.examine.event.port.EventChannelTransport;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Transactional
public class MessageTemplateService {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z][A-Za-z0-9]*)}");
    private static final String DEFAULT_CHANNELS_JSON = "[\"INBOX\"]";
    static final String TEMPORARY_FAILURE = "MESSAGE_DELIVERY_TEMPORARY_FAILURE";
    static final String SAFE_TEMPORARY_MESSAGE = "Notification delivery failed safely";
    private static final List<Definition> DEFAULTS = List.of(
            new Definition("MODULE_IMPORT_SUCCEEDED", "MODULE_IMPORT_SUCCEEDED", "导入成功通知",
                    "导入已完成", "{moduleCode} 导入完成：新增 {newRows} 条，更新 {updateRows} 条。",
                    Set.of("moduleCode", "newRows", "updateRows")),
            new Definition("MODULE_IMPORT_FAILED", "MODULE_IMPORT_FAILED", "导入失败通知",
                    "导入失败", "{moduleCode} 导入失败：{errorMessage}",
                    Set.of("moduleCode", "errorMessage")),
            new Definition("MODULE_EXPORT_SUCCEEDED", "MODULE_EXPORT_SUCCEEDED", "导出成功通知",
                    "导出已完成", "{moduleCode} 已导出 {rows} 条记录，可打开任务下载结果。",
                    Set.of("moduleCode", "rows")),
            new Definition("MODULE_EXPORT_FAILED", "MODULE_EXPORT_FAILED", "导出失败通知",
                    "导出失败", "{moduleCode} 导出失败：{errorMessage}",
                    Set.of("moduleCode", "errorMessage")),
            new Definition("MODULE_PRINT_SUCCEEDED", "MODULE_PRINT_SUCCEEDED", "打印成功通知",
                    "PDF 已生成", "记录 {recordNo} 的“{templateName}”PDF 已生成，可打开打印历史下载。",
                    Set.of("recordNo", "templateName")),
            new Definition("MODULE_PRINT_FAILED", "MODULE_PRINT_FAILED", "打印失败通知",
                    "PDF 生成失败", "记录 {recordNo} 的“{templateName}”生成失败：{errorMessage}",
                    Set.of("recordNo", "templateName", "errorMessage")),
            new Definition("RECORD_COMMENT_MENTIONED", "RECORD_COMMENT_MENTIONED", "评论提及通知",
                    "你在评论中被提及",
                    "模块 {moduleCode} 的记录 {recordId} 有一条评论提及了你：{commentExcerpt}",
                    Set.of("moduleCode", "recordId", "commentExcerpt")),
            new Definition(
                    "FLOW_APPROVAL_DEADLINE_REMINDER",
                    "FLOW_APPROVAL_DEADLINE_REMINDER",
                    "Approval deadline reminder",
                    "Approval is due soon",
                    "Flow instance {instanceId}, {branchName}, is due at {dueAt}.",
                    Set.of("definitionId", "instanceId", "branchName", "dueAt")
            )
    );

    private final JdbcMessageTemplateRepository repository;
    private final DeliveryPreferenceRepository preferences;
    private final MessageInboxService messages;
    private final ObjectMapper mapper;
    private final DurableJobFacade jobs;
    private final Map<DeliveryChannel, EventChannelTransport> transports;

    public MessageTemplateService(JdbcMessageTemplateRepository repository,
                                  DeliveryPreferenceRepository preferences,
                                  MessageInboxService messages,
                                  ObjectMapper mapper,
                                  DurableJobFacade jobs) {
        this(repository, preferences, messages, mapper, jobs, List.of());
    }

    public MessageTemplateService(JdbcMessageTemplateRepository repository,
                                  DeliveryPreferenceRepository preferences,
                                  MessageInboxService messages,
                                  ObjectMapper mapper,
                                  DurableJobFacade jobs,
                                  Collection<EventChannelTransport> transports) {
        this.repository = repository;
        this.preferences = preferences;
        this.messages = messages;
        this.mapper = mapper;
        this.jobs = jobs;
        var registered = new EnumMap<DeliveryChannel, EventChannelTransport>(DeliveryChannel.class);
        if (transports != null) {
            for (var transport : transports) {
                if (transport == null || transport.channel() == null
                        || transport.channel() == DeliveryChannel.INBOX
                        || registered.putIfAbsent(transport.channel(), transport) != null) {
                    throw new IllegalArgumentException("External Event channel transports must be unique");
                }
            }
        }
        this.transports = Map.copyOf(registered);
    }

    public List<TemplateView> list(long systemId, long actorId) {
        ensureDefaults(systemId, actorId);
        return repository.templates(systemId).stream().map(this::view).toList();
    }

    public List<PreferenceTemplate> preferenceCatalog(long systemId, long actorId) {
        ensureDefaults(systemId, actorId);
        return repository.templates(systemId).stream()
                .filter(template -> hasDefinition(template.templateCode()))
                .map(template -> new PreferenceTemplate(
                        template.templateCode(), template.eventType(), template.name(),
                        channels(template.channelsJson())))
                .toList();
    }

    public PreferenceTemplate requirePreferenceTemplate(long systemId, long actorId, String code) {
        ensureDefaults(systemId, actorId);
        if (!hasDefinition(code)) throw notFound();
        var template = requireTemplate(systemId, code);
        return new PreferenceTemplate(template.templateCode(), template.eventType(), template.name(),
                channels(template.channelsJson()));
    }

    public TemplateView update(long systemId, long actorId, String code, UpdateCommand command) {
        ensureDefaults(systemId, actorId);
        var current = requireTemplate(systemId, code);
        if (command == null || command.expectedVersion() != current.version()) {
            throw conflict();
        }
        var definition = definition(code);
        var name = required(command.name(), "Template name", 128);
        var title = validateTemplate(command.titleTemplate(), 200, definition.allowedVariables());
        var body = validateTemplate(command.bodyTemplate(), 4000, definition.allowedVariables());
        var channelsJson = json(validateChannels(command.channels()).stream().map(Enum::name).toList());
        if (!repository.updateDraft(systemId, current.id(), command.expectedVersion(), name,
                command.enabled(), title, body, channelsJson, actorId)) {
            throw conflict();
        }
        return view(requireTemplate(systemId, code));
    }

    public TemplateView publish(long systemId, long actorId, String code, long expectedVersion) {
        ensureDefaults(systemId, actorId);
        var current = requireTemplate(systemId, code);
        var replay = repository.sourceVersion(current.id(), expectedVersion);
        if (replay.isPresent()) return view(current);
        if (current.version() != expectedVersion) {
            throw conflict();
        }
        var definition = definition(code);
        validateTemplate(current.draftTitleTemplate(), 200, definition.allowedVariables());
        validateTemplate(current.draftBodyTemplate(), 4000, definition.allowedVariables());
        validateChannels(jsonList(current.channelsJson()));
        var version = new JdbcMessageTemplateRepository.VersionRecord(repository.nextId(), systemId,
                current.id(), current.templateCode(), current.eventType(), repository.nextVersionNo(current.id()),
                current.version(), current.desiredEnabled(), current.draftTitleTemplate(),
                current.draftBodyTemplate(), current.channelsJson(), current.allowedVariablesJson(), null, actorId);
        repository.insertVersion(version);
        if (!repository.publish(systemId, current.id(), expectedVersion, version.id(), actorId)) {
            throw conflict();
        }
        return view(requireTemplate(systemId, code));
    }

    public ResultNotificationFacade.DeliveryReceipt dispatch(ResultNotificationFacade.Command command) {
        ensureDefaults(command.systemId(), command.senderMemberId());
        var template = repository.template(command.systemId(), command.templateCode()).orElse(null);
        var version = template == null ? null : repository.publishedVersion(template).orElse(null);
        var channels = version == null ? List.of(DeliveryChannel.INBOX) : channels(version.channelsJson());
        var receipts = new EnumMap<DeliveryChannel, ResultNotificationFacade.DeliveryReceipt>(DeliveryChannel.class);
        for (var channel : channels) {
            receipts.put(channel, dispatchChannel(command, version, channel));
        }
        var primary = receipts.get(DeliveryChannel.INBOX);
        return primary == null ? receipts.get(channels.getFirst()) : primary;
    }

    private ResultNotificationFacade.DeliveryReceipt dispatchChannel(
            ResultNotificationFacade.Command command,
            JdbcMessageTemplateRepository.VersionRecord version,
            DeliveryChannel channel
    ) {
        var begun = repository.begin(command, version == null ? null : version.id(), channel);
        var delivery = begun.delivery();
        if (!"PENDING".equals(delivery.status())) {
            return receipt(delivery, true);
        }
        if (version == null || !version.enabled()) {
            return receipt(complete(delivery, "SKIPPED", null,
                    version == null ? "MESSAGE_TEMPLATE_NOT_PUBLISHED" : "MESSAGE_TEMPLATE_DISABLED",
                    version == null ? "Published template was not found" : "Published template is disabled",
                    0, trace(delivery), maskedMember(command.recipientMemberId())),
                    begun.replay());
        }
        var disabled = preferences.find(command.systemId(), command.tenantId(),
                command.recipientMemberId(), command.templateCode(), channel)
                .filter(preference -> !preference.enabled())
                .isPresent();
        if (disabled) {
            return receipt(complete(delivery, "SKIPPED", null,
                    "MESSAGE_DELIVERY_DISABLED_BY_RECIPIENT",
                    "Recipient disabled this notification", 0,
                    trace(delivery), maskedMember(command.recipientMemberId())), begun.replay());
        }
        var completed = deliverPending(delivery, version, command, channel, !begun.replay());
        return receipt(completed, begun.replay());
    }

    private JdbcMessageTemplateRepository.DeliveryRecord deliverPending(
            JdbcMessageTemplateRepository.DeliveryRecord delivery,
            JdbcMessageTemplateRepository.VersionRecord version,
            ResultNotificationFacade.Command command,
            DeliveryChannel channel,
            boolean enqueueTemporaryFailure
    ) {
        var started = System.nanoTime();
        try {
            var allowed = jsonSet(version.allowedVariablesJson());
            var title = render(version.titleTemplate(), command.variables(), allowed, 200);
            var body = render(version.bodyTemplate(), command.variables(), allowed, 4000);
            if (channel == DeliveryChannel.INBOX) {
                var actor = new EventActor(command.systemId(), command.tenantId(), command.senderMemberId(),
                        Set.of(MessageInboxService.CREATE));
                var message = messages.createForDelivery(delivery.id(), actor, command.recipientMemberId(),
                        command.templateCode(), title, body, command.target(), command.targetPath());
                return complete(delivery, "DELIVERED", message.id(), null, null,
                        elapsed(started), trace(delivery), maskedMember(command.recipientMemberId()));
            }
            var transport = transports.get(channel);
            if (transport == null) {
                return complete(delivery, "FAILED", null,
                        "MESSAGE_DELIVERY_CHANNEL_UNAVAILABLE",
                        "Delivery channel is not configured", elapsed(started),
                        trace(delivery), maskedMember(command.recipientMemberId()));
            }
            var result = transport.deliver(new EventChannelTransport.DeliveryCommand(
                    delivery.id(), command.systemId(), command.tenantId(), command.recipientMemberId(),
                    command.templateCode(), command.dedupeKey(), title, body,
                    command.target().type(), command.target().id(), command.targetPath(), command.variables()));
            if (result == null || result.status() == null) {
                throw new IllegalStateException("External channel returned no safe result");
            }
            var duration = result.durationMillis() < 0 ? elapsed(started) : result.durationMillis();
            var resultTrace = safeTrace(result.traceId(), delivery);
            var destination = safeDestination(result.maskedDestination(), command.recipientMemberId());
            if (result.status() == EventChannelTransport.Status.SENT) {
                return complete(delivery, "DELIVERED", null, null, null,
                        duration, resultTrace, destination);
            }
            if (result.status() == EventChannelTransport.Status.PERMANENT_FAILURE) {
                return complete(delivery, "FAILED", null,
                        safeFailureCode(result.failureCode(), "MESSAGE_DELIVERY_PERMANENT_FAILURE"),
                        "External delivery was rejected permanently", duration, resultTrace, destination);
            }
            var failed = complete(delivery, "FAILED", null, TEMPORARY_FAILURE,
                    "External delivery failed temporarily", duration, resultTrace, destination);
            if (enqueueTemporaryFailure) enqueueRetry(delivery.id(), command);
            return failed;
        } catch (BusinessException failure) {
            return complete(delivery, "FAILED", null, failure.code(), safe(failure.getMessage()),
                    elapsed(started), trace(delivery), maskedMember(command.recipientMemberId()));
        } catch (RuntimeException failure) {
            var failed = complete(delivery, "FAILED", null, TEMPORARY_FAILURE, SAFE_TEMPORARY_MESSAGE,
                    elapsed(started), trace(delivery), maskedMember(command.recipientMemberId()));
            if (enqueueTemporaryFailure) enqueueRetry(delivery.id(), command);
            return failed;
        }
    }

    private void enqueueRetry(long deliveryId, ResultNotificationFacade.Command command) {
        jobs.enqueue(new DurableJobFacade.EnqueueCommand(
                EventDeliveryRetryWorker.JOB_TYPE,
                EventDeliveryRetryWorker.OWNER_TYPE,
                Long.toString(deliveryId),
                command.systemId(), command.tenantId(), command.senderMemberId(),
                EventDeliveryRetryPayload.encode(deliveryId, command), 2));
    }

    /**
     * Performs one compensation attempt against the exact immutable delivery
     * identity and template version captured by the initial dispatch.
     */
    public RetryOutcome retry(long deliveryId, ResultNotificationFacade.Command command,
                              int expectedAttemptCount) {
        if (deliveryId <= 0 || command == null || expectedAttemptCount < 1 || expectedAttemptCount > 2) {
            throw new IllegalArgumentException("A valid delivery retry command is required");
        }
        var current = repository.delivery(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery retry target was not found"));
        requireSameDelivery(current, command);
        if (!isTemporary(current)) return outcome(current);

        var claimed = repository.retryTemporary(deliveryId, expectedAttemptCount).orElse(null);
        if (claimed == null) {
            return outcome(repository.delivery(deliveryId)
                    .orElseThrow(() -> new IllegalStateException("Delivery retry target disappeared")));
        }
        var version = claimed.templateVersionId() == null ? null
                : repository.version(claimed.templateVersionId()).orElse(null);
        if (version == null || version.systemId() != claimed.systemId()
                || !version.templateCode().equals(claimed.templateCode())) {
            return outcome(complete(claimed, "FAILED", null,
                    "MESSAGE_TEMPLATE_VERSION_NOT_FOUND", "Recorded template version was not found",
                    0, trace(claimed), maskedMember(command.recipientMemberId())));
        }
        if (!version.enabled()) {
            return outcome(complete(claimed, "SKIPPED", null,
                    "MESSAGE_TEMPLATE_DISABLED", "Recorded template version is disabled",
                    0, trace(claimed), maskedMember(command.recipientMemberId())));
        }
        final DeliveryChannel channel;
        try {
            channel = DeliveryChannel.valueOf(claimed.channel());
        } catch (IllegalArgumentException invalid) {
            return outcome(complete(claimed, "FAILED", null,
                    "MESSAGE_DELIVERY_CHANNEL_INVALID", "Recorded delivery channel is invalid",
                    0, trace(claimed), maskedMember(command.recipientMemberId())));
        }
        if (!channels(version.channelsJson()).contains(channel)) {
            return outcome(complete(claimed, "FAILED", null,
                    "MESSAGE_DELIVERY_CHANNEL_INVALID", "Recorded delivery channel is not published",
                    0, trace(claimed), maskedMember(command.recipientMemberId())));
        }
        var disabled = preferences.find(command.systemId(), command.tenantId(),
                        command.recipientMemberId(), command.templateCode(), channel)
                .filter(preference -> !preference.enabled())
                .isPresent();
        if (disabled) {
            return outcome(complete(claimed, "SKIPPED", null,
                    "MESSAGE_DELIVERY_DISABLED_BY_RECIPIENT",
                    "Recipient disabled this notification", 0,
                    trace(claimed), maskedMember(command.recipientMemberId())));
        }
        return outcome(deliverPending(claimed, version, command, channel, false));
    }

    private static void requireSameDelivery(JdbcMessageTemplateRepository.DeliveryRecord delivery,
                                            ResultNotificationFacade.Command command) {
        if (delivery.systemId() != command.systemId()
                || delivery.tenantId() != command.tenantId()
                || delivery.recipientMemberId() != command.recipientMemberId()
                || !delivery.templateCode().equals(command.templateCode())
                || !delivery.dedupeKey().equals(command.dedupeKey())
                || !delivery.targetType().equals(command.target().type())
                || !delivery.targetId().equals(command.target().id())
                || !Objects.equals(delivery.targetPath(), command.targetPath())) {
            throw new IllegalArgumentException("Delivery retry identity does not match its durable command");
        }
    }

    private static RetryOutcome outcome(JdbcMessageTemplateRepository.DeliveryRecord delivery) {
        return new RetryOutcome(delivery.id(), delivery.messageId(), delivery.status(),
                delivery.attemptCount(), "PENDING".equals(delivery.status())
                || isTemporary(delivery) && delivery.attemptCount() < 3);
    }

    private static boolean isTemporary(JdbcMessageTemplateRepository.DeliveryRecord delivery) {
        return "FAILED".equals(delivery.status()) && TEMPORARY_FAILURE.equals(delivery.failureCode());
    }

    private void ensureDefaults(long systemId, long actorId) {
        for (var definition : DEFAULTS) {
            if (repository.template(systemId, definition.code()).isPresent()) continue;
            var templateId = repository.nextId();
            var versionId = repository.nextId();
            var allowedJson = json(definition.allowedVariables().stream().sorted().toList());
            try {
                repository.insertTemplate(new JdbcMessageTemplateRepository.TemplateRecord(templateId, systemId,
                        definition.code(), definition.eventType(), definition.name(), true,
                        definition.titleTemplate(), definition.bodyTemplate(), DEFAULT_CHANNELS_JSON, allowedJson,
                        null, null, actorId, null, actorId, 1));
                repository.insertVersion(new JdbcMessageTemplateRepository.VersionRecord(versionId, systemId,
                        templateId, definition.code(), definition.eventType(), 1, 1, true,
                        definition.titleTemplate(), definition.bodyTemplate(), DEFAULT_CHANNELS_JSON, allowedJson,
                        null, actorId));
                repository.pointInitialPublished(systemId, templateId, versionId);
            } catch (DuplicateKeyException ignored) {
                // A concurrent first-use transaction provisioned the same stable template.
            }
        }
    }

    private TemplateView view(JdbcMessageTemplateRepository.TemplateRecord template) {
        var published = repository.publishedVersion(template).orElse(null);
        return new TemplateView(template.templateCode(), template.eventType(), template.name(),
                template.desiredEnabled(), template.draftTitleTemplate(), template.draftBodyTemplate(),
                jsonList(template.channelsJson()), jsonList(template.allowedVariablesJson()),
                published == null ? null : published.versionNo(),
                published == null ? null : published.sourceDraftVersion(),
                published == null ? null : published.enabled(), template.version(),
                template.updatedAt() == null ? null : template.updatedAt().toString());
    }

    private JdbcMessageTemplateRepository.TemplateRecord requireTemplate(long systemId, String code) {
        return repository.template(systemId, code).orElseThrow(MessageTemplateService::notFound);
    }

    private static Definition definition(String code) {
        return DEFAULTS.stream().filter(value -> value.code().equals(code)).findFirst()
                .orElseThrow(MessageTemplateService::notFound);
    }

    private static boolean hasDefinition(String code) {
        return DEFAULTS.stream().anyMatch(value -> value.code().equals(code));
    }

    private static BusinessException notFound() {
        return new BusinessException("MESSAGE_TEMPLATE_NOT_FOUND", "Message template was not found",
                HttpStatus.NOT_FOUND);
    }

    private static String validateTemplate(String value, int max, Set<String> allowed) {
        var text = required(value, "Message template", max);
        var matcher = PLACEHOLDER.matcher(text);
        var remainder = new StringBuilder();
        int offset = 0;
        while (matcher.find()) {
            remainder.append(text, offset, matcher.start());
            if (!allowed.contains(matcher.group(1))) {
                throw invalid("Unknown template variable: " + matcher.group(1));
            }
            offset = matcher.end();
        }
        remainder.append(text.substring(offset));
        if (remainder.indexOf("{") >= 0 || remainder.indexOf("}") >= 0) {
            throw invalid("Template contains an invalid placeholder");
        }
        return text;
    }

    private static String render(String template, Map<String, String> variables, Set<String> allowed, int max) {
        var matcher = PLACEHOLDER.matcher(template);
        var rendered = new StringBuffer();
        while (matcher.find()) {
            var key = matcher.group(1);
            if (!allowed.contains(key) || !variables.containsKey(key)) {
                throw invalid("Missing template variable: " + key);
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(variables.get(key)));
        }
        matcher.appendTail(rendered);
        if (rendered.length() > max) throw invalid("Rendered message exceeds " + max + " characters");
        return rendered.toString();
    }

    private static List<DeliveryChannel> validateChannels(List<String> values) {
        if (values == null || values.isEmpty() || values.size() > DeliveryChannel.STABLE_ORDER.size()) {
            throw invalid("Template must declare one to three controlled delivery channels");
        }
        var channels = new LinkedHashSet<DeliveryChannel>();
        for (var value : values) {
            final DeliveryChannel channel;
            try {
                channel = DeliveryChannel.parse(value);
            } catch (RuntimeException invalid) {
                throw invalid("Template delivery channel is invalid");
            }
            if (!channels.add(channel)) throw invalid("Template delivery channels must be unique");
        }
        return channels.stream().sorted(DeliveryChannel.stableComparator()).toList();
    }

    private List<DeliveryChannel> channels(String json) {
        return validateChannels(jsonList(json));
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException(exception); }
    }

    private List<String> jsonList(String value) {
        try { return List.copyOf(mapper.readValue(value, new TypeReference<List<String>>() { })); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Stored template JSON is invalid", exception); }
    }

    private Set<String> jsonSet(String value) {
        return Set.copyOf(jsonList(value));
    }

    private JdbcMessageTemplateRepository.DeliveryRecord complete(
            JdbcMessageTemplateRepository.DeliveryRecord delivery,
            String status,
            Long messageId,
            String failureCode,
            String failureMessage,
            long durationMillis,
            String traceId,
            String maskedDestination
    ) {
        return repository.complete(delivery.id(), status, messageId, failureCode, failureMessage,
                Math.max(0, durationMillis), safeTrace(traceId, delivery),
                safeDestination(maskedDestination, delivery.recipientMemberId()));
    }

    private static long elapsed(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    private static String trace(JdbcMessageTemplateRepository.DeliveryRecord delivery) {
        return "evt-" + delivery.id() + "-" + delivery.attemptCount();
    }

    private static String safeTrace(String value, JdbcMessageTemplateRepository.DeliveryRecord delivery) {
        if (value == null || value.isBlank() || value.length() > 64
                || !value.matches("[A-Za-z0-9._:-]+")) return trace(delivery);
        return value;
    }

    private static String maskedMember(long memberId) {
        var id = Long.toString(memberId);
        return "member-***" + id.substring(Math.max(0, id.length() - 3));
    }

    private static String safeDestination(String value, long memberId) {
        if (value == null || value.isBlank() || value.length() > 200
                || value.contains("?") || value.contains("#") || value.contains("@") && !value.contains("***")) {
            return maskedMember(memberId);
        }
        return value;
    }

    private static String safeFailureCode(String value, String fallback) {
        return value != null && value.matches("[A-Z][A-Z0-9_]{1,63}") ? value : fallback;
    }

    private static ResultNotificationFacade.DeliveryReceipt receipt(
            JdbcMessageTemplateRepository.DeliveryRecord value, boolean replay) {
        return new ResultNotificationFacade.DeliveryReceipt(value.id(), value.messageId(), value.status(), replay);
    }

    private static String required(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw invalid(name + " must contain 1 to " + max + " characters");
        }
        return value.trim();
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) return "Notification delivery failed safely";
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("MESSAGE_TEMPLATE_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static BusinessException conflict() {
        return new BusinessException("MESSAGE_TEMPLATE_VERSION_CONFLICT", "Message template version is stale",
                HttpStatus.CONFLICT);
    }

    private record Definition(String code, String eventType, String name, String titleTemplate,
                              String bodyTemplate, Set<String> allowedVariables) { }

    public record UpdateCommand(long expectedVersion, String name, boolean enabled, String titleTemplate,
                                String bodyTemplate, List<String> channels) {
        public UpdateCommand { channels = channels == null ? List.of() : List.copyOf(channels); }
    }

    public record TemplateView(String templateCode, String eventType, String name, boolean enabled,
                               String titleTemplate, String bodyTemplate, List<String> channels,
                               List<String> allowedVariables, Long publishedVersion,
                               Long publishedSourceDraftVersion, Boolean publishedEnabled,
                               long version, String updatedAt) { }

    public record PreferenceTemplate(String templateCode, String eventType, String name,
                                     List<DeliveryChannel> channels) {
        public PreferenceTemplate {
            channels = channels == null ? List.of() : List.copyOf(channels);
        }

        public PreferenceTemplate(String templateCode, String eventType, String name) {
            this(templateCode, eventType, name, List.of(DeliveryChannel.INBOX));
        }
    }

    public record RetryOutcome(long deliveryId, Long messageId, String status,
                               int attemptCount, boolean retryable) { }
}
