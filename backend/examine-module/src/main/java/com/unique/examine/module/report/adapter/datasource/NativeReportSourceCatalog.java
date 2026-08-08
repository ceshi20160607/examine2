package com.unique.examine.module.report.adapter.datasource;

import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceVersion;
import com.unique.examine.module.datasource.port.DataSourceModuleCatalog;
import com.unique.examine.module.datasource.port.DataSourceRepository;
import com.unique.examine.module.report.port.ReportSourceCatalog;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Resolves immutable report pins from the native published data-source model. */
@Component
public final class NativeReportSourceCatalog implements ReportSourceCatalog {
    private final DataSourceRepository sources;
    private final DataSourceModuleCatalog modules;

    public NativeReportSourceCatalog(
            DataSourceRepository sources,
            DataSourceModuleCatalog modules
    ) {
        this.sources = Objects.requireNonNull(sources, "sources");
        this.modules = Objects.requireNonNull(modules, "modules");
    }

    @Override
    public Optional<SourceVersion> active(
            long systemId,
            long tenantId,
            long dataSourceId
    ) {
        return sources.findActiveVersion(systemId, tenantId, dataSourceId)
                .map(this::sourceVersion);
    }

    @Override
    public Optional<SourceVersion> version(
            long systemId,
            long tenantId,
            long dataSourceId,
            long dataSourceVersionId
    ) {
        return sources.findVersionById(
                        systemId, tenantId, dataSourceId,
                        dataSourceVersionId)
                .map(this::sourceVersion);
    }

    private SourceVersion sourceVersion(DataSourceVersion source) {
        if (source.snapshot().sourceKind()
                == DataSourceDraft.SourceKind.MULTI_MODULE_JOIN) {
            var fields = source.snapshot().multiModuleJoin().projections()
                    .stream().filter(field -> field.logicalFieldId() > 0)
                    .map(field -> new Field(
                            field.logicalFieldId(), field.fieldCode(),
                            field.fieldName(), field.type(), field.queryType(),
                            true)).toList();
            return new SourceVersion(
                    source.dataSourceId(), source.systemId(), source.tenantId(),
                    source.code(), source.name(), source.id(),
                    source.versionNumber(), source.moduleId(),
                    source.moduleCode(), source.schemaVersionId(), fields);
        }
        var module = modules.publishedModule(
                source.systemId(), source.tenantId(), source.moduleId());
        var sameSchema = module.filter(value -> value.schemaVersionId()
                .equals(source.schemaVersionId()));
        var fields = sameSchema.map(value -> sourceFieldsByCode(
                fieldCodes(source.snapshot()), value)).orElse(List.of());
        return new SourceVersion(
                source.dataSourceId(), source.systemId(), source.tenantId(),
                source.code(), source.name(), source.id(),
                source.versionNumber(), source.moduleId(),
                source.moduleCode(), source.schemaVersionId(), fields);
    }

    static List<Field> sourceFields(
            List<DataSourceDraft.OutputField> outputFields,
            DataSourceModuleCatalog.PublishedModule module
    ) {
        return sourceFieldsByCode(outputFields.stream()
                .map(DataSourceDraft.OutputField::fieldCode).toList(), module);
    }

    private static List<Field> sourceFieldsByCode(
            List<String> outputFields,
            DataSourceModuleCatalog.PublishedModule module
    ) {
        Map<String, DataSourceModuleCatalog.FieldCapability> capabilities =
                module.fields().stream().collect(Collectors.toMap(
                        DataSourceModuleCatalog.FieldCapability::code,
                        Function.identity(), (left, right) -> left));
        return outputFields.stream()
                .map(capabilities::get)
                .filter(Objects::nonNull)
                .map(field -> new Field(
                        field.logicalFieldId(), field.code(),
                        field.fieldName(), field.type(), field.queryType(),
                        field.available()))
                .toList();
    }

    private static List<String> fieldCodes(DataSourceDraft snapshot) {
        return switch (snapshot.sourceKind()) {
            case NATIVE_MODULE -> snapshot.outputFields().stream()
                    .map(DataSourceDraft.OutputField::fieldCode).toList();
            case HTTP_JSON -> snapshot.httpFieldProjections().stream()
                    .map(DataSourceDraft.HttpJsonFieldProjection::fieldCode)
                    .toList();
            case JDBC_TABLE -> snapshot.jdbcFieldProjections().stream()
                    .map(DataSourceDraft.JdbcTableFieldProjection::fieldCode)
                    .toList();
            case MULTI_MODULE_JOIN -> snapshot.multiModuleJoin().projections()
                    .stream().map(DataSourceDraft.JoinProjection::fieldCode)
                    .toList();
        };
    }
}
