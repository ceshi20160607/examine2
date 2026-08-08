package com.unique.examine.module.runtime.flow;

import com.unique.examine.core.runtime.RuntimeRecordFlowFacade;

import java.time.Instant;
import java.util.Set;

/**
 * Module-owned bridge between a Flow projection mapping and the immutable runtime record schema.
 */
public interface RecordFlowStatusMappingPort {

    RuntimeRecordFlowFacade.RecordStatusMapping validateBinding(
            long systemId,
            long tenantId,
            long recordId,
            long schemaVersionId,
            long moduleSnapshotId,
            long logicalModuleId,
            String moduleCode,
            Set<String> effectivePermissions,
            RuntimeRecordFlowFacade.RecordStatusMapping mapping
    );

    void applyTerminal(
            long systemId,
            long tenantId,
            long recordId,
            long logicalModuleId,
            RuntimeRecordFlowFacade.RecordStatusMapping mapping,
            RuntimeRecordFlowFacade.FlowStatus status,
            long actorMemberId,
            Instant occurredAt
    );
}
