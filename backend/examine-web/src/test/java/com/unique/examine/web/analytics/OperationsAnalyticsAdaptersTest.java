package com.unique.examine.web.analytics;

import com.unique.examine.analytics.domain.AnalyticsActor;
import com.unique.examine.analytics.domain.AnalyticsRange;
import com.unique.examine.flow.api.FlowPermissions;
import com.unique.examine.flow.metrics.FlowMetricsSnapshot;
import com.unique.examine.todo.adapter.memory.InMemoryTodoRepository;
import com.unique.examine.todo.service.TodoService;
import com.unique.examine.work.adapter.memory.InMemoryWorkTaskRepository;
import com.unique.examine.work.domain.WorkActor;
import com.unique.examine.work.service.WorkMetricsFacade;
import com.unique.examine.work.service.WorkTaskService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OperationsAnalyticsAdaptersTest {
    private static final Instant NOW = Instant.parse("2026-08-01T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final AnalyticsRange RANGE = new AnalyticsRange(
            LocalDate.parse("2026-07-31"), LocalDate.parse("2026-08-02"), 2);

    @Test
    void mapsWorkOwnerMetricsAndKeepsPermissionAbsenceExplicit() {
        var repository = new InMemoryWorkTaskRepository();
        var tasks = new WorkTaskService(repository,
                (systemId, tenantId, memberId) -> true, CLOCK);
        tasks.create(new WorkActor(10, 20, 30,
                        Set.of(WorkTaskService.CREATE)),
                "Analytics task", 30);
        var adapter = new WorkOperationsAnalyticsAdapter(
                new WorkMetricsFacade(repository, CLOCK));

        var available = adapter.load(actor(WorkTaskService.ACCESS), RANGE);
        assertThat(available.available()).isTrue();
        assertThat(available.openCount()).isOne();
        assertThat(available.daily()).extracting(value -> value.date().toString())
                .containsExactly("2026-07-31", "2026-08-01");
        assertThat(available.daily()).extracting(value -> value.created())
                .containsExactly(0L, 1L);
        assertThat(available.openRoute()).startsWith("/systems/10/tasks?");

        var unavailable = adapter.load(actor(), RANGE);
        assertThat(unavailable.available()).isFalse();
        assertThat(unavailable.unavailableReason()).isEqualTo("WORK_TASK_FORBIDDEN");
        assertThat(unavailable.daily()).isEmpty();
    }

    @Test
    void mapsFlowOwnerBreakdownAndTodoPersonalCounts() {
        var flow = new FlowOperationsAnalyticsAdapter((session, from, to) ->
                new FlowMetricsSnapshot(from, to,
                        new FlowMetricsSnapshot.Metric(2,
                                "/systems/10/flows?taskStatus=PENDING"),
                        new FlowMetricsSnapshot.Metric(1,
                                "/systems/10/flows?from=2026-07-31&to=2026-08-02"),
                        List.of(
                                new FlowMetricsSnapshot.Daily(from, 1, 0,
                                        "/systems/10/flows", "/systems/10/flows"),
                                new FlowMetricsSnapshot.Daily(from.plusDays(1), 0, 1,
                                        "/systems/10/flows", "/systems/10/flows")),
                        List.of(
                                terminal(FlowMetricsSnapshot.TerminalStatus.APPROVED, 1),
                                terminal(FlowMetricsSnapshot.TerminalStatus.REJECTED, 0),
                                terminal(FlowMetricsSnapshot.TerminalStatus.WITHDRAWN, 0),
                                terminal(FlowMetricsSnapshot.TerminalStatus.TERMINATED, 0))));
        var flowSection = flow.load(actor(FlowPermissions.INSTANCE_READ), RANGE);
        assertThat(flowSection.pendingCount()).isEqualTo(2);
        assertThat(flowSection.terminalInRangeCount()).isOne();
        assertThat(flowSection.approvedInRangeCount()).isOne();
        assertThat(flowSection.terminalBreakdown()).extracting(
                        value -> value.status())
                .containsExactly("APPROVED", "REJECTED", "WITHDRAWN", "TERMINATED");
        assertThat(flowSection.terminalBreakdown()).allSatisfy(value -> {
            assertThat(value.route()).contains("?status=" + value.status());
            assertThat(value.route()).doesNotContain("instanceStatus=");
        });

        var todoService = new TodoService(new InMemoryTodoRepository(),
                List.of(), List.of(), CLOCK);
        var todo = new TodoOperationsAnalyticsAdapter(todoService).load(actor(), RANGE);
        assertThat(todo.available()).isTrue();
        assertThat(todo.openCount()).isZero();
        assertThat(todo.route()).isEqualTo("/systems/10/todos");
    }

    private static FlowMetricsSnapshot.TerminalBreakdown terminal(
            FlowMetricsSnapshot.TerminalStatus status,
            long count
    ) {
        return new FlowMetricsSnapshot.TerminalBreakdown(status, count,
                "/systems/10/flows?status=" + status.name());
    }

    private static AnalyticsActor actor(String... permissions) {
        return new AnalyticsActor(1, 10, 20, 30, Set.of(permissions));
    }
}
