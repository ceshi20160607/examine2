package com.unique.unexamine.core.field;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 固定字段类型决定物理值列；保存、读取和查询不能各自猜测 JSON 类型。 */
public final class FieldValueCodec {
    private FieldValueCodec() {}

    /** 一行只写一种业务值列，短字符串可直接使用值列上的索引。 */
    public record Cells(String varchar, String text, BigDecimal number, LocalDateTime time, String json) {}

    /** 将已校验的原始业务值编码到对应物理列。 */
    public static Cells encode(FieldType type, Object value, ObjectMapper mapper) {
        return encode(new FieldDefinition("value", "值", type, false, java.util.Map.of()), value, mapper);
    }

    /** 按完整字段定义编码，计算字段的结果列由 formulaConfig.resultType 决定。 */
    public static Cells encode(FieldDefinition definition, Object value, ObjectMapper mapper) {
        FieldType type = definition.type();
        if (value == null) return new Cells(null, null, null, null, null);
        FieldType.ValueColumn column = valueColumn(definition, value);
        if (column == FieldType.ValueColumn.NONE)
            throw new IllegalArgumentException("计算字段不能直接保存业务值");
        return switch (column) {
            case NUMBER -> {
                BigDecimal number = new BigDecimal(value.toString());
                if (type.kind() == FieldType.Kind.OPTION || type.kind() == FieldType.Kind.REFERENCE)
                    number = BigDecimal.valueOf(number.longValueExact());
                BigDecimal normalized = number.stripTrailingZeros();
                if (normalized.precision() - normalized.scale() > 35 || Math.max(normalized.scale(), 0) > 30)
                    throw new IllegalArgumentException("数值超出数据库可保存范围");
                yield new Cells(null, null, number, null, null);
            }
            case TIME -> new Cells(null, null, null, value.toString().length()==10
                    ? LocalDate.parse(value.toString()).atStartOfDay() : LocalDateTime.parse(value.toString()), null);
            case VARCHAR -> {
                String text = value.toString();
                if (text.codePointCount(0, text.length()) > 255)
                    throw new IllegalArgumentException("短字符串超出255字符保存范围");
                yield new Cells(text, null, null, null, null);
            }
            case TEXT -> {
                String text = value.toString();
                yield new Cells(null, text, null, null, null);
            }
            case JSON -> structured(value, mapper);
            case NONE -> throw new IllegalArgumentException("计算字段不能直接保存业务值");
        };
    }

    private static Cells structured(Object value, ObjectMapper mapper) {
        try { return new Cells(null, null, null, null, mapper.writeValueAsString(value)); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("结构化字段值无法保存", ex); }
    }

    /** 按发布字段类型还原原始值，展示对象在业务层另行解析。 */
    public static Object decode(FieldType type, Cells cells, ObjectMapper mapper) {
        return decode(new FieldDefinition("value", "值", type, false, java.util.Map.of()), cells, mapper);
    }

    /** 按完整字段定义还原值，保证公式结果按配置类型读取。 */
    public static Object decode(FieldDefinition definition, Cells cells, ObjectMapper mapper) {
        FieldType type = definition.type();
        if ((type==FieldType.SELECT || type.kind() == FieldType.Kind.REFERENCE) && cells.json() != null)
            return readStructured(cells.json(), mapper);
        return switch (valueColumn(definition)) {
            case NUMBER -> {
                if (cells.number() == null) yield null;
                if (type.kind() == FieldType.Kind.OPTION || type.kind() == FieldType.Kind.REFERENCE)
                    yield cells.number().longValueExact();
                yield cells.number().stripTrailingZeros();
            }
            case TIME -> cells.time() == null ? null : cells.time().toString();
            case VARCHAR -> cells.varchar();
            case TEXT -> cells.text();
            case JSON -> cells.json() == null ? null : readStructured(cells.json(), mapper);
            case NONE -> null;
        };
    }

    /** 返回字段当前配置对应的扩展表值列，筛选和排序必须复用该映射。 */
    public static FieldType.ValueColumn valueColumn(FieldDefinition definition) {
        FieldType type = definition.type();
        if (type == FieldType.AUTO_NUMBER) return FieldType.ValueColumn.VARCHAR;
        if (type == FieldType.FORMULA) {
            Object config = definition.rules().get("formulaConfig");
            if (config instanceof java.util.Map<?, ?> map && "NUMBER".equals(map.get("resultType")))
                return FieldType.ValueColumn.NUMBER;
            return FieldType.ValueColumn.TEXT;
        }
        return type.valueColumn(Boolean.TRUE.equals(definition.rules().get("multiple")));
    }

    private static FieldType.ValueColumn valueColumn(FieldDefinition definition, Object value) {
        if (value instanceof java.util.List<?> && !definition.type().computed())
            return definition.type().valueColumn(true);
        return valueColumn(definition);
    }

    private static Object readStructured(String source, ObjectMapper mapper) {
        try { return mapper.readValue(source, Object.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("结构化字段值损坏", ex); }
    }
}
