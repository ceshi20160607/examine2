package com.unique.examine.todo.ai;

import com.unique.examine.core.ai.AiTodoReadFacade;
import com.unique.examine.todo.domain.TodoActor;
import com.unique.examine.todo.domain.TodoItem;
import com.unique.examine.todo.domain.TodoQuery;
import com.unique.examine.todo.service.TodoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Objects;

/** Todo-owned translation from the strict AI request to native read services. */
@Component
public class AiTodoReadAdapter implements AiTodoReadFacade {
    private static final String REQUEST_ID = "ai-todo-read";

    private final TodoService todos;
    private final Clock clock;

    @Autowired
    public AiTodoReadAdapter(TodoService todos) {
        this(todos, Clock.systemUTC());
    }

    AiTodoReadAdapter(TodoService todos, Clock clock) {
        this.todos = Objects.requireNonNull(todos, "todos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional(readOnly = true)
    public Result query(Request request) {
        Objects.requireNonNull(request, "request");
        var day = LocalDate.now(clock);
        var todayStart = day.atStartOfDay().toInstant(ZoneOffset.UTC);
        var tomorrowStart = day.plusDays(1)
                .atStartOfDay().toInstant(ZoneOffset.UTC);
        var actor = new TodoActor(
                request.systemId(), request.tenantId(), request.memberId(),
                request.effectivePermissions(), REQUEST_ID, REQUEST_ID);
        var page = todos.page(actor, new TodoQuery(
                TodoQuery.CategoryFilter.valueOf(request.category().name()),
                TodoQuery.StateFilter.valueOf(request.state().name()),
                TodoQuery.TimeFilter.valueOf(request.time().name()),
                1, request.limit(), todayStart, tomorrowStart));
        var counts = todos.counts(actor);
        return new Result(
                request.category(), request.state(), request.time(),
                page.total(),
                new Counts(
                        counts.open(), counts.task(), counts.approval(),
                        counts.today(), counts.overdue()),
                page.items().stream()
                        .limit(request.limit())
                        .map(AiTodoReadAdapter::item)
                        .toList());
    }

    private static Item item(TodoItem value) {
        return new Item(
                Long.toString(value.id()),
                Category.valueOf(value.category().name()),
                SourceType.valueOf(value.identity().sourceType().name()),
                value.identity().sourceId(), value.title(), value.priority(),
                value.dueAt(), value.routeHint(),
                value.availableActions().stream()
                        .map(action -> Action.valueOf(action.name()))
                        .sorted(Comparator.comparing(Enum::name))
                        .toList(),
                State.valueOf(value.state().name()), value.version());
    }
}
