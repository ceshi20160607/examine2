package com.unique.examine.module.runtime.ai;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.unique.examine.core.ai.AiRecordQueryFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.RecordRuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Module-owned adapter for AI record queries.
 *
 * <p>The adapter only translates the bounded AI fragment into the existing
 * native query contract. SQL compilation, tenant and row scope, field
 * projection and sensitive-value handling remain owned by
 * {@link RecordRuntimeService}.</p>
 */
@Component
public class AiRecordQueryAdapter implements AiRecordQueryFacade {
    private static final long UNUSED_ACCOUNT_ID = 0L;
    private static final Set<String> FRAGMENT_FIELDS = Set.of(
            "filter", "sort", "columns", "q", "recordScope");

    private final RecordSchemaReader schemas;
    private final RecordQueryReader records;
    private final ObjectMapper strictJson;

    @Autowired
    public AiRecordQueryAdapter(RecordRuntimeService records) {
        this(records::schema, records::query);
    }

    AiRecordQueryAdapter(
            RecordSchemaReader schemas,
            RecordQueryReader records
    ) {
        this.schemas = Objects.requireNonNull(schemas, "schemas");
        this.records = Objects.requireNonNull(records, "records");
        this.strictJson = new ObjectMapper(JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public Result query(Request request) {
        Objects.requireNonNull(request, "request");
        requireView(request);
        var session = new RuntimeSession(
                UNUSED_ACCOUNT_ID,
                request.systemId(),
                request.memberId(),
                request.tenantId(),
                request.effectivePermissions());
        var fragment = fragment(request.canonicalQueryJson());
        var outputFields = outputFields(fragment, request.outboundFieldCodes());
        var schema = schemas.read(session, request.moduleCode());
        var nativeQuery = nativeQuery(
                schema.schemaVersionId(), fragment,
                outputFields, request.maxRows());
        var page = records.query(session, request.moduleCode(), nativeQuery);
        var allowed = Set.copyOf(outputFields);
        var rows = page.rows().stream()
                .limit(request.maxRows())
                .map(row -> new AiRecordQueryFacade.Record(
                        row.recordId(),
                        row.recordNo(),
                        row.version(),
                        row.status(),
                        row.title(),
                        row.values().stream()
                                .filter(value -> allowed.contains(value.fieldCode()))
                                .map(value -> new AiRecordQueryFacade.DisplayValue(
                                        value.fieldCode(), value.displayValue()))
                                .toList()))
                .toList();
        return new Result(page.total(), rows);
    }

    private static void requireView(Request request) {
        var moduleView = "module." + request.moduleCode() + ".view";
        if (!request.effectivePermissions().contains("system.runtime.access")
                || !request.effectivePermissions().contains(moduleView)) {
            throw new BusinessException(
                    "PERMISSION_DENIED",
                    "The current member cannot query this runtime module",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private JsonNode fragment(String value) {
        final JsonNode fragment;
        try {
            fragment = strictJson.readTree(value);
        } catch (JsonProcessingException exception) {
            throw invalid("AI record query fragment is malformed or contains duplicate keys");
        }
        if (fragment == null || !fragment.isObject()) {
            throw invalid("AI record query fragment must be an object");
        }
        var names = new LinkedHashSet<String>();
        fragment.fieldNames().forEachRemaining(names::add);
        if (!FRAGMENT_FIELDS.containsAll(names)) {
            throw invalid("AI record query fragment contains unknown fields");
        }
        return fragment;
    }

    private static List<String> outputFields(
            JsonNode fragment,
            List<String> outboundFieldCodes
    ) {
        var columns = fragment.get("columns");
        if (columns == null) {
            return outboundFieldCodes;
        }
        if (!columns.isArray()) {
            throw invalid("AI record query columns must be an array");
        }
        var allowed = Set.copyOf(outboundFieldCodes);
        var result = new LinkedHashSet<String>();
        for (var column : columns) {
            if (!column.isTextual()
                    || !column.textValue().matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")
                    || !allowed.contains(column.textValue())
                    || !result.add(column.textValue())) {
                throw invalid("AI record query columns exceed the outbound field allowlist");
            }
        }
        return List.copyOf(result);
    }

    private String nativeQuery(
            String schemaVersionId,
            JsonNode fragment,
            List<String> outputFields,
            int maxRows
    ) {
        var root = JsonNodeFactory.instance.objectNode();
        root.put("schemaVersionId", schemaVersionId);
        root.put("page", 1);
        root.put("size", maxRows);
        root.put("recordScope", fragment.path("recordScope").asText("active"));
        root.set("q", fragment.has("q")
                ? fragment.get("q").deepCopy()
                : JsonNodeFactory.instance.nullNode());
        root.set("filter", fragment.has("filter")
                ? fragment.get("filter").deepCopy()
                : JsonNodeFactory.instance.nullNode());
        root.set("sort", fragment.has("sort")
                ? fragment.get("sort").deepCopy()
                : JsonNodeFactory.instance.arrayNode());
        var columns = root.putArray("columns");
        outputFields.forEach(columns::add);
        root.putNull("viewId");
        try {
            return strictJson.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot build native AI record query", exception);
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(
                "AI_RECORD_QUERY_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @FunctionalInterface
    interface RecordSchemaReader {
        RecordRuntimeViews.RecordSchema read(
                RuntimeSession session,
                String moduleCode);
    }

    @FunctionalInterface
    interface RecordQueryReader {
        RecordRuntimeViews.RecordPage query(
                RuntimeSession session,
                String moduleCode,
                String canonicalQueryJson);
    }
}
