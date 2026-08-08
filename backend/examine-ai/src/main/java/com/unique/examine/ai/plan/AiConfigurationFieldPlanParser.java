package com.unique.examine.ai.plan;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.unique.examine.ai.AiSupport;
import com.unique.examine.ai.domain.AiPolicy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Strict, fail-closed parser for one scalar configuration-draft field. */
@Component
public final class AiConfigurationFieldPlanParser {
    static final int MAXIMUM_PLAN_BYTES = 8 * 1024;
    private static final Set<String> ROOT_FIELDS = Set.of(
            "operation", "moduleCode", "fieldCode", "fieldName", "fieldType",
            "required", "settings", "confidence", "clarification");
    private static final Set<String> TEXT_SETTINGS = Set.of("maxLength");
    private static final Set<String> INTEGER_SETTINGS = Set.of("minimum", "maximum");
    private static final Set<String> DECIMAL_SETTINGS = Set.of(
            "precision", "scale", "minimum", "maximum");
    private static final Set<String> EMPTY_SETTINGS = Set.of();

    private final ObjectMapper strict;

    public AiConfigurationFieldPlanParser() {
        strict = new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
        strict.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
    }

    public boolean isConfigurationDraft(String providerOutput) {
        if (providerOutput == null || providerOutput.isBlank()
                || providerOutput.getBytes(StandardCharsets.UTF_8).length
                > MAXIMUM_PLAN_BYTES) return false;
        try {
            var root = strict.readTree(providerOutput);
            var operation = root == null ? null : root.get("operation");
            return root != null && root.isObject() && operation != null
                    && operation.isTextual()
                    && "CONFIG_FIELD_DRAFT".equals(operation.textValue());
        } catch (JsonProcessingException failure) {
            return false;
        }
    }

