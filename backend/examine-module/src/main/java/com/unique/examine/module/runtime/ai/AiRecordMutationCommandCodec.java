package com.unique.examine.module.runtime.ai;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.ai.AiRecordMutationFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict translation between the AI owner envelope and native runtime DTOs. */
final class AiRecordMutationCommandCodec {
    private static final Set<String> ROOT_FIELDS = Set.of(
            "schemaVersionId", "recordId", "expectedVersion", "title",
            "values", "relations", "subtables");
    private static final Set<String> RELATION_FIELDS = Set.of("fieldCode", "targets");
    private static final Set<String> TARGET_FIELDS = Set.of(
            "targetRecordId", "targetExpectedVersion", "ordinal");
    private static final Set<String> SUBTABLE_FIELDS = Set.of("fieldCode", "rows");
    private static final Set<String> ROW_FIELDS = Set.of(
            "clientRowKey", "rowId", "expectedVersion", "ordinal", "values");
    private static final Set<String> SEALED_FIELDS = Set.of(
            "canonicalCommandJson", "writableFieldCodes");

    private final ObjectMapper json = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    ParsedCommand parse(String source, AiRecordMutationFacade.Operation operation) {
        var root = object(read(source), ROOT_FIELDS, "owner command");
        requireExactly(root, ROOT_FIELDS, "owner command");
        var schemaVersionId = positiveDecimal(text(root, "schemaVersionId", false), "schemaVersionId");
        var recordId = nullableText(root, "recordId");
        var expectedVersion = nullableNonnegativeLong(root, "expectedVersion");
        var title = nullableText(root, "title");
        if (title != null && title.length() > 500) {
            throw invalid("title exceeds 500 characters");
        }
        if (operation == AiRecordMutationFacade.Operation.RECORD_CREATE) {
            if (recordId != null || expectedVersion != null) {
                throw invalid("create command cannot contain recordId or expectedVersion");
            }
        } else {
            recordId = positiveDecimal(recordId, "recordId");
            if (expectedVersion == null) {
                throw invalid("update command requires expectedVersion");
            }
        }

        var values = values(root.get("values"), "values");
        var relations = relations(root.get("relations"));
        var subtables = subtables(root.get("subtables"));
        var touched = new LinkedHashSet<String>();
        touched.addAll(values.keySet());
        relations.forEach(relation -> requireDistinct(touched, relation.fieldCode()));
        subtables.forEach(subtable -> requireDistinct(touched, subtable.fieldCode()));

        var canonical = write(canonical(root));
        return new ParsedCommand(
                canonical, schemaVersionId, recordId, expectedVersion, title,
                values, relations, subtables, Set.copyOf(touched));
    }

    String sealPayload(String canonicalCommand, Set<String> writableFieldCodes) {
        var root = JsonNodeFactory.instance.objectNode();
        root.put("canonicalCommandJson", canonicalCommand);
        var fields = root.putArray("writableFieldCodes");
        writableFieldCodes.stream().sorted().forEach(fields::add);
        return write(canonical(root));
    }

    OpenedPayload openPayload(String source) {
        var root = object(read(source), SEALED_FIELDS, "sealed owner payload");
        requireExactly(root, SEALED_FIELDS, "sealed owner payload");
        var command = text(root, "canonicalCommandJson", false);
        var fieldsNode = root.get("writableFieldCodes");
        if (fieldsNode == null || !fieldsNode.isArray()) {
            throw sealedInvalid();
        }
        var fields = new LinkedHashSet<String>();
        for (var field : fieldsNode) {
            if (!field.isTextual() || !validCode(field.textValue()) || !fields.add(field.textValue())) {
                throw sealedInvalid();
            }
        }
        return new OpenedPayload(command, Set.copyOf(fields));
    }

    private List<RecordRuntimeViews.RelationInput> relations(JsonNode node) {
        if (node == null || !node.isArray()) {
            throw invalid("relations must be an array");
        }
        var result = new ArrayList<RecordRuntimeViews.RelationInput>();
        for (var raw : node) {
            var relation = object(raw, RELATION_FIELDS, "relation");
            requireExactly(relation, RELATION_FIELDS, "relation");
            var fieldCode = code(text(relation, "fieldCode", false), "relation fieldCode");
            var targetsNode = relation.get("targets");
            if (targetsNode == null || !targetsNode.isArray()) {
                throw invalid("relation targets must be an array");
            }
            var targets = new ArrayList<RecordRuntimeViews.RelationTargetInput>();
            for (var rawTarget : targetsNode) {
                var target = object(rawTarget, TARGET_FIELDS, "relation target");
                requireExactly(target, TARGET_FIELDS, "relation target");
                targets.add(new RecordRuntimeViews.RelationTargetInput(
                        positiveDecimal(text(target, "targetRecordId", false), "targetRecordId"),
                        nonnegativeLong(target, "targetExpectedVersion"),
                        nonnegativeInt(target, "ordinal")));
            }
            result.add(new RecordRuntimeViews.RelationInput(fieldCode, targets));
        }
        return List.copyOf(result);
    }

