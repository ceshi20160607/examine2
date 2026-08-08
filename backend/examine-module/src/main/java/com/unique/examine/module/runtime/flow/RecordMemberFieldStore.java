package com.unique.examine.module.runtime.flow;

import java.util.List;
import java.util.Optional;

interface RecordMemberFieldStore {

    Optional<PublishedSchema> activeSchema(long systemId, String moduleCode);

    List<ProjectedField> projectedFields(
            long systemId,
            long schemaVersionId,
            long moduleSnapshotId,
            long logicalModuleId
    );

    Optional<LockedRecord> lockRecord(
            long systemId,
            long tenantId,
            String moduleCode,
            long recordId
    );

    Optional<ProjectedField> projectedField(
            long systemId,
            long schemaVersionId,
            long moduleSnapshotId,
            long logicalModuleId,
            long fieldId
    );

    List<ValueRow> lockValues(
            long systemId,
            long tenantId,
            long recordId,
            long schemaVersionId,
            long moduleSnapshotId,
            long logicalModuleId,
            long fieldId
    );

    record PublishedSchema(
            long systemId,
            long schemaVersionId,
            long moduleSnapshotId,
            long logicalModuleId,
            String moduleCode,
            String configSnapshotJson
    ) {
    }

    record LockedRecord(
            long systemId,
            long tenantId,
            long recordId,
            long schemaVersionId,
            long moduleSnapshotId,
            long logicalModuleId,
            String moduleCode,
            String configSnapshotJson
    ) {
    }

    record ProjectedField(
            long fieldSnapshotId,
            long sourceFieldId,
            long logicalFieldId,
            String fieldCode,
            String fieldName,
            String fieldType,
            String fieldScope,
            String propertyJson
    ) {
    }

    record ValueRow(
            int ordinal,
            String fieldType,
            String fieldScope,
            Long referenceValue
    ) {
    }
}
