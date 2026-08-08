package com.unique.examine.event.service;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.event.adapter.memory.InMemoryInboxMessageRepository;
import com.unique.examine.event.domain.EventActor;
import com.unique.examine.event.domain.EventDomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageInboxServiceTest {
    private MessageInboxService service;
    private EventActor sender;

    @BeforeEach
    void setUp() {
        service = new MessageInboxService(new InMemoryInboxMessageRepository(),
                (systemId, tenantId, memberId) -> systemId == 10 && tenantId == 20
                        && Set.of(100L, 101L, 102L).contains(memberId),
                Clock.fixed(Instant.parse("2026-07-25T09:00:00Z"), ZoneOffset.UTC));
        sender = actor(100, MessageInboxService.CREATE);
    }

    @Test
    void createReadReadAllArchiveAndUnreadCountFormAClosedInboxLifecycle() {
        var first = service.create(sender, 101, "TASK_ASSIGNED", "Task assigned", "Review release",
                new AggregateRef("WORK_TASK", "42"));
        var second = service.create(sender, 101, "TASK_DUE", "Task due", "Due tomorrow", null);
        var recipient = actor(101);

        assertThat(service.unreadCount(recipient)).isEqualTo(2);
        assertThat(service.markRead(recipient, first.id()).readAt()).isNotNull();
        assertThat(service.unreadCount(recipient)).isEqualTo(1);
        assertThat(service.markAllRead(recipient)).isEqualTo(1);
        assertThat(service.unreadCount(recipient)).isZero();

        assertThat(service.archive(recipient, second.id()).archivedAt()).isNotNull();
        assertThat(service.inbox(recipient, "ALL", 1, 20).items())
                .extracting("id").containsExactly(first.id());
        assertThat(service.inbox(recipient, "ARCHIVED", 1, 20).items())
                .extracting("id").containsExactly(second.id());
    }

    @Test
    void readAndArchiveAreIdempotent() {
        var message = service.create(sender, 101, "NOTICE", "Notice", "Body", null);
        var recipient = actor(101);

        var read = service.markRead(recipient, message.id());
        assertThat(service.markRead(recipient, message.id())).isEqualTo(read);
        var archived = service.archive(recipient, message.id());
        assertThat(service.archive(recipient, message.id())).isEqualTo(archived);
    }

    @Test
    void senderPermissionAndRecipientMembershipAreEnforced() {
        assertThatThrownBy(() -> service.create(actor(100), 101, "NOTICE", "Notice", "Body", null))
                .isInstanceOfSatisfying(EventDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("EVENT_MESSAGE_FORBIDDEN"));
        assertThatThrownBy(() -> service.create(sender, 999, "NOTICE", "Notice", "Body", null))
                .isInstanceOfSatisfying(EventDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("EVENT_MESSAGE_RECIPIENT_INVALID"));
    }

    @Test
    void anotherRecipientAndAnotherTenantCannotObserveOrMutateTheMessage() {
        var message = service.create(sender, 101, "NOTICE", "Notice", "Body", null);

        assertThat(service.unreadCount(actor(102))).isZero();
        assertThatThrownBy(() -> service.markRead(actor(102), message.id()))
                .isInstanceOfSatisfying(EventDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("EVENT_MESSAGE_NOT_FOUND"));
        assertThatThrownBy(() -> service.archive(
                new EventActor(10, 21, 101, Set.of()), message.id()))
                .isInstanceOfSatisfying(EventDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("EVENT_MESSAGE_NOT_FOUND"));
    }

    @Test
    void archivingAnUnreadMessageAlsoRemovesItFromUnreadCount() {
        var message = service.create(sender, 101, "NOTICE", "Notice", "Body", null);
        var recipient = actor(101);

        var archived = service.archive(recipient, message.id());
        assertThat(archived.readAt()).isNotNull();
        assertThat(service.unreadCount(recipient)).isZero();
    }

    @Test
    void filtersAndPagesByStatusWithStableCreatedAtAndIdOrdering() {
        var first = service.create(sender, 101, "NOTICE", "First", "Body", null);
        var second = service.create(sender, 101, "NOTICE", "Second", "Body", null);
        var third = service.create(sender, 101, "NOTICE", "Third", "Body", null);
        var recipient = actor(101);
        service.markRead(recipient, first.id());
        service.archive(recipient, second.id());

        var all = service.inbox(recipient, "ALL", 1, 20);
        assertThat(all.total()).isEqualTo(2);
        assertThat(all.items()).extracting("id").containsExactly(third.id(), first.id());
        assertThat(service.inbox(recipient, "UNREAD", 1, 20).items())
                .extracting("id").containsExactly(third.id());
        assertThat(service.inbox(recipient, "READ", 1, 20).items())
                .extracting("id").containsExactly(first.id());
        assertThat(service.inbox(recipient, "ARCHIVED", 1, 20).items())
                .extracting("id").containsExactly(second.id());

        var secondPage = service.inbox(recipient, "ALL", 2, 1);
        assertThat(secondPage.page()).isEqualTo(2);
        assertThat(secondPage.size()).isEqualTo(1);
        assertThat(secondPage.total()).isEqualTo(2);
        assertThat(secondPage.items()).extracting("id").containsExactly(first.id());
    }

    @Test
    void normalizesPagingAndRejectsUnknownStatus() {
        service.create(sender, 101, "NOTICE", "First", "Body", null);
        var normalized = service.inbox(actor(101), "all", -1, 500);

        assertThat(normalized.page()).isEqualTo(1);
        assertThat(normalized.size()).isEqualTo(100);
        assertThatThrownBy(() -> service.inbox(actor(101), "DELETED", 1, 20))
                .isInstanceOfSatisfying(EventDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("EVENT_MESSAGE_STATUS_INVALID"));
    }

    @Test
    void deterministicDeliveryKeyCreatesExactlyOneInboxFactAcrossReplay() {
        var target = new AggregateRef("WORK_TASK", "42");
        var first = service.createIdempotentDelivery(
                "work-task-reminder:10:20:42:1",
                10, 20, 101, "WORK_TASK_REMINDER",
                "Task reminder", "Review release",
                target, "/systems/10/work/tasks/42");
        var replay = service.createIdempotentDelivery(
                "work-task-reminder:10:20:42:1",
                10, 20, 101, "WORK_TASK_REMINDER",
                "Task reminder changed after persistence", "Changed body",
                target, "/systems/10/work/tasks/42");

        assertThat(replay).isEqualTo(first);
        assertThat(service.inbox(actor(101), "ALL", 1, 20).items())
                .containsExactly(first);
        assertThatThrownBy(() -> service.createIdempotentDelivery(
                "work-task-reminder:10:20:42:1",
                10, 20, 102, "WORK_TASK_REMINDER",
                "Task reminder", "Review release",
                target, "/systems/10/work/tasks/42"))
                .isInstanceOfSatisfying(EventDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("EVENT_MESSAGE_VERSION_CONFLICT"));
    }

    private static EventActor actor(long memberId, String... permissions) {
        return new EventActor(10, 20, memberId, Set.of(permissions));
    }
}
