package com.unique.examine.web.analytics;

import com.unique.examine.analytics.domain.AnalyticsActor;
import com.unique.examine.analytics.domain.AnalyticsRange;
import com.unique.examine.analytics.domain.OperationsDashboard;
import com.unique.examine.analytics.port.WorkAnalyticsSource;
import com.unique.examine.work.domain.WorkActor;
import com.unique.examine.work.service.WorkMetricsFacade;
import com.unique.examine.work.service.WorkTaskService;
import org.springframework.stereotype.Component;

@Component
public final class WorkOperationsAnalyticsAdapter implements WorkAnalyticsSource {
    private final WorkMetricsFacade metrics;

    public WorkOperationsAnalyticsAdapter(WorkMetricsFacade metrics) {
        if (metrics == null) throw new IllegalArgumentException("Work metrics are required");
        this.metrics = metrics;
    }

    @Override
    public OperationsDashboard.WorkSection load(AnalyticsActor actor, AnalyticsRange range) {
        var overviewRoute = "/systems/" + actor.systemId() + "/tasks";
        if (!actor.has(WorkTaskService.ACCESS)) {
            return OperationsDashboard.WorkSection.unavailable(
                    "WORK_TASK_FORBIDDEN", overviewRoute);
        }
        var snapshot = metrics.snapshot(new WorkActor(actor.systemId(), actor.tenantId(),
                actor.memberId(), actor.permissions()), range.from(), range.to());
        if (!snapshot.fromInclusive().equals(range.from())
                || !snapshot.toExclusive().equals(range.to())) {
            throw new IllegalStateException("Work metrics returned another range");
        }
        return new OperationsDashboard.WorkSection(true, null,
                snapshot.open().count(), snapshot.open().route(),
                snapshot.overdueOpen().count(), snapshot.overdueOpen().route(),
                snapshot.dueInRangeOpen().count(), snapshot.dueInRangeOpen().route(),
                snapshot.completedInRange().count(), snapshot.completedInRange().route(),
                snapshot.daily().stream().map(value ->
                        new OperationsDashboard.WorkDaily(value.date(),
                                value.createdCount(), value.completedCount())).toList(),
                snapshot.topAssignees().stream().map(value ->
                        new OperationsDashboard.AssigneeOpen(value.assigneeMemberId(),
                                value.openCount(), value.route())).toList(),
                overviewRoute);
    }
}
