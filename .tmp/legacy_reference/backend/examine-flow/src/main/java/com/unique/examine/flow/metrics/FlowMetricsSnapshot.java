package com.unique.examine.flow.metrics;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Public Flow-owned operations metrics with native drill routes. */
public record FlowMetricsSnapshot(
        LocalDate fromInclusive,
        LocalDate toExclusive,
        Metric pending,
        Metric terminalInRange,
        List<Daily> daily,
        List<TerminalBreakdown> terminalBreakdown
) {
    public enum TerminalStatus { APPROVED, REJECTED, WITHDRAWN, TERMINATED }

    public FlowMetricsSnapshot {
        var days = fromInclusive == null || toExclusive == null
                ? 0 : ChronoUnit.DAYS.between(fromInclusive, toExclusive);
        if (days < 1 || days > 31 || pending == null || terminalInRange == null
                || daily == null || terminalBreakdown == null
                || daily.size() != days || terminalBreakdown.size() != 4) {
            throw new IllegalArgumentException("Flow metrics snapshot is invalid");
        }
        daily = List.copyOf(daily);
        terminalBreakdown = List.copyOf(terminalBreakdown);
        for (int index = 0; index < daily.size(); index++) {
            if (!daily.get(index).date().equals(fromInclusive.plusDays(index))) {
                throw new IllegalArgumentException("Flow daily metrics must fill every range day");
            }
        }
        var statuses = List.of(TerminalStatus.values());
        for (int index = 0; index < statuses.size(); index++) {
            if (terminalBreakdown.get(index).status() != statuses.get(index)) {
                throw new IllegalArgumentException("Flow terminal breakdown order is invalid");
            }
        }
        var breakdownTotal = terminalBreakdown.stream()
                .mapToLong(TerminalBreakdown::count).sum();
        if (breakdownTotal != terminalInRange.count()) {
            throw new IllegalArgumentException("Flow terminal metrics do not balance");
        }
    }

    public record Metric(long count, String route) {
        public Metric {
            if (count < 0) throw new IllegalArgumentException("Flow metric count cannot be negative");
            route = FlowMetricsSnapshot.route(route);
        }
    }

    public record Daily(
            LocalDate date,
            long startedCount,
            long terminalCount,
            String startedRoute,
            String terminalRoute
    ) {
        public Daily {
            if (date == null || startedCount < 0 || terminalCount < 0) {
                throw new IllegalArgumentException("Flow daily metric is invalid");
            }
            startedRoute = route(startedRoute);
            terminalRoute = route(terminalRoute);
        }
    }

    public record TerminalBreakdown(TerminalStatus status, long count, String route) {
        public TerminalBreakdown {
            if (status == null || count < 0) {
                throw new IllegalArgumentException("Flow terminal breakdown is invalid");
            }
            route = FlowMetricsSnapshot.route(route);
        }
    }

    private static String route(String value) {
        if (value == null || value.isBlank() || value.length() > 1_000) {
            throw new IllegalArgumentException("Flow metric route is invalid");
        }
        return value.trim();
    }
}
