package com.unique.examine.work.adapter.memory;

import com.unique.examine.work.domain.WorkDomainException;
import com.unique.examine.work.domain.WorkTask;
import com.unique.examine.work.domain.WorkTaskPage;
import com.unique.examine.work.domain.WorkTaskQuery;
import com.unique.examine.work.domain.WorkTaskMetricFacts;
import com.unique.examine.work.port.WorkTaskRepository;
import com.unique.examine.work.port.WorkProjectRepository;
import com.unique.examine.work.domain.WorkProjectMember;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryWorkTaskRepository implements WorkTaskRepository {
    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentHashMap<Long, WorkTask> tasks = new ConcurrentHashMap<>();
    private final WorkProjectRepository projects;

    public InMemoryWorkTaskRepository() {
        this(null);
    }

    public InMemoryWorkTaskRepository(WorkProjectRepository projects) {
        this.projects = projects;
    }

    @Override
    public long nextId() {
        return sequence.incrementAndGet();
    }

    @Override
    public Optional<WorkTask> findById(long systemId, long tenantId, long id) {
        return Optional.ofNullable(tasks.get(id))
                .filter(task -> task.systemId() == systemId && task.tenantId() == tenantId);
    }

    @Override
    public List<WorkTask> findAll(long systemId, long tenantId) {
        return tasks.values().stream()
                .filter(task -> task.systemId() == systemId && task.tenantId() == tenantId)
                .sorted(order())
                .toList();
    }

    @Override
    public List<WorkTask> findParticipating(long systemId, long tenantId, long memberId) {
        return tasks.values().stream()
                .filter(task -> task.systemId() == systemId && task.tenantId() == tenantId)
                .filter(task -> task.creatorMemberId() == memberId || task.assigneeMemberId() == memberId)
                .sorted(order())
                .toList();
    }

    @Override
    public WorkTaskPage findPage(
            long systemId,
            long tenantId,
            long memberId,
            WorkTaskQuery query
    ) {
        var filtered = tasks.values().stream()
                .filter(task -> task.systemId() == systemId && task.tenantId() == tenantId)
                .filter(task -> matchesStatus(task, query.status()))
                .filter(task -> matchesRole(task, memberId, query.role()))
                .filter(task -> query.projectId() == null
                        || query.projectId().equals(task.projectId()))
                .filter(task -> query.dueFrom() == null
                        || task.dueAt() != null
                        && !task.dueAt().isBefore(query.dueFrom()))
                .filter(task -> query.dueTo() == null
                        || task.dueAt() != null
                        && !task.dueAt().isAfter(query.dueTo()))
                .filter(task -> query.dueBefore() == null
                        || task.dueAt() != null
                        && task.dueAt().isBefore(query.dueBefore()))
                .filter(task -> query.createdFrom() == null
                        || !task.createdAt().isBefore(query.createdFrom()))
                .filter(task -> query.createdBefore() == null
                        || task.createdAt().isBefore(query.createdBefore()))
                .filter(task -> query.updatedFrom() == null
                        || !task.updatedAt().isBefore(query.updatedFrom()))
                .filter(task -> query.updatedBefore() == null
                        || task.updatedAt().isBefore(query.updatedBefore()))
                .filter(task -> query.assigneeMemberId() == null
                        || task.assigneeMemberId()
                        == query.assigneeMemberId())
                .filter(task -> query.reminderFrom() == null
                        || task.reminderAt() != null
                        && !task.reminderAt().isBefore(query.reminderFrom()))
                .filter(task -> query.reminderTo() == null
                        || task.reminderAt() != null
                        && !task.reminderAt().isAfter(query.reminderTo()))
                .filter(task -> query.keyword().isEmpty()
                        || task.title().toLowerCase(java.util.Locale.ROOT)
                        .contains(query.keyword().toLowerCase(java.util.Locale.ROOT)))
                .sorted(order())
                .toList();
        var from = Math.min(query.offset(), filtered.size());
        var to = Math.min(from + query.size(), filtered.size());
        return new WorkTaskPage(
                filtered.subList((int) from, (int) to),
                query.page(),
                query.size(),
                filtered.size());
    }

    @Override
    public WorkTaskMetricFacts metrics(
            long systemId,
            long tenantId,
            long memberId,
            boolean tenantWide,
            Instant fromInclusive,
            Instant toExclusive,
            Instant now
    ) {
        return metrics(
                systemId, tenantId, memberId, tenantWide, null,
                fromInclusive, toExclusive, now);
    }

    @Override
    public WorkTaskMetricFacts metrics(
            long systemId,
            long tenantId,
            long memberId,
            boolean tenantWide,
            Long projectId,
            Instant fromInclusive,
            Instant toExclusive,
            Instant now
    ) {
        if (projectId != null && projectId <= 0) {
            throw new IllegalArgumentException("projectId is invalid");
        }
        Objects.requireNonNull(fromInclusive, "fromInclusive");
        Objects.requireNonNull(toExclusive, "toExclusive");
        Objects.requireNonNull(now, "now");
        var visible = tasks.values().stream()
                .filter(task -> task.systemId() == systemId
                        && task.tenantId() == tenantId)
                .filter(task -> projectId == null
                        || projectId.equals(task.projectId()))
                .filter(task -> tenantWide
                        || task.creatorMemberId() == memberId
                        || task.assigneeMemberId() == memberId
                        || activeProjectMember(task, memberId))
                .toList();
        var total = visible.size();
        var open = visible.stream()
                .filter(task -> task.status() == WorkTask.Status.OPEN)
                .count();
        var completed = visible.stream()
                .filter(task -> task.status()
                        == WorkTask.Status.COMPLETED)
                .count();
        var overdue = visible.stream()
                .filter(task -> task.status() == WorkTask.Status.OPEN)
                .filter(task -> task.dueAt() != null
                        && task.dueAt().isBefore(now))
                .count();
        var dueInRange = visible.stream()
                .filter(task -> task.status() == WorkTask.Status.OPEN)
                .filter(task -> inRange(
                        task.dueAt(), fromInclusive, toExclusive))
                .count();
        var completedInRange = visible.stream()
                .filter(task -> task.status() == WorkTask.Status.COMPLETED)
                .filter(task -> inRange(
                        task.updatedAt(), fromInclusive, toExclusive))
                .count();
        var createdByDay = dayCounts(
                visible.stream()
                        .map(WorkTask::createdAt)
                        .filter(value -> inRange(
                                value, fromInclusive, toExclusive))
                        .toList());
        var completedByDay = dayCounts(
                visible.stream()
                        .filter(task -> task.status()
                                == WorkTask.Status.COMPLETED)
                        .map(WorkTask::updatedAt)
                        .filter(value -> inRange(
                                value, fromInclusive, toExclusive))
                        .toList());
        var topAssignees = visible.stream()
                .filter(task -> task.status() == WorkTask.Status.OPEN)
                .collect(Collectors.groupingBy(
                        WorkTask::assigneeMemberId,
                        Collectors.counting()))
                .entrySet().stream()
                .sorted(Comparator
                        .<java.util.Map.Entry<Long, Long>>comparingLong(
                                java.util.Map.Entry::getValue)
                        .reversed()
                        .thenComparingLong(java.util.Map.Entry::getKey))
                .limit(20)
                .map(entry -> new WorkTaskMetricFacts.AssigneeOpen(
                        entry.getKey(), entry.getValue()))
                .toList();
        return new WorkTaskMetricFacts(
                total, completed, open, overdue, dueInRange, completedInRange,
                createdByDay, completedByDay, topAssignees);
    }

    @Override
    public WorkTask save(WorkTask task) {
        tasks.compute(task.id(), (id, current) -> {
            if (current != null && task.version() != current.version() + 1) {
                throw new WorkDomainException("WORK_TASK_VERSION_CONFLICT", "Task version is stale");
            }
            if (current == null && task.version() != 1) {
                throw new WorkDomainException("WORK_TASK_VERSION_CONFLICT", "New task must start at version 1");
            }
            return task;
        });
        return task;
    }

    private static Comparator<WorkTask> order() {
        return Comparator.comparingInt(
                        (WorkTask task) -> task.status() == WorkTask.Status.OPEN ? 0 : 1)
                .thenComparing(Comparator.comparing(WorkTask::updatedAt).reversed())
                .thenComparing(Comparator.comparingLong(WorkTask::id).reversed());
    }

    private static boolean matchesStatus(
            WorkTask task,
            WorkTaskQuery.StatusFilter status
    ) {
        return status == WorkTaskQuery.StatusFilter.ALL
                || task.status().name().equals(status.name());
    }

    private boolean matchesRole(
            WorkTask task,
            long memberId,
            WorkTaskQuery.RoleFilter role
    ) {
        return switch (role) {
            case PARTICIPATING ->
                    task.creatorMemberId() == memberId
                            || task.assigneeMemberId() == memberId
                            || activeProjectMember(task, memberId);
            case CREATED_BY_ME -> task.creatorMemberId() == memberId;
            case ASSIGNED_TO_ME -> task.assigneeMemberId() == memberId;
            case ALL -> true;
        };
    }

    private boolean activeProjectMember(WorkTask task, long memberId) {
        return projects != null && task.projectId() != null
                && projects.findMember(
                        task.systemId(), task.tenantId(),
                        task.projectId(), memberId)
                .filter(value -> value.status()
                        == WorkProjectMember.Status.ACTIVE)
                .isPresent();
    }

    private static boolean inRange(
            Instant value,
            Instant fromInclusive,
            Instant toExclusive
    ) {
        return value != null && !value.isBefore(fromInclusive)
                && value.isBefore(toExclusive);
    }

    private static List<WorkTaskMetricFacts.DayCount> dayCounts(
            List<Instant> values
    ) {
        return values.stream()
                .collect(Collectors.groupingBy(
                        value -> value.atZone(ZoneOffset.UTC).toLocalDate(),
                        Collectors.counting()))
                .entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> new WorkTaskMetricFacts.DayCount(
                        entry.getKey(), entry.getValue()))
                .toList();
    }
}
