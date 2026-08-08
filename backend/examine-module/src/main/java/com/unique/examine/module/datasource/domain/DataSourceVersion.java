package com.unique.examine.module.datasource.domain;

import java.time.Instant;

public record DataSourceVersion(
        long id,
        long dataSourceId,
        long systemId,
        long tenantId,
        int versionNumber,
        String code,
        long moduleId,
        String moduleCode,
        String schemaVersionId,
        String name,
        String description,
        DataSourceDraft snapshot,
        String fingerprint,
        long publishedByMemberId,
        Instant publishedAt
) {
    public DataSourceVersion {
        if (id <= 0 || dataSourceId <= 0 || systemId <= 0 || tenantId <= 0
                || versionNumber <= 0 || moduleId <= 0
                || publishedByMemberId <= 0 || publishedAt == null
                || snapshot == null) {
            throw invalid("Published data source state is incomplete");
        }
        code = ModuleDataSource.code(code);
        name = ModuleDataSource.name(name);
        description = ModuleDataSource.description(description);
        moduleCode = text(moduleCode, "moduleCode", 100);
        schemaVersionId = text(schemaVersionId, "schemaVersionId", 200);
        if (fingerprint == null
                || !fingerprint.matches("^[0-9a-f]{64}$")) {
            throw invalid("Published data source fingerprint is invalid");
        }
    }

    private static String text(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw invalid(name + " is invalid");
        }
        return value.strip();
    }

    private static DataSourceException invalid(String message) {
        return new DataSourceException("DATA_SOURCE_VERSION_INVALID", message);
    }
}
