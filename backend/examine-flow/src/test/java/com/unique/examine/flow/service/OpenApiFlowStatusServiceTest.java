package com.unique.examine.flow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.core.runtime.RuntimeRecordAccessFacade;
import com.unique.examine.flow.api.FlowViews;
import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import com.unique.examine.flow.security.FlowSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class OpenApiFlowStatusServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-04T02:00:00Z");
    private static final Set<String> READ_PERMISSIONS = Set.of(
            "flow.instance.read",
            "system.runtime.access",
            "module.purchase_order.view"
    );

    @Test
    void projectsOnlyTheStableExternalStatusContract() throws Exception {
        var fixture = fixture(null);

        var result = fixture.status.status(fixture.session, fixture.instance.id());

        assertThat(result).isEqualTo(new FlowViews.OpenApiInstanceStatus(
                Long.toString(fixture.instance.id()),
                Long.toString(fixture.instance.definitionId()),
                1,
                "purchase-001",
                "PENDING",
                NOW.toString(),
                null,
                0,
                0,
                "legacy",
                "SEQUENTIAL",
                "HUMAN_APPROVAL",
                null
        ));
        var json = new ObjectMapper().valueToTree(result);
        var names = new ArrayList<String>();
        json.fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactly(
                "instanceId", "definitionId", "definitionVersion", "businessKey",
                "status", "startedAt", "completedAt", "currentStepIndex",
                "currentStageIndex", "currentStageCode", "approvalMode",
                "completionPhase", "recordBinding");
        assertThat(json.toString()).doesNotContain(
                "requester", "approver", "history", "evidence", "decision",
                "completionExecutions", "compensation", "payload");
    }

    @Test
    void unboundInstanceDoesNotResolveRuntimeRecordAccess() {
        var fixture = fixture(null);

        fixture.status.status(fixture.session, fixture.instance.id());

        assertThat(fixture.access.requests).isEmpty();
    }

    @Test
    void boundInstanceUsesTheCanonicalViewGateWithTheCompleteLiveSession() {
        var binding = new ApprovalInstance.RecordBinding("purchase_order", 901L);
        var fixture = fixture(binding);

        var result = fixture.status.status(fixture.session, fixture.instance.id());

        assertThat(fixture.access.requests).containsExactly(
                new RuntimeRecordAccessFacade.RuntimeRecordAccessRequest(
                        11L,
                        22L,
                        33L,
                        READ_PERMISSIONS,
                        "purchase_order",
                        901L
                ));
        assertThat(result.recordBinding())
                .isEqualTo(new FlowViews.RecordBinding("purchase_order", "901"));
    }

    @Test
    void boundRecordNotFoundOrOutOfScopeFailurePropagatesUnchanged() {
        var fixture = fixture(
                new ApprovalInstance.RecordBinding("purchase_order", 901L));
        var failure = new BusinessException(
                "RECORD_NOT_FOUND",
                "Record does not exist or is outside the current data scope",
                HttpStatus.NOT_FOUND
        );
        fixture.access.failure = failure;

        var thrown = catchThrowable(
                () -> fixture.status.status(fixture.session, fixture.instance.id()));

        assertThat(thrown).isSameAs(failure);
    }

    @Test
    void resolvesTheWorkflowFromTheAuthenticatedSystemAndTenant() {
        var fixture = fixture(null);

        fixture.status.status(fixture.session, fixture.instance.id());

        assertThat(fixture.services.requests).containsExactly(new Scope(11L, 22L));
    }

    @Test
    void aDifferentTenantCannotObserveTheInstance() {
        var fixture = fixture(null);
        fixture.services.workflows.put(
                new Scope(11L, 23L), workflow(11L, 23L, new AtomicLong(500L)));
        var otherTenant = new FlowSession(44L, 11L, 23L, 55L, READ_PERMISSIONS);

        var failure = catchThrowableOfType(
                () -> fixture.status.status(otherTenant, fixture.instance.id()),
                ApprovalDomainException.class);

        assertThat(failure.code())
                .isEqualTo(ApprovalDomainException.Code.INSTANCE_NOT_FOUND);
        assertThat(fixture.services.requests).containsExactly(new Scope(11L, 23L));
        assertThat(fixture.access.requests).isEmpty();
    }

    @Test
    void requiresTheCurrentFlowReadPermissionBeforeResolvingTheTenant() {
        var fixture = fixture(null);
        var revoked = new FlowSession(44L, 11L, 22L, 33L, Set.of());

        var failure = catchThrowableOfType(
                () -> fixture.status.status(revoked, fixture.instance.id()),
                BusinessException.class);

        assertThat(failure.code()).isEqualTo("PERMISSION_DENIED");
        assertThat(failure.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(fixture.services.requests).isEmpty();
    }

    @Test
    void rejectsNonPositiveInstanceIdsBeforeResolvingTheTenant() throws Exception {
        var fixture = fixture(null);

        assertThat(catchThrowable(
                () -> fixture.status.status(fixture.session, 0L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("instance ID");
        assertThat(fixture.services.requests).isEmpty();
        assertThat(OpenApiFlowStatusService.class.getMethod(
                        "status", FlowSession.class, long.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    private static Fixture fixture(ApprovalInstance.RecordBinding binding) {
        var sequence = new AtomicLong(100L);
        var workflow = workflow(11L, 22L, sequence);
        var definition = workflow.createDraft("Purchase approval", 20L);
        workflow.publish(definition.id());
        var instance = workflow.startLatest(
                definition.id(), "purchase-001", 33L, binding);
        var services = new TenantServices();
        services.workflows.put(new Scope(11L, 22L), workflow);
        var access = new AccessGate();
        var status = new OpenApiFlowStatusService(services, access);
        var session = new FlowSession(44L, 11L, 22L, 33L, READ_PERMISSIONS);
        return new Fixture(
                workflow, instance, services, access, status, session);
    }

    private static ApprovalWorkflowService workflow(
            long systemId,
            long tenantId,
            AtomicLong sequence
    ) {
        IdService ids = new IdService() {
            @Override
            public long nextId() {
                return sequence.incrementAndGet();
            }
        };
        return new ApprovalWorkflowService(
                new InMemoryApprovalRepository(),
                ids,
                Clock.fixed(NOW, ZoneOffset.UTC),
                systemId,
                tenantId
        );
    }

    private record Fixture(
            ApprovalWorkflowService workflow,
            ApprovalInstance instance,
            TenantServices services,
            AccessGate access,
            OpenApiFlowStatusService status,
            FlowSession session
    ) {
    }

    private record Scope(long systemId, long tenantId) {
    }

    private static final class TenantServices implements FlowRequestServiceFactory {
        private final Map<Scope, ApprovalWorkflowService> workflows = new HashMap<>();
        private final List<Scope> requests = new ArrayList<>();

        @Override
        public ApprovalWorkflowService forTenant(long systemId, long tenantId) {
            var scope = new Scope(systemId, tenantId);
            requests.add(scope);
            var workflow = workflows.get(scope);
            if (workflow == null) {
                throw new AssertionError("Unexpected workflow scope " + scope);
            }
            return workflow;
        }
    }

    private static final class AccessGate implements RuntimeRecordAccessFacade {
        private final List<RuntimeRecordAccessRequest> requests = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public RuntimeRecordAccess requireView(RuntimeRecordAccessRequest request) {
            requests.add(request);
            if (failure != null) {
                throw failure;
            }
            return new RuntimeRecordAccess(
                    Long.toString(request.recordId()), 7L, true);
        }
    }
}
