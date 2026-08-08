package com.unique.examine.module.datasource.runtime;

import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.query.NativeRecordAggregatePlan;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.RecordRuntimeService;
import org.springframework.stereotype.Component;

@Component
public final class NativeDataSourceRecordQueryGateway
        implements DataSourceRecordQueryGateway {
    private final RecordRuntimeService records;

    public NativeDataSourceRecordQueryGateway(RecordRuntimeService records) {
        this.records = records;
    }

    @Override
    public RecordRuntimeViews.RecordSchema schema(
            RuntimeSession session,
            String moduleCode
    ) {
        return records.schema(session, moduleCode);
    }

    @Override
    public RecordRuntimeViews.RecordSchema schema(
            RuntimeSession session,
            String moduleCode,
            String schemaVersionId
    ) {
        return records.schema(session, moduleCode, schemaVersionId);
    }

    @Override
    public RecordRuntimeViews.RecordPage query(
            RuntimeSession session,
            String moduleCode,
            String queryJson
    ) {
        return records.query(session, moduleCode, queryJson);
    }

    @Override
    public NativeRecordAggregatePlan prepareActiveAggregate(
            RuntimeSession session,
            String moduleCode,
            String queryJson
    ) {
        return records.prepareActiveAggregate(
                session, moduleCode, queryJson);
    }

    @Override
    public NativeRecordAggregatePlan prepareActiveAggregate(
            RuntimeSession session,
            String moduleCode,
            String schemaVersionId,
            String queryJson
    ) {
        return records.prepareActiveAggregate(
                session, moduleCode, schemaVersionId, queryJson);
    }
}
