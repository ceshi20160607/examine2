package com.unique.unexamine.moduleconfig.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 业务规则、审批分支、触发绑定与共享范围共用的类型条件协议。字段描述必须来自可信发布目录。 */
@Component
public class StructuredConditionProtocol {
    /** 尚未贯通的字段家族保留后续实现，不能退化为用户手写标识或配置。 */
    public static final Set<String> TYPES = Set.of("TEXT", "MULTILINE_TEXT", "PHONE", "EMAIL", "URL", "ID_CARD", "BARCODE", "QRCODE",
            "NUMBER", "MONEY", "DATE", "DATETIME", "BOOLEAN", "SINGLE_SELECT", "MULTI_SELECT", "STATUS");
    private static final Set<String> ORDERED_TYPES = Set.of("NUMBER", "MONEY", "DATE", "DATETIME");
    private static final Set<String> CONTAINS_TYPES = Set.of("TEXT", "MULTILINE_TEXT", "PHONE", "EMAIL", "URL", "ID_CARD", "BARCODE", "QRCODE", "MULTI_SELECT");
    private static final Set<String> OPTION_TYPES = Set.of("SINGLE_SELECT", "MULTI_SELECT", "STATUS");
    private final ObjectMapper json;
    private final StructuredRuleEvaluator evaluator;

    /** 选项只包含当前可读的编码/名称，不携带字段默认值或业务记录内容。 */
    public record Field(String code, String name, String fieldType, JsonNode config) { }

    public StructuredConditionProtocol(ObjectMapper json, StructuredRuleEvaluator evaluator) {
        this.json = json;
        this.evaluator = evaluator;
    }

    /** 校验整棵树并返回规范化副本；不修改客户端对象，不根据文本猜测字段类型。 */
    public JsonNode normalize(JsonNode condition, List<Field> fields, String errorCode) {
        Map<String, Field> catalog = catalog(fields);
        List<String> issues = evaluator.inspectConditions(condition, catalog.keySet());
        if (!issues.isEmpty()) throw invalid(errorCode, issues.getFirst());
        JsonNode result = condition.deepCopy();
        normalizeNode(result, catalog, errorCode);
        return result;
    }

    /** 预览和实例执行使用同一类型语义；无效的实际值报错，不能把解析失败判为“不等于”。 */
    public boolean matches(JsonNode condition, JsonNode values, List<Field> fields) {
        return matches(condition, values, fields, new ArrayList<>());
    }

    /** 普通预览可以返回当前已授权字段的逐项结果；运行调用方不必返回或记录字段值。 */
    public boolean matches(JsonNode condition, JsonNode values, List<Field> fields,
                           List<ModuleRuleModels.ConditionResult> results) {
        JsonNode normalized = normalize(condition, fields, "CONDITION_INVALID");
        ObjectNode actual = values != null && values.isObject() ? ((ObjectNode) values).deepCopy() : json.createObjectNode();
        normalizeActual(normalized, actual, catalog(fields));
        return evaluator.evaluateConditions(normalized, actual, results);
    }

    private Map<String, Field> catalog(List<Field> fields) {
        Map<String, Field> result = new LinkedHashMap<>();
        fields.stream().filter(field -> TYPES.contains(field.fieldType())).forEach(field -> result.put(field.code(), field));
        return result;
    }

    private void normalizeNode(JsonNode node, Map<String, Field> fields, String code) {
        if (node.has("conditions")) {
            node.path("conditions").forEach(child -> normalizeNode(child, fields, code));
            return;
        }
        Field field = fields.get(node.path("field").asText());
        String op = node.path("operator").asText();
        if (Set.of("EMPTY", "NOT_EMPTY").contains(op)) return;
        if (Set.of("GT", "GTE", "LT", "LTE").contains(op) && !ORDERED_TYPES.contains(field.fieldType()))
            throw invalid(code, field.name() + "不支持大小比较");
        if ("CONTAINS".equals(op) && !CONTAINS_TYPES.contains(field.fieldType()))
            throw invalid(code, field.name() + "不支持包含比较");
        JsonNode value = node.path("value");
        if ("IN".equals(op)) {
            if ("MULTI_SELECT".equals(field.fieldType()) || value.isEmpty())
                throw invalid(code, field.name() + "的任一值条件不可用或为空");
            ArrayNode normalized = json.createArrayNode();
            value.forEach(item -> normalized.add(normalizeValue(field, item, false, code, true)));
            ((ObjectNode) node).set("value", normalized);
        } else ((ObjectNode) node).set("value", normalizeValue(field, value, "CONTAINS".equals(op), code, true));
    }

    private void normalizeActual(JsonNode node, ObjectNode actual, Map<String, Field> fields) {
        if (node.has("conditions")) {
            node.path("conditions").forEach(child -> normalizeActual(child, actual, fields));
            return;
        }
        String fieldCode = node.path("field").asText();
        JsonNode value = actual.path(fieldCode);
        // 缺值保留原空值语义；历史选项值可以用于 EMPTY/NE，但非法条件常量不能发布。
        if (value.isMissingNode() || value.isNull()) return;
        actual.set(fieldCode, normalizeValue(fields.get(fieldCode), value, false, "CONDITION_RUNTIME_VALUE_INVALID", false));
    }

    private JsonNode normalizeValue(Field field, JsonNode value, boolean contains, String code, boolean checkOptions) {
        String type = field.fieldType();
        boolean valid = value != null && !value.isNull();
        if (Set.of("NUMBER", "MONEY").contains(type)) valid &= value != null && value.isNumber();
        else if ("BOOLEAN".equals(type)) valid &= value != null && value.isBoolean();
        else if ("MULTI_SELECT".equals(type) && !contains) valid &= value != null && value.isArray()
                && java.util.stream.StreamSupport.stream(value.spliterator(), false).allMatch(JsonNode::isTextual);
        else valid &= value != null && value.isTextual();
        if (!valid) throw invalid(code, field.name() + "的比较值类型不正确");
        if (checkOptions && OPTION_TYPES.contains(type)) {
            List<String> options = new ArrayList<>();
            field.config().path("options").forEach(option -> options.add(option.path("value").asText(option.path("code").asText())));
            List<JsonNode> selected = value.isArray() ? java.util.stream.StreamSupport.stream(value.spliterator(), false).toList() : List.of(value);
            if (selected.stream().anyMatch(item -> !options.contains(item.asText())))
                throw invalid(code, field.name() + "的选项已失效，请重新选择");
        }
        try {
            if ("DATE".equals(type)) return TextNode.valueOf(LocalDate.parse(value.asText()).toString());
            if ("DATETIME".equals(type)) {
                try { return TextNode.valueOf(DateTimeFieldProtocol.normalize(value.asText())); }
                catch (IllegalArgumentException invalid) { throw invalid(code, field.name() + "的比较值格式不正确或超过毫秒精度"); }
            }
            if (Set.of("NUMBER", "MONEY").contains(type)) value.decimalValue();
        } catch (java.time.format.DateTimeParseException | NumberFormatException failure) {
            throw invalid(code, field.name() + "的比较值格式不正确");
        }
        if ("MULTI_SELECT".equals(type) && !contains) {
            ArrayNode normalized = json.createArrayNode();
            java.util.stream.StreamSupport.stream(value.spliterator(), false).map(JsonNode::asText).distinct()
                    .sorted(Comparator.naturalOrder()).forEach(normalized::add);
            return normalized;
        }
        return value.deepCopy();
    }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
