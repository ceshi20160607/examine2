package com.unique.examine.plat.manage.controller;

import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.lifecycle.PlatformLifecycleOperations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformLifecycleControllerTest {
    private final AtomicInteger calls = new AtomicInteger();
    private PlatformLifecycleController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        var operations = (PlatformLifecycleOperations) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{PlatformLifecycleOperations.class},
                (proxy, method, args) -> {
                    calls.incrementAndGet();
                    if (List.class.isAssignableFrom(method.getReturnType())) return List.of();
                    return null;
                });
        controller = new PlatformLifecycleController(operations, null);
        request = new MockHttpServletRequest();
        request.setAttribute(WebRequestAttributes.REQUEST_ID, "lifecycle-request");
        request.setAttribute(WebRequestAttributes.TRACE_ID, "lifecycle-trace");
    }

    @Test
    void systemDeletionRequiresPlatformContextAndPlatformSystemPermissionBeforeServiceCall() {
        assertCode(() -> controller.previewSystemDeletion(10, null, request), "AUTH_REQUIRED");
        assertCode(() -> controller.previewSystemDeletion(10,
                session(ContextType.SYSTEM, 10L, 20L, Set.of("platform.system.manage")), request),
                "CONTEXT_PLATFORM_REQUIRED");
        assertCode(() -> controller.previewSystemDeletion(10,
                session(ContextType.PLATFORM, null, null, Set.of()), request), "PERMISSION_DENIED");
        assertThat(calls).hasValue(0);

        controller.previewSystemDeletion(10,
                session(ContextType.PLATFORM, null, null, Set.of("platform.system.manage")), request);
        assertThat(calls).hasValue(1);
    }

    @Test
    void tenantOperationsRequireMatchingSystemContextAndTenantPermissionBeforeServiceCall() {
        assertCode(() -> controller.domains(10, 20, null, request), "AUTH_REQUIRED");
        assertCode(() -> controller.domains(10, 20,
                session(ContextType.PLATFORM, null, null, Set.of("system.tenant.manage")), request),
                "CONTEXT_SYSTEM_MISMATCH");
        assertCode(() -> controller.domains(10, 20,
                session(ContextType.SYSTEM, 11L, 20L, Set.of("system.tenant.manage")), request),
                "CONTEXT_SYSTEM_MISMATCH");
        assertCode(() -> controller.domains(10, 20,
                session(ContextType.SYSTEM, 10L, 20L, Set.of()), request), "PERMISSION_DENIED");
        assertThat(calls).hasValue(0);

        controller.domains(10, 20,
                session(ContextType.SYSTEM, 10L, 20L, Set.of("system.tenant.manage")), request);
        assertThat(calls).hasValue(1);
    }

    private static AuthenticatedSession session(
            ContextType context, Long systemId, Long tenantId, Set<String> permissions) {
        return new AuthenticatedSession(1, 2, context, systemId, tenantId,
                context == ContextType.SYSTEM ? 3L : null, 1, permissions);
    }

    private static void assertCode(Runnable action, String expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                failure -> assertThat(failure.code()).isEqualTo(expected));
    }
}
