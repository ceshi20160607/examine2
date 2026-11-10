package com.unique.unexamine.core.field;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 固定槽位协议的分配、类型匹配和物理值转换测试。 */
class FieldSlotRegistryTest {
    @Test
    void allocatesCompatibleSlotsAndFallsBackAfterExhaustion() {
        FieldDefinition number = new FieldDefinition("pending", "数字", FieldType.NUMBER, false, Map.of());
        assertEquals("item_number01", FieldSlotRegistry.allocate(number, Set.of()));
        var occupied = new HashSet<String>();
        for (int i = 1; i <= 15; i++) occupied.add("item_number" + String.format("%02d", i));
        assertTrue(FieldSlotRegistry.allocate(number, occupied).startsWith("field_"));

        FieldDefinition multiple = new FieldDefinition("pending", "选项", FieldType.SELECT, false,
                Map.of("multiple", true));
        assertTrue(FieldSlotRegistry.allocate(multiple, Set.of()).startsWith("field_"));
    }

    @Test
    void matchesSlotToTypeAndConvertsFixedColumnValues() {
        FieldDefinition date = new FieldDefinition("item_time01", "日期", FieldType.DATE_TIME, false,
                Map.of("displayStyle", "DATE"));
        assertTrue(FieldSlotRegistry.isMain(date));
        assertEquals("item_time01", FieldSlotRegistry.column("item_time01"));
        assertEquals("2026-10-24T00:00", FieldSlotRegistry.physicalValue(date, "2026-10-24").toString());

        FieldDefinition select = new FieldDefinition("item_select01", "状态", FieldType.SELECT, false, Map.of());
        assertEquals("enabled", FieldSlotRegistry.physicalValue(select, "enabled"));
        assertEquals(new BigDecimal("12.50"), FieldSlotRegistry.physicalValue(
                new FieldDefinition("item_number01", "金额", FieldType.NUMBER, false, Map.of()), "12.50"));
        assertFalse(FieldSlotRegistry.isMain(new FieldDefinition("item_select01", "状态", FieldType.SELECT, false,
                Map.of("multiple", true))));
    }

    @Test
    void multilineTextUsesLongTextReservation() {
        FieldDefinition text = new FieldDefinition("pending", "说明", FieldType.TEXT, false,
                Map.of("displayStyle", "MULTI_LINE"));
        assertEquals("item_long_text01", FieldSlotRegistry.allocate(text, Set.of()));
    }

    @Test
    void attachmentUsesExtensionStorageInsteadOfTextSlot() {
        FieldDefinition attachment = new FieldDefinition("pending", "附件", FieldType.ATTACHMENT, false, Map.of());
        assertTrue(FieldSlotRegistry.allocate(attachment, Set.of()).startsWith("field_"));
        assertFalse(FieldSlotRegistry.isMain(new FieldDefinition("item_text01", "附件", FieldType.ATTACHMENT,
                false, Map.of())));
    }
}
