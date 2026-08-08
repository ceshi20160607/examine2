package com.unique.examine.ai.plan;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.unique.examine.ai.AiSupport;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.core.ai.AiRecordMutationFacade;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Strictly turns provider JSON into the owner's deterministic command shape. */
@Component
public final class AiRecordMutationPlanParser {
    static final int MAXIMUM_PLAN_BYTES = 64 * 1024;
    private static final double MINIMUM_CONFIDENCE = 0.80d;
    private static final Set<String> ROOT_FIELDS = Set.of(
            "operation", "moduleCode", "recordId", "expectedVersion", "title",
            "values", "relations", "subtables", "confidence", "clarifications");
    private static final Set<String> RELATION_FIELDS = Set.of("fieldCode", "targets");
    private static final Set<String> TARGET_FIELDS = Set.of(
            "targetRecordId", "targetExpectedVersion", "ordinal");
    private static final Set<String> SUBTABLE_FIELDS = Set.of("fieldCode", "rows");
    private static final Set<String> ROW_FIELDS = Set.of(
            "clientRowKey", "rowId", "expectedVersion", "ordinal", "values");

    private final ObjectMapper strict;

    public AiRecordMutationPlanParser() {
        strict = new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
        strict.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
    }

    public boolean isMutation(String providerOutput) {
        if (providerOutput == null || providerOutput.isBlank()
                || providerOutput.getBytes(StandardCharsets.UTF_8).length
                > MAXIMUM_PLAN_BYTES) return false;
        try {
            var root = strict.readTree(providerOutput);
            if (root == null || !root.isObject()) return false;
            var operation = root.get("operation");
            return operation != null && operation.isTextual()
                    && Set.of("RECORD_CREATE", "RECORD_UPDATE")
                    .contains(operation.textValue());
        } catch (JsonProcessingException failure) {
            return false;
        }
    }

    public String targetModule(String providerOutput, AiPolicy.Version policy) {
        try {
            var root = strict.readTree(providerOutput);
            if (root == null || !root.isObject() || !fields(root).equals(ROOT_FIELDS)) {
                throw invalid("AI mutation plan contains unknown or missing fields");
            }
            var module = code(root.get("moduleCode"), "moduleCode");
            if (!policy.writableFields().containsKey(module)) {
                throw invalid("AI mutation module has no writable policy scope");
            }
            return module;
        } catch (JsonProcessingException failure) {
            throw invalid("AI mutation plan JSON is malformed or has duplicate keys");
        }
    }

    public Plan parse(
            String providerOutput,
            AiPolicy.Version policy,
            String schemaVersionId
    ) {
        if (providerOutput == null || providerOutput.isBlank()
                || providerOutput.getBytes(StandardCharsets.UTF_8).length
                > MAXIMUM_PLAN_BYTES) {
            throw invalid("AI mutation plan must be a JSON object no larger than 64 KiB");
        }
        final JsonNode root;
        try {
            root = strict.readTree(providerOutput);
        } catch (JsonProcessingException failure) {
            throw invalid("AI mutation plan JSON is malformed or has duplicate keys");
        }
        if (root == null || !root.isObject() || !fields(root).equals(ROOT_FIELDS)) {
            throw invalid("AI mutation plan contains unknown or missing fields");
        }
        var operationText = text(root.get("operation"), "operation", 32);
        final AiRecordMutationFacade.Operation operation;
        try {
            operation = AiRecordMutationFacade.Operation.valueOf(operationText);
        } catch (RuntimeException failure) {
            throw invalid("AI mutation plan operation is unsupported");
        }
        if (!policy.allowedOperations().contains(operation.name())) {
            throw invalid("AI mutation operation is not authorized by policy");
        }
        var moduleCode = code(root.get("moduleCode"), "moduleCode");
        var allowed = policy.writableFields().get(moduleCode);
        if (allowed == null || allowed.isEmpty()) {
            throw invalid("AI mutation module has no writable policy scope");
        }
        positiveDecimal(schemaVersionId, "schemaVersionId");
        validateIdentity(root, operation);

        var usedFields = new LinkedHashSet<String>();
        validateValues(root.get("values"), "values", allowed, usedFields, 128);
        validateRelations(root.get("relations"), allowed, usedFields);
        validateSubtables(root.get("subtables"), allowed, usedFields);
        var clarifications = clarifications(root.get("clarifications"));
        var confidences = confidence(root.get("confidence"), usedFields);
        var lowConfidence = confidences.stream()
                .anyMatch(value -> value.confidence() < MINIMUM_CONFIDENCE);

        var owner = JsonNodeFactory.instance.objectNode();
        owner.put("schemaVersionId", schemaVersionId);
        owner.set("recordId", canonical(root.get("recordId")));
        owner.set("expectedVersion", canonical(root.get("expectedVersion")));
        owner.set("title", canonical(root.get("title")));
        owner.set("values", canonical(root.get("values")));
        owner.set("relations", canonical(root.get("relations")));
        owner.set("subtables", canonical(root.get("subtables")));
        try {
            var canonical = strict.writeValueAsString(canonical(owner));
            var planHash = AiSupport.sha256(strict.writeValueAsString(canonical(root)));
            return new Plan(operation, moduleCode, nullableText(root.get("recordId")),
                    nullableLong(root.get("expectedVersion")),
                    nullableText(root.get("title")), List.copyOf(usedFields),
                    confidences, clarifications, !lowConfidence && clarifications.isEmpty(),
                    canonical, planHash);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot canonicalize AI mutation plan", failure);
        }
    }

