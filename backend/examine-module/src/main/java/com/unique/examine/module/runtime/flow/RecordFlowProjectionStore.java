package com.unique.examine.module.runtime.flow;

import com.unique.examine.core.runtime.RuntimeRecordFlowFacade;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface RecordFlowProjectionStore {

    Optional<LockedRecord> lockRecord(long systemId, long tenantId, String moduleCode, long recordId);

    Optional<Projection> lockByRecord(long systemId, long tenantId, long recordId);

    Optional<Projection> lockByInstance(long systemId, long tenantId, long instanceId);

    Optional<Projection> lockAdditionalByInstance(long systemId, long tenantId, long instanceId);

    boolean lockPendingAdditionalByRecord(long systemId, long tenantId, long recordId);

    Optional<Projection> findByRecord(long systemId, long tenantId, long recordId);

    List<Projection> findAdditionalByRecord(long systemId, long tenantId, long recordId);

    void insert(
            long systemId,
            long tenantId,
            long recordId,
            long logicalModuleId,
            long instanceId,
            long memberId,
            Instant occurredAt,
            RuntimeRecordFlowFacade.RecordStatusMapping statusMapping
    );

    void insertAdditional(
            long systemId,
            long tenantId,
            long recordId,
            long logicalModuleId,
            long instanceId,
            String eventKey,
            long memberId,
            Instant occurredAt,
            RuntimeRecordFlowFacade.RecordStatusMapping statusMapping
    );

    int replaceTerminal(
            Projection previous,
            long logicalModuleId,
            long instanceId,
            long memberId,
            Instant occurredAt,
            RuntimeRecordFlowFacade.RecordStatusMapping statusMapping
    );

    int transitionAdditional(
            Projection previous,
            RuntimeRecordFlowFacade.FlowStatus status,
            long actorMemberId,
            Instant occurredAt
    );

    int transition(
            Projection previous,
            RuntimeRecordFlowFacade.FlowStatus status,
            long actorMemberId,
            Instant occurredAt
    );

    record LockedRecord(
            long logicalModuleId,
            String status,
            long schemaVersionId,
            long moduleSnapshotId
    ) {
        LockedRecord(long logicalModuleId, String status) {
            this(logicalModuleId, status, 1L, logicalModuleId);
        }
    }

    record Projection(
            long systemId,
            long tenantId,
            long recordId,
            long logicalModuleId,
            long instanceId,
            RuntimeRecordFlowFacade.FlowStatus status,
            long version,
            Instant updatedAt,
            RuntimeRecordFlowFacade.RecordStatusMapping statusMapping
    ) {
        Projection(
                long systemId,
                long tenantId,
                long recordId,
                long logicalModuleId,
                long instanceId,
                RuntimeRecordFlowFacade.FlowStatus status,
                long version,
                Instant updatedAt
        ) {
            this(systemId, tenantId, recordId, logicalModuleId, instanceId, status, version, updatedAt, null);
        }

        RuntimeRecordFlowFacade.RecordFlowState toState() {
            return new RuntimeRecordFlowFacade.RecordFlowState(instanceId, status, version, updatedAt);
        }
    }
}
