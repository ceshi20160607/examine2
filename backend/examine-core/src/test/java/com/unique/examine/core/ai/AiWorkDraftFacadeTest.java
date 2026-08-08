package com.unique.examine.core.ai;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiWorkDraftFacadeTest {

    @Test
    void prepareBindsImmutableIdentityAndExactlyOneMatchingDraft() {
        var permissions = new HashSet<>(Set.of(
                "ai.agent.use", "work.task.create"));
        var task = new AiWorkDraftFacade.TaskDraft(
                " Task ", " Description ", "101", "31",
                Instant.parse("2026-08-06T08:00:00Z"));
        var request = new AiWorkDraftFacade.PrepareRequest(
                "proposal-1", "session-1", "turn-1",
                7, 11, 21, 17, 3, permissions,
                AiWorkDraftFacade.Operation.WORK_TASK_DRAFT,
                task, null, "51", "61", 0, "prompt-v1",
                "request-1", "trace-1");
        permissions.clear();

        assertThat(request.effectivePermissions())
                .containsExactlyInAnyOrder(
                        "ai.agent.use", "work.task.create");
        assertThat(request.task().title()).isEqualTo("Task");
        assertThat(request.task().description()).isEqualTo("Description");
        assertThatThrownBy(() -> new AiWorkDraftFacade.PrepareRequest(
                "proposal-1", "session-1", "turn-1",
                7, 11, 21, 17, 3, Set.of(),
                AiWorkDraftFacade.Operation.WORK_DAILY_REPORT_DRAFT,
                task, null, "51", "61", 0, "prompt-v1",
                "request-1", "trace-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("matching Work draft");
    }

    @Test
    void reportAndReadbacksAreBoundedToDraftState() {
        var report = new AiWorkDraftFacade.DailyReportDraft(
                LocalDate.of(2026, 8, 4), " Done ", " Next ", null);
        assertThat(report.completedWork()).isEqualTo("Done");
        assertThatThrownBy(() -> new AiWorkDraftFacade.TaskReadback(
                "1", 1, "Task", null, "COMPLETED", "101", null,
                null, Instant.EPOCH, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
        assertThatThrownBy(() -> new AiWorkDraftFacade.DailyReportReadback(
                "1", 1, "17", report.workDate(), "Done", "Next", null,
                "SUBMITTED", Instant.EPOCH, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
    }
}
