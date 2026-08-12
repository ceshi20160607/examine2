package com.unique.examine.module.datasource.statistics.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatisticsDomainTest {
    @Test
    void canonicalDecimalUsesPlainMinimalStrings() {
        assertThat(CanonicalDecimal.from(new BigDecimal("1.2300")))
                .isEqualTo("1.23");
        assertThat(CanonicalDecimal.from(new BigDecimal("0.000")))
                .isEqualTo("0");
        assertThat(CanonicalDecimal.from(new BigDecimal("1E+3")))
                .isEqualTo("1000");
        assertThat(CanonicalDecimal.from(null)).isNull();

        assertThatThrownBy(() -> CanonicalDecimal.require("1.2300"))
                .isInstanceOf(StatisticsException.class);
        assertThatThrownBy(() -> CanonicalDecimal.require("1e3"))
                .isInstanceOf(StatisticsException.class);
        assertThatThrownBy(() -> CanonicalDecimal.require("-0"))
                .isInstanceOf(StatisticsException.class);
    }

    @Test
    void aggregationAndGroupingBoundsAreImmutable() {
        var grouping = new StatisticsRequest.Grouping("category", 20);
        var request = new StatisticsRequest(
                StatisticsAggregation.SUM, "amount", grouping, null);

        assertThat(request.aggregation()).isEqualTo(StatisticsAggregation.SUM);
        assertThat(request.measureFieldCode()).isEqualTo("amount");
        assertThat(request.grouping()).isEqualTo(grouping);

        assertThatThrownBy(() -> new StatisticsRequest(
                StatisticsAggregation.COUNT, "amount", null, null))
                .isInstanceOf(StatisticsException.class);
        assertThatThrownBy(() -> new StatisticsRequest(
                StatisticsAggregation.AVG, null, null, null))
                .isInstanceOf(StatisticsException.class);
        assertThatThrownBy(() -> new StatisticsRequest.Grouping("category", 21))
                .isInstanceOf(StatisticsException.class);
    }

    @Test
    void trendRequiresAlignedBoundedCalendarBuckets() {
        var weekly = new StatisticsRequest.Trend(
                "createdAt", StatisticsGrain.WEEK,
                LocalDate.parse("2026-08-03"),
                LocalDate.parse("2026-08-24"));

        assertThat(weekly.bucketCount()).isEqualTo(3);
        assertThatThrownBy(() -> new StatisticsRequest.Trend(
                "createdAt", StatisticsGrain.WEEK,
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-24")))
                .isInstanceOf(StatisticsException.class);
        assertThatThrownBy(() -> new StatisticsRequest.Trend(
                "createdAt", StatisticsGrain.DAY,
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-05-01")))
                .isInstanceOf(StatisticsException.class);
        assertThatThrownBy(() -> new StatisticsRequest(
                StatisticsAggregation.COUNT, null,
                new StatisticsRequest.Grouping("category", 10), weekly))
                .isInstanceOf(StatisticsException.class);
    }

    @Test
    void internalOwnershipRestrictionsAreTypedCanonicalAndBounded() {
        assertThat(StatisticsRecordRestriction.ownerMembers(
                List.of(30L, 10L, 30L)).ownerIds())
                .containsExactly(10L, 30L);
        assertThat(StatisticsRecordRestriction.ownerMembers(List.of())
                .matchesNoRecords()).isTrue();
        assertThat(StatisticsRecordRestriction.ownerMember(9).kind())
                .isEqualTo(StatisticsRecordRestriction.Kind.OWNER_MEMBER);
        assertThat(StatisticsRecordRestriction.ownerDepartment(7).kind())
                .isEqualTo(StatisticsRecordRestriction.Kind.OWNER_DEPARTMENT);

        assertThatThrownBy(() -> StatisticsRecordRestriction.ownerMember(0))
                .isInstanceOfSatisfying(StatisticsException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("STATISTICS_RESTRICTION_INVALID"));
        assertThatThrownBy(() -> StatisticsRecordRestriction.ownerMembers(
                Collections.nCopies(
                        StatisticsRecordRestriction.MAX_OWNER_IDS + 1, 1L)))
                .isInstanceOf(StatisticsException.class);
    }
}
