package com.unique.examine.analytics.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record OperationsDashboard(
        Instant generatedAt,
        AnalyticsRange range,
        WorkSection work,
        FlowSection flow,
        TodoSection todo
) {
    public OperationsDashboard {
        if (generatedAt == null || range == null || work == null
                || flow == null || todo == null) {
            throw new IllegalArgumentException("Operations dashboard is incomplete");
        }
    }

    public record WorkSection(
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
        public WorkSection {
            requireAvailability(available, unavailableReason, openCount,
                    overdueOpenCount, dueInRangeOpenCount, completedInRangeCount);
            daily = immutable(daily);
            topAssignees = immutable(topAssignees);
            route = safeRoute(route);
            openRoute = safeRoute(openRoute);
            overdueOpenRoute = safeRoute(overdueOpenRoute);
            dueInRangeOpenRoute = safeRoute(dueInRangeOpenRoute);
            completedInRangeRoute = safeRoute(completedInRangeRoute);
            if (!available && (!daily.isEmpty() || !topAssignees.isEmpty())) {
                throw new IllegalArgumentException("Unavailable Work analytics cannot expose data");
            }
        }

        public static WorkSection unavailable(String reason, String route) {
            return new WorkSection(false, reason, 0, route, 0, route,
                    0, route, 0, route, List.of(), List.of(), route);
        }
    }

    public record FlowSection(
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
        public FlowSection {
            requireAvailability(available, unavailableReason, pendingCount,
                    terminalInRangeCount, approvedInRangeCount, rejectedInRangeCount,
                    withdrawnInRangeCount, terminatedInRangeCount);
            daily = immutable(daily);
            terminalBreakdown = immutable(terminalBreakdown);
            route = safeRoute(route);
            pendingRoute = safeRoute(pendingRoute);
            terminalInRangeRoute = safeRoute(terminalInRangeRoute);
            if (!available && (!daily.isEmpty() || !terminalBreakdown.isEmpty())) {
                throw new IllegalArgumentException("Unavailable Flow analytics cannot expose data");
            }
        }

        public static FlowSection unavailable(String reason, String route) {
            return new FlowSection(false, reason, 0, route, 0, route, 0, 0, 0, 0,
                    List.of(), List.of(), route);
        }
    }

    public record TodoSection(
            boolean available,
            String unavailableReason,
            long openCount,
            long taskCount,
            long approvalCount,
            long todayCount,
            long overdueCount,
            String route
    ) {
        public TodoSection {
            requireAvailability(available, unavailableReason, openCount,
                    taskCount, approvalCount, todayCount, overdueCount);
            route = safeRoute(route);
        }

        public static TodoSection unavailable(String reason, String route) {
            return new TodoSection(false, reason, 0, 0, 0, 0, 0, route);
        }
    }

    public record WorkDaily(LocalDate date, long created, long completed) {
        public WorkDaily {
            if (date == null || created < 0 || completed < 0) invalidCount();
        }
    }

    public record FlowDaily(LocalDate date, long started, long terminal) {
        public FlowDaily {
            if (date == null || started < 0 || terminal < 0) invalidCount();
        }
    }

    public record AssigneeOpen(long memberId, long openCount, String route) {
        public AssigneeOpen {
            if (memberId <= 0 || openCount <= 0) invalidCount();
            route = safeRoute(route);
        }
    }

    public record TerminalCount(String status, long count, String route) {
        public TerminalCount {
            if (status == null || status.isBlank() || count < 0) invalidCount();
            status = status.trim();
            route = safeRoute(route);
        }
    }

    private static void requireAvailability(boolean available, String reason, long... counts) {
        for (var count : counts) if (count < 0) invalidCount();
        if (available && reason != null || !available
                && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("Analytics availability facts are inconsistent");
        }
        if (!available) {
            for (var count : counts) if (count != 0) {
                throw new IllegalArgumentException("Unavailable analytics cannot expose counts");
            }
        }
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static String safeRoute(String value) {
        if (value == null || value.isBlank() || value.length() > 500
                || !value.startsWith("/systems/")) {
            throw new IllegalArgumentException("Analytics route must be a safe system route");
        }
        return value.trim();
    }

    private static void invalidCount() {
        throw new IllegalArgumentException("Analytics count facts are invalid");
    }
}
