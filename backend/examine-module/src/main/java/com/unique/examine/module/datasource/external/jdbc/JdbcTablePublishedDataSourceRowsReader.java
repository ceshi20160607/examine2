package com.unique.examine.module.datasource.external.jdbc;

import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.domain.DataSourcePublication;
import com.unique.examine.module.datasource.service.PublishedJdbcTableDataSourceRowsReader;

import java.util.Objects;

public final class JdbcTablePublishedDataSourceRowsReader
        implements PublishedJdbcTableDataSourceRowsReader {
    private final JdbcTableSafeClient client;

    public JdbcTablePublishedDataSourceRowsReader(
            JdbcTableSafeClient client
    ) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public Result read(
            DataSourceActor actor,
            DataSourcePublication publication
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(publication, "publication");
        var version = publication.version();
        if (version.systemId() != actor.systemId()
                || version.tenantId() != actor.tenantId()) {
            throw new DataSourceException(
                    "DATA_SOURCE_JDBC_ROWS_UNAVAILABLE",
                    "Published JDBC table rows are unavailable");
        }
        var snapshot = version.snapshot();
        if (snapshot.sourceKind() != DataSourceDraft.SourceKind.JDBC_TABLE
                || snapshot.jdbcTableConnection() == null
                || snapshot.jdbcFieldProjections().isEmpty()) {
            throw new DataSourceException(
                    "DATA_SOURCE_JDBC_RUNTIME_UNAVAILABLE",
                    "Published JDBC rows require a JDBC table version");
        }
        try {
            var outcome = client.rows(
                    actor,
                    JdbcTableAdapterSupport.spec(
                            snapshot.jdbcTableConnection()),
                    JdbcTableAdapterSupport.projections(
                            snapshot.jdbcFieldProjections()));
            return new Result(
                    publication.root().id(), publication.root().code(),
                    version.id(), version.versionNumber(),
                    outcome.fields().stream().map(field -> new Field(
                            field.fieldCode(), field.sourceType().name()))
                            .toList(),
                    outcome.rows().stream().map(row -> new Row(
                            row.rowIndex(), row.values())).toList());
        } catch (DataSourceException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new DataSourceException(
                    "DATA_SOURCE_JDBC_READ_FAILED",
                    "The MySQL table could not be read");
        }
    }
}
