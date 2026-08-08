package com.unique.examine.module.datasource.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.module.datasource.domain.DataSourceCheckReport;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.domain.DataSourceVersion;
import com.unique.examine.module.datasource.domain.ModuleDataSource;
import com.unique.examine.module.datasource.port.DataSourceModuleCatalog;
import com.unique.examine.module.datasource.service.DataSourceConnectionCheckUseCase;
import com.unique.examine.module.datasource.service.DataSourceDraftRowsPreviewUseCase;
import com.unique.examine.module.datasource.service.DataSourceSchemaDiscoveryUseCase;
import com.unique.examine.module.datasource.service.JdbcTableDataSourceConnectionCheckUseCase;
import com.unique.examine.module.datasource.service.JdbcTableDataSourceDraftRowsPreviewUseCase;
import com.unique.examine.module.datasource.service.JdbcTableDataSourceSchemaDiscoveryUseCase;

import java.util.Comparator;
import java.util.List;

public final class DataSourceMapping {
    private DataSourceMapping() {
    }

    public static long positiveId(String value, String field) {
        if (value == null || !value.matches("^[1-9][0-9]{0,18}$")) {
            throw invalid(field + " must be a positive decimal id string");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw invalid(field + " is outside the supported id range");
        }
    }

    public static DataSourceDraft draft(
            DataSourceRequests.SaveDraft request,
            ObjectMapper mapper
    ) {
        return draft(request, mapper, null);
    }

    public static DataSourceDraft draft(
            DataSourceRequests.SaveDraft request,
            ObjectMapper mapper,
            DataSourceDraft current
    ) {
        if (request == null) {
            throw invalid("Data source draft is required");
        }
        var outputs = request.outputFields().stream()
                .map(value -> new DataSourceDraft.OutputField(value.fieldCode()))
                .toList();
        var filters = request.fixedFilters().stream()
                .map(value -> new DataSourceDraft.FixedFilter(
                        value.fieldCode(), value.operator(),
                        canonicalJson(value.canonicalValue(), mapper)))
                .toList();
        DataSourceDraft.DefaultSort sort = null;
        if (request.defaultSort() != null) {
            final DataSourceDraft.Direction direction;
            try {
                direction = DataSourceDraft.Direction.valueOf(
                        request.defaultSort().direction());
            } catch (IllegalArgumentException exception) {
                throw invalid("Data source sort direction must be ASC or DESC");
            }
            sort = new DataSourceDraft.DefaultSort(
                    request.defaultSort().fieldCode(), direction);
        }
        return new DataSourceDraft(
                outputs, filters, sort, request.defaultTimeFieldCode(),
                sourceKind(request.sourceKind()),
                httpConnection(request.httpJsonConnection()),
                httpFieldProjections(request.httpFieldProjections()),
                jdbcTableConnection(
                        request.jdbcTableConnection(), current),
                jdbcFieldProjections(request.jdbcFieldProjections()),
                multiModuleJoin(request.multiModuleJoin()));
    }

    public static DataSourceDraft draft(DataSourceRequests.Create request) {
        if (request == null) {
            throw invalid("Data source create request is required");
        }
        return new DataSourceDraft(
                List.of(), List.of(), null, null,
                sourceKind(request.sourceKind()),
                httpConnection(request.httpJsonConnection()), List.of(),
                jdbcTableConnection(request.jdbcTableConnection(), null),
                List.of(), multiModuleJoin(request.multiModuleJoin()));
    }

    public static DataSourceViews.Source source(
            ModuleDataSource source,
            String moduleCode,
            ObjectMapper mapper
    ) {
        return new DataSourceViews.Source(
                Long.toString(source.id()), Long.toString(source.systemId()),
                Long.toString(source.tenantId()), source.code(),
                Long.toString(source.moduleId()), moduleCode, source.name(),
                source.description(), source.draftVersion(),
                string(source.activeVersionId()), source.activeVersionNumber(),
                source.createdAt().toString(), source.updatedAt().toString(),
                source.version(), draft(source.draft(), mapper));
    }

    public static DataSourceViews.CheckResult check(
            DataSourceCheckReport report
    ) {
        return new DataSourceViews.CheckResult(
                Long.toString(report.dataSourceId()), report.draftVersion(),
                report.schemaVersionId(), report.publishable(),
                report.blockerCount(), report.warningCount(),
                report.issues().stream().map(issue ->
                        new DataSourceViews.CheckIssue(
                                issue.severity().name(), issue.code(),
                                issue.path(), issue.message())).toList());
    }

    public static DataSourceViews.ConnectionCheckResult connectionCheck(
            DataSourceConnectionCheckUseCase.Result result
    ) {
        if (result == null) {
            throw new IllegalStateException(
                    "Data source connection check returned no result");
        }
        return new DataSourceViews.ConnectionCheckResult(
                result.reachable(), result.contractValid(),
                result.httpStatus(), result.durationMillis(), result.code(),
                result.message());
    }

