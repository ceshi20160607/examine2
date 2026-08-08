package com.unique.examine.module.report.domain;

public record PublishedReport(ReportDefinition root, ReportVersion version) {
    public PublishedReport {
        if (root == null || version == null
                || root.id() != version.reportId()
                || root.systemId() != version.systemId()
                || root.tenantId() != version.tenantId()
                || !root.code().equals(version.code())) {
            throw new IllegalArgumentException(
                    "Published report identity is inconsistent");
        }
    }

    public static PublishedReport active(
            ReportDefinition root,
            ReportVersion version
    ) {
        var result = new PublishedReport(root, version);
        if (root.activeVersionId() == null
                || root.activeVersionId() != version.id()
                || root.activeVersionNumber() == null
                || root.activeVersionNumber() != version.versionNumber()) {
            throw new IllegalArgumentException(
                    "Published report active pointer is inconsistent");
        }
        return result;
    }
}
