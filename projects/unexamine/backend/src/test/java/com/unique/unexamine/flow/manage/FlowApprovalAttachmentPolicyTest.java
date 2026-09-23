package com.unique.unexamine.flow.manage;

import com.unique.unexamine.shared.manage.web.DomainException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证旧发布版本兼容以及附件必填配置不能被弱类型或额外属性绕过。 */
class FlowApprovalAttachmentPolicyTest {
    @Test
    void absentPolicyKeepsLegacyNodesOptional() {
        var policy = FlowApprovalAttachmentPolicy.fromConfig(Map.of());
        assertFalse(policy.required());
        assertEquals(0, policy.minimumCount());
    }

    @Test
    void structuredRequiredPolicyIsAccepted() {
        var policy = FlowApprovalAttachmentPolicy.fromConfig(Map.of(
                FlowApprovalAttachmentPolicy.CONFIG_KEY, Map.of("required", true, "minimumCount", 1)));
        assertTrue(policy.required());
        assertEquals(1, policy.minimumCount());
    }

    @Test
    void malformedPoliciesAreRejected() {
        for (Object value : List.of("true", Map.of("required", false, "minimumCount", 1),
                Map.of("required", true), Map.of("required", true, "minimumCount", 0),
                Map.of("required", true, "minimumCount", 1.5),
                Map.of("required", true, "minimumCount", 1, "attachmentIds", List.of(1)))) {
            var config = Map.<String, Object>of(FlowApprovalAttachmentPolicy.CONFIG_KEY, value);
            assertEquals("FLOW_APPROVAL_ATTACHMENT_POLICY_INVALID",
                    assertThrows(DomainException.class,
                            () -> FlowApprovalAttachmentPolicy.fromConfig(config)).code());
        }
    }
}
