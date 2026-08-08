package com.unique.examine.module.datasource.statistics.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.domain.DataSourcePublication;
import com.unique.examine.module.datasource.domain.DataSourceVersion;
import com.unique.examine.module.datasource.domain.ModuleDataSource;
import com.unique.examine.module.datasource.runtime.DataSourceRecordQueryGateway;
import com.unique.examine.module.datasource.service.PublishedHttpDataSourceRowsReader;
import com.unique.examine.module.datasource.service.PublishedJdbcTableDataSourceRowsReader;
import com.unique.examine.module.datasource.service.PublishedMultiModuleJoinRowsReader;
import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;
import com.unique.examine.module.datasource.statistics.domain.StatisticsException;
import com.unique.examine.module.datasource.statistics.domain.StatisticsRecordRestriction;
import com.unique.examine.module.datasource.statistics.domain.StatisticsRequest;
import com.unique.examine.module.datasource.statistics.port.DataSourceStatisticsGateway;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NegotiatingDataSourceStatisticsGatewayTest {
    private static final RuntimeSession SESSION = new RuntimeSession(
            1, 10, 7, 20L, Set.of("system.runtime.access"));

    @Test
    void productionConstructorIsExplicitAndTestConstructorIsNotPublic() {
        var constructors = NegotiatingDataSourceStatisticsGateway.class
                .getConstructors();

        assertThat(constructors).singleElement().satisfies(constructor ->
                assertThat(constructor.getAnnotation(Autowired.class))
                        .isNotNull());
    }

    @Test
    void httpProjectionAggregatesWithOwnerRestrictionAndExactPin() {
        var publication = publication(DataSourceDraft.SourceKind.HTTP_JSON);
        var gateway = gateway(publication, httpRows(publication,
                row("amount", new BigDecimal("10"), "category", "A",
                        "ownerMemberId", 7L),
                row("amount", new BigDecimal("5"), "category", "B",
                        "ownerMemberId", 7L),
                row("amount", new BigDecimal("99"), "category", "A",
                        "ownerMemberId", 8L)), unavailableJdbc());
        var capabilities = gateway.capabilities(SESSION, publication);
        var measure = field(capabilities, "amount");
        var group = field(capabilities, "category");
        var request = new StatisticsRequest(
                StatisticsAggregation.SUM, "amount",
                new StatisticsRequest.Grouping("category", 10), null);

        var result = gateway.execute(SESSION, publication,
                new DataSourceStatisticsGateway.StatisticsPlan(
                        request, measure, group, null),
                StatisticsRecordRestriction.ownerMember(7));

        assertThat(result.value()).isEqualByComparingTo("15");
        assertThat(result.matchedRecordCount()).isEqualTo(2);
        assertThat(result.groupBuckets()).extracting(value -> value.key())
                .containsExactly("A", "B");
        assertThat(result.queryId()).matches("^[0-9a-f]{64}$");
    }

    @Test
    void jdbcProjectionUsesSameNegotiatedStatisticsPath() {
        var publication = publication(DataSourceDraft.SourceKind.JDBC_TABLE);
        var gateway = gateway(publication, unavailableHttp(),
                jdbcRows(publication,
                        row("amount", new BigDecimal("2"), "category", "A",
                                "ownerMemberId", 7L),
                        row("amount", new BigDecimal("3"), "category", "A",
                                "ownerMemberId", 7L)));
        var capabilities = gateway.capabilities(SESSION, publication);
        var request = new StatisticsRequest(
                StatisticsAggregation.COUNT, null, null, null);

        var result = gateway.execute(SESSION, publication,
                new DataSourceStatisticsGateway.StatisticsPlan(
                        request, null, null, null));

        assertThat(capabilities.schemaVersionId()).isEqualTo("schema-1");
        assertThat(result.matchedRecordCount()).isEqualTo(2);
        assertThat(result.value()).isEqualByComparingTo("2");
    }

    @Test
    void readerTimeoutAndMissingOwnerFailClosedWithoutCredentialDisclosure() {
        var publication = publication(DataSourceDraft.SourceKind.HTTP_JSON);
        PublishedHttpDataSourceRowsReader timeout = (actor, value) -> {
            throw new DataSourceException(
                    "DATA_SOURCE_HTTP_ROWS_TIMEOUT",
                    "Published HTTP rows read timed out");
        };
        var gateway = gateway(publication, timeout, unavailableJdbc());
        var capabilities = gateway.capabilities(SESSION, publication);
        var request = new StatisticsRequest(
                StatisticsAggregation.COUNT, null, null, null);

        assertThatThrownBy(() -> gateway.execute(SESSION, publication,
                new DataSourceStatisticsGateway.StatisticsPlan(
                        request, null, null, null)))
                .isInstanceOfSatisfying(DataSourceException.class, error -> {
                    assertThat(error.code())
                            .isEqualTo("DATA_SOURCE_HTTP_ROWS_TIMEOUT");
                    assertThat(error.getMessage()).doesNotContain(
                            "env://", "password", "token");
                });

        var noOwner = gateway(publication, httpRows(publication,
                row("amount", BigDecimal.ONE, "category", "A")),
                unavailableJdbc());
        assertThatThrownBy(() -> noOwner.execute(SESSION, publication,
                new DataSourceStatisticsGateway.StatisticsPlan(
                        request, null, null, null),
                StatisticsRecordRestriction.ownerMember(7)))
                .hasMessageContaining("owner projection");
        assertThat(capabilities.moduleCode()).isEqualTo("orders_module");
    }

    @Test
    void joinedStatisticsUsePinnedCapabilitiesAndFailClosedWhenPartial() {
        var publication = joinedPublication();
        PublishedMultiModuleJoinRowsReader complete = (session, value) ->
                new PublishedMultiModuleJoinRowsReader.Result(
                        value.root().id(), value.version().id(),
                        value.version().versionNumber(), List.of(
                        new PublishedMultiModuleJoinRowsReader.Row(
                                "join:1", Map.of(
                                "orders__amount", new BigDecimal("8"),
                                "customers__ownerMemberId", 7L),
                                Map.of("orders", "order-1"))),
                        false, List.of(), 50);
        var gateway = new NegotiatingDataSourceStatisticsGateway(
                new NativeDataSourceStatisticsGateway(
                        new JdbcTemplate(), new SchemaGateway(schema()),
                        new ObjectMapper()),
                new SchemaGateway(schema()), unavailableHttp(),
                unavailableJdbc(), complete);
        var capabilities = gateway.capabilities(SESSION, publication);
        var measure = field(capabilities, "orders__amount");
        var request = new StatisticsRequest(
                StatisticsAggregation.SUM, "orders__amount", null, null);

        var result = gateway.execute(SESSION, publication,
                new DataSourceStatisticsGateway.StatisticsPlan(
                        request, measure, null, null),
                StatisticsRecordRestriction.ownerMember(7));

        assertThat(result.value()).isEqualByComparingTo("8");

        PublishedMultiModuleJoinRowsReader partial = (session, value) ->
                new PublishedMultiModuleJoinRowsReader.Result(
                value.root().id(), value.version().id(),
                value.version().versionNumber(), List.of(), true,
                List.of("orders"), 50);
        var partialGateway = new NegotiatingDataSourceStatisticsGateway(
                new NativeDataSourceStatisticsGateway(
                        new JdbcTemplate(), new SchemaGateway(schema()),
                        new ObjectMapper()),
                new SchemaGateway(schema()), unavailableHttp(),
                unavailableJdbc(), partial);
        assertThatThrownBy(() -> partialGateway.execute(SESSION, publication,
                new DataSourceStatisticsGateway.StatisticsPlan(
                        request, measure, null, null)))
                .isInstanceOf(StatisticsException.class)
                .hasMessageContaining("partial");
    }

    private static DataSourcePublication joinedPublication() {
        var now = Instant.parse("2026-08-07T00:00:00Z");
        var join = new DataSourceDraft.MultiModuleJoin(
                List.of(
                        new DataSourceDraft.JoinInput("customers", 101, 1001),
                        new DataSourceDraft.JoinInput("orders", 202, 2002)),
                List.of(new DataSourceDraft.JoinEdge(
                        "customers", "id", "orders", "customerId",
                        DataSourceDraft.JoinType.LEFT,
                        DataSourceDraft.JoinCardinality.ONE_TO_MANY)),
                List.of(
                        new DataSourceDraft.JoinProjection(
                                "customers", "ownerMemberId",
                                "customers__ownerMemberId", 71,
                                "Owner", "MEMBER", "MEMBER",
                                false, false, true),
                        new DataSourceDraft.JoinProjection(
                                "orders", "amount", "orders__amount", 81,
                                "Amount", "NUMBER", "NUMBER",
                                true, false, true)),
                DataSourceDraft.JoinFailureMode.ALLOW_PARTIAL_LEFT, 3, 50);
        var draft = new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.MULTI_MODULE_JOIN,
                null, List.of(), null, List.of(), join);
        var root = new ModuleDataSource(
                91, 10, 20, "customer_orders", 31, "Customer orders", null,
                draft, 1, 901L, 1, now, now, 2);
        var version = new DataSourceVersion(
                901, 91, 10, 20, 1, "customer_orders", 31,
                "customers_module", "schema-1", "Customer orders", null,
                draft, "a".repeat(64), 7, now);
        return new DataSourcePublication(root, version);
    }

    private static NegotiatingDataSourceStatisticsGateway gateway(
            DataSourcePublication publication,
            PublishedHttpDataSourceRowsReader http,
            PublishedJdbcTableDataSourceRowsReader jdbc
    ) {
        return new NegotiatingDataSourceStatisticsGateway(
                new NativeDataSourceStatisticsGateway(
                        new JdbcTemplate(), new SchemaGateway(schema()),
                        new ObjectMapper()),
                new SchemaGateway(schema()), http, jdbc);
    }

    private static DataSourceStatisticsGateway.FieldCapability field(
            DataSourceStatisticsGateway.SourceCapabilities capabilities,
            String code
    ) {
        return capabilities.fields().stream()
                .filter(value -> value.code().equals(code))
                .findFirst().orElseThrow();
    }

    private static DataSourcePublication publication(
            DataSourceDraft.SourceKind kind
    ) {
        var now = Instant.parse("2026-08-07T00:00:00Z");
        var projections = List.of(
                new DataSourceDraft.HttpJsonFieldProjection(
                        "amount", "amount",
                        DataSourceDraft.HttpJsonSourceType.DECIMAL),
                new DataSourceDraft.HttpJsonFieldProjection(
                        "category", "category",
                        DataSourceDraft.HttpJsonSourceType.STRING),
                new DataSourceDraft.HttpJsonFieldProjection(
                        "ownerMemberId", "ownerMemberId",
                        DataSourceDraft.HttpJsonSourceType.INTEGER));
        DataSourceDraft draft;
        if (kind == DataSourceDraft.SourceKind.HTTP_JSON) {
            draft = new DataSourceDraft(List.of(), List.of(), null, null,
                    kind, new DataSourceDraft.HttpJsonConnection(
                    "https://api.example.invalid/orders",
                    "env://EXAMINE_DS_S10_T20_TOKEN_V1", 3), projections);
        } else {
            draft = new DataSourceDraft(List.of(), List.of(), null, null,
                    kind, null, List.of(),
                    new DataSourceDraft.JdbcTableConnection(
                            "db.example.invalid", 3306, "analytics", "orders",
                            "env://EXAMINE_DS_S10_T20_USERNAME_V1",
                            "env://EXAMINE_DS_S10_T20_PASSWORD_V1", 3, 3),
                    List.of(
                            new DataSourceDraft.JdbcTableFieldProjection(
                                    "amount", "amount",
                                    DataSourceDraft.JdbcTableSourceType.DECIMAL),
                            new DataSourceDraft.JdbcTableFieldProjection(
                                    "category", "category",
                                    DataSourceDraft.JdbcTableSourceType.STRING),
                            new DataSourceDraft.JdbcTableFieldProjection(
                                    "owner_member_id", "ownerMemberId",
                                    DataSourceDraft.JdbcTableSourceType.INTEGER)));
        }
        var root = new ModuleDataSource(
                100, 10, 20, "orders_external", 91, "Orders external", null,
                DataSourceDraft.empty(), 2, 501L, 1, now, now, 2);
        var version = new DataSourceVersion(
                501, 100, 10, 20, 1, "orders_external", 91,
                "orders_module", "schema-1", "Orders external", null,
                draft, "a".repeat(64), 7, now);
        return new DataSourcePublication(root, version);
    }

    private static RecordRuntimeViews.RecordSchema schema() {
        return new RecordRuntimeViews.RecordSchema(
                "schema-1", "600", "91", "checksum", "READY", null, 1,
                List.of(
                        schemaField("11", "amount", "Amount", "NUMBER"),
                        schemaField("12", "category", "Category", "TEXT"),
                        schemaField("13", "ownerMemberId", "Owner", "NUMBER")),
                List.of(), new RecordRuntimeViews.QueryLimits(25, 25, 0));
    }

    private static RecordRuntimeViews.FieldCapability schemaField(
            String id, String code, String name, String type
    ) {
        return new RecordRuntimeViews.FieldCapability(
                code, name, id, type, "OPTIONAL", true, false,
                true, true, false, List.of("EQ"), false, true, true,
                List.of(), new ObjectMapper().createObjectNode());
    }

    @SafeVarargs
    private static PublishedHttpDataSourceRowsReader httpRows(
            DataSourcePublication publication,
            Map<String, Object>... rows
    ) {
        return (actor, value) -> new PublishedHttpDataSourceRowsReader.Result(
                publication.root().id(), publication.root().code(),
                publication.version().id(), publication.version().versionNumber(),
                List.of(), java.util.stream.IntStream.range(0, rows.length)
                .mapToObj(index -> new PublishedHttpDataSourceRowsReader.Row(
                        index + 1, rows[index])).toList());
    }

    @SafeVarargs
    private static PublishedJdbcTableDataSourceRowsReader jdbcRows(
            DataSourcePublication publication,
            Map<String, Object>... rows
    ) {
        var fields = List.of(
                new PublishedJdbcTableDataSourceRowsReader.Field(
                        "amount", "DECIMAL"),
                new PublishedJdbcTableDataSourceRowsReader.Field(
                        "category", "STRING"),
                new PublishedJdbcTableDataSourceRowsReader.Field(
                        "ownerMemberId", "INTEGER"));
        return (actor, value) -> new PublishedJdbcTableDataSourceRowsReader.Result(
                publication.root().id(), publication.root().code(),
                publication.version().id(), publication.version().versionNumber(),
                fields, java.util.stream.IntStream.range(0, rows.length)
                .mapToObj(index -> new PublishedJdbcTableDataSourceRowsReader.Row(
                        index + 1, rows[index])).toList());
    }

    private static Map<String, Object> row(Object... values) {
        var row = new LinkedHashMap<String, Object>();
        for (int index = 0; index < values.length; index += 2) {
            row.put((String) values[index], values[index + 1]);
        }
        return row;
    }

    private static PublishedHttpDataSourceRowsReader unavailableHttp() {
        return (actor, publication) -> {
            throw new AssertionError("HTTP reader must not be used");
        };
    }

    private static PublishedJdbcTableDataSourceRowsReader unavailableJdbc() {
        return (actor, publication) -> {
            throw new AssertionError("JDBC reader must not be used");
        };
    }

    private record SchemaGateway(RecordRuntimeViews.RecordSchema schema)
            implements DataSourceRecordQueryGateway {
        @Override
        public RecordRuntimeViews.RecordSchema schema(
                RuntimeSession session, String moduleCode
        ) {
            return schema;
        }

        @Override
        public RecordRuntimeViews.RecordSchema schema(
                RuntimeSession session, String moduleCode,
                String schemaVersionId
        ) {
            return schema;
        }

        @Override
        public RecordRuntimeViews.RecordPage query(
                RuntimeSession session, String moduleCode, String queryJson
        ) {
            throw new AssertionError("Native query must not be used");
        }
    }
}
