package com.unique.examine.analytics.service;

import com.unique.examine.analytics.domain.AnalyticsActor;
import com.unique.examine.analytics.domain.AnalyticsRange;
import com.unique.examine.analytics.domain.OperationsDashboard;
import com.unique.examine.analytics.port.FlowAnalyticsSource;
import com.unique.examine.analytics.port.TodoAnalyticsSource;
import com.unique.examine.analytics.port.WorkAnalyticsSource;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

public final class OperationsDashboardService {
    private final WorkAnalyticsSource work;
    private final FlowAnalyticsSource flow;
    private final TodoAnalyticsSource todo;
    private final Clock clock;

    public OperationsDashboardService(
            WorkAnalyticsSource work,
            FlowAnalyticsSource flow,
            TodoAnalyticsSource todo,
            Clock clock
    ) {
        if (work == null || flow == null || todo == null || clock == null) {
            throw new IllegalArgumentException("Analytics dependencies are required");
        }
        this.work = work;
        this.flow = flow;
        this.todo = todo;
        this.clock = clock;
    }

    public OperationsDashboard snapshot(
            AnalyticsActor actor,
            LocalDate from,
            LocalDate to
    ) {
        if (actor == null) throw new IllegalArgumentException("Analytics actor is required");
        var range = AnalyticsRange.resolve(from, to, clock);
        var workSection = work.load(actor, range);
        var flowSection = flow.load(actor, range);
        var todoSection = todo.load(actor, range);
        requireExactDates(range, workSection.available(),
                workSection.daily().stream().map(OperationsDashboard.WorkDaily::date).toList(),
                "Work");
        requireExactDates(range, flowSection.available(),
                flowSection.daily().stream().map(OperationsDashboard.FlowDaily::date).toList(),
                "Flow");
        return new OperationsDashboard(clock.instant(), range,
                workSection, flowSection, todoSection);
    }

    private static void requireExactDates(
            AnalyticsRange range,
            boolean available,
            List<LocalDate> actual,
            String source
    ) {
        var expected = available ? range.dates() : List.<LocalDate>of();
        if (!expected.equals(actual)) {
            throw new IllegalStateException(source
                    + " analytics must return every requested date exactly once");
        }
    }
}
