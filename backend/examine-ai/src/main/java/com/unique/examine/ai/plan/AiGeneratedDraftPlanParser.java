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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Strict, fail-closed parser for one generated Flow/report/print draft. */
@Component
public final class AiGeneratedDraftPlanParser {
    static final int MAXIMUM_PLAN_BYTES = 32 * 1024;
    private static final Set<String> OPERATIONS = Set.of(
            "FLOW_DEFINITION_DRAFT", "CONFIG_REPORT_DRAFT",
            "CONFIG_PRINT_TEMPLATE_DRAFT");
    private static final Set<String> FLOW_FIELDS = Set.of(
            "operation", "name", "approverMemberIds", "confidence",
            "clarification");
    private static final Set<String> REPORT_FIELDS = Set.of(
            "operation", "code", "name", "description", "dataSourceId",
            "outputFieldCodes", "confidence", "clarification");
    private static final Set<String> PRINT_FIELDS = Set.of(
            "operation", "moduleCode", "code", "name", "paperSize",
            "orientation", "title", "fieldCodes", "footer", "confidence",
            "clarification");
    private static final Pattern CODE = Pattern.compile(
            "^[A-Za-z][A-Za-z0-9_]{0,63}$");
    private static final Pattern LOWER_CODE = Pattern.compile(
            "^[a-z][a-z0-9_]{1,63}$");
    private static final Pattern FORBIDDEN_TEXT = Pattern.compile(
            "(?i)(?:jdbc:|file:|https?://|<script|javascript:|data:|url\\s*\\()");

    private final ObjectMapper strict;

    public AiGeneratedDraftPlanParser() {
        strict = new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
        strict.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
    }

    public boolean isGeneratedDraft(String providerOutput) {
        if (providerOutput == null || providerOutput.isBlank()
                || providerOutput.getBytes(StandardCharsets.UTF_8).length
                > MAXIMUM_PLAN_BYTES) return false;
        try {
            var root = strict.readTree(providerOutput);
            var operation = root == null ? null : root.get("operation");
            return root != null && root.isObject() && operation != null
                    && operation.isTextual()
                    && OPERATIONS.contains(operation.textValue());
        } catch (JsonProcessingException failure) {
            return false;
        }
    }

    public Plan parse(String providerOutput, AiPolicy.Version policy) {
        Objects.requireNonNull(policy, "policy");
        var root = object(providerOutput);
        var operation = operation(root.get("operation"));
        if (!policy.allowedOperations().contains(operation.name())) {
            throw invalid("AI generated-draft operation is not authorized by policy");
        }
        exact(root, switch (operation) {
            case FLOW_DEFINITION_DRAFT -> FLOW_FIELDS;
            case CONFIG_REPORT_DRAFT -> REPORT_FIELDS;
            case CONFIG_PRINT_TEMPLATE_DRAFT -> PRINT_FIELDS;
        });
        rejectForbiddenText(root);
        var confidence = decimal(root.get("confidence"), "confidence");
        if (confidence.compareTo(BigDecimal.ZERO) < 0
                || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw invalid("AI generated-draft confidence must be within 0..1");
        }
        var clarification = nullableText(
                root.get("clarification"), "clarification", 500);
        return switch (operation) {
            case FLOW_DEFINITION_DRAFT -> flow(root, confidence.doubleValue(), clarification);
            case CONFIG_REPORT_DRAFT -> report(root, confidence.doubleValue(), clarification);
            case CONFIG_PRINT_TEMPLATE_DRAFT -> print(root, confidence.doubleValue(), clarification);
        };
    }

    private FlowPlan flow(JsonNode root, double confidence, String clarification) {
        if (clarification != null) {
            requireNull(root, "name", "approverMemberIds");
            return new FlowPlan(null, null, confidence, clarification, hash(root));
        }
        return new FlowPlan(
                text(root.get("name"), "name", 200),
                positiveIds(root.get("approverMemberIds"), "approverMemberIds", 10),
                confidence, null, hash(root));
    }

