package com.unique.examine.module.runtime.query;

import java.util.HashSet;
import java.util.List;

/**
 * Opaque, already-authorized native record predicate prepared by the ordinary
 * record-query chain. Callers may add aggregate projections but must not alter
 * the frozen FROM/WHERE identity or its ordered arguments.
 */
public record NativeRecordAggregatePlan(
        String queryIdentity,
        String moduleCode,
        long systemId,
        long tenantId,
        long schemaVersionId,
        long moduleSnapshotId,
        long logicalModuleId,
        String whereSql,
        List<Object> whereArguments,
        List<Field> readableFields
) {
    public NativeRecordAggregatePlan {
        if (queryIdentity == null
                || !queryIdentity.matches("^[0-9a-f]{64}$")
                || moduleCode == null
                || !moduleCode.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")
                || systemId <= 0 || tenantId <= 0 || schemaVersionId <= 0
                || moduleSnapshotId <= 0 || logicalModuleId <= 0
                || whereSql == null || whereSql.isBlank()
                || whereArguments == null || readableFields == null) {
            throw new IllegalArgumentException(
                    "Native aggregate plan is incomplete");
        }
        whereArguments = List.copyOf(whereArguments);
        readableFields = List.copyOf(readableFields);
        var codes = new HashSet<String>();
        if (readableFields.stream().anyMatch(field -> !codes.add(field.code()))) {
            throw new IllegalArgumentException(
                    "Native aggregate plan contains duplicate field codes");
        }
    }

    public Field field(String code) {
        return readableFields.stream()
                .filter(field -> field.code().equals(code))
                .findFirst()
                .orElse(null);
    }

    public record Field(long logicalFieldId, String code, String queryType) {
        public Field {
            if (logicalFieldId <= 0 || code == null
                    || !code.matches("^[A-Za-z][A-Za-z0-9_-]{0,63}$")
                    || queryType == null || queryType.isBlank()) {
                throw new IllegalArgumentException(
                        "Native aggregate field is invalid");
            }
        }
    }
}
