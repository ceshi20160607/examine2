package com.unique.examine.module.dashboard.service;

import com.unique.examine.module.dashboard.domain.DashboardActor;
import com.unique.examine.module.dashboard.domain.DashboardDraft;
import com.unique.examine.module.dashboard.domain.DashboardException;
import com.unique.examine.module.dashboard.domain.DashboardGrid;
import com.unique.examine.module.dashboard.domain.DashboardPlacement;
import com.unique.examine.module.dashboard.domain.DashboardVersion;
import com.unique.examine.module.dashboard.domain.DashboardWidgetDraft;
import com.unique.examine.module.dashboard.domain.DashboardWidgetType;
import com.unique.examine.module.dashboard.domain.SystemDashboard;
import com.unique.examine.module.dashboard.port.DashboardRepository;
import com.unique.examine.module.dashboard.port.DashboardKpiCatalog;
import com.unique.examine.module.dashboard.port.DashboardSourceCatalog;
import com.unique.examine.module.dashboard.port.DashboardSourceCatalog.SourceField;
import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;
import com.unique.examine.module.datasource.statistics.domain.StatisticsGrain;
import com.unique.examine.module.datasource.statistics.domain.StatisticsRequest;
import com.unique.examine.module.kpi.domain.KpiPeriodType;
import com.unique.examine.module.kpi.domain.KpiSubjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardServiceTest {
    private static final DashboardActor ACTOR =
            new DashboardActor(10, 20, 30);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-01T02:03:04Z"), ZoneOffset.UTC);

    private InMemoryRepository repository;
    private MutableSourceCatalog sources;
    private MutableKpiCatalog kpis;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        sources = new MutableSourceCatalog();
        kpis = new MutableKpiCatalog();
        sources.addSource(10, 20, 100, 501, 1);
        service = new DashboardService(repository, sources, kpis, CLOCK);
    }

    @Test
    void checkIsReadOnlyAndReturnsStableLayoutAndSourceBlockers() {
        var root = create(new DashboardDraft(List.of(
                new DashboardWidgetDraft(
                        "summary", DashboardWidgetType.STAT_COUNT,
                        "Summary", 100, 1,
                        new DashboardGrid(0, 0, 6, 2)),
                new DashboardWidgetDraft(
                        "summary", DashboardWidgetType.DATA_LIST,
                        "List", 100, 21,
                        new DashboardGrid(4, 0, 6, 2)),
                new DashboardWidgetDraft(
                        "missing", DashboardWidgetType.DATA_LIST,
                        "Missing", 999, 2,
                        new DashboardGrid(11, 99, 2, 2)))));

        var report = service.check(ACTOR, root.id());

        assertThat(report.issues()).extracting(issue -> issue.code())
                .containsExactly(
                        "STAT_COUNT_ROW_LIMIT_FORBIDDEN",
                        "WIDGET_CODE_DUPLICATE",
                        "DATA_LIST_ROW_LIMIT_INVALID",
                        "WIDGET_GRID_OVERLAP",
                        "WIDGET_GRID_OUT_OF_BOUNDS",
                        "WIDGET_SOURCE_NOT_FOUND");
        assertThat(report.blockerCount()).isEqualTo(6);
        assertThat(report.warningCount()).isZero();
        assertThat(report.publishable()).isFalse();
        assertThat(repository.saveCount).isZero();
        assertThat(repository.publishCount).isZero();
    }

    @Test
    void publishPinsSourceVersionReplaysAndKeepsOlderWidgetsImmutable() {
        var root = create(validDraft("Records"));

        var first = service.publish(ACTOR, root.id(), 1);
        var replay = service.publish(ACTOR, root.id(), 1);

        assertThat(replay).isEqualTo(first);
        assertThat(first.sourceDraftVersion()).isEqualTo(1);
        assertThat(first.fingerprint()).isEqualTo(
                "2c11f108512bed1016e72a195e4c1307c69504c69d401a245f36551daada5c3b");
        assertThat(repository.publishCount).isEqualTo(1);
        assertThat(first.widgets()).extracting(widget -> widget.code())
                .containsExactly("count", "records");
        assertThat(first.widgets()).extracting(
                        widget -> widget.dataSourceVersionId())
                .containsExactly(501L, 501L);

        sources.addSource(10, 20, 100, 502, 2);
        var second = service.publish(ACTOR, root.id(), 1);

        assertThat(second.versionNumber()).isEqualTo(2);
        assertThat(second.sourceDraftVersion()).isEqualTo(1);
        assertThat(second.widgets()).extracting(
                        widget -> widget.dataSourceVersionId())
                .containsOnly(502L);
        assertThat(first.widgets()).extracting(
                        widget -> widget.dataSourceVersionId())
                .containsOnly(501L);

        var revised = service.revise(
                ACTOR, root.id(), 1, "Home", "changed",
                validDraft("Latest records"));
        var third = service.publish(
                ACTOR, root.id(), revised.draftVersion());

        assertThat(third.versionNumber()).isEqualTo(3);
        assertThat(third.sourceDraftVersion()).isEqualTo(2);
        assertThat(third.widgets().get(1).title())
                .isEqualTo("Latest records");
        assertThat(first.widgets().get(1).title()).isEqualTo("Records");
        assertThat(service.versions(ACTOR, root.id()))
                .extracting(DashboardVersion::versionNumber)
                .containsExactly(3, 2, 1);
        assertThat(service.version(ACTOR, root.id(), 1)).isEqualTo(first);
        assertThat(service.activeHome(ACTOR).version()).isEqualTo(third);
    }

    @Test
    void restoreCreatesDraftThenExplicitPublishMovesRuntime() {
        var root = create(validDraft("Records"));
        var first = service.publish(ACTOR, root.id(), 1);
        var revised = service.saveDraft(ACTOR, root.id(), 1, "Home",
                null, validDraft("Changed"));
        var second = service.publish(
                ACTOR, root.id(), revised.draftVersion());

        var restored = service.restoreVersion(ACTOR, root.id(),
                first.versionNumber(), revised.draftVersion());

        assertThat(restored.draftVersion()).isEqualTo(3);
                assertThat(restored.draft().widgets()).extracting(
                        DashboardWidgetDraft::title)
                .containsExactly("Total", "Records");
        assertThat(restored.activeVersionId()).isEqualTo(second.id());
        assertThat(service.activeHome(ACTOR).version()).isEqualTo(second);
        assertThat(service.check(ACTOR, root.id()).publishable()).isTrue();

        var republished = service.publish(
                ACTOR, root.id(), restored.draftVersion());
        assertThat(republished.versionNumber()).isEqualTo(3);
        assertThat(republished.widgets()).extracting(
                        widget -> widget.title())
                .containsExactly("Total", "Records");
        assertThat(service.version(ACTOR, root.id(), 1)).isEqualTo(first);
    }

    @Test
    void compareAndSwapUniqueHomeAndTenantIsolationAreEnforced() {
        var root = create(validDraft("Records"));
        var otherTenant = new DashboardActor(10, 21, 31);

        assertThatThrownBy(() -> service.revise(
                ACTOR, root.id(), 9, "Changed", null,
                validDraft("Changed")))
                .isInstanceOf(DashboardException.class)
                .satisfies(error -> assertThat(
                        ((DashboardException) error).code())
                        .isEqualTo("DASHBOARD_VERSION_CONFLICT"));
        assertThatThrownBy(() -> service.create(
                ACTOR, "another", DashboardPlacement.SYSTEM_HOME,
                "Another", null, DashboardDraft.empty()))
                .isInstanceOf(DashboardException.class)
                .satisfies(error -> assertThat(
                        ((DashboardException) error).code())
                        .isEqualTo("DASHBOARD_PLACEMENT_CONFLICT"));
        assertThatThrownBy(() -> service.detail(otherTenant, root.id()))
                .isInstanceOf(DashboardException.class)
                .satisfies(error -> assertThat(
                        ((DashboardException) error).code())
                        .isEqualTo("DASHBOARD_NOT_FOUND"));

        var other = service.create(
                otherTenant, "home", DashboardPlacement.SYSTEM_HOME,
                "Other home", null, DashboardDraft.empty());
        assertThat(other.tenantId()).isEqualTo(21);
        assertThat(service.list(ACTOR)).containsExactly(root);
    }

    @Test
    void emptyAndUnavailableSourcesBlockPublication() {
        var empty = create(DashboardDraft.empty());

        assertThat(service.check(ACTOR, empty.id()).issues())
                .extracting(issue -> issue.code())
                .containsExactly("WIDGETS_REQUIRED");
        assertThatThrownBy(() -> service.publish(ACTOR, empty.id(), 1))
                .isInstanceOf(DashboardException.class)
                .satisfies(error -> assertThat(
                        ((DashboardException) error).code())
                        .isEqualTo("DASHBOARD_CHECK_BLOCKED"));
    }

    @Test
    void sourceFailureKindsAreReportedWithoutCrossTenantLeakage() {
        sources.roots.put(new MutableSourceCatalog.SourceKey(10, 20, 200),
                new DashboardSourceCatalog.SourceRoot(
                        200, 10, 20, "unpublished", null, null));
        sources.roots.put(new MutableSourceCatalog.SourceKey(10, 20, 300),
                new DashboardSourceCatalog.SourceRoot(
                        300, 10, 20, "missing_version", 601L, 1));
        sources.roots.put(new MutableSourceCatalog.SourceKey(10, 20, 400),
                new DashboardSourceCatalog.SourceRoot(
                        400, 10, 20, "unavailable", 701L, 1));
        sources.versions.put(
                new MutableSourceCatalog.VersionKey(10, 20, 400, 701),
                new DashboardSourceCatalog.SourceVersion(
                        701, 400, 10, 20, 1, "unavailable",
                        "module", "schema", false, 0));
        sources.addSource(10, 21, 999, 801, 1);
        var root = create(new DashboardDraft(List.of(
                listWidget("unpublished", 200, 0),
                listWidget("missingVersion", 300, 2),
                listWidget("unavailable", 400, 4),
                listWidget("foreign", 999, 6))));

        assertThat(service.check(ACTOR, root.id()).issues())
                .extracting(issue -> issue.code())
                .containsExactly(
                        "WIDGET_SOURCE_UNPUBLISHED",
                        "WIDGET_SOURCE_VERSION_NOT_FOUND",
                        "WIDGET_SOURCE_MODULE_UNAVAILABLE",
                        "WIDGET_SOURCE_OUTPUT_UNREADABLE",
                        "WIDGET_SOURCE_NOT_FOUND");
    }

    @Test
    void statisticsWidgetsValidateCapabilitiesAndPublishExactRequests() {
        var scalar = new StatisticsRequest(
                StatisticsAggregation.SUM, "amount", null, null);
        var grouped = new StatisticsRequest(
                StatisticsAggregation.COUNT, null,
                new StatisticsRequest.Grouping("status", 10), null);
        var trend = new StatisticsRequest(
                StatisticsAggregation.COUNT, null, null,
                new StatisticsRequest.Trend(
                        "createdAt", StatisticsGrain.MONTH,
                        LocalDate.parse("2026-06-01"),
                        LocalDate.parse("2026-08-01")));
        var root = create(new DashboardDraft(List.of(
                new DashboardWidgetDraft(
                        "revenue", DashboardWidgetType.STAT_VALUE,
                        "Revenue", 100, null, scalar,
                        new DashboardGrid(0, 0, 4, 2)),
                new DashboardWidgetDraft(
                        "byStatus", DashboardWidgetType.BAR_CHART,
                        "By status", 100, null, grouped,
                        new DashboardGrid(4, 0, 4, 3)),
                new DashboardWidgetDraft(
                        "monthly", DashboardWidgetType.LINE_TREND,
                        "Monthly", 100, null, trend,
                        new DashboardGrid(8, 0, 4, 3)))));

        assertThat(service.check(ACTOR, root.id()).issues()).isEmpty();
        var version = service.publish(ACTOR, root.id(), 1);

        assertThat(version.widgets()).extracting(
                        widget -> widget.statistics().request())
                .containsExactly(scalar, grouped, trend);
        assertThat(version.widgets().getFirst().statistics().measure())
                .satisfies(field -> {
                    assertThat(field.logicalFieldId()).isPositive();
                    assertThat(field.code()).isEqualTo("amount");
                    assertThat(field.queryType()).isNotBlank();
                });
        assertThat(version.widgets()).extracting(
                        widget -> widget.dataSourceVersionId())
                .containsOnly(501L);
    }

    @Test
    void statisticsFieldsMustBePublishedReadableAndTyped() {
        var root = create(new DashboardDraft(List.of(
                new DashboardWidgetDraft(
                        "badMeasure", DashboardWidgetType.STAT_VALUE,
                        "Bad measure", 100, null,
                        new StatisticsRequest(
                                StatisticsAggregation.SUM,
                                "title", null, null),
                        new DashboardGrid(0, 0, 4, 2)),
                new DashboardWidgetDraft(
                        "badGroup", DashboardWidgetType.PIE_CHART,
                        "Bad group", 100, null,
                        new StatisticsRequest(
                                StatisticsAggregation.COUNT, null,
                                new StatisticsRequest.Grouping("secret", 5),
                                null),
                        new DashboardGrid(4, 0, 4, 2)),
                new DashboardWidgetDraft(
                        "badTime", DashboardWidgetType.LINE_TREND,
                        "Bad time", 100, null,
                        new StatisticsRequest(
                                StatisticsAggregation.COUNT, null, null,
                                new StatisticsRequest.Trend(
                                        "title", StatisticsGrain.DAY,
                                        LocalDate.parse("2026-07-01"),
                                        LocalDate.parse("2026-07-03"))),
                        new DashboardGrid(8, 0, 4, 2)))));

        assertThat(service.check(ACTOR, root.id()).issues())
                .extracting(issue -> issue.code())
                .containsExactly(
                        "STATISTICS_MEASURE_NOT_NUMERIC",
                        "STATISTICS_GROUP_UNAVAILABLE",
                        "STATISTICS_TIME_UNAVAILABLE");
    }

    @Test
    void kpiWidgetPinsActiveVersionReplaysAndHistoryDoesNotDrift() {
        kpis.put(10, 20, 900, 901, 1, "revenue", "Revenue",
                KpiSubjectType.ROLE, KpiPeriodType.MONTH);
        var root = create(new DashboardDraft(List.of(
                DashboardWidgetDraft.kpiValue(
                        "revenueKpi", "Revenue KPI", 900,
                        new DashboardGrid(0, 0, 6, 3)))));

        assertThat(service.check(ACTOR, root.id()).issues()).isEmpty();
        var first = service.publish(ACTOR, root.id(), 1);
        var replay = service.publish(ACTOR, root.id(), 1);

        assertThat(replay).isEqualTo(first);
        assertThat(repository.publishCount).isEqualTo(1);
        assertThat(first.widgets().getFirst()).satisfies(widget -> {
            assertThat(widget.type()).isEqualTo(DashboardWidgetType.KPI_VALUE);
            assertThat(widget.kpiId()).isEqualTo(900L);
            assertThat(widget.kpiVersionId()).isEqualTo(901L);
            assertThat(widget.kpiVersionNumber()).isEqualTo(1);
            assertThat(widget.kpiCode()).isEqualTo("revenue");
            assertThat(widget.kpiName()).isEqualTo("Revenue");
            assertThat(widget.kpiSubjectType()).isEqualTo(KpiSubjectType.ROLE);
            assertThat(widget.kpiPeriodType()).isEqualTo(KpiPeriodType.MONTH);
            assertThat(widget.dataSourceId()).isNull();
            assertThat(widget.statistics()).isNull();
        });

        kpis.put(10, 20, 900, 902, 2, "revenue", "Revenue v2",
                KpiSubjectType.ROLE, KpiPeriodType.QUARTER);
        var second = service.publish(ACTOR, root.id(), 1);

        assertThat(second.versionNumber()).isEqualTo(2);
        assertThat(second.fingerprint()).isNotEqualTo(first.fingerprint());
        assertThat(second.widgets().getFirst().kpiVersionId()).isEqualTo(902L);
        assertThat(second.widgets().getFirst().kpiPeriodType())
                .isEqualTo(KpiPeriodType.QUARTER);
        assertThat(first.widgets().getFirst().kpiVersionId()).isEqualTo(901L);
        assertThat(first.widgets().getFirst().kpiName()).isEqualTo("Revenue");
        assertThat(service.version(ACTOR, root.id(), 1)).isEqualTo(first);
    }

    @Test
    void missingUnpublishedAndCrossTenantKpisAreHiddenByTheScopedCatalog() {
        kpis.put(10, 21, 900, 901, 1, "foreign", "Foreign",
                KpiSubjectType.MEMBER, KpiPeriodType.YEAR);
        var root = create(new DashboardDraft(List.of(
                DashboardWidgetDraft.kpiValue(
                        "hidden", "Hidden", 900,
                        new DashboardGrid(0, 0, 4, 2)))));

        assertThat(service.check(ACTOR, root.id()).issues())
                .extracting(issue -> issue.code())
                .containsExactly("WIDGET_KPI_UNAVAILABLE");
        assertThatThrownBy(() -> service.publish(ACTOR, root.id(), 1))
                .isInstanceOfSatisfying(DashboardException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("DASHBOARD_CHECK_BLOCKED"));
    }

    @Test
    void kpiDraftRejectsEveryDataSourceOwnedSetting() {
        var grid = new DashboardGrid(0, 0, 4, 2);

        assertThatThrownBy(() -> new DashboardWidgetDraft(
                "mixed", DashboardWidgetType.KPI_VALUE, "Mixed",
                100L, 900L, null, null, grid))
                .isInstanceOf(DashboardException.class);
        assertThatThrownBy(() -> new DashboardWidgetDraft(
                "limited", DashboardWidgetType.KPI_VALUE, "Limited",
                null, 900L, 10, null, grid))
                .isInstanceOf(DashboardException.class);
        assertThatThrownBy(() -> new DashboardWidgetDraft(
                "statistics", DashboardWidgetType.KPI_VALUE, "Statistics",
                null, 900L, null, new StatisticsRequest(
                StatisticsAggregation.COUNT, null, null, null), grid))
                .isInstanceOf(DashboardException.class);
    }

    private SystemDashboard create(DashboardDraft draft) {
        return service.create(
                ACTOR, "home", DashboardPlacement.SYSTEM_HOME,
                "Home", null, draft);
    }

    private static DashboardDraft validDraft(String listTitle) {
        return new DashboardDraft(List.of(
                new DashboardWidgetDraft(
                        "count", DashboardWidgetType.STAT_COUNT,
                        "Total", 100, null,
                        new DashboardGrid(0, 0, 4, 2)),
                new DashboardWidgetDraft(
                        "records", DashboardWidgetType.DATA_LIST,
                        listTitle, 100, 10,
                        new DashboardGrid(4, 0, 8, 4))));
    }

    private static DashboardWidgetDraft listWidget(
            String code,
            long sourceId,
            int y
    ) {
        return new DashboardWidgetDraft(
                code, DashboardWidgetType.DATA_LIST, code,
                sourceId, 10, new DashboardGrid(0, y, 6, 2));
    }

    private static final class MutableSourceCatalog
            implements DashboardSourceCatalog {
        private final Map<SourceKey, SourceRoot> roots = new HashMap<>();
        private final Map<VersionKey, SourceVersion> versions = new HashMap<>();

        private void addSource(
                long systemId,
                long tenantId,
                long sourceId,
                long versionId,
                int versionNumber
        ) {
            roots.put(new SourceKey(systemId, tenantId, sourceId),
                    new SourceRoot(sourceId, systemId, tenantId,
                            "orders", versionId, versionNumber));
            versions.put(new VersionKey(
                            systemId, tenantId, sourceId, versionId),
                    new SourceVersion(
                            versionId, sourceId, systemId, tenantId,
                            versionNumber, "orders", "orders_module",
                            "schema-" + versionNumber, true, 4,
                            List.of(
                                    new SourceField(
                                            "amount", "NUMBER", true,
                                            true, false, true),
                                    new SourceField(
                                            "status", "STATUS", true,
                                            false, false, true),
                                    new SourceField(
                                            "createdAt", "DATETIME", true,
                                            false, true, true),
                                    new SourceField(
                                            "title", "TEXT", true,
                                            false, false, true),
                                    new SourceField(
                                            "secret", "SECRET", false,
                                            false, false, false))));
        }

        @Override
        public Optional<SourceRoot> source(
                long systemId,
                long tenantId,
                long dataSourceId
        ) {
            return Optional.ofNullable(roots.get(
                    new SourceKey(systemId, tenantId, dataSourceId)));
        }

        @Override
        public Optional<SourceVersion> version(
                long systemId,
                long tenantId,
                long dataSourceId,
                long dataSourceVersionId
        ) {
            return Optional.ofNullable(versions.get(new VersionKey(
                    systemId, tenantId, dataSourceId, dataSourceVersionId)));
        }

        private record SourceKey(
                long systemId,
                long tenantId,
                long sourceId
        ) {
        }

        private record VersionKey(
                long systemId,
                long tenantId,
                long sourceId,
                long versionId
        ) {
        }
    }

    private static final class MutableKpiCatalog
            implements DashboardKpiCatalog {
        private final Map<Key, ActiveKpiVersion> versions = new HashMap<>();

        private void put(
                long systemId,
                long tenantId,
                long kpiId,
                long versionId,
                int versionNumber,
                String code,
                String name,
                KpiSubjectType subjectType,
                KpiPeriodType periodType
        ) {
            versions.put(new Key(systemId, tenantId, kpiId),
                    new ActiveKpiVersion(kpiId, systemId, tenantId,
                            versionId, versionNumber, code, name,
                            subjectType, periodType));
        }

        @Override
        public Optional<ActiveKpiVersion> activeVersion(
                long systemId,
                long tenantId,
                long kpiId
        ) {
            return Optional.ofNullable(versions.get(
                    new Key(systemId, tenantId, kpiId)));
        }

        private record Key(long systemId, long tenantId, long kpiId) {
        }
    }

    private static final class InMemoryRepository
            implements DashboardRepository {
        private long dashboardSequence = 100;
        private long versionSequence = 500;
        private long widgetSequence = 800;
        private int saveCount;
        private int publishCount;
        private final Map<Key, SystemDashboard> roots = new LinkedHashMap<>();
        private final Map<Long, List<DashboardVersion>> versions =
                new HashMap<>();

        @Override
        public long nextDashboardId() {
            return ++dashboardSequence;
        }

        @Override
        public long nextVersionId() {
            return ++versionSequence;
        }

        @Override
        public long nextVersionWidgetId() {
            return ++widgetSequence;
        }

        @Override
        public Optional<SystemDashboard> findById(
                long systemId,
                long tenantId,
                long dashboardId
        ) {
            return Optional.ofNullable(
                    roots.get(new Key(systemId, tenantId, dashboardId)));
        }

        @Override
        public Optional<SystemDashboard> findByCode(
                long systemId,
                long tenantId,
                String code
        ) {
            return roots.values().stream()
                    .filter(root -> root.systemId() == systemId
                            && root.tenantId() == tenantId
                            && root.code().equals(code))
                    .findFirst();
        }

        @Override
        public Optional<SystemDashboard> findByPlacement(
                long systemId,
                long tenantId,
                DashboardPlacement placement
        ) {
            return roots.values().stream()
                    .filter(root -> root.systemId() == systemId
                            && root.tenantId() == tenantId
                            && root.placement() == placement)
                    .findFirst();
        }

        @Override
        public List<SystemDashboard> findAll(
                long systemId,
                long tenantId
        ) {
            return roots.values().stream()
                    .filter(root -> root.systemId() == systemId
                            && root.tenantId() == tenantId)
                    .toList();
        }

        @Override
        public SystemDashboard insert(SystemDashboard root) {
            if (findByPlacement(root.systemId(), root.tenantId(),
                    root.placement()).isPresent()) {
                throw new DashboardException(
                        "DASHBOARD_PLACEMENT_CONFLICT", "Duplicate placement");
            }
            roots.put(key(root), root);
            return root;
        }

        @Override
        public SystemDashboard saveDraft(
                SystemDashboard expected,
                SystemDashboard revised
        ) {
            if (!roots.replace(key(expected), expected, revised)) {
                throw new DashboardException(
                        "DASHBOARD_VERSION_CONFLICT", "Stale root");
            }
            saveCount++;
            return revised;
        }

        @Override
        public DashboardVersion publish(
                SystemDashboard expected,
                SystemDashboard activated,
                DashboardVersion version
        ) {
            if (!roots.replace(key(expected), expected, activated)) {
                throw new DashboardException(
                        "DASHBOARD_VERSION_CONFLICT", "Stale root");
            }
            versions.computeIfAbsent(expected.id(), ignored ->
                    new ArrayList<>()).add(version);
            publishCount++;
            return version;
        }

        @Override
        public Optional<DashboardVersion> findActiveVersion(
                long systemId,
                long tenantId,
                long dashboardId
        ) {
            var root = findById(systemId, tenantId, dashboardId);
            if (root.isEmpty() || root.get().activeVersionNumber() == null) {
                return Optional.empty();
            }
            return findVersion(systemId, tenantId, dashboardId,
                    root.get().activeVersionNumber());
        }

        @Override
        public Optional<DashboardVersion> findVersion(
                long systemId,
                long tenantId,
                long dashboardId,
                int versionNumber
        ) {
            if (findById(systemId, tenantId, dashboardId).isEmpty()) {
                return Optional.empty();
            }
            return versions.getOrDefault(dashboardId, List.of()).stream()
                    .filter(value -> value.versionNumber() == versionNumber)
                    .findFirst();
        }

        @Override
        public List<DashboardVersion> findVersions(
                long systemId,
                long tenantId,
                long dashboardId
        ) {
            if (findById(systemId, tenantId, dashboardId).isEmpty()) {
                return List.of();
            }
            return List.copyOf(versions.getOrDefault(
                    dashboardId, List.of()));
        }

        private static Key key(SystemDashboard root) {
            return new Key(root.systemId(), root.tenantId(), root.id());
        }

        private record Key(long systemId, long tenantId, long id) {
        }
    }
}
