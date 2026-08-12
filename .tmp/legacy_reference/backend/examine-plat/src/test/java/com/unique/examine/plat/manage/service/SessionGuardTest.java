package com.unique.examine.plat.manage.service;

import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.plat.api.AuthenticatedSession;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionGuardTest {
    @Test
    void enforcesPlatformSystemTenantAndPermissionBoundaries() {
        var platform = session(ContextType.PLATFORM, null, null, Set.of("platform.admin.access"));
        var system = session(ContextType.SYSTEM, 10L, 20L, Set.of("system.admin.access"));

        assertThat(SessionGuard.requirePlatform(platform, "platform.admin.access")).isSameAs(platform);
        assertThat(SessionGuard.requireSystem(system, 10L, "system.admin.access")).isSameAs(system);
        assertThat(SessionGuard.requireTenant(system, 10L, 20L, "system.admin.access")).isSameAs(system);

        assertCode(() -> SessionGuard.requirePlatform(system, "platform.admin.access"), "CONTEXT_PLATFORM_REQUIRED");
        assertCode(() -> SessionGuard.requireSystem(platform, 10L, "system.admin.access"), "CONTEXT_SYSTEM_MISMATCH");
        assertCode(() -> SessionGuard.requireSystem(system, 11L, "system.admin.access"), "CONTEXT_SYSTEM_MISMATCH");
        assertCode(() -> SessionGuard.requireTenant(system, 10L, 21L, "system.admin.access"), "CONTEXT_TENANT_MISMATCH");
        assertCode(() -> SessionGuard.requirePermission(system, "system.role.manage"), "PERMISSION_DENIED");
    }

    private static AuthenticatedSession session(
            ContextType type,
            Long systemId,
            Long tenantId,
            Set<String> permissions
    ) {
        return new AuthenticatedSession(1L, 2L, type, systemId, tenantId, 3L, 1L, permissions);
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.code()).isEqualTo(code));
    }
}
