package com.unique.examine.todo.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TodoItemTest {
    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void sameProjectionRefreshDoesNotAdvanceVersion() {
        var source = taskSource(1, "Task");
        var item = source.create(10, NOW);

        assertThat(item.reconcile(source, NOW.plusSeconds(1))).isSameAs(item);
        assertThat(item.version()).isEqualTo(1);
    }

    @Test
    void newerProjectionUpdatesButClosedIdentityNeverReopens() {
        var item = taskSource(1, "Task").create(10, NOW);
        var updated = item.reconcile(taskSource(2, "Task changed"), NOW.plusSeconds(1));
        var closed = updated.close(TodoItem.CloseReason.SOURCE_COMPLETED, NOW.plusSeconds(2));

        assertThat(updated.sourceVersion()).isEqualTo(2);
        assertThat(updated.title()).isEqualTo("Task changed");
        assertThat(closed.reconcile(taskSource(3, "Reopened"), NOW.plusSeconds(3)))
                .isSameAs(closed);
        assertThat(closed.state()).isEqualTo(TodoItem.State.CLOSED);
    }

    @Test
    void sourceCategoryActionsAndRepresentedAuthorityAreStrict() {
        assertThatThrownBy(() -> new TodoSourceSnapshot(
                identity(TodoItem.SourceType.WORK_TASK), 1,
                TodoItem.Category.TASK, 10, "Task", null, "/work/tasks/1",
                Set.of(TodoItem.ActionCode.COMPLETE), 99L).create(1, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TodoSourceSnapshot(
                identity(TodoItem.SourceType.FLOW_APPROVAL), 1,
                TodoItem.Category.APPROVAL, 1, "Approval", null, "/flows/1",
                Set.of(TodoItem.ActionCode.APPROVE), 99L).create(1, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eventMessagesAreStrictReminderOrCcMarkReadProjections() {
        var reminder = eventSource(TodoItem.Category.REMINDER, "101");
        var cc = eventSource(TodoItem.Category.CC, "102");

        assertThat(reminder.create(11, NOW).availableActions())
                .containsExactly(TodoItem.ActionCode.MARK_READ);
        assertThat(cc.create(12, NOW).category()).isEqualTo(TodoItem.Category.CC);
        assertThatThrownBy(() -> new TodoSourceSnapshot(
                eventIdentity("103", "MARK_READ"), 1, TodoItem.Category.TASK,
                10, "Message", null, "/messages/103",
                Set.of(TodoItem.ActionCode.MARK_READ), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TodoSourceSnapshot(
                eventIdentity("104", "MARK_READ"), 1, TodoItem.Category.REMINDER,
                10, "Message", null, "/messages/104",
                Set.of(TodoItem.ActionCode.COMPLETE), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TodoSourceSnapshot(
                eventIdentity("105", "MARK_READ"), 1, TodoItem.Category.CC,
                10, "Message", null, "/messages/105",
                Set.of(TodoItem.ActionCode.MARK_READ), 30L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> eventIdentity("001", "MARK_READ"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> eventIdentity("106", "READ"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static TodoSourceSnapshot taskSource(long version, String title) {
        return new TodoSourceSnapshot(identity(TodoItem.SourceType.WORK_TASK),
                version, TodoItem.Category.TASK, 10, title,
                NOW.plusSeconds(100), "/work/tasks/1",
                Set.of(TodoItem.ActionCode.COMPLETE), null);
    }

    private static TodoIdentity identity(TodoItem.SourceType type) {
        return new TodoIdentity(10, 20, 30, type, "1", "scope-1");
    }

    private static TodoSourceSnapshot eventSource(
            TodoItem.Category category, String sourceId) {
        return new TodoSourceSnapshot(eventIdentity(sourceId, "MARK_READ"),
                1, category, 10, "Message " + sourceId, null,
                "/messages/" + sourceId,
                Set.of(TodoItem.ActionCode.MARK_READ), null);
    }

    private static TodoIdentity eventIdentity(String sourceId, String actionScope) {
        return new TodoIdentity(10, 20, 30, TodoItem.SourceType.EVENT_MESSAGE,
                sourceId, actionScope);
    }
}
