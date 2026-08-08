package com.unique.examine.ai.plan;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.unique.examine.ai.AiSupport;
import com.unique.examine.core.ai.AiFieldFillFacade;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public final class AiFillResultParser {
    static final int MAXIMUM_RESULT_BYTES = 16 * 1024;
    private static final Set<String> ROOT_FIELDS = Set.of(
            "value", "confidence", "clarification");
    private final ObjectMapper strict;

    public AiFillResultParser() {
        strict = new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
        strict.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
    }

    public Result parse(
            String providerOutput,
            AiFieldFillFacade.ResultSchema resultSchema,
            double minimumConfidence
    ) {
        if (providerOutput == null || providerOutput.isBlank()
                || providerOutput.getBytes(StandardCharsets.UTF_8).length
                > MAXIMUM_RESULT_BYTES) {
            throw invalid("AI fill result must be JSON no larger than 16 KiB");
        }
        final JsonNode root;
        try {
            root = strict.readTree(providerOutput);
        } catch (JsonProcessingException failure) {
            throw invalid("AI fill result JSON is malformed or has duplicate keys");
        }
        if (root == null || !root.isObject() || !fields(root).equals(ROOT_FIELDS)) {
            throw invalid("AI fill result contains unknown or missing fields");
        }
        if (!Double.isFinite(minimumConfidence)
                || minimumConfidence < 0.50d || minimumConfidence > 1d) {
            throw new IllegalArgumentException("minimumConfidence is invalid");
        }
        var clarification = clarification(root.get("clarification"));
        var confidenceNode = root.get("confidence");
        if (confidenceNode == null || !confidenceNode.isNumber()
                || !Double.isFinite(confidenceNode.doubleValue())
                || confidenceNode.doubleValue() < 0d
                || confidenceNode.doubleValue() > 1d) {
            throw invalid("AI fill confidence must be within 0..1");
        }
        var confidence = confidenceNode.doubleValue();
        var value = typed(root.get("value"), resultSchema, clarification != null);
        var owner = JsonNodeFactory.instance.objectNode();
        owner.set("value", value);
        owner.set("confidence", canonical(confidenceNode));
        try {
            var canonicalOwner = strict.writeValueAsString(owner);
            var canonicalProvider = strict.writeValueAsString(canonical(root));
            return new Result(
                    resultSchema, canonicalOwner, confidence, clarification,
                    clarification == null && confidence >= minimumConfidence,
                    AiSupport.sha256(canonicalProvider));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot canonicalize AI fill result", failure);
        }
    }

    private static JsonNode typed(
            JsonNode value,
            AiFieldFillFacade.ResultSchema schema,
            boolean clarification
    ) {
        if (value == null || value.isNull()) {
            if (clarification) return JsonNodeFactory.instance.nullNode();
            throw invalid("AI fill value is required without a clarification");
        }
        return switch (schema) {
            case STRING -> {
                if (!value.isTextual() || value.textValue().length() > 8_000) {
                    throw invalid("AI fill STRING value is invalid");
                }
                yield JsonNodeFactory.instance.textNode(value.textValue());
            }
            case DECIMAL -> {
                if (!value.isNumber()) throw invalid("AI fill DECIMAL value is invalid");
                var decimal = value.decimalValue().stripTrailingZeros();
                if (decimal.precision() > 38 || Math.abs(decimal.scale()) > 18) {
                    throw invalid("AI fill DECIMAL value exceeds the safe precision");
                }
                yield JsonNodeFactory.instance.numberNode(decimal);
            }
            case INTEGER -> {
                if (!value.isIntegralNumber() || !value.canConvertToLong()) {
                    throw invalid("AI fill INTEGER value is invalid");
                }
                yield JsonNodeFactory.instance.numberNode(value.longValue());
            }
            case BOOLEAN -> {
                if (!value.isBoolean()) throw invalid("AI fill BOOLEAN value is invalid");
                yield JsonNodeFactory.instance.booleanNode(value.booleanValue());
            }
            case DATE -> {
                if (!value.isTextual()) throw invalid("AI fill DATE value is invalid");
                try {
                    yield JsonNodeFactory.instance.textNode(
                            LocalDate.parse(value.textValue()).toString());
                } catch (DateTimeParseException failure) {
                    throw invalid("AI fill DATE value must be ISO-8601");
                }
            }
            case DATETIME -> {
                if (!value.isTextual()) throw invalid("AI fill DATETIME value is invalid");
                try {
                    var instant = OffsetDateTime.parse(value.textValue()).toInstant();
                    yield JsonNodeFactory.instance.textNode(instant.toString());
                } catch (DateTimeParseException first) {
                    try {
                        yield JsonNodeFactory.instance.textNode(
                                Instant.parse(value.textValue()).toString());
                    } catch (DateTimeParseException second) {
                        throw invalid("AI fill DATETIME value must be ISO-8601 with offset");
                    }
                }
            }
        };
    }

    private static String clarification(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (!node.isTextual() || node.textValue().isBlank()
                || node.textValue().codePointCount(0, node.textValue().length()) > 500) {
            throw invalid("AI fill clarification must be null or bounded text");
        }
        return node.textValue().strip();
    }

    private static Set<String> fields(JsonNode node) {
        var result = new LinkedHashSet<String>();
        node.fieldNames().forEachRemaining(result::add);
        return Set.copyOf(result);
    }

    private static JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            var result = JsonNodeFactory.instance.objectNode();
            var names = new java.util.TreeSet<String>();
            node.fieldNames().forEachRemaining(names::add);
            names.forEach(name -> result.set(name, canonical(node.get(name))));
            return result;
        }
        if (node.isArray()) {
            var result = JsonNodeFactory.instance.arrayNode();
            node.forEach(item -> result.add(canonical(item)));
            return result;
        }
        if (node.isFloatingPointNumber()) {
            BigDecimal value = node.decimalValue().stripTrailingZeros();
            return JsonNodeFactory.instance.numberNode(value);
        }
        return node.deepCopy();
    }

    private static com.unique.examine.core.error.BusinessException invalid(
            String message) {
        return AiSupport.invalid("AI_FILL_RESULT_INVALID", message);
    }

    public record Result(
            AiFieldFillFacade.ResultSchema resultSchema,
            String canonicalOwnerResultJson,
            double confidence,
            String clarification,
            boolean confirmable,
            String resultHash
    ) {
    }
}
