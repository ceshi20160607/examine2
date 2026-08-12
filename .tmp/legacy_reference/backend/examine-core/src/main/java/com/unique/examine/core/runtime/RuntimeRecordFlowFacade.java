package com.unique.examine.core.runtime;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Narrow cross-module port for binding approval instances to runtime records and projecting
 * approval status back to the record owner.
 */
public interface RuntimeRecordFlowFacade {

    RecordFlowState bind(BindRequest request);

    RecordFlowState bindAdditional(AdditionalBindRequest request);

    RecordFlowState transition(TransitionRequest request);

    enum FlowStatus {
        PENDING,
        APPROVED,
        REJECTED,
        WITHDRAWN,
        TERMINATED
    }

    enum BindingSource {
        MANUAL,
        OPENAPI,
        AUTOMATIC_EVENT
    }

    record RecordStatusMapping(
            String fieldCode,
            String approvedValue,
            String rejectedValue,
            String withdrawnValue,
            String terminatedValue
    ) {
        public RecordStatusMapping {
            if (fieldCode == null || !fieldCode.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
                throw new IllegalArgumentException("Record Flow status mapping field code is invalid");
            }
            approvedValue = requireOption(approvedValue);
            rejectedValue = requireOption(rejectedValue);
            withdrawnValue = requireOption(withdrawnValue);
            terminatedValue = requireOption(terminatedValue);
        }

        public String valueFor(FlowStatus status) {
            Objects.requireNonNull(status, "status");
            return switch (status) {
                case APPROVED -> approvedValue;
                case REJECTED -> rejectedValue;
                case WITHDRAWN -> withdrawnValue;
                case TERMINATED -> terminatedValue;
                case PENDING -> throw new IllegalArgumentException(
                        "Record Flow status mapping requires a terminal status");
            };
        }

        private static String requireOption(String value) {
            if (value == null || !value.matches("^[1-9][0-9]{0,18}$")) {
                throw new IllegalArgumentException("Record Flow status mapping option is invalid");
            }
            return value;
        }
    }

    record AdditionalBindRequest(
            long systemId,
            long tenantId,
            long memberId,
            Set<String> effectivePermissions,
            String moduleCode,
            long recordId,
            long instanceId,
            Instant occurredAt,
            String eventKey,
            RecordStatusMapping recordStatusMapping,
            BindingSource bindingSource
    ) {
        public AdditionalBindRequest(
                long systemId,
                long tenantId,
                long memberId,
                Set<String> effectivePermissions,
                String moduleCode,
                long recordId,
                long instanceId,
                Instant occurredAt,
                String eventKey
        ) {
            this(systemId, tenantId, memberId, effectivePermissions, moduleCode, recordId,
                    instanceId, occurredAt, eventKey, null, BindingSource.MANUAL);
        }

        public AdditionalBindRequest(
                long systemId,
                long tenantId,
                long memberId,
                Set<String> effectivePermissions,
                String moduleCode,
                long recordId,
                long instanceId,
                Instant occurredAt,
                String eventKey,
                RecordStatusMapping recordStatusMapping
        ) {
            this(systemId, tenantId, memberId, effectivePermissions, moduleCode, recordId,
                    instanceId, occurredAt, eventKey, recordStatusMapping, BindingSource.MANUAL);
        }

        public AdditionalBindRequest(
                long systemId,
                long tenantId,
                long memberId,
                Set<String> effectivePermissions,
                String moduleCode,
                long recordId,
                long instanceId,
                Instant occurredAt,
                String eventKey,
                BindingSource bindingSource
        ) {
            this(systemId, tenantId, memberId, effectivePermissions, moduleCode, recordId,
                    instanceId, occurredAt, eventKey, null, bindingSource);
        }

        public AdditionalBindRequest {
            var binding = new BindRequest(
                    systemId,
                    tenantId,
                    memberId,
                    effectivePermissions,
                    moduleCode,
                    recordId,
                    instanceId,
                    occurredAt,
                    recordStatusMapping,
                    bindingSource);
            effectivePermissions = binding.effectivePermissions();
            bindingSource = binding.bindingSource();
            if (eventKey == null) {
                throw new IllegalArgumentException("Additional record Flow event key is invalid");
            }
            eventKey = eventKey.strip();
            var length = eventKey.codePointCount(0, eventKey.length());
            if (length < 1 || length > 200) {
                throw new IllegalArgumentException("Additional record Flow event key is invalid");
            }
        }
    }

