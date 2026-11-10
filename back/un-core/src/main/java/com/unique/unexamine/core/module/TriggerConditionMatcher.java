package com.unique.unexamine.core.module;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.field.FieldDefinition;
import com.unique.unexamine.core.field.FieldType;
import com.unique.unexamine.core.field.FieldValidationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 解析模块 Flow/审批绑定的结构化触发条件。
 *
 * <p>条件只允许字段比较，不执行脚本、SpEL、SQL 或 Java 表达式。数组条件按 AND 处理；对象
 * 可使用 {@code conditions} 或 {@code all} 包装数组，也可直接表示一条条件。</p>
 */
public final class TriggerConditionMatcher {
    private static final Set<String> OPERATORS = Set.of("EMPTY", "NOT_EMPTY", "EQ", "NE", "IN", "NOT_IN",
            "CONTAINS", "STARTS_WITH", "ENDS_WITH", "GT", "GE", "LT", "LE");

    private TriggerConditionMatcher() {}

    /** 保存 Flow/审批线路前校验条件结构，字段是否属于具体模块由业务边界另行检查。 */
    public static void validate(ObjectMapper mapper, String conditionJson) {
        if (conditionJson == null || conditionJson.isBlank()) return;
        try {
            JsonNode root = mapper.readTree(conditionJson);
            for (JsonNode condition : flatten(root)) validateOne(condition);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("触发条件配置无效", ex);
        }
    }

    /** 所有条件命中时返回 true；空条件表示无条件触发。 */
    public static boolean matches(ObjectMapper mapper, String conditionJson, Map<String, Object> values) {
        if (conditionJson == null || conditionJson.isBlank()) return true;
        try {
            JsonNode root = mapper.readTree(conditionJson);
            List<JsonNode> conditions = flatten(root);
            for (JsonNode condition : conditions) if (!matchesOne(condition, values)) return false;
            return true;
        } catch (Exception ex) {
            throw new IllegalArgumentException("触发条件配置无效", ex);
        }
    }

    /**
     * 校验条件引用的字段都属于当前业务上下文。
     *
     * <p>条件结构本身由 {@link #validate(ObjectMapper, String)} 校验，具体模块绑定再调用本方法
     * 绑定当前有效字段，避免未知字段在运行时只得到 null 而静默跳过线路。</p>
     */
    public static void validateFieldKeys(ObjectMapper mapper, String conditionJson, Set<String> allowedKeys) {
        if (conditionJson == null || conditionJson.isBlank()) return;
        if (allowedKeys == null) throw new IllegalArgumentException("触发条件缺少字段上下文");
        try {
            Set<String> fieldKeys = fieldKeys(mapper, conditionJson);
            List<String> unknown = fieldKeys.stream().filter(key -> !allowedKeys.contains(key)).toList();
            if (!unknown.isEmpty()) throw new IllegalArgumentException("触发条件引用了不存在或已停用的字段: " + unknown);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("触发条件字段引用无效", ex);
        }
    }

