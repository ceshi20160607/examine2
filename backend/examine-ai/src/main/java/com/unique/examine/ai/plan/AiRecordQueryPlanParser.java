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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

@Component
public class AiRecordQueryPlanParser {
    static final int MAXIMUM_PLAN_BYTES = 32 * 1024;
    private static final int MAXIMUM_DEPTH = 5;
    private static final int MAXIMUM_PREDICATES = 20;
    private static final Set<String> ROOT_FIELDS = Set.of(
            "operation", "moduleCode", "filter", "sort",
            "outputFields", "limit");
    private static final Set<String> PREDICATE_FIELDS = Set.of(
            "kind", "fieldCode", "operator", "value");
    private static final Set<String> GROUP_FIELDS = Set.of("kind", "children");
    private static final Set<String> SORT_FIELDS = Set.of(
            "fieldCode", "direction", "nulls");
    private static final Set<String> OPERATORS = Set.of(
            "EQ", "NE", "GT", "GTE", "LT", "LTE", "IN", "NOT_IN",
            "CONTAINS", "STARTS_WITH", "ENDS_WITH", "EMPTY", "NOT_EMPTY",
            "BETWEEN");
    private static final Pattern FORBIDDEN_TEXT = Pattern.compile(
            "(?i)(?:https?://|jdbc:|file:|<script|javascript:|"
                    + "(?:^|[^A-Za-z])(select|insert|update|delete|drop|alter|"
                    + "create|merge|grant|revoke)(?:[^A-Za-z]|$))");

    private final ObjectMapper strict;

