package com.unique.examine.module.field;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.module.entity.po.ModuleField;
import com.unique.examine.module.entity.po.ModuleRecordData;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * EAV typed 列（value_num / value_dt）与 value_text 的协同：写入时填充 typed，读出时优先 typed。
 */
public final class EavTypedValueSupport {

    private static final DateTimeFormatter ISO_DT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter ISO_D = DateTimeFormatter.ISO_LOCAL_DATE;

    private EavTypedValueSupport() {
    }

    public static void validateJsonValue(ModuleField field, JsonNode valueNode) {
        if (field == null || valueNode == null || valueNode.isNull()) {
            return;
        }
        ModuleFieldType type = ModuleFieldConfigSupport.typeOf(field);
        if (type == null) {
            return;
        }
        String code = field.getFieldCode();
        switch (type) {
            case NUMBER, MONEY, PERCENT -> parseDecimalNullable(valueNode, code);
            case BOOLEAN -> parseBooleanNumNullable(valueNode, code);
            case DATETIME -> parseDateTimeNullable(valueNode, code);
            default -> { }
        }
    }

    /** 写入前：在已设置 valueText 后调用，填充或清空 value_num / value_dt。 */
    public static void applyTypedColumns(ModuleField field, JsonNode valueNode, String valueText, ModuleRecordData row) {
        row.setValueNum(null);
        row.setValueDt(null);
        if (field == null || row == null) {
            return;
        }
        ModuleFieldType type = ModuleFieldConfigSupport.typeOf(field);
        if (type == null) {
            return;
        }
        if (valueNode == null || valueNode.isNull()) {
            return;
        }
        try {
            switch (type) {
                case NUMBER, MONEY, PERCENT -> {
                    BigDecimal n = parseDecimalNullable(valueNode, field.getFieldCode());
                    row.setValueNum(n);
                }
                case BOOLEAN -> row.setValueNum(parseBooleanNumNullable(valueNode, field.getFieldCode()));
                case DATETIME -> row.setValueDt(parseDateTimeNullable(valueNode, field.getFieldCode()));
                default -> { }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "字段 " + field.getFieldCode() + " 类型与值不匹配");
        }
    }

    public static JsonNode toJsonNode(com.fasterxml.jackson.databind.ObjectMapper om,
                                      ModuleField field,
                                      ModuleRecordData row) {
        if (row == null) {
            return om.nullNode();
        }
        String vt = row.getValueText();
        ModuleFieldType type = field == null ? null : ModuleFieldConfigSupport.typeOf(field);
        if (type == null) {
            return vt == null ? om.nullNode() : om.getNodeFactory().textNode(vt);
        }
        return switch (type) {
            case NUMBER, MONEY, PERCENT -> {
                if (row.getValueNum() != null) {
                    yield om.getNodeFactory().numberNode(row.getValueNum());
                }
                if (vt == null || vt.isBlank()) {
                    yield om.nullNode();
                }
                try {
                    yield om.getNodeFactory().numberNode(new BigDecimal(vt.trim()));
                } catch (NumberFormatException e) {
                    yield om.getNodeFactory().textNode(vt);
                }
            }
            case BOOLEAN -> {
                if (row.getValueNum() != null) {
                    int cmp = row.getValueNum().compareTo(BigDecimal.ZERO);
                    yield om.getNodeFactory().booleanNode(cmp != 0);
                }
                if (vt == null || vt.isBlank()) {
                    yield om.nullNode();
                }
                String s = vt.trim().toLowerCase(Locale.ROOT);
                if ("true".equals(s) || "1".equals(s)) {
                    yield om.getNodeFactory().booleanNode(true);
                }
                if ("false".equals(s) || "0".equals(s)) {
                    yield om.getNodeFactory().booleanNode(false);
                }
                yield om.getNodeFactory().textNode(vt);
            }
            case DATETIME -> {
                if (row.getValueDt() != null) {
                    yield om.getNodeFactory().textNode(ISO_DT.format(row.getValueDt()));
                }
                if (vt == null || vt.isBlank()) {
                    yield om.nullNode();
                }
                yield om.getNodeFactory().textNode(vt);
            }
            default -> vt == null ? om.nullNode() : om.getNodeFactory().textNode(vt);
        };
    }

