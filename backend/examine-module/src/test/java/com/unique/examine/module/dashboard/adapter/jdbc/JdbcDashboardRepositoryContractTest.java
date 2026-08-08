package com.unique.examine.module.dashboard.adapter.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.id.IdService;
import com.unique.examine.module.dashboard.domain.DashboardDraft;
import com.unique.examine.module.dashboard.domain.DashboardException;
import com.unique.examine.module.dashboard.domain.DashboardGrid;
import com.unique.examine.module.dashboard.domain.DashboardPlacement;
import com.unique.examine.module.dashboard.domain.DashboardStatisticsSnapshot;
import com.unique.examine.module.dashboard.domain.DashboardVersion;
import com.unique.examine.module.dashboard.domain.DashboardVersionWidget;
import com.unique.examine.module.dashboard.domain.DashboardWidgetDraft;
import com.unique.examine.module.dashboard.domain.DashboardWidgetType;
import com.unique.examine.module.dashboard.domain.SystemDashboard;
import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;
import com.unique.examine.module.datasource.statistics.domain.StatisticsRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcDashboardRepositoryContractTest {
    @Test
    void draftSaveUsesScopedImmutableIdentityAndDualCasFacts() {
        var jdbc = new RecordingJdbcTemplate();
        var repository = repository(jdbc);
        var expected = root();
        var revised = expected.reviseDraft(
                "Home v2", "Current home", draft(),
                expected.updatedAt().plusSeconds(1));

        assertThat(repository.saveDraft(expected, revised)).isEqualTo(revised);

        var call = jdbc.updates.getFirst();
        assertThat(normalize(call.sql()))
                .contains("where system_id=? and tenant_id=? and id=?")
                .contains("dashboard_code=? and placement=?")
                .contains("draft_version=? and version=?")
                .contains("active_version_id <=> ?")
                .contains("active_version_no <=> ?")
                .doesNotContain(
                        "set dashboard_code=", "set placement=");
        assertThat(call.arguments())
                .contains(revised.draftVersion(), revised.version(),
                        expected.systemId(), expected.tenantId(), expected.id(),
                        expected.code(), expected.placement().name(),
                        expected.draftVersion(), expected.version());
    }

    @Test
    void publishAtomicallyWritesVersionWidgetsThenAdvancesScopedPointer() {
        var jdbc = new RecordingJdbcTemplate();
        var repository = repository(jdbc);
        var expected = root();
        var version = version(expected, expected.draftVersion());
        var activated = expected.activate(
                version, version.publishedAt());

        assertThat(repository.publish(expected, activated, version))
                .isEqualTo(version);

        assertThat(jdbc.updates).hasSize(2);
        assertThat(normalize(jdbc.updates.getFirst().sql()))
                .startsWith("insert into un_module_dashboard_version")
                .contains("source_draft_version")
                .contains("snapshot_json", "snapshot_fingerprint")
                .contains("widget_count");
        assertThat(jdbc.updates.getFirst().arguments()[5])
                .isEqualTo(expected.draftVersion());
        assertThat(jdbc.updates.getFirst().arguments()[12]).isEqualTo(2);

        assertThat(jdbc.batches).hasSize(1);
        var batch = jdbc.batches.getFirst();
        assertThat(normalize(batch.sql()))
                .startsWith("insert into un_module_dashboard_version_widget")
                .contains("dashboard_version_no")
                .contains("data_source_version_id")
                .contains("data_source_version_no")
                .contains("module_code", "schema_version_id")
                .contains("widget_ordinal");
        assertThat(batch.arguments()).hasSize(2);
        assertThat(batch.arguments().getFirst())
                .containsSequence(
                        301L, 10L, 20L, 100L, 200L, 1, 0,
                        "openOrders", "STAT_COUNT", "Open orders", 501L,
                        601L, 3, "open_orders", "orders", "701");
        assertThat(batch.arguments().get(1))
                .contains(502L, 602L, 4, "recent_orders", "orders", "701",
                        10);

        assertThat(normalize(jdbc.updates.getLast().sql()))
                .startsWith("update un_module_dashboard")
                .contains("set active_version_id=?,active_version_no=?")
                .contains("where system_id=? and tenant_id=? and id=?")
                .contains("dashboard_code=? and placement=?")
                .contains("draft_version=? and version=?")
                .contains("active_version_id <=> ?")
                .contains("active_version_no <=> ?");
    }

    @Test
    void staleDraftCasFailsWithoutReturningAFalseSuccess() {
        var jdbc = new RecordingJdbcTemplate();
        jdbc.affected = 0;
        var repository = repository(jdbc);
        var expected = root();
        var revised = expected.reviseDraft(
                "Home v2", null, draft(),
                expected.updatedAt().plusSeconds(1));

        assertThatThrownBy(() -> repository.saveDraft(expected, revised))
                .isInstanceOfSatisfying(DashboardException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("DASHBOARD_VERSION_CONFLICT"));
    }

    @Test
    void publicationRejectsAClaimForAnotherDraftGeneration() {
        var jdbc = new RecordingJdbcTemplate();
        var repository = repository(jdbc);
        var expected = root();
        var version = version(expected, expected.draftVersion() + 1);
        var activated = expected.activate(
                version, version.publishedAt());

        assertThatThrownBy(
                () -> repository.publish(expected, activated, version))
                .isInstanceOfSatisfying(DashboardException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("DASHBOARD_INVALID"));
        assertThat(jdbc.updates).isEmpty();
        assertThat(jdbc.batches).isEmpty();
    }

    @Test
    void readsAreTenantScopedAndUseStableVersionWidgetOrdering() {
        var jdbc = new RecordingJdbcTemplate();
        var repository = repository(jdbc);

        assertThat(repository.findById(10, 20, 100)).isEmpty();
        assertThat(repository.findByCode(10, 20, "home")).isEmpty();
        assertThat(repository.findByPlacement(
                10, 20, DashboardPlacement.SYSTEM_HOME)).isEmpty();
        assertThat(repository.findAll(10, 20)).isEmpty();
        assertThat(repository.findActiveVersion(10, 20, 100)).isEmpty();
        assertThat(repository.findVersion(10, 20, 100, 1)).isEmpty();
        assertThat(repository.findVersions(10, 20, 100)).isEmpty();

        assertThat(jdbc.queries)
                .allSatisfy(call -> assertThat(normalize(call.sql()))
                        .contains("system_id=?", "tenant_id=?"));
        assertThat(normalize(jdbc.queries.get(2).sql()))
                .contains("placement=?");
        assertThat(normalize(jdbc.queries.get(3).sql()))
                .contains("order by updated_at desc,id desc");
        assertThat(normalize(jdbc.queries.get(4).sql()))
                .contains("join un_module_dashboard_version version_row")
                .contains("version_row.system_id=root.system_id")
                .contains("version_row.tenant_id=root.tenant_id")
                .contains("version_row.id=root.active_version_id")
                .contains("version_row.version_no=root.active_version_no")
                .contains("version_row.source_draft_version");
        assertThat(normalize(jdbc.queries.getLast().sql()))
                .contains("order by version_no desc,id desc");

        assertThat(normalize(JdbcDashboardRepository.WIDGET_COLUMNS))
                .contains("widget_ordinal", "data_source_version_id",
                        "data_source_version_no", "module_code",
                        "schema_version_id", "grid_x", "grid_height");
    }

    @Test
    void publicationPersistsImmutableStatisticsRequestColumns() {
        var jdbc = new RecordingJdbcTemplate();
        var repository = repository(jdbc);
        var expected = root();
        var publishedAt = expected.updatedAt().plusSeconds(1);
        var request = new StatisticsRequest(
                StatisticsAggregation.SUM, "amount", null, null);
        var widget = new DashboardVersionWidget(
                303, 203, expected.id(), expected.systemId(),
                expected.tenantId(), 0, "revenue",
                DashboardWidgetType.STAT_VALUE, "Revenue", 501,
                "open_orders", 601, 3, "orders", "701", null,
                new DashboardStatisticsSnapshot(
                        request,
                        new DashboardStatisticsSnapshot.Field(
                                901, "amount", "Amount", "DECIMAL",
                                "DECIMAL"),
                        null, null),
                new DashboardGrid(0, 0, 4, 2));
        var version = new DashboardVersion(
                203, expected.id(), expected.systemId(), expected.tenantId(),
                1, expected.draftVersion(), expected.code(),
                expected.placement(), expected.name(), expected.description(),
                List.of(widget), "c".repeat(64), 30, publishedAt);

        repository.publish(
                expected, expected.activate(version, publishedAt), version);

        var batch = jdbc.batches.getFirst();
        assertThat(normalize(batch.sql()))
                .contains("stat_aggregation", "stat_measure_field_code",
                        "stat_group_field_code", "stat_group_limit",
                        "stat_time_field_code", "stat_time_grain",
                        "stat_time_start", "stat_time_end");
        var arguments = batch.arguments().getFirst();
        assertThat(arguments).hasSize(51);
        assertThat(arguments[17]).isEqualTo("SUM");
        assertThat(arguments[18]).isEqualTo("amount");
        assertThat(arguments[19]).isEqualTo(901L);
        assertThat(arguments[20]).isEqualTo("Amount");
        assertThat(arguments[21]).isEqualTo("DECIMAL");
        assertThat(arguments[22]).isEqualTo("DECIMAL");
        assertThat(arguments[23]).isNull();
        assertThat(arguments[29]).isNull();
    }

    @Test
    void publicationPersistsOnlyImmutableKpiPinsForKpiWidgets() {
        var jdbc = new RecordingJdbcTemplate();
        var repository = repository(jdbc);
        var expected = root();
        var publishedAt = expected.updatedAt().plusSeconds(1);
        var widget = DashboardVersionWidget.kpiValue(
                304, 204, expected.id(), expected.systemId(),
                expected.tenantId(), 0, "revenueKpi", "Revenue KPI",
                900, 901, 3, "revenue", "Revenue",
                com.unique.examine.module.kpi.domain.KpiSubjectType.ROLE,
                com.unique.examine.module.kpi.domain.KpiPeriodType.MONTH,
                new DashboardGrid(0, 0, 4, 2));
        var version = new DashboardVersion(
                204, expected.id(), expected.systemId(), expected.tenantId(),
                1, expected.draftVersion(), expected.code(),
                expected.placement(), expected.name(), expected.description(),
                List.of(widget), "d".repeat(64), 30, publishedAt);

        repository.publish(
                expected, expected.activate(version, publishedAt), version);

        var batch = jdbc.batches.getFirst();
        assertThat(normalize(batch.sql()))
                .contains("kpi_id", "kpi_version_id", "kpi_version_no",
                        "kpi_code", "kpi_name", "kpi_subject_type",
                        "kpi_period_type");
        var arguments = batch.arguments().getFirst();
        assertThat(arguments).hasSize(51);
        assertThat(arguments[10]).isNull();
        assertThat(arguments[11]).isNull();
        assertThat(arguments[12]).isNull();
        assertThat(arguments[37]).isEqualTo(900L);
        assertThat(arguments[38]).isEqualTo(901L);
        assertThat(arguments[39]).isEqualTo(3);
        assertThat(arguments[40]).isEqualTo("revenue");
        assertThat(arguments[41]).isEqualTo("Revenue");
        assertThat(arguments[42]).isEqualTo("ROLE");
        assertThat(arguments[43]).isEqualTo("MONTH");
        assertThat(arguments[44]).isEqualTo(0);
        assertThat(arguments[45]).isNull();
        assertThat(arguments[46]).isEqualTo("STANDARD");
    }

    @Test
    void jsonSnapshotsRoundTripLegacyAndKpiWidgetShapes() throws Exception {
        var json = new ObjectMapper();
        var legacy = new DashboardWidgetDraft(
                "records", DashboardWidgetType.DATA_LIST, "Records",
                501, 10, new DashboardGrid(0, 0, 6, 3));
        var kpi = DashboardWidgetDraft.kpiValue(
                "revenueKpi", "Revenue KPI", 900,
                new DashboardGrid(6, 0, 6, 3));
        var draft = new DashboardDraft(List.of(legacy, kpi));

        var restored = json.readValue(
                json.writeValueAsString(draft), DashboardDraft.class);

        assertThat(restored).isEqualTo(draft);
        assertThat(restored.widgets().getFirst().dataSourceId())
                .isEqualTo(501L);
        assertThat(restored.widgets().getFirst().kpiId()).isNull();
        assertThat(restored.widgets().get(1).dataSourceId()).isNull();
        assertThat(restored.widgets().get(1).kpiId()).isEqualTo(900L);

        var published = DashboardVersionWidget.kpiValue(
                304, 204, 100, 10, 20, 0, "revenueKpi", "Revenue KPI",
                900, 901, 3, "revenue", "Revenue",
                com.unique.examine.module.kpi.domain.KpiSubjectType.ROLE,
                com.unique.examine.module.kpi.domain.KpiPeriodType.MONTH,
                new DashboardGrid(0, 0, 4, 2));
        assertThat(json.readValue(json.writeValueAsString(published),
                DashboardVersionWidget.class)).isEqualTo(published);
    }

    private static JdbcDashboardRepository repository(
            RecordingJdbcTemplate jdbc
    ) {
        var sequence = new AtomicLong(900);
        var ids = new IdService() {
            @Override
            public long nextId() {
                return sequence.incrementAndGet();
            }
        };
        return new JdbcDashboardRepository(
                jdbc, new ObjectMapper(), ids, transactions());
    }

    private static SystemDashboard root() {
        return SystemDashboard.create(
                100, 10, 20, "home", DashboardPlacement.SYSTEM_HOME,
                "Home", null, draft(),
                Instant.parse("2026-08-01T00:00:00Z"));
    }

    private static DashboardDraft draft() {
        return new DashboardDraft(List.of(
                new DashboardWidgetDraft(
                        "openOrders", DashboardWidgetType.STAT_COUNT,
                        "Open orders", 501, null,
                        new DashboardGrid(0, 0, 4, 2)),
                new DashboardWidgetDraft(
                        "recentOrders", DashboardWidgetType.DATA_LIST,
                        "Recent orders", 502, 10,
                        new DashboardGrid(4, 0, 8, 4))));
    }

    private static DashboardVersion version(
            SystemDashboard root,
            long sourceDraftVersion
    ) {
        var publishedAt = root.updatedAt().plusSeconds(1);
        return new DashboardVersion(
                200, root.id(), root.systemId(), root.tenantId(), 1,
                sourceDraftVersion, root.code(), root.placement(), root.name(),
                root.description(), List.of(
                new DashboardVersionWidget(
                        301, 200, root.id(), root.systemId(), root.tenantId(),
                        0, "openOrders", DashboardWidgetType.STAT_COUNT,
                        "Open orders", 501, "open_orders", 601, 3,
                        "orders", "701", null,
                        new DashboardGrid(0, 0, 4, 2)),
                new DashboardVersionWidget(
                        302, 200, root.id(), root.systemId(), root.tenantId(),
                        1, "recentOrders", DashboardWidgetType.DATA_LIST,
                        "Recent orders", 502, "recent_orders", 602, 4,
                        "orders", "701", 10,
                        new DashboardGrid(4, 0, 8, 4))),
                "a".repeat(64), 30, publishedAt);
    }

    private static PlatformTransactionManager transactions() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(
                    TransactionDefinition definition
            ) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<Call> updates = new ArrayList<>();
        private final List<Call> queries = new ArrayList<>();
        private final List<BatchCall> batches = new ArrayList<>();
        private int affected = 1;

        @Override
        public int update(String sql, Object... arguments) {
            updates.add(new Call(sql, arguments));
            return affected;
        }

        @Override
        public int[] batchUpdate(String sql, List<Object[]> batchArgs) {
            batches.add(new BatchCall(sql, batchArgs));
            return batchArgs.stream().mapToInt(ignored -> affected).toArray();
        }

        @Override
        public <T> List<T> query(
                String sql,
                org.springframework.jdbc.core.RowMapper<T> rowMapper,
                Object... arguments
        ) {
            queries.add(new Call(sql, arguments));
            return List.of();
        }
    }

    private record Call(String sql, Object[] arguments) {
    }

    private record BatchCall(String sql, List<Object[]> arguments) {
    }
}