    private ReportPlan report(JsonNode root, double confidence, String clarification) {
        if (clarification != null) {
            requireNull(root, "code", "name", "description", "dataSourceId",
                    "outputFieldCodes");
            return new ReportPlan(null, null, null, null, null,
                    confidence, clarification, hash(root));
        }
        return new ReportPlan(
                code(root.get("code"), "code"),
                text(root.get("name"), "name", 200),
                nullableText(root.get("description"), "description", 2_000),
                positiveId(root.get("dataSourceId"), "dataSourceId"),
                codes(root.get("outputFieldCodes"), "outputFieldCodes", 100),
                confidence, null, hash(root));
    }

    private PrintPlan print(JsonNode root, double confidence, String clarification) {
        if (clarification != null) {
            requireNull(root, "moduleCode", "code", "name", "paperSize",
                    "orientation", "title", "fieldCodes", "footer");
            return new PrintPlan(null, null, null, null, null, null, null, null,
                    confidence, clarification, hash(root));
        }
        var paperSize = enumText(root.get("paperSize"), "paperSize", Set.of("A4", "A5"));
        var orientation = enumText(root.get("orientation"), "orientation",
                Set.of("PORTRAIT", "LANDSCAPE"));
        return new PrintPlan(
                lowerCode(root.get("moduleCode"), "moduleCode"),
                lowerCode(root.get("code"), "code"),
                text(root.get("name"), "name", 128),
                paperSize, orientation,
                text(root.get("title"), "title", 160),
                codes(root.get("fieldCodes"), "fieldCodes", 50),
                nullableText(root.get("footer"), "footer", 300),
                confidence, null, hash(root));
    }

    private JsonNode object(String providerOutput) {
        if (providerOutput == null || providerOutput.isBlank()
                || providerOutput.getBytes(StandardCharsets.UTF_8).length
                > MAXIMUM_PLAN_BYTES) {
            throw invalid("AI generated-draft plan must be a JSON object no larger than 32 KiB");
        }
        try {
            var root = strict.readTree(providerOutput);
            if (root == null || !root.isObject()) {
                throw invalid("AI generated-draft plan must be a JSON object");
            }
            return root;
        } catch (JsonProcessingException failure) {
            throw invalid("AI generated-draft plan JSON is malformed or has duplicate keys");
        }
    }

    private String hash(JsonNode root) {
        try {
            return AiSupport.sha256(strict.writeValueAsString(canonical(root)));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "Cannot canonicalize AI generated-draft plan", failure);
        }
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

