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
        String mode = definition.path("mode").asText("ALL");
        if (!Set.of("ALL", "ANY").contains(mode)) issues.add("条件模式只能是 ALL 或 ANY");
        JsonNode conditions = definition.path("conditions");
        if (!conditions.isArray() || conditions.isEmpty()) issues.add("至少配置一个结构化条件");
        else for (int index = 0; index < conditions.size(); index++) {
            JsonNode condition = conditions.get(index);
            String field = condition.path("field").asText();
            String operator = condition.path("operator").asText();
            if (!activeFieldCodes.contains(field)) issues.add("条件 " + (index + 1) + " 引用不存在或已停用字段：" + field);
            if (!OPERATORS.contains(operator)) issues.add("条件 " + (index + 1) + " 使用不支持的运算符：" + operator);
            if (!Set.of("EMPTY", "NOT_EMPTY").contains(operator) && !condition.has("value")) {
                issues.add("条件 " + (index + 1) + " 缺少比较值");
            }
        }
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
        for (JsonNode condition : definition.path("conditions")) {
            String field = condition.path("field").asText();
            String operator = condition.path("operator").asText();
            JsonNode actual = sampleFields.path(field);
            JsonNode expected = condition.path("value");
            results.add(new ConditionResult(field, operator, matches(actual, operator, expected), actual, expected));
        }
        boolean matched = "ANY".equals(definition.path("mode").asText("ALL"))
                ? results.stream().anyMatch(ConditionResult::matched)
                : results.stream().allMatch(ConditionResult::matched);
        JsonNode effect = definition.path("effect");
        return new RuleTestResult(matched, effect.path("type").asText(),
                effect.path("field").asText(null), message, results);
    }

    private boolean matches(JsonNode actual, String operator, JsonNode expected) {
        return switch (operator) {
            case "EMPTY" -> actual.isMissingNode() || actual.isNull() || actual.asText().isBlank();
            case "NOT_EMPTY" -> !(actual.isMissingNode() || actual.isNull() || actual.asText().isBlank());
            case "EQ" -> actual.equals(expected) || actual.asText().equals(expected.asText());
            case "NE" -> !(actual.equals(expected) || actual.asText().equals(expected.asText()));
            case "CONTAINS" -> actual.isArray()
                    ? java.util.stream.StreamSupport.stream(actual.spliterator(), false).anyMatch(expected::equals)
                    : actual.asText().contains(expected.asText());
            case "IN" -> expected.isArray()
                    && java.util.stream.StreamSupport.stream(expected.spliterator(), false)
                    .anyMatch(value -> value.equals(actual) || value.asText().equals(actual.asText()));
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
            return false;
        }
    }
}
