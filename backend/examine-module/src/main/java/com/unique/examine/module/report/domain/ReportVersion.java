package com.unique.examine.module.report.domain;

import java.time.Instant;

public record ReportVersion(
        long id,
        long reportId,
        long systemId,
        long tenantId,
        int versionNumber,
        long sourceDraftVersion,
        String code,
        String name,
        String description,
        ReportSourcePin source,
        String fingerprint,
        long publishedByMemberId,
        Instant publishedAt
) {
    public ReportVersion {
        if (id <= 0 || reportId <= 0 || systemId <= 0 || tenantId <= 0
                || versionNumber <= 0 || sourceDraftVersion <= 0
                || source == null || publishedByMemberId <= 0
                || publishedAt == null) {
            throw invalid("Published report state is incomplete");
        }
        code = ReportDefinition.code(code);
        name = ReportDefinition.name(name);
        description = ReportDefinition.description(description);
        if (fingerprint == null || !fingerprint.matches("^[0-9a-f]{64}$")) {
            throw invalid("Published report fingerprint is invalid");
        }
    }

    private static ReportException invalid(String message) {
        return new ReportException("REPORT_VERSION_INVALID", message);
    }
}
