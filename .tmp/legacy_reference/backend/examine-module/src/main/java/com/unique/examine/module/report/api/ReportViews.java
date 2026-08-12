package com.unique.examine.module.report.api;

import java.util.List;

public final class ReportViews {
    private ReportViews() {
    }

    public record Draft(
            String dataSourceId,
            List<String> outputFieldCodes
    ) {
        public Draft {
            outputFieldCodes = List.copyOf(outputFieldCodes);
        }
    }

    public record Definition(
            String id,
            String systemId,
            String tenantId,
            String code,
            String name,
            String description,
            long draftVersion,
            String activeVersionId,
            Integer activeVersionNumber,
            String createdAt,
            String updatedAt,
            long version,
            Draft draft
    ) {
    }

    public record FieldCapability(
            String logicalFieldId,
            String code,
            String name,
            String type,
            String queryType,
            boolean readable
    ) {
    }

    public record SourceCapability(
            String dataSourceId,
            String dataSourceCode,
            String dataSourceName,
            String dataSourceVersionId,
            int dataSourceVersionNumber,
            String moduleId,
            String moduleCode,
            String schemaVersionId,
            List<FieldCapability> fields
    ) {
        public SourceCapability {
            fields = List.copyOf(fields);
        }
    }

    public record CheckIssue(
            String severity,
            String code,
            String path,
            String message
    ) {
    }

    public record CheckResult(
            String reportId,
            long checkedDraftVersion,
            boolean valid,
            long blockerCount,
            long warningCount,
            SourceCapability source,
            List<CheckIssue> issues
    ) {
        public CheckResult {
            issues = List.copyOf(issues);
        }
    }

    public record FieldPin(
            String logicalFieldId,
            String code,
            String name,
            String type,
            String queryType
    ) {
    }

    public record Version(
            String id,
            String reportId,
            int versionNumber,
            long sourceDraftVersion,
            String code,
            String name,
            String description,
            String dataSourceId,
            String dataSourceCode,
            String dataSourceName,
            String dataSourceVersionId,
            int dataSourceVersionNumber,
            String moduleId,
            String moduleCode,
            String schemaVersionId,
            List<FieldPin> fields,
            String fingerprint,
            String publishedBy,
            String publishedAt,
            boolean active
    ) {
        public Version {
            fields = List.copyOf(fields);
        }
    }

    public record PublishResult(Definition report, Version version) {
    }
}
