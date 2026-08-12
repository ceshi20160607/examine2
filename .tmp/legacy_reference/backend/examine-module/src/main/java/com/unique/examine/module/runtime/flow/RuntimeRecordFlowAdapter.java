package com.unique.examine.module.runtime.flow;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.runtime.RuntimeRecordAccessFacade;
import com.unique.examine.core.runtime.RuntimeRecordFlowFacade;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.Objects;

@Component
public class RuntimeRecordFlowAdapter implements RuntimeRecordFlowFacade {
    private static final Set<String> AUTOMATIC_EVENT_RECORD_STATUSES =
            Set.of("DRAFT", "ACTIVE", "ARCHIVED", "TRASHED", "EXPIRED");

    private final RuntimeRecordAccessFacade access;
    private final RecordFlowProjectionStore projections;
    private final RecordFlowStatusMappingPort statusMappings;

    public RuntimeRecordFlowAdapter(
            RuntimeRecordAccessFacade access,
            RecordFlowProjectionStore projections,
            RecordFlowStatusMappingPort statusMappings
    ) {
        this.access = Objects.requireNonNull(access, "access");
        this.projections = Objects.requireNonNull(projections, "projections");
        this.statusMappings = Objects.requireNonNull(statusMappings, "statusMappings");
    }

    @Override
    @Transactional
    public RecordFlowState bind(BindRequest request) {
        Objects.requireNonNull(request, "request");
        requireBindingAccess(
                request.systemId(),
                request.tenantId(),
                request.memberId(),
                request.effectivePermissions(),
                request.moduleCode(),
                request.recordId(),
                request.bindingSource());

        var record = projections.lockRecord(
                        request.systemId(), request.tenantId(), request.moduleCode(), request.recordId())
                .orElseThrow(RuntimeRecordFlowAdapter::recordNotActive);
        if (!bindingStatusAllowed(record.status(), request.bindingSource())) {
            throw recordNotActive();
        }
        var statusMapping = statusMappings.validateBinding(
                request.systemId(),
                request.tenantId(),
                request.recordId(),
                record.schemaVersionId(),
                record.moduleSnapshotId(),
                record.logicalModuleId(),
                request.moduleCode(),
                request.effectivePermissions(),
                request.recordStatusMapping());

        var existing = projections.lockByRecord(
                request.systemId(), request.tenantId(), request.recordId());
        if (existing.isEmpty()) {
            if (projections.lockPendingAdditionalByRecord(
                    request.systemId(), request.tenantId(), request.recordId())) {
                throw alreadyPending();
            }
            if (projections.lockAdditionalByInstance(
                    request.systemId(), request.tenantId(), request.instanceId()).isPresent()) {
                throw stateConflict();
            }
            try {
                projections.insert(
                        request.systemId(),
                        request.tenantId(),
                        request.recordId(),
                        record.logicalModuleId(),
                        request.instanceId(),
                        request.memberId(),
                        request.occurredAt(),
                        statusMapping);
            } catch (DataIntegrityViolationException exception) {
                throw stateConflict(exception);
            }
            return new RecordFlowState(request.instanceId(), FlowStatus.PENDING, 0, request.occurredAt());
        }

        var previous = existing.get();
        if (previous.status() == FlowStatus.PENDING) {
            if (previous.instanceId() == request.instanceId()
                    && Objects.equals(previous.statusMapping(), statusMapping)) {
                return previous.toState();
            }
            if (previous.instanceId() == request.instanceId()) {
                throw stateConflict();
            }
            throw alreadyPending();
        }
        if (projections.lockPendingAdditionalByRecord(
                request.systemId(), request.tenantId(), request.recordId())) {
            throw alreadyPending();
        }
        if (projections.lockAdditionalByInstance(
                request.systemId(), request.tenantId(), request.instanceId()).isPresent()) {
            throw stateConflict();
        }

        final int updated;
        try {
            updated = projections.replaceTerminal(
                    previous,
                    record.logicalModuleId(),
                    request.instanceId(),
                    request.memberId(),
                    request.occurredAt(),
                    statusMapping);
        } catch (DataIntegrityViolationException exception) {
            throw stateConflict(exception);
        }
        if (updated != 1) {
            throw stateConflict();
        }
        return new RecordFlowState(
                request.instanceId(),
                FlowStatus.PENDING,
                previous.version() + 1,
                request.occurredAt());
    }

