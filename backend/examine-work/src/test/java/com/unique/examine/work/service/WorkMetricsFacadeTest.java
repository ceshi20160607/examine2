package com.unique.examine.work.service;

import com.unique.examine.work.adapter.memory.InMemoryWorkTaskRepository;
import com.unique.examine.work.domain.WorkActor;
import com.unique.examine.work.domain.WorkDomainException;
import com.unique.examine.work.domain.WorkMetricsSnapshot;
import com.unique.examine.work.domain.WorkTask;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkMetricsFacadeTest {
    private static final LocalDate FROM = LocalDate.parse("2026-08-01");
    private static final LocalDate TO = LocalDate.parse("2026-08-08");
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    @Test
    void exactUtcBoundariesZeroFillVisibilityRoutesAndTenantIsolation() {
        var tasks = new InMemoryWorkTaskRepository();
        var from = Instant.parse("2026-08-01T00:00:00Z");
        var to = Instant.parse("2026-08-08T00:00:00Z");
        save(tasks, 1, 20, 100, 101, WorkTask.Status.OPEN,
                from, from, from);
        save(tasks, 2, 20, 900, 901, WorkTask.Status.OPEN,
                from, from, from);
        save(tasks, 3, 20, 100, 103, WorkTask.Status.OPEN,
                from.minusSeconds(1), from.minusSeconds(1), to);
        save(tasks, 4, 20, 104, 100, WorkTask.Status.OPEN,
                Instant.parse("2026-08-03T01:00:00Z"),
                Instant.parse("2026-08-03T01:00:00Z"), NOW);
        save(tasks, 5, 20, 100, 105, WorkTask.Status.COMPLETED,
                Instant.parse("2026-08-02T01:00:00Z"), from, null);
        save(tasks, 6, 20, 100, 106, WorkTask.Status.COMPLETED,
                Instant.parse("2026-08-04T01:00:00Z"), to, null);
        save(tasks, 7, 20, 100, 107, WorkTask.Status.COMPLETED,
                Instant.parse("2026-08-07T01:00:00Z"),
                to.minusNanos(1), null);
        save(tasks, 8, 20, 100, 108, WorkTask.Status.COMPLETED,
                to, to, null);
        save(tasks, 9, 21, 100, 101, WorkTask.Status.OPEN,
                from, from, from);
        var facade = new WorkMetricsFacade(
                tasks, Clock.fixed(NOW, ZoneOffset.UTC));

        var snapshot = facade.snapshot(
                actor(20, 100, WorkTaskService.ACCESS), FROM, TO);

        assertThat(snapshot.total()).isEqualTo(7);
        assertThat(snapshot.completed()).isEqualTo(4);
        assertThat(snapshot.open().count()).isEqualTo(3);
        assertThat(snapshot.overdueOpen().count()).isEqualTo(2);
        assertThat(snapshot.dueInRangeOpen().count()).isEqualTo(1);
        assertThat(snapshot.completedInRange().count()).isEqualTo(2);
        assertThat(snapshot.daily()).hasSize(7);
        assertThat(snapshot.daily())
                .extracting(value -> value.date().toString())
                .containsExactly(
                        "2026-08-01", "2026-08-02", "2026-08-03",
                        "2026-08-04", "2026-08-05", "2026-08-06",
                        "2026-08-07");
        assertThat(snapshot.daily())
                .extracting(value -> value.createdCount())
                .containsExactly(1L, 1L, 1L, 1L, 0L, 0L, 1L);
        assertThat(snapshot.daily())
                .extracting(value -> value.completedCount())
                .containsExactly(1L, 0L, 0L, 0L, 0L, 0L, 1L);
        assertThat(snapshot.topAssignees())
                .extracting(value -> value.assigneeMemberId())
                .containsExactly(100L, 101L, 103L);
        assertThat(snapshot.open().route())
                .isEqualTo("/systems/10/tasks?role=PARTICIPATING&status=OPEN");
        assertThat(snapshot.overdueOpen().route())
                .contains("status=OPEN&dueBefore=2026-08-10T00:00:00Z");
        assertThat(snapshot.dueInRangeOpen().route())
                .contains("dueFrom=2026-08-01T00:00:00Z")
                .contains("dueBefore=2026-08-08T00:00:00Z");
        assertThat(snapshot.completedInRange().route())
                .contains("updatedFrom=2026-08-01T00:00:00Z")
                .contains("updatedBefore=2026-08-08T00:00:00Z");
        assertThat(snapshot.daily().get(4).createdRoute())
                .contains("createdFrom=2026-08-05T00:00:00Z")
                .contains("createdBefore=2026-08-06T00:00:00Z");

        var tenant21 = facade.snapshot(
                actor(21, 100, WorkTaskService.ACCESS), FROM, TO);
        assertThat(tenant21.open().count()).isEqualTo(1);
        assertThat(tenant21.total()).isEqualTo(1);
        assertThat(tenant21.topAssignees()).singleElement()
                .extracting(value -> value.assigneeMemberId())
                .isEqualTo(101L);

        var manager = facade.snapshot(
                actor(20, 100,
                        WorkTaskService.ACCESS, WorkTaskService.MANAGE),
                FROM, TO);
        assertThat(manager.open().count()).isEqualTo(4);
        assertThat(manager.total()).isEqualTo(8);
        assertThat(manager.completed()).isEqualTo(4);
        assertThat(manager.open().route()).contains("role=ALL");
        assertThat(manager.open().route()).doesNotContain("projectId=");
    }

    @Test
    void rangeAndAccessFailClosedAndTopTwentyOrderIsStable() {
        var tasks = new InMemoryWorkTaskRepository();
        var created = Instant.parse("2026-08-02T00:00:00Z");
        long id = 1;
        for (long assignee = 200; assignee < 222; assignee++) {
            save(tasks, id++, 20, 900, assignee, WorkTask.Status.OPEN,
                    created, created, null);
        }
        save(tasks, id, 20, 900, 205, WorkTask.Status.OPEN,
                created, created, null);
        var facade = new WorkMetricsFacade(
                tasks, Clock.fixed(NOW, ZoneOffset.UTC));
        var manager = actor(
                20, 100,
                WorkTaskService.ACCESS, WorkTaskService.MANAGE);

        var snapshot = facade.snapshot(manager, FROM, TO);
        assertThat(snapshot.topAssignees()).hasSize(20);
        assertThat(snapshot.topAssignees().getFirst())
                .extracting(
                        value -> value.assigneeMemberId(),
                        value -> value.openCount())
                .containsExactly(205L, 2L);
        assertThat(snapshot.topAssignees().subList(1, 5))
                .extracting(value -> value.assigneeMemberId())
                .containsExactly(200L, 201L, 202L, 203L);
        assertThat(snapshot.topAssignees().getLast().assigneeMemberId())
                .isEqualTo(219L);

        assertThatThrownBy(() -> facade.snapshot(
                actor(20, 100), FROM, TO))
                .isInstanceOfSatisfying(
                        WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_TASK_FORBIDDEN"));
        assertThatThrownBy(() -> facade.snapshot(
                manager, FROM, FROM))
                .isInstanceOfSatisfying(
                        WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_METRICS_RANGE_INVALID"));
        assertThatThrownBy(() -> facade.snapshot(
                manager, FROM, FROM.plusDays(32)))
                .isInstanceOfSatisfying(
                        WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_METRICS_RANGE_INVALID"));
    }

    @Test
    void projectOverloadFiltersAllTimeProgressAndEveryDrillRoute() {
        var tasks = new InMemoryWorkTaskRepository();
        var created = Instant.parse("2026-08-02T00:00:00Z");
        save(tasks, 1, 20, 100, 101, WorkTask.Status.OPEN,
                created, created, NOW, 77L);
        save(tasks, 2, 20, 100, 101, WorkTask.Status.COMPLETED,
                created, created, null, 77L);
        save(tasks, 3, 20, 100, 101, WorkTask.Status.OPEN,
                created, created, NOW, 88L);
        var facade = new WorkMetricsFacade(
                tasks, Clock.fixed(NOW, ZoneOffset.UTC));

        var snapshot = facade.snapshot(
                actor(20, 100,
                        WorkTaskService.ACCESS, WorkTaskService.MANAGE),
                77L, FROM, TO);

        assertThat(snapshot.total()).isEqualTo(2);
        assertThat(snapshot.open().count()).isEqualTo(1);
        assertThat(snapshot.completed()).isEqualTo(1);
        assertThat(java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(
                                snapshot.open().route(),
                                snapshot.overdueOpen().route(),
                                snapshot.dueInRangeOpen().route(),
                                snapshot.completedInRange().route()),
                        java.util.stream.Stream.concat(
                                snapshot.daily().stream().flatMap(day ->
                                        java.util.stream.Stream.of(
                                                day.createdRoute(),
                                                day.completedRoute())),
                                snapshot.topAssignees().stream()
                                        .map(WorkMetricsSnapshot.AssigneeOpen::route))))
                .allMatch(route -> route.contains("projectId=77"));
    }

    private static void save(
            InMemoryWorkTaskRepository tasks,
            long id,
            long tenantId,
            long creator,
            long assignee,
            WorkTask.Status status,
            Instant createdAt,
            Instant updatedAt,
            Instant dueAt
    ) {
        save(tasks, id, tenantId, creator, assignee, status,
                createdAt, updatedAt, dueAt, null);
    }

    private static void save(
            InMemoryWorkTaskRepository tasks,
            long id,
            long tenantId,
            long creator,
            long assignee,
            WorkTask.Status status,
            Instant createdAt,
            Instant updatedAt,
            Instant dueAt,
            Long projectId
    ) {
        tasks.save(new WorkTask(
                id, 10, tenantId, creator, assignee,
                "Task " + id, status, createdAt, updatedAt, 1,
                projectId, null, dueAt, null));
    }

    private static WorkActor actor(
            long tenantId,
            long memberId,
            String... permissions
    ) {
        return new WorkActor(
                10, tenantId, memberId, Set.of(permissions));
    }
}
