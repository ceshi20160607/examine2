package com.unique.examine.analytics.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;

public record AnalyticsRange(LocalDate from, LocalDate to, int days) {
    public static final int MAX_DAYS = 31;

    public AnalyticsRange {
        if (from == null || to == null || !from.isBefore(to)) {
            throw invalid("Analytics range must have from before to");
        }
        var exact = Math.toIntExact(ChronoUnit.DAYS.between(from, to));
        if (exact < 1 || exact > MAX_DAYS || days != exact) {
            throw invalid("Analytics range must contain 1 to 31 whole days");
        }
    }

    public static AnalyticsRange resolve(LocalDate from, LocalDate to, Clock clock) {
        if (clock == null) throw new IllegalArgumentException("Analytics clock is required");
        if (from == null && to == null) {
            var today = LocalDate.now(clock);
            return new AnalyticsRange(today.minusDays(6), today.plusDays(1), 7);
        }
        if (from == null || to == null) {
            throw invalid("Analytics from and to must be supplied together");
        }
        return new AnalyticsRange(from, to,
                Math.toIntExact(ChronoUnit.DAYS.between(from, to)));
    }

    public Instant fromInstant() {
        return from.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    public Instant toInstant() {
        return to.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    public List<LocalDate> dates() {
        return IntStream.range(0, days).mapToObj(from::plusDays).toList();
    }

    private static AnalyticsException invalid(String message) {
        return new AnalyticsException("ANALYTICS_RANGE_INVALID", message);
    }
}
