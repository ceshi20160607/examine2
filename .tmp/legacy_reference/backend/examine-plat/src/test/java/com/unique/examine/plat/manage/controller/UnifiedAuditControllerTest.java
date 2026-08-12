package com.unique.examine.plat.manage.controller;

import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.audit.OperationsHealthModels;
import com.unique.examine.plat.manage.audit.OperationsHealthQuery;
import com.unique.examine.plat.manage.audit.UnifiedAuditModels;
import com.unique.examine.plat.manage.audit.UnifiedAuditQuery;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnifiedAuditControllerTest {
    @Test
    void systemEndpointsDeriveTenantFromAuthorizedSession() {
        var audit = new CapturingAudit();
        var health = new CapturingHealth();
        var controller = new UnifiedAuditController(audit, health);
        var session = session(ContextType.SYSTEM, 41L, 73L, Set.of("system.audit.view"));

        controller.system(41, "request-1", null, null, null, null, null,
                null, null, 1, 20, session, request());
        controller.systemHealth(41, session, request());

        assertThat(audit.scope).isEqualTo(UnifiedAuditModels.Scope.system(41, 73));
        assertThat(audit.query.requestId()).isEqualTo("request-1");
        assertThat(health.scope).isEqualTo(UnifiedAuditModels.Scope.system(41, 73));
    }

    @Test
    void platformEndpointCannotBeUsedWithSystemSessionOrMissingPermission() {
        var controller = new UnifiedAuditController(new CapturingAudit(), new CapturingHealth());

        assertThatThrownBy(() -> controller.platform(null, null, null, null, null, null,
                null, null, 1, 20,
                session(ContextType.SYSTEM, 41L, 73L, Set.of("system.audit.view")), request()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> controller.platformHealth(
                session(ContextType.PLATFORM, null, null, Set.of()), request()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void systemEndpointRejectsAnotherSystemEvenWithPermission() {
        var controller = new UnifiedAuditController(new CapturingAudit(), new CapturingHealth());
        assertThatThrownBy(() -> controller.system(42, null, null, null, null, null, null,
                null, null, 1, 20,
                session(ContextType.SYSTEM, 41L, 73L, Set.of("system.audit.view")), request()))
                .isInstanceOf(BusinessException.class);
    }

    private static AuthenticatedSession session(
            ContextType type, Long systemId, Long tenantId, Set<String> permissions
    ) {
        return new AuthenticatedSession(1, 2, type, systemId, tenantId,
                type == ContextType.SYSTEM ? 3L : null, 1, permissions);
    }

    private static MockHttpServletRequest request() {
        var request = new MockHttpServletRequest();
        request.setAttribute("requestId", "request-test");
        request.setAttribute("traceId", "trace-test");
        return request;
    }

    private static final class CapturingAudit implements UnifiedAuditQuery {
        private UnifiedAuditModels.Scope scope;
        private UnifiedAuditModels.Query query;

        @Override
        public UnifiedAuditModels.Page search(UnifiedAuditModels.Scope scope, UnifiedAuditModels.Query query) {
            this.scope = scope;
            this.query = query;
            return new UnifiedAuditModels.Page(List.of(), query.page(), query.size(), 0);
        }
    }

    private static final class CapturingHealth implements OperationsHealthQuery {
        private UnifiedAuditModels.Scope scope;

        @Override
        public OperationsHealthModels.Summary inspect(UnifiedAuditModels.Scope scope) {
            this.scope = scope;
            return new OperationsHealthModels.Summary(
                    Instant.parse("2026-08-07T00:00:00Z"), "AVAILABLE", "test", "8.93.0", List.of());
        }
    }
}
