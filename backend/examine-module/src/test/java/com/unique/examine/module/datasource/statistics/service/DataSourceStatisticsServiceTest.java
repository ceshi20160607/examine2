package com.unique.examine.module.datasource.statistics.service;

import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.domain.DataSourcePublication;
import com.unique.examine.module.datasource.domain.DataSourceVersion;
import com.unique.examine.module.datasource.domain.ModuleDataSource;
import com.unique.examine.module.datasource.port.DataSourceModuleCatalog;
import com.unique.examine.module.datasource.port.DataSourceRepository;
import com.unique.examine.module.datasource.service.DataSourceService;
import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;
import com.unique.examine.module.datasource.statistics.domain.StatisticsException;
import com.unique.examine.module.datasource.statistics.domain.StatisticsFieldPins;
import com.unique.examine.module.datasource.statistics.domain.StatisticsGrain;
import com.unique.examine.module.datasource.statistics.domain.StatisticsRequest;
import com.unique.examine.module.datasource.statistics.domain.StatisticsRecordRestriction;
import com.unique.examine.module.datasource.statistics.port.DataSourceStatisticsGateway;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataSourceStatisticsServiceTest {
    private static final RuntimeSession SESSION = new RuntimeSession(
            9, 10, 30, 20L, Set.of("system.runtime.access"));

    private FixedPublicationRepository repository;
    private FakeGateway gateway;
    private DataSourceStatisticsService service;

    @BeforeEach
    void setUp() {
        repository = new FixedPublicationRepository();
        var dataSources = new DataSourceService(
                repository, new EmptyModuleCatalog(),
                Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"),
                        ZoneOffset.UTC));
        gateway = new FakeGateway();
        service = new DataSourceStatisticsService(dataSources, gateway);
    }

    @Test
    void capabilitiesUseExactPublicationAndPublishedOutputOrder() {
        var result = service.capabilities(SESSION, 100, 501);

        assertThat(result.dataSourceVersionId()).isEqualTo(501);
        assertThat(result.dataSourceVersionNumber()).isEqualTo(1);
        assertThat(result.schemaVersionId()).isEqualTo("schema-1");
        assertThat(result.fields()).extracting(field -> field.code())
                .containsExactly("amount", "category", "createdAt", "secret");
        assertThat(result.fields().getFirst().numeric()).isTrue();
        assertThat(gateway.lastPublication.version().id()).isEqualTo(501);
        assertThat(gateway.lastPublication.version().snapshot().fixedFilters())
                .hasSize(1);
    }

    @Test
    void groupResultCanonicalizesSortsNullLastAndReportsOverflow() {
        gateway.raw = new DataSourceStatisticsGateway.RawStatistics(
                "query-group", new BigDecimal("7.5000"), 4,
                List.of(
                        new DataSourceStatisticsGateway.RawGroupBucket(
                                null, null, true,
                                new BigDecimal("1.00"), 1),
                        new DataSourceStatisticsGateway.RawGroupBucket(
                                "b", "Beta", false,
                                new BigDecimal("2.500"), 1),
                        new DataSourceStatisticsGateway.RawGroupBucket(
                                "a", "Alpha", false,
                                new BigDecimal("4.000"), 2)),
                List.of(), 4, true);
        var request = new StatisticsRequest(
                StatisticsAggregation.SUM, "amount",
                new StatisticsRequest.Grouping("category", 3), null);

        var result = service.statistics(SESSION, 100, 501, request);

        assertThat(result.value()).isEqualTo("7.5");
        assertThat(result.groupBuckets()).extracting(bucket -> bucket.key())
                .containsExactly("a", "b", null);
        assertThat(result.groupBuckets()).extracting(bucket -> bucket.value())
                .containsExactly("4", "2.5", "1");
        assertThat(result.groupBuckets().getLast().nullBucket()).isTrue();
        assertThat(result.totalBucketCount()).isEqualTo(4);
        assertThat(result.truncated()).isTrue();
        assertThat(result.aggregateCount()).isEqualTo(4);
        assertThat(result.bucketCount()).isEqualTo(3);
        assertThat(gateway.lastPlan.group().logicalFieldId()).isEqualTo(12);
    }

    @Test
    void countTrendEmitsStableZeroBuckets() {
        gateway.raw = new DataSourceStatisticsGateway.RawStatistics(
                "query-count", new BigDecimal("2"), 2,
                List.of(),
                List.of(new DataSourceStatisticsGateway.RawTrendBucket(
                        LocalDate.parse("2026-08-01"),
                        new BigDecimal("999"), 2)),
                1, false);
        var request = trend(StatisticsAggregation.COUNT, null);

        var result = service.statistics(SESSION, 100, 501, request);

        assertThat(result.value()).isEqualTo("2");
        assertThat(result.trendBuckets()).extracting(bucket -> bucket.value())
                .containsExactly("2", "0", "0");
        assertThat(result.trendBuckets()).extracting(bucket -> bucket.empty())
                .containsExactly(false, true, true);
        assertThat(result.totalBucketCount()).isEqualTo(3);
    }

    @Test
    void averageTrendEmitsNullForEmptyBuckets() {
        gateway.raw = new DataSourceStatisticsGateway.RawStatistics(
                "query-average", new BigDecimal("1.2500"), 2,
                List.of(),
                List.of(new DataSourceStatisticsGateway.RawTrendBucket(
                        LocalDate.parse("2026-08-03"),
                        new BigDecimal("1.2500"), 2)),
                1, false);

        var result = service.statistics(
                SESSION, 100, 501,
                trend(StatisticsAggregation.AVG, "amount"));

        assertThat(result.value()).isEqualTo("1.25");
        assertThat(result.trendBuckets()).extracting(bucket -> bucket.value())
                .containsExactly(null, null, "1.25");
        assertThat(result.trendBuckets()).extracting(
                        bucket -> bucket.startInclusive())
                .containsExactly(
                        LocalDate.parse("2026-08-01"),
                        LocalDate.parse("2026-08-02"),
                        LocalDate.parse("2026-08-03"));
    }

    @Test
    void emptyRoleRestrictionProducesExactZeroOverallAndTrend() {
        gateway.raw = new DataSourceStatisticsGateway.RawStatistics(
                "query-empty-role", BigDecimal.ZERO, 0,
                List.of(), List.of(), 0, false);
        var restriction = StatisticsRecordRestriction.ownerMembers(List.of());

        var result = service.statistics(
                SESSION, 100, 501,
                trend(StatisticsAggregation.SUM, "amount"),
                null, restriction);

        assertThat(result.value()).isEqualTo("0");
        assertThat(result.matchedRecordCount()).isZero();
        assertThat(result.trendBuckets()).extracting(bucket -> bucket.value())
                .containsExactly("0", "0", "0");
        assertThat(gateway.lastRestriction).isSameAs(restriction);
    }

    @Test
    void compilerRejectsIneligibleAndUnreadablePublishedFields() {
        assertThatThrownBy(() -> service.statistics(
                SESSION, 100, 501,
                new StatisticsRequest(
                        StatisticsAggregation.SUM, "category", null, null)))
                .isInstanceOf(StatisticsException.class)
                .satisfies(error -> assertThat(
                        ((StatisticsException) error).code())
                        .isEqualTo("STATISTICS_MEASURE_INVALID"));
        assertThatThrownBy(() -> service.statistics(
                SESSION, 100, 501,
                new StatisticsRequest(
                        StatisticsAggregation.SUM, "secret", null, null)))
                .isInstanceOf(StatisticsException.class)
                .satisfies(error -> assertThat(
                        ((StatisticsException) error).code())
                        .isEqualTo("STATISTICS_FIELD_NOT_FOUND"));
        assertThat(gateway.executeCount).isZero();
    }

    @Test
    void immutableConsumerFieldPinsAreVerifiedBeforeExecution() {
        var request = new StatisticsRequest(
                StatisticsAggregation.SUM, "amount", null, null);
        var valid = new StatisticsFieldPins(
                new StatisticsFieldPins.Field(
                        11, "amount", "Amount", "NUMBER", "NUMBER"),
                null, null);

        service.statistics(SESSION, 100, 501, request, valid);
        assertThat(gateway.executeCount).isEqualTo(1);

        var stale = new StatisticsFieldPins(
                new StatisticsFieldPins.Field(
                        99, "amount", "Amount", "NUMBER", "NUMBER"),
                null, null);
        assertThatThrownBy(() -> service.statistics(
                SESSION, 100, 501, request, stale))
                .isInstanceOf(StatisticsException.class)
                .satisfies(error -> assertThat(
                        ((StatisticsException) error).code())
                        .isEqualTo("STATISTICS_SOURCE_UNAVAILABLE"));
        assertThat(gateway.executeCount).isEqualTo(1);
    }

    @Test
    void exactHistoricalPublicationAndTenantIsolationArePreserved() {
        gateway.raw = new DataSourceStatisticsGateway.RawStatistics(
                "query-history", new BigDecimal("9"), 9,
                List.of(), List.of(), 0, false);

        var result = service.statistics(
                SESSION, 100, 501,
                new StatisticsRequest(
                        StatisticsAggregation.COUNT, null, null, null));

        assertThat(result.dataSourceVersionId()).isEqualTo(501);
        assertThat(result.value()).isEqualTo("9");
        assertThat(repository.root.activeVersionId()).isEqualTo(502);
        assertThatThrownBy(() -> service.statistics(
                new RuntimeSession(9, 10, 31, 21L, Set.of()),
                100, 501,
                new StatisticsRequest(
                        StatisticsAggregation.COUNT, null, null, null)))
                .isInstanceOf(DataSourceException.class)
                .satisfies(error -> assertThat(
                        ((DataSourceException) error).code())
                        .isEqualTo("DATA_SOURCE_NOT_FOUND"));
    }

    @Test
    void negotiatesHttpPublicationBeforeExecution() {
        repository = new FixedPublicationRepository(true);
        var dataSources = new DataSourceService(
                repository, new EmptyModuleCatalog(),
                Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"),
                        ZoneOffset.UTC));
        gateway = new FakeGateway();
        service = new DataSourceStatisticsService(dataSources, gateway);

        var capabilities = service.capabilities(SESSION, 100, 501);
        var result = service.statistics(
                SESSION, 100, 501,
                new StatisticsRequest(
                        StatisticsAggregation.COUNT, null, null, null));
        assertThat(capabilities.sourceKind()).isEqualTo("HTTP_JSON");
        assertThat(capabilities.partial()).isTrue();
        assertThat(result.dataSourceVersionId()).isEqualTo(501);
        assertThat(gateway.capabilitiesCount).isEqualTo(2);
        assertThat(gateway.executeCount).isEqualTo(1);
    }

    @Test
    void negotiatesJdbcPublicationBeforeExecution() {
        repository = new FixedPublicationRepository(false, true);
        var dataSources = new DataSourceService(
                repository, new EmptyModuleCatalog(),
                Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"),
                        ZoneOffset.UTC));
        gateway = new FakeGateway();
        service = new DataSourceStatisticsService(dataSources, gateway);

        var capabilities = service.capabilities(SESSION, 100, 501);
        var result = service.statistics(
                SESSION, 100, 501,
                new StatisticsRequest(
                        StatisticsAggregation.COUNT, null, null, null));
        assertThat(capabilities.sourceKind()).isEqualTo("JDBC_TABLE");
        assertThat(capabilities.sourceRowLimit()).isEqualTo(25);
        assertThat(result.dataSourceVersionId()).isEqualTo(501);
        assertThat(gateway.capabilitiesCount).isEqualTo(2);
        assertThat(gateway.executeCount).isEqualTo(1);
    }

    private static StatisticsRequest trend(
            StatisticsAggregation aggregation,
            String measure
    ) {
        return new StatisticsRequest(
                aggregation, measure, null,
                new StatisticsRequest.Trend(
                        "createdAt", StatisticsGrain.DAY,
                        LocalDate.parse("2026-08-01"),
                        LocalDate.parse("2026-08-04")));
    }

    private static final class FakeGateway
            implements DataSourceStatisticsGateway {
        private DataSourcePublication lastPublication;
        private StatisticsPlan lastPlan;
        private StatisticsRecordRestriction lastRestriction;
        private int capabilitiesCount;
        private int executeCount;
        private RawStatistics raw = new RawStatistics(
                "query-default", BigDecimal.ZERO, 0,
                List.of(), List.of(), 0, false);

        @Override
        public SourceCapabilities capabilities(
                RuntimeSession session,
                DataSourcePublication publication
        ) {
            capabilitiesCount++;
            lastPublication = publication;
            return new SourceCapabilities(
                    91, "orders_module", "schema-1",
                    List.of(
                            field(13, "createdAt", "Created at",
                                    "DATETIME", false, true, true, true),
                            field(12, "category", "Category",
                                    "TEXT", false, false, true, true),
                            field(11, "amount", "Amount",
                                    "NUMBER", true, false, true, true),
                            field(14, "secret", "Secret",
                                    "SECRET", false, false, false, false),
                            field(15, "notOutput", "Not output",
                                    "NUMBER", true, false, true, true)));
        }

        @Override
        public RawStatistics execute(
                RuntimeSession session,
                DataSourcePublication publication,
                StatisticsPlan plan
        ) {
            executeCount++;
            lastPublication = publication;
            lastPlan = plan;
            return raw;
        }

        @Override
        public RawStatistics execute(
                RuntimeSession session,
                DataSourcePublication publication,
                StatisticsPlan plan,
                StatisticsRecordRestriction restriction
        ) {
            lastRestriction = restriction;
            return execute(session, publication, plan);
        }

        private static FieldCapability field(
                long id,
                String code,
                String name,
                String type,
                boolean numeric,
                boolean temporal,
                boolean groupable,
                boolean readable
        ) {
            return new FieldCapability(
                    id, code, name, type, type,
                    readable, numeric, temporal, groupable);
        }
    }

    private static final class FixedPublicationRepository
            implements DataSourceRepository {
        private final ModuleDataSource root;
        private final DataSourceVersion historical;

        private FixedPublicationRepository() {
            this(false, false);
        }

        private FixedPublicationRepository(boolean http) {
            this(http, false);
        }

        private FixedPublicationRepository(boolean http, boolean jdbc) {
            var publishedAt = Instant.parse("2026-07-01T00:00:00Z");
            root = new ModuleDataSource(
                    100, 10, 20, "orders", 91, "Orders", null,
                    DataSourceDraft.empty(), 2, 502L, 2,
                    publishedAt, publishedAt, 2);
            var snapshot = http
                    ? new DataSourceDraft(
                    List.of(new DataSourceDraft.OutputField("amount")),
                    List.of(), null, null,
                    DataSourceDraft.SourceKind.HTTP_JSON,
                    new DataSourceDraft.HttpJsonConnection(
                            "https://data.example.invalid/orders", null, 3),
                    List.of(new DataSourceDraft.HttpJsonFieldProjection(
                            "amount", "amount",
                            DataSourceDraft.HttpJsonSourceType.DECIMAL)))
                    : jdbc ? new DataSourceDraft(
                    List.of(), List.of(), null, null,
                    DataSourceDraft.SourceKind.JDBC_TABLE,
                    null, List.of(),
                    new DataSourceDraft.JdbcTableConnection(
                            "db.example.test", 3306, "analytics",
                            "orders",
                            "env://EXAMINE_DS_S10_T20_USERNAME_V1",
                            "env://EXAMINE_DS_S10_T20_PASSWORD_V1",
                            3, 3),
                    List.of(new DataSourceDraft.JdbcTableFieldProjection(
                            "amount", "amount",
                            DataSourceDraft.JdbcTableSourceType.DECIMAL)))
                    : new DataSourceDraft(
                    List.of(
                            new DataSourceDraft.OutputField("amount"),
                            new DataSourceDraft.OutputField("category"),
                            new DataSourceDraft.OutputField("createdAt"),
                            new DataSourceDraft.OutputField("secret")),
                    List.of(new DataSourceDraft.FixedFilter(
                            "status", "EQ", "\"OPEN\"")),
                    null, "createdAt");
            historical = new DataSourceVersion(
                    501, 100, 10, 20, 1, "orders", 91,
                    "orders_module", "schema-1", "Orders", null,
                    snapshot,
                    "0".repeat(64), 30, publishedAt);
        }

        @Override
        public Optional<ModuleDataSource> findById(
                long systemId,
                long tenantId,
                long dataSourceId
        ) {
            return root.systemId() == systemId && root.tenantId() == tenantId
                    && root.id() == dataSourceId
                    ? Optional.of(root) : Optional.empty();
        }

        @Override
        public Optional<DataSourceVersion> findVersionById(
                long systemId,
                long tenantId,
                long dataSourceId,
                long versionId
        ) {
            return findById(systemId, tenantId, dataSourceId).isPresent()
                    && historical.id() == versionId
                    ? Optional.of(historical) : Optional.empty();
        }

        @Override public long nextDataSourceId() { throw unsupported(); }
        @Override public long nextVersionId() { throw unsupported(); }
        @Override public Optional<ModuleDataSource> findByCode(
                long systemId, long tenantId, String code) {
            return Optional.empty();
        }
        @Override public List<ModuleDataSource> findAll(
                long systemId, long tenantId) { return List.of(); }
        @Override public ModuleDataSource insert(ModuleDataSource value) {
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
        @Override public List<DataSourceVersion> findVersions(
                long systemId, long tenantId, long dataSourceId) {
            return List.of();
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException();
        }
    }

    private static final class EmptyModuleCatalog
            implements DataSourceModuleCatalog {
        @Override
        public Optional<PublishedModule> publishedModule(
                long systemId,
                long tenantId,
                long moduleId
        ) {
            return Optional.empty();
        }
    }
}
