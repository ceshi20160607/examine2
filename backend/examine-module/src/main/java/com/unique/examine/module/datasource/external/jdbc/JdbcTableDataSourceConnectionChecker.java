package com.unique.examine.module.datasource.external.jdbc;

import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.service.DataSourceService;
import com.unique.examine.module.datasource.service.JdbcTableDataSourceConnectionCheckUseCase;

import java.util.Objects;

public final class JdbcTableDataSourceConnectionChecker
        implements JdbcTableDataSourceConnectionCheckUseCase {
    private final DataSourceService dataSources;
    private final JdbcTableSafeClient client;

    public JdbcTableDataSourceConnectionChecker(
            DataSourceService dataSources,
            JdbcTableSafeClient client
    ) {
        this.dataSources = Objects.requireNonNull(dataSources, "dataSources");
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public Result check(
            DataSourceActor actor,
            long dataSourceId,
            long expectedVersion
    ) {
        var source = JdbcTableAdapterSupport.exactJdbcDraft(
                dataSources, actor, dataSourceId, expectedVersion,
                "DATA_SOURCE_JDBC_CONNECTION_CHECK_INVALID",
                "Connection check requires a JDBC table data-source draft");
        var started = System.nanoTime();
        try {
            var outcome = client.check(actor, JdbcTableAdapterSupport.spec(
                    source.draft().jdbcTableConnection()));
            return new Result(
                    true, true, outcome.durationMillis(),
                    "SUCCESS", "MySQL connection check succeeded");
        } catch (DataSourceException failure) {
            return new Result(
                    JdbcTableAdapterSupport.reachable(failure), false,
                    JdbcTableAdapterSupport.elapsedMillis(started),
                    failure.code(), failure.getMessage());
        } catch (RuntimeException failure) {
            return new Result(
                    false, false,
                    JdbcTableAdapterSupport.elapsedMillis(started),
                    "DATA_SOURCE_JDBC_CONNECTION_FAILED",
                    "The MySQL connection check failed");
        }
    }
}
