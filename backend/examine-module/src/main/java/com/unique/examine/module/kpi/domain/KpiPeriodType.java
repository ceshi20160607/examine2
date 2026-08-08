package com.unique.examine.module.kpi.domain;

public enum KpiPeriodType {
    MONTH(1),
    QUARTER(3),
    YEAR(12);

    private final int monthCount;

    KpiPeriodType(int monthCount) {
        this.monthCount = monthCount;
    }

    public int monthCount() {
        return monthCount;
    }
}
