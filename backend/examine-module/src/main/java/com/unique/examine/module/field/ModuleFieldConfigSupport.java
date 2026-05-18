package com.unique.examine.module.field;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.module.entity.po.ModuleField;

import java.util.Locale;
import java.util.Set;

/**
 * 字段类型枚举校验与 config_json / 列字段同步。
 */
public final class ModuleFieldConfigSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> TEXT_INPUT_STYLES = Set.of("normal", "password", "phone", "email", "url");
    private static final Set<String> SELECT_STYLES = Set.of("dropdown", "radio", "rating", "tag");
    private static final Set<String> REF_DISPLAY_STYLES = Set.of("inline", "list", "table");

    private ModuleFieldConfigSupport() {}

    public static ModuleFieldType resolveType(String rawFieldType) {
        return ModuleFieldType.parse(rawFieldType)
                .orElseThrow(() -> new BusinessException(400, "fieldType 不在支持的枚举内: " + rawFieldType));
    }

    public static String normalizeConfigJson(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(configJson);
            if (!node.isObject()) {
                throw new BusinessException(400, "configJson 须为 JSON 对象");
            }
            return MAPPER.writeValueAsString(node);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "configJson 非法: " + e.getMessage());
        }
    }

    /** 保存前：校验 config、同步列字段、规范化 fieldType 为枚举 code */
    public static void applyOnUpsert(ModuleField f, ModuleFieldType type, String configJson,
                                     String validateType, Integer multiFlag) {
        f.setFieldType(type.getCode());
        f.setConfigJson(normalizeConfigJson(configJson));

        JsonNode cfg = readConfig(f.getConfigJson());

        switch (type) {
            case TEXT -> applyText(f, cfg, validateType);
            case MONEY, PERCENT, NUMBER -> applyNumber(f, cfg, type);
            case DATETIME -> applyDatetime(f, cfg);
            case SELECT, MULTI_SELECT -> applySelect(f, cfg, type, multiFlag);
            case REF_MODULE -> { /* ref 列由 MetaService 设置 */ }
            case TAG -> applyTag(cfg);
            case TITLE -> applyTitle(cfg);
            case SERIAL_NO -> applySerial(cfg);
            case ADDRESS, PERSON, DEPARTMENT, SIGNATURE, RICH_TEXT, DATE_RANGE, FILE, TEXTAREA, BOOLEAN -> {
                // 通用：仅 config_json
            }
            default -> { }
        }

        if (type == ModuleFieldType.MULTI_SELECT || (type == ModuleFieldType.REF_MODULE && multiFlag != null && multiFlag == 1)) {
            f.setMultiFlag(1);
        } else if (type.allowsMultiFlag() && multiFlag != null && multiFlag == 1) {
            f.setMultiFlag(1);
        } else if (type == ModuleFieldType.SELECT || type == ModuleFieldType.REF_MODULE) {
            f.setMultiFlag(0);
        }
    }

    private static void applyText(ModuleField f, JsonNode cfg, String validateType) {
        String style = textFromCfg(cfg, "inputStyle");
        if (style != null) {
            f.setValidateType(mapInputStyleToValidate(style));
        } else if (validateType != null && !validateType.isBlank()) {
            f.setValidateType(validateType.trim());
        } else {
            f.setValidateType(null);
        }
        Integer maxLen = intFromCfg(cfg, "maxLength");
        if (maxLen != null) {
            f.setMaxLength(maxLen);
        }
        f.setDictCode(null);
        f.setRefModelId(null);
        f.setRefDisplayField(null);
    }

    private static void applyNumber(ModuleField f, JsonNode cfg, ModuleFieldType type) {
        f.setValidateType(null);
        f.setDictCode(null);
        f.setRefModelId(null);
        f.setRefDisplayField(null);
        if (type == ModuleFieldType.MONEY || type == ModuleFieldType.PERCENT) {
            Integer dp = intFromCfg(cfg, "decimalPlaces");
            if (dp != null && (dp < 0 || dp > 8)) {
                throw new BusinessException(400, "decimalPlaces 须在 0~8");
            }
        }
    }

    private static void applyDatetime(ModuleField f, JsonNode cfg) {
        String mode = textFromCfg(cfg, "pickerMode");
        if (mode == null || mode.isBlank()) {
            f.setDateFormat(f.getDateFormat() != null ? f.getDateFormat() : "yyyy-MM-dd HH:mm:ss");
        } else {
            f.setDateFormat(switch (mode.toLowerCase(Locale.ROOT)) {
                case "date" -> "yyyy-MM-dd";
                case "time" -> "HH:mm:ss";
                case "datetime" -> "yyyy-MM-dd HH:mm:ss";
                default -> throw new BusinessException(400, "pickerMode 须为 date|datetime|time");
            });
        }
        f.setDictCode(null);
        f.setRefModelId(null);
        f.setRefDisplayField(null);
    }

    private static void applySelect(ModuleField f, JsonNode cfg, ModuleFieldType type, Integer multiFlag) {
        String style = textFromCfg(cfg, "displayStyle");
        if (style != null && !SELECT_STYLES.contains(style)) {
            throw new BusinessException(400, "displayStyle 须为 dropdown|radio|rating|tag");
        }
        if (f.getDictCode() == null || f.getDictCode().isBlank()) {
            throw new BusinessException(400, "单选/多选须指定 dictCode");
        }
        f.setRefModelId(null);
        f.setRefDisplayField(null);
        f.setValidateType(null);
        if (type == ModuleFieldType.MULTI_SELECT || (multiFlag != null && multiFlag == 1)) {
            f.setMultiFlag(1);
        } else {
            f.setMultiFlag(0);
        }
    }

    private static void applyTag(JsonNode cfg) {
        if (cfg == null || !cfg.has("tags")) {
            throw new BusinessException(400, "标签字段 configJson.tags 不能为空");
        }
    }

    private static void applyTitle(JsonNode cfg) {
        if (cfg == null || !cfg.has("content") || cfg.get("content").asText("").isBlank()) {
            throw new BusinessException(400, "占位标题 configJson.content 不能为空");
        }
    }

    private static void applySerial(JsonNode cfg) {
        if (cfg == null || !cfg.has("segments")) {
            throw new BusinessException(400, "自定义编号 configJson.segments 不能为空");
        }
    }

    public static void validateRefConfig(ModuleField f) {
        JsonNode cfg = readConfig(f.getConfigJson());
        if (cfg != null && cfg.has("displayStyle")) {
            String s = cfg.get("displayStyle").asText("");
            if (!REF_DISPLAY_STYLES.contains(s)) {
                throw new BusinessException(400, "关联模块 displayStyle 须为 inline|list|table");
            }
            if ("table".equals(s) || "list".equals(s)) {
                if (!cfg.has("listFields") || !cfg.get("listFields").isArray() || cfg.get("listFields").isEmpty()) {
                    throw new BusinessException(400, "列表/子表样式须配置 listFields");
                }
            }
        }
        if (cfg != null && cfg.has("subTable") && cfg.get("subTable").asBoolean(false)) {
            if (f.getMultiFlag() == null || f.getMultiFlag() != 1) {
                throw new BusinessException(400, "子表/明细须开启多选(multi)");
            }
        }
        if (cfg != null && cfg.has("relationId") && !cfg.get("relationId").isNull()) {
            long rid = cfg.get("relationId").asLong(0L);
            if (rid <= 0L) {
                throw new BusinessException(400, "configJson.relationId 须为正整数");
            }
        }
    }

    private static String mapInputStyleToValidate(String style) {
        return switch (style.toLowerCase(Locale.ROOT)) {
            case "password" -> "password";
            case "phone" -> "phone";
            case "email" -> "email";
            case "url" -> "url";
            case "normal" -> null;
            default -> throw new BusinessException(400, "inputStyle 须为 normal|password|phone|email|url");
        };
    }

    private static JsonNode readConfig(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private static String textFromCfg(JsonNode cfg, String key) {
        if (cfg == null || !cfg.has(key) || cfg.get(key).isNull()) {
            return null;
        }
        return cfg.get(key).asText().trim();
    }

    private static Integer intFromCfg(JsonNode cfg, String key) {
        if (cfg == null || !cfg.has(key) || cfg.get(key).isNull()) {
            return null;
        }
        return cfg.get(key).asInt();
    }

    public static ModuleFieldType typeOf(ModuleField f) {
        if (f == null || f.getFieldType() == null) {
            return null;
        }
        return ModuleFieldType.parse(f.getFieldType()).orElse(null);
    }
}
