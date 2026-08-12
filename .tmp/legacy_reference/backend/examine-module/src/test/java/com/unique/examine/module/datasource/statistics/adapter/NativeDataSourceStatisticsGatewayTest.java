package com.unique.examine.module.datasource.statistics.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourcePublication;
import com.unique.examine.module.datasource.domain.DataSourceVersion;
import com.unique.examine.module.datasource.domain.ModuleDataSource;
import com.unique.examine.module.datasource.runtime.DataSourceRecordQueryGateway;
import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;
import com.unique.examine.module.datasource.statistics.domain.StatisticsException;
import com.unique.examine.module.datasource.statistics.domain.StatisticsGrain;
import com.unique.examine.module.datasource.statistics.domain.StatisticsRequest;
import com.unique.examine.module.datasource.statistics.domain.StatisticsRecordRestriction;
import com.unique.examine.module.datasource.statistics.port.DataSourceStatisticsGateway.FieldCapability;
import com.unique.examine.module.datasource.statistics.port.DataSourceStatisticsGateway.RawTrendBucket;
import com.unique.examine.module.datasource.statistics.port.DataSourceStatisticsGateway.StatisticsPlan;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.query.NativeRecordAggregatePlan;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NativeDataSourceStatisticsGatewayTest {
    @Test
    void capabilitiesUseTheNativeReadableProjectionAndEffectiveTypes() {
        var records = new NativeRecords();
        var gateway = gateway(new RecordingJdbcTemplate(), records);

        var capabilities = gateway.capabilities(session(), publication());

        assertThat(capabilities.logicalModuleId()).isEqualTo(30);
        assertThat(capabilities.moduleCode()).isEqualTo("orders");
        assertThat(capabilities.schemaVersionId()).isEqualTo("40");
        assertThat(capabilities.fields())
                .extracting(FieldCapability::code)
                .containsExactly("amount", "category", "occurred", "money");
        assertThat(capabilities.fields().getFirst())
                .satisfies(field -> {
                    assertThat(field.name()).isEqualTo("Amount");
                    assertThat(field.logicalFieldId()).isEqualTo(11);
                    assertThat(field.numeric()).isTrue();
                    assertThat(field.groupable()).isTrue();
                });
        assertThat(capabilities.fields().get(2).temporal()).isTrue();
        assertThat(capabilities.fields().getLast().numeric()).isFalse();
        assertThat(capabilities.fields().getLast().groupable()).isFalse();
        assertThat(records.activeSchemaCalls).isZero();
        assertThat(records.exactSchemaCalls).isEqualTo(1);
        assertThat(records.requestedSchemaVersion).isEqualTo("40");
    }

    @Test
    void historicalCapabilitiesStillApplyCurrentFieldReadPermission() {
        var records = new NativeRecords(true);
        var gateway = gateway(new RecordingJdbcTemplate(), records);

        var hidden = gateway.capabilities(session(), publication());
        var allowed = gateway.capabilities(new RuntimeSession(
                1, 10, 2, 20L, Set.of(
                "module.orders.view",
                "module.orders.field.amount.read")), publication());

        assertThat(hidden.fields())
                .extracting(FieldCapability::code)
                .doesNotContain("amount");
        assertThat(allowed.fields())
                .extracting(FieldCapability::code)
                .contains("amount");
        assertThat(records.activeSchemaCalls).isZero();
    }

    @Test
    void scalarAggregateUsesTheCompleteScopedDatabaseSetNotAPage() {
        var jdbc = new RecordingJdbcTemplate();
        jdbc.overall = new NativeDataSourceStatisticsGateway.Overall(
                new BigDecimal("802.5000"), 401);
        var records = new NativeRecords();
        var gateway = gateway(jdbc, records);
        var amount = capability(11, "amount", "Amount", "NUMBER",
                true, false, true);
        var request = new StatisticsRequest(
                StatisticsAggregation.SUM, "amount", null, null);

        var result = gateway.execute(
                session(), publication(),
                new StatisticsPlan(request, amount, null, null));

        assertThat(result.value()).isEqualByComparingTo("802.5");
        assertThat(result.matchedRecordCount()).isEqualTo(401);
        assertThat(records.pageQueryCalls).isZero();
        assertThat(records.preparedQuery)
                .contains("\"page\":1", "\"size\":1")
                .contains("\"recordScope\":\"active\"")
                .contains("\"fieldCode\":\"category\"")
                .contains("\"operator\":\"EQ\"")
                .doesNotContain("LIMIT");

        var call = jdbc.calls.getFirst();
        assertThat(normalize(call.sql()))
                .startsWith("select sum(measure_value.decimal_value)")
                .contains("from un_module_record r")
                .contains("measure_value.logical_field_id=?")
                .contains("r.system_id=?", "r.tenant_id=?")
                .contains("r.status=?")
                .contains("r.owner_member_id in (?)")
                .contains("exists (select 1 from un_module_record_index")
                .doesNotContain(" limit ", " offset ");
        assertThat(call.arguments())
                .containsExactly(
                        11L, 10L, 20L, 30L, 40L, 31L,
                        "ACTIVE", 2L, "OPEN");
    }

    @Test
    void groupingCanonicalizesDecimalKeysOrdersNullLastAndReportsOverflow() {
        var jdbc = new RecordingJdbcTemplate();
        jdbc.overall = new NativeDataSourceStatisticsGateway.Overall(
                new BigDecimal("4"), 4);
        jdbc.groups = List.of(
                new NativeDataSourceStatisticsGateway.GroupRow(
                        "1.0000000000", "One", BigDecimal.ONE, 1, 4),
                new NativeDataSourceStatisticsGateway.GroupRow(
                        "2.5000000000", "Two point five",
                        new BigDecimal("2"), 2, 4),
                new NativeDataSourceStatisticsGateway.GroupRow(
                        null, null, BigDecimal.ONE, 1, 4));
        var records = new NativeRecords();
        var gateway = gateway(jdbc, records);
        var amount = capability(11, "amount", "Amount", "NUMBER",
                true, false, true);
        var request = new StatisticsRequest(
                StatisticsAggregation.COUNT, null,
                new StatisticsRequest.Grouping("amount", 3), null);

        var result = gateway.execute(
                session(), publication(),
                new StatisticsPlan(request, null, amount, null));

        assertThat(result.totalBucketCount()).isEqualTo(4);
        assertThat(result.truncated()).isTrue();
        assertThat(result.groupBuckets())
                .extracting(bucket -> bucket.key())
                .containsExactly("1", "2.5", null);
        assertThat(result.groupBuckets().getLast().nullBucket()).isTrue();
        assertThat(records.pageQueryCalls).isZero();

        var grouped = jdbc.calls.get(1);
        assertThat(normalize(grouped.sql()))
                .contains("group by case when group_value.decimal_value is null")
                .contains("count(*) over() as total_bucket_count")
                .contains("order by grouped.group_key is null asc")
                .contains("binary grouped.group_key asc limit ?")
                .doesNotContain(" offset ");
        assertThat(grouped.arguments().getLast()).isEqualTo(3);
    }

    @Test
    void textGroupingUsesBinaryCollationBeforeDatabaseAggregation() {
        var jdbc = new RecordingJdbcTemplate();
        var gateway = gateway(jdbc, new NativeRecords());
        var text = capability(
                12, "category", "Category", "TEXT",
                false, false, true);
        var request = new StatisticsRequest(
                StatisticsAggregation.COUNT, null,
                new StatisticsRequest.Grouping("category", 20), null);

        gateway.execute(session(), publication(),
                new StatisticsPlan(request, null, text, null));

        assertThat(normalize(jdbc.calls.get(1).sql()))
                .contains("group_value.string_value collate utf8mb4_bin as group_key")
                .contains("min(group_value.display_value collate utf8mb4_bin) as group_label")
                .contains("group by group_value.string_value collate utf8mb4_bin");
    }

    @Test
    void trendAppliesExclusiveRangeAndDatabaseGrainWithoutPageLoading() {
        var jdbc = new RecordingJdbcTemplate();
        jdbc.overall = new NativeDataSourceStatisticsGateway.Overall(
                new BigDecimal("15.0"), 3);
        jdbc.trend = List.of(
                new RawTrendBucket(
                        LocalDate.of(2026, 8, 1),
                        new BigDecimal("10"), 2),
                new RawTrendBucket(
                        LocalDate.of(2026, 8, 3),
                        new BigDecimal("5"), 1));
        var records = new NativeRecords();
        var gateway = gateway(jdbc, records);
        var amount = capability(11, "amount", "Amount", "NUMBER",
                true, false, true);
        var occurred = capability(13, "occurred", "Occurred", "DATE",
                false, true, true);
        var request = new StatisticsRequest(
                StatisticsAggregation.AVG, "amount", null,
                new StatisticsRequest.Trend(
                        "occurred", StatisticsGrain.DAY,
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 8, 5)));

        var result = gateway.execute(
                session(), publication(),
                new StatisticsPlan(request, amount, null, occurred));

        assertThat(result.trendBuckets()).hasSize(2);
        assertThat(result.totalBucketCount()).isEqualTo(2);
        assertThat(records.pageQueryCalls).isZero();
        assertThat(jdbc.calls).hasSize(2);
        assertThat(jdbc.calls)
                .allSatisfy(call -> {
                    assertThat(normalize(call.sql()))
                            .contains("time_value.date_value>=?")
                            .contains("time_value.date_value<?")
                            .doesNotContain(" limit ", " offset ");
                    assertThat(call.arguments())
                            .containsSubsequence(
                                    LocalDate.of(2026, 8, 1),
                                    LocalDate.of(2026, 8, 5));
                });
        assertThat(normalize(jdbc.calls.getLast().sql()))
                .contains("date(time_value.date_value) as bucket_start")
                .contains("group by date(time_value.date_value)")
                .contains("order by bucket_start asc");
    }

    @Test
    void crossTenantPublicationIsHiddenBeforeNativePlanningOrSql() {
        var jdbc = new RecordingJdbcTemplate();
        var records = new NativeRecords();
        var gateway = gateway(jdbc, records);
        var otherTenant = new RuntimeSession(
                1, 10, 2, 21L, Set.of("module.orders.view"));
        var request = new StatisticsRequest(
                StatisticsAggregation.COUNT, null, null, null);

        assertThatThrownBy(() -> gateway.execute(
                otherTenant, publication(),
                new StatisticsPlan(request, null, null, null)))
                .isInstanceOfSatisfying(StatisticsException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("STATISTICS_SOURCE_NOT_FOUND"));
        assertThat(records.preparedQuery).isNull();
        assertThat(jdbc.calls).isEmpty();
    }

    @Test
    void queryIdentityIsStableWithinAScopeAndDistinctAcrossTypedScopes() {
        var statistics = new StatisticsPlan(
                new StatisticsRequest(
                        StatisticsAggregation.COUNT, null, null, null),
                null, null, null);

        var firstBytes = NativeDataSourceStatisticsGateway.queryId(
                publication(), planWithScope(new byte[]{1, 2, 3}),
                statistics);
        var sameBytes = NativeDataSourceStatisticsGateway.queryId(
                publication(), planWithScope(new byte[]{1, 2, 3}),
                statistics);
        var differentScope = NativeDataSourceStatisticsGateway.queryId(
                publication(), planWithScope(3L), statistics);
        var ownerScope = NativeDataSourceStatisticsGateway.queryId(
                publication(), planWithScope(2L), statistics);
        var dateScope = NativeDataSourceStatisticsGateway.queryId(
                publication(), planWithScope(LocalDate.of(2026, 8, 1)),
                statistics);
        var textScope = NativeDataSourceStatisticsGateway.queryId(
                publication(), planWithScope("2026-08-01"), statistics);

        assertThat(firstBytes).isEqualTo(sameBytes);
        assertThat(ownerScope).isNotEqualTo(differentScope);
        assertThat(dateScope).isNotEqualTo(textScope);
    }

    @Test
    void ownershipRestrictionsAreAppliedBeforeEveryAggregate() {
        var jdbc = new RecordingJdbcTemplate();
        var gateway = gateway(jdbc, new NativeRecords());
        var request = new StatisticsRequest(
                StatisticsAggregation.COUNT, null, null,
                new StatisticsRequest.Trend(
                        "occurred", StatisticsGrain.DAY,
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 8, 3)));
        var occurred = capability(13, "occurred", "Occurred", "DATE",
                false, true, true);

        gateway.execute(session(), publication(),
                new StatisticsPlan(request, null, null, occurred),
                StatisticsRecordRestriction.ownerMembers(
                        List.of(9L, 4L, 9L)));

        assertThat(jdbc.calls).hasSize(2).allSatisfy(call -> {
            assertThat(normalize(call.sql()))
                    .contains("where r.system_id=?")
                    .contains("and r.owner_member_id in (?,?)")
                    .contains("time_value.date_value>=?");
            assertThat(call.arguments()).containsSubsequence(4L, 9L);
        });
    }

    @Test
    void emptyRoleRestrictionReturnsExactZeroWithoutRunningAggregateSql() {
        var jdbc = new RecordingJdbcTemplate();
        var gateway = gateway(jdbc, new NativeRecords());
        var request = new StatisticsRequest(
                StatisticsAggregation.SUM, "amount", null, null);
        var amount = capability(11, "amount", "Amount", "NUMBER",
                true, false, true);

        var result = gateway.execute(session(), publication(),
                new StatisticsPlan(request, amount, null, null),
                StatisticsRecordRestriction.ownerMembers(List.of()));

        assertThat(result.value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.matchedRecordCount()).isZero();
        assertThat(result.queryId()).hasSize(64);
        assertThat(jdbc.calls).isEmpty();
    }

    @Test
    void restrictionKindAndCanonicalIdsParticipateInQueryIdentity() {
        var statistics = new StatisticsPlan(
                new StatisticsRequest(
                        StatisticsAggregation.COUNT, null, null, null),
                null, null, null);
        var plan = planWithScope(2L);

        var members = NativeDataSourceStatisticsGateway.queryId(
                publication(), plan, statistics,
                StatisticsRecordRestriction.ownerMembers(List.of(9L, 4L)));
        var canonicalMembers = NativeDataSourceStatisticsGateway.queryId(
                publication(), plan, statistics,
                StatisticsRecordRestriction.ownerMembers(List.of(4L, 9L)));
        var member = NativeDataSourceStatisticsGateway.queryId(
                publication(), plan, statistics,
                StatisticsRecordRestriction.ownerMember(4L));
        var department = NativeDataSourceStatisticsGateway.queryId(
                publication(), plan, statistics,
                StatisticsRecordRestriction.ownerDepartment(4L));

        assertThat(members).isEqualTo(canonicalMembers);
        assertThat(member).isNotEqualTo(department).isNotEqualTo(members);
    }

    private static NativeDataSourceStatisticsGateway gateway(
            RecordingJdbcTemplate jdbc,
            NativeRecords records
    ) {
        return new NativeDataSourceStatisticsGateway(
                jdbc, records, new ObjectMapper());
    }

    private static RuntimeSession session() {
        return new RuntimeSession(
                1, 10, 2, 20L, Set.of("module.orders.view"));
    }

    private static DataSourcePublication publication() {
        var instant = Instant.parse("2026-08-01T00:00:00Z");
        var draft = new DataSourceDraft(
                List.of(
                        new DataSourceDraft.OutputField("amount"),
                        new DataSourceDraft.OutputField("category"),
                        new DataSourceDraft.OutputField("occurred"),
                        new DataSourceDraft.OutputField("money")),
                List.of(new DataSourceDraft.FixedFilter(
                        "category", "EQ", "\"OPEN\"")),
                null, "occurred");
        var root = ModuleDataSource.create(
                100, 10, 20, "statistics", 30,
                "Statistics", null, draft, instant);
        var version = new DataSourceVersion(
                200, root.id(), root.systemId(), root.tenantId(), 1,
                root.code(), root.moduleId(), "orders", "40",
                root.name(), root.description(), draft, "a".repeat(64),
                2, instant);
        return new DataSourcePublication(root, version);
    }

    private static FieldCapability capability(
            long id,
            String code,
            String name,
            String queryType,
            boolean numeric,
            boolean temporal,
            boolean groupable
    ) {
        return new FieldCapability(
                id, code, name, queryType, queryType,
                true, numeric, temporal, groupable);
    }

    private static NativeRecordAggregatePlan planWithScope(Object scope) {
        return new NativeRecordAggregatePlan(
                "b".repeat(64), "orders", 10, 20,
                40, 31, 30,
                "r.system_id=? AND r.tenant_id=? "
                        + "AND r.owner_member_id=?",
                List.of(10L, 20L, scope),
                List.of(new NativeRecordAggregatePlan.Field(
                        11, "amount", "NUMBER")));
    }

    private static RecordRuntimeViews.FieldCapability nativeField(
            long id,
            String code,
            String name,
            String type
    ) {
        return new RecordRuntimeViews.FieldCapability(
                code, name, Long.toString(id), type, "READONLY",
                true, false, false, false, false,
                List.of(), true, true, true, List.of(),
                JsonNodeFactory.instance.objectNode());
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private static final class NativeRecords
            implements DataSourceRecordQueryGateway {
        private final boolean protectAmount;
        private String preparedQuery;
        private String requestedSchemaVersion;
        private int pageQueryCalls;
        private int activeSchemaCalls;
        private int exactSchemaCalls;

        private NativeRecords() {
            this(false);
        }

        private NativeRecords(boolean protectAmount) {
            this.protectAmount = protectAmount;
        }

        @Override
        public RecordRuntimeViews.RecordSchema schema(
                RuntimeSession session,
                String moduleCode
        ) {
            activeSchemaCalls++;
            return new RecordRuntimeViews.RecordSchema(
                    "41", "91", "30", "c".repeat(64),
                    "READY", null, 2,
                    List.of(nativeField(
                            111, "amount", "Current Amount", "TEXT")),
                    List.of(), new RecordRuntimeViews.QueryLimits(50, 200, 3));
        }

        @Override
        public RecordRuntimeViews.RecordSchema schema(
                RuntimeSession session,
                String moduleCode,
                String schemaVersionId
        ) {
            exactSchemaCalls++;
            requestedSchemaVersion = schemaVersionId;
            var fields = new ArrayList<RecordRuntimeViews.FieldCapability>();
            if (!protectAmount || session.permissions().contains(
                    "module.orders.field.amount.read")) {
                fields.add(nativeField(
                        11, "amount", "Amount", "NUMBER"));
            }
            fields.addAll(List.of(
                    nativeField(12, "category", "Category", "TEXT"),
                    nativeField(13, "occurred", "Occurred", "DATE"),
                    nativeField(14, "money", "Money", "MONEY")));
            return new RecordRuntimeViews.RecordSchema(
                    "40", "31", "30", "a".repeat(64),
                    "READY", null, 1,
                    List.copyOf(fields),
                    List.of(), new RecordRuntimeViews.QueryLimits(50, 200, 3));
        }

        @Override
        public RecordRuntimeViews.RecordPage query(
                RuntimeSession session,
                String moduleCode,
                String queryJson
        ) {
            pageQueryCalls++;
            throw new AssertionError(
                    "Statistics must not execute the paged record query");
        }

        @Override
        public NativeRecordAggregatePlan prepareActiveAggregate(
                RuntimeSession session,
                String moduleCode,
                String queryJson
        ) {
            preparedQuery = queryJson;
            return new NativeRecordAggregatePlan(
                    "b".repeat(64), moduleCode, 10, 20,
                    40, 31, 30,
                    "r.system_id=? AND r.tenant_id=? "
                            + "AND r.logical_module_id=? "
                            + "AND r.schema_version_id=? "
                            + "AND r.module_snapshot_id=? AND r.status=? "
                            + "AND r.owner_member_id IN (?) "
                            + "AND EXISTS (SELECT 1 FROM "
                            + "un_module_record_index filter_value "
                            + "WHERE filter_value.record_id=r.record_id "
                            + "AND filter_value.string_value=?)",
                    List.of(10L, 20L, 30L, 40L, 31L,
                            "ACTIVE", 2L, "OPEN"),
                    List.of(
                            new NativeRecordAggregatePlan.Field(
                                    11, "amount", "NUMBER"),
                            new NativeRecordAggregatePlan.Field(
                                    12, "category", "TEXT"),
                            new NativeRecordAggregatePlan.Field(
                                    13, "occurred", "DATE"),
                            new NativeRecordAggregatePlan.Field(
                                    14, "money", "MONEY")));
        }

        @Override
        public NativeRecordAggregatePlan prepareActiveAggregate(
                RuntimeSession session,
                String moduleCode,
                String schemaVersionId,
                String queryJson
        ) {
            requestedSchemaVersion = schemaVersionId;
            return prepareActiveAggregate(session, moduleCode, queryJson);
        }
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<Call> calls = new ArrayList<>();
        private NativeDataSourceStatisticsGateway.Overall overall =
                new NativeDataSourceStatisticsGateway.Overall(
                        BigDecimal.ZERO, 0);
        private List<NativeDataSourceStatisticsGateway.GroupRow> groups =
                List.of();
        private List<RawTrendBucket> trend = List.of();

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(
                String sql,
                org.springframework.jdbc.core.RowMapper<T> rowMapper,
                Object... arguments
        ) {
            calls.add(new Call(sql, List.of(arguments)));
            if (sql.contains("grouped.group_key")) {
                return (List<T>) groups;
            }
            if (sql.contains(" AS bucket_start")) {
                return (List<T>) trend;
            }
            return (List<T>) List.of(overall);
        }
    }

    private record Call(String sql, List<Object> arguments) {
    }
}
