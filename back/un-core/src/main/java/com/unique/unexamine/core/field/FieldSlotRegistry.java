package com.unique.unexamine.core.field;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 动态字段的固定槽位规则。
 *
 * <p>槽位名称、数量和字段类型的对应关系属于工程公共协议，不依赖具体业务模块。
 * 业务模块只需要把协议中的列接到自己的记录实体，槽位耗尽后统一落到 field_* 扩展键。</p>
 */
public final class FieldSlotRegistry {
    private static final Map<String, Slot> SLOTS = new LinkedHashMap<>();

    static {
        register("item_number", 15);
        register("item_text", 10);
        register("item_long_text", 2);
        register("item_time", 10);
        register("item_user", 5);
        register("item_dept", 5);
        register("item_module", 5);
        register("item_select", 10);
    }

    private FieldSlotRegistry() {
    }

    private static void register(String prefix, int count) {
        for (int index = 1; index <= count; index++) {
            String key = prefix + String.format("%02d", index);
            SLOTS.put(key, new Slot(key, prefix, index));
        }
    }

    /** 分配类型匹配的第一个未使用槽位，耗尽时生成稳定格式的扩展字段键。 */
    public static String allocate(FieldDefinition definition, Set<String> occupied) {
        String prefix = prefix(definition);
        if (prefix != null) {
            for (Slot slot : SLOTS.values()) {
                if (slot.prefix().equals(prefix) && !occupied.contains(slot.key())) return slot.key();
            }
        }
        return "field_" + UUID.randomUUID().toString().replace("-", "").toLowerCase();
    }

    /** 判断字段是否能直接落到当前固定主表槽位。 */
    public static boolean isMain(FieldDefinition definition) {
        if (definition == null) return false;
        Slot slot = SLOTS.get(definition.code());
        String prefix = prefix(definition);
        return slot != null && prefix != null && slot.prefix().equals(prefix);
    }

    /** 判断字段键是否为公共固定槽位列。 */
    public static boolean isReservedKey(String fieldKey) {
        return SLOTS.containsKey(fieldKey);
    }

    /** 固定列名来自白名单，可安全用于动态查询或更新。 */
    public static String column(String fieldKey) {
        if (!isReservedKey(fieldKey)) throw new IllegalArgumentException("字段不是固定主表槽位");
        return fieldKey;
    }

    /** 将已校验的业务值转换为固定列对应的 JDBC 值。 */
    public static Object physicalValue(FieldDefinition definition, Object value) {
        if (value == null) return null;
        return switch (definition.type()) {
            case NUMBER -> new BigDecimal(value.toString());
            case DATE_TIME -> toDateTime(value);
            case USER, DEPARTMENT, RELATION -> new BigDecimal(value.toString()).longValueExact();
            case SELECT, TEXT, RICH_TEXT, ATTACHMENT -> value.toString();
            default -> value;
        };
    }

    private static String prefix(FieldDefinition definition) {
        if (Boolean.TRUE.equals(definition.rules().get("multiple"))) return null;
        return switch (definition.type()) {
            case NUMBER -> "item_number";
            case TEXT -> longText(definition) ? "item_long_text" : "item_text";
            case RICH_TEXT -> "item_long_text";
            case DATE_TIME -> "item_time";
            case USER -> "item_user";
            case DEPARTMENT -> "item_dept";
            case RELATION -> "item_module";
            case SELECT -> "item_select";
            case ATTACHMENT -> "item_text";
            default -> null;
        };
    }

    private static boolean longText(FieldDefinition definition) {
        Object max = definition.rules().get("maxLength");
        return max instanceof Number number && number.intValue() > 255
                || "MULTILINE".equals(definition.rules().get("displayStyle"));
    }

    private static LocalDateTime toDateTime(Object value) {
        String text = value.toString();
        return text.length() == 10 ? LocalDate.parse(text).atStartOfDay() : LocalDateTime.parse(text);
    }

    private record Slot(String key, String prefix, int index) {
    }
}
