package com.unique.examine.core.ai;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRuntimeStatisticsReadFacadeTest {
    private static final String MODULE = "work_order";

    @Test
    void requestKeepsServerAuthorityAndPolicySnapshots() {
        var permissions = new HashSet<>(Set.of(
                "system.runtime.access", "module.work_order.view"));
        var modules = new HashSet<>(Set.of(MODULE));
        var moduleFields = new HashSet<>(Set.of("amount", "created_on"));
        var outbound = new HashMap<String, Set<String>>();
        outbound.put(MODULE, moduleFields);

        var request = new AiRuntimeStatisticsReadFacade.Request(
                11L, 13L, 17L, permissions, modules, outbound, 10,
                MODULE, "active_orders",
                AiRuntimeStatisticsReadFacade.Aggregation.SUM, "amount",
                null, new AiRuntimeStatisticsReadFacade.Trend(
                "created_on", AiRuntimeStatisticsReadFacade.Grain.DAY,
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-04")));

        permissions.add("module.work_order.update");
        modules.add("secret_module");
        moduleFields.add("secret_note");
        outbound.put("secret_module", Set.of("secret_note"));

        assertThat(request.effectivePermissions()).containsExactlyInAnyOrder(
                "system.runtime.access", "module.work_order.view");
        assertThat(request.allowedModuleCodes()).containsExactly(MODULE);
        assertThat(request.outboundFields()).containsOnlyKeys(MODULE);
        assertThat(request.outboundFields().get(MODULE))
                .containsExactlyInAnyOrder("amount", "created_on");
        assertThatThrownBy(() -> request.outboundFields().put(
                "other", Set.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void requestEnforcesAggregationBranchesBoundsAndPolicyGates() {
        assertThatThrownBy(() -> request(
                AiRuntimeStatisticsReadFacade.Aggregation.COUNT, "amount",
                null, null, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("COUNT");
        assertThatThrownBy(() -> request(
                AiRuntimeStatisticsReadFacade.Aggregation.AVG, null,
                null, null, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires");
        assertThatThrownBy(() -> request(
                AiRuntimeStatisticsReadFacade.Aggregation.SUM, "amount",
                new AiRuntimeStatisticsReadFacade.Grouping("status", 2),
                trend(3), 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");
        assertThatThrownBy(() -> request(
                AiRuntimeStatisticsReadFacade.Aggregation.COUNT, null,
                new AiRuntimeStatisticsReadFacade.Grouping("status", 6),
                null, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRows");
        assertThatThrownBy(() -> request(
                AiRuntimeStatisticsReadFacade.Aggregation.COUNT, null,
                null, trend(6), 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRows");
        assertThatThrownBy(() -> new AiRuntimeStatisticsReadFacade.Trend(
                "created_on", AiRuntimeStatisticsReadFacade.Grain.WEEK,
                LocalDate.parse("2026-08-02"),
                LocalDate.parse("2026-08-09")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unaligned");
        assertThatThrownBy(() -> new AiRuntimeStatisticsReadFacade.Request(
                11L, 13L, 17L, Set.of(), Set.of(MODULE),
                Map.of("other", Set.of("status")), 10,
                MODULE, "active_orders",
                AiRuntimeStatisticsReadFacade.Aggregation.SUM,
                "amount", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outbound fields");
    }

    @Test
    void resultPreservesCanonicalDecimalsAndSafeBucketProjection() {
        var buckets = new ArrayList<>(List.of(
                new AiRuntimeStatisticsReadFacade.GroupBucket(
                        "Open", false, "12.3", 2L),
                new AiRuntimeStatisticsReadFacade.GroupBucket(
                        null, true, null, 1L)));

        assertThatThrownBy(() -> new AiRuntimeStatisticsReadFacade.Result(
                "active_orders", MODULE, 3,
                AiRuntimeStatisticsReadFacade.Aggregation.SUM, "amount",
                "12.30", 3L, 2, 3L, true,
                new AiRuntimeStatisticsReadFacade.GroupingResult(
                        "status", buckets), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decimal");

        var result = new AiRuntimeStatisticsReadFacade.Result(
                "active_orders", MODULE, 3,
                AiRuntimeStatisticsReadFacade.Aggregation.SUM, "amount",
                "12.3", 3L, 2, 3L, true,
                new AiRuntimeStatisticsReadFacade.GroupingResult(
                        "status", buckets), null);
        buckets.clear();

        assertThat(result.value()).isEqualTo("12.3");
        assertThat(result.grouping().buckets()).hasSize(2);
        assertThatThrownBy(() -> result.grouping().buckets().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(AiRuntimeStatisticsReadFacade.GroupBucket.class
                .getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("label", "nullBucket", "value", "recordCount");
        assertThat(AiRuntimeStatisticsReadFacade.Result.class
                .getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("queryId", "dataSourceId",
                        "dataSourceVersionId", "schemaVersionId", "rawKey");
    }

    @Test
    void trendResultRequiresACompleteContiguousRange() {
        var start = LocalDate.parse("2026-08-01");
        var buckets = List.of(
                new AiRuntimeStatisticsReadFacade.TrendBucket(
                        start, start.plusDays(1), "0", 0L, true),
                new AiRuntimeStatisticsReadFacade.TrendBucket(
                        start.plusDays(1), start.plusDays(2), "2.5", 2L, false));
        var trend = new AiRuntimeStatisticsReadFacade.TrendResult(
                "created_on", AiRuntimeStatisticsReadFacade.Grain.DAY,
                start, start.plusDays(2), buckets);
        var result = new AiRuntimeStatisticsReadFacade.Result(
                "active_orders", MODULE, 4,
                AiRuntimeStatisticsReadFacade.Aggregation.AVG, "amount",
                "2.5", 2L, 2, 2L, false, null, trend);

        assertThat(result.trend().buckets()).extracting(
                        AiRuntimeStatisticsReadFacade.TrendBucket::value)
                .containsExactly("0", "2.5");
        assertThatThrownBy(() -> new AiRuntimeStatisticsReadFacade.TrendResult(
                "created_on", AiRuntimeStatisticsReadFacade.Grain.DAY,
                start, start.plusDays(2), List.of(buckets.get(1), buckets.get(0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contiguous");
    }

    private static AiRuntimeStatisticsReadFacade.Request request(
            AiRuntimeStatisticsReadFacade.Aggregation aggregation,
            String measure,
            AiRuntimeStatisticsReadFacade.Grouping grouping,
            AiRuntimeStatisticsReadFacade.Trend trend,
            int maxRows
    ) {
        return new AiRuntimeStatisticsReadFacade.Request(
                11L, 13L, 17L, Set.of(), Set.of(MODULE),
                Map.of(MODULE, Set.of("amount", "status", "created_on")),
                maxRows, MODULE, "active_orders", aggregation,
                measure, grouping, trend);
    }

    private static AiRuntimeStatisticsReadFacade.Trend trend(int days) {
        var start = LocalDate.parse("2026-08-01");
        return new AiRuntimeStatisticsReadFacade.Trend(
                "created_on", AiRuntimeStatisticsReadFacade.Grain.DAY,
                start, start.plusDays(days));
    }
}
