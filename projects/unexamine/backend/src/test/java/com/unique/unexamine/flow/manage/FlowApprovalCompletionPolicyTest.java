package com.unique.unexamine.flow.manage;

import com.unique.unexamine.shared.manage.web.DomainException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 覆盖旧版本兼容、比例取整和跨审批方式绕过边界。 */
class FlowApprovalCompletionPolicyTest {
    @Test
    void absentPolicyKeepsAllParticipantsRequired() {
        var policy = FlowApprovalCompletionPolicy.fromConfig(Map.of(), "ALL_SIGN");
        assertFalse(policy.reached(2, 3));
        assertTrue(policy.reached(3, 3));
    }

    @Test
    void ratioUsesCeilingAgainstCurrentEffectiveParticipants() {
        var policy = FlowApprovalCompletionPolicy.fromConfig(Map.of(FlowApprovalCompletionPolicy.CONFIG_KEY,
                Map.of("mode", "RATIO", "ratioPercent", 60)), "ALL_SIGN");
        assertFalse(policy.reached(2, 5));
        assertTrue(policy.reached(3, 5));
        assertEquals(policy, FlowApprovalCompletionPolicy.fromConfig(
                Map.of(FlowApprovalCompletionPolicy.CONFIG_KEY, policy.snapshot()), "ALL_SIGN"));
    }

    @Test
    void explicitPolicyRejectsWrongModeTypeRangeAndUnknownKeys() {
        for (var config : java.util.List.<Map<String, Object>>of(
                Map.of(FlowApprovalCompletionPolicy.CONFIG_KEY, Map.<String, Object>of("mode", "RATIO", "ratioPercent", 0)),
                Map.of(FlowApprovalCompletionPolicy.CONFIG_KEY, Map.<String, Object>of("mode", "RATIO", "ratioPercent", 60.0)),
                Map.of(FlowApprovalCompletionPolicy.CONFIG_KEY, Map.<String, Object>of("mode", "ALL", "ratioPercent", 100)),
                Map.of(FlowApprovalCompletionPolicy.CONFIG_KEY, Map.<String, Object>of("mode", "UNKNOWN")))) {
            assertEquals("FLOW_ALL_SIGN_COMPLETION_INVALID", assertThrows(DomainException.class,
                    () -> FlowApprovalCompletionPolicy.fromConfig(config, "ALL_SIGN")).code());
        }
        assertThrows(DomainException.class, () -> FlowApprovalCompletionPolicy.fromConfig(
                Map.of(FlowApprovalCompletionPolicy.CONFIG_KEY, Map.of("mode", "ALL")), "OR_SIGN"));
    }
}
