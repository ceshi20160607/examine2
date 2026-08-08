package com.unique.examine.module.datasource.statistics.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public enum StatisticsGrain {
    DAY {
        @Override
        public boolean aligned(LocalDate value) {
            return true;
        }

        @Override
        public long buckets(LocalDate start, LocalDate end) {
            return ChronoUnit.DAYS.between(start, end);
        }

        @Override
        public LocalDate next(LocalDate value) {
            return value.plusDays(1);
        }
    },
    WEEK {
        @Override
        public boolean aligned(LocalDate value) {
            return value.getDayOfWeek() == DayOfWeek.MONDAY;
        }

        @Override
        public long buckets(LocalDate start, LocalDate end) {
            return ChronoUnit.WEEKS.between(start, end);
        }

        @Override
        public LocalDate next(LocalDate value) {
            return value.plusWeeks(1);
        }
    },
    MONTH {
        @Override
        public boolean aligned(LocalDate value) {
            return value.getDayOfMonth() == 1;
        }

        @Override
        public long buckets(LocalDate start, LocalDate end) {
            return ChronoUnit.MONTHS.between(start, end);
        }

        @Override
        public LocalDate next(LocalDate value) {
            return value.plusMonths(1);
        }
    };

    public abstract boolean aligned(LocalDate value);

    public abstract long buckets(LocalDate start, LocalDate end);

    public abstract LocalDate next(LocalDate value);
}