    public static DataSourceViews.SchemaDiscoveryResult schemaDiscovery(
            DataSourceSchemaDiscoveryUseCase.Result result
    ) {
        if (result == null) {
            throw new IllegalStateException(
                    "Data source schema discovery returned no result");
        }
        return new DataSourceViews.SchemaDiscoveryResult(
                result.reachable(), result.contractValid(),
                result.httpStatus(), result.durationMillis(), result.code(),
                result.message(), result.checkedDraftVersion(),
                result.fields().stream().map(field ->
                        new DataSourceViews.SchemaDiscoveryField(
                                field.sourceField(),
                                field.suggestedFieldCode(),
                                field.inferredType(), field.nullable(),
                                field.selectable(), field.issueCode()))
                        .toList());
    }

    public static DataSourceViews.ConnectionCheckResult connectionCheck(
            JdbcTableDataSourceConnectionCheckUseCase.Result result
    ) {
        if (result == null) {
            throw new IllegalStateException(
                    "JDBC table connection check returned no result");
        }
        return new DataSourceViews.ConnectionCheckResult(
                result.reachable(), result.contractValid(), null,
                result.durationMillis(), result.code(), result.message());
    }

    public static DataSourceViews.SchemaDiscoveryResult schemaDiscovery(
            JdbcTableDataSourceSchemaDiscoveryUseCase.Result result
    ) {
        if (result == null) {
            throw new IllegalStateException(
                    "JDBC table schema discovery returned no result");
        }
        return new DataSourceViews.SchemaDiscoveryResult(
                result.reachable(), result.contractValid(), null,
                result.durationMillis(), result.code(), result.message(),
                result.checkedDraftVersion(),
                result.fields().stream().map(field ->
                        new DataSourceViews.SchemaDiscoveryField(
                                null, field.sourceColumn(),
                                field.suggestedFieldCode(),
                                field.inferredType(), field.nullable(),
                                field.selectable(), field.issueCode()))
                        .toList());
    }

    public static DataSourceViews.DraftRowsPreviewResult draftRowsPreview(
            DataSourceDraftRowsPreviewUseCase.Result result
    ) {
        if (result == null) {
            throw new IllegalStateException(
                    "Data source draft rows preview returned no result");
        }
        return new DataSourceViews.DraftRowsPreviewResult(
                result.reachable(), result.contractValid(),
                result.httpStatus(), result.durationMillis(), result.code(),
                result.message(), result.checkedDraftVersion(),
                result.fields().stream().map(field ->
                        new DataSourceViews.DraftRowsPreviewField(
                                field.fieldCode(), field.sourceType()))
                        .toList(),
                result.rows().stream().map(row ->
                        new DataSourceViews.DraftRowsPreviewRow(
                                row.rowIndex(), row.values()))
                        .toList());
    }

    public static DataSourceViews.DraftRowsPreviewResult draftRowsPreview(
            JdbcTableDataSourceDraftRowsPreviewUseCase.Result result
    ) {
        if (result == null) {
            throw new IllegalStateException(
                    "JDBC table draft rows preview returned no result");
        }
        return new DataSourceViews.DraftRowsPreviewResult(
                result.reachable(), result.contractValid(), null,
                result.durationMillis(), result.code(), result.message(),
                result.checkedDraftVersion(),
                result.fields().stream().map(field ->
                        new DataSourceViews.DraftRowsPreviewField(
                                field.fieldCode(), field.sourceType()))
                        .toList(),
                result.rows().stream().map(row ->
                        new DataSourceViews.DraftRowsPreviewRow(
                                row.rowIndex(), row.values()))
                        .toList());
    }

    public static DataSourceViews.Version version(
            DataSourceVersion version,
            Long activeVersionId,
            ObjectMapper mapper
    ) {
        return new DataSourceViews.Version(
                Long.toString(version.id()),
                Long.toString(version.dataSourceId()),
                version.versionNumber(), version.code(),
                Long.toString(version.moduleId()), version.moduleCode(),
                version.schemaVersionId(), version.name(),
                version.description(), draft(version.snapshot(), mapper),
                version.fingerprint(),
                Long.toString(version.publishedByMemberId()),
                version.publishedAt().toString(),
                activeVersionId != null && activeVersionId == version.id());
    }

