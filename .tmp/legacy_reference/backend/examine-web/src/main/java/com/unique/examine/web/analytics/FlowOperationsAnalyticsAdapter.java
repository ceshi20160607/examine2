package com.unique.examine.web.analytics;

import com.unique.examine.analytics.domain.AnalyticsActor;
import com.unique.examine.analytics.domain.AnalyticsRange;
import com.unique.examine.analytics.domain.OperationsDashboard;
import com.unique.examine.analytics.port.FlowAnalyticsSource;
import com.unique.examine.flow.api.FlowPermissions;
import com.unique.examine.flow.metrics.FlowMetricsFacade;
import com.unique.examine.flow.metrics.FlowMetricsSnapshot;
import com.unique.examine.flow.security.FlowSession;
import org.springframework.stereotype.Component;

import java.util.EnumMap;

@Component
public final class FlowOperationsAnalyticsAdapter implements FlowAnalyticsSource {
    private final FlowMetricsFacade metrics;

    public FlowOperationsAnalyticsAdapter(FlowMetricsFacade metrics) {
        if (metrics == null) throw new IllegalArgumentException("Flow metrics are required");
        this.metrics = metrics;
    }

    @Override
    public OperationsDashboard.FlowSection load(AnalyticsActor actor, AnalyticsRange range) {
        var overviewRoute = "/systems/" + actor.systemId() + "/flows";
        if (!actor.has(FlowPermissions.INSTANCE_READ)) {
            return OperationsDashboard.FlowSection.unavailable(
                    "PERMISSION_DENIED", overviewRoute);
        }
        var snapshot = metrics.snapshot(new FlowSession(actor.accountId(), actor.systemId(),
                        actor.tenantId(), actor.memberId(), actor.permissions()),
                range.from(), range.to());
        if (!snapshot.fromInclusive().equals(range.from())
                || !snapshot.toExclusive().equals(range.to())) {
            throw new IllegalStateException("Flow metrics returned another range");
        }
        var counts = new EnumMap<FlowMetricsSnapshot.TerminalStatus, Long>(
                FlowMetricsSnapshot.TerminalStatus.class);
        snapshot.terminalBreakdown().forEach(value -> counts.put(value.status(), value.count()));
        return new OperationsDashboard.FlowSection(true, null,
                snapshot.pending().count(), snapshot.pending().route(),
                snapshot.terminalInRange().count(), snapshot.terminalInRange().route(),
                count(counts, FlowMetricsSnapshot.TerminalStatus.APPROVED),
                count(counts, FlowMetricsSnapshot.TerminalStatus.REJECTED),
                count(counts, FlowMetricsSnapshot.TerminalStatus.WITHDRAWN),
                count(counts, FlowMetricsSnapshot.TerminalStatus.TERMINATED),
                snapshot.daily().stream().map(value ->
                        new OperationsDashboard.FlowDaily(value.date(),
                                value.startedCount(), value.terminalCount())).toList(),
                snapshot.terminalBreakdown().stream().map(value ->
                        new OperationsDashboard.TerminalCount(value.status().name(),
                                value.count(), value.route())).toList(),
                overviewRoute);
    }

    private static long count(
            EnumMap<FlowMetricsSnapshot.TerminalStatus, Long> counts,
            FlowMetricsSnapshot.TerminalStatus status
    ) {
        return counts.getOrDefault(status, 0L);
    }
}
