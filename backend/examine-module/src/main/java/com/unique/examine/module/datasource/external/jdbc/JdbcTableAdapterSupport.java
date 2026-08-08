package com.unique.examine.module.datasource.external.jdbc;

import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.domain.ModuleDataSource;
import com.unique.examine.module.datasource.service.DataSourceService;

import java.util.List;

final class JdbcTableAdapterSupport {
    private static final long MAXIMUM_DURATION_MILLIS = 30_000;

    private JdbcTableAdapterSupport() {
    }

    static ModuleDataSource exactJdbcDraft(
            DataSourceService dataSources,
            DataSourceActor actor,
            long dataSourceId,
            long expectedVersion,
            String invalidCode,
            String invalidMessage
    ) {
        var source = dataSources.detail(actor, dataSourceId);
        if (expectedVersion <= 0
                || source.draftVersion() != expectedVersion) {
            throw new DataSourceException(
                    "DATA_SOURCE_VERSION_CONFLICT",
                    "Data source draft changed; refresh before retrying");
        }
        requireJdbcDraft(source.draft(), invalidCode, invalidMessage);
        return source;
    }

    static void requireJdbcDraft(
            DataSourceDraft draft,
            String code,
            String message
    ) {
        if (draft == null
                || draft.sourceKind() != DataSourceDraft.SourceKind.JDBC_TABLE
                || draft.jdbcTableConnection() == null) {
            throw new DataSourceException(code, message);
        }
    }

    static JdbcTableSafeClient.ConnectionSpec spec(
            DataSourceDraft.JdbcTableConnection connection
    ) {
        return new JdbcTableSafeClient.ConnectionSpec(
                connection.host(), connection.port(),
                connection.databaseName(), connection.tableName(),
                connection.usernameSecretRef(),
                connection.passwordSecretRef(),
                connection.connectTimeoutSeconds(),
                connection.queryTimeoutSeconds());
    }

    static List<JdbcTableSafeClient.Projection> projections(
            List<DataSourceDraft.JdbcTableFieldProjection> projections
    ) {
        return projections.stream().map(projection ->
                new JdbcTableSafeClient.Projection(
                        projection.sourceColumn(), projection.fieldCode(),
                        JdbcTableSafeClient.SourceType.valueOf(
                                projection.sourceType().name())))
                .toList();
    }

    static boolean reachable(DataSourceException failure) {
        return switch (failure.code()) {
            case "DATA_SOURCE_JDBC_SAFE_TARGET",
                 "DATA_SOURCE_JDBC_SECRET_REF_INVALID",
                 "DATA_SOURCE_JDBC_SECRET_UNAVAILABLE",
                 "DATA_SOURCE_JDBC_CONNECT_TIMEOUT",
                 "DATA_SOURCE_JDBC_CONNECTION_FAILED" -> false;
            default -> true;
        };
    }

    static long elapsedMillis(long started) {
        return Math.min(MAXIMUM_DURATION_MILLIS, Math.max(
                0, (System.nanoTime() - started) / 1_000_000));
    }
}