    private List<RecordRuntimeViews.SubtableInput> subtables(JsonNode node) {
        if (node == null || !node.isArray()) {
            throw invalid("subtables must be an array");
        }
        var result = new ArrayList<RecordRuntimeViews.SubtableInput>();
        for (var raw : node) {
            var subtable = object(raw, SUBTABLE_FIELDS, "subtable");
            requireExactly(subtable, SUBTABLE_FIELDS, "subtable");
            var fieldCode = code(text(subtable, "fieldCode", false), "subtable fieldCode");
            var rowsNode = subtable.get("rows");
            if (rowsNode == null || !rowsNode.isArray()) {
                throw invalid("subtable rows must be an array");
            }
            var rows = new ArrayList<RecordRuntimeViews.SubRowInput>();
            for (var rawRow : rowsNode) {
                var row = object(rawRow, ROW_FIELDS, "subtable row");
                requireExactly(row, ROW_FIELDS, "subtable row");
                var clientRowKey = nullableText(row, "clientRowKey");
                if (clientRowKey != null && (clientRowKey.isBlank() || clientRowKey.length() > 128)) {
                    throw invalid("clientRowKey is invalid");
                }
                var rowId = nullableText(row, "rowId");
                if (rowId != null) {
                    rowId = positiveDecimal(rowId, "rowId");
                }
                rows.add(new RecordRuntimeViews.SubRowInput(
                        clientRowKey,
                        rowId,
                        nullableNonnegativeLong(row, "expectedVersion"),
                        nonnegativeInt(row, "ordinal"),
                        values(row.get("values"), "subtable row values")));
            }
            result.add(new RecordRuntimeViews.SubtableInput(fieldCode, rows));
        }
        return List.copyOf(result);
    }

    private static Map<String, JsonNode> values(JsonNode node, String label) {
        if (node == null || !node.isObject()) {
            throw invalid(label + " must be an object");
        }
        var result = new LinkedHashMap<String, JsonNode>();
        node.fields().forEachRemaining(entry -> {
            code(entry.getKey(), label + " fieldCode");
            result.put(entry.getKey(), entry.getValue().deepCopy());
        });
        return Map.copyOf(result);
    }

    private JsonNode read(String source) {
        try {
            return json.readTree(source);
        } catch (JsonProcessingException exception) {
            throw invalid("owner command JSON is malformed or contains duplicate keys");
        }
    }

    private String write(JsonNode value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize canonical AI mutation command", exception);
        }
    }

    private static JsonNode canonical(JsonNode value) {
        if (value.isObject()) {
            var result = JsonNodeFactory.instance.objectNode();
            var names = new ArrayList<String>();
            value.fieldNames().forEachRemaining(names::add);
            names.sort(Comparator.naturalOrder());
            names.forEach(name -> result.set(name, canonical(value.get(name))));
            return result;
        }
        if (value.isArray()) {
            var result = JsonNodeFactory.instance.arrayNode();
            value.forEach(item -> result.add(canonical(item)));
            return result;
        }
        return value.deepCopy();
    }

    private static ObjectNode object(JsonNode value, Set<String> allowed, String label) {
        if (value == null || !value.isObject()) {
            throw invalid(label + " must be an object");
        }
        var result = (ObjectNode) value;
        result.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) {
                throw invalid(label + " contains unknown field " + name);
            }
        });
        return result;
    }

    private static void requireExactly(ObjectNode object, Set<String> fields, String label) {
        if (object.size() != fields.size() || fields.stream().anyMatch(field -> !object.has(field))) {
            throw invalid(label + " must contain its complete canonical field set");
        }
    }

    private static String text(ObjectNode object, String field, boolean nullable) {
        var value = object.get(field);
        if (nullable && (value == null || value.isNull())) {
            return null;
        }
        if (value == null || !value.isTextual()) {
            throw invalid(field + " must be a string");
        }
        return value.textValue();
    }

    private static String nullableText(ObjectNode object, String field) {
        return text(object, field, true);
    }

    private static Long nullableNonnegativeLong(ObjectNode object, String field) {
        var value = object.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return nonnegativeLong(object, field);
    }

    private static long nonnegativeLong(ObjectNode object, String field) {
        var value = object.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() < 0) {
            throw invalid(field + " must be a nonnegative integer");
        }
        return value.longValue();
    }

    private static int nonnegativeInt(ObjectNode object, String field) {
        var value = object.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()
                || value.intValue() < 0) {
            throw invalid(field + " must be a nonnegative integer");
        }
        return value.intValue();
    }

    private static String positiveDecimal(String value, String field) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) {
                throw invalid(field + " must be a positive canonical decimal string");
            }
            return value;
        } catch (RuntimeException exception) {
            if (exception instanceof BusinessException business) {
                throw business;
            }
            throw invalid(field + " must be a positive canonical decimal string");
        }
    }

    private static String code(String value, String label) {
        if (!validCode(value)) {
            throw invalid(label + " is invalid");
        }
        return value;
    }

    private static boolean validCode(String value) {
        return value != null && value.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$");
    }

    private static void requireDistinct(Set<String> values, String value) {
        if (!values.add(value)) {
            throw invalid("field " + value + " is supplied by more than one mutation section");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(
                "AI_RECORD_MUTATION_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static BusinessException sealedInvalid() {
        return new BusinessException(
                "AI_MUTATION_COMMAND_INVALID",
                "AI mutation command envelope is invalid",
                HttpStatus.CONFLICT);
    }

    record ParsedCommand(
            String canonicalJson,
            String schemaVersionId,
            String recordId,
            Long expectedVersion,
            String title,
            Map<String, JsonNode> values,
            List<RecordRuntimeViews.RelationInput> relations,
            List<RecordRuntimeViews.SubtableInput> subtables,
            Set<String> touchedFieldCodes
    ) {
        RecordRuntimeViews.CreateRecordRequest createRequest() {
            return new RecordRuntimeViews.CreateRecordRequest(
                    schemaVersionId, title, values, relations, subtables);
        }

        RecordRuntimeViews.UpdateRecordRequest updateRequest() {
            return new RecordRuntimeViews.UpdateRecordRequest(
                    schemaVersionId, title, expectedVersion, values, relations, subtables);
        }
    }

    record OpenedPayload(String canonicalCommandJson, Set<String> writableFieldCodes) { }
}
