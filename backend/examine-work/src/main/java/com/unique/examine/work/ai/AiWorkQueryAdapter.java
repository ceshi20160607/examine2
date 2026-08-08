package com.unique.examine.work.ai;

import com.unique.examine.core.ai.AiWorkQueryFacade;
import com.unique.examine.work.domain.WorkActor;
import com.unique.examine.work.domain.WorkDailyReport;
import com.unique.examine.work.domain.WorkDailyReportQuery;
import com.unique.examine.work.domain.WorkTask;
import com.unique.examine.work.domain.WorkTaskQuery;
import com.unique.examine.work.service.WorkDailyReportService;
import com.unique.examine.work.service.WorkMetricsFacade;
import com.unique.examine.work.service.WorkProjectService;
import com.unique.examine.work.service.WorkTaskService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** Work-owned translation from strict AI query contracts to native services. */
@Component
public class AiWorkQueryAdapter implements AiWorkQueryFacade {
    private final WorkTaskService tasks;
    private final WorkDailyReportService reports;
    private final WorkProjectService projects;
    private final WorkMetricsFacade metrics;

    public AiWorkQueryAdapter(
            WorkTaskService tasks,
            WorkDailyReportService reports,
            WorkProjectService projects,
            WorkMetricsFacade metrics
    ) {
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.reports = Objects.requireNonNull(reports, "reports");
        this.projects = Objects.requireNonNull(projects, "projects");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResult taskQuery(TaskRequest request) {
        Objects.requireNonNull(request, "request");
        var page = tasks.page(
                actor(request.systemId(), request.tenantId(), request.memberId(),
                        request.effectivePermissions()),
                new WorkTaskQuery(
                        request.keyword(),
                        WorkTaskQuery.StatusFilter.valueOf(request.status().name()),
                        WorkTaskQuery.RoleFilter.valueOf(request.role().name()),
                        1,
                        request.limit(),
                        request.projectId(),
                        request.dueFrom(),
                        request.dueTo()));
        return new TaskResult(
                page.total(),
                page.items().stream().limit(request.limit())
                        .map(AiWorkQueryAdapter::task)
                        .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public DailyReportResult dailyReportQuery(DailyReportRequest request) {
        Objects.requireNonNull(request, "request");
        var page = reports.page(
                actor(request.systemId(), request.tenantId(), request.memberId(),
                        request.effectivePermissions()),
                WorkDailyReportQuery.Scope.valueOf(request.scope().name()),
                request.authorMemberId() == null
                        ? null : Long.parseLong(request.authorMemberId()),
                request.dateFrom(),
                request.dateTo(),
                WorkDailyReportQuery.StatusFilter.valueOf(request.status().name()),
                1,
                request.limit());
        return new DailyReportResult(
                page.total(),
                page.items().stream().limit(request.limit())
                        .map(AiWorkQueryAdapter::report)
                        .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectMetricsResult projectMetrics(
            ProjectMetricsRequest request
    ) {
        Objects.requireNonNull(request, "request");
        var actor = actor(
                request.systemId(), request.tenantId(), request.memberId(),
                request.effectivePermissions());
        var projectId = Long.parseLong(request.projectId());
        var project = projects.get(actor, projectId);
        var snapshot = metrics.snapshot(
                actor, projectId,
                request.fromInclusive(), request.toExclusive());
        var visibility = actor.has(WorkTaskService.MANAGE)
                ? ProjectVisibility.ALL
                : ProjectVisibility.PARTICIPATING;
        return new ProjectMetricsResult(
                Long.toString(project.id()), project.title(),
                project.status().name(), project.updatedAt(),
                snapshot.fromInclusive(), snapshot.toExclusive(), visibility,
                snapshot.total(), snapshot.open().count(),
                snapshot.completed(),
                metric(snapshot.overdueOpen()),
                metric(snapshot.dueInRangeOpen()),
                metric(snapshot.completedInRange()),
                snapshot.daily().stream()
                        .map(value -> new DailyMetric(
                                value.date(), value.createdCount(),
                                value.completedCount(), value.createdRoute(),
                                value.completedRoute()))
                        .toList(),
                snapshot.topAssignees().stream()
                        .map(value -> new AssigneeOpen(
                                Long.toString(value.assigneeMemberId()),
                                value.openCount(), value.route()))
                        .toList());
    }

    private static WorkActor actor(
            long systemId,
            long tenantId,
            long memberId,
            java.util.Set<String> permissions
    ) {
        return new WorkActor(systemId, tenantId, memberId, permissions);
    }

    private static AiWorkQueryFacade.Task task(WorkTask value) {
        return new AiWorkQueryFacade.Task(
                Long.toString(value.id()), value.version(), value.title(),
                value.description(), value.status().name(),
                value.projectId() == null ? null : Long.toString(value.projectId()),
                Long.toString(value.creatorMemberId()),
                Long.toString(value.assigneeMemberId()),
                value.dueAt(), value.reminderAt(),
                value.createdAt(), value.updatedAt());
    }

    private static AiWorkQueryFacade.DailyReport report(
            WorkDailyReport value
    ) {
        return new AiWorkQueryFacade.DailyReport(
                Long.toString(value.id()), value.version(),
                Long.toString(value.authorMemberId()), value.workDate(),
                value.completedWork(), value.plannedWork(), value.blockers(),
                value.status().name(), value.createdAt(), value.updatedAt(),
                value.submittedAt());
    }

    private static AiWorkQueryFacade.Metric metric(
            com.unique.examine.work.domain.WorkMetricsSnapshot.Metric value
    ) {
        return new AiWorkQueryFacade.Metric(value.count(), value.route());
    }
}
