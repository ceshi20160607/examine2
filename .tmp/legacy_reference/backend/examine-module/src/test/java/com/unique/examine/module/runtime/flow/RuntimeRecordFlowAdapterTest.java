package com.unique.examine.module.runtime.flow;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.runtime.RuntimeRecordAccessFacade;
import com.unique.examine.core.runtime.RuntimeRecordFlowFacade;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeRecordFlowAdapterTest {
    private static final Instant T0 = Instant.parse("2026-07-27T20:30:00Z");
    private static final Instant T1 = Instant.parse("2026-07-27T20:31:00Z");
    private static final Instant T2 = Instant.parse("2026-07-27T20:32:00Z");

    @Test
    void bindsOnlyAfterCanonicalViewAndActiveRecordLock() {
        var seenAccess = new AtomicReference<RuntimeRecordAccessFacade.RuntimeRecordAccessRequest>();
        var store = new FakeStore();
        var adapter = new RuntimeRecordFlowAdapter(request -> {
            seenAccess.set(request);
            assertThat(store.recordLockCount).isZero();
            return new RuntimeRecordAccessFacade.RuntimeRecordAccess(
                    Long.toString(request.recordId()), 7L, true);
        }, store, statusMappings());

        var state = adapter.bind(bind(101L, T0));

        assertThat(state).isEqualTo(new RuntimeRecordFlowFacade.RecordFlowState(
                101L, RuntimeRecordFlowFacade.FlowStatus.PENDING, 0L, T0));
        assertThat(seenAccess.get()).isEqualTo(new RuntimeRecordAccessFacade.RuntimeRecordAccessRequest(
                11L, 22L, 33L, VIEW, "purchase_order", 44L));
        assertThat(store.recordLockCount).isOne();
        assertThat(store.projection.instanceId()).isEqualTo(101L);
    }

    @Test
    void preservesCanonicalAccessFailureAndDoesNotProbeRecordState() {
        var store = new FakeStore();
        var notFound = new BusinessException(
                "RECORD_NOT_FOUND",
                "Record is missing or outside VIEW scope",
                HttpStatus.NOT_FOUND);
        var adapter = new RuntimeRecordFlowAdapter(request -> {
            throw notFound;
        }, store, statusMappings());

        assertThatThrownBy(() -> adapter.bind(bind(101L, T0))).isSameAs(notFound);
        assertThat(store.recordLockCount).isZero();
    }

    @Test
    void rejectsVisibleButNonActiveRecords() {
        var store = new FakeStore();
        store.record = new RecordFlowProjectionStore.LockedRecord(55L, "ARCHIVED");
        var adapter = adapter(store);

        assertConflict(
                () -> adapter.bind(bind(101L, T0)),
                "RECORD_FLOW_RECORD_NOT_ACTIVE");
        assertThat(store.projection).isNull();
    }

    @Test
    void openApiBindingUsesManualViewAuthorizationAndActiveRecordRule() {
        var seenAccess = new AtomicReference<RuntimeRecordAccessFacade.RuntimeRecordAccessRequest>();
        var store = new FakeStore();
        var adapter = new RuntimeRecordFlowAdapter(request -> {
            seenAccess.set(request);
            return new RuntimeRecordAccessFacade.RuntimeRecordAccess(
                    Long.toString(request.recordId()), 7L, true);
        }, store, statusMappings());

        assertThat(adapter.bind(openApiBind(101L, T0)))
                .isEqualTo(new RuntimeRecordFlowFacade.RecordFlowState(
                        101L, RuntimeRecordFlowFacade.FlowStatus.PENDING, 0L, T0));
        assertThat(seenAccess.get()).isEqualTo(
                new RuntimeRecordAccessFacade.RuntimeRecordAccessRequest(
                        11L, 22L, 33L, VIEW, "purchase_order", 44L));

        var archivedStore = new FakeStore();
        archivedStore.record = new RecordFlowProjectionStore.LockedRecord(55L, "ARCHIVED");
        assertConflict(
                () -> new RuntimeRecordFlowAdapter(
                        request -> new RuntimeRecordAccessFacade.RuntimeRecordAccess(
                                Long.toString(request.recordId()), 7L, true),
                        archivedStore,
                        statusMappings()
                ).bind(openApiBind(102L, T1)),
                "RECORD_FLOW_RECORD_NOT_ACTIVE");
    }

    @Test
    void automaticEventBindingAcceptsEveryDurableRecordStatus() {
        for (var status : List.of("DRAFT", "ACTIVE", "ARCHIVED", "TRASHED", "EXPIRED")) {
            var store = new FakeStore();
            store.record = new RecordFlowProjectionStore.LockedRecord(55L, status);
            var adapter = new RuntimeRecordFlowAdapter(
                    request -> {
                        throw new AssertionError(
                                "automatic event binding must not re-enter post-mutation detail visibility");
                    },
                    store,
                    statusMappings());

            assertThat(adapter.bind(automaticBind(101L, T0)))
                    .isEqualTo(new RuntimeRecordFlowFacade.RecordFlowState(
                            101L, RuntimeRecordFlowFacade.FlowStatus.PENDING, 0L, T0));
        }
    }

    @Test
    void automaticEventBindingStillRequiresRuntimeShellAndModuleViewPermissions() {
        var store = new FakeStore();
        var adapter = adapter(store);
        var request = new RuntimeRecordFlowFacade.BindRequest(
                11L,
                22L,
                33L,
                Set.of("system.runtime.access"),
                "purchase_order",
                44L,
                101L,
                T0,
                RuntimeRecordFlowFacade.BindingSource.AUTOMATIC_EVENT);

        assertThatThrownBy(() -> adapter.bind(request))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("PERMISSION_DENIED");
                    assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
                });
        assertThat(store.recordLockCount).isZero();
    }

    @Test
    void automaticAdditionalBindingAcceptsNonActiveRecordWithoutWeakeningManualRules() {
        var store = new FakeStore();
        store.record = new RecordFlowProjectionStore.LockedRecord(55L, "TRASHED");
        store.projection = projection(
                101L, RuntimeRecordFlowFacade.FlowStatus.PENDING, 0L, T0);
        var adapter = adapter(store);

        assertThat(adapter.bindAdditional(automaticAdditionalBind(102L, T1)))
                .isEqualTo(new RuntimeRecordFlowFacade.RecordFlowState(
                        102L, RuntimeRecordFlowFacade.FlowStatus.PENDING, 0L, T1));
        assertConflict(
                () -> adapter.bindAdditional(additionalBind(103L, T1)),
                "RECORD_FLOW_RECORD_NOT_ACTIVE");
    }

    @Test
    void returnsSamePendingBindingButRejectsAnotherPendingInstance() {
        var store = new FakeStore();
        store.projection = projection(
                101L, RuntimeRecordFlowFacade.FlowStatus.PENDING, 0L, T0);
        var adapter = adapter(store);

        assertThat(adapter.bind(bind(101L, T1))).isEqualTo(store.projection.toState());
        assertConflict(
                () -> adapter.bind(bind(102L, T1)),
                "RECORD_FLOW_ALREADY_PENDING");
        assertThat(store.replaceCount).isZero();
    }

    @Test
    void terminalProjectionCanBeReboundWithoutVersionRollbackThenTransitionedOnce() {
        var store = new FakeStore();
        store.projection = projection(
                101L, RuntimeRecordFlowFacade.FlowStatus.APPROVED, 1L, T0);
        var adapter = adapter(store);

        var rebound = adapter.bind(bind(202L, T1));
        var withdrawn = adapter.transition(new RuntimeRecordFlowFacade.TransitionRequest(
                11L,
                22L,
                202L,
                RuntimeRecordFlowFacade.FlowStatus.WITHDRAWN,
                33L,
                T2));

        assertThat(rebound).isEqualTo(new RuntimeRecordFlowFacade.RecordFlowState(
                202L, RuntimeRecordFlowFacade.FlowStatus.PENDING, 2L, T1));
        assertThat(withdrawn).isEqualTo(new RuntimeRecordFlowFacade.RecordFlowState(
                202L, RuntimeRecordFlowFacade.FlowStatus.WITHDRAWN, 3L, T2));
        assertThat(store.projection.version()).isEqualTo(3L);
        assertThat(store.replaceCount).isOne();
        assertThat(store.transitionCount).isOne();

        assertConflict(
                () -> adapter.transition(new RuntimeRecordFlowFacade.TransitionRequest(
                        11L,
                        22L,
                        202L,
                        RuntimeRecordFlowFacade.FlowStatus.TERMINATED,
                        33L,
                        T2)),
                "RECORD_FLOW_STATE_CONFLICT");
        assertThat(store.transitionCount).isOne();
    }

    @Test
    void transitionRequiresTheSameScopedPendingInstance() {
        var store = new FakeStore();
        var adapter = adapter(store);

        assertConflict(
                () -> adapter.transition(new RuntimeRecordFlowFacade.TransitionRequest(
                        11L,
                        22L,
                        999L,
                        RuntimeRecordFlowFacade.FlowStatus.REJECTED,
                        33L,
                        T1)),
                "RECORD_FLOW_STATE_CONFLICT");
        assertThat(store.transitionCount).isZero();
    }

    @Test
    void additionalBindingRequiresPendingPrimaryAndTransitionsIndependently() {
        var store = new FakeStore();
        store.projection = projection(
                101L, RuntimeRecordFlowFacade.FlowStatus.PENDING, 0L, T0);
        var adapter = adapter(store);

        var additional = adapter.bindAdditional(additionalBind(102L, T1));
        var approved = adapter.transition(new RuntimeRecordFlowFacade.TransitionRequest(
                11L,
                22L,
                102L,
                RuntimeRecordFlowFacade.FlowStatus.APPROVED,
                33L,
                T2));

        assertThat(additional).isEqualTo(new RuntimeRecordFlowFacade.RecordFlowState(
                102L, RuntimeRecordFlowFacade.FlowStatus.PENDING, 0L, T1));
        assertThat(approved).isEqualTo(new RuntimeRecordFlowFacade.RecordFlowState(
                102L, RuntimeRecordFlowFacade.FlowStatus.APPROVED, 1L, T2));
        assertThat(store.projection.toState()).isEqualTo(new RuntimeRecordFlowFacade.RecordFlowState(
                101L, RuntimeRecordFlowFacade.FlowStatus.PENDING, 0L, T0));
        assertThat(store.additional).hasSize(1);
        assertThat(store.additional.getFirst().toState()).isEqualTo(approved);
        assertThat(store.additionalTransitionCount).isOne();

        assertConflict(
                () -> adapter.bind(bind(103L, T2)),
                "RECORD_FLOW_ALREADY_PENDING");
    }

    @Test
    void additionalBindingRejectsMissingOrTerminalPrimary() {
        var store = new FakeStore();
        var adapter = adapter(store);

        assertConflict(
                () -> adapter.bindAdditional(additionalBind(102L, T1)),
                "RECORD_FLOW_STATE_CONFLICT");

        store.projection = projection(
                101L, RuntimeRecordFlowFacade.FlowStatus.APPROVED, 1L, T0);
        assertConflict(
                () -> adapter.bindAdditional(additionalBind(102L, T1)),
                "RECORD_FLOW_STATE_CONFLICT");
        assertThat(store.additional).isEmpty();
    }

    @Test
    void manualRebindWaitsUntilEveryAdditionalProjectionIsTerminal() {
        var store = new FakeStore();
        store.projection = projection(
                101L, RuntimeRecordFlowFacade.FlowStatus.APPROVED, 1L, T0);
        store.additional.add(projection(
                102L, RuntimeRecordFlowFacade.FlowStatus.PENDING, 0L, T0));
        var adapter = adapter(store);

        assertConflict(
                () -> adapter.bind(bind(103L, T1)),
                "RECORD_FLOW_ALREADY_PENDING");
        assertThat(store.replaceCount).isZero();

        adapter.transition(new RuntimeRecordFlowFacade.TransitionRequest(
                11L,
                22L,
                102L,
                RuntimeRecordFlowFacade.FlowStatus.REJECTED,
                33L,
                T1));
        var rebound = adapter.bind(bind(103L, T2));

        assertThat(rebound).isEqualTo(new RuntimeRecordFlowFacade.RecordFlowState(
                103L, RuntimeRecordFlowFacade.FlowStatus.PENDING, 2L, T2));
        assertThat(store.replaceCount).isOne();
    }

    private static RuntimeRecordFlowAdapter adapter(FakeStore store) {
        return new RuntimeRecordFlowAdapter(
                request -> new RuntimeRecordAccessFacade.RuntimeRecordAccess(
                        Long.toString(request.recordId()), 7L, true),
                store,
                statusMappings());
    }

    private static RecordFlowStatusMappingPort statusMappings() {
        return new RecordFlowStatusMappingPort() {
            @Override
            public RuntimeRecordFlowFacade.RecordStatusMapping validateBinding(
                    long systemId,
                    long tenantId,
                    long recordId,
                    long schemaVersionId,
                    long moduleSnapshotId,
                    long logicalModuleId,
                    String moduleCode,
                    Set<String> effectivePermissions,
                    RuntimeRecordFlowFacade.RecordStatusMapping mapping
            ) {
                return mapping;
            }

            @Override
            public void applyTerminal(
                    long systemId,
                    long tenantId,
                    long recordId,
                    long logicalModuleId,
                    RuntimeRecordFlowFacade.RecordStatusMapping mapping,
                    RuntimeRecordFlowFacade.FlowStatus status,
                    long actorMemberId,
                    Instant occurredAt
            ) {
            }
        };
    }

    private static RuntimeRecordFlowFacade.BindRequest bind(long instanceId, Instant occurredAt) {
        return new RuntimeRecordFlowFacade.BindRequest(
                11L,
                22L,
                33L,
                VIEW,
                "purchase_order",
                44L,
                instanceId,
                occurredAt);
    }

    private static RuntimeRecordFlowFacade.BindRequest automaticBind(
            long instanceId,
            Instant occurredAt
    ) {
        return new RuntimeRecordFlowFacade.BindRequest(
                11L,
                22L,
                33L,
                VIEW,
                "purchase_order",
                44L,
                instanceId,
                occurredAt,
                RuntimeRecordFlowFacade.BindingSource.AUTOMATIC_EVENT);
    }

    private static RuntimeRecordFlowFacade.BindRequest openApiBind(
            long instanceId,
            Instant occurredAt
    ) {
        return new RuntimeRecordFlowFacade.BindRequest(
                11L,
                22L,
                33L,
                VIEW,
                "purchase_order",
                44L,
                instanceId,
                occurredAt,
                RuntimeRecordFlowFacade.BindingSource.OPENAPI);
    }

    private static RuntimeRecordFlowFacade.AdditionalBindRequest additionalBind(
            long instanceId,
            Instant occurredAt
    ) {
        return new RuntimeRecordFlowFacade.AdditionalBindRequest(
                11L,
                22L,
                33L,
                VIEW,
                "purchase_order",
                44L,
                instanceId,
                occurredAt,
                "record:11:22:purchase_order:44:1:RECORD_ACTIVATED");
    }

    private static RuntimeRecordFlowFacade.AdditionalBindRequest automaticAdditionalBind(
            long instanceId,
            Instant occurredAt
    ) {
        return new RuntimeRecordFlowFacade.AdditionalBindRequest(
                11L,
                22L,
                33L,
                VIEW,
                "purchase_order",
                44L,
                instanceId,
                occurredAt,
                "record:11:22:purchase_order:44:1:RECORD_DELETED",
                RuntimeRecordFlowFacade.BindingSource.AUTOMATIC_EVENT);
    }

    private static RecordFlowProjectionStore.Projection projection(
            long instanceId,
            RuntimeRecordFlowFacade.FlowStatus status,
            long version,
            Instant updatedAt
    ) {
        return new RecordFlowProjectionStore.Projection(
                11L, 22L, 44L, 55L, instanceId, status, version, updatedAt);
    }

    private static void assertConflict(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
            String code
    ) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(code);
                    assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    private static final Set<String> VIEW = Set.of(
            "system.runtime.access",
            "module.purchase_order.view");

    private static final class FakeStore implements RecordFlowProjectionStore {
        private LockedRecord record = new LockedRecord(55L, "ACTIVE");
        private Projection projection;
        private final List<Projection> additional = new ArrayList<>();
        private int recordLockCount;
        private int replaceCount;
        private int transitionCount;
        private int additionalTransitionCount;

        @Override
        public Optional<LockedRecord> lockRecord(
                long systemId,
                long tenantId,
                String moduleCode,
                long recordId
        ) {
            recordLockCount++;
            return Optional.ofNullable(record);
        }

        @Override
        public Optional<Projection> lockByRecord(long systemId, long tenantId, long recordId) {
            return Optional.ofNullable(projection);
        }

        @Override
        public Optional<Projection> lockByInstance(long systemId, long tenantId, long instanceId) {
            return Optional.ofNullable(projection)
                    .filter(value -> value.systemId() == systemId
                            && value.tenantId() == tenantId
                            && value.instanceId() == instanceId);
        }

        @Override
        public Optional<Projection> lockAdditionalByInstance(
                long systemId,
                long tenantId,
                long instanceId
        ) {
            return additional.stream()
                    .filter(value -> value.systemId() == systemId
                            && value.tenantId() == tenantId
                            && value.instanceId() == instanceId)
                    .findFirst();
        }

        @Override
        public boolean lockPendingAdditionalByRecord(long systemId, long tenantId, long recordId) {
            return additional.stream().anyMatch(value -> value.systemId() == systemId
                    && value.tenantId() == tenantId
                    && value.recordId() == recordId
                    && value.status() == RuntimeRecordFlowFacade.FlowStatus.PENDING);
        }

        @Override
        public Optional<Projection> findByRecord(long systemId, long tenantId, long recordId) {
            return Optional.ofNullable(projection);
        }

        @Override
        public List<Projection> findAdditionalByRecord(long systemId, long tenantId, long recordId) {
            return List.copyOf(additional);
        }

        @Override
        public void insert(
                long systemId,
                long tenantId,
                long recordId,
                long logicalModuleId,
                long instanceId,
                long memberId,
                Instant occurredAt,
                RuntimeRecordFlowFacade.RecordStatusMapping statusMapping
        ) {
            projection = new Projection(
                    systemId,
                    tenantId,
                    recordId,
                    logicalModuleId,
                    instanceId,
                    RuntimeRecordFlowFacade.FlowStatus.PENDING,
                    0,
                    occurredAt,
                    statusMapping);
        }

        @Override
        public void insertAdditional(
                long systemId,
                long tenantId,
                long recordId,
                long logicalModuleId,
                long instanceId,
                String eventKey,
                long memberId,
                Instant occurredAt,
                RuntimeRecordFlowFacade.RecordStatusMapping statusMapping
        ) {
            additional.add(new Projection(
                    systemId,
                    tenantId,
                    recordId,
                    logicalModuleId,
                    instanceId,
                    RuntimeRecordFlowFacade.FlowStatus.PENDING,
                    0,
                    occurredAt,
                    statusMapping));
        }

        @Override
        public int replaceTerminal(
                Projection previous,
                long logicalModuleId,
                long instanceId,
                long memberId,
                Instant occurredAt,
                RuntimeRecordFlowFacade.RecordStatusMapping statusMapping
        ) {
            replaceCount++;
            projection = new Projection(
                    previous.systemId(),
                    previous.tenantId(),
                    previous.recordId(),
                    logicalModuleId,
                    instanceId,
                    RuntimeRecordFlowFacade.FlowStatus.PENDING,
                    previous.version() + 1,
                    occurredAt,
                    statusMapping);
            return 1;
        }

        @Override
        public int transition(
                Projection previous,
                RuntimeRecordFlowFacade.FlowStatus status,
                long actorMemberId,
                Instant occurredAt
        ) {
            transitionCount++;
            projection = new Projection(
                    previous.systemId(),
                    previous.tenantId(),
                    previous.recordId(),
                    previous.logicalModuleId(),
                    previous.instanceId(),
                    status,
                    previous.version() + 1,
                    occurredAt);
            return 1;
        }

        @Override
        public int transitionAdditional(
                Projection previous,
                RuntimeRecordFlowFacade.FlowStatus status,
                long actorMemberId,
                Instant occurredAt
        ) {
            additionalTransitionCount++;
            var index = additional.indexOf(previous);
            additional.set(index, new Projection(
                    previous.systemId(),
                    previous.tenantId(),
                    previous.recordId(),
                    previous.logicalModuleId(),
                    previous.instanceId(),
                    status,
                    previous.version() + 1,
                    occurredAt));
            return 1;
        }
    }
}
