package com.unique.unexamine.flow.manage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowAutomaticActionPolicyTest {

    @Test
    void parsesStructuredCreateAndResolvesValues() {
        var policy = FlowAutomaticActionPolicy.parse("DATA_ACTION", Map.of(
                "operation", "CREATE",
                "moduleId", 51L,
                "title", Map.of("source", "VARIABLE", "variable", "customerName"),
                "assignments", List.of(
                        Map.of("fieldCode", "amount", "value", Map.of("source", "FIXED", "value", 12)),
                        Map.of("fieldCode", "ownerName", "value", Map.of("source", "VARIABLE", "variable", "customerName")))));

        assertEquals("CREATE", policy.operation());
        assertEquals("示例客户", policy.resolvedTitle(Map.of("customerName", "示例客户")));
        assertEquals(Map.of("amount", 12, "ownerName", "示例客户"),
                policy.assignmentValues(Map.of("customerName", "示例客户")));
    }

    @Test
    void convertsLegacyWritebackWithoutKeepingTemplateExpressions() {
        var policy = FlowAutomaticActionPolicy.parse("UPDATE_FIELD", Map.of(
                "businessAction", "UPDATE",
                "fieldUpdates", Map.of("amount", "${requestedAmount}", "remark", "已处理")));

        assertTrue(policy.legacy());
        assertEquals("UPDATE", policy.operation());
        assertEquals(Map.of("amount", 30, "remark", "已处理"),
                policy.assignmentValues(Map.of("requestedAmount", 30)));
    }

    @Test
    void rejectsDuplicateTargetsAndLegacyCustomAction() {
        assertThrows(IllegalArgumentException.class, () -> FlowAutomaticActionPolicy.parse("DATA_ACTION", Map.of(
                "operation", "UPDATE", "moduleId", 51L, "targetRecordSource", "CURRENT_RECORD",
                "assignments", List.of(
                        Map.of("fieldCode", "amount", "value", Map.of("source", "FIXED", "value", 1)),
                        Map.of("fieldCode", "amount", "value", Map.of("source", "FIXED", "value", 2))))));
        assertThrows(IllegalArgumentException.class, () -> FlowAutomaticActionPolicy.parse("UPDATE_FIELD", Map.of(
                "businessAction", "APPROVE", "fieldUpdates", Map.of("amount", 1))));
    }
}
