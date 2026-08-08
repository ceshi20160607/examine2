package com.unique.examine.module.datasource.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DataSourceViews {
    private DataSourceViews() {
    }

    public record OutputField(String fieldCode) {
    }

    public record FixedFilter(
            String fieldCode,
            String operator,
            Object canonicalValue
    ) {
    }

    public record DefaultSort(String fieldCode, String direction) {
    }

    public record HttpJsonConnection(
            String endpoint,
            String authSecretRef,
            int timeoutSeconds
    ) {
    }

    public record HttpJsonFieldProjection(
            String sourceField,
            String fieldCode,
            String sourceType
    ) {
    }

    /** Safe JDBC connection view; secret references are never returned. */
    public record JdbcTableConnection(
            String host,
            int port,
            String databaseName,
            String tableName,
            boolean usernameConfigured,
            boolean passwordConfigured,
            int connectTimeoutSeconds,
            int queryTimeoutSeconds
    ) {
    }

    public record JdbcTableFieldProjection(
            String sourceColumn,
            String fieldCode,
            String sourceType
    ) {
    }

    public record MultiModuleJoin(
            List<JoinInput> inputs,
            List<JoinEdge> edges,
            List<JoinProjection> projections,
            String failureMode,
            int timeoutSeconds,
            int rowLimit
    ) {
        public MultiModuleJoin {
            inputs = List.copyOf(inputs);
            edges = List.copyOf(edges);
            projections = List.copyOf(projections);
        }
    }

    public record JoinInput(
            String alias,
            String dataSourceId,
            String dataSourceVersionId
    ) {
    }

    public record JoinEdge(
            String leftAlias,
            String leftFieldCode,
            String rightAlias,
            String rightFieldCode,
            String joinType,
            String cardinality
    ) {
    }

    public record JoinProjection(
            String sourceAlias,
            String sourceFieldCode,
            String fieldCode,
            String logicalFieldId,
            String fieldName,
            String type,
            String queryType,
            boolean numeric,
            boolean temporal,
            boolean groupable
    ) {
    }

    public record Draft(
            List<OutputField> outputFields,
            List<FixedFilter> fixedFilters,
            DefaultSort defaultSort,
            String defaultTimeFieldCode,
            String sourceKind,
            HttpJsonConnection httpJsonConnection,
            List<HttpJsonFieldProjection> httpFieldProjections,
            JdbcTableConnection jdbcTableConnection,
            List<JdbcTableFieldProjection> jdbcFieldProjections,
            MultiModuleJoin multiModuleJoin
    ) {
        public Draft {
            outputFields = List.copyOf(outputFields);
            fixedFilters = List.copyOf(fixedFilters);
            httpFieldProjections = List.copyOf(httpFieldProjections);
            jdbcFieldProjections = List.copyOf(jdbcFieldProjections);
        }

        /** Compatibility constructor for Native/HTTP fixtures. */
        public Draft(
                List<OutputField> outputFields,
                List<FixedFilter> fixedFilters,
                DefaultSort defaultSort,
                String defaultTimeFieldCode,
                String sourceKind,
                HttpJsonConnection httpJsonConnection,
                List<HttpJsonFieldProjection> httpFieldProjections
        ) {
            this(outputFields, fixedFilters, defaultSort,
                    defaultTimeFieldCode, sourceKind, httpJsonConnection,
                    httpFieldProjections, null, List.of(), null);
        }

        /** Compatibility constructor for Native/HTTP/JDBC fixtures. */
        public Draft(
                List<OutputField> outputFields,
                List<FixedFilter> fixedFilters,
                DefaultSort defaultSort,
                String defaultTimeFieldCode,
                String sourceKind,
                HttpJsonConnection httpJsonConnection,
                List<HttpJsonFieldProjection> httpFieldProjections,
                JdbcTableConnection jdbcTableConnection,
                List<JdbcTableFieldProjection> jdbcFieldProjections
        ) {
            this(outputFields, fixedFilters, defaultSort,
                    defaultTimeFieldCode, sourceKind, httpJsonConnection,
                    httpFieldProjections, jdbcTableConnection,
                    jdbcFieldProjections, null);
        }
    }

    public record Source(
            String id,
            String systemId,
            String tenantId,
            String code,
            String moduleId,
            String moduleCode,
            String name,
            String description,
            long draftVersion,
            String activeVersionId,
            Integer activeVersionNumber,
            String createdAt,
            String updatedAt,
            long version,
            Draft draft
    ) {
    }

    public record CheckIssue(
            String severity,
            String code,
            String path,
            String message
    ) {
    }

    public record CheckResult(
            String dataSourceId,
            long checkedDraftVersion,
            String schemaVersionId,
            boolean valid,
            long blockerCount,
            long warningCount,
            List<CheckIssue> issues
    ) {
        public CheckResult {
            issues = List.copyOf(issues);
        }
    }

    public record Version(
            String id,
            String dataSourceId,
            int versionNumber,
            String code,
            String moduleId,
            String moduleCode,
            String schemaVersionId,
            String name,
            String description,
            Draft snapshot,
            String fingerprint,
            String publishedBy,
            String publishedAt,
            boolean active
    ) {
    }

    public record PublishResult(Source source, Version version) {
    }

    public record ConnectionCheckResult(
            boolean reachable,
            boolean contractValid,
            Integer httpStatus,
            long durationMillis,
            String code,
            String message
    ) {
    }

    public record SchemaDiscoveryField(
            String sourceField,
            String sourceColumn,
            String suggestedFieldCode,
            String inferredType,
            boolean nullable,
            boolean selectable,
            String issueCode
    ) {
        /** Compatibility constructor for HTTP schema discovery. */
        public SchemaDiscoveryField(
                String sourceField,
                String suggestedFieldCode,
                String inferredType,
                boolean nullable,
                boolean selectable,
                String issueCode
        ) {
            this(sourceField, null, suggestedFieldCode, inferredType,
                    nullable, selectable, issueCode);
        }
    }

    public record SchemaDiscoveryResult(
            boolean reachable,
            boolean contractValid,
            Integer httpStatus,
            long durationMillis,
            String code,
            String message,
            long checkedDraftVersion,
            List<SchemaDiscoveryField> fields
    ) {
        public SchemaDiscoveryResult {
            fields = List.copyOf(fields);
        }
    }

    public record DraftRowsPreviewField(
            String fieldCode,
            String sourceType
    ) {
    }

    public record DraftRowsPreviewRow(
            int rowIndex,
            Map<String, Object> values
    ) {
        public DraftRowsPreviewRow {
            var ordered = new LinkedHashMap<String, Object>();
            if (values != null) {
                ordered.putAll(values);
            }
            values = Collections.unmodifiableMap(ordered);
        }

        @Override
        public String toString() {
            return "DataSourceViews.DraftRowsPreviewRow[rowIndex="
                    + rowIndex + ", values=redacted]";
        }
    }

    public record DraftRowsPreviewResult(
            boolean reachable,
            boolean contractValid,
            Integer httpStatus,
            long durationMillis,
            String code,
            String message,
            long checkedDraftVersion,
            List<DraftRowsPreviewField> fields,
            List<DraftRowsPreviewRow> rows
    ) {
        public DraftRowsPreviewResult {
            fields = fields == null ? List.of() : List.copyOf(fields);
            rows = rows == null ? List.of() : List.copyOf(rows);
        }

        @Override
        public String toString() {
            return "DataSourceViews.DraftRowsPreviewResult[reachable="
                    + reachable + ", contractValid=" + contractValid
                    + ", httpStatus=" + httpStatus
                    + ", durationMillis=" + durationMillis
                    + ", code=" + code + ", message=" + message
                    + ", checkedDraftVersion=" + checkedDraftVersion
                    + ", fields=" + fields + ", rows=redacted]";
        }
    }

    public record Catalog(List<CatalogModule> modules) {
        public Catalog {
            modules = List.copyOf(modules);
        }
    }

    public record CatalogModule(
            String moduleId,
            String moduleCode,
            String moduleName,
            String schemaVersionId,
            boolean available,
            String unavailableReason,
            List<CatalogField> fields
    ) {
        public CatalogModule {
            fields = List.copyOf(fields);
        }
    }

    public record CatalogField(
            String fieldCode,
            String fieldName,
            String type,
            List<String> operators,
            boolean sortable,
            boolean temporal,
            boolean available,
            String unavailableReason
    ) {
        public CatalogField {
            operators = List.copyOf(operators);
        }
    }

    public record RuntimeField(
            String fieldCode,
            String fieldName,
            String type
    ) {
    }

    public record RuntimeMetadata(
            String id,
            String code,
            String name,
            String description,
            String moduleCode,
            String versionId,
            String activeVersionId,
            int versionNumber,
            int activeVersionNumber,
            String schemaVersionId,
            List<RuntimeField> outputFields,
            List<RuntimeField> fields,
            DefaultSort defaultSort,
            String defaultTimeFieldCode
    ) {
        public RuntimeMetadata {
            outputFields = List.copyOf(outputFields);
            fields = List.copyOf(fields);
        }
    }

    public record RuntimeValue(
            String fieldCode,
            String fieldName,
            String type,
            Object value,
            String displayValue
    ) {
    }

    public record RuntimeRow(
            String recordId,
            String recordNo,
            long version,
            String status,
            String title,
            List<RuntimeValue> values,
            Map<String, String> drillThrough
    ) {
        public RuntimeRow {
            values = List.copyOf(values);
            var links = new LinkedHashMap<String, String>();
            if (drillThrough != null) {
                links.putAll(drillThrough);
            }
            drillThrough = Collections.unmodifiableMap(links);
        }

        public RuntimeRow(
                String recordId,
                String recordNo,
                long version,
                String status,
                String title,
                List<RuntimeValue> values
        ) {
            this(recordId, recordNo, version, status, title, values, Map.of());
        }
    }

    public record RuntimeRows(
            List<RuntimeRow> rows,
            List<RuntimeRow> items,
            int page,
            int size,
            long total,
            String queryHash,
            boolean partial,
            String sourceKind,
            int sourceRowLimit,
            List<String> failedSourceAliases
    ) {
        public RuntimeRows {
            rows = List.copyOf(rows);
            items = rows;
            failedSourceAliases = failedSourceAliases == null
                    ? List.of() : List.copyOf(failedSourceAliases);
        }

        public RuntimeRows(
                List<RuntimeRow> rows,
                List<RuntimeRow> items,
                int page,
                int size,
                long total,
                String queryHash,
                boolean partial,
                String sourceKind,
                int sourceRowLimit
        ) {
            this(rows, items, page, size, total, queryHash, partial,
                    sourceKind, sourceRowLimit, List.of());
        }

        public RuntimeRows(
                List<RuntimeRow> rows,
                List<RuntimeRow> items,
                int page,
                int size,
                long total,
                String queryHash
        ) {
            this(rows, items, page, size, total, queryHash,
                    false, "NATIVE_MODULE", 0, List.of());
        }
    }

    public record PublishedHttpRowsField(
            String fieldCode,
            String sourceType
    ) {
    }

    public record PublishedHttpRowsRow(
            int rowIndex,
            Map<String, Object> values
    ) {
        public PublishedHttpRowsRow {
            var ordered = new LinkedHashMap<String, Object>();
            if (values != null) {
                ordered.putAll(values);
            }
            values = Collections.unmodifiableMap(ordered);
        }

        @Override
        public String toString() {
            return "DataSourceViews.PublishedHttpRowsRow[rowIndex="
                    + rowIndex + ", values=redacted]";
        }
    }

    public record PublishedHttpRowsResult(
            String dataSourceId,
            String dataSourceCode,
            String dataSourceVersionId,
            int dataSourceVersionNumber,
            List<PublishedHttpRowsField> fields,
            List<PublishedHttpRowsRow> rows
    ) {
        public PublishedHttpRowsResult {
            fields = fields == null ? List.of() : List.copyOf(fields);
            rows = rows == null ? List.of() : List.copyOf(rows);
        }

        @Override
        public String toString() {
            return "DataSourceViews.PublishedHttpRowsResult[dataSourceId="
                    + dataSourceId + ", dataSourceCode=" + dataSourceCode
                    + ", dataSourceVersionId=" + dataSourceVersionId
                    + ", dataSourceVersionNumber="
                    + dataSourceVersionNumber
                    + ", fields=" + fields + ", rows=redacted]";
        }
    }

    public record PublishedJdbcTableRowsField(
            String fieldCode,
            String sourceType
    ) {
    }

    public record PublishedJdbcTableRowsRow(
            int rowIndex,
            Map<String, Object> values
    ) {
        public PublishedJdbcTableRowsRow {
            var ordered = new LinkedHashMap<String, Object>();
            if (values != null) {
                ordered.putAll(values);
            }
            values = Collections.unmodifiableMap(ordered);
        }

        @Override
        public String toString() {
            return "DataSourceViews.PublishedJdbcTableRowsRow[rowIndex="
                    + rowIndex + ", values=redacted]";
        }
    }

    public record PublishedJdbcTableRowsResult(
            String dataSourceId,
            String dataSourceCode,
            String dataSourceVersionId,
            int dataSourceVersionNumber,
            List<PublishedJdbcTableRowsField> fields,
            List<PublishedJdbcTableRowsRow> rows
    ) {
        public PublishedJdbcTableRowsResult {
            fields = fields == null ? List.of() : List.copyOf(fields);
            rows = rows == null ? List.of() : List.copyOf(rows);
        }

        @Override
        public String toString() {
            return "DataSourceViews.PublishedJdbcTableRowsResult["
                    + "dataSourceId=" + dataSourceId
                    + ", dataSourceCode=" + dataSourceCode
                    + ", dataSourceVersionId=" + dataSourceVersionId
                    + ", dataSourceVersionNumber="
                    + dataSourceVersionNumber
                    + ", fields=" + fields + ", rows=redacted]";
        }
    }
}
