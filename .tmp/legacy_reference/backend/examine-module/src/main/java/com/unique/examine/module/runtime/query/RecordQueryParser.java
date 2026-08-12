package com.unique.examine.module.runtime.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.query.RecordQueryModels.FilterNode;
import com.unique.examine.module.runtime.query.RecordQueryModels.Group;
import com.unique.examine.module.runtime.query.RecordQueryModels.Predicate;
import com.unique.examine.module.runtime.query.RecordQueryModels.RecordQuery;
import com.unique.examine.module.runtime.query.RecordQueryModels.SortItem;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Component
public class RecordQueryParser {
    private static final int MAX_CANONICAL_BYTES = 32 * 1024;
    private static final Set<String> ROOT_FIELDS = Set.of(
            "schemaVersionId", "page", "size", "recordScope", "q", "filter", "sort", "columns", "viewId");
    private static final Set<String> REQUIRED_FIELDS = Set.of(
            "schemaVersionId", "page", "size", "recordScope", "q", "filter", "sort", "columns");
    private static final Set<String> PREDICATE_FIELDS = Set.of("kind", "fieldCode", "operator", "value");
    private static final Set<String> GROUP_FIELDS = Set.of("kind", "children");
    private static final Set<String> SORT_FIELDS = Set.of("fieldCode", "direction", "nulls", "currency");
    private static final Set<String> REQUIRED_SORT_FIELDS = Set.of("fieldCode", "direction", "nulls");

    private final ObjectMapper strictMapper;

