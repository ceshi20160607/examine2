package com.unique.examine.module.datasource.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.module.datasource.api.DataSourceViews;
import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.domain.DataSourcePublication;
import com.unique.examine.module.datasource.service.DataSourceService;
import com.unique.examine.module.datasource.service.PublishedHttpDataSourceRowsReader;
import com.unique.examine.module.datasource.service.PublishedJdbcTableDataSourceRowsReader;
import com.unique.examine.module.datasource.service.PublishedMultiModuleJoinRowsReader;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public final class DataSourceRuntimeService
        implements ExactPublishedDataSourceRowsReader {
    private static final int MAX_PAGE_SIZE = 200;

    private final DataSourceService sources;
    private final DataSourceRecordQueryGateway records;
    private final ObjectMapper mapper;
    private final PublishedHttpDataSourceRowsReader publishedHttpRows;
    private final PublishedJdbcTableDataSourceRowsReader publishedJdbcRows;
    private final ObjectProvider<PublishedMultiModuleJoinRowsReader>
            publishedJoinRows;

    @Autowired
    public DataSourceRuntimeService(
            DataSourceService sources,
            DataSourceRecordQueryGateway records,
            ObjectMapper mapper,
            ObjectProvider<PublishedHttpDataSourceRowsReader> readers,
            ObjectProvider<PublishedJdbcTableDataSourceRowsReader> jdbcReaders,
            ObjectProvider<PublishedMultiModuleJoinRowsReader> joinReaders
    ) {
        this(sources, records, mapper, readers.getIfUnique(
                        DataSourceRuntimeService::unavailableHttpRowsReader),
                jdbcReaders.getIfUnique(
                        DataSourceRuntimeService::unavailableJdbcRowsReader),
                joinReaders);
    }

    public DataSourceRuntimeService(
            DataSourceService sources,
            DataSourceRecordQueryGateway records,
            ObjectMapper mapper,
            ObjectProvider<PublishedHttpDataSourceRowsReader> readers
    ) {
        this(sources, records, mapper, readers.getIfUnique(
                        DataSourceRuntimeService::unavailableHttpRowsReader),
                unavailableJdbcRowsReader());
    }

    public DataSourceRuntimeService(
            DataSourceService sources,
            DataSourceRecordQueryGateway records,
            ObjectMapper mapper,
            PublishedHttpDataSourceRowsReader publishedHttpRows
    ) {
        this(sources, records, mapper, publishedHttpRows,
                unavailableJdbcRowsReader());
    }

    public DataSourceRuntimeService(
            DataSourceService sources,
            DataSourceRecordQueryGateway records,
            ObjectMapper mapper,
            PublishedHttpDataSourceRowsReader publishedHttpRows,
            PublishedJdbcTableDataSourceRowsReader publishedJdbcRows
    ) {
        this(sources, records, mapper, publishedHttpRows, publishedJdbcRows,
                null);
    }

    private DataSourceRuntimeService(
            DataSourceService sources,
            DataSourceRecordQueryGateway records,
            ObjectMapper mapper,
            PublishedHttpDataSourceRowsReader publishedHttpRows,
            PublishedJdbcTableDataSourceRowsReader publishedJdbcRows,
            ObjectProvider<PublishedMultiModuleJoinRowsReader> joinReaders
    ) {
        this.sources = sources;
        this.records = records;
        this.mapper = mapper;
        this.publishedHttpRows = publishedHttpRows;
        this.publishedJdbcRows = publishedJdbcRows;
        this.publishedJoinRows = joinReaders;
    }

    /** Compatibility constructor for Native-only callers and fixtures. */
    public DataSourceRuntimeService(
            DataSourceService sources,
            DataSourceRecordQueryGateway records,
            ObjectMapper mapper
    ) {
        this(sources, records, mapper, unavailableHttpRowsReader());
    }

    public DataSourceViews.RuntimeMetadata metadata(
            RuntimeSession session,
            String code
    ) {
        return resolved(session, code).metadata();
    }

    public DataSourceViews.RuntimeMetadata metadata(
            RuntimeSession session,
            long dataSourceId,
            long versionId
    ) {
        return resolved(session, dataSourceId, versionId).metadata();
    }

    public DataSourceViews.RuntimeRows rows(
            RuntimeSession session,
            String code,
            int page,
            int size
    ) {
        requirePage(page, size);
        return rows(resolved(session, code), session, page, size);
    }

    @Override
    public DataSourceViews.RuntimeRows rows(
            RuntimeSession session,
            long dataSourceId,
            long versionId,
            int page,
            int size
    ) {
        requirePage(page, size);
        return rows(resolved(session, dataSourceId, versionId),
                session, page, size);
    }

    public DataSourceViews.PublishedHttpRowsResult publishedHttpRows(
            RuntimeSession session,
            String code
    ) {
        var actor = actor(session);
        var active = sources.active(actor, code);
        return publishedHttpRows(
                session, actor,
                new DataSourcePublication(active.root(), active.version()));
    }

    public DataSourceViews.PublishedHttpRowsResult publishedHttpRows(
            RuntimeSession session,
            long dataSourceId,
            long versionId
    ) {
        var actor = actor(session);
        return publishedHttpRows(
                session, actor,
                sources.publication(actor, dataSourceId, versionId));
    }

    private DataSourceViews.PublishedHttpRowsResult publishedHttpRows(
            RuntimeSession session,
            DataSourceActor actor,
            DataSourcePublication publication
    ) {
        requireHttpPublication(publication);
        // Reuse the current runtime module-view authorization boundary. The
        // HTTP executor remains independent from the Native record query.
        records.schema(session, publication.version().moduleCode());
        var result = publishedHttpRows.read(actor, publication);
        requireHttpRowsIdentity(publication, result);
        return new DataSourceViews.PublishedHttpRowsResult(
                Long.toString(result.dataSourceId()),
                result.dataSourceCode(), Long.toString(result.versionId()),
                result.versionNumber(), result.fields().stream().map(field ->
                new DataSourceViews.PublishedHttpRowsField(
                        field.fieldCode(), field.sourceType())).toList(),
                result.rows().stream().map(row ->
                new DataSourceViews.PublishedHttpRowsRow(
                        row.rowIndex(), row.values())).toList());
    }

    public DataSourceViews.PublishedJdbcTableRowsResult publishedJdbcRows(
            RuntimeSession session,
            String code
    ) {
        var actor = actor(session);
        var active = sources.active(actor, code);
        return publishedJdbcRows(
                session, actor,
                new DataSourcePublication(active.root(), active.version()));
    }

    public DataSourceViews.PublishedJdbcTableRowsResult publishedJdbcRows(
            RuntimeSession session,
            long dataSourceId,
            long versionId
    ) {
        var actor = actor(session);
        return publishedJdbcRows(
                session, actor,
                sources.publication(actor, dataSourceId, versionId));
    }

    private DataSourceViews.PublishedJdbcTableRowsResult publishedJdbcRows(
            RuntimeSession session,
            DataSourceActor actor,
            DataSourcePublication publication
    ) {
        requireJdbcPublication(publication);
        // Reuse module-view authorization without entering Native querying.
        records.schema(session, publication.version().moduleCode());
        var result = publishedJdbcRows.read(actor, publication);
        requireJdbcRowsIdentity(publication, result);
        return new DataSourceViews.PublishedJdbcTableRowsResult(
                Long.toString(result.dataSourceId()),
                result.dataSourceCode(), Long.toString(result.versionId()),
                result.versionNumber(), result.fields().stream().map(field ->
                new DataSourceViews.PublishedJdbcTableRowsField(
                        field.fieldCode(), field.sourceType())).toList(),
                result.rows().stream().map(row ->
                new DataSourceViews.PublishedJdbcTableRowsRow(
                        row.rowIndex(), row.values())).toList());
    }

    private DataSourceViews.RuntimeRows rows(
            Resolved resolved,
            RuntimeSession session,
            int page,
            int size
    ) {
        var source = resolved.source();
        if (source.version().snapshot().sourceKind()
                != DataSourceDraft.SourceKind.NATIVE_MODULE) {
            return externalRows(resolved, session, page, size);
        }
        var query = query(
                source.version(), resolved.schema(),
                resolved.outputFields(), page, size);
        var nativePage = records.query(
                session, source.version().moduleCode(), query);
        var rows = nativePage.rows().stream().map(row ->
                new DataSourceViews.RuntimeRow(
                        row.recordId(), row.recordNo(), row.version(),
                        row.status(), row.title(), row.values().stream()
                        .map(value -> new DataSourceViews.RuntimeValue(
                                value.fieldCode(), value.fieldName(),
                                value.type(), value.value(),
                                value.displayValue()))
                        .toList())).toList();
        return new DataSourceViews.RuntimeRows(
                rows, rows, nativePage.page(), nativePage.size(),
                nativePage.total(), nativePage.queryHash());
    }

    private DataSourceViews.RuntimeRows externalRows(
            Resolved resolved,
            RuntimeSession session,
            int page,
            int size
    ) {
        var publication = resolved.source();
        var kind = publication.version().snapshot().sourceKind();
        if (kind == DataSourceDraft.SourceKind.MULTI_MODULE_JOIN) {
            var reader = publishedJoinRows == null ? null
                    : publishedJoinRows.getIfUnique();
            if (reader == null) {
                throw new DataSourceException(
                        "DATA_SOURCE_JOIN_UNAVAILABLE",
                        "Published multi-module join runtime is unavailable");
            }
            var result = reader.read(session, publication);
            if (result.dataSourceId() != publication.root().id()
                    || result.versionId() != publication.version().id()
                    || result.versionNumber()
                    != publication.version().versionNumber()) {
                throw new IllegalStateException(
                        "Multi-module join reader returned inconsistent identity");
            }
            var rows = new java.util.ArrayList<DataSourceViews.RuntimeRow>();
            for (var row : result.rows()) {
                var projected = resolved.outputFields().stream().map(field -> {
                    var value = row.values().get(field.fieldCode());
                    return new DataSourceViews.RuntimeValue(
                            field.fieldCode(), field.fieldName(), field.type(),
                            value, display(value));
                }).toList();
                rows.add(new DataSourceViews.RuntimeRow(
                        row.recordId(), row.recordId(), 1, "ACTIVE",
                        firstDisplay(projected, publication.version().name()),
                        projected, row.drillThrough()));
            }
            var offset = (long) (page - 1) * size;
            var from = (int) Math.min(rows.size(), offset);
            var to = Math.min(rows.size(), from + size);
            var visible = rows.subList(from, to);
            return new DataSourceViews.RuntimeRows(
                    visible, visible, page, size, rows.size(),
                    result.queryHash(), result.partial(),
                    kind.name(), result.sourceRowLimit(),
                    result.failedAliases());
        }
        if (page > 1) {
            return new DataSourceViews.RuntimeRows(
                    List.of(), List.of(), page, size, 0,
                    externalQueryHash(publication), true, kind.name(), 25);
        }
        List<Map<String, Object>> values;
        if (kind == DataSourceDraft.SourceKind.HTTP_JSON) {
            var result = publishedHttpRows.read(actor(session), publication);
            requireHttpRowsIdentity(publication, result);
            values = result.rows().stream()
                    .map(PublishedHttpDataSourceRowsReader.Row::values)
                    .toList();
        } else if (kind == DataSourceDraft.SourceKind.JDBC_TABLE) {
            var result = publishedJdbcRows.read(actor(session), publication);
            requireJdbcRowsIdentity(publication, result);
            values = result.rows().stream()
                    .map(PublishedJdbcTableDataSourceRowsReader.Row::values)
                    .toList();
        } else {
            throw new IllegalStateException("Unsupported external source kind");
        }
        var limited = values.stream().limit(size).toList();
        var rows = new java.util.ArrayList<DataSourceViews.RuntimeRow>();
        for (int index = 0; index < limited.size(); index++) {
            var row = limited.get(index);
            var projected = resolved.outputFields().stream().map(field -> {
                var value = row.get(field.fieldCode());
                return new DataSourceViews.RuntimeValue(
                        field.fieldCode(), field.fieldName(), field.type(),
                        value, display(value));
            }).toList();
            rows.add(new DataSourceViews.RuntimeRow(
                    "external:" + publication.version().id() + ":" + (index + 1),
                    "EXT-" + (index + 1), 1, "ACTIVE",
                    firstDisplay(projected, publication.version().name()),
                    projected));
        }
        return new DataSourceViews.RuntimeRows(
                rows, rows, 1, size, values.size(),
                externalQueryHash(publication), true, kind.name(), 25);
    }

    private static void requirePage(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new DataSourceException(
                    "DATA_SOURCE_PAGE_INVALID",
                    "page must be positive and size must be between 1 and 200");
        }
    }

    private Resolved resolved(RuntimeSession session, String code) {
        var actor = actor(session);
        var active = sources.active(actor, code);
        return resolved(session, new DataSourcePublication(
                active.root(), active.version()), false);
    }

    private Resolved resolved(
            RuntimeSession session,
            long dataSourceId,
            long versionId
    ) {
        return resolved(session, sources.publication(
                actor(session), dataSourceId, versionId), true);
    }

    private Resolved resolved(
            RuntimeSession session,
            DataSourcePublication source,
            boolean exactVersion
    ) {
        if (source.version().snapshot().sourceKind()
                == DataSourceDraft.SourceKind.MULTI_MODULE_JOIN) {
            var join = source.version().snapshot().multiModuleJoin();
            if (join == null || join.projections().stream()
                    .anyMatch(projection -> projection.logicalFieldId() <= 0)) {
                throw new DataSourceException(
                        "DATA_SOURCE_JOIN_UNAVAILABLE",
                        "Published multi-module join capability pins are unavailable");
            }
            var output = join.projections().stream().map(projection ->
                    new DataSourceViews.RuntimeField(
                            projection.fieldCode(), projection.fieldName(),
                            projection.type())).toList();
            var metadata = new DataSourceViews.RuntimeMetadata(
                    Long.toString(source.root().id()), source.root().code(),
                    source.version().name(), source.version().description(),
                    source.version().moduleCode(),
                    Long.toString(source.version().id()),
                    Long.toString(source.version().id()),
                    source.version().versionNumber(),
                    source.version().versionNumber(),
                    source.version().schemaVersionId(), output, output,
                    null, null);
            return new Resolved(source, null, output, metadata);
        }
        final RecordRuntimeViews.RecordSchema schema;
        if (exactVersion || source.version().snapshot().sourceKind()
                != DataSourceDraft.SourceKind.NATIVE_MODULE) {
            RecordRuntimeViews.RecordSchema exact;
            try {
                exact = records.schema(
                        session, source.version().moduleCode(),
                        source.version().schemaVersionId());
            } catch (UnsupportedOperationException historicalUnavailable) {
                // Compatibility for gateways that can only return the current
                // schema. The immutable id comparison below still fails closed
                // if current no longer equals the pinned publication schema.
                exact = records.schema(
                        session, source.version().moduleCode());
            }
            schema = exact;
        } else {
            schema = records.schema(session, source.version().moduleCode());
        }
        if (!"READY".equals(schema.runtimeState())) {
            throw new DataSourceException(
                    "DATA_SOURCE_MODULE_UNAVAILABLE",
                    schema.unavailableReason() == null
                            ? "The published module runtime is unavailable"
                            : schema.unavailableReason());
        }
        if (!source.version().schemaVersionId()
                .equals(schema.schemaVersionId())) {
            throw new DataSourceException(
                    "DATA_SOURCE_SCHEMA_STALE",
                    "The data source must be republished for the current module schema");
        }

        var capabilities = capabilities(schema);
        var output = fieldCodes(source.version().snapshot()).stream()
                .map(fieldCode -> requiredCapability(
                        capabilities, fieldCode))
                .filter(RecordRuntimeViews.FieldCapability::readable)
                .map(field -> new DataSourceViews.RuntimeField(
                        field.fieldCode(), field.fieldName(), field.type()))
                .toList();
        var sort = source.version().snapshot().defaultSort();
        var visibleSort = sort != null
                && requiredCapability(capabilities, sort.fieldCode()).readable()
                ? new DataSourceViews.DefaultSort(
                sort.fieldCode(), sort.direction().name()) : null;
        var metadata = new DataSourceViews.RuntimeMetadata(
                Long.toString(source.root().id()), source.root().code(),
                source.version().name(), source.version().description(),
                source.version().moduleCode(),
                Long.toString(source.version().id()),
                Long.toString(source.version().id()),
                source.version().versionNumber(),
                source.version().versionNumber(),
                source.version().schemaVersionId(), output, output,
                visibleSort,
                visible(output, source.version().snapshot()
                        .defaultTimeFieldCode())
                        ? source.version().snapshot()
                        .defaultTimeFieldCode() : null);
        return new Resolved(source, schema, output, metadata);
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

    private static String display(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.time.temporal.TemporalAccessor temporal) {
            return temporal.toString();
        }
        return String.valueOf(value);
    }

    private static String firstDisplay(
            List<DataSourceViews.RuntimeValue> values,
            String fallback
    ) {
        return values.stream().map(DataSourceViews.RuntimeValue::displayValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst().orElse(fallback);
    }

    private static String externalQueryHash(DataSourcePublication source) {
        return "external-v1:" + source.version().snapshot().sourceKind().name()
                + ":" + source.version().id();
    }

    private String query(
            com.unique.examine.module.datasource.domain.DataSourceVersion source,
            RecordRuntimeViews.RecordSchema schema,
            List<DataSourceViews.RuntimeField> outputFields,
            int page,
            int size
    ) {
        var root = mapper.createObjectNode();
        root.put("schemaVersionId", source.schemaVersionId());
        root.put("page", page);
        root.put("size", size);
        root.put("recordScope", "active");
        root.putNull("q");
        root.set("filter", filter(source.snapshot().fixedFilters()));
        root.set("sort", sorts(source.snapshot(), schema, outputFields));
        var columns = root.putArray("columns");
        outputFields.forEach(field -> columns.add(field.fieldCode()));
        root.putNull("viewId");
        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Cannot serialize the native data source query", exception);
        }
    }

    private JsonNode filter(List<DataSourceDraft.FixedFilter> filters) {
        if (filters.isEmpty()) {
            return mapper.nullNode();
        }
        if (filters.size() == 1) {
            return predicate(filters.getFirst());
        }
        var group = mapper.createObjectNode();
        group.put("kind", "AND");
        var children = group.putArray("children");
        filters.forEach(filter -> children.add(predicate(filter)));
        return group;
    }

    private ObjectNode predicate(DataSourceDraft.FixedFilter filter) {
        var predicate = mapper.createObjectNode();
        predicate.put("kind", "PREDICATE");
        predicate.put("fieldCode", filter.fieldCode());
        predicate.put("operator", filter.operator());
        if ("EMPTY".equals(filter.operator())
                && filter.canonicalValue() == null) {
            // The native query compiler models EMPTY as a boolean switch.
            predicate.put("value", true);
        } else if (filter.canonicalValue() != null) {
            try {
                predicate.set("value", mapper.readTree(
                        filter.canonicalValue()));
            } catch (JsonProcessingException exception) {
                throw new DataSourceException(
                        "DATA_SOURCE_FILTER_INVALID",
                        "A published fixed filter is not canonical JSON");
            }
        }
        return predicate;
    }

    private ArrayNode sorts(
            DataSourceDraft draft,
            RecordRuntimeViews.RecordSchema schema,
            List<DataSourceViews.RuntimeField> outputs
    ) {
        var result = mapper.createArrayNode();
        var sort = draft.defaultSort();
        if (sort == null || !visible(outputs, sort.fieldCode())) {
            return result;
        }
        var capability = requiredCapability(
                capabilities(schema), sort.fieldCode());
        if (!capability.sortable()) {
            throw new DataSourceException(
                    "DATA_SOURCE_SCHEMA_STALE",
                    "The published default sort is no longer available");
        }
        var item = result.addObject();
        item.put("fieldCode", sort.fieldCode());
        item.put("direction", sort.direction().name());
        item.put("nulls", "LAST");
        return result;
    }

    private static Map<String, RecordRuntimeViews.FieldCapability>
    capabilities(RecordRuntimeViews.RecordSchema schema) {
        var result = new LinkedHashMap<String,
                RecordRuntimeViews.FieldCapability>();
        schema.fields().forEach(field -> result.put(
                field.fieldCode(), field));
        return result;
    }

    private static RecordRuntimeViews.FieldCapability requiredCapability(
            Map<String, RecordRuntimeViews.FieldCapability> fields,
            String fieldCode
    ) {
        var field = fields.get(fieldCode);
        if (field == null) {
            throw new DataSourceException(
                    "DATA_SOURCE_SCHEMA_STALE",
                    "A published data source field no longer exists");
        }
        return field;
    }

    private static boolean visible(
            List<DataSourceViews.RuntimeField> fields,
            String fieldCode
    ) {
        return fieldCode != null && fields.stream()
                .anyMatch(field -> field.fieldCode().equals(fieldCode));
    }

    private static void requireHttpPublication(
            DataSourcePublication publication
    ) {
        if (publication.version().snapshot().sourceKind()
                != DataSourceDraft.SourceKind.HTTP_JSON) {
            throw new DataSourceException(
                    "DATA_SOURCE_HTTP_ROWS_SOURCE_INVALID",
                    "Published HTTP rows require an HTTP JSON data source");
        }
    }

    private static void requireHttpRowsIdentity(
            DataSourcePublication publication,
            PublishedHttpDataSourceRowsReader.Result result
    ) {
        if (result == null
                || result.dataSourceId() != publication.root().id()
                || !publication.root().code().equals(
                result.dataSourceCode())
                || result.versionId() != publication.version().id()
                || result.versionNumber()
                != publication.version().versionNumber()) {
            throw new IllegalStateException(
                    "Published HTTP rows returned inconsistent identity");
        }
    }

    private static void requireJdbcPublication(
            DataSourcePublication publication
    ) {
        if (publication.version().snapshot().sourceKind()
                != DataSourceDraft.SourceKind.JDBC_TABLE) {
            throw new DataSourceException(
                    "DATA_SOURCE_JDBC_ROWS_SOURCE_INVALID",
                    "Published JDBC rows require a JDBC table data source");
        }
    }

    private static void requireJdbcRowsIdentity(
            DataSourcePublication publication,
            PublishedJdbcTableDataSourceRowsReader.Result result
    ) {
        if (result == null
                || result.dataSourceId() != publication.root().id()
                || !publication.root().code().equals(
                result.dataSourceCode())
                || result.versionId() != publication.version().id()
                || result.versionNumber()
                != publication.version().versionNumber()) {
            throw new IllegalStateException(
                    "Published JDBC rows returned inconsistent identity");
        }
    }

    private static DataSourceActor actor(RuntimeSession session) {
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new DataSourceException(
                    "DATA_SOURCE_TENANT_REQUIRED",
                    "Select an active tenant before reading a data source");
        }
        return new DataSourceActor(
                session.systemId(), session.tenantId(), session.memberId());
    }

    private static PublishedHttpDataSourceRowsReader
    unavailableHttpRowsReader() {
        return (actor, publication) -> {
            throw new DataSourceException(
                    "DATA_SOURCE_PUBLISHED_HTTP_ROWS_UNAVAILABLE",
                    "Published HTTP data source rows are unavailable");
        };
    }

    private static PublishedJdbcTableDataSourceRowsReader
    unavailableJdbcRowsReader() {
        return (actor, publication) -> {
            throw new DataSourceException(
                    "DATA_SOURCE_PUBLISHED_JDBC_ROWS_UNAVAILABLE",
                    "Published JDBC table rows are unavailable");
        };
    }

    private record Resolved(
            DataSourcePublication source,
            RecordRuntimeViews.RecordSchema schema,
            List<DataSourceViews.RuntimeField> outputFields,
            DataSourceViews.RuntimeMetadata metadata
    ) {
    }
}
