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

        var multi = FieldValueCodec.encode(FieldType.MULTI_SELECT, List.of(1L, 2L), json);
        assertEquals(List.of(1, 2), FieldValueCodec.decode(FieldType.MULTI_SELECT, multi, json));
        assertNull(multi.number());

        var cascade = FieldValueCodec.encode(FieldType.CASCADE, Map.of("path", List.of(1L, 2L)), json);
        assertEquals(Map.of("path", List.of(1, 2)), FieldValueCodec.decode(FieldType.CASCADE, cascade, json));
        assertNull(cascade.number());
    }
}