    public Plan parse(String providerOutput, AiPolicy.Version policy) {
        Objects.requireNonNull(policy, "policy");
        var root = object(providerOutput);
        exact(root, ROOT_FIELDS, "plan");
        if (!"CONFIG_FIELD_DRAFT".equals(text(
                root.get("operation"), "operation", 32))) {
            throw invalid("AI configuration operation is unsupported");
        }
        if (!policy.allowedOperations().contains("CONFIG_FIELD_DRAFT")) {
            throw invalid("AI configuration operation is not authorized by policy");
        }
        var confidence = decimal(root.get("confidence"), "confidence");
        if (confidence.compareTo(BigDecimal.ZERO) < 0
                || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw invalid("AI configuration confidence must be within 0..1");
        }

        var clarification = nullableText(
                root.get("clarification"), "clarification", 500);
        final String moduleCode;
        final String fieldCode;
        final String fieldName;
        final FieldType fieldType;
        final Boolean required;
        final Settings settings;
        if (clarification != null) {
            requireNull(root, "moduleCode", "fieldCode", "fieldName", "fieldType",
                    "required", "settings");
            moduleCode = null;
            fieldCode = null;
            fieldName = null;
            fieldType = null;
            required = null;
            settings = null;
        } else {
            moduleCode = code(root.get("moduleCode"), "moduleCode");
            if (!policy.allowedModuleCodes().contains(moduleCode)) {
                throw invalid("AI configuration module is not authorized by policy");
            }
            fieldCode = code(root.get("fieldCode"), "fieldCode");
            fieldName = text(root.get("fieldName"), "fieldName", 128).strip();
            fieldType = fieldType(root.get("fieldType"));
            required = bool(root.get("required"), "required");
            settings = settings(root.get("settings"), fieldType);
        }

        try {
            var canonicalPlan = strict.writeValueAsString(canonical(root));
            String ownerJson = null;
            if (clarification == null) {
                var owner = JsonNodeFactory.instance.objectNode();
                owner.put("moduleCode", moduleCode);
                owner.put("fieldCode", fieldCode);
                owner.put("fieldName", fieldName);
                owner.put("fieldType", fieldType.name());
                owner.put("required", required);
                owner.set("settings", canonical(root.get("settings")));
                ownerJson = strict.writeValueAsString(canonical(owner));
            }
            return new Plan(moduleCode, fieldCode, fieldName, fieldType, required,
                    settings, confidence.doubleValue(), clarification,
                    ownerJson, AiSupport.sha256(canonicalPlan));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "Cannot canonicalize AI configuration field plan", failure);
        }
    }

    private JsonNode object(String providerOutput) {
        if (providerOutput == null || providerOutput.isBlank()
                || providerOutput.getBytes(StandardCharsets.UTF_8).length
                > MAXIMUM_PLAN_BYTES) {
            throw invalid("AI configuration plan must be a JSON object no larger than 8 KiB");
        }
        try {
            var root = strict.readTree(providerOutput);
            if (root == null || !root.isObject()) {
                throw invalid("AI configuration plan must be a JSON object");
            }
            return root;
        } catch (JsonProcessingException failure) {
            throw invalid("AI configuration plan JSON is malformed or has duplicate keys");
        }
    }

    private static Settings settings(JsonNode value, FieldType type) {
        return switch (type) {
            case TEXT -> {
                exact(value, TEXT_SETTINGS, "TEXT settings");
                yield new Settings(integer(value.get("maxLength"),
                        "settings.maxLength", 1, 4_000), null, null, null, null);
            }
            case LONG_TEXT -> {
                exact(value, TEXT_SETTINGS, "LONG_TEXT settings");
                yield new Settings(integer(value.get("maxLength"),
                        "settings.maxLength", 1, 65_535), null, null, null, null);
            }
            case INTEGER -> {
                exact(value, INTEGER_SETTINGS, "INTEGER settings");
                var minimum = nullableLong(value.get("minimum"), "settings.minimum");
                var maximum = nullableLong(value.get("maximum"), "settings.maximum");
                if (minimum != null && maximum != null
                        && Long.parseLong(minimum) > Long.parseLong(maximum)) {
                    throw invalid("AI configuration integer minimum exceeds maximum");
                }
                yield new Settings(null, null, null, minimum, maximum);
            }
            case DECIMAL -> {
                exact(value, DECIMAL_SETTINGS, "DECIMAL settings");
                var precision = integer(value.get("precision"),
                        "settings.precision", 1, 38);
                var scale = integer(value.get("scale"),
                        "settings.scale", 0, 18);
                if (scale > precision) {
                    throw invalid("AI configuration decimal scale exceeds precision");
                }
                var minimum = nullableBoundedDecimal(
                        value.get("minimum"), "settings.minimum", precision, scale);
                var maximum = nullableBoundedDecimal(
                        value.get("maximum"), "settings.maximum", precision, scale);
                if (minimum != null && maximum != null
                        && new BigDecimal(minimum).compareTo(new BigDecimal(maximum)) > 0) {
                    throw invalid("AI configuration decimal minimum exceeds maximum");
                }
                yield new Settings(null, precision, scale, minimum, maximum);
            }
            case BOOLEAN, DATE, DATETIME -> {
                exact(value, EMPTY_SETTINGS, type.name() + " settings");
                yield new Settings(null, null, null, null, null);
            }
        };
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

    private static void exact(JsonNode node, Set<String> expected, String path) {
        if (node == null || !node.isObject() || !fields(node).equals(expected)) {
            throw invalid("AI configuration " + path
                    + " contains unknown or missing fields");
        }
    }

    private static Set<String> fields(JsonNode node) {
        var values = new LinkedHashSet<String>();
        node.fieldNames().forEachRemaining(values::add);
        return Set.copyOf(values);
    }

    private static FieldType fieldType(JsonNode node) {
        try {
            return FieldType.valueOf(text(node, "fieldType", 32));
        } catch (RuntimeException failure) {
            throw invalid("AI configuration field type is unsupported");
        }
    }

    private static String code(JsonNode node, String path) {
        var value = text(node, path, 64);
        if (!value.matches("^[a-z][a-z0-9_]{1,63}$")) {
            throw invalid("AI configuration " + path + " is not a stable code");
        }
        return value;
    }

    private static String text(JsonNode node, String path, int maximum) {
        if (node == null || !node.isTextual() || node.textValue().isBlank()
                || node.textValue().codePointCount(
                0, node.textValue().length()) > maximum) {
            throw invalid("AI configuration " + path + " must be bounded text");
        }
        return node.textValue();
    }

    private static String nullableText(JsonNode node, String path, int maximum) {
        return node == null || node.isNull() ? null : text(node, path, maximum).strip();
    }

    private static boolean bool(JsonNode node, String path) {
        if (node == null || !node.isBoolean()) {
            throw invalid("AI configuration " + path + " must be boolean");
        }
        return node.booleanValue();
    }

    private static int integer(
            JsonNode node, String path, int minimum, int maximum) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()
                || node.intValue() < minimum || node.intValue() > maximum) {
            throw invalid("AI configuration " + path + " is outside its bound");
        }
        return node.intValue();
    }

    private static BigDecimal decimal(JsonNode node, String path) {
        if (node == null || !node.isNumber()) {
            throw invalid("AI configuration " + path + " must be a number");
        }
        return node.decimalValue();
    }

    private static String nullableLong(JsonNode node, String path) {
        if (node == null || node.isNull()) return null;
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            throw invalid("AI configuration " + path + " must be a 64-bit integer");
        }
        return Long.toString(node.longValue());
    }

    private static String nullableBoundedDecimal(
            JsonNode node, String path, int precision, int scale) {
        if (node == null || node.isNull()) return null;
        if (!node.isNumber()) {
            throw invalid("AI configuration " + path + " must be a decimal number");
        }
        var value = node.decimalValue().stripTrailingZeros();
        var effectiveScale = Math.max(value.scale(), 0);
        var integerDigits = Math.max(value.precision() - value.scale(), 0);
        if (effectiveScale > scale || integerDigits + scale > precision) {
            throw invalid("AI configuration " + path + " exceeds precision or scale");
        }
        return value.toPlainString();
    }

    private static void requireNull(JsonNode root, String... fields) {
        for (var field : fields) {
            if (!root.get(field).isNull()) {
                throw invalid("AI clarification cannot contain executable field data");
            }
        }
    }

    private static com.unique.examine.core.error.BusinessException invalid(
            String message) {
        return AiSupport.invalid("AI_CONFIG_FIELD_PLAN_INVALID", message);
    }

    public enum FieldType {
        TEXT, LONG_TEXT, INTEGER, DECIMAL, BOOLEAN, DATE, DATETIME
    }

    public record Settings(
            Integer maxLength,
            Integer precision,
            Integer scale,
            String minimum,
            String maximum
    ) { }

    public record Plan(
            String moduleCode,
            String fieldCode,
            String fieldName,
            FieldType fieldType,
            Boolean required,
            Settings settings,
            double confidence,
            String clarification,
            String canonicalOwnerCommandJson,
            String planHash
    ) {
        public Plan {
            if (!Double.isFinite(confidence) || confidence < 0d || confidence > 1d) {
                throw new IllegalArgumentException("AI configuration confidence is invalid");
            }
            if ((clarification == null) == (canonicalOwnerCommandJson == null)) {
                throw new IllegalArgumentException(
                        "AI configuration plan must be actionable or clarification-only");
            }
        }

        public boolean actionable() {
            return clarification == null;
        }
    }
}
