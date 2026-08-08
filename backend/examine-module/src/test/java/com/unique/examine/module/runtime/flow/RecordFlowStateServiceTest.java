package com.unique.examine.module.runtime.flow;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.runtime.RuntimeRecordAccessFacade;
import com.unique.examine.core.runtime.RuntimeRecordFlowFacade;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordFlowStateServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-27T20:30:00Z");
    private static final Set<String> VIEW = Set.of(
            "system.runtime.access",
            "module.purchase_order.view");

    @Test
    void canonicalViewRunsBeforeProjectionReadAndMapsIdsAsStrings() {
        var accessRequest = new AtomicReference<RuntimeRecordAccessFacade.RuntimeRecordAccessRequest>();
        var store = new QueryStore();
        store.projection = new RecordFlowProjectionStore.Projection(
                11L,
                22L,
                44L,
                55L,
                101L,
                RuntimeRecordFlowFacade.FlowStatus.PENDING,
                2L,
                NOW);
        var service = new RecordFlowStateService(request -> {
            accessRequest.set(request);
            assertThat(store.read).isFalse();
            return new RuntimeRecordAccessFacade.RuntimeRecordAccess("44", 7L, true);
        }, store);

        var result = service.find(session(), "purchase_order", 44L);

        assertThat(accessRequest.get()).isEqualTo(new RuntimeRecordAccessFacade.RuntimeRecordAccessRequest(
                11L, 22L, 33L, VIEW, "purchase_order", 44L));
        assertThat(result).isEqualTo(new RecordFlowViews.RecordFlowState(
                "101", RuntimeRecordFlowFacade.FlowStatus.PENDING, 2L, NOW));
    }

    @Test
    void returnsNullWhenVisibleRecordHasNoProjection() {
        var service = new RecordFlowStateService(
                request -> new RuntimeRecordAccessFacade.RuntimeRecordAccess("44", 7L, true),
                new QueryStore());

        assertThat(service.find(session(), "purchase_order", 44L)).isNull();
    }

    @Test
    void canonicalFailurePreventsProjectionProbe() {
        var store = new QueryStore();
        var notFound = new BusinessException(
                "RECORD_NOT_FOUND",
                "Record is outside VIEW scope",
                HttpStatus.NOT_FOUND);
        var service = new RecordFlowStateService(request -> {
            throw notFound;
        }, store);

        assertThatThrownBy(() -> service.find(session(), "purchase_order", 44L))
                .isSameAs(notFound);
        assertThat(store.read).isFalse();
    }

    @Test
    void pluralReadReturnsPrimaryThenAdditionalStatesInStoreOrder() {
        var store = new QueryStore();
        store.projection = projection(
                101L, RuntimeRecordFlowFacade.FlowStatus.PENDING, 2L, NOW);
        store.additional.add(projection(
                102L, RuntimeRecordFlowFacade.FlowStatus.APPROVED, 1L, NOW.plusSeconds(1)));
        store.additional.add(projection(
                103L, RuntimeRecordFlowFacade.FlowStatus.PENDING, 0L, NOW.plusSeconds(1)));
        var service = new RecordFlowStateService(
                request -> {
                    assertThat(store.read).isFalse();
                    return new RuntimeRecordAccessFacade.RuntimeRecordAccess("44", 7L, true);
                },
                store);

        var result = service.findAll(session(), "purchase_order", 44L);

        assertThat(result).containsExactly(
                new RecordFlowViews.RecordFlowState(
                        "101", RuntimeRecordFlowFacade.FlowStatus.PENDING, 2L, NOW),
                new RecordFlowViews.RecordFlowState(
                        "102", RuntimeRecordFlowFacade.FlowStatus.APPROVED, 1L, NOW.plusSeconds(1)),
                new RecordFlowViews.RecordFlowState(
                        "103", RuntimeRecordFlowFacade.FlowStatus.PENDING, 0L, NOW.plusSeconds(1)));
    }

    @Test
    void pluralReadReturnsAnEmptyArrayWhenNoProjectionExists() {
        var service = new RecordFlowStateService(
                request -> new RuntimeRecordAccessFacade.RuntimeRecordAccess("44", 7L, true),
                new QueryStore());

        assertThat(service.findAll(session(), "purchase_order", 44L)).isEmpty();
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

    private static RuntimeSession session() {
        return new RuntimeSession(9L, 11L, 33L, 22L, VIEW);
    }

    private static final class QueryStore implements RecordFlowProjectionStore {
        private Projection projection;
        private final List<Projection> additional = new ArrayList<>();
        private boolean read;

        @Override
        public Optional<Projection> findByRecord(long systemId, long tenantId, long recordId) {
            read = true;
            return Optional.ofNullable(projection);
        }

        @Override
        public List<Projection> findAdditionalByRecord(long systemId, long tenantId, long recordId) {
            read = true;
            return List.copyOf(additional);
        }

        @Override
        public Optional<LockedRecord> lockRecord(
                long systemId,
                long tenantId,
                String moduleCode,
                long recordId
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Projection> lockByRecord(long systemId, long tenantId, long recordId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Projection> lockByInstance(long systemId, long tenantId, long instanceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Projection> lockAdditionalByInstance(
                long systemId,
                long tenantId,
                long instanceId
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean lockPendingAdditionalByRecord(long systemId, long tenantId, long recordId) {
            throw new UnsupportedOperationException();
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
            throw new UnsupportedOperationException();
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
            throw new UnsupportedOperationException();
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
            throw new UnsupportedOperationException();
        }

        @Override
        public int transition(
                Projection previous,
                RuntimeRecordFlowFacade.FlowStatus status,
                long actorMemberId,
                Instant occurredAt
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int transitionAdditional(
                Projection previous,
                RuntimeRecordFlowFacade.FlowStatus status,
                long actorMemberId,
                Instant occurredAt
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
