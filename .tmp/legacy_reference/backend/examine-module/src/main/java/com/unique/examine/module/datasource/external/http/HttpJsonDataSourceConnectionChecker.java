package com.unique.examine.module.datasource.external.http;

import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.service.DataSourceConnectionCheckUseCase;
import com.unique.examine.module.datasource.service.DataSourceService;

import java.util.Objects;

/** Executes one bounded, non-retrying administrator HTTP connection check. */
public final class HttpJsonDataSourceConnectionChecker
        implements DataSourceConnectionCheckUseCase {
    private final DataSourceService dataSources;
    private final HttpJsonDataSourceProbe probe;

    public HttpJsonDataSourceConnectionChecker(
            DataSourceService dataSources,
            HttpJsonDataSourceProbe probe
    ) {
        this.dataSources = Objects.requireNonNull(dataSources, "dataSources");
        this.probe = Objects.requireNonNull(probe, "probe");
    }

    @Override
    public Result check(
            DataSourceActor actor,
            long dataSourceId,
            long expectedVersion
    ) {
        Objects.requireNonNull(actor, "actor");
        var source = dataSources.detail(actor, dataSourceId);
        if (expectedVersion <= 0
                || source.draftVersion() != expectedVersion) {
            throw new DataSourceException(
                    "DATA_SOURCE_VERSION_CONFLICT",
                    "Data source draft changed; refresh before retrying");
        }
        var draft = source.draft();
        if (draft.sourceKind() != DataSourceDraft.SourceKind.HTTP_JSON
                || draft.httpConnection() == null) {
            throw new DataSourceException(
                    "DATA_SOURCE_CONNECTION_CHECK_INVALID",
                    "Connection check requires an HTTP JSON data-source draft");
        }
        var outcome = probe.execute(
                actor, draft.httpConnection(),
                HttpJsonDataSourceProbe.Purpose.CHECK);
        return new Result(
                outcome.reachable(), outcome.contractValid(),
                outcome.httpStatus(), outcome.durationMillis(),
                outcome.code(), outcome.message());
    }
}
