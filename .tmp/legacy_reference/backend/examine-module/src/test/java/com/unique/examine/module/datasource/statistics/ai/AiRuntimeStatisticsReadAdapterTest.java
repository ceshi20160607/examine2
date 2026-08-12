package com.unique.examine.module.datasource.statistics.ai;

import com.unique.examine.core.ai.AiRuntimeStatisticsReadFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.domain.DataSourceVersion;
import com.unique.examine.module.datasource.domain.ModuleDataSource;
import com.unique.examine.module.datasource.domain.PublishedDataSource;
import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;
import com.unique.examine.module.datasource.statistics.domain.StatisticsRequest;
import com.unique.examine.module.datasource.statistics.domain.StatisticsResult;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRuntimeStatisticsReadAdapterTest {
    private static final String MODULE = "work_order";
    private static final String SOURCE = "active_orders";
    private static final Set<String> VIEW = Set.of(
            "system.runtime.access", "module.work_order.view");

    @Test
    void resolvesTheActivePublicationAndDelegatesTheExactNativeRequest() {
        var seenActor = new AtomicReference<DataSourceActor>();
        var seenCode = new AtomicReference<String>();
        var seenSession = new AtomicReference<RuntimeSession>();
        var seenDataSourceId = new AtomicReference<Long>();
        var seenVersionId = new AtomicReference<Long>();
        var seenRequest = new AtomicReference<StatisticsRequest>();
        var active = active(MODULE, 11L, 13L);
        var adapter = new AiRuntimeStatisticsReadAdapter(
                (actor, code) -> {
                    seenActor.set(actor);
                    seenCode.set(code);
                    return active;
                },
                (session, dataSourceId, exactVersionId, request) -> {
                    seenSession.set(session);
                    seenDataSourceId.set(dataSourceId);
                    seenVersionId.set(exactVersionId);
                    seenRequest.set(request);
                    return scalar(StatisticsAggregation.SUM, "amount", "12.3", 4L);
                });

        var result = adapter.query(request(
                VIEW, Set.of(MODULE),
                Map.of(MODULE, Set.of("amount")),
                AiRuntimeStatisticsReadFacade.Aggregation.SUM,
                "amount", null, null));

        assertThat(seenActor.get()).isEqualTo(new DataSourceActor(11L, 13L, 17L));
        assertThat(seenCode.get()).isEqualTo(SOURCE);
        assertThat(seenSession.get()).isEqualTo(new RuntimeSession(
                0L, 11L, 17L, 13L, VIEW));
        assertThat(seenDataSourceId.get()).isEqualTo(101L);
        assertThat(seenVersionId.get()).isEqualTo(503L);
        assertThat(seenRequest.get()).isEqualTo(new StatisticsRequest(
                StatisticsAggregation.SUM, "amount", null, null));
        assertThat(result.dataSourceCode()).isEqualTo(SOURCE);
        assertThat(result.moduleCode()).isEqualTo(MODULE);
        assertThat(result.dataSourceVersionNumber()).isEqualTo(3);
        assertThat(result.value()).isEqualTo("12.3");
        assertThat(result.matchedRecordCount()).isEqualTo(4L);
        assertThat(result.bucketCount()).isZero();
        assertThat(result.grouping()).isNull();
        assertThat(result.trend()).isNull();
    }

    @Test
    void mapsGroupsWithoutRawKeysAndPreservesNullBucketsAndOverflow() {
        var adapter = new AiRuntimeStatisticsReadAdapter(
                (actor, code) -> active(MODULE, actor.systemId(), actor.tenantId()),
                (session, dataSourceId, exactVersionId, request) -> grouped());
        var grouping = new AiRuntimeStatisticsReadFacade.Grouping("status", 2);

        var result = adapter.query(request(
                VIEW, Set.of(MODULE),
                Map.of(MODULE, Set.of("amount", "status")),
                AiRuntimeStatisticsReadFacade.Aggregation.SUM,
                "amount", grouping, null));

        assertThat(result.grouping().fieldCode()).isEqualTo("status");
        assertThat(result.grouping().buckets())
                .extracting(AiRuntimeStatisticsReadFacade.GroupBucket::label)
                .containsExactly("Open", null);
        assertThat(result.grouping().buckets())
                .extracting(AiRuntimeStatisticsReadFacade.GroupBucket::value)
                .containsExactly("7.5", null);
        assertThat(result.grouping().buckets().getLast().nullBucket()).isTrue();
        assertThat(result.totalBucketCount()).isEqualTo(3L);
        assertThat(result.truncated()).isTrue();
        assertThat(AiRuntimeStatisticsReadFacade.GroupBucket.class
                .getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("key", "rawKey");
    }

    @Test
    void mapsACompleteTrendAndDistinguishesZeroFromEmptyNull() {
        var seen = new AtomicReference<StatisticsRequest>();
        var adapter = new AiRuntimeStatisticsReadAdapter(
                (actor, code) -> active(MODULE, actor.systemId(), actor.tenantId()),
                (session, dataSourceId, exactVersionId, request) -> {
                    seen.set(request);
                    return trend();
                });
        var start = LocalDate.parse("2026-08-01");
        var requestedTrend = new AiRuntimeStatisticsReadFacade.Trend(
                "created_on", AiRuntimeStatisticsReadFacade.Grain.DAY,
                start, start.plusDays(3));

        var result = adapter.query(request(
                VIEW, Set.of(MODULE),
                Map.of(MODULE, Set.of("amount", "created_on")),
                AiRuntimeStatisticsReadFacade.Aggregation.AVG,
                "amount", null, requestedTrend));

        assertThat(seen.get().trend().bucketCount()).isEqualTo(3);
        assertThat(result.trend().fieldCode()).isEqualTo("created_on");
        assertThat(result.trend().buckets())
                .extracting(AiRuntimeStatisticsReadFacade.TrendBucket::value)
                .containsExactly("0", null, "2.5");
        assertThat(result.trend().buckets())
                .extracting(AiRuntimeStatisticsReadFacade.TrendBucket::empty)
                .containsExactly(false, true, false);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void deniesPermissionsAndPolicyBeforeStatisticsExecution() {
        var sourceCalls = new AtomicInteger();
        var statisticsCalls = new AtomicInteger();
        var adapter = new AiRuntimeStatisticsReadAdapter(
                (actor, code) -> {
                    sourceCalls.incrementAndGet();
                    return active(MODULE, actor.systemId(), actor.tenantId());
                },
                (session, dataSourceId, exactVersionId, request) -> {
                    statisticsCalls.incrementAndGet();
                    return scalar(StatisticsAggregation.COUNT, null, "0", 0L);
                });

        assertThatThrownBy(() -> adapter.query(request(
                Set.of("system.runtime.access"), Set.of(MODULE),
                Map.of(MODULE, Set.of()),
                AiRuntimeStatisticsReadFacade.Aggregation.COUNT,
                null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code())
                        .isEqualTo("PERMISSION_DENIED"));
        assertThatThrownBy(() -> adapter.query(request(
                VIEW, Set.of("other_module"),
                Map.of("other_module", Set.of()),
                AiRuntimeStatisticsReadFacade.Aggregation.COUNT,
                null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code())
                        .isEqualTo("AI_POLICY_MODULE_DENIED"));
        assertThatThrownBy(() -> adapter.query(request(
                VIEW, Set.of(MODULE), Map.of(MODULE, Set.of("status")),
                AiRuntimeStatisticsReadFacade.Aggregation.SUM,
                "amount", null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code())
                        .isEqualTo("AI_POLICY_FIELD_DENIED"));

        assertThat(sourceCalls).hasValue(0);
        assertThat(statisticsCalls).hasValue(0);
    }

    @Test
    void hidesForeignTenantAndRejectsARealModuleMismatchWithoutExecution() {
        var statisticsCalls = new AtomicInteger();
        var foreign = new AiRuntimeStatisticsReadAdapter(
                (actor, code) -> active(MODULE, actor.systemId(), 99L),
                (session, dataSourceId, exactVersionId, request) -> {
                    statisticsCalls.incrementAndGet();
                    return scalar(StatisticsAggregation.COUNT, null, "0", 0L);
                });
        assertThatThrownBy(() -> foreign.query(request(
                VIEW, Set.of(MODULE), Map.of(MODULE, Set.of()),
                AiRuntimeStatisticsReadFacade.Aggregation.COUNT,
                null, null, null)))
                .isInstanceOf(DataSourceException.class)
                .satisfies(error -> assertThat(((DataSourceException) error).code())
                        .isEqualTo("DATA_SOURCE_NOT_FOUND"));

        var mismatched = new AiRuntimeStatisticsReadAdapter(
                (actor, code) -> active("secret_module",
                        actor.systemId(), actor.tenantId()),
                (session, dataSourceId, exactVersionId, request) -> {
                    statisticsCalls.incrementAndGet();
                    return scalar(StatisticsAggregation.COUNT, null, "0", 0L);
                });
        assertThatThrownBy(() -> mismatched.query(request(
                VIEW, Set.of(MODULE), Map.of(MODULE, Set.of()),
                AiRuntimeStatisticsReadFacade.Aggregation.COUNT,
                null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code())
                        .isEqualTo("AI_POLICY_MODULE_DENIED"));
        assertThat(statisticsCalls).hasValue(0);
    }

    @Test
    void ownerMethodIsTransactionallyReadOnlyAndCarriesNoWritePort() throws Exception {
        var method = AiRuntimeStatisticsReadAdapter.class.getMethod(
                "query", AiRuntimeStatisticsReadFacade.Request.class);
        assertThat(method.getAnnotation(Transactional.class)).satisfies(annotation ->
                assertThat(annotation.readOnly()).isTrue());
        assertThat(AiRuntimeStatisticsReadAdapter.class.getDeclaredFields())
                .filteredOn(field -> !java.lang.reflect.Modifier.isStatic(
                        field.getModifiers()))
                .extracting(field -> field.getType().getName())
                .containsExactlyInAnyOrder(
                        AiRuntimeStatisticsReadAdapter.ActiveDataSourceReader.class
                                .getName(),
                        AiRuntimeStatisticsReadAdapter.StatisticsReader.class
                                .getName())
                .doesNotContain(javax.sql.DataSource.class.getName(),
                        org.springframework.jdbc.core.JdbcTemplate.class
                                .getName());
    }

    private static AiRuntimeStatisticsReadFacade.Request request(
            Set<String> permissions,
            Set<String> modules,
            Map<String, Set<String>> fields,
            AiRuntimeStatisticsReadFacade.Aggregation aggregation,
            String measure,
            AiRuntimeStatisticsReadFacade.Grouping grouping,
            AiRuntimeStatisticsReadFacade.Trend trend
    ) {
        return new AiRuntimeStatisticsReadFacade.Request(
                11L, 13L, 17L, permissions, modules, fields, 10,
                MODULE, SOURCE, aggregation, measure, grouping, trend);
    }

    private static PublishedDataSource active(
            String moduleCode,
            long systemId,
            long tenantId
    ) {
        var publishedAt = Instant.parse("2026-08-01T00:00:00Z");
        var root = new ModuleDataSource(
                101L, systemId, tenantId, SOURCE, 41L,
                "Active orders", null, DataSourceDraft.empty(),
                3L, 503L, 3, publishedAt, publishedAt, 3L);
        var version = new DataSourceVersion(
                503L, 101L, systemId, tenantId, 3, SOURCE, 41L,
                moduleCode, "schema-3", "Active orders", null,
                new DataSourceDraft(
                        List.of(
                                new DataSourceDraft.OutputField("amount"),
                                new DataSourceDraft.OutputField("status"),
                                new DataSourceDraft.OutputField("created_on")),
                        List.of(), null, "created_on"),
                "a".repeat(64), 17L, publishedAt);
        return new PublishedDataSource(root, version);
    }

    private static StatisticsResult scalar(
            StatisticsAggregation aggregation,
            String measure,
            String value,
            long matched
    ) {
        return new StatisticsResult(
                "query-scalar", 101L, SOURCE, 503L, 3,
                MODULE, "schema-3", aggregation, measure, value,
                matched, 1, 0, List.of(), List.of(), 0L, false);
    }

    private static StatisticsResult grouped() {
        return new StatisticsResult(
                "query-group", 101L, SOURCE, 503L, 3,
                MODULE, "schema-3", StatisticsAggregation.SUM, "amount",
                "7.5", 3L, 3, 2,
                List.of(
                        new StatisticsResult.GroupBucket(
                                "OPEN", "Open", false, "7.5", 2L),
                        new StatisticsResult.GroupBucket(
                                null, null, true, null, 1L)),
                List.of(), 3L, true);
    }

    private static StatisticsResult trend() {
        var start = LocalDate.parse("2026-08-01");
        return new StatisticsResult(
                "query-trend", 101L, SOURCE, 503L, 3,
                MODULE, "schema-3", StatisticsAggregation.AVG, "amount",
                "2.5", 3L, 4, 3, List.of(),
                List.of(
                        new StatisticsResult.TrendBucket(
                                start, start.plusDays(1), "0", 1L, false),
                        new StatisticsResult.TrendBucket(
                                start.plusDays(1), start.plusDays(2),
                                null, 0L, true),
                        new StatisticsResult.TrendBucket(
                                start.plusDays(2), start.plusDays(3),
                                "2.5", 2L, false)),
                3L, false);
    }
}
