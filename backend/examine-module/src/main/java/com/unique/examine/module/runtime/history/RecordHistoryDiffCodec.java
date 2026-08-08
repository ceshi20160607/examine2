package com.unique.examine.module.runtime.history;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class RecordHistoryDiffCodec {
    private static final Set<String> SENSITIVE_TYPES = Set.of("IDENTITY", "SECRET");
    private static final Set<String> METADATA_FIELDS = Set.of("title", "status", "state");
    private static final JsonNode MASK = TextNode.valueOf("********");

    private final ObjectMapper objectMapper;

    public RecordHistoryDiffCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public List<RecordHistoryDiff> changed(Object before, Object after) {
        return changed(before, after, Set.of());
    }

    public List<RecordHistoryDiff> changed(
            Object before,
            Object after,
            Set<String> forceMaskedFields
    ) {
        var beforeSnapshot = project(before);
        var afterSnapshot = project(after);
        var codes = selectedCodes(beforeSnapshot, afterSnapshot);
        codes.addAll(forceMaskedFields);
        var result = new ArrayList<RecordHistoryDiff>();
        for (var code : codes) {
            var oldValue = beforeSnapshot.values().get(code);
            var newValue = afterSnapshot.values().get(code);
            var forced = forceMaskedFields.contains(code);
            var beforeValue = value(oldValue);
            var afterValue = value(newValue);
            if (!forced && beforeValue.equals(afterValue)) {
                continue;
            }
            var masked = forced
                    || oldValue != null && oldValue.masked()
                    || newValue != null && newValue.masked();
            if (masked) {
                beforeValue = maskedValue(oldValue);
                afterValue = maskedValue(newValue);
            }
            result.add(new RecordHistoryDiff(code, beforeValue, afterValue, masked));
        }
        return List.copyOf(result);
    }

    public List<RecordHistoryDiff> changedField(
            String fieldCode,
            Object before,
            Object after,
            boolean masked
    ) {
        var beforeValue = masked ? MASK : tree(before);
        var afterValue = masked ? MASK : tree(after);
        if (!masked && beforeValue.equals(afterValue)) {
            return List.of();
        }
        return List.of(new RecordHistoryDiff(
                fieldCode,
                beforeValue,
                afterValue,
                masked));
    }

    private Snapshot project(Object value) {
        if (value == null) {
            return new Snapshot(Map.of(), false, true);
        }
        var node = tree(value);
        if (!node.isObject()) {
            return new Snapshot(Map.of(), false, false);
        }
        var fullRecord = node.path("values").isArray();
        var result = new LinkedHashMap<String, ProjectedValue>();
        if (fullRecord) {
            putMetadata(result, "$title", node.get("title"));
            putMetadata(result, "$status", node.get("status"));
            for (var field : node.path("values")) {
                var fieldCode = field.path("fieldCode").asText("");
                if (fieldCode.isBlank() || fieldCode.length() > 128) {
                    continue;
                }
                var sensitive = SENSITIVE_TYPES.contains(field.path("type").asText());
                var projected = sensitive
                        ? maskedDisplay(field.path("displayValue"))
                        : copy(field.get("value"));
                result.put(fieldCode, new ProjectedValue(projected, sensitive));
            }
            return new Snapshot(
                    Collections.unmodifiableMap(new LinkedHashMap<>(result)),
                    true,
                    false);
        }
        for (var field : METADATA_FIELDS) {
            if (node.has(field)) {
                putMetadata(result, "$" + field, node.get(field));
            }
        }
        return new Snapshot(
                Collections.unmodifiableMap(new LinkedHashMap<>(result)),
                false,
                false);
    }

    private static LinkedHashSet<String> selectedCodes(Snapshot before, Snapshot after) {
        var result = new LinkedHashSet<String>();
        if (before.absent()) {
            result.addAll(after.values().keySet());
            return result;
        }
        if (after.absent()) {
            result.addAll(before.values().keySet());
            return result;
        }
        if (before.fullRecord() == after.fullRecord()) {
            result.addAll(before.values().keySet());
            result.addAll(after.values().keySet());
            return result;
        }
        before.values().keySet().stream()
                .filter(after.values()::containsKey)
                .forEach(result::add);
        return result;
    }

    private static void putMetadata(
            Map<String, ProjectedValue> target,
            String code,
            JsonNode value
    ) {
        target.put(code, new ProjectedValue(copy(value), false));
    }

    private JsonNode tree(Object value) {
        return value == null ? NullNode.getInstance() : objectMapper.valueToTree(value);
    }

    private static JsonNode value(ProjectedValue value) {
        return value == null ? NullNode.getInstance() : copy(value.value());
    }

    private static JsonNode maskedValue(ProjectedValue value) {
        return value == null || value.value().isNull() ? NullNode.getInstance() : copy(value.value());
    }

    private static JsonNode maskedDisplay(JsonNode display) {
        if (display == null || display.isNull() || display.asText().isBlank()) {
            return MASK;
        }
        return display.deepCopy();
    }

    private static JsonNode copy(JsonNode value) {
        return value == null ? NullNode.getInstance() : value.deepCopy();
    }

    private record ProjectedValue(JsonNode value, boolean masked) {
    }

    private record Snapshot(
            Map<String, ProjectedValue> values,
            boolean fullRecord,
            boolean absent
    ) {
    }
}
