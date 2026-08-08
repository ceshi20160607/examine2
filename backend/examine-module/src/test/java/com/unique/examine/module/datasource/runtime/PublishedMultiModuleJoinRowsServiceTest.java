package com.unique.examine.module.datasource.runtime;

import com.unique.examine.module.datasource.api.DataSourceViews;
import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.domain.DataSourcePublication;
import com.unique.examine.module.datasource.domain.DataSourceVersion;
import com.unique.examine.module.datasource.domain.ModuleDataSource;
import com.unique.examine.module.datasource.service.ExactDataSourcePublicationResolver;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublishedMultiModuleJoinRowsServiceTest {
    private static final RuntimeSession SESSION = new RuntimeSession(
            1, 10, 7, 20L, Set.of("system.runtime.access"));

    @Test
    void executesPinnedLeftDeepJoinAndPreservesDrillThrough() {
        var runtime = new StubRowsReader();
        var dataSources = childPublications();
        var publication = joinPublication(false);
        runtime.results.put(101L, rows(50,
                        row("customer-1", "id", 1L),
                        row("customer-2", "id", 2L)));
        runtime.results.put(202L, rows(50,
                        row("order-1", "customerId", 1L, "amount", 5),
                        row("order-2", "customerId", 1L, "amount", 7)));
        runtime.results.put(303L, rows(50,
                        row("profile-1", "customerId", 1L,
                                "segment", "A"),
                        row("profile-2", "customerId", 2L,
                                "segment", "B")));

        var result = new PublishedMultiModuleJoinRowsService(
                runtime, dataSources).read(SESSION, publication);

        assertThat(result.partial()).isFalse();
        assertThat(result.queryHash()).matches("^[0-9a-f]{64}$");
        assertThat(result.rows()).hasSize(3);
        assertThat(result.rows()).extracting(value ->
                        value.values().get("orders__amount"))
                .containsExactly(5, 7, null);
        assertThat(result.rows().getFirst().drillThrough())
                .containsEntry("customers", "customer-1")
                .containsEntry("orders", "order-1")
                .containsEntry("profile", "profile-1");
        assertThat(runtime.calls).contains("101:1001:50");
    }

    @Test
    void leftFailureIsObservableWhileInnerOrAnchorFailureFailsClosed() {
        var runtime = new StubRowsReader();
        var dataSources = childPublications();
        var publication = joinPublication(true);
        runtime.results.put(101L, rows(
                50, row("customer-1", "id", 1L)));
        runtime.failures.put(202L, new DataSourceException(
                "DATA_SOURCE_MODULE_FORBIDDEN", "denied"));
        runtime.results.put(303L, rows(50, row(
                "profile-1", "customerId", 1L, "segment", "A")));

        var result = new PublishedMultiModuleJoinRowsService(
                runtime, dataSources).read(SESSION, publication);

        assertThat(result.partial()).isTrue();
        assertThat(result.failedAliases()).containsExactly("orders");
        assertThat(result.rows().getFirst().values())
                .containsEntry("orders__amount", null);
    }

    @Test
    void propagatesChildBoundAndRejectsDeclaredCardinalityViolation() {
        var runtime = new StubRowsReader();
        var dataSources = childPublications();
        var publication = joinPublication(false);
        runtime.results.put(101L, rows(
                25, row("customer-1", "id", 1L)));
        runtime.results.put(202L, rows(25, row(
                "order-1", "customerId", 1L, "amount", 5)));
        runtime.results.put(303L, rows(25,
                        row("profile-1", "customerId", 1L, "segment", "A"),
                        row("profile-2", "customerId", 1L, "segment", "B")));

        assertThatThrownBy(() -> new PublishedMultiModuleJoinRowsService(
                runtime, dataSources).read(SESSION, publication))
                .isInstanceOf(DataSourceException.class)
                .hasMessageContaining("cardinality");
    }

    @Test
    void wholeJoinTimeoutIsBoundedAndSanitized() {
        var runtime = new StubRowsReader();
        var publication = joinPublication(false, 1);
        runtime.results.put(101L, rows(
                50, row("customer-1", "id", 1L)));
        runtime.results.put(202L, rows(50, row(
                "order-1", "customerId", 1L, "amount", 5)));
        runtime.results.put(303L, rows(50, row(
                "profile-1", "customerId", 1L, "segment", "A")));
        runtime.delays.put(202L, 2_000L);
        var started = System.nanoTime();

        assertThatThrownBy(() -> new PublishedMultiModuleJoinRowsService(
                runtime, childPublications()).read(SESSION, publication))
                .isInstanceOf(DataSourceException.class)
                .hasMessage("A joined source is unavailable or timed out")
                .hasMessageNotContaining("password")
                .hasMessageNotContaining("token")
                .hasMessageNotContaining("env://");
        assertThat(java.time.Duration.ofNanos(
                System.nanoTime() - started)).isLessThan(
                java.time.Duration.ofMillis(1_900));
    }

    private static ExactDataSourcePublicationResolver childPublications() {
        var publications = List.of(
                child(101, 1001, "customers", 11),
                child(202, 2002, "orders", 22),
                child(303, 3003, "profile", 33));
        return (actor, dataSourceId, versionId) -> publications.stream()
                .filter(value -> value.root().id() == dataSourceId
                        && value.version().id() == versionId
                        && actor.equals(new DataSourceActor(10, 20, 7)))
                .findFirst().orElseThrow();
    }

    private static DataSourcePublication joinPublication(boolean partial) {
        return joinPublication(partial, 3);
    }

    private static DataSourcePublication joinPublication(
            boolean partial,
            int timeoutSeconds
    ) {
        var inputs = List.of(
                new DataSourceDraft.JoinInput("customers", 101, 1001),
                new DataSourceDraft.JoinInput("orders", 202, 2002),
                new DataSourceDraft.JoinInput("profile", 303, 3003));
        var edges = List.of(
                new DataSourceDraft.JoinEdge(
                        "customers", "id", "orders", "customerId",
                        DataSourceDraft.JoinType.LEFT,
                        DataSourceDraft.JoinCardinality.ONE_TO_MANY),
                new DataSourceDraft.JoinEdge(
                        "customers", "id", "profile", "customerId",
                        DataSourceDraft.JoinType.LEFT,
                        DataSourceDraft.JoinCardinality.ONE_TO_ONE));
        var projections = List.of(
                pin("customers", "id", "customers__id", 11, "ID", "NUMBER"),
                pin("orders", "amount", "orders__amount", 22, "Amount", "NUMBER"),
                pin("profile", "segment", "profile__segment", 33, "Segment", "TEXT"));
        var draft = new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.MULTI_MODULE_JOIN,
                null, List.of(), null, List.of(),
                new DataSourceDraft.MultiModuleJoin(
                        inputs, edges, projections,
                        partial
                                ? DataSourceDraft.JoinFailureMode.ALLOW_PARTIAL_LEFT
                                : DataSourceDraft.JoinFailureMode.FAIL_FAST,
                        timeoutSeconds, 50));
        return publication(900, 9000, "customer_orders", 11, draft);
    }

    private static DataSourceDraft.JoinProjection pin(
            String alias, String source, String output, long id,
            String name, String type
    ) {
        return new DataSourceDraft.JoinProjection(
                alias, source, output, id, name, type, type,
                "NUMBER".equals(type), false, true);
    }

    private static DataSourcePublication child(
            long id, long version, String code, long module
    ) {
        return publication(id, version, code, module,
                new DataSourceDraft(
                        List.of(new DataSourceDraft.OutputField("id")),
                        List.of(), null, null));
    }

    private static DataSourcePublication publication(
            long id, long versionId, String code, long moduleId,
            DataSourceDraft draft
    ) {
        var now = Instant.parse("2026-08-07T00:00:00Z");
        var root = new ModuleDataSource(
                id, 10, 20, code, moduleId, code, null, draft, 1,
                versionId, 1, now, now, 2);
        var version = new DataSourceVersion(
                versionId, id, 10, 20, 1, code, moduleId,
                code + "_module", "schema-" + moduleId, code, null, draft,
                "a".repeat(64), 7, now);
        return new DataSourcePublication(root, version);
    }

    private static DataSourceViews.RuntimeRows rows(
            int sourceBound,
            DataSourceViews.RuntimeRow... rows
    ) {
        return new DataSourceViews.RuntimeRows(
                List.of(rows), List.of(rows), 1, 50, rows.length, "hash",
                sourceBound < 50, sourceBound < 50 ? "HTTP_JSON"
                        : "NATIVE_MODULE", sourceBound < 50 ? sourceBound : 0);
    }

    private static DataSourceViews.RuntimeRow row(
            String id, Object... values
    ) {
        var projected = new java.util.ArrayList<DataSourceViews.RuntimeValue>();
        for (var index = 0; index < values.length; index += 2) {
            projected.add(new DataSourceViews.RuntimeValue(
                    String.valueOf(values[index]),
                    String.valueOf(values[index]), "TEXT", values[index + 1],
                    String.valueOf(values[index + 1])));
        }
        return new DataSourceViews.RuntimeRow(
                id, id, 1, "ACTIVE", id, projected);
    }

    private static final class StubRowsReader
            implements ExactPublishedDataSourceRowsReader {
        private final Map<Long, DataSourceViews.RuntimeRows> results =
                new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<Long, RuntimeException> failures =
                new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<Long, Long> delays =
                new java.util.concurrent.ConcurrentHashMap<>();
        private final List<String> calls =
                new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public DataSourceViews.RuntimeRows rows(
                RuntimeSession session,
                long dataSourceId,
                long dataSourceVersionId,
                int page,
                int size
        ) {
            calls.add(dataSourceId + ":" + dataSourceVersionId + ":" + size);
            var delay = delays.get(dataSourceId);
            if (delay != null) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new DataSourceException(
                            "DATA_SOURCE_JOIN_UNAVAILABLE", "interrupted");
                }
            }
            var failure = failures.get(dataSourceId);
            if (failure != null) {
                throw failure;
            }
            return results.get(dataSourceId);
        }
    }
}
