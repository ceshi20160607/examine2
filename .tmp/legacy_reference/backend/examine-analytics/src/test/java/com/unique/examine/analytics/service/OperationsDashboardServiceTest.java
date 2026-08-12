package com.unique.examine.analytics.service;

import com.unique.examine.analytics.domain.AnalyticsActor;
import com.unique.examine.analytics.domain.OperationsDashboard;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationsDashboardServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-01T08:00:00Z"), ZoneOffset.UTC);
    private static final AnalyticsActor ACTOR = new AnalyticsActor(
            1, 10, 20, 30, Set.of("system.runtime.access"));

    @Test
    void composesAvailableAndUnavailableOwnerSectionsWithoutConflatingZero() {
        var service = new OperationsDashboardService(
                (actor, range) -> new OperationsDashboard.WorkSection(
                        true, null, 3, route("tasks?status=OPEN"),
                        1, route("tasks?status=OPEN&time=OVERDUE"),
                        2, route("tasks?status=OPEN&time=RANGE"),
                        4, route("tasks?status=COMPLETED"),
                        range.dates().stream().map(date ->
                                new OperationsDashboard.WorkDaily(date, 1, 2)).toList(),
                        List.of(new OperationsDashboard.AssigneeOpen(
                                30, 3, route("tasks?assigneeMemberId=30"))),
                        route("tasks")),
                (actor, range) -> OperationsDashboard.FlowSection.unavailable(
                        "PERMISSION_DENIED", route("flows")),
                (actor, range) -> new OperationsDashboard.TodoSection(
                        true, null, 5, 3, 2, 1, 1, route("todos")),
                CLOCK);

        var result = service.snapshot(ACTOR,
                LocalDate.parse("2026-07-30"),
                LocalDate.parse("2026-08-02"));

        assertThat(result.generatedAt()).isEqualTo(CLOCK.instant());
        assertThat(result.range().days()).isEqualTo(3);
        assertThat(result.work().daily()).hasSize(3);
        assertThat(result.flow().available()).isFalse();
        assertThat(result.flow().unavailableReason()).isEqualTo("PERMISSION_DENIED");
        assertThat(result.todo().openCount()).isEqualTo(5);
    }

    @Test
    void rejectsOwnerSeriesThatOmitsOrReordersRequestedDays() {
        var service = new OperationsDashboardService(
                (actor, range) -> new OperationsDashboard.WorkSection(
                        true, null, 0, route("tasks"), 0, route("tasks"),
                        0, route("tasks"), 0, route("tasks"),
                        List.of(new OperationsDashboard.WorkDaily(
                                range.to().minusDays(1), 0, 0)),
                        List.of(), route("tasks")),
                (actor, range) -> OperationsDashboard.FlowSection.unavailable(
                        "PERMISSION_DENIED", route("flows")),
                (actor, range) -> OperationsDashboard.TodoSection.unavailable(
                        "UNAVAILABLE", route("todos")),
                CLOCK);

        assertThatThrownBy(() -> service.snapshot(ACTOR,
                LocalDate.parse("2026-07-30"),
                LocalDate.parse("2026-08-02")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("every requested date");
    }

    private static String route(String suffix) {
        return "/systems/10/" + suffix;
    }
}
