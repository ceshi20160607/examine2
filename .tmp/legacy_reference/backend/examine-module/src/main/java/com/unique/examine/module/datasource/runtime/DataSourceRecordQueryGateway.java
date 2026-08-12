package com.unique.examine.module.datasource.runtime;

import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.query.NativeRecordAggregatePlan;
import com.unique.examine.module.runtime.security.RuntimeSession;

public interface DataSourceRecordQueryGateway {
    RecordRuntimeViews.RecordSchema schema(
            RuntimeSession session,
            String moduleCode);

    default RecordRuntimeViews.RecordSchema schema(
            RuntimeSession session,
            String moduleCode,
            String schemaVersionId
    ) {
        throw new UnsupportedOperationException(
                "Historical native schema is unavailable");
    }

    RecordRuntimeViews.RecordPage query(
            RuntimeSession session,
            String moduleCode,
            String queryJson);

    default NativeRecordAggregatePlan prepareActiveAggregate(
            RuntimeSession session,
            String moduleCode,
            String queryJson
    ) {
        throw new UnsupportedOperationException(
                "Native aggregate preparation is unavailable");
    }

    default NativeRecordAggregatePlan prepareActiveAggregate(
            RuntimeSession session,
            String moduleCode,
            String schemaVersionId,
            String queryJson
    ) {
        throw new UnsupportedOperationException(
                "Historical native aggregate preparation is unavailable");
    }
}
