package com.unique.examine.module.report.scheduling;

import com.unique.examine.module.report.domain.ReportException;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportScheduleTimingTest {
    @Test
    void advancesGapByItsExactDuration() {
        var cadence = new ReportScheduleTiming.Cadence(
                ReportScheduleTiming.Kind.DAILY,
                LocalTime.of(2, 30), List.of());

        var next = cadence.nextAfter(
                Instant.parse("2026-03-08T05:00:00Z"),
                ZoneId.of("America/New_York"));

        assertThat(next).isEqualTo(Instant.parse("2026-03-08T07:30:00Z"));
        assertThat(next.atZone(ZoneId.of("America/New_York"))
                .toLocalTime()).isEqualTo(LocalTime.of(3, 30));
    }

    @Test
    void overlapUsesEarlierOffsetAndNeverFiresTheRepeatedTimeTwice() {
        var cadence = new ReportScheduleTiming.Cadence(
                ReportScheduleTiming.Kind.DAILY,
                LocalTime.of(1, 30), List.of());
        var zone = ZoneId.of("America/New_York");

        assertThat(cadence.nextAfter(
                Instant.parse("2026-11-01T04:00:00Z"), zone))
                .isEqualTo(Instant.parse("2026-11-01T05:30:00Z"));
        assertThat(cadence.nextAfter(
                Instant.parse("2026-11-01T05:45:00Z"), zone))
                .isEqualTo(Instant.parse("2026-11-02T06:30:00Z"));
    }

    @Test
    void weeklyCadenceUsesTheNextConfiguredWeekday() {
        var cadence = new ReportScheduleTiming.Cadence(
                ReportScheduleTiming.Kind.WEEKLY,
                LocalTime.of(9, 0),
                List.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));

        assertThat(cadence.nextAfter(
                Instant.parse("2026-08-04T00:00:00Z"),
                ReportScheduleTiming.zone("Asia/Shanghai")))
                .isEqualTo(Instant.parse("2026-08-07T01:00:00Z"));
    }

    @Test
    void acceptsUtcAndIanaRegionsButRejectsAliasesAndOffsets() {
        assertThat(ReportScheduleTiming.zone("UTC").getId()).isEqualTo("UTC");
        assertThat(ReportScheduleTiming.zone("Asia/Shanghai").getId())
                .isEqualTo("Asia/Shanghai");
        for (var invalid : List.of("GMT", "Z", "+08:00")) {
            assertThatThrownBy(() -> ReportScheduleTiming.zone(invalid))
                    .isInstanceOf(ReportException.class);
        }
    }
}
