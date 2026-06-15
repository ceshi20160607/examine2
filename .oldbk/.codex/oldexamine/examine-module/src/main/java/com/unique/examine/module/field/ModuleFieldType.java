package com.unique.examine.module.field;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 模块字段类型枚举（与 un_module_field.field_type 存储值一致，大写蛇形）。
 */
public enum ModuleFieldType {

    /** 单行文本；子样式见 validateType / config.inputStyle */
    TEXT("单行文本"),
    /** 多行文本 */
    TEXTAREA("多行文本"),
    /** 整数/小数数字 */
    NUMBER("数字"),
    /** 金额 */
    MONEY("金额"),
    /** 百分比 */
    PERCENT("百分比"),
    /** 日期时间（存储 datetime；格式见 dateFormat + config） */
    DATETIME("日期时间"),
    /** 布尔 */
    BOOLEAN("布尔"),
    /** 单选（字典/选项 + 展示样式） */
    SELECT("单选"),
    /** 多选 */
    MULTI_SELECT("多选"),
    /** 地址 */
    ADDRESS("地址"),
    /** 人员 */
    PERSON("人员"),
    /** 部门 */
    DEPARTMENT("部门"),
    /** 占位标题（展示用） */
    TITLE("占位标题"),
    /** 手写签名（存 fileId） */
    SIGNATURE("手写签名"),
    /** 自定义编号 */
    SERIAL_NO("自定义编号"),
    /** 富文本 */
    RICH_TEXT("富文本"),
    /** 关联其它模块记录 */
    REF_MODULE("关联模块"),
    /** 标签（每模块至多一个） */
    TAG("标签"),
    /** 日期区间 */
    DATE_RANGE("日期区间"),
    /** 附件 */
    FILE("附件");

    private final String label;

    ModuleFieldType(String label) {
        this.label = label;
    }

    public String getCode() {
        return name();
    }

    public String getLabel() {
        return label;
    }

    private static final Map<String, ModuleFieldType> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(t -> t.name(), t -> t));

    private static final Set<String> LEGACY_REF = Set.of("ref", "relation", "lookup");
    private static final Set<String> LEGACY_FILE = Set.of("file", "upload", "attachment", "image");

    public static Optional<ModuleFieldType> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        ModuleFieldType direct = BY_CODE.get(u);
        if (direct != null) {
            return Optional.of(direct);
        }
        return Optional.ofNullable(normalizeLegacy(raw.trim().toLowerCase(Locale.ROOT)));
    }

    public static ModuleFieldType require(String raw) {
        return parse(raw).orElseThrow(() -> new IllegalArgumentException("未知字段类型: " + raw));
    }

    /** 将历史自由字符串映射为枚举（无法识别时返回 null） */
    public static ModuleFieldType normalizeLegacy(String lower) {
        if (lower == null || lower.isBlank()) {
            return null;
        }
        return switch (lower) {
            case "string", "text", "password", "email", "phone", "url", "idcard", "id_card", "color", "switch" -> TEXT;
            case "textarea" -> TEXTAREA;
            case "currency" -> MONEY;
            case "percent" -> PERCENT;
            case "number", "int", "integer", "long", "decimal", "double", "float", "rating", "slider" -> NUMBER;
            case "date", "datetime", "time", "year", "month" -> DATETIME;
            case "bool", "boolean" -> BOOLEAN;
            case "enum", "select", "dict", "radio" -> SELECT;
            case "multiselect", "multi_select", "checkbox" -> MULTI_SELECT;
            case "address", "region" -> ADDRESS;
            case "person", "user" -> PERSON;
            case "dept", "department" -> DEPARTMENT;
            case "title", "heading" -> TITLE;
            case "signature", "sign" -> SIGNATURE;
            case "serial", "serial_no", "auto_no" -> SERIAL_NO;
            case "rich_text", "richtext", "html" -> RICH_TEXT;
            case "ref", "relation", "lookup", "ref_multi" -> REF_MODULE;
            case "tag", "tags" -> TAG;
            case "date_range", "daterange" -> DATE_RANGE;
            case "file", "upload", "attachment", "image" -> FILE;
            case "json" -> TEXTAREA;
            default -> {
                if (LEGACY_REF.contains(lower)) yield REF_MODULE;
                if (LEGACY_FILE.contains(lower)) yield FILE;
                yield null;
            }
        };
    }

    public boolean isRefType() {
        return this == REF_MODULE;
    }

    public boolean isFileType() {
        return this == FILE || this == SIGNATURE;
    }

    public boolean needsDict() {
        return this == SELECT || this == MULTI_SELECT;
    }

    public boolean allowsMultiFlag() {
        return this == MULTI_SELECT || this == REF_MODULE || this == PERSON || this == DEPARTMENT || this == TAG;
    }

    public boolean isDisplayOnly() {
        return this == TITLE;
    }
}
