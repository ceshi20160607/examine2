package com.unique.unexamine.moduleconfig.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 固定状态动作无填写字段，发布前仍须拒绝伪造效果和无效状态。 */
class NamedActionConfigurationTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ModuleConfigurationService configuration = new ModuleConfigurationService(
            null, null, null, null, null, null, null, null,
            null, null, null, json, null, null, null, null, null, null, null);

    @Test
    void acceptsNamedStatusAndExistingFieldUpdate() {
        assertThatCode(() -> configuration.validateCustomActionConfig(json.valueToTree(Map.of(
                "operation", "SET_STATUS", "parameterFieldCodes", List.of(), "statusValue", "已完成")), List.of()))
                .doesNotThrowAnyException();
        assertThatCode(() -> configuration.validateCustomActionConfig(json.valueToTree(Map.of(
                "operation", "UPDATE_FIELDS", "parameterFieldCodes", List.of("result"))), List.of(
                json.valueToTree(Map.of("code", "result", "status", "ACTIVE", "fieldType", "TEXT",
                        "configJson", "{}")))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsForgedStatusConfiguration() {
        for (Object status : List.of("  ", "含\n换行", 123)) {
            assertThatThrownBy(() -> configuration.validateCustomActionConfig(json.valueToTree(Map.of(
                    "operation", "SET_STATUS", "parameterFieldCodes", List.of(), "statusValue", status)), List.of()))
                    .isInstanceOf(DomainException.class);
        }
        assertThatThrownBy(() -> configuration.validateCustomActionConfig(json.valueToTree(Map.of(
                "operation", "SET_STATUS", "parameterFieldCodes", List.of("secret"),
                "statusValue", "已完成")), List.of()))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> configuration.validateCustomActionConfig(json.valueToTree(Map.of(
                "operation", "EXECUTE_SCRIPT", "parameterFieldCodes", List.of(),
                "statusValue", "已完成")), List.of()))
                .isInstanceOf(DomainException.class);
    }
}