    record BindRequest(
            long systemId,
            long tenantId,
            long memberId,
            Set<String> effectivePermissions,
            String moduleCode,
            long recordId,
            long instanceId,
            Instant occurredAt,
            RecordStatusMapping recordStatusMapping,
            BindingSource bindingSource
    ) {
        public BindRequest(
                long systemId,
                long tenantId,
                long memberId,
                Set<String> effectivePermissions,
                String moduleCode,
                long recordId,
                long instanceId,
                Instant occurredAt
        ) {
            this(systemId, tenantId, memberId, effectivePermissions, moduleCode, recordId,
                    instanceId, occurredAt, null, BindingSource.MANUAL);
        }

        public BindRequest(
                long systemId,
                long tenantId,
                long memberId,
                Set<String> effectivePermissions,
                String moduleCode,
                long recordId,
                long instanceId,
                Instant occurredAt,
                RecordStatusMapping recordStatusMapping
        ) {
            this(systemId, tenantId, memberId, effectivePermissions, moduleCode, recordId,
                    instanceId, occurredAt, recordStatusMapping, BindingSource.MANUAL);
        }

        public BindRequest(
                long systemId,
                long tenantId,
                long memberId,
                Set<String> effectivePermissions,
                String moduleCode,
                long recordId,
                long instanceId,
                Instant occurredAt,
                BindingSource bindingSource
        ) {
            this(systemId, tenantId, memberId, effectivePermissions, moduleCode, recordId,
                    instanceId, occurredAt, null, bindingSource);
        }

        public BindRequest {
            if (systemId <= 0 || tenantId <= 0 || memberId <= 0 || recordId <= 0 || instanceId <= 0) {
                throw new IllegalArgumentException("Record Flow binding IDs must be positive");
            }
            if (moduleCode == null || !moduleCode.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
                throw new IllegalArgumentException("Record Flow binding module code is invalid");
            }
            if (effectivePermissions == null
                    || effectivePermissions.stream().anyMatch(
                    permission -> permission == null || permission.isBlank())) {
                throw new IllegalArgumentException("Record Flow binding permissions are invalid");
            }
            effectivePermissions = Set.copyOf(effectivePermissions);
            Objects.requireNonNull(occurredAt, "occurredAt");
            Objects.requireNonNull(bindingSource, "bindingSource");
        }
    }

    record TransitionRequest(
            long systemId,
            long tenantId,
            long instanceId,
            FlowStatus status,
            long actorMemberId,
            Instant occurredAt
    ) {
        public TransitionRequest {
            if (systemId <= 0 || tenantId <= 0 || instanceId <= 0 || actorMemberId <= 0) {
                throw new IllegalArgumentException("Record Flow transition IDs must be positive");
            }
            Objects.requireNonNull(status, "status");
            if (status == FlowStatus.PENDING) {
                throw new IllegalArgumentException("Record Flow transition status must be terminal");
            }
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    record RecordFlowState(
            long instanceId,
            FlowStatus status,
            long version,
            Instant updatedAt
    ) {
        public RecordFlowState {
            if (instanceId <= 0) {
                throw new IllegalArgumentException("Record Flow state instance ID must be positive");
            }
            Objects.requireNonNull(status, "status");
            if (version < 0) {
                throw new IllegalArgumentException("Record Flow state version must not be negative");
            }
            Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }
}
