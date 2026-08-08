package com.unique.examine.web.todo;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.event.adapter.memory.InMemoryInboxMessageRepository;
import com.unique.examine.event.domain.EventActor;
import com.unique.examine.event.domain.InboxMessage;
import com.unique.examine.event.service.MessageInboxService;
import com.unique.examine.event.todo.EventTodoReminderFacade;
import com.unique.examine.todo.domain.TodoActor;
import com.unique.examine.todo.domain.TodoItem;
import com.unique.examine.todo.domain.TodoSourceSnapshot;
import com.unique.examine.todo.port.TodoSourceActionCommand;
import com.unique.examine.todo.port.TodoSourceActionResult;
import com.unique.examine.todo.port.TodoSourceReference;
import com.unique.examine.todo.port.TodoSourceReload;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EventTodoAdapterTest {
    private static final Instant NOW =
            Instant.parse("2026-08-05T03:30:00Z");

    @Test
    void mapsOnlySafeReminderAndCcSnapshotsWithOwnerRoutes() {
        var fixture = fixture();
        fixture.workReminder();
        fixture.flowCopy();
        fixture.messages.create(
                creator(), 101, "UNRELATED_MESSAGE", "Unrelated",
                "must not be projected",
                new AggregateRef("WORK_TASK", "701"));

        var snapshots = fixture.adapter.loadOpen(todoActor(101));

        assertThat(snapshots).hasSize(2);
        assertThat(snapshots).extracting(TodoSourceSnapshot::category)
                .containsExactlyInAnyOrder(
                        TodoItem.Category.REMINDER, TodoItem.Category.CC);
        var reminder = snapshots.stream()
                .filter(value -> value.category()
                        == TodoItem.Category.REMINDER)
                .findFirst().orElseThrow();
        assertThat(reminder.title()).isEqualTo("Task reminder");
        assertThat(reminder.routeHint())
                .isEqualTo("/systems/10/tasks?taskId=700");
        assertThat(reminder.availableActions())
                .containsExactly(TodoItem.ActionCode.MARK_READ);
        assertThat(reminder.dueAt()).isNull();
        assertThat(reminder.representedMemberId()).isNull();
        var cc = snapshots.stream()
                .filter(value -> value.category() == TodoItem.Category.CC)
                .findFirst().orElseThrow();
        assertThat(cc.routeHint())
                .isEqualTo("/systems/10/flows?instanceId=800");
        assertThat(cc.priority()).isGreaterThan(reminder.priority());
    }

    @Test
    void reloadHidesForeignMessagesAndClosesExternallyCompletedSources() {
        var fixture = fixture();
        var message = fixture.flowCopy();
        var snapshot = fixture.adapter.loadOpen(todoActor(101)).getFirst();

        assertThat(fixture.adapter.reload(
                todoActor(101), reference(snapshot, message.version() + 1))
                .status()).isEqualTo(TodoSourceReload.Status.STALE);
        assertThat(fixture.adapter.reload(
                todoActor(102), reference(snapshot, message.version()))
                .status()).isEqualTo(TodoSourceReload.Status.MISSING);

        fixture.messages.markRead(recipient(), message.id(), message.version());

        assertThat(fixture.adapter.reload(
                todoActor(101), reference(snapshot, message.version()))
                .status()).isEqualTo(TodoSourceReload.Status.COMPLETED);
    }

    @Test
    void delegatesMarkReadToTheNativeEventOwner() {
        var fixture = fixture();
        var message = fixture.workReminder();
        var snapshot = fixture.adapter.loadOpen(todoActor(101)).getFirst();

        var result = fixture.adapter.execute(new TodoSourceActionCommand(
                10, 20, 101, Set.of("todo.access"),
                TodoItem.SourceType.EVENT_MESSAGE,
                Long.toString(message.id()), message.version(),
                EventTodoReminderFacade.ACTION_SCOPE,
                TodoItem.ActionCode.MARK_READ,
                null, null, null, "event-read-1",
                "request-1", "trace-1"));

        assertThat(result.code())
                .isEqualTo(TodoSourceActionResult.Code.SUCCESS);
        assertThat(result.sourceVersion()).isEqualTo(2);
        assertThat(fixture.messages.findOwn(recipient(), message.id()))
                .get().extracting(InboxMessage::status)
                .isEqualTo(InboxMessage.Status.READ);
        assertThat(fixture.adapter.reload(
                todoActor(101), reference(snapshot, message.version()))
                .status()).isEqualTo(TodoSourceReload.Status.COMPLETED);
    }

    private static Fixture fixture() {
        var messages = new MessageInboxService(
                new InMemoryInboxMessageRepository(),
                (systemId, tenantId, memberId) -> true,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(
                messages,
                new EventTodoAdapter(new EventTodoReminderFacade(messages)));
    }

    private static EventActor creator() {
        return new EventActor(
                10, 20, 100, Set.of(MessageInboxService.CREATE));
    }

    private static EventActor recipient() {
        return new EventActor(10, 20, 101, Set.of());
    }

    private static TodoActor todoActor(long memberId) {
        return new TodoActor(
                10, 20, memberId, Set.of("todo.access"),
                "request-1", "trace-1");
    }

    private static TodoSourceReference reference(
            TodoSourceSnapshot snapshot,
            long sourceVersion
    ) {
        return new TodoSourceReference(
                snapshot.identity().sourceType(),
                snapshot.identity().sourceId(), sourceVersion,
                snapshot.identity().actionScope(),
                snapshot.representedMemberId());
    }

    private record Fixture(
            MessageInboxService messages,
            EventTodoAdapter adapter
    ) {
        private InboxMessage workReminder() {
            return messages.createForDelivery(
                    501, creator(), 101,
                    EventTodoReminderFacade.WORK_TASK_REMINDER,
                    "Task reminder", "private reminder body",
                    new AggregateRef("WORK_TASK", "700"),
                    "/systems/10/work/tasks/700");
        }

        private InboxMessage flowCopy() {
            return messages.create(
                    creator(), 101,
                    EventTodoReminderFacade.FLOW_INSTANCE_COPIED,
                    "Flow copy", "private copy body",
                    new AggregateRef("FLOW_INSTANCE", "800"));
        }
    }
}
