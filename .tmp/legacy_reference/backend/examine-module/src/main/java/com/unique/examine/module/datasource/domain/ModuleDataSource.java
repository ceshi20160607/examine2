package com.unique.examine.module.datasource.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record ModuleDataSource(
        long id,
        long systemId,
        long tenantId,
        String code,
        long moduleId,
        String name,
        String description,
        DataSourceDraft draft,
        long draftVersion,
        Long activeVersionId,
        Integer activeVersionNumber,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    private static final Pattern CODE =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");

    public ModuleDataSource {
        if (id <= 0 || systemId <= 0 || tenantId <= 0 || moduleId <= 0
                || draftVersion <= 0 || version <= 0 || draft == null
                || createdAt == null || updatedAt == null
                || updatedAt.isBefore(createdAt)) {
            throw invalid("Data source root state is incomplete");
        }
        code = code(code);
        name = name(name);
        description = description(description);
        if ((activeVersionId == null) != (activeVersionNumber == null)
                || activeVersionId != null && activeVersionId <= 0
                || activeVersionNumber != null && activeVersionNumber <= 0) {
            throw invalid("Data source active version pointer is invalid");
        }
    }

    public static ModuleDataSource create(
            long id,
            long systemId,
            long tenantId,
            String code,
            long moduleId,
            String name,
            String description,
            DataSourceDraft draft,
            Instant now
    ) {
        return new ModuleDataSource(
                id, systemId, tenantId, code, moduleId,
                name, description, draft, 1,
                null, null, now, now, 1);
    }

    public ModuleDataSource reviseDraft(
            String nextName,
            String nextDescription,
            DataSourceDraft nextDraft,
            Instant now
    ) {
        if (nextDraft == null || now == null || now.isBefore(updatedAt)) {
            throw invalid("Data source draft revision is invalid");
        }
        return new ModuleDataSource(
                id, systemId, tenantId, code, moduleId,
                nextName, nextDescription, nextDraft,
                draftVersion + 1, activeVersionId, activeVersionNumber,
                createdAt, now, version + 1);
    }

    public ModuleDataSource activate(
            DataSourceVersion published,
            Instant now
    ) {
        if (published == null || published.dataSourceId() != id
                || published.systemId() != systemId
                || published.tenantId() != tenantId || now == null
                || !published.code().equals(code)
                || published.moduleId() != moduleId
                || !published.name().equals(name)
                || !Objects.equals(published.description(), description)
                || now.isBefore(updatedAt)
                || published.versionNumber()
                != (activeVersionNumber == null
                ? 1 : activeVersionNumber + 1)) {
            throw invalid("Data source publication pointer is invalid");
        }
        return new ModuleDataSource(
                id, systemId, tenantId, code, moduleId,
                name, description, draft, draftVersion,
                published.id(), published.versionNumber(),
                createdAt, now, version + 1);
    }

    public static String code(String value) {
        if (value == null || !CODE.matcher(value).matches()) {
            throw invalid("Data source code is invalid");
        }
        return value;
    }

    public static String name(String value) {
        if (value == null || value.isBlank()
                || value.codePointCount(0, value.length()) > 200) {
            throw invalid("Data source name must contain 1 to 200 characters");
        }
        return value.strip();
    }

    public static String description(String value) {
        if (value == null) {
            return null;
        }
        value = value.strip();
        if (value.isEmpty()) {
            return null;
        }
        if (value.codePointCount(0, value.length()) > 2_000) {
            throw invalid(
                    "Data source description cannot exceed 2000 characters");
        }
        return value;
    }

    private static DataSourceException invalid(String message) {
        return new DataSourceException("DATA_SOURCE_INVALID", message);
    }
}
