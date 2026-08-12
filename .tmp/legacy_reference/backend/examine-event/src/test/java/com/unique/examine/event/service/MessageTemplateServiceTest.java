package com.unique.examine.event.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.ResultNotificationFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.job.DurableJobFacade;
import com.unique.examine.event.adapter.jdbc.JdbcMessageTemplateRepository;
import com.unique.examine.event.adapter.memory.InMemoryInboxMessageRepository;
import com.unique.examine.event.domain.DeliveryPreference;
import com.unique.examine.event.domain.DeliveryChannel;
import com.unique.examine.event.port.EventChannelTransport;
import com.unique.examine.event.support.TestDeliveryPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageTemplateServiceTest {
    private FakeTemplateRepository templates;
    private InMemoryInboxMessageRepository inbox;
    private TestDeliveryPreferenceRepository preferences;
    private FakeJobs jobs;
    private MessageTemplateService service;

    @BeforeEach
    void setUp() {
        templates = new FakeTemplateRepository();
        inbox = new InMemoryInboxMessageRepository();
        preferences = new TestDeliveryPreferenceRepository();
        jobs = new FakeJobs();
        var messages = new MessageInboxService(inbox,
                (systemId, tenantId, memberId) -> systemId == 10 && Set.of(20L, 21L).contains(tenantId)
                        && Set.of(100L, 101L).contains(memberId),
                Clock.fixed(Instant.parse("2026-07-29T08:00:00Z"), ZoneOffset.UTC));
        service = new MessageTemplateService(templates, preferences, messages, new ObjectMapper(), jobs);
    }

    @Test
    void provisionsValidatesVersionsAndPublishesOnlyTheRequestedDraft() {
        var defaults = service.list(10, 100);
        assertThat(defaults)
                .hasSize(8)
                .allSatisfy(view -> {
                    assertThat(view.publishedVersion()).isEqualTo(1);
                    assertThat(view.publishedSourceDraftVersion()).isEqualTo(1);
                    assertThat(view.channels()).containsExactly("INBOX");
                });
        assertThat(defaults)
                .filteredOn(view -> "FLOW_APPROVAL_DEADLINE_REMINDER"
                        .equals(view.templateCode()))
                .singleElement()
                .satisfies(view -> assertThat(view.allowedVariables())
                        .containsExactlyInAnyOrder(
                                "definitionId", "instanceId", "branchName", "dueAt"));

        assertThatThrownBy(() -> service.update(10, 100, "MODULE_EXPORT_SUCCEEDED",
                new MessageTemplateService.UpdateCommand(1, "导出完成", true,
                        "{unknown}", "共 {rows} 条", List.of("INBOX"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("MESSAGE_TEMPLATE_INVALID"));
        assertThatThrownBy(() -> service.update(10, 100, "MODULE_EXPORT_SUCCEEDED",
                new MessageTemplateService.UpdateCommand(1, "导出完成", true,
                        "导出完成", "共 {rows} 条", List.of("email"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("MESSAGE_TEMPLATE_INVALID"));

        var draft = service.update(10, 100, "MODULE_EXPORT_SUCCEEDED",
                new MessageTemplateService.UpdateCommand(1, "导出结果", false,
                        "{moduleCode} 导出完成", "共 {rows} 条", List.of("INBOX")));
        assertThat(draft.version()).isEqualTo(2);
        assertThat(draft.publishedSourceDraftVersion()).isEqualTo(1);
        assertThat(draft.publishedEnabled()).isTrue();

        var published = service.publish(10, 100, "MODULE_EXPORT_SUCCEEDED", 2);
        assertThat(published.publishedVersion()).isEqualTo(2);
        assertThat(published.publishedSourceDraftVersion()).isEqualTo(2);
        assertThat(published.publishedEnabled()).isFalse();
        assertThat(service.publish(10, 100, "MODULE_EXPORT_SUCCEEDED", 2).publishedVersion())
                .isEqualTo(2);
        assertThat(templates.versionCount("MODULE_EXPORT_SUCCEEDED")).isEqualTo(2);
    }

    @Test
    void rendersLinksDeduplicatesAndFailsSafelyForEveryNonDeliveryOutcome() {
        var delivered = service.dispatch(command("MODULE_IMPORT_SUCCEEDED", Map.of(
                "moduleCode", "purchase_order", "newRows", "3", "updateRows", "2"),
                101, "import-42"));
        assertThat(delivered.status()).isEqualTo("DELIVERED");
        assertThat(delivered.replay()).isFalse();
        assertThat(delivered.messageId()).isEqualTo(delivered.deliveryLogId());
        assertThat(inbox.findInbox(10, 20, 101)).singleElement().satisfies(message -> {
            assertThat(message.title()).isEqualTo("导入已完成");
            assertThat(message.body()).contains("purchase_order", "新增 3 条", "更新 2 条");
            assertThat(message.targetPath()).isEqualTo(
                    "/systems/10/workbench?module=purchase_order&panel=import&task=42");
        });

        var replay = service.dispatch(command("MODULE_IMPORT_SUCCEEDED", Map.of(
                "moduleCode", "purchase_order", "newRows", "3", "updateRows", "2"),
                101, "import-42"));
        assertThat(replay.deliveryLogId()).isEqualTo(delivered.deliveryLogId());
        assertThat(replay.status()).isEqualTo("DELIVERED");
        assertThat(replay.replay()).isTrue();
        assertThat(inbox.findInbox(10, 20, 101)).hasSize(1);

        service.update(10, 100, "MODULE_PRINT_SUCCEEDED",
                new MessageTemplateService.UpdateCommand(1, "打印结果", false,
                        "PDF 已生成", "记录 {recordNo} 的 {templateName} 已生成",
                        List.of("INBOX")));
        service.publish(10, 100, "MODULE_PRINT_SUCCEEDED", 2);
        var globallyDisabledPreference = new DeliveryPreference(preferences.nextId(), 10, 20, 101,
                "MODULE_PRINT_SUCCEEDED", DeliveryChannel.INBOX, false,
                Instant.parse("2026-07-29T07:30:00Z"), Instant.parse("2026-07-29T07:30:00Z"), 1);
        assertThat(preferences.insert(globallyDisabledPreference)).isTrue();
        assertThat(service.dispatch(command("MODULE_PRINT_SUCCEEDED", Map.of(
                "recordNo", "PO-42", "templateName", "采购单"), 101, "print-disabled")).status())
                .isEqualTo("SKIPPED");
        assertThat(templates.delivery("print-disabled").failureCode())
                .isEqualTo("MESSAGE_TEMPLATE_DISABLED");

        assertThat(service.dispatch(command("UNKNOWN_TEMPLATE", Map.of(), 101, "missing-template")).status())
                .isEqualTo("SKIPPED");
        assertThat(templates.delivery("missing-template").failureCode())
                .isEqualTo("MESSAGE_TEMPLATE_NOT_PUBLISHED");

        assertThat(service.dispatch(command("MODULE_IMPORT_SUCCEEDED", Map.of(
                "moduleCode", "purchase_order", "newRows", "3"), 101, "missing-variable")).status())
                .isEqualTo("FAILED");
        assertThat(templates.delivery("missing-variable").failureCode())
                .isEqualTo("MESSAGE_TEMPLATE_INVALID");

        assertThat(service.dispatch(command("MODULE_IMPORT_SUCCEEDED", Map.of(
                "moduleCode", "purchase_order", "newRows", "3", "updateRows", "2"),
                999, "inactive-recipient")).status()).isEqualTo("FAILED");
        assertThat(templates.delivery("inactive-recipient").failureCode())
                .isEqualTo("EVENT_MESSAGE_RECIPIENT_INVALID");

        var mentioned = service.dispatch(command("RECORD_COMMENT_MENTIONED", Map.of(
                "moduleCode", "purchase_order", "recordId", "42",
                "commentExcerpt", "请复核交付时间"), 101, "comment-mention-42"));
        assertThat(mentioned.status()).isEqualTo("DELIVERED");
        assertThat(inbox.findInbox(10, 20, 101)).filteredOn(message ->
                        "RECORD_COMMENT_MENTIONED".equals(message.templateCode()))
                .singleElement()
                .satisfies(message -> assertThat(message.body())
                        .contains("purchase_order", "42", "请复核交付时间"));
    }

    @Test
    void recipientPreferenceSkipsAfterBeginReplaysAndOnlyAffectsFutureExactOwnerDeliveries() {
        var now = Instant.parse("2026-07-29T07:45:00Z");
        var disabledPreference = new DeliveryPreference(preferences.nextId(), 10, 20, 101,
                "MODULE_EXPORT_SUCCEEDED", DeliveryChannel.INBOX,
                false, now, now, 1);
        assertThat(preferences.insert(disabledPreference)).isTrue();

        var variables = Map.of("moduleCode", "purchase_order", "rows", "4");
        var skipped = service.dispatch(command("MODULE_EXPORT_SUCCEEDED", variables,
                101, "export-preference-disabled"));
        assertThat(skipped.status()).isEqualTo("SKIPPED");
        assertThat(skipped.messageId()).isNull();
        assertThat(skipped.replay()).isFalse();
        assertThat(templates.delivery("export-preference-disabled")).satisfies(delivery -> {
            assertThat(delivery.attemptCount()).isEqualTo(1);
            assertThat(delivery.messageId()).isNull();
            assertThat(delivery.failureCode()).isEqualTo(
                    "MESSAGE_DELIVERY_DISABLED_BY_RECIPIENT");
            assertThat(delivery.failureMessage()).isEqualTo(
                    "Recipient disabled this notification");
        });
        assertThat(inbox.findInbox(10, 20, 101)).isEmpty();

        var replay = service.dispatch(command("MODULE_EXPORT_SUCCEEDED", variables,
                101, "export-preference-disabled"));
        assertThat(replay.deliveryLogId()).isEqualTo(skipped.deliveryLogId());
        assertThat(replay.status()).isEqualTo("SKIPPED");
        assertThat(replay.replay()).isTrue();
        assertThat(templates.deliveryCount("export-preference-disabled")).isEqualTo(1);
        assertThat(inbox.findInbox(10, 20, 101)).isEmpty();

        var otherRecipient = service.dispatch(command("MODULE_EXPORT_SUCCEEDED", variables,
                100, "export-other-recipient"));
        assertThat(otherRecipient.status()).isEqualTo("DELIVERED");
        assertThat(inbox.findInbox(10, 20, 100)).hasSize(1);

        var otherTenant = service.dispatch(command(21, "MODULE_EXPORT_SUCCEEDED", variables,
                101, "export-other-tenant"));
        assertThat(otherTenant.status()).isEqualTo("DELIVERED");
        assertThat(inbox.findInbox(10, 21, 101)).hasSize(1);

        var enabledPreference = disabledPreference.change(true,
                Instant.parse("2026-07-29T07:50:00Z"));
        assertThat(preferences.update(enabledPreference, 1)).isTrue();
        var future = service.dispatch(command("MODULE_EXPORT_SUCCEEDED", variables,
                101, "export-preference-enabled-future"));
        assertThat(future.status()).isEqualTo("DELIVERED");
        assertThat(inbox.findInbox(10, 20, 101)).singleElement()
                .satisfies(message -> assertThat(message.templateCode())
                        .isEqualTo("MODULE_EXPORT_SUCCEEDED"));
    }

    @Test
    void temporaryFailureEnqueuesOnceRedactsAndRecoversThroughTheRecordedDelivery() {
        var failingMessages = new MessageInboxService(inbox,
                (systemId, tenantId, memberId) -> true,
                Clock.fixed(Instant.parse("2026-07-29T08:00:00Z"), ZoneOffset.UTC)) {
            @Override
            public com.unique.examine.event.domain.InboxMessage createForDelivery(
                    long messageId, com.unique.examine.event.domain.EventActor actor,
                    long recipientMemberId, String templateCode, String title, String body,
                    AggregateRef target, String targetPath) {
                throw new IllegalStateException("jdbc password and host must never be persisted");
            }
        };
        service = new MessageTemplateService(templates, preferences, failingMessages,
                new ObjectMapper(), jobs);
        var command = command("MODULE_IMPORT_SUCCEEDED", Map.of(
                "moduleCode", "purchase_order", "newRows", "3", "updateRows", "2"),
                101, "temporary-import-42");

        var failed = service.dispatch(command);
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(templates.delivery("temporary-import-42")).satisfies(delivery -> {
            assertThat(delivery.attemptCount()).isEqualTo(1);
            assertThat(delivery.failureCode()).isEqualTo(MessageTemplateService.TEMPORARY_FAILURE);
            assertThat(delivery.failureMessage()).isEqualTo(MessageTemplateService.SAFE_TEMPORARY_MESSAGE);
        });
        assertThat(jobs.enqueued).singleElement().satisfies(job -> {
            assertThat(job.jobType()).isEqualTo(EventDeliveryRetryWorker.JOB_TYPE);
            assertThat(job.ownerType()).isEqualTo(EventDeliveryRetryWorker.OWNER_TYPE);
            assertThat(job.maxAttempts()).isEqualTo(2);
            assertThat(job.input()).doesNotContainValue("jdbc password and host must never be persisted");
        });

        assertThat(service.dispatch(command).replay()).isTrue();
        assertThat(jobs.enqueued).hasSize(1);

        service.update(10, 100, "MODULE_IMPORT_SUCCEEDED",
                new MessageTemplateService.UpdateCommand(1, "Changed import result", true,
                        "New retry title", "New {moduleCode} {newRows} {updateRows}",
                        List.of("INBOX")));
        service.publish(10, 100, "MODULE_IMPORT_SUCCEEDED", 2);

        var workingMessages = new MessageInboxService(inbox,
                (systemId, tenantId, memberId) -> systemId == 10 && tenantId == 20 && memberId == 101,
                Clock.fixed(Instant.parse("2026-07-29T08:05:00Z"), ZoneOffset.UTC));
        service = new MessageTemplateService(templates, preferences, workingMessages,
                new ObjectMapper(), jobs);
        var recovered = service.retry(failed.deliveryLogId(), command, 1);
        assertThat(recovered.status()).isEqualTo("DELIVERED");
        assertThat(recovered.attemptCount()).isEqualTo(2);
        assertThat(recovered.messageId()).isEqualTo(failed.deliveryLogId());
        assertThat(recovered.retryable()).isFalse();
        assertThat(inbox.findInbox(10, 20, 101)).singleElement()
                .satisfies(message -> {
                    assertThat(message.id()).isEqualTo(failed.deliveryLogId());
                    assertThat(message.title()).isNotEqualTo("New retry title");
                    assertThat(message.body()).doesNotStartWith("New purchase_order");
                });
    }

    @Test
    void repeatedTemporaryFailureStopsAfterTwoCompensationsAndThreeTotalAttempts() {
        var failingMessages = new MessageInboxService(inbox,
                (systemId, tenantId, memberId) -> true,
                Clock.fixed(Instant.parse("2026-07-29T08:00:00Z"), ZoneOffset.UTC)) {
            @Override
            public com.unique.examine.event.domain.InboxMessage createForDelivery(
                    long messageId, com.unique.examine.event.domain.EventActor actor,
                    long recipientMemberId, String templateCode, String title, String body,
                    AggregateRef target, String targetPath) {
                throw new IllegalStateException("provider-secret-must-not-survive");
            }
        };
        service = new MessageTemplateService(templates, preferences, failingMessages,
                new ObjectMapper(), jobs);
        var command = command("MODULE_EXPORT_SUCCEEDED",
                Map.of("moduleCode", "purchase_order", "rows", "4"),
                101, "temporary-export-exhausted");

        var initial = service.dispatch(command);
        var second = service.retry(initial.deliveryLogId(), command, 1);
        var third = service.retry(initial.deliveryLogId(), command, 2);

        assertThat(second.attemptCount()).isEqualTo(2);
        assertThat(second.retryable()).isTrue();
        assertThat(third.attemptCount()).isEqualTo(3);
        assertThat(third.status()).isEqualTo("FAILED");
        assertThat(third.retryable()).isFalse();
        assertThat(templates.retryTemporary(initial.deliveryLogId(), 2)).isEmpty();
        assertThat(templates.delivery("temporary-export-exhausted")).satisfies(delivery -> {
            assertThat(delivery.attemptCount()).isEqualTo(3);
            assertThat(delivery.failureCode()).isEqualTo(MessageTemplateService.TEMPORARY_FAILURE);
            assertThat(delivery.failureMessage()).isEqualTo(MessageTemplateService.SAFE_TEMPORARY_MESSAGE)
                    .doesNotContain("provider-secret-must-not-survive");
        });
        assertThat(inbox.findInbox(10, 20, 101)).isEmpty();
    }

    @Test
    void publishedChannelsFanOutIndependentlyInStableOrderAndReplayExactlyOnce() {
        var email = new FakeTransport(DeliveryChannel.EMAIL, EventChannelTransport.Status.SENT);
        var webhook = new FakeTransport(DeliveryChannel.WEBHOOK,
                EventChannelTransport.Status.PERMANENT_FAILURE);
        service = new MessageTemplateService(templates, preferences,
                new MessageInboxService(inbox,
                        (systemId, tenantId, memberId) -> true,
                        Clock.fixed(Instant.parse("2026-07-29T08:00:00Z"), ZoneOffset.UTC)),
                new ObjectMapper(), jobs, List.of(email, webhook));

        var draft = service.update(10, 100, "MODULE_EXPORT_SUCCEEDED",
                new MessageTemplateService.UpdateCommand(1, "Export result", true,
                        "Export {moduleCode}", "Exported {rows} rows",
                        List.of("WEBHOOK", "INBOX", "EMAIL")));
        assertThat(draft.channels()).containsExactly("INBOX", "EMAIL", "WEBHOOK");
        service.publish(10, 100, "MODULE_EXPORT_SUCCEEDED", draft.version());

        var command = command("MODULE_EXPORT_SUCCEEDED",
                Map.of("moduleCode", "purchase_order", "rows", "4"), 101, "fanout-42");
        var receipt = service.dispatch(command);

        assertThat(receipt.status()).isEqualTo("DELIVERED");
        assertThat(templates.deliveryCount("fanout-42")).isEqualTo(3);
        assertThat(templates.delivery("fanout-42", DeliveryChannel.INBOX).status()).isEqualTo("DELIVERED");
        assertThat(templates.delivery("fanout-42", DeliveryChannel.EMAIL).status()).isEqualTo("DELIVERED");
        assertThat(templates.delivery("fanout-42", DeliveryChannel.WEBHOOK)).satisfies(value -> {
            assertThat(value.status()).isEqualTo("FAILED");
            assertThat(value.failureCode()).isEqualTo("WEBHOOK_REJECTED");
        });
        assertThat(email.commands).hasSize(1);
        assertThat(webhook.commands).hasSize(1);

        assertThat(service.dispatch(command).replay()).isTrue();
        assertThat(templates.deliveryCount("fanout-42")).isEqualTo(3);
        assertThat(email.commands).hasSize(1);
        assertThat(webhook.commands).hasSize(1);
    }

    private static ResultNotificationFacade.Command command(String templateCode, Map<String, String> variables,
                                                             long recipient, String dedupeKey) {
        return command(20, templateCode, variables, recipient, dedupeKey);
    }

    private static ResultNotificationFacade.Command command(long tenantId, String templateCode,
                                                             Map<String, String> variables,
                                                             long recipient, String dedupeKey) {
        return new ResultNotificationFacade.Command(10, tenantId, 100, recipient, templateCode, variables,
                new AggregateRef("MODULE_IMPORT_BATCH", "42"),
                "/systems/10/workbench?module=purchase_order&panel=import&task=42", dedupeKey);
    }

    private static final class FakeTemplateRepository extends JdbcMessageTemplateRepository {
        private final Map<String, TemplateRecord> templates = new LinkedHashMap<>();
        private final Map<Long, VersionRecord> versions = new LinkedHashMap<>();
        private final Map<String, DeliveryRecord> deliveries = new LinkedHashMap<>();
        private long nextId = 1000;

        private FakeTemplateRepository() {
            super(null, null);
        }

        @Override
        public long nextId() {
            return ++nextId;
        }

        @Override
        public List<TemplateRecord> templates(long systemId) {
            return templates.values().stream().filter(value -> value.systemId() == systemId)
                    .sorted((left, right) -> left.templateCode().compareTo(right.templateCode())).toList();
        }

        @Override
        public Optional<TemplateRecord> template(long systemId, String code) {
            return Optional.ofNullable(templates.get(key(systemId, code)));
        }

        @Override
        public void insertTemplate(TemplateRecord value) {
            templates.putIfAbsent(key(value.systemId(), value.templateCode()), value);
        }

        @Override
        public void insertVersion(VersionRecord value) {
            versions.put(value.id(), value);
        }

        @Override
        public void pointInitialPublished(long systemId, long templateId, long versionId) {
            replace(templateById(templateId), null, null, null, versionId, null);
        }

        @Override
        public boolean updateDraft(long systemId, long templateId, long expectedVersion, String name,
                                   boolean enabled, String title, String body, String channelsJson, long actorId) {
            var current = templateById(templateId);
            if (current.systemId() != systemId || current.version() != expectedVersion) return false;
            replace(current, name, enabled, new String[] {title, body, channelsJson},
                    current.publishedVersionId(), current.version() + 1);
            return true;
        }

        @Override
        public Optional<VersionRecord> publishedVersion(TemplateRecord template) {
            return Optional.ofNullable(template.publishedVersionId()).map(versions::get);
        }

        @Override
        public Optional<VersionRecord> sourceVersion(long templateId, long sourceDraftVersion) {
            return versions.values().stream().filter(value -> value.templateId() == templateId
                    && value.sourceDraftVersion() == sourceDraftVersion).findFirst();
        }

        @Override
        public long nextVersionNo(long templateId) {
            return versions.values().stream().filter(value -> value.templateId() == templateId)
                    .mapToLong(VersionRecord::versionNo).max().orElse(0) + 1;
        }

        @Override
        public boolean publish(long systemId, long templateId, long expectedVersion,
                               long versionId, long actorId) {
            var current = templateById(templateId);
            if (current.systemId() != systemId || current.version() != expectedVersion) return false;
            replace(current, null, null, null, versionId, null);
            return true;
        }

        @Override
        public BeginDelivery begin(ResultNotificationFacade.Command command, Long templateVersionId) {
            return begin(command, templateVersionId, DeliveryChannel.INBOX);
        }

        @Override
        public BeginDelivery begin(ResultNotificationFacade.Command command, Long templateVersionId,
                                   DeliveryChannel channel) {
            var scoped = deliveryKey(command, channel);
            var existing = deliveries.get(scoped);
            if (existing != null) return new BeginDelivery(existing, true);
            var now = LocalDateTime.of(2026, 7, 29, 8, 0);
            var created = new DeliveryRecord(nextId(), command.systemId(), command.tenantId(),
                    command.recipientMemberId(), command.templateCode(), templateVersionId, channel.name(),
                    command.dedupeKey(), command.target().type(), command.target().id(), command.targetPath(),
                    "PENDING", 1, null, null, null, now, null);
            deliveries.put(scoped, created);
            return new BeginDelivery(created, false);
        }

        @Override
        public DeliveryRecord complete(long deliveryId, String status, Long messageId,
                                       String failureCode, String failureMessage) {
            var entry = deliveries.entrySet().stream()
                    .filter(value -> value.getValue().id() == deliveryId).findFirst().orElseThrow();
            var current = entry.getValue();
            if (!"PENDING".equals(current.status())) return current;
            var completed = new DeliveryRecord(current.id(), current.systemId(), current.tenantId(),
                    current.recipientMemberId(), current.templateCode(), current.templateVersionId(),
                    current.channel(), current.dedupeKey(), current.targetType(), current.targetId(),
                    current.targetPath(), status, current.attemptCount(), messageId, failureCode,
                    failureMessage, current.createdAt(), LocalDateTime.of(2026, 7, 29, 8, 1));
            deliveries.put(entry.getKey(), completed);
            return completed;
        }

        @Override
        public DeliveryRecord complete(long deliveryId, String status, Long messageId,
                                       String failureCode, String failureMessage, long durationMillis,
                                       String traceId, String maskedDestination) {
            return complete(deliveryId, status, messageId, failureCode, failureMessage);
        }

        @Override
        public Optional<DeliveryRecord> delivery(long deliveryId) {
            return deliveries.values().stream().filter(value -> value.id() == deliveryId).findFirst();
        }

        @Override
        public Optional<VersionRecord> version(long versionId) {
            return Optional.ofNullable(versions.get(versionId));
        }

        @Override
        public Optional<DeliveryRecord> retryTemporary(long deliveryId, int expectedAttemptCount) {
            var entry = deliveries.entrySet().stream()
                    .filter(value -> value.getValue().id() == deliveryId).findFirst().orElse(null);
            if (entry == null) return Optional.empty();
            var current = entry.getValue();
            if (!"FAILED".equals(current.status())
                    || !MessageTemplateService.TEMPORARY_FAILURE.equals(current.failureCode())
                    || current.attemptCount() != expectedAttemptCount || current.attemptCount() >= 3) {
                return Optional.empty();
            }
            var pending = new DeliveryRecord(current.id(), current.systemId(), current.tenantId(),
                    current.recipientMemberId(), current.templateCode(), current.templateVersionId(),
                    current.channel(), current.dedupeKey(), current.targetType(), current.targetId(),
                    current.targetPath(), "PENDING", current.attemptCount() + 1, null, null, null,
                    current.createdAt(), null);
            deliveries.put(entry.getKey(), pending);
            return Optional.of(pending);
        }

        private int versionCount(String code) {
            var template = templates.get(key(10, code));
            return (int) versions.values().stream().filter(value -> value.templateId() == template.id()).count();
        }

        private DeliveryRecord delivery(String dedupeKey) {
            return deliveries.values().stream().filter(value -> value.dedupeKey().equals(dedupeKey))
                    .findFirst().orElseThrow();
        }

        private DeliveryRecord delivery(String dedupeKey, DeliveryChannel channel) {
            return deliveries.values().stream().filter(value -> value.dedupeKey().equals(dedupeKey)
                            && value.channel().equals(channel.name()))
                    .findFirst().orElseThrow();
        }

        private long deliveryCount(String dedupeKey) {
            return deliveries.values().stream()
                    .filter(value -> value.dedupeKey().equals(dedupeKey)).count();
        }

        private TemplateRecord templateById(long id) {
            return templates.values().stream().filter(value -> value.id() == id).findFirst().orElseThrow();
        }

        private void replace(TemplateRecord current, String name, Boolean enabled, String[] draft,
                             Long publishedVersionId, Long version) {
            var updated = new TemplateRecord(current.id(), current.systemId(), current.templateCode(),
                    current.eventType(), name == null ? current.name() : name,
                    enabled == null ? current.desiredEnabled() : enabled,
                    draft == null ? current.draftTitleTemplate() : draft[0],
                    draft == null ? current.draftBodyTemplate() : draft[1],
                    draft == null ? current.channelsJson() : draft[2], current.allowedVariablesJson(),
                    publishedVersionId, current.createdAt(), current.createdBy(),
                    LocalDateTime.of(2026, 7, 29, 8, 0), current.updatedBy(),
                    version == null ? current.version() : version);
            templates.put(key(updated.systemId(), updated.templateCode()), updated);
        }

        private static String key(long systemId, String code) {
            return systemId + ":" + code;
        }

        private static String deliveryKey(ResultNotificationFacade.Command command,
                                          DeliveryChannel channel) {
            return command.systemId() + ":" + command.tenantId() + ":" + command.recipientMemberId()
                    + ":" + channel.name() + ":" + command.dedupeKey();
        }
    }

    private static final class FakeTransport implements EventChannelTransport {
        private final DeliveryChannel channel;
        private final Status status;
        private final List<DeliveryCommand> commands = new ArrayList<>();

        private FakeTransport(DeliveryChannel channel, Status status) {
            this.channel = channel;
            this.status = status;
        }

        @Override public DeliveryChannel channel() { return channel; }

        @Override
        public DeliveryResult deliver(DeliveryCommand command) {
            commands.add(command);
            return new DeliveryResult(status,
                    status == Status.PERMANENT_FAILURE ? "WEBHOOK_REJECTED" : null,
                    channel == DeliveryChannel.EMAIL ? "u***@example.com" : "https://hooks.example/***",
                    12, "trace-" + channel.name().toLowerCase());
        }
    }

    private static final class FakeJobs implements DurableJobFacade {
        private final List<JobRecord> enqueued = new ArrayList<>();

        @Override
        public JobRecord enqueue(EnqueueCommand command) {
            var now = LocalDateTime.of(2026, 7, 29, 8, 0);
            var job = new JobRecord(9000 + enqueued.size(), command.jobType(), command.ownerType(),
                    command.ownerId(), command.systemId(), command.tenantId(), command.requestedBy(),
                    "QUEUED", 0, command.input(), Map.of(), 0, command.maxAttempts(), now,
                    null, null, null, null, now, now, 0);
            enqueued.add(job);
            return job;
        }

        @Override public Optional<JobRecord> claim(String jobType, Duration lease) { return Optional.empty(); }
        @Override public JobRecord succeed(long jobId, long claimVersion, Map<String, Object> result) {
            throw new UnsupportedOperationException();
        }
        @Override public JobRecord fail(long jobId, long claimVersion, String error, Duration retryDelay) {
            throw new UnsupportedOperationException();
        }
        @Override public JobRecord require(long jobId) { throw new UnsupportedOperationException(); }
    }
}
