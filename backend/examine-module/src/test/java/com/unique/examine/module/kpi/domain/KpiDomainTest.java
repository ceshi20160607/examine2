package com.unique.examine.module.kpi.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KpiDomainTest {
    @Test
    void periodsMustAlignExactlyToMonthQuarterAndYearBoundaries() {
        assertThat(KpiPeriod.starting(
                KpiPeriodType.MONTH, LocalDate.parse("2026-07-01")))
                .extracting(KpiPeriod::endExclusive,
                        period -> period.monthCount())
                .containsExactly(LocalDate.parse("2026-08-01"), 1);
        assertThat(KpiPeriod.starting(
                KpiPeriodType.QUARTER, LocalDate.parse("2026-07-01")))
                .extracting(KpiPeriod::endExclusive,
                        period -> period.monthCount())
                .containsExactly(LocalDate.parse("2026-10-01"), 3);
        assertThat(KpiPeriod.starting(
                KpiPeriodType.YEAR, LocalDate.parse("2026-01-01")))
                .extracting(KpiPeriod::endExclusive,
                        period -> period.monthCount())
                .containsExactly(LocalDate.parse("2027-01-01"), 12);

        assertThatThrownBy(() -> KpiPeriod.starting(
                KpiPeriodType.MONTH, LocalDate.parse("2026-07-02")))
                .isInstanceOf(KpiException.class)
                .satisfies(error -> assertThat(((KpiException) error).code())
                        .isEqualTo("KPI_PERIOD_INVALID"));
        assertThatThrownBy(() -> KpiPeriod.starting(
                KpiPeriodType.QUARTER, LocalDate.parse("2026-08-01")))
                .isInstanceOf(KpiException.class);
        assertThatThrownBy(() -> KpiPeriod.starting(
                KpiPeriodType.YEAR, LocalDate.parse("2026-04-01")))
                .isInstanceOf(KpiException.class);
    }

    @Test
    void decimalsAreCanonicalNonNegativeAndNeverUseExponentNotation() {
        assertThat(KpiDecimal.canonical(new BigDecimal("100.000")))
                .isEqualTo("100");
        assertThat(KpiDecimal.canonical(new BigDecimal("0.000")))
                .isEqualTo("0");
        assertThat(KpiDecimal.canonical(new BigDecimal("1E+3")))
                .isEqualTo("1000");
        assertThat(KpiDecimal.requireNonNegative("0.125"))
                .isEqualTo("0.125");

        assertThatThrownBy(() -> KpiDecimal.require("1.0"))
                .isInstanceOf(KpiException.class);
        assertThatThrownBy(() -> KpiDecimal.require("1e3"))
                .isInstanceOf(KpiException.class);
        assertThatThrownBy(() -> KpiDecimal.requireNonNegative("-1"))
                .isInstanceOf(KpiException.class);
    }

    @Test
    void attainmentHandlesBothDirectionsThresholdsAndZeroTargets() {
        assertThat(KpiAttainment.evaluate(
                KpiAttainmentDirection.AT_LEAST, "100", "100", "0.8"))
                .isEqualTo(new KpiAttainment.Result(
                        "1", KpiWarningStatus.ACHIEVED));
        assertThat(KpiAttainment.evaluate(
                KpiAttainmentDirection.AT_LEAST, "100", "90", "0.8"))
                .isEqualTo(new KpiAttainment.Result(
                        "0.9", KpiWarningStatus.AT_RISK));
        assertThat(KpiAttainment.evaluate(
                KpiAttainmentDirection.AT_LEAST, "100", "70", "0.8"))
                .isEqualTo(new KpiAttainment.Result(
                        "0.7", KpiWarningStatus.MISSED));

        assertThat(KpiAttainment.evaluate(
                KpiAttainmentDirection.AT_MOST, "100", "90", "0.8"))
                .isEqualTo(new KpiAttainment.Result(
                        "1.111111111111", KpiWarningStatus.ACHIEVED));
        assertThat(KpiAttainment.evaluate(
                KpiAttainmentDirection.AT_MOST, "100", "120", "0.8"))
                .isEqualTo(new KpiAttainment.Result(
                        "0.833333333333", KpiWarningStatus.AT_RISK));
        assertThat(KpiAttainment.evaluate(
                KpiAttainmentDirection.AT_MOST, "100", "200", "0.8"))
                .isEqualTo(new KpiAttainment.Result(
                        "0.5", KpiWarningStatus.MISSED));

        assertThat(KpiAttainment.evaluate(
                KpiAttainmentDirection.AT_LEAST, "0", "0", "0.8"))
                .isEqualTo(new KpiAttainment.Result(
                        "1", KpiWarningStatus.ACHIEVED));
        assertThat(KpiAttainment.evaluate(
                KpiAttainmentDirection.AT_LEAST, "0", "9", "0.8"))
                .isEqualTo(new KpiAttainment.Result(
                        "1", KpiWarningStatus.ACHIEVED));
        assertThat(KpiAttainment.evaluate(
                KpiAttainmentDirection.AT_MOST, "0", "0", "0.8"))
                .isEqualTo(new KpiAttainment.Result(
                        "1", KpiWarningStatus.ACHIEVED));
        assertThat(KpiAttainment.evaluate(
                KpiAttainmentDirection.AT_MOST, "0", "1", "0.8"))
                .isEqualTo(new KpiAttainment.Result(
                        "0", KpiWarningStatus.MISSED));
        assertThat(KpiAttainment.evaluate(
                KpiAttainmentDirection.AT_LEAST, "10", "-2", "0.8"))
                .isEqualTo(new KpiAttainment.Result(
                        "0", KpiWarningStatus.MISSED));
        assertThat(KpiAttainment.evaluate(
                KpiAttainmentDirection.AT_MOST, "10", "-2", "0.8"))
                .isEqualTo(new KpiAttainment.Result(
                        "1", KpiWarningStatus.ACHIEVED));
    }

    @Test
    void warningThresholdIsCanonicalAndStrictlyBounded() {
        assertThat(KpiAttainment.requireThreshold("0.75"))
                .isEqualTo("0.75");
        assertThat(KpiAttainment.requireThreshold("1")).isEqualTo("1");
        assertThatThrownBy(() -> KpiAttainment.requireThreshold("0"))
                .isInstanceOf(KpiException.class);
        assertThatThrownBy(() -> KpiAttainment.requireThreshold("1.01"))
                .isInstanceOf(KpiException.class);
        assertThatThrownBy(() -> KpiAttainment.requireThreshold(".8"))
                .isInstanceOf(KpiException.class);
    }
}
