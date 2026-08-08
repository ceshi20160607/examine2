package com.unique.examine.module.kpi.domain;

import java.time.LocalDate;

public record KpiPeriod(
        KpiPeriodType type,
        LocalDate startInclusive,
        LocalDate endExclusive
) {
    public KpiPeriod {
        if (type == null || startInclusive == null || endExclusive == null
                || !startInclusive.isBefore(endExclusive)
                || startInclusive.getDayOfMonth() != 1
                || endExclusive.getDayOfMonth() != 1
                || !endExclusive.equals(startInclusive.plusMonths(
                type.monthCount()))
                || type == KpiPeriodType.QUARTER
                && (startInclusive.getMonthValue() - 1) % 3 != 0
                || type == KpiPeriodType.YEAR
                && startInclusive.getMonthValue() != 1) {
            throw new KpiException("KPI_PERIOD_INVALID",
                    "KPI period is not aligned to its configured period type");
        }
    }

    public static KpiPeriod starting(KpiPeriodType type, LocalDate start) {
        if (type == null || start == null) {
            throw new KpiException("KPI_PERIOD_INVALID",
                    "KPI period type and start are required");
        }
        return new KpiPeriod(type, start, start.plusMonths(type.monthCount()));
    }

    public int monthCount() {
        return type.monthCount();
    }
}
