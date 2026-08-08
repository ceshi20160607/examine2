package com.unique.examine.module.report.runtime;

import com.unique.examine.module.datasource.runtime.DataSourceRuntimeService;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public final class NativeReportDataSourceRuntime
        implements ReportDataSourceRuntime {
    private final DataSourceRuntimeService sources;

    public NativeReportDataSourceRuntime(DataSourceRuntimeService sources) {
        this.sources = Objects.requireNonNull(sources, "sources");
    }

    @Override
    public SourceMetadata metadata(
            RuntimeSession session,
            long dataSourceId,
            long dataSourceVersionId
    ) {
        var source = sources.metadata(
                session, dataSourceId, dataSourceVersionId);
        return new SourceMetadata(
                source.id(), source.code(), source.name(),
                source.moduleCode(), source.versionId(),
                source.versionNumber(), source.schemaVersionId(),
                source.outputFields().stream().map(field ->
                        new SourceField(field.fieldCode(), field.fieldName(),
                                field.type())).toList());
    }

    @Override
    public SourceRows rows(
            RuntimeSession session,
            long dataSourceId,
            long dataSourceVersionId,
            int page,
            int size
    ) {
        var result = sources.rows(
                session, dataSourceId, dataSourceVersionId, page, size);
        return new SourceRows(result.rows().stream().map(row ->
                new SourceRow(row.recordId(), row.recordNo(), row.version(),
                        row.status(), row.title(), row.values().stream()
                        .map(value -> new SourceValue(
                                value.fieldCode(), value.fieldName(),
                                value.type(), value.value(),
                                value.displayValue())).toList(),
                        row.drillThrough())).toList(),
                result.page(), result.size(), result.total(),
                result.queryHash(), result.partial(), result.sourceKind(),
                result.failedSourceAliases());
    }
}
