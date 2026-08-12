package com.unique.examine.module.manage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.unique.examine.module.manage.api.ConfigRequests;
import com.unique.examine.module.manage.api.ConfigTypes;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Currency;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.unique.examine.module.manage.api.ConfigTypes.*;

@Component
public class StructuredPropertyValidator {
    static final int PROPERTY_MAX_BYTES = 65_536;
    static final int LAYOUT_MAX_BYTES = 131_072;
    static final int JSON_MAX_DEPTH = 8;
    static final int LAYOUT_MAX_DEPTH = 16;
    static final int RULE_MAX_DEPTH = 5;

    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "script", "sql", "javascript", "groovy", "spel", "class", "className", "runtime"
    );
    private static final Set<String> COMMON_FIELD_KEYS = Set.of(
            "placeholder", "helpText", "defaultMode", "defaultValue", "defaultFormula", "parentFieldId",
            "displayFormat", "width", "unit", "mask", "sensitive", "unique", "validationMessage"
    );
    private static final Set<FieldType> P4C4_DERIVED_FIELDS = Set.of(
            FieldType.FORMULA, FieldType.SUMMARY, FieldType.CALCULATED,
            FieldType.LOOKUP, FieldType.AGGREGATE, FieldType.AI_FILL
    );
    private static final Set<String> DERIVED_PRESENTATION_KEYS = Set.of(
            "helpText", "displayFormat", "width", "unit", "resultSchema"
    );
    private static final Set<String> TEXT_KEYS = Set.of(
            "minLength", "maxLength", "pattern", "trim", "multiline", "sanitize", "rows"
    );
    private static final Set<String> NUMBER_KEYS = Set.of(
            "minimum", "maximum", "precision", "scale", "roundingMode", "thousandsSeparator", "step"
    );
    private static final Set<String> DATE_KEYS = Set.of(
            "minimum", "maximum", "includeTime", "timezone", "format"
    );
    private static final Set<String> OPTION_KEYS = Set.of(
            "multiple", "clearable", "searchable", "displayStyle", "maxSelections"
    );
    private static final Set<String> FILE_KEYS = Set.of(
            "maxFiles", "maxSizeMb", "allowedExtensions", "imageOnly", "preview", "watermark"
    );
    private static final Set<String> RELATION_KEYS = Set.of(
            "multiple", "displayFieldId", "filter", "allowCreate", "reverseRelation"
    );
    private static final Set<String> REFERENCE_KEYS = Set.of("sourceFieldId", "targetFieldId");
    private static final Set<String> LEGACY_RELATION_KEYS = Set.of(
            "multiple", "displayFieldId", "valueFieldId", "filter", "aggregation",
            "sourceFieldId", "allowCreate", "reverseRelation"
    );
    private static final Set<String> MONEY_KEYS = Set.of("currency", "currencies", "fixedCurrency");
    private static final Set<String> ACTOR_KEYS = Set.of("multiple", "selectionScope", "allowInactive");
    private static final Set<String> SUBTABLE_KEYS = Set.of(
            "minRows", "maxRows", "columnFieldIds", "allowRowCreate", "allowRowUpdate", "allowRowDelete",
            "allowRowReorder", "aggregates"
    );
    private static final Set<String> GEO_KEYS = Set.of("coordinateSystem", "geoPrecision");
    private static final Set<String> PHONE_KEYS = Set.of("defaultCountry");
    private static final Set<String> IDENTITY_KEYS = Set.of("identityKind");
    private static final Set<String> BARCODE_KEYS = Set.of("symbologies");
    private static final Set<String> STATUS_KEYS = Set.of("initialStateIds", "transitions");
    private static final Set<String> AUTO_NUMBER_KEYS = Set.of("autoNumberPrefix", "digits");
    private static final Set<String> FORMULA_KEYS = Set.of("astVersion", "expressionAst");
    private static final Set<String> SUMMARY_KEYS = Set.of("relationFieldId", "targetFieldId", "reduction");
    private static final Set<String> CALCULATED_KEYS = Set.of("astVersion", "expressionAst");
    private static final Set<String> LOOKUP_KEYS = Set.of("relationFieldId", "targetFieldId", "distinct");
    private static final Set<String> AGGREGATE_KEYS = Set.of("subtableFieldId", "aggregateId");
    private static final Set<String> AI_KEYS = Set.of(
            "sourceFieldIds", "promptTemplate", "modelPolicy", "minConfidence", "overwriteMode");
    private static final Set<String> JSON_KEYS = Set.of("jsonSchema", "queryPaths");
    private static final Set<String> ADDRESS_KEYS = Set.of("addressLevel");
    private static final Set<String> RATING_KEYS = Set.of("maxRating", "step");
    private static final Set<String> PROGRESS_KEYS = Set.of("step");
    private static final Set<String> COMPONENT_KEYS = Set.of(
            "label", "title", "description", "visible", "collapsible", "collapsed", "columns",
            "gap", "align", "variant", "content", "actionId", "labelPosition", "emptyText"
    );
    private static final Set<String> LAYOUT_KEYS = Set.of(
            "columns", "gap", "labelPosition", "density", "stickyActions", "pageSize",
            "defaultSort", "showSearch", "showFilters", "sections",
            "filterScenarios", "defaultFilterScenarioCode"
    );
    private static final Set<String> ACTION_KEYS = Set.of(
            "style", "iconKey", "openMode", "successMessage", "approvalFlowId", "targetPageId",
            "selectionMode", "danger", "disabledReason"
    );

    private final ObjectMapper objectMapper;
    private final ConfigFilterScenarioValidator filterScenarios;

    public StructuredPropertyValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.filterScenarios = new ConfigFilterScenarioValidator(objectMapper);
    }

    public String field(FieldType type, JsonNode properties) {
        requireObject(properties, "properties");
        validateJson(properties, "properties", PROPERTY_MAX_BYTES, JSON_MAX_DEPTH);
        var derived = P4C4_DERIVED_FIELDS.contains(type);
        var allowed = new HashSet<>(derived ? DERIVED_PRESENTATION_KEYS : COMMON_FIELD_KEYS);
        if (!derived && ConfigTypes.TEXT_FIELDS.contains(type)) {
            allowed.addAll(TEXT_KEYS);
        }
        if (!derived && ConfigTypes.NUMERIC_FIELDS.contains(type)) {
            allowed.addAll(NUMBER_KEYS);
        }
        if (type == FieldType.DATE || type == FieldType.DATETIME || type == FieldType.DATE_RANGE
                || type == FieldType.TIME || type == FieldType.TIME_RANGE
                || type == FieldType.CREATED_AT || type == FieldType.UPDATED_AT) {
            allowed.addAll(DATE_KEYS);
        }
        if (ConfigTypes.DICTIONARY_FIELDS.contains(type) || type == FieldType.SWITCH) {
            allowed.addAll(OPTION_KEYS);
        }
        if (type == FieldType.ATTACHMENT || type == FieldType.IMAGE || type == FieldType.FILE_GROUP
                || type == FieldType.SIGNATURE) {
            allowed.addAll(FILE_KEYS);
        }
        if (type == FieldType.RELATION) {
            allowed.addAll(RELATION_KEYS);
        } else if (type == FieldType.REFERENCE) {
            allowed.addAll(REFERENCE_KEYS);
        } else if (!derived && ConfigTypes.RELATION_FIELDS.contains(type) && type != FieldType.SUBTABLE) {
            allowed.addAll(LEGACY_RELATION_KEYS);
        }
        if (type == FieldType.MONEY) {
            allowed.addAll(MONEY_KEYS);
        }
        if (type == FieldType.MEMBER || type == FieldType.DEPARTMENT || type == FieldType.TENANT) {
            allowed.addAll(ACTOR_KEYS);
        }
        if (type == FieldType.SUBTABLE) {
            allowed.addAll(SUBTABLE_KEYS);
        }
        if (type == FieldType.GEO) {
            allowed.addAll(GEO_KEYS);
        }
        if (type == FieldType.PHONE) {
            allowed.addAll(PHONE_KEYS);
        }
        if (type == FieldType.IDENTITY) {
            allowed.addAll(IDENTITY_KEYS);
        }
        if (type == FieldType.BARCODE) {
            allowed.addAll(BARCODE_KEYS);
        }
        if (type == FieldType.STATUS) {
            allowed.addAll(STATUS_KEYS);
        }
        if (type == FieldType.AUTO_NUMBER) {
            allowed.addAll(AUTO_NUMBER_KEYS);
        }
        if (type == FieldType.FORMULA) {
            allowed.addAll(FORMULA_KEYS);
        }
        if (type == FieldType.SUMMARY) {
            allowed.addAll(SUMMARY_KEYS);
        }
        if (type == FieldType.CALCULATED) {
            allowed.addAll(CALCULATED_KEYS);
        }
        if (type == FieldType.LOOKUP) {
            allowed.addAll(LOOKUP_KEYS);
        }
        if (type == FieldType.AGGREGATE) {
            allowed.addAll(AGGREGATE_KEYS);
        }
        if (type == FieldType.AI_FILL) {
            allowed.addAll(AI_KEYS);
            if (properties instanceof com.fasterxml.jackson.databind.node.ObjectNode object
                    && !properties.has("minConfidence")) {
                object.put("minConfidence", new BigDecimal("0.80"));
            }
        }
        if (type == FieldType.JSON) {
            allowed.addAll(JSON_KEYS);
        }
        if (type == FieldType.ADDRESS) {
            allowed.addAll(ADDRESS_KEYS);
        }
        if (type == FieldType.RATING) {
            allowed.addAll(RATING_KEYS);
        }
        if (type == FieldType.PROGRESS) {
            allowed.addAll(PROGRESS_KEYS);
        }
        rejectUnknown(properties, allowed, "properties");
        validateTextLength(properties, "placeholder", 500);
        validateTextLength(properties, "helpText", 2000);
        validateTextLength(properties, "displayFormat", 128);
        validateTextLength(properties, "unit", 64);
        validateTextLength(properties, "mask", 256);
        validateTextLength(properties, "validationMessage", 500);
        validateTextLength(properties, "defaultFormula", 2000);
        validateTextLength(properties, "parentFieldId", 32);
        validateIntegerRange(properties, "width", 40, 1200);
        validateIntegerRange(properties, "minLength", 0, 100_000);
        validateIntegerRange(properties, "maxLength", 1, 1_000_000);
        validateIntegerRange(properties, "rows", 1, 100);
        validateIntegerRange(properties, "precision", 1, 38);
        validateIntegerRange(properties, "scale", 0, 18);
        validateIntegerRange(properties, "maxFiles", 1, 100);
        validateIntegerRange(properties, "maxSizeMb", 1, 1024);
        validateIntegerRange(properties, "maxSelections", 1, 1000);
        validateIntegerRange(properties, "maxRating", 1, 20);
        validateIntegerRange(properties, "digits", 1, 32);
        validateIntegerRange(properties, "minRows", 0, 200);
        validateIntegerRange(properties, "maxRows", 1, 200);
        validateIntegerRange(properties, "geoPrecision", 0, 8);
        validateTextLength(properties, "pattern", 500);
        validateTextLength(properties, "formula", 2000);
        validateTextLength(properties, "expression", 2000);
        validateTextLength(properties, "promptTemplate", 4000);
        validateTextLength(properties, "modelPolicy", 128);
        validateTextLength(properties, "autoNumberPrefix", 64);
        validateTextLength(properties, "timezone", 64);
        validateTextLength(properties, "format", 128);
        validateTextLength(properties, "displayFieldId", 32);
        validateTextLength(properties, "sourceFieldId", 32);
        validateTextLength(properties, "valueFieldId", 32);
        validateTextLength(properties, "targetFieldId", 32);
        validateTextLength(properties, "currency", 3);
        validateTextLength(properties, "fixedCurrency", 3);
        validateTextLength(properties, "aggregation", 16);
        validateBooleans(properties, "sensitive", "unique", "trim", "multiline", "sanitize",
                "thousandsSeparator", "includeTime", "multiple", "clearable", "searchable", "imageOnly",
                "preview", "watermark", "cascadeDelete", "allowInactive", "allowCreate", "reverseRelation",
                "allowRowCreate", "allowRowUpdate", "allowRowDelete", "allowRowReorder", "distinct");
        validateEnum(properties, "defaultMode", Set.of(
                "NONE", "FIXED", "CURRENT_USER", "CURRENT_DEPARTMENT", "CURRENT_DATE", "FORMULA", "PARENT_FIELD"));
        validateEnum(properties, "roundingMode", Set.of(
                "UP", "DOWN", "CEILING", "FLOOR", "HALF_UP", "HALF_DOWN", "HALF_EVEN", "UNNECESSARY"));
        validateEnum(properties, "displayStyle", Set.of("DEFAULT", "TAG", "BUTTON"));
        validateEnum(properties, "aggregation", Set.of("COUNT", "SUM", "AVG", "MIN", "MAX"));
        validateEnum(properties, "addressLevel", Set.of("COUNTRY", "PROVINCE", "CITY", "DISTRICT", "STREET"));
        validateEnum(properties, "selectionScope", Set.of("ALL", "CURRENT_TENANT", "CURRENT_DEPARTMENT", "SPECIFIED"));
        validateEnum(properties, "coordinateSystem", Set.of("WGS84"));
        validateCurrency(properties);
        validateStringArray(properties, "currencies", 50, 3);
        validateStringArray(properties, "allowedExtensions", 100, 32);
        validateStringArray(properties, "columnFieldIds", 200, 32);
        validateObjectValue(properties, "filter");
        validateConditionObject(properties, "filter");
        validateObjectValue(properties, "jsonSchema");
        validatePositiveNumber(properties, "step");
        validateDeclarativeExpression(properties, "formula");
        validateDeclarativeExpression(properties, "expression");
        validateDeclarativeExpression(properties, "defaultFormula");
        validateDefaultValue(type, properties);
        validateP4C1Properties(type, properties);
        validateP4C2Properties(type, properties);
        validateP4C3Properties(type, properties);
        validateP4C4Properties(type, properties);
        var precision = integer(properties, "precision");
        var scale = integer(properties, "scale");
        if (precision != null && scale != null && scale > precision) {
            throw ConfigErrors.invalid("properties.scale 不能大于 precision");
        }
        var minLength = integer(properties, "minLength");
        var maxLength = integer(properties, "maxLength");
        if (minLength != null && maxLength != null && minLength > maxLength) {
            throw ConfigErrors.invalid("properties.minLength 不能大于 maxLength");
        }
        var minRows = integer(properties, "minRows");
        var maxRows = integer(properties, "maxRows");
        if (minRows != null && maxRows != null && minRows > maxRows) {
            throw ConfigErrors.invalid("properties.minRows 不能大于 maxRows");
        }
        if (ConfigTypes.NUMERIC_FIELDS.contains(type)) {
            validateNumberOrder(properties, "minimum", "maximum");
        } else if (type == FieldType.DATE || type == FieldType.DATETIME || type == FieldType.DATE_RANGE
                || type == FieldType.TIME || type == FieldType.TIME_RANGE
                || type == FieldType.CREATED_AT || type == FieldType.UPDATED_AT) {
            validateTextLength(properties, "minimum", 64);
            validateTextLength(properties, "maximum", 64);
        }
        return write(properties);
    }

    private void validateConditionObject(JsonNode properties, String key) {
        if (!properties.hasNonNull(key)) return;
        try {
            validateCondition(objectMapper.treeToValue(properties.get(key), ConfigRequests.Condition.class), 1);
        } catch (JsonProcessingException exception) {
            throw ConfigErrors.invalid("properties." + key + " 不是有效的结构化条件");
        }
    }

    private void validateDefaultValue(FieldType type, JsonNode properties) {
        if (!properties.hasNonNull("defaultMode")) return;
        var mode = properties.path("defaultMode").asText();
        var hasValue = properties.has("defaultValue") && !properties.get("defaultValue").isNull();
        var hasFormula = properties.hasNonNull("defaultFormula")
                && !properties.path("defaultFormula").asText().isBlank();
        var hasParent = properties.hasNonNull("parentFieldId")
                && !properties.path("parentFieldId").asText().isBlank();
        if ("FIXED".equals(mode) && !hasValue) {
            throw ConfigErrors.invalid("FIXED 默认值必须配置 defaultValue");
        }
        if ("FIXED".equals(mode)) {
            validateFixedDefault(type, properties.get("defaultValue"), properties.path("multiple").asBoolean(false));
        }
        if ("CURRENT_USER".equals(mode) && type != FieldType.MEMBER) {
            throw ConfigErrors.invalid("CURRENT_USER 默认值只适用于人员字段");
        }
        if ("CURRENT_DEPARTMENT".equals(mode) && type != FieldType.DEPARTMENT) {
            throw ConfigErrors.invalid("CURRENT_DEPARTMENT 默认值只适用于部门字段");
        }
        if ("CURRENT_DATE".equals(mode) && type != FieldType.DATE && type != FieldType.DATETIME) {
            throw ConfigErrors.invalid("CURRENT_DATE 默认值只适用于日期或日期时间字段");
        }
        if ("FORMULA".equals(mode) && !hasFormula) {
            throw ConfigErrors.invalid("FORMULA 默认值必须配置 defaultFormula");
        }
        if ("PARENT_FIELD".equals(mode) && !hasParent) {
            throw ConfigErrors.invalid("PARENT_FIELD 默认值必须配置 parentFieldId");
        }
        if (!"FIXED".equals(mode) && hasValue) {
            throw ConfigErrors.invalid("只有 FIXED 默认值可以配置 defaultValue");
        }
        if (!"FORMULA".equals(mode) && hasFormula) {
            throw ConfigErrors.invalid("只有 FORMULA 默认值可以配置 defaultFormula");
        }
        if (!"PARENT_FIELD".equals(mode) && hasParent) {
            throw ConfigErrors.invalid("只有 PARENT_FIELD 默认值可以配置 parentFieldId");
        }
    }

    private void validateFixedDefault(FieldType type, JsonNode value, boolean multiple) {
        if (ConfigTypes.SYSTEM_FIELDS.contains(type) || ConfigTypes.RELATION_FIELDS.contains(type)
                || type == FieldType.AUTO_NUMBER || type == FieldType.FORMULA || type == FieldType.AI_FILL
                || type == FieldType.ATTACHMENT || type == FieldType.IMAGE || type == FieldType.FILE_GROUP
                || type == FieldType.SIGNATURE || type == FieldType.MEMBER || type == FieldType.DEPARTMENT
                || type == FieldType.TENANT || type == FieldType.GEO) {
            throw ConfigErrors.invalid(type + " 字段不支持固定默认值");
        }
        if (ConfigTypes.NUMERIC_FIELDS.contains(type) && type != FieldType.MONEY && !value.isNumber()) {
            throw ConfigErrors.invalid(type + " 字段的固定默认值必须是数字");
        }
        if (type == FieldType.MONEY) {
            if (!value.isObject() || value.size() != 2 || !value.path("amount").isTextual()
                    || !value.path("currency").isTextual()) {
                throw ConfigErrors.invalid("MONEY fixed default must contain amount and currency");
            }
            return;
        }
        if (type == FieldType.SWITCH && !value.isBoolean()) {
            throw ConfigErrors.invalid("SWITCH 字段的固定默认值必须是布尔值");
        }
        if (type == FieldType.DATE_RANGE || type == FieldType.TIME_RANGE) {
            if (!value.isArray() || value.size() != 2 || !value.get(0).isTextual() || !value.get(1).isTextual()) {
                throw ConfigErrors.invalid(type + " 字段的固定默认值必须是两个文本边界");
            }
            return;
        }
        if (type == FieldType.DATE || type == FieldType.DATETIME || type == FieldType.TIME) {
            if (!value.isTextual()) throw ConfigErrors.invalid(type + " 字段的固定默认值必须是文本时间值");
            return;
        }
        if (ConfigTypes.DICTIONARY_FIELDS.contains(type)) {
            if (multiple || type == FieldType.MULTI_SELECT) {
                if (!value.isArray() || value.isEmpty()) {
                    throw ConfigErrors.invalid(type + " 字段的固定默认值必须是非空选项数组");
                }
                for (var item : value) {
                    if (!item.isTextual() && !item.isIntegralNumber()) {
                        throw ConfigErrors.invalid(type + " 字段的固定默认值包含无效选项");
                    }
                }
            } else if (!value.isTextual() && !value.isIntegralNumber()) {
                throw ConfigErrors.invalid(type + " 字段的固定默认值必须是选项 ID");
            }
            return;
        }
        if (type != FieldType.JSON && !ConfigTypes.NUMERIC_FIELDS.contains(type) && type != FieldType.SWITCH
                && !value.isTextual()) {
            throw ConfigErrors.invalid(type + " 字段的固定默认值必须是文本");
        }
    }

    public String component(ComponentType type, String fieldId, JsonNode properties) {
        requireObject(properties, "properties");
        validateJson(properties, "properties", PROPERTY_MAX_BYTES, JSON_MAX_DEPTH);
        rejectUnknown(properties, COMPONENT_KEYS, "properties");
        if (type == ComponentType.FIELD && (fieldId == null || fieldId.isBlank())) {
            throw ConfigErrors.invalid("FIELD 组件必须关联 fieldId");
        }
        if (type != ComponentType.FIELD && fieldId != null && !fieldId.isBlank()) {
            throw ConfigErrors.invalid("只有 FIELD 组件可以关联 fieldId");
        }
        validateTextLength(properties, "content", 10_000);
        validateIntegerRange(properties, "columns", 1, 24);
        validateTextLength(properties, "label", 256);
        validateTextLength(properties, "title", 256);
        validateTextLength(properties, "description", 2000);
        validateTextLength(properties, "actionId", 32);
        validateBooleans(properties, "visible", "collapsible", "collapsed");
        return write(properties);
    }

    public String layout(PageType pageType, JsonNode layout) {
        requireObject(layout, "layout");
        validateJson(layout, "layout", LAYOUT_MAX_BYTES, LAYOUT_MAX_DEPTH);
        rejectUnknown(layout, LAYOUT_KEYS, "layout");
        validateIntegerRange(layout, "columns", 1, 24);
        validateIntegerRange(layout, "gap", 0, 64);
        validateIntegerRange(layout, "pageSize", 1, 500);
        validateBooleans(layout, "stickyActions", "showSearch", "showFilters");
        validateEnum(layout, "labelPosition", Set.of("TOP", "LEFT"));
        validateEnum(layout, "density", Set.of("DEFAULT", "COMPACT"));
        return write(filterScenarios.canonicalize(pageType, layout));
    }

    boolean layoutReferencesField(String layoutJson, String fieldCode) {
        return filterScenarios.referencesField(read(layoutJson), fieldCode);
    }

    public String action(JsonNode properties) {
        requireObject(properties, "properties");
        validateJson(properties, "properties", PROPERTY_MAX_BYTES, JSON_MAX_DEPTH);
        rejectUnknown(properties, ACTION_KEYS, "properties");
        validateEnum(properties, "style", Set.of("PRIMARY", "DEFAULT", "DANGER", "LINK"));
        validateEnum(properties, "openMode", Set.of("CURRENT", "DRAWER", "MODAL", "NEW_TAB"));
        validateTextLength(properties, "iconKey", 64);
        validateTextLength(properties, "successMessage", 500);
        validateTextLength(properties, "approvalFlowId", 32);
        validateTextLength(properties, "targetPageId", 32);
        validateTextLength(properties, "disabledReason", 500);
        validateBooleans(properties, "danger");
        return write(properties);
    }

    public RuleJson rule(ConfigRequests.Condition condition, java.util.List<ConfigRequests.Effect> effects) {
        validateCondition(condition, 1);
        for (var effect : effects) {
            if (effect == null || effect.effect() == null) {
                throw ConfigErrors.invalid("effects 包含无效项");
            }
            if (effect.value() == null || !effect.value().isBoolean()) {
                throw ConfigErrors.invalid("规则效果 value 必须是 boolean");
            }
        }
        var conditionJson = write(condition);
        var effectJson = write(effects);
        if (bytes(conditionJson) > PROPERTY_MAX_BYTES || bytes(effectJson) > PROPERTY_MAX_BYTES) {
            throw ConfigErrors.invalid("规则配置超过 64 KiB 限制");
        }
        return new RuleJson(conditionJson, effectJson);
    }

    public JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored configuration JSON is invalid", exception);
        }
    }

    public <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored configuration JSON is invalid", exception);
        }
    }

    public ConfigRequests.Condition readCondition(String json) {
        return read(json, ConfigRequests.Condition.class);
    }

    public java.util.List<ConfigRequests.Effect> readEffects(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored rule effect JSON is invalid", exception);
        }
    }

    private void validateCondition(ConfigRequests.Condition condition, int depth) {
        if (condition == null || depth > RULE_MAX_DEPTH) {
            throw ConfigErrors.invalid("规则条件最大深度为 " + RULE_MAX_DEPTH);
        }
        var children = condition.children() == null ? java.util.List.<ConfigRequests.Condition>of() : condition.children();
        if (condition.join() != null) {
            if (condition.fieldId() != null || condition.operator() != null || children.isEmpty()) {
                throw ConfigErrors.invalid("规则逻辑节点必须只包含 join 和非空 children");
            }
            if (children.size() > 50) {
                throw ConfigErrors.invalid("单个规则逻辑节点最多包含 50 个条件");
            }
            children.forEach(child -> validateCondition(child, depth + 1));
            return;
        }
        if (!children.isEmpty() || condition.fieldId() == null || condition.fieldId().isBlank()
                || condition.operator() == null) {
            throw ConfigErrors.invalid("规则叶子必须包含 fieldId 和 operator");
        }
        var requiresNoValue = condition.operator() == ConditionOperator.EMPTY
                || condition.operator() == ConditionOperator.NOT_EMPTY;
        if (requiresNoValue && condition.value() != null && !condition.value().isNull()) {
            throw ConfigErrors.invalid(condition.operator() + " 条件不能包含 value");
        }
        if (!requiresNoValue && (condition.value() == null || condition.value().isNull())) {
            throw ConfigErrors.invalid(condition.operator() + " 条件必须包含 value");
        }
        if ((condition.operator() == ConditionOperator.IN || condition.operator() == ConditionOperator.NOT_IN)
                && (!condition.value().isArray() || condition.value().isEmpty())) {
            throw ConfigErrors.invalid(condition.operator() + " 条件 value 必须是非空数组");
        }
        if (condition.operator() == ConditionOperator.BETWEEN
                && (!condition.value().isArray() || condition.value().size() != 2)) {
            throw ConfigErrors.invalid("BETWEEN 条件 value 必须包含两个边界值");
        }
        if (condition.value() != null) {
            validateJson(condition.value(), "condition.value", PROPERTY_MAX_BYTES, JSON_MAX_DEPTH);
        }
    }

    private void validateJson(JsonNode node, String path, int maxBytes, int maxDepth) {
        if (node == null || node.isMissingNode()) {
            throw ConfigErrors.invalid(path + " 不能为空");
        }
        var json = write(node);
        if (bytes(json) > maxBytes) {
            throw ConfigErrors.invalid(path + " 超过大小限制");
        }
        walk(node, path, 1, maxDepth);
    }

    private void walk(JsonNode node, String path, int depth, int maxDepth) {
        if (depth > maxDepth) {
            throw ConfigErrors.invalid(path + " 超过最大 JSON 深度 " + maxDepth);
        }
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                var key = entry.getKey();
                if (FORBIDDEN_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                    throw ConfigErrors.invalid(path + "." + key + " 不允许脚本或 SQL 配置");
                }
                walk(entry.getValue(), path + "." + key, depth + 1, maxDepth);
            });
        } else if (node.isArray()) {
            if (node.size() > 1000) {
                throw ConfigErrors.invalid(path + " 数组元素过多");
            }
            for (var child : node) {
                walk(child, path, depth + 1, maxDepth);
            }
        } else if (node.isTextual() && node.textValue().length() > 10_000) {
            throw ConfigErrors.invalid(path + " 文本过长");
        }
    }

    private void rejectUnknown(JsonNode object, Set<String> allowed, String path) {
        object.fieldNames().forEachRemaining(key -> {
            if (!allowed.contains(key)) {
                throw ConfigErrors.invalid(path + "." + key + " 不是支持的结构化属性");
            }
        });
    }

    private void requireObject(JsonNode value, String path) {
        if (value == null || !value.isObject()) {
            throw ConfigErrors.invalid(path + " 必须是 JSON object");
        }
    }

    private void validateIntegerRange(JsonNode object, String key, int min, int max) {
        var value = object.get(key);
        if (value != null && (!value.canConvertToInt() || value.intValue() < min || value.intValue() > max)) {
            throw ConfigErrors.invalid(key + " 必须在 " + min + " 到 " + max + " 之间");
        }
    }

    private void validateTextLength(JsonNode object, String key, int max) {
        var value = object.get(key);
        if (value != null && (!value.isTextual() || value.textValue().length() > max)) {
            throw ConfigErrors.invalid(key + " 必须是长度不超过 " + max + " 的文本");
        }
    }

    private void validateBooleans(JsonNode object, String... keys) {
        for (var key : keys) {
            var value = object.get(key);
            if (value != null && !value.isBoolean()) {
                throw ConfigErrors.invalid("properties." + key + " 必须是 boolean");
            }
        }
    }

    private void validateEnum(JsonNode object, String key, Set<String> allowed) {
        var value = object.get(key);
        if (value != null && (!value.isTextual() || !allowed.contains(value.textValue()))) {
            throw ConfigErrors.invalid("properties." + key + " 不是支持的选项");
        }
    }

    private void validateP4C1Properties(FieldType type, JsonNode properties) {
        if (type == FieldType.MONEY) {
            var currencies = new HashSet<String>();
            if (properties.path("currencies").isArray()) {
                for (var item : properties.path("currencies")) {
                    currencies.add(validCurrency(item.asText(), "properties.currencies"));
                }
                if (currencies.size() != properties.path("currencies").size()) {
                    throw ConfigErrors.invalid("properties.currencies must contain unique ISO-4217 codes");
                }
            }
            String fixed = null;
            for (var key : Set.of("currency", "fixedCurrency")) {
                if (properties.path(key).isTextual()) {
                    var candidate = validCurrency(properties.path(key).textValue(), "properties." + key);
                    if (fixed != null && !fixed.equals(candidate)) {
                        throw ConfigErrors.invalid("properties.currency and fixedCurrency must agree");
                    }
                    fixed = candidate;
                    currencies.add(candidate);
                }
            }
            if (currencies.isEmpty()) {
                throw ConfigErrors.invalid("MONEY properties must declare currency, fixedCurrency or currencies");
            }
        }
        var scale = integer(properties, "scale");
        if (type == FieldType.PERCENT && scale != null && scale > 4) {
            throw ConfigErrors.invalid("PERCENT properties.scale must be within 0..4");
        }
        if (type == FieldType.PROGRESS && scale != null && scale > 2) {
            throw ConfigErrors.invalid("PROGRESS properties.scale must be within 0..2");
        }
        var maxSelections = integer(properties, "maxSelections");
        if (type == FieldType.MULTI_SELECT && maxSelections != null && maxSelections > 100) {
            throw ConfigErrors.invalid("MULTI_SELECT properties.maxSelections must not exceed 100");
        }
        var maxRating = integer(properties, "maxRating");
        if (type == FieldType.RATING && maxRating != null && maxRating != 5) {
            throw ConfigErrors.invalid("RATING properties.maxRating is fixed at 5");
        }
    }

    private void validateP4C2Properties(FieldType type, JsonNode properties) {
        if (type == FieldType.PHONE && properties.has("defaultCountry")) {
            var country = properties.path("defaultCountry").asText("").toUpperCase(Locale.ROOT);
            if (!Set.of(Locale.getISOCountries()).contains(country)) {
                throw ConfigErrors.invalid("PHONE properties.defaultCountry must be an ISO alpha-2 country code");
            }
        }
        if (type == FieldType.IDENTITY) {
            var kind = properties.path("identityKind").asText("");
            if (!Set.of("CN_RESIDENT_ID", "GENERIC").contains(kind)) {
                throw ConfigErrors.invalid("IDENTITY properties.identityKind is required");
            }
            if ("GENERIC".equals(kind)) {
                var expression = properties.path("pattern").asText("");
                if (!safeAnchoredPattern(expression)) {
                    throw ConfigErrors.invalid("GENERIC IDENTITY requires a bounded anchored pattern");
                }
            }
        }
        if (type == FieldType.GEO
                && !"WGS84".equals(properties.path("coordinateSystem").asText("WGS84"))) {
            throw ConfigErrors.invalid("GEO coordinateSystem is fixed at WGS84");
        }
        if (type == FieldType.BARCODE) {
            var values = stringSet(properties.path("symbologies"), "BARCODE properties.symbologies", 2);
            if (values.isEmpty() || !Set.of("CODE128", "EAN13").containsAll(values)) {
                throw ConfigErrors.invalid("BARCODE must enable CODE128 and/or EAN13");
            }
        }
        if (type == FieldType.RICH_TEXT && properties.has("sanitize")
                && !properties.path("sanitize").asBoolean()) {
            throw ConfigErrors.invalid("RICH_TEXT server sanitization cannot be disabled");
        }
        if (type == FieldType.JSON) {
            validateJsonSchemaSubset(properties.path("jsonSchema"), "properties.jsonSchema", 1);
            validateQueryPaths(properties.path("queryPaths"), properties.path("jsonSchema"));
        }
        if (type == FieldType.SECRET) {
            var minimum = integer(properties, "minLength");
            var maximum = integer(properties, "maxLength");
            if (minimum != null && minimum != 1 || maximum != null && maximum != 4096
                    || properties.path("trim").asBoolean(false) || properties.path("sanitize").asBoolean(false)
                    || properties.has("pattern")) {
                throw ConfigErrors.invalid("SECRET canonical value is fixed at exact 1..4096 characters");
            }
        }
        if (type == FieldType.STATUS) {
            var initial = numericIdSet(properties.path("initialStateIds"), "STATUS initialStateIds", 100);
            if (initial.isEmpty()) {
                throw ConfigErrors.invalid("STATUS requires at least one initial state");
            }
            var transitions = properties.path("transitions");
            if (!transitions.isArray() || transitions.isEmpty() || transitions.size() > 1000) {
                throw ConfigErrors.invalid("STATUS requires 1..1000 transitions");
            }
            var edges = new HashSet<String>();
            for (var transition : transitions) {
                if (!transition.isObject() || transition.size() != 2) {
                    throw ConfigErrors.invalid("STATUS transitions contain only from and to ids");
                }
                var from = numericId(transition.path("from"));
                var to = numericId(transition.path("to"));
                if (from == null || to == null || from.equals(to) || !edges.add(from + "->" + to)) {
                    throw ConfigErrors.invalid("STATUS contains an invalid or duplicate transition");
                }
            }
        }
    }

    private void validateP4C3Properties(FieldType type, JsonNode properties) {
        if (type == FieldType.RELATION) {
            rejectKeys(properties, Set.of("defaultMode", "defaultValue", "unique", "cascadeDelete",
                    "valueFieldId", "sourceFieldId", "aggregation"), "RELATION");
        }
        if (type == FieldType.REFERENCE) {
            rejectKeys(properties, Set.of("defaultMode", "defaultValue", "unique", "multiple", "allowCreate",
                    "reverseRelation", "displayFieldId", "valueFieldId", "filter", "aggregation"), "REFERENCE");
            requireResourceId(properties, "sourceFieldId", "REFERENCE");
            requireResourceId(properties, "targetFieldId", "REFERENCE");
        }
        if (type != FieldType.SUBTABLE) {
            return;
        }
        rejectKeys(properties, Set.of("defaultMode", "defaultValue", "unique", "allowImport", "allowExport",
                "cascadeDelete", "aggregation"), "SUBTABLE");
        var columns = stringSet(properties.path("columnFieldIds"), "SUBTABLE properties.columnFieldIds", 200);
        if (columns.isEmpty()) {
            throw ConfigErrors.invalid("SUBTABLE requires at least one unique columnFieldId");
        }
        var minimum = integer(properties, "minRows");
        var maximum = integer(properties, "maxRows");
        if (minimum == null || maximum == null) {
            throw ConfigErrors.invalid("SUBTABLE requires minRows and maxRows within 0..200");
        }
        var aggregates = properties.path("aggregates");
        if (!aggregates.isMissingNode() && !aggregates.isNull()) {
            if (!aggregates.isArray() || aggregates.size() > 20) {
                throw ConfigErrors.invalid("SUBTABLE properties.aggregates must contain at most 20 declarations");
            }
            var ids = new HashSet<String>();
            for (var aggregate : aggregates) {
                var aggregateKeys = new HashSet<String>();
                aggregate.fieldNames().forEachRemaining(aggregateKeys::add);
                if (!aggregate.isObject() || !Set.of("id", "function", "columnFieldId")
                        .containsAll(aggregateKeys)) {
                    throw ConfigErrors.invalid("SUBTABLE aggregate contains unknown properties");
                }
                var aggregateId = aggregate.path("id").asText("");
                var function = aggregate.path("function").asText("");
                if (!aggregateId.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$") || !ids.add(aggregateId)
                        || !Set.of("COUNT", "SUM", "MIN", "MAX", "AVG").contains(function)) {
                    throw ConfigErrors.invalid("SUBTABLE contains an invalid or duplicate aggregate declaration");
                }
                var columnId = aggregate.path("columnFieldId").asText("");
                if ("COUNT".equals(function)) {
                    if (!columnId.isEmpty()) {
                        throw ConfigErrors.invalid("SUBTABLE COUNT aggregate does not accept columnFieldId");
                    }
                } else if (!columns.contains(columnId)) {
                    throw ConfigErrors.invalid("SUBTABLE aggregate columnFieldId must be a selected column");
                }
            }
        }
    }

    private void validateP4C4Properties(FieldType type, JsonNode properties) {
        if (!P4C4_DERIVED_FIELDS.contains(type)) {
            return;
        }
        var resultSchema = properties.path("resultSchema");
        if (!resultSchema.isTextual() || !Set.of(
                "STRING", "DECIMAL", "INTEGER", "DATE", "DATETIME", "BOOLEAN"
        ).contains(resultSchema.asText())) {
            throw ConfigErrors.invalid(type + " requires a supported properties.resultSchema");
        }
        if (type == FieldType.AI_FILL) {
            var sources = stringSet(
                    properties.path("sourceFieldIds"),
                    "AI_FILL properties.sourceFieldIds", 16);
            if (sources.isEmpty() || sources.stream()
                    .anyMatch(value -> !value.matches("^[1-9][0-9]{0,18}$"))) {
                throw ConfigErrors.invalid(
                        "AI_FILL requires 1..16 unique positive sourceFieldIds");
            }
            var prompt = properties.path("promptTemplate");
            if (!prompt.isTextual() || prompt.textValue().isBlank()
                    || prompt.textValue().length() > 4000) {
                throw ConfigErrors.invalid(
                        "AI_FILL requires a promptTemplate within 1..4000 characters");
            }
            if (!"SYSTEM_DEFAULT".equals(properties.path("modelPolicy").asText())) {
                throw ConfigErrors.invalid(
                        "AI_FILL modelPolicy must be SYSTEM_DEFAULT");
            }
            var minimum = properties.path("minConfidence");
            if (!minimum.isNumber()
                    || minimum.decimalValue().compareTo(new BigDecimal("0.50")) < 0
                    || minimum.decimalValue().compareTo(BigDecimal.ONE) > 0) {
                throw ConfigErrors.invalid(
                        "AI_FILL minConfidence must be within 0.50..1.00");
            }
            if (!Set.of("NEVER", "CONFIRM")
                    .contains(properties.path("overwriteMode").asText())) {
                throw ConfigErrors.invalid(
                        "AI_FILL overwriteMode must be NEVER or CONFIRM");
            }
            return;
        }
        if (type == FieldType.FORMULA || type == FieldType.CALCULATED) {
            if (!properties.path("astVersion").canConvertToInt()
                    || properties.path("astVersion").intValue() != 1) {
                throw ConfigErrors.invalid(type + " requires properties.astVersion=1");
            }
            validateDerivedAst(properties.path("expressionAst"), "properties.expressionAst", 1, new int[]{0});
            return;
        }
        if (type == FieldType.SUMMARY) {
            requireResourceId(properties, "relationFieldId", "SUMMARY");
            var reduction = properties.path("reduction").asText("");
            if (!Set.of("COUNT", "SUM", "MIN", "MAX", "AVG").contains(reduction)) {
                throw ConfigErrors.invalid("SUMMARY requires a supported properties.reduction");
            }
            if ("COUNT".equals(reduction)) {
                if (properties.has("targetFieldId")) {
                    throw ConfigErrors.invalid("SUMMARY COUNT does not accept properties.targetFieldId");
                }
            } else {
                requireResourceId(properties, "targetFieldId", "SUMMARY");
            }
            return;
        }
        if (type == FieldType.LOOKUP) {
            requireResourceId(properties, "relationFieldId", "LOOKUP");
            requireResourceId(properties, "targetFieldId", "LOOKUP");
            return;
        }
        requireResourceId(properties, "subtableFieldId", "AGGREGATE");
        var aggregateId = properties.path("aggregateId");
        if (!aggregateId.isTextual()
                || !aggregateId.asText().matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")
                || !"DECIMAL".equals(resultSchema.asText())) {
            throw ConfigErrors.invalid("AGGREGATE requires a declared aggregateId and DECIMAL resultSchema");
        }
    }

    private void validateDerivedAst(JsonNode node, String path, int depth, int[] count) {
        if (!node.isObject() || depth > 8 || ++count[0] > 64) {
            throw ConfigErrors.invalid(path + " must be a bounded structured AST");
        }
        var keys = new HashSet<String>();
        node.fieldNames().forEachRemaining(keys::add);
        if (node.has("fieldId")) {
            if (!keys.equals(Set.of("fieldId")) || numericId(node.path("fieldId")) == null) {
                throw ConfigErrors.invalid(path + " contains an invalid field reference");
            }
            return;
        }
        if (node.has("literalType")) {
            if (!keys.equals(Set.of("literalType", "value"))) {
                throw ConfigErrors.invalid(path + " contains an invalid typed literal");
            }
            var literalType = node.path("literalType").asText("");
            var value = node.get("value");
            var valid = switch (literalType) {
                case "STRING", "DATE", "DATETIME" -> value != null && value.isTextual();
                case "DECIMAL" -> value != null && value.isNumber();
                case "INTEGER" -> value != null && value.isIntegralNumber();
                case "BOOLEAN" -> value != null && value.isBoolean();
                default -> false;
            };
            if (!valid) {
                throw ConfigErrors.invalid(path + " contains a mismatched typed literal");
            }
            return;
        }
        if (!keys.equals(Set.of("op", "args")) || !node.path("op").isTextual()
                || !node.path("args").isArray()) {
            throw ConfigErrors.invalid(path + " contains an unknown AST node");
        }
        var operation = node.path("op").asText();
        var arguments = node.path("args");
        var arityValid = switch (operation) {
            case "NOT" -> arguments.size() == 1;
            case "IF" -> arguments.size() == 3;
            case "ADD", "SUBTRACT", "MULTIPLY", "DIVIDE", "CONCAT", "EQ", "NE", "GT", "GTE",
                    "LT", "LTE", "AND", "OR", "ADD_DAYS", "DAYS_BETWEEN" -> arguments.size() == 2;
            default -> false;
        };
        if (!arityValid) {
            throw ConfigErrors.invalid(path + " contains an unsupported operator or arity");
        }
        for (var index = 0; index < arguments.size(); index++) {
            validateDerivedAst(arguments.get(index), path + ".args[" + index + "]", depth + 1, count);
        }
    }

    private static void rejectKeys(JsonNode properties, Set<String> keys, String fieldType) {
        for (var key : keys) {
            if (properties.has(key)) {
                throw ConfigErrors.invalid(fieldType + " does not support properties." + key);
            }
        }
    }

    private static void requireResourceId(JsonNode properties, String key, String fieldType) {
        if (!properties.path(key).isTextual()
                || !properties.path(key).textValue().matches("^[1-9][0-9]{0,18}$")) {
            throw ConfigErrors.invalid(fieldType + " requires properties." + key);
        }
    }

    private static boolean safeAnchoredPattern(String expression) {
        if (expression == null || expression.length() < 3 || expression.length() > 256
                || !expression.startsWith("^") || !expression.endsWith("$")
                || expression.contains("(?") || expression.matches(".*\\\\[1-9].*")
                || expression.contains(".*") || expression.matches(".*[+*}]\\s*[+*{].*")) {
            return false;
        }
        try {
            Pattern.compile(expression);
            return true;
        } catch (PatternSyntaxException exception) {
            return false;
        }
    }

    private void validateJsonSchemaSubset(JsonNode schema, String path, int depth) {
        if (schema == null || schema.isMissingNode() || schema.isNull() || schema.isEmpty()) {
            return;
        }
        if (!schema.isObject() || depth > 10) {
            throw ConfigErrors.invalid(path + " must be a bounded JSON Schema object");
        }
        var allowed = Set.of("type", "required", "properties", "additionalProperties", "enum",
                "minLength", "maxLength", "minimum", "maximum");
        schema.fieldNames().forEachRemaining(key -> {
            if (!allowed.contains(key)) {
                throw ConfigErrors.invalid(path + "." + key + " is not supported in P4-C2");
            }
        });
        if (schema.has("type") && (!schema.path("type").isTextual()
                || !Set.of("object", "array", "string", "number", "integer", "boolean", "null")
                .contains(schema.path("type").asText()))) {
            throw ConfigErrors.invalid(path + ".type is not supported");
        }
        if (schema.has("required")) {
            stringSet(schema.path("required"), path + ".required", 100);
        }
        if (schema.has("additionalProperties") && !schema.path("additionalProperties").isBoolean()) {
            throw ConfigErrors.invalid(path + ".additionalProperties must be boolean");
        }
        if (schema.path("properties").isObject()) {
            schema.path("properties").properties().forEach(entry ->
                    validateJsonSchemaSubset(entry.getValue(), path + ".properties." + entry.getKey(), depth + 1));
        } else if (schema.has("properties")) {
            throw ConfigErrors.invalid(path + ".properties must be an object");
        }
        if (schema.has("enum") && (!schema.path("enum").isArray() || schema.path("enum").isEmpty()
                || schema.path("enum").size() > 100)) {
            throw ConfigErrors.invalid(path + ".enum must contain 1..100 values");
        }
    }

    private void validateQueryPaths(JsonNode paths, JsonNode schema) {
        if (paths == null || paths.isMissingNode() || paths.isNull()) {
            return;
        }
        if (!paths.isArray() || paths.size() > 32) {
            throw ConfigErrors.invalid("JSON queryPaths must contain no more than 32 declarations");
        }
        var ids = new HashSet<String>();
        var values = new HashSet<String>();
        for (var declaration : paths) {
            if (!declaration.isObject() || declaration.size() != 3) {
                throw ConfigErrors.invalid("Each JSON query path requires pathSnapshotId, path and type");
            }
            var id = numericId(declaration.path("pathSnapshotId"));
            var path = declaration.path("path").asText("");
            var type = declaration.path("type").asText("");
            if (id == null || !ids.add(id) || !values.add(path)
                    || !path.matches("^\\$\\.[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$")
                    || !Set.of("STRING", "DECIMAL", "INTEGER", "BOOLEAN", "DATE", "DATETIME").contains(type)) {
                throw ConfigErrors.invalid("JSON contains an invalid or duplicate declared query path");
            }
            var pathSchema = declaredPathSchema(schema, path);
            var expectedSchemaType = switch (type) {
                case "STRING", "DATE", "DATETIME" -> "string";
                case "DECIMAL" -> "number";
                case "INTEGER" -> "integer";
                case "BOOLEAN" -> "boolean";
                default -> "";
            };
            if (pathSchema == null || !expectedSchemaType.equals(pathSchema.path("type").asText())) {
                throw ConfigErrors.invalid("JSON query path " + path + " does not match jsonSchema type " + type);
            }
            if ("STRING".equals(type)
                    && (!pathSchema.path("maxLength").canConvertToInt()
                    || pathSchema.path("maxLength").intValue() < 1
                    || pathSchema.path("maxLength").intValue() > 512)) {
                throw ConfigErrors.invalid("Declared STRING query paths require maxLength within 1..512");
            }
        }
    }

    private static JsonNode declaredPathSchema(JsonNode root, String path) {
        if (root == null || !root.isObject() || path == null || path.length() < 3) {
            return null;
        }
        JsonNode current = root;
        for (var segment : path.substring(2).split("\\.")) {
            if (!"object".equals(current.path("type").asText())
                    || !current.path("properties").isObject()
                    || !current.path("properties").has(segment)) {
                return null;
            }
            current = current.path("properties").path(segment);
        }
        return current;
    }

    private static Set<String> stringSet(JsonNode values, String path, int maximum) {
        if (!values.isArray() || values.size() > maximum) {
            throw ConfigErrors.invalid(path + " must be a bounded string array");
        }
        var result = new HashSet<String>();
        for (var value : values) {
            if (!value.isTextual() || value.asText().isBlank() || !result.add(value.asText())) {
                throw ConfigErrors.invalid(path + " contains an invalid or duplicate string");
            }
        }
        return result;
    }

    private static Set<String> numericIdSet(JsonNode values, String path, int maximum) {
        if (!values.isArray() || values.size() > maximum) {
            throw ConfigErrors.invalid(path + " must be a bounded id array");
        }
        var result = new HashSet<String>();
        for (var value : values) {
            var id = numericId(value);
            if (id == null || !result.add(id)) {
                throw ConfigErrors.invalid(path + " contains an invalid or duplicate id");
            }
        }
        return result;
    }

    private static String numericId(JsonNode value) {
        if (value.isIntegralNumber() && value.canConvertToLong() && value.longValue() > 0) {
            return Long.toString(value.longValue());
        }
        return value.isTextual() && value.asText().matches("^[1-9][0-9]{0,18}$") ? value.asText() : null;
    }

    private String validCurrency(String value, String path) {
        if (value == null || !value.matches("^[A-Z]{3}$")) {
            throw ConfigErrors.invalid(path + " must contain uppercase ISO-4217 codes");
        }
        try {
            var currency = Currency.getInstance(value);
            if (currency.getDefaultFractionDigits() < 0) {
                throw ConfigErrors.invalid(path + " contains an unsupported currency code");
            }
            return currency.getCurrencyCode();
        } catch (IllegalArgumentException exception) {
            throw ConfigErrors.invalid(path + " contains an invalid ISO-4217 code");
        }
    }

    private void validateCurrency(JsonNode object) {
        var value = object.get("currency");
        if (value != null && (!value.isTextual() || !value.textValue().matches("^[A-Z]{3}$"))) {
            throw ConfigErrors.invalid("properties.currency 必须是三位大写币种编码");
        }
    }
    private void validateStringArray(JsonNode object, String key, int maxItems, int maxTextLength) {
        var value = object.get(key);
        if (value == null) {
            return;
        }
        if (!value.isArray() || value.size() > maxItems) {
            throw ConfigErrors.invalid("properties." + key + " 必须是受限字符串数组");
        }
        for (var item : value) {
            if (!item.isTextual() || item.textValue().isBlank() || item.textValue().length() > maxTextLength) {
                throw ConfigErrors.invalid("properties." + key + " 包含无效字符串");
            }
        }
    }

    private void validateObjectValue(JsonNode object, String key) {
        var value = object.get(key);
        if (value != null && !value.isObject()) {
            throw ConfigErrors.invalid("properties." + key + " 必须是 JSON object");
        }
    }

    private void validatePositiveNumber(JsonNode object, String key) {
        var value = object.get(key);
        if (value != null && (!value.isNumber() || value.decimalValue().signum() <= 0)) {
            throw ConfigErrors.invalid("properties." + key + " 必须是正数");
        }
    }

    private void validateDeclarativeExpression(JsonNode object, String key) {
        var value = object.get(key);
        if (value == null) {
            return;
        }
        var expression = value.textValue().toLowerCase(Locale.ROOT);
        if (expression.contains(";") || expression.contains("{") || expression.contains("}")
                || expression.contains("\\") || expression.contains("javascript") || expression.contains("groovy")
                || expression.contains("spel") || expression.contains("java.") || expression.contains("system.")
                || expression.contains("runtime") || expression.contains("exec(")) {
            throw ConfigErrors.invalid("properties." + key + " 只能包含声明式表达式");
        }
    }
    private void validateNumberOrder(JsonNode object, String minKey, String maxKey) {
        var min = decimal(object.get(minKey));
        var max = decimal(object.get(maxKey));
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw ConfigErrors.invalid(minKey + " 不能大于 " + maxKey);
        }
    }

    private BigDecimal decimal(JsonNode value) {
        if (value == null) {
            return null;
        }
        if (!value.isNumber()) {
            throw ConfigErrors.invalid("数值范围属性必须是 number");
        }
        return value.decimalValue();
    }

    private Integer integer(JsonNode object, String key) {
        var value = object.get(key);
        return value == null ? null : value.intValue();
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw ConfigErrors.invalid("配置无法序列化为 JSON");
        }
    }

    private int bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    public record RuleJson(String conditionJson, String effectJson) { }
}