    public static DataSourceViews.Catalog catalog(
            List<DataSourceModuleCatalog.PublishedModule> modules
    ) {
        var views = modules.stream()
                .sorted(Comparator.comparing(
                                DataSourceModuleCatalog.PublishedModule::moduleCode)
                        .thenComparingLong(
                                DataSourceModuleCatalog.PublishedModule::moduleId))
                .map(module -> new DataSourceViews.CatalogModule(
                        Long.toString(module.moduleId()), module.moduleCode(),
                        module.moduleName(), module.schemaVersionId(), true,
                        null, module.fields().stream()
                        .map(field -> new DataSourceViews.CatalogField(
                                field.code(), field.fieldName(), field.type(),
                                field.operators().stream().sorted().toList(),
                                field.sortable(), field.temporal(),
                                field.available(), field.available()
                                ? null : "FIELD_RUNTIME_UNAVAILABLE"))
                        .toList()))
                .toList();
        return new DataSourceViews.Catalog(views);
    }

    private static DataSourceViews.Draft draft(
            DataSourceDraft draft,
            ObjectMapper mapper
    ) {
        return new DataSourceViews.Draft(
                draft.outputFields().stream()
                        .map(field -> new DataSourceViews.OutputField(
                                field.fieldCode())).toList(),
                draft.fixedFilters().stream()
                        .map(filter -> new DataSourceViews.FixedFilter(
                                filter.fieldCode(), filter.operator(),
                                jsonValue(filter.canonicalValue(), mapper)))
                        .toList(),
                draft.defaultSort() == null ? null
                        : new DataSourceViews.DefaultSort(
                        draft.defaultSort().fieldCode(),
                        draft.defaultSort().direction().name()),
                draft.defaultTimeFieldCode(), draft.sourceKind().name(),
                httpConnection(draft.httpConnection()),
                draft.httpFieldProjections().stream().map(projection ->
                        new DataSourceViews.HttpJsonFieldProjection(
                                projection.sourceField(),
                                projection.fieldCode(),
                                projection.sourceType().name()))
                        .toList(),
                jdbcTableConnection(draft.jdbcTableConnection()),
                draft.jdbcFieldProjections().stream().map(projection ->
                        new DataSourceViews.JdbcTableFieldProjection(
                                projection.sourceColumn(),
                                projection.fieldCode(),
                                projection.sourceType().name()))
                        .toList(), multiModuleJoin(draft.multiModuleJoin()));
    }

    private static DataSourceDraft.SourceKind sourceKind(String value) {
        if (value == null) {
            return DataSourceDraft.SourceKind.NATIVE_MODULE;
        }
        try {
            return DataSourceDraft.SourceKind.valueOf(value);
        } catch (IllegalArgumentException invalidKind) {
            throw invalid(
                    "Data source kind must be NATIVE_MODULE, HTTP_JSON, "
                            + "JDBC_TABLE, or MULTI_MODULE_JOIN");
        }
    }

    private static DataSourceDraft.MultiModuleJoin multiModuleJoin(
            DataSourceRequests.MultiModuleJoin value
    ) {
        if (value == null) {
            return null;
        }
        try {
            return new DataSourceDraft.MultiModuleJoin(
                    value.inputs().stream().map(input ->
                            new DataSourceDraft.JoinInput(
                                    input.alias(), positiveId(
                                    input.dataSourceId(), "join dataSourceId"),
                                    positiveId(input.dataSourceVersionId(),
                                            "join dataSourceVersionId")))
                            .toList(),
                    value.edges().stream().map(edge ->
                            new DataSourceDraft.JoinEdge(
                                    edge.leftAlias(), edge.leftFieldCode(),
                                    edge.rightAlias(), edge.rightFieldCode(),
                                    DataSourceDraft.JoinType.valueOf(
                                            edge.joinType()),
                                    DataSourceDraft.JoinCardinality.valueOf(
                                            edge.cardinality())))
                            .toList(),
                    value.projections().stream().map(projection ->
                            new DataSourceDraft.JoinProjection(
                                    projection.sourceAlias(),
                                    projection.sourceFieldCode(),
                                    projection.fieldCode())).toList(),
                    DataSourceDraft.JoinFailureMode.valueOf(
                            value.failureMode()),
                    value.timeoutSeconds(), value.rowLimit());
        } catch (IllegalArgumentException invalidJoin) {
            throw invalid("Multi-module join enum value is invalid");
        }
    }

