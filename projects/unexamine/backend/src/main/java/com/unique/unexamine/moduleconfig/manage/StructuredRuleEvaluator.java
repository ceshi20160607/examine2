package com.unique.unexamine.moduleconfig.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.unexamine.moduleconfig.manage.ModuleRuleModels.ConditionResult;
import com.unique.unexamine.moduleconfig.manage.ModuleRuleModels.RuleTestResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class StructuredRuleEvaluator {
    private static final Set<String> OPERATORS = Set.of(
            "EQ", "NE", "GT", "GTE", "LT", "LTE", "IN", "CONTAINS", "EMPTY", "NOT_EMPTY");
    private static final Set<String> EFFECTS = Set.of(
            "BLOCK", "REQUIRE_FIELD", "SET_VISIBILITY", "SET_EDITABLE", "REQUIRE_APPROVAL", "DELETE_ROLE");

    public List<String> inspect(JsonNode definition, Set<String> activeFieldCodes) {
        List<String> issues = new ArrayList<>();
        if (definition == null || !definition.isObject()) return List.of("规则定义必须是结构化对象");
        issues.addAll(inspectConditions(definition, activeFieldCodes));
        if (definition.path("conditions").isEmpty()) issues.add("至少配置一个结构化条件");
        JsonNode effect = definition.path("effect");
        String effectType = effect.path("type").asText();
        if (!EFFECTS.contains(effectType)) issues.add("规则结果类型不受支持");
        if (Set.of("REQUIRE_FIELD", "SET_VISIBILITY", "SET_EDITABLE", "REQUIRE_APPROVAL").contains(effectType)
                && !activeFieldCodes.contains(effect.path("field").asText())) {
            issues.add("规则结果引用不存在或已停用字段：" + effect.path("field").asText());
        }
        if ("DELETE_ROLE".equals(effectType) && effect.path("roleCode").asText().isBlank()) {
            issues.add("指定角色删除规则必须配置角色编码");
        }
        return issues;
    }

    public RuleTestResult evaluate(JsonNode definition, JsonNode sampleFields, String message) {
        List<ConditionResult> results = new ArrayList<>();
        boolean matched = evaluateConditions(definition, sampleFields, results);
        JsonNode effect = definition.path("effect");
        return new RuleTestResult(matched, effect.path("type").asText(),
                effect.path("field").asText(null), message, results);
    }

    /** Shared tree contract for business rules, trigger bindings and Flow conditions. */
    public List<String> inspectConditions(JsonNode condition, Set<String> allowedFields) {
        List<String> issues = new ArrayList<>();
        inspectCondition(condition, allowedFields, 0, new int[]{0}, issues);
        return issues;
    }

    private void inspectCondition(JsonNode node, Set<String> fields, int depth, int[] count, List<String> issues) {
        if (++count[0] > 100 || depth > 8) { issues.add("条件最多 100 项、8 层"); return; }
        if (node == null || !node.isObject()) { issues.add("条件必须是结构化对象"); return; }
        if (node.has("conditions")) {
            if (!Set.of("ALL", "ANY").contains(node.path("mode").asText("ALL"))) issues.add("条件组只能选择全部或任一满足");
            if (!node.path("conditions").isArray()) { issues.add("条件组内容必须是数组"); return; }
            if ("ANY".equals(node.path("mode").asText()) && node.path("conditions").isEmpty()) issues.add("任一满足的条件组不能为空");
            for (JsonNode child : node.path("conditions")) inspectCondition(child, fields, depth + 1, count, issues);
        } else {
            if (!fields.contains(node.path("field").asText())) issues.add("条件引用未发布、不可读取或不支持的字段");
            String op = node.path("operator").asText();
            if (!OPERATORS.contains(op)) issues.add("条件运算符不受支持");
            if (!Set.of("EMPTY", "NOT_EMPTY").contains(op) && !node.has("value")) issues.add("条件缺少比较值");
            if ("IN".equals(op) && !node.path("value").isArray()) issues.add("属于任一值需要配置多个比较值");
        }
    }

    public boolean evaluateConditions(JsonNode node, JsonNode fields, List<ConditionResult> results) {
        if (node.has("conditions")) {
            List<Boolean> children = new ArrayList<>();
            for (JsonNode child : node.path("conditions")) children.add(evaluateConditions(child, fields, results));
            return "ANY".equals(node.path("mode").asText("ALL"))
                    ? children.stream().anyMatch(Boolean::booleanValue) : children.stream().allMatch(Boolean::booleanValue);
        }
        String code = node.path("field").asText();
        String op = node.path("operator").asText();
        JsonNode actual = fields.path(code), expected = node.path("value");
        boolean matched = matches(actual, op, expected);
        results.add(new ConditionResult(code, op, matched, actual, expected));
        return matched;
    }

    private boolean empty(JsonNode value) {
        return value.isMissingNode() || value.isNull() || value.isContainerNode() && value.isEmpty()
                || value.isTextual() && value.asText().isBlank();
    }

    private boolean equal(JsonNode left, JsonNode right) {
        return left.isNumber() && right.isNumber() ? left.decimalValue().compareTo(right.decimalValue()) == 0 : left.equals(right);
    }

    private boolean matches(JsonNode actual, String operator, JsonNode expected) {
        return switch (operator) {
            case "EMPTY" -> empty(actual);
            case "NOT_EMPTY" -> !empty(actual);
            case "EQ" -> equal(actual, expected);
            case "NE" -> !equal(actual, expected);
            case "CONTAINS" -> actual.isArray()
                    ? java.util.stream.StreamSupport.stream(actual.spliterator(), false).anyMatch(value -> equal(value, expected))
                    : actual.isTextual() && expected.isTextual() && actual.asText().contains(expected.asText());
            case "IN" -> expected.isArray()
                    && java.util.stream.StreamSupport.stream(expected.spliterator(), false)
                    .anyMatch(value -> equal(value, actual));
            case "GT" -> compare(actual, expected, comparison -> comparison > 0);
            case "GTE" -> compare(actual, expected, comparison -> comparison >= 0);
            case "LT" -> compare(actual, expected, comparison -> comparison < 0);
            case "LTE" -> compare(actual, expected, comparison -> comparison <= 0);
            default -> false;
        };
    }

    private boolean compare(JsonNode actual, JsonNode expected, java.util.function.IntPredicate predicate) {
        try {
            BigDecimal left = actual.isNumber() ? actual.decimalValue() : new BigDecimal(actual.asText());
            BigDecimal right = expected.isNumber() ? expected.decimalValue() : new BigDecimal(expected.asText());
            return predicate.test(left.compareTo(right));
        } catch (Exception ignored) {
            try {
                return predicate.test(temporal(actual.asText()).compareTo(temporal(expected.asText())));
            } catch (Exception invalidDate) { return false; }
        }
    }

    private java.time.Instant temporal(String value) {
        if (value.length() == 10) return java.time.LocalDate.parse(value).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
        try { return java.time.OffsetDateTime.parse(value).toInstant(); }
        catch (java.time.format.DateTimeParseException ignored) { return java.time.LocalDateTime.parse(value).toInstant(java.time.ZoneOffset.UTC); }
    }
}