    private static void validateIdentity(
            JsonNode root, AiRecordMutationFacade.Operation operation) {
        if (operation == AiRecordMutationFacade.Operation.RECORD_CREATE) {
            if (!root.get("recordId").isNull()
                    || !root.get("expectedVersion").isNull()) {
                throw invalid("AI create plan cannot contain a record identity");
            }
        } else {
            positiveDecimal(text(root.get("recordId"), "recordId", 20), "recordId");
            nonnegativeLong(root.get("expectedVersion"), "expectedVersion");
        }
        if (!root.get("title").isNull()) {
            text(root.get("title"), "title", 500);
        }
    }

    private static void validateValues(
            JsonNode node, String path, Set<String> allowed,
            Set<String> usedFields, int maximum) {
        if (node == null || !node.isObject() || node.size() > maximum) {
            throw invalid("AI " + path + " must be a bounded object");
        }
        node.fields().forEachRemaining(entry -> {
            requireCode(entry.getKey(), path + " field");
            if (!allowed.contains(entry.getKey()) || !usedFields.add(entry.getKey())) {
                throw invalid("AI " + path + " field is unauthorized or duplicated");
            }
            if (!entry.getValue().isValueNode()) {
                throw invalid("AI " + path + " values must be scalar JSON values");
            }
            if (entry.getValue().isTextual()
                    && entry.getValue().textValue().length() > 8_000) {
                throw invalid("AI " + path + " text value is too long");
            }
        });
    }

    private static void validateRelations(
            JsonNode node, Set<String> allowed, Set<String> usedFields) {
        if (node == null || !node.isArray() || node.size() > 32) {
            throw invalid("AI relations must be a bounded array");
        }
        node.forEach(relation -> {
            exactObject(relation, RELATION_FIELDS, "relation");
            var field = code(relation.get("fieldCode"), "relations.fieldCode");
            if (!allowed.contains(field) || !usedFields.add(field)) {
                throw invalid("AI relation field is unauthorized or duplicated");
            }
            var targets = relation.get("targets");
            if (!targets.isArray() || targets.size() > 100) {
                throw invalid("AI relation targets are invalid");
            }
            var ordinals = new LinkedHashSet<Integer>();
            targets.forEach(target -> {
                exactObject(target, TARGET_FIELDS, "relation target");
                positiveDecimal(text(target.get("targetRecordId"),
                        "targetRecordId", 20), "targetRecordId");
                nonnegativeLong(target.get("targetExpectedVersion"),
                        "targetExpectedVersion");
                var ordinal = nonnegativeInt(target.get("ordinal"), "ordinal");
                if (!ordinals.add(ordinal)) {
                    throw invalid("AI relation target ordinals must be unique");
                }
            });
        });
    }

    private static void validateSubtables(
            JsonNode node, Set<String> allowed, Set<String> usedFields) {
        if (node == null || !node.isArray() || node.size() > 16) {
            throw invalid("AI subtables must be a bounded array");
        }
        node.forEach(subtable -> {
            exactObject(subtable, SUBTABLE_FIELDS, "subtable");
            var field = code(subtable.get("fieldCode"), "subtables.fieldCode");
            if (!allowed.contains(field) || !usedFields.add(field)) {
                throw invalid("AI subtable field is unauthorized or duplicated");
            }
            var rows = subtable.get("rows");
            if (!rows.isArray() || rows.size() > 100) {
                throw invalid("AI subtable rows are invalid");
            }
            var keys = new LinkedHashSet<String>();
            var ordinals = new LinkedHashSet<Integer>();
            rows.forEach(row -> {
                exactObject(row, ROW_FIELDS, "subtable row");
                var clientKey = nullableBoundedText(
                        row.get("clientRowKey"), "clientRowKey", 128);
                var rowId = nullablePositiveDecimal(row.get("rowId"), "rowId");
                var expected = nullableNonnegativeLong(
                        row.get("expectedVersion"), "expectedVersion");
                if (rowId == null != (expected == null)) {
                    throw invalid("AI subtable row identity and version must be paired");
                }
                if (clientKey != null && !keys.add(clientKey)) {
                    throw invalid("AI subtable client row keys must be unique");
                }
                var ordinal = nonnegativeInt(row.get("ordinal"), "ordinal");
                if (!ordinals.add(ordinal)) {
                    throw invalid("AI subtable ordinals must be unique");
                }
                validateNestedValues(row.get("values"));
            });
        });
    }

