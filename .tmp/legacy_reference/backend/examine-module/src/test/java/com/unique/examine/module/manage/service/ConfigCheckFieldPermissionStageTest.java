package com.unique.examine.module.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigCheckFieldPermissionStageTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void blocksDirectEnforcementWithoutAnActiveLifecycleDeclaration() throws Exception {
        var findings = ConfigCheckService.unstagedEnforcedPermissions(
                snapshot("ENABLED", "module.orders.field.amount.read"), null);

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.fieldId()).isEqualTo(11L);
            assertThat(finding.direction()).isEqualTo("read");
            assertThat(finding.code()).isEqualTo("module.orders.field.amount.read");
        });
    }

    @Test
    void acceptsPreviouslyStagedOrEnforcedCanonicalDirection() throws Exception {
        var draft = snapshot("ENABLED", "module.orders.field.amount.read");

        assertThat(ConfigCheckService.unstagedEnforcedPermissions(
                draft, snapshot("DISABLED", "module.orders.field.amount.read"))).isEmpty();
        assertThat(ConfigCheckService.unstagedEnforcedPermissions(
                draft, snapshot("ENABLED", "module.orders.field.amount.read"))).isEmpty();
    }

    @Test
    void doesNotTreatWriteOrSensitiveReadAsStagingForGenericRead() throws Exception {
        var draft = snapshot("ENABLED", "module.orders.field.amount.read");

        assertThat(ConfigCheckService.unstagedEnforcedPermissions(
                draft, snapshot("DISABLED", "module.orders.field.amount.write"))).hasSize(1);
        assertThat(ConfigCheckService.unstagedEnforcedPermissions(
                draft, snapshot("ENABLED", "module.orders.field.amount.sensitive.read"))).hasSize(1);
    }

    private JsonNode snapshot(String status, String permissionCode) throws Exception {
        return objectMapper.readTree("""
                {
                  "modules":[{"id":"1","module_code":"orders"}],
                  "fields":[{"id":"11","module_id":"1","field_code":"amount"}],
                  "permissions":[{
                    "resource_type":"FIELD",
                    "resource_id":"11",
                    "permission_code":"%s",
                    "desired_status":"%s"
                  }]
                }
                """.formatted(permissionCode, status));
    }
}
