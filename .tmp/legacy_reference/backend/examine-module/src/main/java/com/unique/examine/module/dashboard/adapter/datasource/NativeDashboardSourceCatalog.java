package com.unique.examine.module.dashboard.adapter.datasource;

import com.unique.examine.module.dashboard.port.DashboardSourceCatalog;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.port.DataSourceModuleCatalog;
import com.unique.examine.module.datasource.port.DataSourceRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Resolves dashboard source pins from the native published data-source model. */
@Component
public class NativeDashboardSourceCatalog implements DashboardSourceCatalog {
    private static final Set<String> NUMERIC_QUERY_TYPES = Set.of(
            "NUMBER", "PERCENT", "RATING", "PROGRESS");
    private static final Set<String> TEMPORAL_QUERY_TYPES = Set.of(
            "DATE", "DATETIME");
    private static final Set<String> GROUPABLE_QUERY_TYPES = Set.of(
            "TEXT", "NUMBER", "DATE", "DATETIME", "RADIO",
            "MEMBER", "DEPARTMENT", "PERCENT", "TIME",
            "SWITCH", "RATING", "PROGRESS", "STATUS");
    private final DataSourceRepository repository;
    private final DataSourceModuleCatalog modules;

    public NativeDashboardSourceCatalog(
            DataSourceRepository repository,
            DataSourceModuleCatalog modules
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.modules = Objects.requireNonNull(modules, "modules");
    }

    @Override
    public Optional<SourceRoot> source(
            long systemId,
            long tenantId,
            long dataSourceId
    ) {
        return repository.findById(systemId, tenantId, dataSourceId)
                .filter(value -> value.activeVersionId() == null
                        || repository.findVersionById(
                                        systemId, tenantId, dataSourceId,
                                        value.activeVersionId())
                                .isPresent())
                .map(value -> new SourceRoot(
                        value.id(), value.systemId(), value.tenantId(),
                        value.code(), value.activeVersionId(),
                        value.activeVersionNumber()));
    }

    @Override
    public Optional<SourceVersion> version(
            long systemId,
            long tenantId,
            long dataSourceId,
            long dataSourceVersionId
    ) {
        return repository.findVersionById(
                        systemId, tenantId, dataSourceId,
                        dataSourceVersionId)
                .map(version -> {
                    if (version.snapshot().sourceKind()
                            == DataSourceDraft.SourceKind.MULTI_MODULE_JOIN) {
                        var fields = joinFields(version.snapshot());
                        return new SourceVersion(
                                version.id(), version.dataSourceId(),
                                version.systemId(), version.tenantId(),
                                version.versionNumber(), version.code(),
                                version.moduleCode(), version.schemaVersionId(),
                                !fields.isEmpty(), fields.size(), fields);
                    }
                    var module = modules.publishedModule(
                            systemId, tenantId, version.moduleId());
                    var available = module.isPresent()
                            && module.get().schemaVersionId().equals(
                            version.schemaVersionId());
                    var readableOutputs = module
                            .map(value -> readableOutputCount(
                                    fieldCodes(version.snapshot()), value))
                            .orElse(0);
                    var fields = module
                            .map(value -> sourceFieldsByCode(
                                    fieldCodes(version.snapshot()), value))
                            .orElse(List.of());
                    return new SourceVersion(
                            version.id(), version.dataSourceId(),
                            version.systemId(), version.tenantId(),
                            version.versionNumber(), version.code(),
                            version.moduleCode(), version.schemaVersionId(),
                            available, readableOutputs, fields);
                });
    }

    static List<SourceField> sourceFields(
            java.util.List<DataSourceDraft.OutputField> outputFields,
            DataSourceModuleCatalog.PublishedModule module
    ) {
        return sourceFieldsByCode(outputFields.stream()
                .map(DataSourceDraft.OutputField::fieldCode).toList(), module);
    }

    private static List<SourceField> sourceFieldsByCode(
            java.util.List<String> outputFields,
            DataSourceModuleCatalog.PublishedModule module
    ) {
        Map<String, DataSourceModuleCatalog.FieldCapability> fields =
                module.fields().stream().collect(Collectors.toMap(
                        DataSourceModuleCatalog.FieldCapability::code,
                        Function.identity(), (left, right) -> left));
        return outputFields.stream()
                .map(fields::get)
                .filter(Objects::nonNull)
                .map(field -> new SourceField(
                        field.logicalFieldId(), field.code(), field.fieldName(),
                        field.type(), field.queryType(), field.available(),
                        field.available() && NUMERIC_QUERY_TYPES.contains(
                                field.queryType()),
                        field.available() && TEMPORAL_QUERY_TYPES.contains(
                                field.queryType()),
                        field.available()
                                && GROUPABLE_QUERY_TYPES.contains(
                                field.queryType())))
                .toList();
    }

    private static int readableOutputCount(
            java.util.List<String> outputFields,
            DataSourceModuleCatalog.PublishedModule module
    ) {
        Map<String, DataSourceModuleCatalog.FieldCapability> fields =
                module.fields().stream().collect(Collectors.toMap(
                        DataSourceModuleCatalog.FieldCapability::code,
                        Function.identity(), (left, right) -> left));
        return (int) outputFields.stream()
                .map(fields::get)
                .filter(Objects::nonNull)
                .filter(DataSourceModuleCatalog.FieldCapability::available)
                .count();
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

    private static List<SourceField> joinFields(DataSourceDraft snapshot) {
        if (snapshot.multiModuleJoin() == null) {
            return List.of();
        }
        return snapshot.multiModuleJoin().projections().stream()
                .filter(field -> field.logicalFieldId() > 0)
                .map(field -> new SourceField(
                        field.logicalFieldId(), field.fieldCode(),
                        field.fieldName(), field.type(), field.queryType(),
                        true, field.numeric(), field.temporal(),
                        field.groupable())).toList();
    }
}
