package com.unique.unexamine.core.field;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 验证大数据量扩展值表的一行一值列契约及固定字段类型映射。 */
class FieldValueCodecTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void scalarAndStructuredValuesUseTheirDeclaredColumns() {
        assertEquals("value_number", FieldType.USER.valueColumn().sqlName());
        assertEquals("value_varchar", FieldType.ATTACHMENT.valueColumn().sqlName());
        assertEquals("value_json", FieldType.CASCADE.valueColumn().sqlName());
        assertEquals("value_varchar", FieldType.SELECT.valueColumn().sqlName());
        assertEquals("value_json", FieldType.SELECT.valueColumn(true).sqlName());
        assertEquals("value_json", FieldType.USER.valueColumn(true).sqlName());

        var number = FieldValueCodec.encode(FieldType.NUMBER, 123, json);
        assertEquals(new BigDecimal("123"), number.number());
        assertNull(number.text()); assertNull(number.time()); assertNull(number.json());

        var member = FieldValueCodec.encode(FieldType.USER, 42L, json);
        assertEquals(42L, FieldValueCodec.decode(FieldType.USER, member, json));
        assertNull(member.json());

        String batchId = FileBatchId.create();
        assertEquals(32, batchId.length());
        assertTrue(FileBatchId.valid(batchId));
        var attachment = FieldValueCodec.encode(FieldType.ATTACHMENT, batchId, json);
        assertEquals(batchId, attachment.varchar());
        assertEquals(batchId, FieldValueCodec.decode(FieldType.ATTACHMENT, attachment, json));
        assertNull(attachment.number());
        assertNull(attachment.text());

        var single = FieldValueCodec.encode(FieldType.SELECT, "enabled", json);
        assertEquals("enabled", FieldValueCodec.decode(FieldType.SELECT, single, json));
        var multi = FieldValueCodec.encode(FieldType.SELECT, List.of("enabled", "disabled"), json);
        assertEquals(List.of("enabled", "disabled"), FieldValueCodec.decode(FieldType.SELECT, multi, json));
        assertNull(multi.number());

        var cascade = FieldValueCodec.encode(FieldType.CASCADE, Map.of("path", List.of(1L, 2L)), json);
        assertEquals(Map.of("path", List.of(1, 2)), FieldValueCodec.decode(FieldType.CASCADE, cascade, json));
        assertNull(cascade.number());
    }

    /** 计算字段落到扩展值表时，列映射和读取必须遵循配置结果类型。 */
    @Test void computedFieldUsesConfiguredStorageColumn() {
        var autoNumber = new FieldDefinition("serial", "编号", FieldType.AUTO_NUMBER, false, Map.of());
        var formulaNumber = new FieldDefinition("amount", "金额", FieldType.FORMULA, false,
                Map.of("formulaConfig", Map.of("resultType", "NUMBER")));
        var formulaText = new FieldDefinition("summary", "摘要", FieldType.FORMULA, false,
                Map.of("formulaConfig", Map.of("resultType", "TEXT")));

        assertEquals(FieldType.ValueColumn.VARCHAR, FieldValueCodec.valueColumn(autoNumber));
        assertEquals(FieldType.ValueColumn.NUMBER, FieldValueCodec.valueColumn(formulaNumber));
        assertEquals(FieldType.ValueColumn.TEXT, FieldValueCodec.valueColumn(formulaText));
        assertEquals("202610280001", FieldValueCodec.decode(autoNumber,
                new FieldValueCodec.Cells("202610280001", null, null, null, null), json));
        assertEquals(0, new BigDecimal("12.50").compareTo((BigDecimal) FieldValueCodec.decode(formulaNumber,
                new FieldValueCodec.Cells(null, null, new BigDecimal("12.50"), null, null), json)));
        assertEquals("合计12.50", FieldValueCodec.decode(formulaText,
                new FieldValueCodec.Cells(null, "合计12.50", null, null, null), json));
    }
}
