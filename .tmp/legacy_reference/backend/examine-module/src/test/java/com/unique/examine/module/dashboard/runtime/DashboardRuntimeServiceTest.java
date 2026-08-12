package com.unique.examine.module.dashboard.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.module.dashboard.domain.DashboardGrid;
import com.unique.examine.module.dashboard.domain.DashboardPlacement;
import com.unique.examine.module.dashboard.domain.DashboardStatisticsSnapshot;
import com.unique.examine.module.dashboard.domain.DashboardVersion;
import com.unique.examine.module.dashboard.domain.DashboardVersionWidget;
import com.unique.examine.module.dashboard.domain.DashboardWidgetType;
import com.unique.examine.module.dashboard.domain.SystemDashboard;
import com.unique.examine.module.dashboard.port.DashboardRepository;
import com.unique.examine.module.dashboard.port.DashboardSourceCatalog;
import com.unique.examine.module.dashboard.service.DashboardService;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceVersion;
import com.unique.examine.module.datasource.domain.ModuleDataSource;
import com.unique.examine.module.datasource.port.DataSourceModuleCatalog;
import com.unique.examine.module.datasource.port.DataSourceRepository;
import com.unique.examine.module.datasource.runtime.DataSourceRecordQueryGateway;
import com.unique.examine.module.datasource.runtime.DataSourceRuntimeService;
import com.unique.examine.module.datasource.service.DataSourceService;
import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;
import com.unique.examine.module.datasource.statistics.domain.StatisticsRequest;
import com.unique.examine.module.datasource.statistics.port.DataSourceStatisticsGateway;
import com.unique.examine.module.datasource.statistics.service.DataSourceStatisticsService;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardRuntimeServiceTest {
    private static final Instant NOW =
            Instant.parse("2026-08-01T03:00:00Z");

    @Test
    void readsPinnedSourceVersionAndKeepsSiblingWhenOneWidgetFails() {
        var dashboard = dashboard();
        var dashboardService = new DashboardService(
                new DashboardStore(dashboard.root(), dashboard.version()),
                emptySourceCatalog(), fixedClock());
        var dataSourceService = new DataSourceService(
                new SourceStore(sourceRoot(), sourceVersion()),
                emptyDataSourceCatalog(), fixedClock());
        var gateway = new QueryGateway();
        var service = new DashboardRuntimeService(
                dashboardService, new DataSourceRuntimeService(
                dataSourceService, gateway, new ObjectMapper()));
        var session = new RuntimeSession(
                1, 10, 30, 20L, Set.of("system.runtime.access"));

        var result = service.systemHome(session);

        assertThat(result.versionId()).isEqualTo("501");
        assertThat(result.widgets()).extracting(value -> value.status())
                .containsExactly("OK", "ERROR");
        assertThat(result.widgets().getFirst().total()).isEqualTo(7);
        assertThat(result.widgets().get(1).error().code())
                .isEqualTo("DATA_SOURCE_NOT_FOUND");
        assertThat(gateway.moduleCode).isEqualTo("work");
        assertThat(gateway.queryJson).contains("\"size\":1");
    }

    @Test
    void requiresAnActiveTenantBeforeResolvingTheHomeDashboard() {
        var dashboard = dashboard();
        var service = new DashboardRuntimeService(
                new DashboardService(
                        new DashboardStore(
                                dashboard.root(), dashboard.version()),
                        emptySourceCatalog(), fixedClock()),
                new DataSourceRuntimeService(
                        new DataSourceService(
                                new SourceStore(sourceRoot(), sourceVersion()),
                                emptyDataSourceCatalog(), fixedClock()),
                        new QueryGateway(), new ObjectMapper()));

        assertThatThrownBy(() -> service.systemHome(new RuntimeSession(
                1, 10, 30, null, Set.of("system.runtime.access"))))
                .hasMessageContaining("active tenant");
    }

    @Test
    void statisticsWidgetUsesPinnedVersionAndReturnsStringIdentityView() {
        var dashboard = statisticsDashboard();
        var dashboardService = new DashboardService(
                new DashboardStore(dashboard.root(), dashboard.version()),
                emptySourceCatalog(), fixedClock());
        var dataSourceService = new DataSourceService(
                new SourceStore(sourceRoot(), sourceVersion()),
                emptyDataSourceCatalog(), fixedClock());
        var statistics = new DataSourceStatisticsService(
                dataSourceService, new StatisticsGateway());
        var service = new DashboardRuntimeService(
                dashboardService,
                new DataSourceRuntimeService(
                        dataSourceService, new QueryGateway(),
                        new ObjectMapper()),
                statistics);

        var result = service.systemHome(new RuntimeSession(
                1, 10, 30, 20L,
                Set.of("system.runtime.access")));

        var widget = result.widgets().getFirst();
        assertThat(widget.status()).isEqualTo("OK");
        assertThat(widget.statisticsResult().value()).isEqualTo("7");
        assertThat(widget.statisticsResult().dataSourceId())
                .isEqualTo("201");
        assertThat(widget.statisticsResult().dataSourceVersionId())
                .isEqualTo("401");
        assertThat(widget.fields()).isEmpty();
        assertThat(widget.rows()).isEmpty();
    }

    @Test
    void unexpectedStatisticsFailureIsIsolatedFromSiblingWidgets() {
        var dashboard = mixedStatisticsDashboard();
        var dashboardService = new DashboardService(
                new DashboardStore(dashboard.root(), dashboard.version()),
                emptySourceCatalog(), fixedClock());
        var dataSourceService = new DataSourceService(
                new SourceStore(sourceRoot(), sourceVersion()),
                emptyDataSourceCatalog(), fixedClock());
        var service = new DashboardRuntimeService(
                dashboardService,
                new DataSourceRuntimeService(
                        dataSourceService, new QueryGateway(),
                        new ObjectMapper()),
                new DataSourceStatisticsService(
                        dataSourceService, new FailingStatisticsGateway()));

        var result = service.systemHome(new RuntimeSession(
                1, 10, 30, 20L,
                Set.of("system.runtime.access")));

        assertThat(result.widgets()).extracting(widget -> widget.status())
                .containsExactly("ERROR", "OK");
        assertThat(result.widgets().getFirst().error().code())
                .isEqualTo("DASHBOARD_WIDGET_EXECUTION_FAILED");
        assertThat(result.widgets().getFirst().error().message())
                .doesNotContain("database detail");
        assertThat(result.widgets().get(1).total()).isEqualTo(7);
    }

    private static com.unique.examine.module.dashboard.domain.PublishedDashboard
    dashboard() {
        var widgets = List.of(
                new DashboardVersionWidget(
                        801, 501, 101, 10, 20, 0, "count",
                        DashboardWidgetType.STAT_COUNT, "Total", 201,
                        "work_items", 401, 1, "work", "schema-1", null,
                        new DashboardGrid(0, 0, 4, 2)),
                new DashboardVersionWidget(
                        802, 501, 101, 10, 20, 1, "failed",
                        DashboardWidgetType.DATA_LIST, "Failed", 999,
                        "missing", 601, 1, "work", "schema-1", 10,
                        new DashboardGrid(4, 0, 8, 4)));
        var version = new DashboardVersion(
                501, 101, 10, 20, 1, 1, "system_home",
                DashboardPlacement.SYSTEM_HOME, "Home", null, widgets,
                "a".repeat(64), 30, NOW);
        var root = new SystemDashboard(
                101, 10, 20, "system_home",
                DashboardPlacement.SYSTEM_HOME, "Home", null,
                new com.unique.examine.module.dashboard.domain.DashboardDraft(
                        List.of()),
                1, 501L, 1, NOW, NOW, 2);
        return new com.unique.examine.module.dashboard.domain.PublishedDashboard(
                root, version);
    }

    private static com.unique.examine.module.dashboard.domain.PublishedDashboard
    statisticsDashboard() {
        var request = new StatisticsRequest(
                StatisticsAggregation.COUNT, null, null, null);
        var widget = new DashboardVersionWidget(
                803, 502, 102, 10, 20, 0, "metric",
                DashboardWidgetType.STAT_VALUE, "Metric", 201,
                "work_items", 401, 1, "work", "schema-1", null,
                new DashboardStatisticsSnapshot(request, null, null, null),
                new DashboardGrid(0, 0, 4, 2));
        var version = new DashboardVersion(
                502, 102, 10, 20, 1, 1, "system_home",
                DashboardPlacement.SYSTEM_HOME, "Statistics home", null,
                List.of(widget), "c".repeat(64), 30, NOW);
        var root = new SystemDashboard(
                102, 10, 20, "system_home",
                DashboardPlacement.SYSTEM_HOME, "Statistics home", null,
                new com.unique.examine.module.dashboard.domain.DashboardDraft(
                        List.of()),
                1, 502L, 1, NOW, NOW, 2);
        return new com.unique.examine.module.dashboard.domain.PublishedDashboard(
                root, version);
    }

    private static com.unique.examine.module.dashboard.domain.PublishedDashboard
    mixedStatisticsDashboard() {
        var request = new StatisticsRequest(
                StatisticsAggregation.COUNT, null, null, null);
        var widgets = List.of(
                new DashboardVersionWidget(
                        804, 503, 103, 10, 20, 0, "metric",
                        DashboardWidgetType.STAT_VALUE, "Metric", 201,
                        "work_items", 401, 1, "work", "schema-1", null,
                        new DashboardStatisticsSnapshot(
                                request, null, null, null),
                        new DashboardGrid(0, 0, 4, 2)),
                new DashboardVersionWidget(
                        805, 503, 103, 10, 20, 1, "count",
                        DashboardWidgetType.STAT_COUNT, "Count", 201,
                        "work_items", 401, 1, "work", "schema-1", null,
                        new DashboardGrid(4, 0, 4, 2)));
        var version = new DashboardVersion(
                503, 103, 10, 20, 1, 1, "system_home",
                DashboardPlacement.SYSTEM_HOME, "Mixed home", null,
                widgets, "e".repeat(64), 30, NOW);
        var root = new SystemDashboard(
                103, 10, 20, "system_home",
                DashboardPlacement.SYSTEM_HOME, "Mixed home", null,
                new com.unique.examine.module.dashboard.domain.DashboardDraft(
                        List.of()),
                1, 503L, 1, NOW, NOW, 2);
        return new com.unique.examine.module.dashboard.domain.PublishedDashboard(
                root, version);
    }

    private static ModuleDataSource sourceRoot() {
        return new ModuleDataSource(
                201, 10, 20, "work_items", 100, "Work items", null,
                sourceVersion().snapshot(), 2, 402L, 2,
                NOW, NOW, 3);
    }

    private static DataSourceVersion sourceVersion() {
        return new DataSourceVersion(
                401, 201, 10, 20, 1, "work_items", 100, "work",
                "schema-1", "Work items v1", null,
                new DataSourceDraft(
                        List.of(new DataSourceDraft.OutputField("title")),
                        List.of(), null, null),
                "b".repeat(64), 30, NOW);
    }

    private static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private static DashboardSourceCatalog emptySourceCatalog() {
        return new DashboardSourceCatalog() {
            @Override
            public Optional<SourceRoot> source(
                    long systemId, long tenantId, long dataSourceId) {
                return Optional.empty();
            }

            @Override
            public Optional<SourceVersion> version(
                    long systemId, long tenantId, long dataSourceId,
                    long dataSourceVersionId) {
                return Optional.empty();
            }
        };
    }

    private static DataSourceModuleCatalog emptyDataSourceCatalog() {
        return new DataSourceModuleCatalog() {
            @Override
            public List<PublishedModule> publishedModules(
                    long systemId, long tenantId) {
                return List.of();
            }

            @Override
            public Optional<PublishedModule> publishedModule(
                    long systemId, long tenantId, long moduleId) {
                return Optional.empty();
            }
        };
    }

    private static final class QueryGateway
            implements DataSourceRecordQueryGateway {
        private String moduleCode;
        private String queryJson;

        @Override
        public RecordRuntimeViews.RecordSchema schema(
                RuntimeSession session, String moduleCode) {
            this.moduleCode = moduleCode;
            return new RecordRuntimeViews.RecordSchema(
                    "schema-1", "600", "100", "checksum", "READY", null,
                    1, List.of(new RecordRuntimeViews.FieldCapability(
                    "title", "Title", "title", "TEXT", "OPTIONAL",
                    true, false, true, true, false, List.of("EQ"),
                    true, true, true, List.of(),
                    new ObjectMapper().createObjectNode())),
                    List.of(), new RecordRuntimeViews.QueryLimits(50, 200, 3));
        }

        @Override
        public RecordRuntimeViews.RecordPage query(
                RuntimeSession session,
                String moduleCode,
                String queryJson
        ) {
            this.moduleCode = moduleCode;
            this.queryJson = queryJson;
            return new RecordRuntimeViews.RecordPage(
                    List.of(), 1, 1, 7, "hash", "snapshot", List.of());
        }
    }

    private static final class StatisticsGateway
            implements DataSourceStatisticsGateway {
        @Override
        public SourceCapabilities capabilities(
                RuntimeSession session,
                com.unique.examine.module.datasource.domain.DataSourcePublication
                        publication
        ) {
            return new SourceCapabilities(
                    100, "work", "schema-1", List.of());
        }

        @Override
        public RawStatistics execute(
                RuntimeSession session,
                com.unique.examine.module.datasource.domain.DataSourcePublication
                        publication,
                StatisticsPlan plan
        ) {
            return new RawStatistics(
                    "d".repeat(64), null, 7,
                    List.of(), List.of(), 0, false);
        }
    }

    private static final class FailingStatisticsGateway
            implements DataSourceStatisticsGateway {
        private int calls;

        @Override
        public SourceCapabilities capabilities(
                RuntimeSession session,
                com.unique.examine.module.datasource.domain.DataSourcePublication
                        publication
        ) {
            return new SourceCapabilities(
                    100, "work", "schema-1", List.of());
        }

        @Override
        public RawStatistics execute(
                RuntimeSession session,
                com.unique.examine.module.datasource.domain.DataSourcePublication
                        publication,
                StatisticsPlan plan
        ) {
            if (calls++ == 0) {
                throw new IllegalStateException(
                        "database detail must stay hidden");
            }
            return new RawStatistics(
                    "e".repeat(64), null, 7,
                    List.of(), List.of(), 0, false);
        }
    }

    private record DashboardStore(
            SystemDashboard root,
            DashboardVersion version
    ) implements DashboardRepository {
        @Override public long nextDashboardId() { throw unsupported(); }
        @Override public long nextVersionId() { throw unsupported(); }
        @Override public long nextVersionWidgetId() { throw unsupported(); }
        @Override public Optional<SystemDashboard> findById(
                long systemId, long tenantId, long dashboardId) {
            return scoped(systemId, tenantId) && root.id() == dashboardId
                    ? Optional.of(root) : Optional.empty();
        }
        @Override public Optional<SystemDashboard> findByCode(
                long systemId, long tenantId, String code) {
            return scoped(systemId, tenantId) && root.code().equals(code)
                    ? Optional.of(root) : Optional.empty();
        }
        @Override public Optional<SystemDashboard> findByPlacement(
                long systemId, long tenantId, DashboardPlacement placement) {
            return scoped(systemId, tenantId) && root.placement() == placement
                    ? Optional.of(root) : Optional.empty();
        }
        @Override public List<SystemDashboard> findAll(
                long systemId, long tenantId) { return List.of(); }
        @Override public SystemDashboard insert(SystemDashboard root) {
            throw unsupported();
        }
        @Override public SystemDashboard saveDraft(
                SystemDashboard expected, SystemDashboard revised) {
            throw unsupported();
        }
        @Override public DashboardVersion publish(
                SystemDashboard expected, SystemDashboard activated,
                DashboardVersion version) { throw unsupported(); }
        @Override public Optional<DashboardVersion> findActiveVersion(
                long systemId, long tenantId, long dashboardId) {
            return scoped(systemId, tenantId) && root.id() == dashboardId
                    ? Optional.of(version) : Optional.empty();
        }
        @Override public Optional<DashboardVersion> findVersion(
                long systemId, long tenantId, long dashboardId,
                int versionNumber) { return Optional.empty(); }
        @Override public List<DashboardVersion> findVersions(
                long systemId, long tenantId, long dashboardId) {
            return List.of();
        }
        private boolean scoped(long systemId, long tenantId) {
            return root.systemId() == systemId && root.tenantId() == tenantId;
        }
    }

    private record SourceStore(
            ModuleDataSource root,
            DataSourceVersion version
    ) implements DataSourceRepository {
        @Override public long nextDataSourceId() { throw unsupported(); }
        @Override public long nextVersionId() { throw unsupported(); }
        @Override public Optional<ModuleDataSource> findById(
                long systemId, long tenantId, long dataSourceId) {
            return scoped(systemId, tenantId) && root.id() == dataSourceId
                    ? Optional.of(root) : Optional.empty();
        }
        @Override public Optional<ModuleDataSource> findByCode(
                long systemId, long tenantId, String code) {
            return Optional.empty();
        }
        @Override public List<ModuleDataSource> findAll(
                long systemId, long tenantId) { return List.of(); }
        @Override public ModuleDataSource insert(ModuleDataSource root) {
            throw unsupported();
        }
        @Override public ModuleDataSource saveDraft(
                ModuleDataSource expected, ModuleDataSource revised) {
            throw unsupported();
        }
        @Override public DataSourceVersion publish(
                ModuleDataSource expected, ModuleDataSource activated,
                DataSourceVersion version) { throw unsupported(); }
        @Override public Optional<DataSourceVersion> findActiveVersion(
                long systemId, long tenantId, long dataSourceId) {
            return Optional.empty();
        }
        @Override public Optional<DataSourceVersion> findVersion(
                long systemId, long tenantId, long dataSourceId,
                int versionNumber) { return Optional.empty(); }
        @Override public Optional<DataSourceVersion> findVersionById(
                long systemId, long tenantId, long dataSourceId,
                long versionId) {
            return scoped(systemId, tenantId)
                    && root.id() == dataSourceId && version.id() == versionId
                    ? Optional.of(version) : Optional.empty();
        }
        @Override public List<DataSourceVersion> findVersions(
                long systemId, long tenantId, long dataSourceId) {
            return List.of();
        }
        private boolean scoped(long systemId, long tenantId) {
            return root.systemId() == systemId && root.tenantId() == tenantId;
        }
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("not used by this test");
    }
}
