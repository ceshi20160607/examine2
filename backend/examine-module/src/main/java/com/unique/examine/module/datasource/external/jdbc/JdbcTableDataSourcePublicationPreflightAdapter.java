package com.unique.examine.module.datasource.external.jdbc;

import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.service.JdbcTableDataSourcePublicationPreflight;

import java.util.Objects;

public final class JdbcTableDataSourcePublicationPreflightAdapter
        implements JdbcTableDataSourcePublicationPreflight {
    private final JdbcTableSafeClient client;

    public JdbcTableDataSourcePublicationPreflightAdapter(
            JdbcTableSafeClient client
    ) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public void verify(
            DataSourceActor actor,
            DataSourceDraft normalizedJdbcDraft
    ) {
        JdbcTableAdapterSupport.requireJdbcDraft(
                normalizedJdbcDraft,
                "DATA_SOURCE_JDBC_PUBLICATION_INVALID",
                "Publication preflight requires a JDBC table draft");
        try {
            client.verifyProjections(
                    actor,
                    JdbcTableAdapterSupport.spec(
                            normalizedJdbcDraft.jdbcTableConnection()),
                    JdbcTableAdapterSupport.projections(
                            normalizedJdbcDraft.jdbcFieldProjections()));
        } catch (DataSourceException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new DataSourceException(
                    "DATA_SOURCE_JDBC_PUBLICATION_FAILED",
                    "The MySQL publication preflight failed");
        }
    }
}
