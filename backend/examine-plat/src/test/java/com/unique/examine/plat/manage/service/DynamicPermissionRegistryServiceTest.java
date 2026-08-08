package com.unique.examine.plat.manage.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicPermissionRegistryServiceTest {

    @Test
    void protectsStaticPermissionWhoseCodeSharesADynamicNamespace() {
        assertThat(DynamicPermissionRegistryService.isStaticPermission("module.config.manage"))
                .isTrue();
        assertThat(DynamicPermissionRegistryService.isStaticPermission("module.orders.view"))
                .isFalse();
    }
}
