package com.unique.unexamine.flow.manage;

import com.unique.unexamine.shared.manage.web.DomainException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 覆盖旧冻结兼容及恶意配置边界；真实鉴权、冻结版本和数据不变另在运行验收中验证。 */
class FlowManualOperationPolicyTest {
    private Map<String, Object> config(Object allowed, Object required) {
        return Map.of(FlowManualOperationPolicy.CONFIG_KEY,
                Map.of("allowedActions", allowed, "opinionRequiredActions", required));
    }

    @Test
    void legacyPolicyKeepsReasonsAndSupportedActions() {
        var policy = FlowManualOperationPolicy.fromConfig(Map.of());
        assertEquals(FlowManualOperationPolicy.LEGACY_ACTIONS, policy.allowedActions());
        assertThrows(DomainException.class, () -> policy.requireOperation("ADD_SIGN", "加签理由"));
        assertThrows(DomainException.class, () -> policy.requireOperation("DELEGATE", "委托理由"));
        assertThrows(DomainException.class, () -> policy.requireOperation("REJECT", " "));
        assertDoesNotThrow(() -> policy.requireOperation("APPROVE", null));
    }

    @Test
    void explicitPolicyRejectsWeakeningUnknownDuplicateOrWronglyTypedConfiguration() {
        for (var value : List.of(config(List.of(), List.of()),
                config(List.of("APPROVE", "CLAIM"), List.of()),
                config(List.of("APPROVE", "APPROVE"), List.of()),
                config(List.of("APPROVE"), List.of("TRANSFER")),
                config(List.of("REJECT"), List.of()),
                config(List.of("RETURN"), List.of()),
                config("APPROVE", List.of()),
                Map.<String, Object>of(FlowManualOperationPolicy.CONFIG_KEY, Map.of("allowedActions", List.of("APPROVE"))))) {
            assertEquals("FLOW_MANUAL_OPERATION_POLICY_INVALID",
                    assertThrows(DomainException.class, () -> FlowManualOperationPolicy.fromConfig(value)).code());
        }
    }

    @Test
    void configuredApprovalRequiresOpinionAndDisabledOperationFailsBeforeBusinessHandling() {
        var policy = FlowManualOperationPolicy.fromConfig(config(List.of("TRANSFER", "APPROVE"), List.of("APPROVE")));
        assertEquals(List.of("APPROVE", "TRANSFER"), policy.allowedActions());
        assertEquals("FLOW_TASK_OPERATION_DISABLED", assertThrows(DomainException.class,
                () -> policy.requireOperation("REJECT", "仍然有原因")).code());
        assertEquals("FLOW_TASK_REASON_REQUIRED", assertThrows(DomainException.class,
                () -> policy.requireOperation("APPROVE", "  ")).code());
        assertDoesNotThrow(() -> policy.requireOperation("APPROVE", "已核对"));
        assertEquals(policy, FlowManualOperationPolicy.fromConfig(Map.of(FlowManualOperationPolicy.CONFIG_KEY, policy.snapshot())));
    }

    @Test
    void claimSettingIsOptionalButStrictlyBooleanAndDoesNotBecomeCompletionOperation() {
        assertFalse(FlowManualOperationPolicy.fromConfig(config(List.of("APPROVE"), List.of())).requireClaim());
        var configured = Map.<String, Object>of(FlowManualOperationPolicy.CONFIG_KEY,
                Map.of("allowedActions", List.of("APPROVE"), "opinionRequiredActions", List.of(), "requireClaim", true));
        var policy = FlowManualOperationPolicy.fromConfig(configured);
        assertTrue(policy.requireClaim());
        assertEquals(List.of("APPROVE"), policy.allowedActions());
        assertThrows(DomainException.class, () -> policy.requireOperation("CLAIM", null));
        assertThrows(DomainException.class, () -> FlowManualOperationPolicy.fromConfig(
                Map.of(FlowManualOperationPolicy.CONFIG_KEY, Map.of("allowedActions", List.of("APPROVE"),
                        "opinionRequiredActions", List.of(), "requireClaim", "true"))));
    }

    @Test
    void participantChangesRequireExplicitFrozenPermissionAndConfiguredOpinion() {
        var policy = FlowManualOperationPolicy.fromConfig(config(
                List.of("REMOVE_SIGN", "DELEGATE", "APPROVE", "ADD_SIGN"),
                List.of("DELEGATE", "ADD_SIGN", "REMOVE_SIGN")));
        assertEquals(List.of("APPROVE", "DELEGATE", "ADD_SIGN", "REMOVE_SIGN"), policy.allowedActions());
        assertEquals("FLOW_TASK_REASON_REQUIRED", assertThrows(DomainException.class,
                () -> policy.requireOperation("REMOVE_SIGN", " ")).code());
        assertDoesNotThrow(() -> policy.requireOperation("ADD_SIGN", "需要共同核对"));
        assertDoesNotThrow(() -> policy.requireOperation("DELEGATE", "休假期间由同事代办"));
        assertEquals(policy, FlowManualOperationPolicy.fromConfig(Map.of(FlowManualOperationPolicy.CONFIG_KEY, policy.snapshot())));
    }
}
