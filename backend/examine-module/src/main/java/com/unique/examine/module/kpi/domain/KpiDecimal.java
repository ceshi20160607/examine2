package com.unique.examine.module.kpi.domain;

import java.math.BigDecimal;

public final class KpiDecimal {
    public static final int MAX_LENGTH = 1_000;

    private KpiDecimal() {
    }

    public static String canonical(BigDecimal value) {
        if (value == null) {
            return null;
        }
        var normalized = value.signum() == 0
                ? "0" : value.stripTrailingZeros().toPlainString();
        if (normalized.length() > MAX_LENGTH) {
            throw invalid("KPI decimal exceeds the supported bound");
        }
        return normalized;
    }

    public static String require(String value) {
        if (value == null) {
            throw invalid("KPI decimal is required");
        }
        final BigDecimal parsed;
        try {
            parsed = new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw invalid("KPI decimal is invalid");
        }
        var normalized = canonical(parsed);
        if (!normalized.equals(value)) {
            throw invalid("KPI decimal must use canonical form");
        }
        return value;
    }

    public static String requireNonNegative(String value) {
        require(value);
        if (new BigDecimal(value).signum() < 0) {
            throw invalid("KPI decimal cannot be negative");
        }
        return value;
    }

    public static BigDecimal value(String value) {
        return new BigDecimal(require(value));
    }

    private static KpiException invalid(String message) {
        return new KpiException("KPI_DECIMAL_INVALID", message);
    }
}
