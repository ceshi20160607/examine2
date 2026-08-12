package com.unique.examine.web.analytics;

import com.unique.examine.analytics.domain.AnalyticsActor;
import com.unique.examine.analytics.domain.AnalyticsRange;
import com.unique.examine.analytics.domain.OperationsDashboard;
import com.unique.examine.analytics.port.TodoAnalyticsSource;
import com.unique.examine.todo.domain.TodoActor;
import com.unique.examine.todo.service.TodoService;
import org.springframework.stereotype.Component;

@Component
public final class TodoOperationsAnalyticsAdapter implements TodoAnalyticsSource {
    private final TodoService todos;

    public TodoOperationsAnalyticsAdapter(TodoService todos) {
        if (todos == null) throw new IllegalArgumentException("Todo service is required");
        this.todos = todos;
    }

    @Override
    public OperationsDashboard.TodoSection load(AnalyticsActor actor, AnalyticsRange range) {
        var counts = todos.counts(new TodoActor(actor.accountId(), actor.systemId(),
                actor.tenantId(), actor.memberId(), actor.permissions(),
                "analytics-read", "analytics-read"));
        return new OperationsDashboard.TodoSection(true, null,
                counts.open(), counts.task(), counts.approval(),
                counts.today(), counts.overdue(),
                "/systems/" + actor.systemId() + "/todos");
    }
}
