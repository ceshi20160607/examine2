package com.unique.examine.module.datasource.statistics.domain;

import java.math.BigDecimal;

public final class CanonicalDecimal {
    public static final int MAX_LENGTH = 1_000;

    private CanonicalDecimal() {
    }

    public static String from(BigDecimal value) {
        if (value == null) {
            return null;
        }
        var normalized = value.signum() == 0
                ? "0" : value.stripTrailingZeros().toPlainString();
        if (normalized.length() > MAX_LENGTH) {
            throw new StatisticsException(
                    "STATISTICS_RESULT_INVALID",
                    "Canonical decimal exceeds the supported bound");
        }
        return normalized;
    }

    public static String require(String value) {
        if (value == null) {
            return null;
        }
        final BigDecimal parsed;
        try {
            parsed = new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw invalid();
        }
        var normalized = from(parsed);
        if (!normalized.equals(value)) {
            throw invalid();
        }
        return value;
    }

    private static StatisticsException invalid() {
        return new StatisticsException(
                "STATISTICS_RESULT_INVALID",
                "Statistics decimal is not canonical");
    }
}
