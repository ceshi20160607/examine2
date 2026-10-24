package com.unique.unexamine.core.field;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公共字段定义解析器。
 *
 * <p>接口配置、模块运行配置和字段转换配置都使用同一套解析规则；模块业务层只负责
 * 字典、关联目标和权限等业务边界，不再各自解释字段类型和规则结构。</p>
 */
public final class FieldDefinitionParser {
    private FieldDefinitionParser() {
    }

    /**
     * 从模块字段配置对象解析公共字段定义。
     * 配置结构固定使用 fieldName、fieldType、required、config 四个键，类型只能是新整数编号。
     */
    public static FieldDefinition parse(String code, Map<String, Object> configured) {
        if (configured == null) {
            throw new IllegalArgumentException("字段配置不能为空");
        }
        Object type = configured.get("fieldType");
        Object required = configured.get("required");
        if (required != null && !(required instanceof Boolean)) {
            throw new IllegalArgumentException("字段必填标记无效");
        }
        Object rawRules = configured.get("config");
        if (rawRules != null && !(rawRules instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("字段规则配置必须是对象");
        }
        Map<String, Object> rules = stringKeyMap(rawRules);
        String label = configured.get("fieldName") instanceof String value ? value : code;
        return parse(code, label, type, Boolean.TRUE.equals(required), rules);
    }

    /** 从已经拆开的标准属性解析字段定义，供不使用接口配置对象的内部业务调用。 */
    public static FieldDefinition parse(String code, String label, Object type, boolean required,
                                        Map<String, Object> rules) {
        return new FieldDefinition(code, label, FieldType.parse(type), required, rules);
    }

    private static Map<String, Object> stringKeyMap(Object raw) {
        if (!(raw instanceof Map<?, ?> source) || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                throw new IllegalArgumentException("字段规则键无效");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }
}
