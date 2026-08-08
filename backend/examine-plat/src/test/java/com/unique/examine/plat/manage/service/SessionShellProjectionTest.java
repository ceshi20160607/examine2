package com.unique.examine.plat.manage.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionShellProjectionTest {

    @Test
    void exposesAdminShellsForEveryIndependentAdministrationOwner() {
        assertThat(SessionService.projectShells("PLATFORM", List.of("platform.ai.policy.manage")))
                .containsExactly("PLATFORM_RUNTIME", "PLATFORM_ADMIN");
        assertThat(SessionService.projectShells("SYSTEM", List.of(
                "system.runtime.access", "event.template.manage")))
                .containsExactly("SYSTEM_RUNTIME", "SYSTEM_ADMIN");
        assertThat(SessionService.projectShells("SYSTEM", List.of(
                "system.runtime.access", "ai.policy.manage")))
                .containsExactly("SYSTEM_RUNTIME", "SYSTEM_ADMIN");
        assertThat(SessionService.projectShells("SYSTEM", List.of(
                "system.runtime.access", "openapi.application.manage")))
                .containsExactly("SYSTEM_RUNTIME", "SYSTEM_ADMIN");
    }

    @Test
    void doesNotPromoteRuntimeOrIncompleteConfigurationPermissions() {
        assertThat(SessionService.projectShells("PLATFORM", List.of("platform.runtime.access")))
                .containsExactly("PLATFORM_RUNTIME");
        assertThat(SessionService.projectShells("SYSTEM", List.of(
                "system.runtime.access", "module.config.manage")))
                .containsExactly("SYSTEM_RUNTIME");
    }
}
