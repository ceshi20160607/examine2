package com.unique.examine.module.kpi.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class KpiAttainment {
    private static final int SCALE = 12;

    private KpiAttainment() {
    }

    public static Result evaluate(
            KpiAttainmentDirection direction,
            String targetValue,
            String actualValue,
            String warningThreshold
    ) {
        if (direction == null) {
            throw new KpiException("KPI_ATTAINMENT_INVALID",
                    "KPI attainment direction is required");
        }
        var target = new BigDecimal(KpiDecimal.requireNonNegative(targetValue));
        var actual = new BigDecimal(KpiDecimal.require(actualValue));
        var threshold = threshold(warningThreshold);
        var achieved = direction == KpiAttainmentDirection.AT_LEAST
                ? actual.compareTo(target) >= 0
                : actual.compareTo(target) <= 0;
        var ratio = ratio(direction, target, actual);
        var status = achieved ? KpiWarningStatus.ACHIEVED
                : ratio.compareTo(threshold) >= 0
                ? KpiWarningStatus.AT_RISK : KpiWarningStatus.MISSED;
        return new Result(KpiDecimal.canonical(ratio), status);
    }

    public static String requireThreshold(String value) {
        return KpiDecimal.canonical(threshold(value));
    }

    private static BigDecimal threshold(String value) {
        var threshold = new BigDecimal(KpiDecimal.requireNonNegative(value));
        if (threshold.signum() <= 0
                || threshold.compareTo(BigDecimal.ONE) > 0) {
            throw new KpiException("KPI_THRESHOLD_INVALID",
                    "KPI warning threshold must be greater than 0 and at most 1");
        }
        return threshold;
    }

    private static BigDecimal ratio(
            KpiAttainmentDirection direction,
            BigDecimal target,
            BigDecimal actual
    ) {
        if (direction == KpiAttainmentDirection.AT_LEAST) {
            if (target.signum() == 0) {
                return actual.signum() >= 0
                        ? BigDecimal.ONE : BigDecimal.ZERO;
            }
            if (actual.signum() <= 0) {
                return BigDecimal.ZERO;
            }
            return actual.divide(target, SCALE, RoundingMode.HALF_UP);
        }
        if (actual.signum() <= 0) {
            return BigDecimal.ONE;
        }
        if (target.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return target.divide(actual, SCALE, RoundingMode.HALF_UP);
    }

    public record Result(String value, KpiWarningStatus status) {
        public Result {
            KpiDecimal.requireNonNegative(value);
            if (status == null || status == KpiWarningStatus.CALCULATION_FAILED) {
                throw new IllegalArgumentException(
                        "KPI attainment result status is invalid");
            }
        }
    }
}
