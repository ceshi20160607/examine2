package com.unique.examine.module.report.scheduling;

import com.unique.examine.module.report.domain.ReportException;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;

public final class ReportScheduleTiming {
    private ReportScheduleTiming() {
    }

    public enum Kind { DAILY, WEEKLY }

    public record Cadence(
            Kind kind,
            LocalTime localTime,
            List<DayOfWeek> daysOfWeek
    ) {
        public Cadence {
            if (kind == null || localTime == null
                    || localTime.getSecond() != 0 || localTime.getNano() != 0
                    || daysOfWeek == null) {
                throw invalid("Schedule cadence is incomplete");
            }
            daysOfWeek = daysOfWeek.stream().distinct()
                    .sorted(Comparator.comparingInt(DayOfWeek::getValue))
                    .toList();
            if (kind == Kind.DAILY && !daysOfWeek.isEmpty()
                    || kind == Kind.WEEKLY && daysOfWeek.isEmpty()) {
                throw invalid("Daily cadence has no weekdays and weekly cadence requires weekdays");
            }
        }

        /** Returns one deterministic occurrence strictly after {@code after}. */
        public Instant nextAfter(Instant after, ZoneId zone) {
            if (after == null || zone == null) {
                throw invalid("Schedule preview boundary is incomplete");
            }
            var firstDate = after.atZone(zone).toLocalDate();
            for (var offset = 0; offset <= 14; offset++) {
                var date = firstDate.plusDays(offset);
                if (!matches(date)) {
                    continue;
                }
                var candidate = resolve(date.atTime(localTime), zone);
                if (candidate.toInstant().isAfter(after)) {
                    return candidate.toInstant();
                }
            }
            throw new IllegalStateException("Unable to calculate bounded next schedule occurrence");
        }

        private boolean matches(LocalDate date) {
            return kind == Kind.DAILY || daysOfWeek.contains(date.getDayOfWeek());
        }
    }

    /** Gaps shift forward by the exact gap; overlaps choose the earlier offset once. */
    static ZonedDateTime resolve(LocalDateTime local, ZoneId zone) {
        var offsets = zone.getRules().getValidOffsets(local);
        if (offsets.size() == 1) {
            return ZonedDateTime.ofLocal(local, zone, offsets.getFirst());
        }
        if (offsets.size() == 2) {
            return ZonedDateTime.ofLocal(local, zone, offsets.getFirst());
        }
        var transition = zone.getRules().getTransition(local);
        if (transition == null || !transition.isGap()) {
            throw invalid("Schedule local time cannot be resolved");
        }
        return ZonedDateTime.ofLocal(
                local.plus(transition.getDuration()), zone,
                transition.getOffsetAfter());
    }

    public static ZoneId zone(String value) {
        if (value == null || value.isBlank() || value.length() > 100) {
            throw invalid("Schedule time zone is invalid");
        }
        try {
            var zone = ZoneId.of(value.strip());
            if (!"UTC".equals(zone.getId()) && !zone.getId().contains("/")) {
                throw invalid("Schedule requires an IANA time zone");
            }
            return zone;
        } catch (ReportException known) {
            throw known;
        } catch (RuntimeException malformed) {
            throw invalid("Schedule time zone is invalid");
        }
    }

    private static ReportException invalid(String message) {
        return new ReportException("REPORT_SCHEDULE_INVALID", message);
    }
}
