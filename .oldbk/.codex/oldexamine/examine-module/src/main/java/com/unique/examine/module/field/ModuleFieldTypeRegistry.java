package com.unique.examine.module.field;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 字段类型元数据（供前端拉取枚举定义）。
 */
public final class ModuleFieldTypeRegistry {

    private ModuleFieldTypeRegistry() {}

    public static List<Map<String, Object>> listDefinitions() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ModuleFieldType t : ModuleFieldType.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", t.getCode());
            m.put("label", t.getLabel());
            m.put("needsDict", t.needsDict());
            m.put("needsRef", t.isRefType());
            m.put("allowsMulti", t.allowsMultiFlag());
            m.put("displayOnly", t.isDisplayOnly());
            m.put("configKeys", configKeysFor(t));
            out.add(m);
        }
        return out;
    }

    private static List<String> configKeysFor(ModuleFieldType t) {
        return switch (t) {
            case TEXT -> List.of("inputStyle", "maxLength");
            case MONEY, PERCENT -> List.of("decimalPlaces");
            case DATETIME -> List.of("pickerMode", "min", "max");
            case SELECT, MULTI_SELECT -> List.of("displayStyle");
            case ADDRESS -> List.of("regionStyle", "includeLocation", "detailMode", "mapPicker");
            case PERSON, DEPARTMENT -> List.of("scope", "multi");
            case TITLE -> List.of("content");
            case SERIAL_NO -> List.of("segments");
            case REF_MODULE -> List.of("displayStyle", "listFields", "multi", "subTable");
            // relation_module_label / ref_model_id / ref_display_field 为表字段，非 config_json
            case TAG -> List.of("tags", "allowCustom");
            case DATE_RANGE -> List.of("pickerMode");
            default -> List.of();
        };
    }
}
