package com.unique.unexamine.core.field;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 共用校验输入；使用草稿校验配置，使用发布快照校验业务值，均不包含布局计算。 */
public record FieldDefinition(String code, String label, FieldType type, boolean required,
                              Map<String, Object> rules) {
    public FieldDefinition {
        if (code == null || code.isBlank() || type == null) {
            throw new IllegalArgumentException("字段定义缺少编码或类型");
        }
        label = label == null || label.isBlank() ? code : label;
        rules = rules == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(rules));
    }
}