    /** 列表接口等仅需字符串时使用。 */
    public static String toDisplayString(ModuleField field, ModuleRecordData row) {
        if (row == null) {
            return "";
        }
        ModuleFieldType type = field == null ? null : ModuleFieldConfigSupport.typeOf(field);
        if (type == ModuleFieldType.NUMBER || type == ModuleFieldType.MONEY || type == ModuleFieldType.PERCENT) {
            if (row.getValueNum() != null) {
                return row.getValueNum().stripTrailingZeros().toPlainString();
            }
        }
        if (type == ModuleFieldType.BOOLEAN) {
            if (row.getValueNum() != null) {
                return row.getValueNum().compareTo(BigDecimal.ZERO) != 0 ? "true" : "false";
            }
        }
        if (type == ModuleFieldType.DATETIME && row.getValueDt() != null) {
            return ISO_DT.format(row.getValueDt());
        }
        return row.getValueText() == null ? "" : row.getValueText();
    }

    public static BigDecimal parseBooleanFilterForEq(Object raw, String fieldLabel) {
        if (raw == null) {
            throw new BusinessException(400, fieldLabel + " 的过滤值不能为空");
        }
        if (raw instanceof Boolean b) {
            return b ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (raw instanceof Number n) {
            return n.intValue() != 0 ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        String s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            throw new BusinessException(400, fieldLabel + " 的过滤值不能为空");
        }
        if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) {
            return BigDecimal.ONE;
        }
        if ("false".equals(s) || "0".equals(s) || "no".equals(s)) {
            return BigDecimal.ZERO;
        }
        throw new BusinessException(400, fieldLabel + " 的过滤值须为布尔");
    }

    public static BigDecimal parseDecimalForFilter(Object raw, String fieldLabel) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) {
            throw new BusinessException(400, fieldLabel + " 的过滤值不能为空");
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new BusinessException(400, fieldLabel + " 的过滤值须为数字");
        }
    }

    public static LocalDateTime parseDateTimeForFilter(Object raw, String fieldLabel) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof LocalDateTime ldt) {
            return ldt;
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) {
            throw new BusinessException(400, fieldLabel + " 的过滤值不能为空");
        }
        try {
            if (s.length() <= 10 && !s.contains("T")) {
                LocalDate d = LocalDate.parse(s, ISO_D);
                return d.atStartOfDay();
            }
            return LocalDateTime.parse(s, ISO_DT);
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, fieldLabel + " 的过滤值须为合法日期时间（ISO-8601）");
        }
    }

    private static BigDecimal parseDecimalNullable(JsonNode v, String fieldCode) {
        if (v.isNumber()) {
            return BigDecimal.valueOf(v.doubleValue());
        }
        if (v.isTextual()) {
            String s = v.asText().trim();
            if (s.isEmpty()) {
                return null;
            }
            try {
                return new BigDecimal(s);
            } catch (NumberFormatException e) {
                throw new BusinessException(400, "字段 " + fieldCode + " 须为数字");
            }
        }
        throw new BusinessException(400, "字段 " + fieldCode + " 须为数字");
    }

    private static BigDecimal parseBooleanNumNullable(JsonNode v, String fieldCode) {
        if (v.isBoolean()) {
            return v.asBoolean() ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (v.isNumber()) {
            return v.asInt() != 0 ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (v.isTextual()) {
            String s = v.asText().trim().toLowerCase(Locale.ROOT);
            if (s.isEmpty()) {
                return null;
            }
            if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) {
                return BigDecimal.ONE;
            }
            if ("false".equals(s) || "0".equals(s) || "no".equals(s)) {
                return BigDecimal.ZERO;
            }
        }
        throw new BusinessException(400, "字段 " + fieldCode + " 须为布尔值");
    }

    private static LocalDateTime parseDateTimeNullable(JsonNode v, String fieldCode) {
        if (v.isTextual()) {
            String s = v.asText().trim();
            if (s.isEmpty()) {
                return null;
            }
            try {
                if (s.length() <= 10 && !s.contains("T")) {
                    return LocalDate.parse(s, ISO_D).atStartOfDay();
                }
                return LocalDateTime.parse(s, ISO_DT);
            } catch (DateTimeParseException e) {
                throw new BusinessException(400, "字段 " + fieldCode + " 须为合法日期时间");
            }
        }
        throw new BusinessException(400, "字段 " + fieldCode + " 须为日期时间字符串");
    }
}