    private static DataSourceViews.MultiModuleJoin multiModuleJoin(
            DataSourceDraft.MultiModuleJoin value
    ) {
        if (value == null) {
            return null;
        }
        return new DataSourceViews.MultiModuleJoin(
                value.inputs().stream().map(input ->
                        new DataSourceViews.JoinInput(
                                input.alias(), Long.toString(
                                input.dataSourceId()), Long.toString(
                                input.dataSourceVersionId()))).toList(),
                value.edges().stream().map(edge ->
                        new DataSourceViews.JoinEdge(
                                edge.leftAlias(), edge.leftFieldCode(),
                                edge.rightAlias(), edge.rightFieldCode(),
                                edge.joinType().name(),
                                edge.cardinality().name())).toList(),
                value.projections().stream().map(projection ->
                        new DataSourceViews.JoinProjection(
                                projection.sourceAlias(),
                                projection.sourceFieldCode(),
                                projection.fieldCode(),
                                projection.logicalFieldId() > 0
                                        ? Long.toString(
                                        projection.logicalFieldId()) : null,
                                projection.fieldName(), projection.type(),
                                projection.queryType(), projection.numeric(),
                                projection.temporal(),
                                projection.groupable())).toList(),
                value.failureMode().name(), value.timeoutSeconds(),
                value.rowLimit());
    }

    private static DataSourceDraft.HttpJsonConnection httpConnection(
            DataSourceRequests.HttpJsonConnection value
    ) {
        return value == null ? null : new DataSourceDraft.HttpJsonConnection(
                value.endpoint(), value.authSecretRef(),
                value.timeoutSeconds());
    }

    private static DataSourceViews.HttpJsonConnection httpConnection(
            DataSourceDraft.HttpJsonConnection value
    ) {
        return value == null ? null : new DataSourceViews.HttpJsonConnection(
                value.endpoint(), value.authSecretRef(),
                value.timeoutSeconds());
    }

    private static DataSourceDraft.JdbcTableConnection jdbcTableConnection(
            DataSourceRequests.JdbcTableConnection value,
            DataSourceDraft current
    ) {
        if (value == null) {
            return null;
        }
        var previous = current == null ? null
                : current.jdbcTableConnection();
        return new DataSourceDraft.JdbcTableConnection(
                value.host(), value.port(), value.databaseName(),
                value.tableName(), retainedSecret(
                value.usernameSecretRef(), previous == null ? null
                        : previous.usernameSecretRef()), retainedSecret(
                value.passwordSecretRef(), previous == null ? null
                        : previous.passwordSecretRef()),
                value.connectTimeoutSeconds(), value.queryTimeoutSeconds());
    }

    private static DataSourceViews.JdbcTableConnection jdbcTableConnection(
            DataSourceDraft.JdbcTableConnection value
    ) {
        return value == null ? null
                : new DataSourceViews.JdbcTableConnection(
                value.host(), value.port(), value.databaseName(),
                value.tableName(), configured(value.usernameSecretRef()),
                configured(value.passwordSecretRef()),
                value.connectTimeoutSeconds(),
                value.queryTimeoutSeconds());
    }

    private static List<DataSourceDraft.HttpJsonFieldProjection>
    httpFieldProjections(
            List<DataSourceRequests.HttpJsonFieldProjection> values
    ) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(value ->
                new DataSourceDraft.HttpJsonFieldProjection(
                        value.sourceField(), value.fieldCode(),
                        sourceType(value.sourceType()))).toList();
    }

    private static List<DataSourceDraft.JdbcTableFieldProjection>
    jdbcFieldProjections(
            List<DataSourceRequests.JdbcTableFieldProjection> values
    ) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(value ->
                new DataSourceDraft.JdbcTableFieldProjection(
                        value.sourceColumn(), value.fieldCode(),
                        jdbcSourceType(value.sourceType()))).toList();
    }

    private static DataSourceDraft.HttpJsonSourceType sourceType(
            String value
    ) {
        if (value == null) {
            throw invalid("HTTP projection source type is required");
        }
        try {
            return DataSourceDraft.HttpJsonSourceType.valueOf(value);
        } catch (IllegalArgumentException invalidType) {
            throw invalid("HTTP projection source type is invalid");
        }
    }

    private static DataSourceDraft.JdbcTableSourceType jdbcSourceType(
            String value
    ) {
        if (value == null) {
            throw invalid("JDBC projection source type is required");
        }
        try {
            return DataSourceDraft.JdbcTableSourceType.valueOf(value);
        } catch (IllegalArgumentException invalidType) {
            throw invalid("JDBC projection source type is invalid");
        }
    }

    private static String retainedSecret(
            String requested,
            String current
    ) {
        return requested == null || requested.isBlank()
                ? current : requested;
    }

    private static boolean configured(String secretReference) {
        return secretReference != null && !secretReference.isBlank();
    }

    private static String canonicalJson(JsonNode value, ObjectMapper mapper) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw invalid("Data source filter value cannot be serialized");
        }
    }

    private static Object jsonValue(String value, ObjectMapper mapper) {
        if (value == null) {
            return null;
        }
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Persisted data source filter is not canonical JSON",
                    exception);
        }
    }

    private static String string(Long value) {
        return value == null ? null : Long.toString(value);
    }

    private static DataSourceException invalid(String message) {
        return new DataSourceException("DATA_SOURCE_REQUEST_INVALID", message);
    }
}