    /**
     * 按模块当前字段定义校验触发条件的值形态，避免错误的数字、日期、选项或引用值保存到绑定配置后，
     * 运行时才静默匹配失败。模块边界另行负责租户成员和关联记录的存在性检查。
     */
    public static void validateFieldValues(ObjectMapper mapper, String conditionJson,
                                           Map<String, FieldDefinition> definitions,
                                           FieldValidationService validation) {
        if (conditionJson == null || conditionJson.isBlank()) return;
        if (definitions == null || validation == null) throw new IllegalArgumentException("触发条件缺少字段定义");
        try {
            JsonNode root = mapper.readTree(conditionJson);
            for (JsonNode condition : flatten(root)) {
                String fieldKey = text(condition, "fieldKey", text(condition, "field", null));
                FieldDefinition definition = definitions.get(fieldKey);
                if (definition == null) throw new IllegalArgumentException("触发条件引用了不存在的字段: " + fieldKey);
                String operator = text(condition, "operator", "EQ").toUpperCase(Locale.ROOT);
                if (Set.of("EMPTY", "NOT_EMPTY").contains(operator)) continue;
                JsonNode expected = condition.get("value");
                if (expected == null || expected.isNull())
                    throw new IllegalArgumentException("触发条件缺少比较值: " + fieldKey);
                if (Set.of("IN", "NOT_IN").contains(operator)) {
                    if (!expected.isArray()) throw new IllegalArgumentException("范围条件的比较值必须是数组: " + fieldKey);
                    if (Boolean.TRUE.equals(definition.rules().get("multiple")))
                        throw new IllegalArgumentException("多选字段不支持 IN/NOT_IN 条件: " + fieldKey);
                    for (JsonNode item : expected) validateValue(mapper, definition, item, validation);
                } else if (Set.of("CONTAINS", "STARTS_WITH", "ENDS_WITH").contains(operator)) {
                    if (definition.type().kind() != FieldType.Kind.TEXT || !expected.isTextual()
                            || expected.asText().isBlank() || expected.asText().length() > 100)
                        throw new IllegalArgumentException("文本条件只支持非空短文本字段: " + fieldKey);
                } else if (Set.of("GT", "GE", "LT", "LE").contains(operator)) {
                    if (definition.type() == FieldType.NUMBER) {
                        if (!expected.isNumber()) throw new IllegalArgumentException("数字大小条件的比较值必须是数字: " + fieldKey);
                    } else if (definition.type() == FieldType.DATE_TIME && expected.isTextual()) {
                        validateValue(mapper, definition, expected, validation);
                    } else {
                        throw new IllegalArgumentException("大小条件只支持数字或日期时间字段: " + fieldKey);
                    }
                } else if (Set.of("EQ", "NE").contains(operator)) {
                    validateValue(mapper, definition, expected, validation);
                } else {
                    throw new IllegalArgumentException("触发条件操作符无效: " + operator);
                }
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("触发条件字段值无效", ex);
        }
    }

    /** 将 JSON 值转换为字段校验入口接受的 Java 值，保持配置和业务保存使用同一类型规则。 */
    private static void validateValue(ObjectMapper mapper, FieldDefinition definition, JsonNode value,
                                      FieldValidationService validation) {
        Object converted = mapper.convertValue(value, Object.class);
        if (definition.type().computed()) validation.validateComputedValue(definition, converted);
        else validation.validateValue(definition, converted);
    }

    /** 读取结构化条件引用的字段 key，供模块和审批发布校验共用。 */
    public static Set<String> fieldKeys(ObjectMapper mapper, String conditionJson) {
        if (conditionJson == null || conditionJson.isBlank()) return Set.of();
        try {
            JsonNode root = mapper.readTree(conditionJson);
            Set<String> result = new LinkedHashSet<>();
            for (JsonNode condition : flatten(root)) {
                String fieldKey = text(condition, "fieldKey", text(condition, "field", null));
                if (fieldKey != null) result.add(fieldKey);
            }
            return Set.copyOf(result);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("触发条件字段引用无效", ex);
        }
    }

    private static List<JsonNode> flatten(JsonNode root) {
        if (root == null || root.isNull()) return List.of();
        if (root.isArray()) {
            List<JsonNode> result = new ArrayList<>();
            root.forEach(result::add);
            return result;
        }
        if (root.isObject()) {
            JsonNode wrapped = root.get("conditions");
            if (wrapped == null) wrapped = root.get("all");
            if (wrapped != null) return flatten(wrapped);
            return List.of(root);
        }
        throw new IllegalArgumentException("触发条件必须是对象或数组");
    }

    private static void validateOne(JsonNode condition) {
        if (condition == null || !condition.isObject()) throw new IllegalArgumentException("触发条件项必须是对象");
        String fieldKey = text(condition, "fieldKey", text(condition, "field", null));
        // Flow 审批结果是内部线路条件，只允许这几个明确的运行事件键，不开放任意双下划线字段。
        if (fieldKey == null || fieldKey.isBlank() || !fieldKey.matches("[a-z][a-zA-Z0-9_]{0,79}")
                && !Set.of("__examineStatus","__flowEvent","__flowAction").contains(fieldKey))
            throw new IllegalArgumentException("触发条件缺少有效字段 key");
        String operator = text(condition, "operator", "EQ").toUpperCase(Locale.ROOT);
        if (!OPERATORS.contains(operator)) throw new IllegalArgumentException("触发条件操作符无效: " + operator);
        if (Set.of("EMPTY", "NOT_EMPTY").contains(operator)) return;
        JsonNode expected = condition.get("value");
        if (expected == null || expected.isNull()) throw new IllegalArgumentException("触发条件缺少比较值");
        if (Set.of("IN", "NOT_IN").contains(operator) && !expected.isArray())
            throw new IllegalArgumentException("范围条件的比较值必须是数组");
        if (Set.of("GT", "GE", "LT", "LE").contains(operator)
                && !expected.isNumber() && !(expected.isTextual() && looksLikeDate(expected.asText())))
            throw new IllegalArgumentException("大小条件的比较值必须是数字或日期文本");
        if (Set.of("CONTAINS", "STARTS_WITH", "ENDS_WITH").contains(operator)
                && !expected.isValueNode()) throw new IllegalArgumentException("文本条件的比较值必须是简单值");
    }

    private static boolean matchesOne(JsonNode condition, Map<String, Object> values) {
        String fieldKey = text(condition, "fieldKey", text(condition, "field", null));
        String operator = text(condition, "operator", "EQ").toUpperCase();
        if (fieldKey == null || fieldKey.isBlank()) throw new IllegalArgumentException("触发条件缺少字段 key");
        Object actual = values.get(fieldKey);
        JsonNode expected = condition.get("value");
        return switch (operator) {
            case "EMPTY" -> empty(actual);
            case "NOT_EMPTY" -> !empty(actual);
            case "EQ" -> equal(actual, expected);
            case "NE" -> !equal(actual, expected);
            case "IN" -> expected != null && expected.isArray() && any(expected, item -> equal(actual, item));
            case "NOT_IN" -> expected == null || !expected.isArray() || !any(expected, item -> equal(actual, item));
            case "CONTAINS" -> String.valueOf(actual).contains(textValue(expected));
            case "STARTS_WITH" -> String.valueOf(actual).startsWith(textValue(expected));
            case "ENDS_WITH" -> String.valueOf(actual).endsWith(textValue(expected));
            case "GT", "GE", "LT", "LE" -> compare(actual, expected, operator);
            default -> throw new IllegalArgumentException("触发条件操作符无效: " + operator);
        };
    }

    private static boolean any(JsonNode array, java.util.function.Predicate<JsonNode> predicate) {
        for (JsonNode item : array) if (predicate.test(item)) return true;
        return false;
    }

    private static boolean compare(Object actual, JsonNode expected, String operator) {
        if (actual == null || expected == null) return false;
        Integer result = null;
        if (expected.isNumber()) {
            try { result = new BigDecimal(actual.toString()).compareTo(expected.decimalValue()); }
            catch (RuntimeException ignored) { return false; }
        } else if (expected.isTextual()) {
            result = compareDateTime(actual, expected.asText());
        }
        if (result == null) return false;
        return switch (operator) {
            case "GT" -> result > 0;
            case "GE" -> result >= 0;
            case "LT" -> result < 0;
            default -> result <= 0;
        };
    }

    /** 日期样式保存为日期文本，日期时间样式保存为 ISO 日期时间文本；两者统一到时间轴比较。 */
    private static Integer compareDateTime(Object actual, String expected) {
        try {
            LocalDateTime left = toDateTime(String.valueOf(actual));
            LocalDateTime right = toDateTime(expected);
            return left.compareTo(right);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static LocalDateTime toDateTime(String value) {
        return value != null && value.length() == 10
                ? LocalDate.parse(value).atStartOfDay() : LocalDateTime.parse(value);
    }

    private static boolean looksLikeDate(String value) {
        try { toDateTime(value); return true; }
        catch (RuntimeException ex) { return false; }
    }

    private static boolean equal(Object actual, JsonNode expected) {
        if (actual == null || expected == null || expected.isNull()) return actual == null;
        if (actual instanceof Number && expected.isNumber())
            return new BigDecimal(actual.toString()).compareTo(expected.decimalValue()) == 0;
        if (actual instanceof Iterable<?> iterable && expected.isArray()) {
            List<Object> actualValues = new ArrayList<>();
            iterable.forEach(actualValues::add);
            if (actualValues.size() != expected.size()) return false;
            Iterator<JsonNode> expectedIterator = expected.elements();
            for (Object item : actualValues) if (!equal(item, expectedIterator.next())) return false;
            return true;
        }
        return textValue(expected).equals(String.valueOf(actual));
    }

    private static boolean empty(Object value) {
        return value == null || value instanceof String text && text.isBlank()
                || value instanceof Iterable<?> iterable && !iterable.iterator().hasNext();
    }

    private static String text(JsonNode node, String key, String fallback) {
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? fallback : value.asText();
    }

    private static String textValue(JsonNode node) {
        return node == null || node.isNull() ? "" : node.isValueNode() ? node.asText() : node.toString();
    }
}