    private static void validateNestedValues(JsonNode node) {
        if (node == null || !node.isObject() || node.size() > 128) {
            throw invalid("AI subtable row values must be a bounded object");
        }
        node.fields().forEachRemaining(entry -> {
            requireCode(entry.getKey(), "subtable value field");
            if (!entry.getValue().isValueNode()) {
                throw invalid("AI subtable values must be scalar JSON values");
            }
        });
    }

    private static List<FieldConfidence> confidence(
            JsonNode node, Set<String> usedFields) {
        if (node == null || !node.isObject()
                || !fields(node).equals(Set.copyOf(usedFields))) {
            throw invalid("AI confidence must cover exactly all proposed fields");
        }
        var result = new ArrayList<FieldConfidence>();
        new TreeSet<>(usedFields).forEach(field -> {
            var value = node.get(field);
            if (value == null || !value.isNumber()
                    || !Double.isFinite(value.doubleValue())
                    || value.doubleValue() < 0d || value.doubleValue() > 1d) {
                throw invalid("AI field confidence must be within 0..1");
            }
            result.add(new FieldConfidence(field, value.doubleValue()));
        });
        return List.copyOf(result);
    }

    private static List<String> clarifications(JsonNode node) {
        if (node == null || !node.isArray() || node.size() > 20) {
            throw invalid("AI clarifications must be a bounded array");
        }
        var result = new LinkedHashSet<String>();
        node.forEach(value -> {
            var text = text(value, "clarification", 500).strip();
            if (!result.add(text)) {
                throw invalid("AI clarifications must be unique");
            }
        });
        return List.copyOf(result);
    }

    private JsonNode canonical(JsonNode node) {
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
            return JsonNodeFactory.instance.numberNode(
                    node.decimalValue().stripTrailingZeros());
        }
        return node.deepCopy();
    }

    private static void exactObject(JsonNode node, Set<String> fields, String path) {
        if (node == null || !node.isObject() || !fields(node).equals(fields)) {
            throw invalid("AI " + path + " contains unknown or missing fields");
        }
    }

    private static Set<String> fields(JsonNode node) {
        var result = new LinkedHashSet<String>();
        node.fieldNames().forEachRemaining(result::add);
        return Set.copyOf(result);
    }

    private static String code(JsonNode node, String path) {
        var value = text(node, path, 64);
        requireCode(value, path);
        return value;
    }

    private static void requireCode(String value, String path) {
        if (value == null || !value.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw invalid("AI " + path + " is not a stable code");
        }
    }

    private static String text(JsonNode node, String path, int maximum) {
        if (node == null || !node.isTextual() || node.textValue().isBlank()
                || node.textValue().codePointCount(0, node.textValue().length()) > maximum) {
            throw invalid("AI " + path + " must be bounded text");
        }
        return node.textValue();
    }

    private static String nullableText(JsonNode node) {
        return node == null || node.isNull() ? null : node.textValue();
    }

    private static String nullableBoundedText(
            JsonNode node, String path, int maximum) {
        return node == null || node.isNull() ? null : text(node, path, maximum);
    }

    private static String nullablePositiveDecimal(JsonNode node, String path) {
        if (node == null || node.isNull()) return null;
        var value = text(node, path, 20);
        positiveDecimal(value, path);
        return value;
    }

    private static void positiveDecimal(String value, String path) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) {
                throw new NumberFormatException();
            }
        } catch (RuntimeException failure) {
            throw invalid("AI " + path + " must be a positive decimal id");
        }
    }

    private static long nonnegativeLong(JsonNode node, String path) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()
                || node.longValue() < 0) {
            throw invalid("AI " + path + " must be a nonnegative integer");
        }
        return node.longValue();
    }

    private static Long nullableNonnegativeLong(JsonNode node, String path) {
        return node == null || node.isNull() ? null : nonnegativeLong(node, path);
    }

    private static Long nullableLong(JsonNode node) {
        return node == null || node.isNull() ? null : node.longValue();
    }

    private static int nonnegativeInt(JsonNode node, String path) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()
                || node.intValue() < 0) {
            throw invalid("AI " + path + " must be a nonnegative integer");
        }
        return node.intValue();
    }

    private static com.unique.examine.core.error.BusinessException invalid(String message) {
        return AiSupport.invalid("AI_MUTATION_PLAN_INVALID", message);
    }

    public record FieldConfidence(String fieldCode, double confidence) { }

    public record Plan(
            AiRecordMutationFacade.Operation operation,
            String moduleCode,
            String recordId,
            Long expectedVersion,
            String title,
            List<String> fieldCodes,
            List<FieldConfidence> confidence,
            List<String> clarifications,
            boolean writable,
            String canonicalOwnerCommandJson,
            String planHash
    ) {
        public Plan {
            fieldCodes = List.copyOf(fieldCodes);
            confidence = List.copyOf(confidence);
            clarifications = List.copyOf(clarifications);
        }
    }
}
