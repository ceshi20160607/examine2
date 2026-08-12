package com.unique.examine.module.manage.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigSnapshotFieldPermissionTest {
    @Test
    void registersEnabledDefinitionsAndOnlyStagedGenericFieldDefinitions() {
        assertThat(registerable("module.orders.view", "MENU", "MODULE", "ENABLED")).isTrue();
        assertThat(registerable("module.orders.field.amount.read", "FIELD", "FIELD", "DISABLED")).isTrue();
        assertThat(registerable("module.orders.field.amount.write", "FIELD", "FIELD", "DISABLED")).isTrue();
        assertThat(registerable("module.orders.field.identity.sensitive.read", "FIELD", "FIELD", "DISABLED"))
                .isFalse();
        assertThat(registerable("module.orders.action.export", "ACTION", "ACTION", "DISABLED")).isFalse();
        assertThat(registerable("module.orders.field.amount.read", "FIELD", "FIELD", "ARCHIVED")).isFalse();
    }

    private static boolean registerable(String code, String permissionType, String resourceType, String status) {
        return ConfigSnapshotService.registerable(new ConfigSnapshotService.PermissionDefinitionRow(
                code, code, permissionType, resourceType, status));
    }
}
