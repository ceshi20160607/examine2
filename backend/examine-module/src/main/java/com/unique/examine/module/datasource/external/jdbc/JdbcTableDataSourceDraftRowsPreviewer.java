package com.unique.examine.module.datasource.external.jdbc;

import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceCheckReport;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.service.DataSourceService;
import com.unique.examine.module.datasource.service.JdbcTableDataSourceDraftRowsPreviewUseCase;

import java.util.List;
import java.util.Objects;

public final class JdbcTableDataSourceDraftRowsPreviewer
        implements JdbcTableDataSourceDraftRowsPreviewUseCase {
    private final DataSourceService dataSources;
    private final JdbcTableSafeClient client;

    public JdbcTableDataSourceDraftRowsPreviewer(
            DataSourceService dataSources,
            JdbcTableSafeClient client
    ) {
        this.dataSources = Objects.requireNonNull(dataSources, "dataSources");
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public Result preview(
            DataSourceActor actor,
            long dataSourceId,
            long expectedVersion
    ) {
        var source = JdbcTableAdapterSupport.exactJdbcDraft(
                dataSources, actor, dataSourceId, expectedVersion,
                "DATA_SOURCE_JDBC_PREVIEW_INVALID",
                "Rows preview requires a JDBC table data-source draft");
        var report = dataSources.check(actor, dataSourceId);
        if (report.draftVersion() != expectedVersion) {
            throw new DataSourceException(
                    "DATA_SOURCE_VERSION_CONFLICT",
                    "Data source draft changed; refresh before retrying");
        }
        if (report.issues().stream().anyMatch(issue ->
                issue.severity() == DataSourceCheckReport.Severity.BLOCKER
                        && !"SOURCE_RUNTIME_UNAVAILABLE".equals(
                        issue.code()))) {
            throw new DataSourceException(
                    "DATA_SOURCE_JDBC_PREVIEW_BLOCKED",
                    "The JDBC table draft is not eligible for preview");
        }

        var started = System.nanoTime();
        try {
            var outcome = client.rows(
                    actor,
                    JdbcTableAdapterSupport.spec(
                            source.draft().jdbcTableConnection()),
                    JdbcTableAdapterSupport.projections(
                            source.draft().jdbcFieldProjections()));
            return new Result(
                    true, true,
                    JdbcTableAdapterSupport.elapsedMillis(started),
                    "SUCCESS", "MySQL table rows preview succeeded",
                    expectedVersion,
                    outcome.fields().stream().map(field -> new Field(
                            field.fieldCode(), field.sourceType().name()))
                            .toList(),
                    outcome.rows().stream().map(row -> new Row(
                            row.rowIndex(), row.values())).toList());
        } catch (DataSourceException failure) {
            return new Result(
                    JdbcTableAdapterSupport.reachable(failure), false,
                    JdbcTableAdapterSupport.elapsedMillis(started),
                    failure.code(), failure.getMessage(), expectedVersion,
                    List.of(), List.of());
        } catch (RuntimeException failure) {
            return new Result(
                    false, false,
                    JdbcTableAdapterSupport.elapsedMillis(started),
                    "DATA_SOURCE_JDBC_READ_FAILED",
                    "The MySQL table could not be read",
                    expectedVersion, List.of(), List.of());
        }
    }
}
