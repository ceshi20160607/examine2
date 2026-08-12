package com.unique.examine.core.ai;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiTodoReadFacadeTest {

    @Test
    void requestAndResultAreStrictBoundedTypedSnapshots() {
        var request = new AiTodoReadFacade.Request(
                10, 20, 30,
                Set.of("todo.access"),
                AiTodoReadFacade.Category.TASK,
                AiTodoReadFacade.State.OPEN,
                AiTodoReadFacade.Time.TODAY,
                20);
        assertThat(request.category()).isEqualTo(
                AiTodoReadFacade.Category.TASK);

        var mutable = new ArrayList<>(List.of(item("1")));
        var result = new AiTodoReadFacade.Result(
                request.category(), request.state(), request.time(), 1,
                new AiTodoReadFacade.Counts(1, 1, 0, 1, 0), mutable);
        mutable.clear();

        assertThat(result.items()).containsExactly(item("1"));
        assertThat(result.items().getFirst().actions())
                .containsExactly(AiTodoReadFacade.Action.COMPLETE);

        var reminder = new AiTodoReadFacade.Item(
                "2", AiTodoReadFacade.Category.REMINDER,
                AiTodoReadFacade.SourceType.EVENT_MESSAGE, "2",
                "Task reminder", 80, null,
                "/systems/10/tasks?taskId=2",
                List.of(AiTodoReadFacade.Action.MARK_READ),
                AiTodoReadFacade.State.OPEN, 1);
        assertThat(reminder.actions())
                .containsExactly(AiTodoReadFacade.Action.MARK_READ);
        assertThat(new AiTodoReadFacade.Counts(2, 1, 0, 1, 0).open())
                .isEqualTo(2);
    }

    @Test
    void rejectsInvalidScopeLimitCountsAndItemShapes() {
        assertThatThrownBy(() -> request(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1..20");
        assertThatThrownBy(() -> request(21))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1..20");
        assertThatThrownBy(() -> new AiTodoReadFacade.Request(
                10, 20, 0, Set.of(), AiTodoReadFacade.Category.ALL,
                AiTodoReadFacade.State.ALL, AiTodoReadFacade.Time.ALL, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("memberId");
        assertThatThrownBy(() -> new AiTodoReadFacade.Counts(
                -1, 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("counts");
        assertThatThrownBy(() -> new AiTodoReadFacade.Item(
                "1", AiTodoReadFacade.Category.ALL,
                AiTodoReadFacade.SourceType.WORK_TASK, "1", "Task", 1,
                null, "/tasks/1", List.of(AiTodoReadFacade.Action.COMPLETE),
                AiTodoReadFacade.State.OPEN, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("category");
        assertThatThrownBy(() -> new AiTodoReadFacade.Result(
                AiTodoReadFacade.Category.ALL,
                AiTodoReadFacade.State.ALL,
                AiTodoReadFacade.Time.ALL,
                21,
                new AiTodoReadFacade.Counts(0, 0, 0, 0, 0),
                java.util.stream.IntStream.rangeClosed(1, 21)
                        .mapToObj(value -> item(Integer.toString(value)))
                        .toList()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("20");
    }

    private static AiTodoReadFacade.Request request(int limit) {
        return new AiTodoReadFacade.Request(
                10, 20, 30, Set.of(), AiTodoReadFacade.Category.ALL,
                AiTodoReadFacade.State.ALL, AiTodoReadFacade.Time.ALL, limit);
    }

    private static AiTodoReadFacade.Item item(String id) {
        return new AiTodoReadFacade.Item(
                id, AiTodoReadFacade.Category.TASK,
                AiTodoReadFacade.SourceType.WORK_TASK, id,
                "Task " + id, 10,
                Instant.parse("2026-08-04T09:00:00Z"),
                "/systems/10/work/tasks/" + id,
                List.of(AiTodoReadFacade.Action.COMPLETE),
                AiTodoReadFacade.State.OPEN, 1);
    }
}
