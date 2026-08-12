package com.unique.examine.module.report.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record ReportDefinition(
        long id,
        long systemId,
        long tenantId,
        String code,
        String name,
        String description,
        ReportDraft draft,
        long draftVersion,
        Long activeVersionId,
        Integer activeVersionNumber,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    private static final Pattern CODE =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");

    public ReportDefinition {
        if (id <= 0 || systemId <= 0 || tenantId <= 0 || draft == null
                || draftVersion <= 0 || createdAt == null || updatedAt == null
                || updatedAt.isBefore(createdAt) || version <= 0) {
            throw invalid("Report definition state is incomplete");
        }
        code = code(code);
        name = name(name);
        description = description(description);
        if ((activeVersionId == null) != (activeVersionNumber == null)
                || activeVersionId != null && activeVersionId <= 0
                || activeVersionNumber != null && activeVersionNumber <= 0) {
            throw invalid("Report active version pointer is invalid");
        }
    }

    public static ReportDefinition create(
            long id,
            long systemId,
            long tenantId,
            String code,
            String name,
            String description,
            ReportDraft draft,
            Instant now
    ) {
        return new ReportDefinition(id, systemId, tenantId, code, name,
                description, draft, 1, null, null, now, now, 1);
    }

    public ReportDefinition reviseDraft(
            String nextName,
            String nextDescription,
            ReportDraft nextDraft,
            Instant now
    ) {
        if (nextDraft == null || now == null || now.isBefore(updatedAt)) {
            throw invalid("Report draft revision is invalid");
        }
        return new ReportDefinition(id, systemId, tenantId, code, nextName,
                nextDescription, nextDraft, draftVersion + 1,
                activeVersionId, activeVersionNumber, createdAt, now,
                version + 1);
    }

    public ReportDefinition activate(ReportVersion published, Instant now) {
        if (published == null || published.reportId() != id
                || published.systemId() != systemId
                || published.tenantId() != tenantId || now == null
                || !published.code().equals(code)
                || !published.name().equals(name)
                || !Objects.equals(published.description(), description)
                || published.sourceDraftVersion() != draftVersion
                || now.isBefore(updatedAt)
                || published.versionNumber() != (activeVersionNumber == null
                ? 1 : activeVersionNumber + 1)) {
            throw invalid("Report publication pointer is invalid");
        }
        return new ReportDefinition(id, systemId, tenantId, code, name,
                description, draft, draftVersion, published.id(),
                published.versionNumber(), createdAt, now, version + 1);
    }

    public static String code(String value) {
        if (value == null || !CODE.matcher(value).matches()) {
            throw invalid("Report code is invalid");
        }
        return value;
    }

    public static String name(String value) {
        if (value == null || value.isBlank()
                || value.codePointCount(0, value.length()) > 200) {
            throw invalid("Report name must contain 1 to 200 characters");
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
            throw invalid("Report description cannot exceed 2000 characters");
        }
        return value;
    }

    private static ReportException invalid(String message) {
        return new ReportException("REPORT_INVALID", message);
    }
}
