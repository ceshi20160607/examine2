package com.unique.examine.core.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Narrow read-side port for selecting and resolving published single-value
 * record MEMBER fields.
 *
 * <p>The field id is the stable published record field identity
 * ({@code logical_field_id}, currently also {@code field_snapshot_id}).
 * Implementations must scope persisted reads by the complete
 * system/tenant/module/record tuple and must validate the resolved member
 * against the active tenant directory.</p>
 */
public interface RuntimeRecordMemberFieldFacade {

    Optional<PublishedFieldCatalog> publishedEligibleFields(
            long systemId,
            String moduleCode
    );

    Resolution resolveCurrent(CurrentRecordRequest request);

    Resolution resolveSnapshot(SnapshotRequest request);

    record PublishedFieldCatalog(
            long systemId,
            long schemaVersionId,
            long moduleSnapshotId,
            long logicalModuleId,
            String moduleCode,
            List<EligibleField> fields
    ) {
        public PublishedFieldCatalog {
            requirePositive(systemId, "systemId");
            requirePositive(schemaVersionId, "schemaVersionId");
            requirePositive(moduleSnapshotId, "moduleSnapshotId");
            requirePositive(logicalModuleId, "logicalModuleId");
            requireModuleCode(moduleCode);
            if (fields == null || fields.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Published member field catalog is invalid");
            }
            fields = List.copyOf(fields);
        }
    }

    record EligibleField(
            long fieldId,
            String fieldCode,
            String fieldName
    ) {
        public EligibleField {
            requirePositive(fieldId, "fieldId");
            if (fieldCode == null || !fieldCode.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
                throw new IllegalArgumentException("Published member field code is invalid");
            }
            if (fieldName == null || fieldName.isBlank()) {
                throw new IllegalArgumentException("Published member field name is invalid");
            }
            fieldName = fieldName.strip();
        }
    }

    record CurrentRecordRequest(
            long systemId,
            long tenantId,
            String moduleCode,
            long recordId,
            long fieldId
    ) {
        public CurrentRecordRequest {
            requirePositive(systemId, "systemId");
            requirePositive(tenantId, "tenantId");
            requireModuleCode(moduleCode);
            requirePositive(recordId, "recordId");
            requirePositive(fieldId, "fieldId");
        }
    }

    record SnapshotRequest(
            long systemId,
            long tenantId,
            String moduleCode,
            long fieldId,
            Map<String, String> valuesJson
    ) {
        public SnapshotRequest {
            requirePositive(systemId, "systemId");
            requirePositive(tenantId, "tenantId");
            requireModuleCode(moduleCode);
            requirePositive(fieldId, "fieldId");
            if (valuesJson == null) {
                throw new IllegalArgumentException("Member field value snapshot is required");
            }
            valuesJson = Collections.unmodifiableMap(new LinkedHashMap<>(valuesJson));
        }
    }

    record Resolution(
            Status status,
            Long memberId
    ) {
        public Resolution {
            Objects.requireNonNull(status, "status");
            if (status == Status.RESOLVED) {
                if (memberId == null || memberId <= 0) {
                    throw new IllegalArgumentException(
                            "Resolved member field result requires a positive memberId");
                }
            } else if (memberId != null) {
                throw new IllegalArgumentException(
                        "Unresolved member field result must not expose a memberId");
            }
        }

        public static Resolution resolved(long memberId) {
            return new Resolution(Status.RESOLVED, memberId);
        }

        public static Resolution sourceMissing() {
            return new Resolution(Status.SOURCE_MISSING, null);
        }

        public static Resolution sourceEmpty() {
            return new Resolution(Status.SOURCE_EMPTY, null);
        }

        public static Resolution sourceInvalid() {
            return new Resolution(Status.SOURCE_INVALID, null);
        }

        public static Resolution memberInactive() {
            return new Resolution(Status.MEMBER_INACTIVE, null);
        }
    }

    enum Status {
        RESOLVED,
        SOURCE_MISSING,
        SOURCE_EMPTY,
        SOURCE_INVALID,
        MEMBER_INACTIVE
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireModuleCode(String moduleCode) {
        if (moduleCode == null || !moduleCode.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw new IllegalArgumentException("Runtime module code is invalid");
        }
    }
}
