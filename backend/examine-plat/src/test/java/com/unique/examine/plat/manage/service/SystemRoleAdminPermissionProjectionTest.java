package com.unique.examine.plat.manage.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SystemRoleAdminPermissionProjectionTest {
    @Test
    void excludesInactiveDefinitionsFromPublishedAndDraftProjection() {
        assertThat(SystemRoleAdminService.onlyActive(
                List.of("module.orders.view", "module.orders.field.amount.read"),
                Set.of("module.orders.view")))
                .containsExactly("module.orders.view");
    }
}
