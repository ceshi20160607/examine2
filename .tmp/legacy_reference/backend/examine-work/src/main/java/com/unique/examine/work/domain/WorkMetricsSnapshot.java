package com.unique.examine.work.domain;

import java.time.LocalDate;
import java.util.List;

/** Public Work-owned operations metrics with native drill routes. */
public record WorkMetricsSnapshot(
        LocalDate fromInclusive,
        LocalDate toExclusive,
        long total,
        long completed,
        Metric open,
        Metric overdueOpen,
        Metric dueInRangeOpen,
        Metric completedInRange,
        List<Daily> daily,
        List<AssigneeOpen> topAssignees
) {
    public WorkMetricsSnapshot {
        if (fromInclusive == null || toExclusive == null
                || !toExclusive.isAfter(fromInclusive)
                || total < 0 || completed < 0
                || open == null || overdueOpen == null
                || dueInRangeOpen == null || completedInRange == null
                || daily == null || topAssignees == null
                || topAssignees.size() > 20) {
            throw new IllegalArgumentException(
                    "Work metrics snapshot is invalid");
        }
        if (total != open.count() + completed) {
            throw new IllegalArgumentException(
                    "Work metrics progress is invalid");
        }
        daily = List.copyOf(daily);
        topAssignees = List.copyOf(topAssignees);
    }

    public record Metric(long count, String route) {
        public Metric {
            route = normalizeRoute(route);
            if (count < 0) {
                throw new IllegalArgumentException(
                        "Work metric count cannot be negative");
            }
        }
    }

    public record Daily(
            LocalDate date,
            long createdCount,
            long completedCount,
            String createdRoute,
            String completedRoute
    ) {
        public Daily {
            createdRoute = normalizeRoute(createdRoute);
            completedRoute = normalizeRoute(completedRoute);
            if (date == null || createdCount < 0 || completedCount < 0) {
                throw new IllegalArgumentException(
                        "Work daily metric is invalid");
            }
        }
    }

    public record AssigneeOpen(
            long assigneeMemberId,
            long openCount,
            String route
    ) {
        public AssigneeOpen {
            route = normalizeRoute(route);
            if (assigneeMemberId <= 0 || openCount <= 0) {
                throw new IllegalArgumentException(
                        "Work assignee metric is invalid");
            }
        }
    }

    private static String normalizeRoute(String value) {
        if (value == null || value.isBlank() || value.length() > 1_000) {
            throw new IllegalArgumentException(
                    "Work metric route is invalid");
        }
        return value.trim();
    }
}
