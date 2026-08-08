package com.unique.examine.core.ai;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiWorkQueryFacadeTest {

    @Test
    void requestsAndResultsKeepBoundedImmutableSnapshots() {
        var permissions = new HashSet<>(Set.of("work.task.access"));
        var request = new AiWorkQueryFacade.TaskRequest(
                10, 20, 100, permissions, " release ", null,
                null, null, AiWorkQueryFacade.TaskStatus.OPEN,
                AiWorkQueryFacade.TaskRole.PARTICIPATING, 20);
        permissions.clear();
        assertThat(request.keyword()).isEqualTo("release");
        assertThat(request.effectivePermissions()).containsExactly("work.task.access");

        var values = new ArrayList<>(List.of(task("1")));
        var result = new AiWorkQueryFacade.TaskResult(1, values);
        values.clear();
        assertThat(result.tasks()).containsExactly(task("1"));
    }

    @Test
    void rejectsOverLimitAndInvalidReportWindows() {
        assertThatThrownBy(() -> taskRequest(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1..50");
        assertThatThrownBy(() -> taskRequest(51))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1..50");
        assertThatThrownBy(() -> new AiWorkQueryFacade.DailyReportRequest(
                10, 20, 100, Set.of(), AiWorkQueryFacade.ReportScope.SELF,
                null, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2),
                AiWorkQueryFacade.ReportStatus.ALL, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date window");
        assertThatThrownBy(() -> new AiWorkQueryFacade.DailyReportRequest(
                10, 20, 100, Set.of(), AiWorkQueryFacade.ReportScope.ALL,
                "not-an-id", null, null, AiWorkQueryFacade.ReportStatus.ALL, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authorMemberId");
    }

    private static AiWorkQueryFacade.TaskRequest taskRequest(int limit) {
        return new AiWorkQueryFacade.TaskRequest(
                10, 20, 100, Set.of(), "", null, null, null,
                AiWorkQueryFacade.TaskStatus.ALL,
                AiWorkQueryFacade.TaskRole.PARTICIPATING, limit);
    }

    private static AiWorkQueryFacade.Task task(String id) {
        var now = Instant.parse("2026-08-04T08:00:00Z");
        return new AiWorkQueryFacade.Task(
                id, 1, "Task", null, "OPEN", null,
                "100", "101", null, null, now, now);
    }
}
