package com.unique.examine.web.todo;

import com.unique.examine.event.todo.EventTodoReminderFacade;
import com.unique.examine.todo.domain.TodoActor;
import com.unique.examine.todo.domain.TodoIdentity;
import com.unique.examine.todo.domain.TodoItem;
import com.unique.examine.todo.domain.TodoSourceSnapshot;
import com.unique.examine.todo.port.TodoActionPort;
import com.unique.examine.todo.port.TodoSourceActionCommand;
import com.unique.examine.todo.port.TodoSourceActionResult;
import com.unique.examine.todo.port.TodoSourcePort;
import com.unique.examine.todo.port.TodoSourceReference;
import com.unique.examine.todo.port.TodoSourceReload;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** Composes Event's recipient-safe inbox boundary into Todo. */
@Component
public final class EventTodoAdapter implements TodoSourcePort, TodoActionPort {
    private static final String WORK_REMINDER = "WORK_TASK_REMINDER";
    private static final String FLOW_COPY = "FLOW_INSTANCE_COPIED";
    private static final int REMINDER_PRIORITY = 75;
    private static final int CC_PRIORITY = 150;

    private final EventTodoReminderFacade events;

    public EventTodoAdapter(EventTodoReminderFacade events) {
        if (events == null) {
            throw new IllegalArgumentException(
                    "Event Todo reminder facade is required");
        }
        this.events = events;
    }

    @Override
    public TodoItem.SourceType sourceType() {
        return TodoItem.SourceType.EVENT_MESSAGE;
    }

    @Override
    public List<TodoSourceSnapshot> loadOpen(TodoActor actor) {
        return events.listUnread(eventActor(actor)).stream()
                .filter(EventTodoAdapter::recognized)
                .map(value -> snapshot(actor, value))
                .toList();
    }

    @Override
    public TodoSourceReload reload(
            TodoActor actor,
            TodoSourceReference reference
    ) {
        if (reference.sourceType() != sourceType()
                || !EventTodoReminderFacade.ACTION_SCOPE.equals(
                        reference.actionScope())
                || reference.representedMemberId() != null) {
            return TodoSourceReload.of(TodoSourceReload.Status.INELIGIBLE);
        }
        var messageId = positiveId(reference.sourceId());
        if (messageId == null) {
            return TodoSourceReload.of(TodoSourceReload.Status.MISSING);
        }
        var result = events.reload(
                eventActor(actor), messageId, reference.sourceVersion());
        return switch (result.status()) {
            case LIVE -> result.snapshot() != null
                    && recognized(result.snapshot())
                    ? TodoSourceReload.live(snapshot(actor, result.snapshot()))
                    : TodoSourceReload.of(TodoSourceReload.Status.INELIGIBLE);
            case STALE -> TodoSourceReload.of(TodoSourceReload.Status.STALE);
            case COMPLETED -> TodoSourceReload.of(
                    TodoSourceReload.Status.COMPLETED);
            case MISSING -> TodoSourceReload.of(TodoSourceReload.Status.MISSING);
            case INELIGIBLE -> TodoSourceReload.of(
                    TodoSourceReload.Status.INELIGIBLE);
        };
    }

    @Override
    public TodoSourceActionResult execute(TodoSourceActionCommand command) {
        if (command.sourceType() != sourceType()
                || command.action() != TodoItem.ActionCode.MARK_READ
                || !EventTodoReminderFacade.ACTION_SCOPE.equals(
                        command.actionScope())
                || command.representedMemberId() != null
                || command.comment() != null
                || command.reason() != null) {
            return result(
                    TodoSourceActionResult.Code.DENIED,
                    command.expectedSourceVersion(),
                    "Event message mark-read is not available");
        }
        var messageId = positiveId(command.sourceId());
        if (messageId == null) {
            return result(
                    TodoSourceActionResult.Code.STALE,
                    command.expectedSourceVersion(),
                    "Event message is no longer available");
        }
        var nativeResult = events.markRead(
                new EventTodoReminderFacade.Actor(
                        command.systemId(), command.tenantId(),
                        command.actorMemberId()),
                messageId, command.expectedSourceVersion());
        return switch (nativeResult.code()) {
            case SUCCESS -> result(
                    TodoSourceActionResult.Code.SUCCESS,
                    nativeResult.sourceVersion(),
                    "Event message marked read");
            case COMPLETED -> result(
                    TodoSourceActionResult.Code.SUCCESS,
                    nativeResult.sourceVersion(),
                    "Event message was already completed");
            case STALE -> result(
                    TodoSourceActionResult.Code.STALE,
                    nativeResult.sourceVersion(),
                    "Event message changed before mark-read");
            case MISSING -> result(
                    TodoSourceActionResult.Code.STALE,
                    nativeResult.sourceVersion(),
                    "Event message is no longer available");
            case INELIGIBLE -> result(
                    TodoSourceActionResult.Code.DENIED,
                    nativeResult.sourceVersion(),
                    "Event message is not eligible for Todo");
        };
    }

    private static TodoSourceSnapshot snapshot(
            TodoActor actor,
            EventTodoReminderFacade.Snapshot value
    ) {
        var category = category(value.templateCode());
        return new TodoSourceSnapshot(
                new TodoIdentity(
                        actor.systemId(), actor.tenantId(), actor.memberId(),
                        TodoItem.SourceType.EVENT_MESSAGE,
                        Long.toString(value.messageId()),
                        EventTodoReminderFacade.ACTION_SCOPE),
                value.version(), category,
                category == TodoItem.Category.REMINDER
                        ? REMINDER_PRIORITY : CC_PRIORITY,
                value.title(), null, value.targetPath(),
                Set.of(TodoItem.ActionCode.MARK_READ), null);
    }

    private static EventTodoReminderFacade.Actor eventActor(TodoActor actor) {
        return new EventTodoReminderFacade.Actor(
                actor.systemId(), actor.tenantId(), actor.memberId());
    }

    private static boolean recognized(
            EventTodoReminderFacade.Snapshot snapshot
    ) {
        return snapshot != null
                && (WORK_REMINDER.equals(snapshot.templateCode())
                || FLOW_COPY.equals(snapshot.templateCode()));
    }

    private static TodoItem.Category category(String templateCode) {
        return WORK_REMINDER.equals(templateCode)
                ? TodoItem.Category.REMINDER : TodoItem.Category.CC;
    }

    private static Long positiveId(String value) {
        try {
            var parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private static TodoSourceActionResult result(
            TodoSourceActionResult.Code code,
            long sourceVersion,
            String message
    ) {
        return new TodoSourceActionResult(code, sourceVersion, message);
    }
}
