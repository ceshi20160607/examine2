package com.unique.examine.analytics.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyticsRangeTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-01T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void defaultsToLatestSevenUtcDaysIncludingToday() {
        var range = AnalyticsRange.resolve(null, null, CLOCK);

        assertThat(range.from()).isEqualTo(LocalDate.parse("2026-07-26"));
        assertThat(range.to()).isEqualTo(LocalDate.parse("2026-08-02"));
        assertThat(range.days()).isEqualTo(7);
        assertThat(range.dates()).containsExactly(
                LocalDate.parse("2026-07-26"), LocalDate.parse("2026-07-27"),
                LocalDate.parse("2026-07-28"), LocalDate.parse("2026-07-29"),
                LocalDate.parse("2026-07-30"), LocalDate.parse("2026-07-31"),
                LocalDate.parse("2026-08-01"));
    }

    @Test
    void rejectsPartialEmptyAndOversizedRanges() {
        assertThatThrownBy(() -> AnalyticsRange.resolve(
                LocalDate.parse("2026-08-01"), null, CLOCK))
                .isInstanceOf(AnalyticsException.class)
                .hasMessageContaining("supplied together");
        assertThatThrownBy(() -> AnalyticsRange.resolve(
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-01"), CLOCK))
                .isInstanceOf(AnalyticsException.class);
        assertThatThrownBy(() -> AnalyticsRange.resolve(
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-08-02"), CLOCK))
                .isInstanceOf(AnalyticsException.class)
                .hasMessageContaining("1 to 31");
    }
}
