package com.unique.examine.module.datasource.external.jdbc;

import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.service.DataSourceService;
import com.unique.examine.module.datasource.service.JdbcTableDataSourceSchemaDiscoveryUseCase;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class JdbcTableDataSourceSchemaDiscoverer
        implements JdbcTableDataSourceSchemaDiscoveryUseCase {
    private static final Pattern FIELD_CODE =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");

    private final DataSourceService dataSources;
    private final JdbcTableSafeClient client;

    public JdbcTableDataSourceSchemaDiscoverer(
            DataSourceService dataSources,
            JdbcTableSafeClient client
    ) {
        this.dataSources = Objects.requireNonNull(dataSources, "dataSources");
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public Result discover(
            DataSourceActor actor,
            long dataSourceId,
            long expectedVersion
    ) {
        var source = JdbcTableAdapterSupport.exactJdbcDraft(
                dataSources, actor, dataSourceId, expectedVersion,
                "DATA_SOURCE_JDBC_SCHEMA_DISCOVERY_INVALID",
                "Schema discovery requires a JDBC table data-source draft");
        var started = System.nanoTime();
        try {
            var fields = client.discover(actor, JdbcTableAdapterSupport.spec(
                            source.draft().jdbcTableConnection()))
                    .stream().map(column -> new Field(
                            column.sourceColumn(),
                            column.selectable()
                                    && FIELD_CODE.matcher(
                                    column.sourceColumn()).matches()
                                    ? column.sourceColumn() : null,
                            column.sourceType() == null
                                    ? "UNSUPPORTED"
                                    : column.sourceType().name(),
                            column.nullable(), column.selectable(),
                            column.issueCode()))
                    .toList();
            var review = fields.stream().anyMatch(field ->
                    !field.selectable());
            return new Result(
                    true, true,
                    JdbcTableAdapterSupport.elapsedMillis(started),
                    review ? "SCHEMA_REVIEW_REQUIRED" : "SUCCESS",
                    review
                            ? "The MySQL table schema contains unsupported columns"
                            : "MySQL table schema discovery succeeded",
                    expectedVersion, fields);
        } catch (DataSourceException failure) {
            return failed(failure, expectedVersion, started);
        } catch (RuntimeException failure) {
            return new Result(
                    false, false,
                    JdbcTableAdapterSupport.elapsedMillis(started),
                    "DATA_SOURCE_JDBC_SCHEMA_FAILED",
                    "The MySQL table metadata could not be read",
                    expectedVersion, List.of());
        }
    }

    private static Result failed(
            DataSourceException failure,
            long expectedVersion,
            long started
    ) {
        return new Result(
                JdbcTableAdapterSupport.reachable(failure), false,
                JdbcTableAdapterSupport.elapsedMillis(started),
                failure.code(), failure.getMessage(), expectedVersion,
                List.of());
    }
}