    public RecordQueryParser() {
        strictMapper = new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build());
        strictMapper.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
    }

    public RecordQuery parse(String body) {
        if (body == null || body.isBlank() || body.getBytes(StandardCharsets.UTF_8).length > MAX_CANONICAL_BYTES) {
            throw invalid("Query body must be a JSON object no larger than 32 KiB");
        }
        final JsonNode root;
        try {
            root = strictMapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw invalid("Query JSON is malformed or contains duplicate keys");
        }
        if (root == null || !root.isObject()) {
            throw invalid("Query body must be an object");
        }
        requireExactFields(root, ROOT_FIELDS, REQUIRED_FIELDS, "query");
        var schemaVersionId = positiveId(root.get("schemaVersionId"), "schemaVersionId");
        var page = integer(root.get("page"), "page", 1, 1_000_000);
        var size = integer(root.get("size"), "size", 1, 200);
        var recordScope = text(root.get("recordScope"), "recordScope");
        if (!Set.of("active", "archived", "trash").contains(recordScope)) {
            throw invalid("recordScope must be active, archived or trash");
        }
        String q = null;
        if (!root.get("q").isNull()) {
            q = RecordSearchTokenizer.normalize(text(root.get("q"), "q"));
            if (q.length() < 2 || q.length() > 100) {
                throw invalid("q must contain 2..100 normalized characters");
            }
            try {
                RecordSearchTokenizer.queryTokens(q);
            } catch (IllegalArgumentException exception) {
                throw invalid(exception.getMessage());
            }
        }
        var counters = new FilterCounters();
        var filter = root.get("filter").isNull() ? null : filter(root.get("filter"), 1, counters);
        var sorts = sorts(root.get("sort"));
        var columns = columns(root.get("columns"));
        var viewId = root.has("viewId") && !root.get("viewId").isNull()
                ? positiveId(root.get("viewId"), "viewId") : null;

        var canonicalRoot = JsonNodeFactory.instance.objectNode();
        canonicalRoot.put("schemaVersionId", schemaVersionId);
        canonicalRoot.put("page", page);
        canonicalRoot.put("size", size);
        canonicalRoot.put("recordScope", recordScope);
        if (q == null) canonicalRoot.putNull("q"); else canonicalRoot.put("q", q);
        canonicalRoot.set("filter", root.get("filter").isNull() ? JsonNodeFactory.instance.nullNode()
                : canonical(root.get("filter")));
        canonicalRoot.set("sort", canonical(root.get("sort")));
        canonicalRoot.set("columns", canonical(root.get("columns")));
        if (viewId == null) canonicalRoot.putNull("viewId"); else canonicalRoot.put("viewId", viewId);
        final String canonicalJson;
        try {
            canonicalJson = strictMapper.writeValueAsString(canonical(canonicalRoot));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot canonicalize record query", exception);
        }
        if (canonicalJson.getBytes(StandardCharsets.UTF_8).length > MAX_CANONICAL_BYTES) {
            throw invalid("Canonical query exceeds 32 KiB");
        }
        return new RecordQuery(schemaVersionId, page, size, recordScope, q, filter, sorts, columns, viewId,
                canonicalJson);
    }

    /**
     * Parses the filter/sort subset used by configuration-owned shared scenarios through the
     * exact same bounded runtime query contract. The synthetic envelope is intentionally fixed
     * and is never persisted.
     */
    public RecordQuery parseScenario(JsonNode filter, JsonNode sort) {
        var root = JsonNodeFactory.instance.objectNode();
        root.put("schemaVersionId", "1");
        root.put("page", 1);
        root.put("size", 20);
        root.put("recordScope", "active");
        root.putNull("q");
        root.set("filter", filter == null ? JsonNodeFactory.instance.missingNode() : filter.deepCopy());
        root.set("sort", sort == null ? JsonNodeFactory.instance.missingNode() : sort.deepCopy());
        root.putArray("columns");
        try {
            return parse(strictMapper.writeValueAsString(root));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize shared filter scenario", exception);
        }
    }

    public String sha256(String canonicalJson) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalJson.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private FilterNode filter(JsonNode node, int depth, FilterCounters counters) {
        if (!node.isObject() || depth > 5) {
            throw invalid("filter must be an object with depth at most 5");
        }
        var kind = text(node.get("kind"), "filter.kind");
        if ("PREDICATE".equals(kind)) {
            requireExactFields(node, PREDICATE_FIELDS, Set.of("kind", "fieldCode", "operator"), "predicate");
            counters.leaves++;
            if (counters.leaves > 20) {
                throw invalid("filter may contain at most 20 predicates");
            }
            var fieldCode = code(node.get("fieldCode"), "filter.fieldCode");
            var operator = text(node.get("operator"), "filter.operator");
            if (!operator.matches("^[A-Z][A-Z0-9_]{1,31}$")) {
                throw invalid("filter.operator must be a canonical uppercase enum");
            }
            return new Predicate(fieldCode, operator,
                    node.has("value") ? canonical(node.get("value")) : JsonNodeFactory.instance.missingNode());
        }
        if (!Set.of("AND", "OR", "NOT").contains(kind)) {
            throw invalid("filter.kind is unsupported");
        }
        requireExactFields(node, GROUP_FIELDS, GROUP_FIELDS, "filter group");
        var childrenNode = node.get("children");
        if (!childrenNode.isArray()) {
            throw invalid("filter.children must be an array");
        }
        var expectedMax = "NOT".equals(kind) ? 1 : 20;
        if (childrenNode.isEmpty() || childrenNode.size() > expectedMax || "NOT".equals(kind) && childrenNode.size() != 1) {
            throw invalid("filter group has an invalid child count");
        }
        var children = new ArrayList<FilterNode>();
        childrenNode.forEach(child -> children.add(filter(child, depth + 1, counters)));
        return new Group(kind, children);
    }

    private List<SortItem> sorts(JsonNode node) {
        if (!node.isArray() || node.size() > 3) {
            throw invalid("sort must be an array with at most three items");
        }
        var result = new ArrayList<SortItem>();
        var fields = new LinkedHashSet<String>();
        for (var item : node) {
            if (!item.isObject()) {
                throw invalid("sort item must be an object");
            }
            requireExactFields(item, SORT_FIELDS, REQUIRED_SORT_FIELDS, "sort item");
            var fieldCode = code(item.get("fieldCode"), "sort.fieldCode");
            var direction = text(item.get("direction"), "sort.direction");
            var nulls = text(item.get("nulls"), "sort.nulls");
            String currency = null;
            if (item.has("currency")) {
                currency = text(item.get("currency"), "sort.currency");
                if (!currency.matches("^[A-Z]{3}$")) {
                    throw invalid("sort.currency must be an uppercase ISO-4217 code");
                }
            }
            if (!Set.of("ASC", "DESC").contains(direction) || !Set.of("FIRST", "LAST").contains(nulls)) {
                throw invalid("sort direction/nulls must use canonical uppercase enums");
            }
            if (!fields.add(fieldCode)) {
                throw invalid("sort fields must be unique");
            }
            result.add(new SortItem(fieldCode, direction, nulls, currency));
        }
        return List.copyOf(result);
    }

    private List<String> columns(JsonNode node) {
        if (!node.isArray()) {
            throw invalid("columns must be an array");
        }
        var result = new LinkedHashSet<String>();
        for (var item : node) {
            if (!result.add(code(item, "columns"))) {
                throw invalid("columns must be unique");
            }
        }
        return List.copyOf(result);
    }

    private JsonNode canonical(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return JsonNodeFactory.instance.missingNode();
        }
        if (node.isObject()) {
            var result = JsonNodeFactory.instance.objectNode();
            var names = new TreeSet<String>();
            node.fieldNames().forEachRemaining(names::add);
            names.forEach(name -> result.set(name, canonical(node.get(name))));
            return result;
        }
        if (node.isArray()) {
            var result = JsonNodeFactory.instance.arrayNode();
            node.forEach(value -> result.add(canonical(value)));
            return result;
        }
        if (node.isFloatingPointNumber()) {
            return JsonNodeFactory.instance.numberNode(node.decimalValue().stripTrailingZeros());
        }
        return node.deepCopy();
    }

    private static void requireExactFields(JsonNode node, Set<String> allowed, Set<String> required, String path) {
        var actual = new LinkedHashSet<String>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!allowed.containsAll(actual) || !actual.containsAll(required)) {
            throw invalid(path + " contains unknown fields or is missing required fields");
        }
    }

    private static int integer(JsonNode node, String path, int minimum, int maximum) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            throw invalid(path + " must be an integer");
        }
        var value = node.intValue();
        if (value < minimum || value > maximum) {
            throw invalid(path + " is outside its accepted range");
        }
        return value;
    }

    private static String positiveId(JsonNode node, String path) {
        var value = text(node, path);
        if (!value.matches("^[1-9][0-9]{0,18}$")) {
            throw invalid(path + " must be a positive decimal id string");
        }
        try {
            Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw invalid(path + " is outside the supported id range");
        }
        return value;
    }

    private static String code(JsonNode node, String path) {
        var value = text(node, path);
        if (!value.matches("^[A-Za-z][A-Za-z0-9_-]{0,63}$")) {
            throw invalid(path + " contains an invalid field code");
        }
        return value;
    }

    private static String text(JsonNode node, String path) {
        if (node == null || !node.isTextual()) {
            throw invalid(path + " must be a string");
        }
        return node.textValue();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("QUERY_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static final class FilterCounters {
        private int leaves;
    }
}
