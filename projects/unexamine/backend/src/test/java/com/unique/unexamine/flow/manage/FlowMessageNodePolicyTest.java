package com.unique.unexamine.flow.manage;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlowMessageNodePolicyTest {
    @Test
    void freezesOnlySafeInAppConfiguration() {
        var policy = FlowMessageNodePolicy.parse("CC", Map.of(
                "schemaVersion", 1,
                "subject", "合同审批抄送",
                "content", "合同已经完成审批，请查看流程详情。",
                "appendInstanceTitle", true,
                "destinationType", "FLOW_INSTANCE"));

        assertEquals("CC", policy.nodeType());
        assertFalse(policy.legacy());
        assertEquals(Map.of(
                "schemaVersion", 1,
                "messageKind", "CC",
                "subject", "合同审批抄送",
                "content", "合同已经完成审批，请查看流程详情。",
                "appendInstanceTitle", true,
                "destinationType", "FLOW_INSTANCE"), FlowMessageNodePolicy.frozen(policy));
    }

    @Test
    void rejectsCustomDestinationsAndInternalDeliveryConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> FlowMessageNodePolicy.parse("MESSAGE", Map.of(
                "schemaVersion", 1, "subject", "业务消息", "content", "请查看",
                "destinationType", "SYSTEM_ROUTE")));
        assertThrows(IllegalArgumentException.class, () -> FlowMessageNodePolicy.parse("NOTIFICATION", Map.of(
                "schemaVersion", 1, "subject", "业务通知", "content", "请查看",
                "destinationType", "FLOW_INSTANCE", "channel", "SMS")));
    }

    @Test
    void upgradesLegacyNotificationWithoutChangingItsBusinessText() {
        var policy = FlowMessageNodePolicy.parse("NOTIFICATION", Map.of(
                "subject", "旧通知", "content", "旧流程正文"));

        assertEquals(true, policy.legacy());
        assertEquals(1, FlowMessageNodePolicy.frozen(policy).get("schemaVersion"));
        assertEquals("旧流程正文", FlowMessageNodePolicy.frozen(policy).get("content"));
    }
}
