package com.unique.examine.web.analytics;

import com.unique.examine.analytics.domain.AnalyticsRange;
import com.unique.examine.analytics.domain.OperationsDashboard;

import java.util.List;

public final class AnalyticsApiModels {
    private AnalyticsApiModels() {
    }

    public record Snapshot(
            String generatedAt,
            Range range,
            Work work,
            Flow flow,
            Todo todo
    ) {
        static Snapshot from(OperationsDashboard value) {
            return new Snapshot(value.generatedAt().toString(),
                    Range.from(value.range()), Work.from(value.work()),
                    Flow.from(value.flow()), Todo.from(value.todo()));
        }
    }

    public record Range(String from, String to, int days) {
        static Range from(AnalyticsRange value) {
            return new Range(value.from().toString(), value.to().toString(), value.days());
        }
    }

    public record Work(
            boolean available,
            String unavailableReason,
            long openCount,
            String openRoute,
            long overdueOpenCount,
            String overdueOpenRoute,
            long dueInRangeOpenCount,
            String dueInRangeOpenRoute,
            long completedInRangeCount,
            String completedInRangeRoute,
            List<WorkDaily> daily,
            List<AssigneeOpen> topAssignees,
            String route
    ) {
        static Work from(OperationsDashboard.WorkSection value) {
            return new Work(value.available(), value.unavailableReason(),
                    value.openCount(), value.openRoute(),
                    value.overdueOpenCount(), value.overdueOpenRoute(),
                    value.dueInRangeOpenCount(), value.dueInRangeOpenRoute(),
                    value.completedInRangeCount(), value.completedInRangeRoute(),
                    value.daily().stream().map(WorkDaily::from).toList(),
                    value.topAssignees().stream().map(AssigneeOpen::from).toList(),
                    value.route());
        }
    }

    public record WorkDaily(String date, long created, long completed) {
        static WorkDaily from(OperationsDashboard.WorkDaily value) {
            return new WorkDaily(value.date().toString(), value.created(), value.completed());
        }
    }

    public record AssigneeOpen(String memberId, long openCount, String route) {
        static AssigneeOpen from(OperationsDashboard.AssigneeOpen value) {
            return new AssigneeOpen(Long.toString(value.memberId()),
                    value.openCount(), value.route());
        }
    }

    public record Flow(
            boolean available,
            String unavailableReason,
            long pendingCount,
            String pendingRoute,
            long terminalInRangeCount,
            String terminalInRangeRoute,
            long approvedInRangeCount,
            long rejectedInRangeCount,
            long withdrawnInRangeCount,
            long terminatedInRangeCount,
            List<FlowDaily> daily,
            List<TerminalCount> terminalBreakdown,
            String route
    ) {
        static Flow from(OperationsDashboard.FlowSection value) {
            return new Flow(value.available(), value.unavailableReason(),
                    value.pendingCount(), value.pendingRoute(),
                    value.terminalInRangeCount(), value.terminalInRangeRoute(),
                    value.approvedInRangeCount(), value.rejectedInRangeCount(),
                    value.withdrawnInRangeCount(), value.terminatedInRangeCount(),
                    value.daily().stream().map(FlowDaily::from).toList(),
                    value.terminalBreakdown().stream().map(TerminalCount::from).toList(),
                    value.route());
        }
    }

    public record FlowDaily(String date, long started, long terminal) {
        static FlowDaily from(OperationsDashboard.FlowDaily value) {
            return new FlowDaily(value.date().toString(), value.started(), value.terminal());
        }
    }

    public record TerminalCount(String status, long count, String route) {
        static TerminalCount from(OperationsDashboard.TerminalCount value) {
            return new TerminalCount(value.status(), value.count(), value.route());
        }
    }

    public record Todo(
            boolean available,
            String unavailableReason,
            long openCount,
            long taskCount,
            long approvalCount,
            long todayCount,
            long overdueCount,
            String route
    ) {
        static Todo from(OperationsDashboard.TodoSection value) {
            return new Todo(value.available(), value.unavailableReason(),
                    value.openCount(), value.taskCount(), value.approvalCount(),
                    value.todayCount(), value.overdueCount(), value.route());
        }
    }
}
