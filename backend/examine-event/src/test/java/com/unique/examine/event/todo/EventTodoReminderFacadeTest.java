package com.unique.examine.event.todo;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.event.adapter.memory.InMemoryInboxMessageRepository;
import com.unique.examine.event.domain.EventActor;
import com.unique.examine.event.domain.EventDomainException;
import com.unique.examine.event.domain.InboxMessage;
import com.unique.examine.event.domain.InboxMessageFilter;
import com.unique.examine.event.port.InboxMessageRepository;
import com.unique.examine.event.service.MessageInboxService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EventTodoReminderFacadeTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");
    private static final EventTodoReminderFacade.Actor ACTOR =
            new EventTodoReminderFacade.Actor(10, 20, 100);

    @Test
    void listsOnlyOwnedUnreadSupportedMessagesAsNarrowSafeSnapshots() {
        var repository = new InMemoryInboxMessageRepository();
        var facade = facade(repository);
        repository.save(workReminder(1, 10, 20, 100, 41, NOW.minusSeconds(2)));
        repository.save(flowCopy(2, 10, 20, 100, 51, NOW.minusSeconds(1)));
        repository.save(message(3, 10, 20, 100, "FLOW_INSTANCE_URGED",
                new AggregateRef("FLOW_INSTANCE", "52"), null, NOW));
        repository.save(message(4, 10, 20, 101, EventTodoReminderFacade.WORK_TASK_REMINDER,
                new AggregateRef("WORK_TASK", "42"), "/systems/10/work/tasks/42", NOW));
        repository.save(message(5, 10, 21, 100, EventTodoReminderFacade.WORK_TASK_REMINDER,
                new AggregateRef("WORK_TASK", "43"), "/systems/10/work/tasks/43", NOW));
        repository.save(message(6, 11, 20, 100, EventTodoReminderFacade.WORK_TASK_REMINDER,
                new AggregateRef("WORK_TASK", "44"), "/systems/11/work/tasks/44", NOW));
        repository.save(message(7, 10, 20, 100, EventTodoReminderFacade.WORK_TASK_REMINDER,
                new AggregateRef("FLOW_INSTANCE", "45"), "/systems/10/work/tasks/45", NOW));
        var read = repository.save(workReminder(8, 10, 20, 100, 46, NOW));
        repository.save(read.markRead(NOW.plusSeconds(1)));
        repository.save(message(9, 10, 20, 100, EventTodoReminderFacade.WORK_TASK_REMINDER,
                new AggregateRef("WORK_TASK", "47"), "/systems/10/tasks?taskId=47", NOW));

        assertThat(facade.listUnread(ACTOR))
                .extracting(EventTodoReminderFacade.Snapshot::messageId)
                .containsExactly(2L, 1L);
        assertThat(facade.listUnread(ACTOR).getFirst()).satisfies(snapshot -> {
            assertThat(snapshot.templateCode())
                    .isEqualTo(EventTodoReminderFacade.FLOW_INSTANCE_COPIED);
            assertThat(snapshot.targetPath())
                    .isEqualTo("/systems/10/flows?instanceId=51");
        });
        assertThat(facade.listUnread(ACTOR).getLast().targetPath())
                .isEqualTo("/systems/10/tasks?taskId=41");
        assertThat(Arrays.stream(EventTodoReminderFacade.Snapshot.class.getRecordComponents())
                .map(component -> component.getName()))
                .containsExactly("messageId", "version", "templateCode", "title",
                        "targetPath", "createdAt")
                .doesNotContain("body", "senderMemberId", "variables");
    }

    @Test
    void reloadSeparatesLiveStaleCompletedAndIneligibleWithoutScopeLeakage() {
        var repository = new InMemoryInboxMessageRepository();
        var facade = facade(repository);
        repository.save(workReminder(1, 10, 20, 100, 41, NOW));
        var read = repository.save(workReminder(2, 10, 20, 100, 42, NOW));
        repository.save(read.markRead(NOW.plusSeconds(1)));
        repository.save(message(3, 10, 20, 100, "FLOW_INSTANCE_URGED",
                new AggregateRef("FLOW_INSTANCE", "52"), null, NOW));
        repository.save(workReminder(4, 10, 20, 101, 43, NOW));
        repository.save(workReminder(5, 10, 21, 100, 44, NOW));

        assertThat(facade.reload(ACTOR, 1, 1)).satisfies(result -> {
            assertThat(result.status()).isEqualTo(EventTodoReminderFacade.Reload.Status.LIVE);
            assertThat(result.snapshot().messageId()).isEqualTo(1);
        });
        assertThat(facade.reload(ACTOR, 1, 2).status())
                .isEqualTo(EventTodoReminderFacade.Reload.Status.STALE);
        assertThat(facade.reload(ACTOR, 2, 2).status())
                .isEqualTo(EventTodoReminderFacade.Reload.Status.COMPLETED);
        assertThat(facade.reload(ACTOR, 3, 1).status())
                .isEqualTo(EventTodoReminderFacade.Reload.Status.INELIGIBLE);
        assertThat(facade.reload(ACTOR, 4, 1).status())
                .isEqualTo(EventTodoReminderFacade.Reload.Status.MISSING);
        assertThat(facade.reload(ACTOR, 5, 1).status())
                .isEqualTo(EventTodoReminderFacade.Reload.Status.MISSING);
        assertThat(facade.reload(new EventTodoReminderFacade.Actor(11, 20, 100), 1, 1).status())
                .isEqualTo(EventTodoReminderFacade.Reload.Status.MISSING);
    }

    @Test
    void markReadUsesInboxServiceOptimisticVersionAndMapsConcurrentConflictToStale() {
        var repository = new InMemoryInboxMessageRepository();
        var messages = messages(repository);
        var facade = new EventTodoReminderFacade(messages);
        repository.save(workReminder(1, 10, 20, 100, 41, NOW));
        repository.save(workReminder(2, 10, 20, 101, 42, NOW));

        var success = facade.markRead(ACTOR, 1, 1);
        assertThat(success.code()).isEqualTo(EventTodoReminderFacade.ActionResult.Code.SUCCESS);
        assertThat(success.sourceVersion()).isEqualTo(2);
        assertThat(messages.findOwn(eventActor(), 1)).hasValueSatisfying(message -> {
            assertThat(message.status()).isEqualTo(InboxMessage.Status.READ);
            assertThat(message.version()).isEqualTo(2);
        });
        assertThat(facade.markRead(ACTOR, 1, 2).code())
                .isEqualTo(EventTodoReminderFacade.ActionResult.Code.COMPLETED);
        assertThat(facade.markRead(ACTOR, 2, 1).code())
                .isEqualTo(EventTodoReminderFacade.ActionResult.Code.MISSING);

        repository.save(workReminder(3, 10, 20, 100, 43, NOW));
        assertThat(facade.markRead(ACTOR, 3, 2).code())
                .isEqualTo(EventTodoReminderFacade.ActionResult.Code.STALE);

        var conflicting = new ConflictOnUpdateRepository(
                workReminder(9, 10, 20, 100, 49, NOW));
        assertThat(facade(conflicting).markRead(ACTOR, 9, 1).code())
                .isEqualTo(EventTodoReminderFacade.ActionResult.Code.STALE);
    }

    private static EventTodoReminderFacade facade(InboxMessageRepository repository) {
        return new EventTodoReminderFacade(messages(repository));
    }

    private static MessageInboxService messages(InboxMessageRepository repository) {
        return new MessageInboxService(repository, (systemId, tenantId, memberId) -> true,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static EventActor eventActor() {
        return new EventActor(10, 20, 100, java.util.Set.of());
    }

    private static InboxMessage workReminder(
            long id, long systemId, long tenantId, long recipientId,
            long taskId, Instant createdAt
    ) {
        return message(id, systemId, tenantId, recipientId,
                EventTodoReminderFacade.WORK_TASK_REMINDER,
                new AggregateRef("WORK_TASK", Long.toString(taskId)),
                "/systems/" + systemId + "/work/tasks/" + taskId,
                createdAt);
    }

    private static InboxMessage flowCopy(
            long id, long systemId, long tenantId, long recipientId,
            long instanceId, Instant createdAt
    ) {
        return message(id, systemId, tenantId, recipientId,
                EventTodoReminderFacade.FLOW_INSTANCE_COPIED,
                new AggregateRef("FLOW_INSTANCE", Long.toString(instanceId)),
                null, createdAt);
    }

    private static InboxMessage message(
            long id, long systemId, long tenantId, long recipientId,
            String templateCode, AggregateRef target, String targetPath,
            Instant createdAt
    ) {
        return new InboxMessage(id, systemId, tenantId, 90, recipientId,
                templateCode, "Safe reminder title", "sensitive body", target,
                targetPath, createdAt, null, null, 1);
    }

    private static final class ConflictOnUpdateRepository
            implements InboxMessageRepository {
        private final InboxMessage message;

        private ConflictOnUpdateRepository(InboxMessage message) {
            this.message = message;
        }

        @Override
        public long nextId() {
            return 10;
        }

        @Override
        public Optional<InboxMessage> findById(long systemId, long tenantId, long id) {
            return systemId == message.systemId() && tenantId == message.tenantId()
                    && id == message.id() ? Optional.of(message) : Optional.empty();
        }

        @Override
        public List<InboxMessage> findInbox(long systemId, long tenantId, long recipientMemberId) {
            return findById(systemId, tenantId, message.id())
                    .filter(value -> value.recipientMemberId() == recipientMemberId)
                    .stream().toList();
        }

        @Override
        public long countInbox(long systemId, long tenantId, long recipientMemberId,
                               InboxMessageFilter status) {
            return findInbox(systemId, tenantId, recipientMemberId).stream()
                    .filter(status::includes).count();
        }

        @Override
        public List<InboxMessage> findInboxPage(long systemId, long tenantId,
                                                long recipientMemberId,
                                                InboxMessageFilter status,
                                                long offset, int limit) {
            return findInbox(systemId, tenantId, recipientMemberId).stream()
                    .filter(status::includes).skip(offset).limit(limit).toList();
        }

        @Override
        public InboxMessage save(InboxMessage value) {
            throw new EventDomainException(
                    "EVENT_MESSAGE_VERSION_CONFLICT", "Message version is stale");
        }
    }
}
