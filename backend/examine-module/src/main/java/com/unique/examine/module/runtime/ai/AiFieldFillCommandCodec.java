package com.unique.examine.module.runtime.ai;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.ai.AiFieldFillFacade;
import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;

/** Strict typed result and sealed owner command codec for AI_FILL. */
final class AiFieldFillCommandCodec {
    private static final Set<String> RESULT_FIELDS = Set.of("value", "confidence");
    private static final Set<String> COMMAND_FIELDS = Set.of(
            "proposalId", "schemaVersionId", "recordVersion", "sourceVersionHash",
            "moduleSnapshotId", "fieldSnapshotId", "logicalFieldId", "fieldCode",
            "resultSchema", "canonicalValue", "displayValue", "confidence",
            "overwriteMode", "currentMaterializationVersion", "currentValueHash",
            "providerId", "providerVersion", "model", "promptVersion", "policyVersionId");

    private final ObjectMapper json = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);

    TypedResult result(
            String source,
            AiFieldFillFacade.ResultSchema schema,
            double minimumConfidence
    ) {
        var root = object(read(source), RESULT_FIELDS, "AI_FILL result");
        exact(root, RESULT_FIELDS, "AI_FILL result");
        var confidenceNode = root.get("confidence");
        if (!confidenceNode.isNumber() || !Double.isFinite(confidenceNode.doubleValue())) {
            throw invalid("AI_FILL confidence must be numeric");
        }
        var confidence = confidenceNode.doubleValue();
        if (!Double.isFinite(confidence) || confidence < minimumConfidence || confidence > 1d) {
            throw new BusinessException(
                    "AI_FILL_CONFIDENCE_TOO_LOW",
                    "AI_FILL result does not meet the published confidence threshold",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        var value = typed(root.get("value"), schema);
        var canonical = JsonNodeFactory.instance.objectNode();
        canonical.set("confidence", JsonNodeFactory.instance.numberNode(
                new BigDecimal(confidenceNode.asText()).stripTrailingZeros()));
        canonical.set("value", value.node());
        return new TypedResult(
                write(canonical(canonical)), value.node(), value.display(), confidence,
                AiFieldFillCommandSealer.sha256(write(canonical(canonical))));
    }

    String command(FillCommand command) {
        var root = JsonNodeFactory.instance.objectNode();
        root.put("proposalId", command.proposalId());
        root.put("schemaVersionId", command.schemaVersionId());
        root.put("recordVersion", command.recordVersion());
        root.put("sourceVersionHash", command.sourceVersionHash());
        root.put("moduleSnapshotId", command.moduleSnapshotId());
        root.put("fieldSnapshotId", command.fieldSnapshotId());
        root.put("logicalFieldId", command.logicalFieldId());
        root.put("fieldCode", command.fieldCode());
        root.put("resultSchema", command.resultSchema().name());
        root.set("canonicalValue", command.canonicalValue());
        root.put("displayValue", command.displayValue());
        root.put("confidence", command.confidence());
        root.put("overwriteMode", command.overwriteMode().name());
        if (command.currentMaterializationVersion() == null) {
            root.putNull("currentMaterializationVersion");
        } else {
            root.put("currentMaterializationVersion", command.currentMaterializationVersion());
        }
        if (command.currentValueHash() == null) {
            root.putNull("currentValueHash");
        } else {
            root.put("currentValueHash", command.currentValueHash());
        }
        root.put("providerId", command.provenance().providerId());
        root.put("providerVersion", command.provenance().providerVersion());
        root.put("model", command.provenance().model());
        root.put("promptVersion", command.provenance().promptVersion());
        root.put("policyVersionId", command.provenance().policyVersionId());
        return write(canonical(root));
    }

    FillCommand open(String source) {
        var root = object(readSealed(source), COMMAND_FIELDS, "AI_FILL sealed command");
        exact(root, COMMAND_FIELDS, "AI_FILL sealed command");
        try {
            var schema = AiFieldFillFacade.ResultSchema.valueOf(text(root, "resultSchema"));
            var value = typed(root.get("canonicalValue"), schema);
            var confidence = finite(root, "confidence");
            if (confidence < 0.50d || confidence > 1d) {
                throw sealedInvalid();
            }
            var display = text(root, "displayValue");
            if (!display.equals(value.display())) {
                throw sealedInvalid();
            }
            return new FillCommand(
                    text(root, "proposalId"), positiveDecimal(root, "schemaVersionId"),
                    nonnegativeLong(root, "recordVersion"), hash(root, "sourceVersionHash"),
                    positiveLong(root, "moduleSnapshotId"), positiveLong(root, "fieldSnapshotId"),
                    positiveLong(root, "logicalFieldId"), code(root, "fieldCode"), schema,
                    value.node(), display, confidence,
                    AiFieldFillFacade.OverwriteMode.valueOf(text(root, "overwriteMode")),
                    nullableNonnegativeLong(root, "currentMaterializationVersion"),
                    nullableHash(root, "currentValueHash"),
                    new AiFieldFillFacade.Provenance(
                            positiveDecimal(root, "providerId"),
                            nonnegativeLong(root, "providerVersion"), text(root, "model"),
                            text(root, "promptVersion"), positiveDecimal(root, "policyVersionId")));
        } catch (RuntimeException failure) {
            if (failure instanceof BusinessException business
                    && "AI_FILL_COMMAND_INVALID".equals(business.code())) {
                throw business;
            }
            throw sealedInvalid();
        }
    }

    private TypedValue typed(JsonNode value, AiFieldFillFacade.ResultSchema schema) {
        if (value == null || value.isNull()) {
            throw invalid("AI_FILL value must not be null");
        }
        try {
            return switch (schema) {
                case STRING -> {
                    if (!value.isTextual() || value.textValue().isBlank()
                            || value.textValue().length() > 8000) {
                        throw invalid("AI_FILL STRING value is invalid");
                    }
                    yield new TypedValue(JsonNodeFactory.instance.textNode(value.textValue()), value.textValue());
                }
                case DECIMAL -> {
                    if (!value.isNumber()) throw invalid("AI_FILL DECIMAL value is invalid");
                    var number = value.decimalValue().stripTrailingZeros();
                    if (number.precision() > 38 || Math.max(number.scale(), 0) > 18) {
                        throw invalid("AI_FILL DECIMAL value exceeds precision");
                    }
                    yield new TypedValue(JsonNodeFactory.instance.numberNode(number), number.toPlainString());
                }
                case INTEGER -> {
                    if (!value.isIntegralNumber()) throw invalid("AI_FILL INTEGER value is invalid");
                    var number = value.bigIntegerValue();
                    if (number.toString().length() > 38) {
                        throw invalid("AI_FILL INTEGER value exceeds precision");
                    }
                    yield new TypedValue(JsonNodeFactory.instance.numberNode(number), number.toString());
                }
                case BOOLEAN -> {
                    if (!value.isBoolean()) throw invalid("AI_FILL BOOLEAN value is invalid");
                    yield new TypedValue(JsonNodeFactory.instance.booleanNode(value.booleanValue()),
                            Boolean.toString(value.booleanValue()));
                }
                case DATE -> {
                    if (!value.isTextual()) throw invalid("AI_FILL DATE value is invalid");
                    var parsed = LocalDate.parse(value.textValue());
                    yield new TypedValue(JsonNodeFactory.instance.textNode(parsed.toString()), parsed.toString());
                }
                case DATETIME -> {
                    if (!value.isTextual()) throw invalid("AI_FILL DATETIME value is invalid");
                    var parsed = LocalDateTime.parse(value.textValue()).truncatedTo(ChronoUnit.MICROS);
                    yield new TypedValue(JsonNodeFactory.instance.textNode(parsed.toString()), parsed.toString());
                }
            };
        } catch (DateTimeParseException failure) {
            throw invalid("AI_FILL temporal value is invalid");
        }
    }

    private JsonNode read(String source) {
        try {
            return json.readTree(source);
        } catch (JsonProcessingException failure) {
            throw invalid("AI_FILL result JSON is malformed or contains duplicate/trailing content");
        }
    }

    private JsonNode readSealed(String source) {
        try {
            return json.readTree(source);
        } catch (JsonProcessingException failure) {
            throw sealedInvalid();
        }
    }

    private String write(JsonNode value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot write canonical AI_FILL command", failure);
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

    private static ObjectNode object(JsonNode node, Set<String> allowed, String label) {
        if (node == null || !node.isObject()) {
            throw invalid(label + " must be an object");
        }
        var result = (ObjectNode) node;
        result.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) throw invalid(label + " contains unknown field " + name);
        });
        return result;
    }

    private static void exact(ObjectNode node, Set<String> fields, String label) {
        if (node.size() != fields.size() || fields.stream().anyMatch(field -> !node.has(field))) {
            throw invalid(label + " has missing fields");
        }
    }

    private static String text(ObjectNode root, String field) {
        var value = root.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) throw sealedInvalid();
        return value.textValue();
    }

    private static String code(ObjectNode root, String field) {
        var value = text(root, field);
        if (!value.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) throw sealedInvalid();
        return value;
    }

    private static String positiveDecimal(ObjectNode root, String field) {
        var value = text(root, field);
        try {
            if (Long.parseLong(value) <= 0 || !Long.toString(Long.parseLong(value)).equals(value)) {
                throw sealedInvalid();
            }
            return value;
        } catch (NumberFormatException failure) {
            throw sealedInvalid();
        }
    }

    private static long positiveLong(ObjectNode root, String field) {
        var value = root.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() <= 0) throw sealedInvalid();
        return value.longValue();
    }

    private static long nonnegativeLong(ObjectNode root, String field) {
        var value = root.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() < 0) throw sealedInvalid();
        return value.longValue();
    }

    private static Long nullableNonnegativeLong(ObjectNode root, String field) {
        return root.path(field).isNull() ? null : nonnegativeLong(root, field);
    }

    private static double finite(ObjectNode root, String field) {
        var value = root.get(field);
        if (value == null || !value.isNumber() || !Double.isFinite(value.doubleValue())) {
            throw sealedInvalid();
        }
        return value.doubleValue();
    }

    private static String hash(ObjectNode root, String field) {
        var value = text(root, field);
        if (!value.matches("^[0-9a-f]{64}$")) throw sealedInvalid();
        return value;
    }

    private static String nullableHash(ObjectNode root, String field) {
        return root.path(field).isNull() ? null : hash(root, field);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(
                "AI_FILL_RESULT_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static BusinessException sealedInvalid() {
        return new BusinessException(
                "AI_FILL_COMMAND_INVALID",
                "AI_FILL owner command authentication failed",
                HttpStatus.CONFLICT);
    }

    record TypedResult(
            String canonicalResultJson,
            JsonNode canonicalValue,
            String displayValue,
            double confidence,
            String resultHash
    ) { }

    record FillCommand(
            String proposalId,
            String schemaVersionId,
            long recordVersion,
            String sourceVersionHash,
            long moduleSnapshotId,
            long fieldSnapshotId,
            long logicalFieldId,
            String fieldCode,
            AiFieldFillFacade.ResultSchema resultSchema,
            JsonNode canonicalValue,
            String displayValue,
            double confidence,
            AiFieldFillFacade.OverwriteMode overwriteMode,
            Long currentMaterializationVersion,
            String currentValueHash,
            AiFieldFillFacade.Provenance provenance
    ) { }

    private record TypedValue(JsonNode node, String display) { }
}
