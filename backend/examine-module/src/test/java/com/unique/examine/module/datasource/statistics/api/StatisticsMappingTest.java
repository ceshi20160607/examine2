package com.unique.examine.module.datasource.statistics.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;
import com.unique.examine.module.datasource.statistics.domain.StatisticsCapabilities;
import com.unique.examine.module.datasource.statistics.domain.StatisticsException;
import com.unique.examine.module.datasource.statistics.domain.StatisticsGrain;
import com.unique.examine.module.datasource.statistics.domain.StatisticsResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatisticsMappingTest {
    @Test
    void wireViewsKeepIdsAsStringsAndCanonicalValuesUnchanged() {
        var capabilities = StatisticsMapping.capabilities(
                new StatisticsCapabilities(
                        9007199254740993L, "orders", 9007199254740995L,
                        7, "order", "701", List.of(
                        new StatisticsCapabilities.Field(
                                "amount", "Amount", "NUMBER",
                                true, true, false, true))));
        var result = StatisticsMapping.result(new StatisticsResult(
                "a".repeat(64), 9007199254740993L, "orders",
                9007199254740995L, 7, "order", "701",
                StatisticsAggregation.SUM, "amount", "1234567890.01",
                3, 1, 0, List.of(), List.of(), 0, false));

        assertThat(capabilities.dataSourceId())
                .isEqualTo("9007199254740993");
        assertThat(capabilities.dataSourceVersionId())
                .isEqualTo("9007199254740995");
        assertThat(result.dataSourceId()).isEqualTo(capabilities.dataSourceId());
        assertThat(result.dataSourceVersionId())
                .isEqualTo(capabilities.dataSourceVersionId());
        assertThat(result.value()).isEqualTo("1234567890.01");
        assertThat(result.bucketCount()).isZero();
    }

    @Test
    void requestMappingBuildsGroupedNativeRequest() {
        var request = StatisticsMapping.request(new StatisticsRequests.Query(
                "AVG", "amount",
                new StatisticsRequests.Grouping("status", 12), null));

        assertThat(request.aggregation()).isEqualTo(StatisticsAggregation.AVG);
        assertThat(request.measureFieldCode()).isEqualTo("amount");
        assertThat(request.grouping().fieldCode()).isEqualTo("status");
        assertThat(request.grouping().bucketLimit()).isEqualTo(12);
    }

    @Test
    void serializedResultKeepsLargeIdsAndDecimalsAsJsonStrings() {
        var result = StatisticsMapping.result(new StatisticsResult(
                "b".repeat(64), 9007199254740993L, "orders",
                9007199254740995L, 7, "order", "701",
                StatisticsAggregation.SUM, "amount", "1234567890.01",
                3, 3, 2, List.of(
                new StatisticsResult.GroupBucket(
                        "paid", "Paid", false, "100.01", 2),
                new StatisticsResult.GroupBucket(
                        null, null, true, "23", 1)),
                List.of(), 2, false));

        var json = new ObjectMapper().valueToTree(result);

        assertThat(json.path("dataSourceId").isTextual()).isTrue();
        assertThat(json.path("dataSourceId").textValue())
                .isEqualTo("9007199254740993");
        assertThat(json.path("dataSourceVersionId").isTextual()).isTrue();
        assertThat(json.path("value").isTextual()).isTrue();
        assertThat(json.path("groupBuckets").get(0).path("value")
                .isTextual()).isTrue();
        assertThat(json.path("groupBuckets").get(1).path("key").isNull())
                .isTrue();
    }

    @Test
    void requestMappingBuildsBoundedTrendAndRejectsUnsupportedEnums() {
        var request = StatisticsMapping.request(new StatisticsRequests.Query(
                "COUNT", null, null, new StatisticsRequests.Trend(
                "createdAt", "WEEK", LocalDate.parse("2026-07-06"),
                LocalDate.parse("2026-07-20"))));

        assertThat(request.trend().fieldCode()).isEqualTo("createdAt");
        assertThat(request.trend().grain()).isEqualTo(StatisticsGrain.WEEK);
        assertThat(request.trend().bucketCount()).isEqualTo(2);
        assertThatThrownBy(() -> StatisticsMapping.request(
                new StatisticsRequests.Query(
                        "MEDIAN", "amount", null, null)))
                .isInstanceOfSatisfying(StatisticsException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("STATISTICS_REQUEST_INVALID"));
        assertThatThrownBy(() -> StatisticsMapping.request(
                new StatisticsRequests.Query(
                        "COUNT", null, null,
                        new StatisticsRequests.Trend(
                                "createdAt", "QUARTER",
                                LocalDate.parse("2026-07-01"),
                                LocalDate.parse("2026-08-01")))))
                .isInstanceOfSatisfying(StatisticsException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("STATISTICS_REQUEST_INVALID"));
    }

    @Test
    void publicStatisticsRequestHasNoOwnershipRestrictionSurface() {
        assertThat(StatisticsRequests.Query.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly(
                        "aggregation", "measureFieldCode", "grouping", "trend");
        var lenient = new ObjectMapper().configure(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        assertThatThrownBy(() -> lenient.readValue("""
                {"aggregation":"COUNT","restriction":{
                  "kind":"OWNER_MEMBER","ownerIds":["9"]}}
                """, StatisticsRequests.Query.class))
                .hasMessageContaining("Unknown public statistics request field");
    }
}
