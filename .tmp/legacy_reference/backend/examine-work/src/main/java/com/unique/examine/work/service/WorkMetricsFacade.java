package com.unique.examine.work.service;

import com.unique.examine.work.domain.WorkActor;
import com.unique.examine.work.domain.WorkDomainException;
import com.unique.examine.work.domain.WorkMetricsSnapshot;
import com.unique.examine.work.domain.WorkTaskMetricFacts;
import com.unique.examine.work.port.WorkTaskRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/** Public Work-owned metrics API; consumers never query Work persistence. */
@Transactional(readOnly = true)
public class WorkMetricsFacade {
    public static final int MAX_RANGE_DAYS = 31;

    private final WorkTaskRepository tasks;
    private final Clock clock;

    public WorkMetricsFacade(WorkTaskRepository tasks, Clock clock) {
        if (tasks == null || clock == null) {
            throw new IllegalArgumentException(
                    "Work metrics dependencies are required");
        }
        this.tasks = tasks;
        this.clock = clock;
    }

    public WorkMetricsSnapshot snapshot(
            WorkActor actor,
            LocalDate fromInclusive,
            LocalDate toExclusive
    ) {
        return snapshot(actor, null, fromInclusive, toExclusive);
    }

    public WorkMetricsSnapshot snapshot(
            WorkActor actor,
            Long projectId,
            LocalDate fromInclusive,
            LocalDate toExclusive
    ) {
        if (actor == null || !actor.has(WorkTaskService.ACCESS)) {
            throw new WorkDomainException(
                    "WORK_TASK_FORBIDDEN",
                    "Work task access is required for metrics");
        }
        if (projectId != null && projectId <= 0) {
            throw new WorkDomainException(
                    "WORK_METRICS_PROJECT_INVALID",
                    "Work metrics project id must be positive");
        }
        var days = validateRange(fromInclusive, toExclusive);
        var from = utcStart(fromInclusive);
        var to = utcStart(toExclusive);
        var now = Instant.now(clock);
        var tenantWide = actor.has(WorkTaskService.MANAGE);
        var facts = tasks.metrics(
                actor.systemId(), actor.tenantId(), actor.memberId(),
                tenantWide, projectId, from, to, now);
        var role = tenantWide ? "ALL" : "PARTICIPATING";
        var base = "/systems/" + actor.systemId()
                + "/tasks?role=" + role
                + (projectId == null ? "" : "&projectId=" + projectId);
        var created = countsByDate(facts.createdByDay());
        var completed = countsByDate(facts.completedByDay());
        var daily = java.util.stream.LongStream.range(0, days)
                .mapToObj(offset -> fromInclusive.plusDays(offset))
                .map(date -> daily(
                        date, base, created.getOrDefault(date, 0L),
                        completed.getOrDefault(date, 0L)))
                .toList();
        var assignees = facts.topAssignees().stream()
                .sorted(Comparator
                        .comparingLong(
                                WorkTaskMetricFacts.AssigneeOpen::openCount)
                        .reversed()
                        .thenComparingLong(
                                WorkTaskMetricFacts.AssigneeOpen
                                        ::assigneeMemberId))
                .limit(20)
                .map(value -> new WorkMetricsSnapshot.AssigneeOpen(
                        value.assigneeMemberId(), value.openCount(),
                        base + "&status=OPEN&assigneeMemberId="
                                + value.assigneeMemberId()))
                .toList();
        return new WorkMetricsSnapshot(
                fromInclusive, toExclusive,
                facts.total(), facts.completed(),
                metric(facts.open(), base + "&status=OPEN"),
                metric(facts.overdueOpen(), base
                        + "&status=OPEN&dueBefore=" + now),
                metric(facts.dueInRangeOpen(), base
                        + "&status=OPEN&dueFrom=" + from
                        + "&dueBefore=" + to),
                metric(facts.completedInRange(), base
                        + "&status=COMPLETED&updatedFrom=" + from
                        + "&updatedBefore=" + to),
                daily, assignees);
    }

    private static WorkMetricsSnapshot.Daily daily(
            LocalDate date,
            String base,
            long created,
            long completed
    ) {
        var from = utcStart(date);
        var before = utcStart(date.plusDays(1));
        return new WorkMetricsSnapshot.Daily(
                date, created, completed,
                base + "&status=ALL&createdFrom=" + from
                        + "&createdBefore=" + before,
                base + "&status=COMPLETED&updatedFrom=" + from
                        + "&updatedBefore=" + before);
    }

    private static WorkMetricsSnapshot.Metric metric(
            long count,
            String route
    ) {
        return new WorkMetricsSnapshot.Metric(count, route);
    }

    private static long validateRange(
            LocalDate fromInclusive,
            LocalDate toExclusive
    ) {
        if (fromInclusive == null || toExclusive == null) {
            throw invalidRange();
        }
        var days = ChronoUnit.DAYS.between(fromInclusive, toExclusive);
        if (days < 1 || days > MAX_RANGE_DAYS) {
            throw invalidRange();
        }
        return days;
    }

    private static Map<LocalDate, Long> countsByDate(
            java.util.List<WorkTaskMetricFacts.DayCount> values
    ) {
        return values.stream().collect(Collectors.toUnmodifiableMap(
                WorkTaskMetricFacts.DayCount::date,
                WorkTaskMetricFacts.DayCount::count,
                Long::sum));
    }

    private static Instant utcStart(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static WorkDomainException invalidRange() {
        return new WorkDomainException(
                "WORK_METRICS_RANGE_INVALID",
                "Work metrics range must contain 1 to 31 UTC days");
    }
}