    private static void exact(JsonNode node, Set<String> expected) {
        var actual = new LinkedHashSet<String>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw invalid("AI generated-draft plan contains unknown or missing fields");
        }
    }

    private static void requireNull(JsonNode root, String... names) {
        for (var name : names) {
            if (root.get(name) == null || !root.get(name).isNull()) {
                throw invalid("AI generated-draft clarification payload must be null");
            }
        }
    }

    private static void rejectForbiddenText(JsonNode node) {
        if (node.isTextual() && FORBIDDEN_TEXT.matcher(node.textValue()).find()) {
            throw invalid("AI generated-draft plan contains unsafe or external material");
        }
        node.elements().forEachRemaining(AiGeneratedDraftPlanParser::rejectForbiddenText);
    }

    private static Operation operation(JsonNode node) {
        try {
            return Operation.valueOf(text(node, "operation", 64));
        } catch (IllegalArgumentException failure) {
            throw invalid("AI generated-draft operation is unsupported");
        }
    }

    private static String positiveId(JsonNode node, String path) {
        var value = text(node, path, 20);
        try {
            var id = Long.parseLong(value);
            if (id <= 0 || !Long.toString(id).equals(value)) {
                throw new NumberFormatException();
            }
            return value;
        } catch (RuntimeException failure) {
            throw invalid("AI generated-draft " + path + " must be a positive decimal id");
        }
    }

    private static List<String> positiveIds(JsonNode node, String path, int maximum) {
        if (node == null || !node.isArray() || node.isEmpty() || node.size() > maximum) {
            throw invalid("AI generated-draft " + path + " size is invalid");
        }
        var values = new ArrayList<String>(node.size());
        var unique = new LinkedHashSet<String>();
        node.forEach(value -> {
            var parsed = positiveId(value, path + "[]");
            if (!unique.add(parsed)) {
                throw invalid("AI generated-draft " + path + " must be unique");
            }
            values.add(parsed);
        });
        return List.copyOf(values);
    }

    private static List<String> codes(JsonNode node, String path, int maximum) {
        if (node == null || !node.isArray() || node.isEmpty() || node.size() > maximum) {
            throw invalid("AI generated-draft " + path + " size is invalid");
        }
        var values = new ArrayList<String>(node.size());
        var unique = new LinkedHashSet<String>();
        node.forEach(value -> {
            var parsed = code(value, path + "[]");
            if (!unique.add(parsed)) {
                throw invalid("AI generated-draft " + path + " must be unique");
            }
            values.add(parsed);
        });
        return List.copyOf(values);
    }

    private static String code(JsonNode node, String path) {
        var value = text(node, path, 64);
        if (!CODE.matcher(value).matches()) {
            throw invalid("AI generated-draft " + path + " is invalid");
        }
        return value;
    }

    private static String lowerCode(JsonNode node, String path) {
        var value = text(node, path, 64);
        if (!LOWER_CODE.matcher(value).matches()) {
            throw invalid("AI generated-draft " + path + " is invalid");
        }
        return value;
    }

    private static String enumText(JsonNode node, String path, Set<String> values) {
        var value = text(node, path, 16).toUpperCase(java.util.Locale.ROOT);
        if (!values.contains(value)) {
            throw invalid("AI generated-draft " + path + " is unsupported");
        }
        return value;
    }

    private static String text(JsonNode node, String path, int maximum) {
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            throw invalid("AI generated-draft " + path + " must be text");
        }
        var value = node.textValue().strip();
        if (value.codePointCount(0, value.length()) > maximum) {
            throw invalid("AI generated-draft " + path + " is too long");
        }
        return value;
    }

    private static String nullableText(JsonNode node, String path, int maximum) {
        return node != null && node.isNull() ? null : text(node, path, maximum);
    }

    private static BigDecimal decimal(JsonNode node, String path) {
        if (node == null || !node.isNumber()) {
            throw invalid("AI generated-draft " + path + " must be a number");
        }
        return node.decimalValue();
    }

    private static com.unique.examine.core.error.BusinessException invalid(
            String message) {
        return AiSupport.invalid("AI_PLAN_INVALID", message);
    }

    public enum Operation {
        FLOW_DEFINITION_DRAFT,
        CONFIG_REPORT_DRAFT,
        CONFIG_PRINT_TEMPLATE_DRAFT
    }

    public sealed interface Plan permits FlowPlan, ReportPlan, PrintPlan {
        Operation operation();
        double confidence();
        String clarification();
        String planHash();

        default boolean actionable() { return clarification() == null; }
    }

    public record FlowPlan(
            String name,
            List<String> approverMemberIds,
            double confidence,
            String clarification,
            String planHash
    ) implements Plan {
        @Override
        public Operation operation() { return Operation.FLOW_DEFINITION_DRAFT; }
    }

    public record ReportPlan(
            String code,
            String name,
            String description,
            String dataSourceId,
            List<String> outputFieldCodes,
            double confidence,
            String clarification,
            String planHash
    ) implements Plan {
        @Override
        public Operation operation() { return Operation.CONFIG_REPORT_DRAFT; }
    }

    public record PrintPlan(
            String moduleCode,
            String code,
            String name,
            String paperSize,
            String orientation,
            String title,
            List<String> fieldCodes,
            String footer,
            double confidence,
            String clarification,
            String planHash
    ) implements Plan {
        @Override
        public Operation operation() {
            return Operation.CONFIG_PRINT_TEMPLATE_DRAFT;
        }
    }
}