    @Override
    @Transactional
    public RecordFlowState bindAdditional(AdditionalBindRequest request) {
        Objects.requireNonNull(request, "request");
        requireBindingAccess(
                request.systemId(),
                request.tenantId(),
                request.memberId(),
                request.effectivePermissions(),
                request.moduleCode(),
                request.recordId(),
                request.bindingSource());

        var record = projections.lockRecord(
                        request.systemId(), request.tenantId(), request.moduleCode(), request.recordId())
                .orElseThrow(RuntimeRecordFlowAdapter::recordNotActive);
        if (!bindingStatusAllowed(record.status(), request.bindingSource())) {
            throw recordNotActive();
        }
        var statusMapping = statusMappings.validateBinding(
                request.systemId(),
                request.tenantId(),
                request.recordId(),
                record.schemaVersionId(),
                record.moduleSnapshotId(),
                record.logicalModuleId(),
                request.moduleCode(),
                request.effectivePermissions(),
                request.recordStatusMapping());
        var primary = projections.lockByRecord(
                        request.systemId(), request.tenantId(), request.recordId())
                .orElseThrow(RuntimeRecordFlowAdapter::stateConflict);
        if (primary.status() != FlowStatus.PENDING
                || primary.instanceId() == request.instanceId()
                || projections.lockAdditionalByInstance(
                        request.systemId(), request.tenantId(), request.instanceId()).isPresent()) {
            throw stateConflict();
        }
        try {
            projections.insertAdditional(
                    request.systemId(),
                    request.tenantId(),
                    request.recordId(),
                    record.logicalModuleId(),
                    request.instanceId(),
                    request.eventKey(),
                    request.memberId(),
                    request.occurredAt(),
                    statusMapping);
        } catch (DataIntegrityViolationException exception) {
            throw stateConflict(exception);
        }
        return new RecordFlowState(
                request.instanceId(), FlowStatus.PENDING, 0, request.occurredAt());
    }

    @Override
    @Transactional
    public RecordFlowState transition(TransitionRequest request) {
        Objects.requireNonNull(request, "request");
        var primary = projections.lockByInstance(
                request.systemId(), request.tenantId(), request.instanceId());
        var previous = primary.orElseGet(() -> projections.lockAdditionalByInstance(
                        request.systemId(), request.tenantId(), request.instanceId())
                .orElseThrow(RuntimeRecordFlowAdapter::stateConflict));
        if (previous.status() != FlowStatus.PENDING) {
            throw stateConflict();
        }
        statusMappings.applyTerminal(
                previous.systemId(),
                previous.tenantId(),
                previous.recordId(),
                previous.logicalModuleId(),
                previous.statusMapping(),
                request.status(),
                request.actorMemberId(),
                request.occurredAt());
        final int updated;
        try {
            updated = primary.isPresent()
                    ? projections.transition(
                            previous, request.status(), request.actorMemberId(), request.occurredAt())
                    : projections.transitionAdditional(
                            previous, request.status(), request.actorMemberId(), request.occurredAt());
        } catch (DataIntegrityViolationException exception) {
            throw stateConflict(exception);
        }
        if (updated != 1) {
            throw stateConflict();
        }
        return new RecordFlowState(
                request.instanceId(),
                request.status(),
                previous.version() + 1,
                request.occurredAt());
    }

    private static boolean bindingStatusAllowed(String status, BindingSource source) {
        return switch (source) {
            case MANUAL, OPENAPI -> "ACTIVE".equals(status);
            case AUTOMATIC_EVENT -> AUTOMATIC_EVENT_RECORD_STATUSES.contains(status);
        };
    }

    private void requireBindingAccess(
            long systemId,
            long tenantId,
            long memberId,
            Set<String> effectivePermissions,
            String moduleCode,
            long recordId,
            BindingSource source
    ) {
        switch (source) {
            case MANUAL, OPENAPI -> access.requireView(
                    new RuntimeRecordAccessFacade.RuntimeRecordAccessRequest(
                            systemId,
                            tenantId,
                            memberId,
                            effectivePermissions,
                            moduleCode,
                            recordId));
            case AUTOMATIC_EVENT -> {
                if (!effectivePermissions.contains("system.runtime.access")
                        || !effectivePermissions.contains("module." + moduleCode + ".view")) {
                    throw new BusinessException(
                            "PERMISSION_DENIED",
                            "The current member cannot bind an automatic Flow event for this runtime module",
                            HttpStatus.FORBIDDEN);
                }
            }
        }
    }

    private static BusinessException recordNotActive() {
        return new BusinessException(
                "RECORD_FLOW_RECORD_NOT_ACTIVE",
                "Only an active runtime record can be bound to an approval instance",
                HttpStatus.CONFLICT);
    }

    private static BusinessException alreadyPending() {
        return new BusinessException(
                "RECORD_FLOW_ALREADY_PENDING",
                "The runtime record already has a pending approval instance",
                HttpStatus.CONFLICT);
    }

    private static BusinessException stateConflict() {
        return new BusinessException(
                "RECORD_FLOW_STATE_CONFLICT",
                "The record Flow state no longer permits this operation",
                HttpStatus.CONFLICT);
    }

    private static BusinessException stateConflict(Throwable cause) {
        var conflict = stateConflict();
        conflict.initCause(cause);
        return conflict;
    }
}
