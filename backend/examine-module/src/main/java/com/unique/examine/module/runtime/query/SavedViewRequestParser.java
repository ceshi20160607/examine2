package com.unique.examine.module.runtime.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class SavedViewRequestParser {
    private static final Set<String> CREATE_FIELDS = Set.of("moduleCode", "name", "query", "columns");
    private static final Set<String> UPDATE_FIELDS = Set.of("expectedVersion", "name", "query", "columns");
    private static final Set<String> DELETE_FIELDS = Set.of("expectedVersion");

    private final ObjectMapper mapper;
    private final RecordQueryParser queryParser;

    public SavedViewRequestParser(RecordQueryParser queryParser) {
        this.queryParser = queryParser;
        mapper = new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build());
    }

    public MutationRequest create(String body) {
        var root = root(body, CREATE_FIELDS);
        var moduleCode = code(root.get("moduleCode"));
        return mutation(moduleCode, root, null);
    }

    public MutationRequest update(String body) {
        var root = root(body, UPDATE_FIELDS);
        return mutation(null, root, version(root.get("expectedVersion")));
    }

    public long delete(String body) {
        return version(root(body, DELETE_FIELDS).get("expectedVersion"));
    }

    private MutationRequest mutation(String moduleCode, JsonNode root, Long expectedVersion) {
        var name = text(root.get("name"), "name").trim();
        if (name.isEmpty() || name.length() > 100) {
            throw invalid("name must contain 1..100 characters after trim");
        }
        if (!root.get("query").isObject()) {
            throw invalid("query must be an object");
        }
        final String queryJson;
        try {
            queryJson = mapper.writeValueAsString(root.get("query"));
        } catch (JsonProcessingException exception) {
            throw invalid("query cannot be serialized");
        }
        var query = queryParser.parse(queryJson);
        if (query.viewId() != null) {
            throw invalid("a saved view cannot reference another saved view");
        }
        var columns = columns(root.get("columns"));
        if (!columns.equals(query.columns())) {
            throw invalid("top-level columns must equal query.columns");
        }
        return new MutationRequest(moduleCode, name, query, columns, expectedVersion);
    }

    private JsonNode root(String body, Set<String> expected) {
        if (body == null || body.isBlank()) {
            throw invalid("request body is required");
        }
        final JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw invalid("request JSON is malformed or contains duplicate keys");
        }
        if (root == null || !root.isObject()) {
            throw invalid("request body must be an object");
        }
        var actual = new LinkedHashSet<String>();
        root.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw invalid("request contains unknown fields or is missing required fields");
        }
        return root;
    }

    private static List<String> columns(JsonNode node) {
        if (!node.isArray()) {
            throw invalid("columns must be an array");
        }
        var result = new LinkedHashSet<String>();
        for (var item : node) {
            if (!item.isTextual() || !item.textValue().matches("^[A-Za-z][A-Za-z0-9_-]{0,63}$")
                    || !result.add(item.textValue())) {
                throw invalid("columns must contain unique field codes");
            }
        }
        return List.copyOf(result);
    }

    private static String code(JsonNode node) {
        var value = text(node, "moduleCode");
        if (!value.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw invalid("moduleCode is invalid");
        }
        return value;
    }

    private static long version(JsonNode node) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() < 0) {
            throw invalid("expectedVersion must be a non-negative integer");
        }
        return node.longValue();
    }

    private static String text(JsonNode node, String path) {
        if (node == null || !node.isTextual()) {
            throw invalid(path + " must be a string");
        }
        return node.textValue();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("SAVED_VIEW_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public record MutationRequest(
            String moduleCode,
            String name,
            RecordQueryModels.RecordQuery query,
            List<String> columns,
            Long expectedVersion
    ) {
        public MutationRequest {
            columns = List.copyOf(columns);
        }
    }
}