    public AiRecordQueryPlanParser() {
        strict = new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build());
        strict.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
    }

    public Plan parse(String providerOutput, AiPolicy.Version policy) {
        if (providerOutput == null || providerOutput.isBlank()
                || providerOutput.getBytes(StandardCharsets.UTF_8).length
                > MAXIMUM_PLAN_BYTES) {
            throw invalid("AI plan must be a JSON object no larger than 32 KiB");
        }
        final JsonNode root;
        try {
            root = strict.readTree(providerOutput);
        } catch (JsonProcessingException failure) {
            throw invalid("AI plan JSON is malformed or has duplicate keys");
        }
        if (root == null || !root.isObject()
                || !fields(root).equals(ROOT_FIELDS)) {
            throw invalid("AI plan contains unknown or missing fields");
        }
        if (!"RECORD_QUERY".equals(text(root.get("operation"), "operation"))) {
            throw invalid("AI plan operation must be RECORD_QUERY");
        }
        var moduleCode = code(root.get("moduleCode"), "moduleCode");
        if (!policy.allowedModuleCodes().contains(moduleCode)) {
            throw invalid("AI plan module is not authorized by policy");
        }
        var outbound = policy.outboundFields().get(moduleCode);
        if (outbound == null || outbound.isEmpty()) {
            throw invalid("AI plan module has no outbound field authorization");
        }
        var outputFields = codes(root.get("outputFields"), "outputFields", 128);
        if (outputFields.isEmpty() || !outbound.containsAll(outputFields)) {
            throw invalid("AI plan output fields exceed policy authorization");
        }
        var limit = integer(root.get("limit"), "limit", 1, policy.maxRows());
        var counters = new Counters();
        var filter = root.get("filter").isNull()
                ? JsonNodeFactory.instance.nullNode()
                : filter(root.get("filter"), 1, counters, outbound);
        var sort = sorts(root.get("sort"), outbound);
        rejectForbiddenText(root);

        var fragment = JsonNodeFactory.instance.objectNode();
        fragment.set("columns", canonical(root.get("outputFields")));
        fragment.set("filter", canonical(filter));
        fragment.putNull("q");
        fragment.put("recordScope", "active");
        fragment.set("sort", canonical(sort));
        try {
            return new Plan(
                    moduleCode,
                    strict.writeValueAsString(canonical(fragment)),
                    outputFields,
                    limit);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot canonicalize AI record query", failure);
        }
    }

    private JsonNode filter(
            JsonNode node,
            int depth,
            Counters counters,
            Set<String> allowedFields
    ) {
        if (!node.isObject() || depth > MAXIMUM_DEPTH) {
            throw invalid("AI filter depth exceeds the safe limit");
        }
        var kind = text(node.get("kind"), "filter.kind");
        if ("PREDICATE".equals(kind)) {
            if (!PREDICATE_FIELDS.containsAll(fields(node))
                    || !fields(node).containsAll(Set.of(
                    "kind", "fieldCode", "operator"))) {
                throw invalid("AI filter predicate contains unknown fields");
            }
            counters.predicates++;
            if (counters.predicates > MAXIMUM_PREDICATES) {
                throw invalid("AI filter has too many predicates");
            }
            var fieldCode = code(node.get("fieldCode"), "filter.fieldCode");
            if (!allowedFields.contains(fieldCode)) {
                throw invalid("AI filter field exceeds policy authorization");
            }
            var operator = text(node.get("operator"), "filter.operator");
            if (!OPERATORS.contains(operator)) {
                throw invalid("AI filter operator is unsupported");
            }
            return canonical(node);
        }
        if (!Set.of("AND", "OR", "NOT").contains(kind)
                || !fields(node).equals(GROUP_FIELDS)
                || !node.get("children").isArray()) {
            throw invalid("AI filter group is invalid");
        }
        var children = node.get("children");
        if (children.isEmpty() || children.size() > 20
                || "NOT".equals(kind) && children.size() != 1) {
            throw invalid("AI filter group child count is invalid");
        }
        children.forEach(child -> filter(
                child, depth + 1, counters, allowedFields));
        return canonical(node);
    }

    private JsonNode sorts(JsonNode node, Set<String> allowedFields) {
        if (!node.isArray() || node.size() > 3) {
            throw invalid("AI sort must contain at most three items");
        }
        var seen = new LinkedHashSet<String>();
        node.forEach(item -> {
            if (!item.isObject() || !fields(item).equals(SORT_FIELDS)) {
                throw invalid("AI sort item contains unknown or missing fields");
            }
            var code = code(item.get("fieldCode"), "sort.fieldCode");
            if (!allowedFields.contains(code) || !seen.add(code)) {
                throw invalid("AI sort fields are unauthorized or duplicated");
            }
            if (!Set.of("ASC", "DESC").contains(
                    text(item.get("direction"), "sort.direction"))
                    || !Set.of("FIRST", "LAST").contains(
                    text(item.get("nulls"), "sort.nulls"))) {
                throw invalid("AI sort direction or null placement is invalid");
            }
        });
        return canonical(node);
    }

    private static void rejectForbiddenText(JsonNode node) {
        if (node.isTextual() && FORBIDDEN_TEXT.matcher(node.textValue()).find()) {
            throw invalid("AI plan contains SQL, URL, script or write material");
        }
        node.elements().forEachRemaining(AiRecordQueryPlanParser::rejectForbiddenText);
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

    private static List<String> codes(JsonNode node, String path, int maximum) {
        if (node == null || !node.isArray() || node.size() > maximum) {
            throw invalid("AI " + path + " is invalid");
        }
        var values = new LinkedHashSet<String>();
        node.forEach(value -> {
            if (!values.add(code(value, path))) {
                throw invalid("AI " + path + " must be unique");
            }
        });
        return List.copyOf(values);
    }

    private static Set<String> fields(JsonNode node) {
        var fields = new LinkedHashSet<String>();
        node.fieldNames().forEachRemaining(fields::add);
        return Set.copyOf(fields);
    }

    private static String code(JsonNode node, String path) {
        var value = text(node, path);
        if (!value.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw invalid("AI " + path + " is not a stable code");
        }
        return value;
    }

    private static String text(JsonNode node, String path) {
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            throw invalid("AI " + path + " must be text");
        }
        return node.textValue();
    }

    private static int integer(
            JsonNode node, String path, int minimum, int maximum) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            throw invalid("AI " + path + " must be an integer");
        }
        var value = node.intValue();
        if (value < minimum || value > maximum) {
            throw invalid("AI " + path + " exceeds the policy limit");
        }
        return value;
    }

    private static com.unique.examine.core.error.BusinessException invalid(
            String message) {
        return AiSupport.invalid("AI_PLAN_INVALID", message);
    }

    public record Plan(
            String moduleCode,
            String canonicalQueryJson,
            List<String> outputFields,
            int limit
    ) {
        public Plan {
            outputFields = List.copyOf(outputFields);
        }
    }

    private static final class Counters {
        private int predicates;
    }
}
