package com.unique.examine.module.report.domain;

import java.util.List;

public record ReportCheckReport(
        long reportId,
        long draftVersion,
        SourceCapability source,
        List<Issue> issues
) {
    public ReportCheckReport {
        if (reportId <= 0 || draftVersion <= 0 || issues == null) {
            throw new IllegalArgumentException("Report check result is invalid");
        }
        issues = List.copyOf(issues);
    }

    public long blockerCount() {
        return issues.stream()
                .filter(issue -> issue.severity() == Severity.BLOCKER).count();
    }

    public long warningCount() {
        return issues.stream()
                .filter(issue -> issue.severity() == Severity.WARNING).count();
    }

    public boolean publishable() {
        return blockerCount() == 0;
    }

    public record SourceCapability(
            long dataSourceId,
            String dataSourceCode,
            String dataSourceName,
            long dataSourceVersionId,
            int dataSourceVersionNumber,
            long moduleId,
            String moduleCode,
            String schemaVersionId,
            List<FieldCapability> fields
    ) {
        public SourceCapability {
            if (dataSourceId <= 0 || dataSourceVersionId <= 0
                    || dataSourceVersionNumber <= 0 || moduleId <= 0
                    || fields == null) {
                throw new IllegalArgumentException(
                        "Report source capability is invalid");
            }
            dataSourceCode = text(dataSourceCode, "source code", 64);
            dataSourceName = text(dataSourceName, "source name", 200);
            moduleCode = text(moduleCode, "module code", 100);
            schemaVersionId = text(schemaVersionId, "schema version", 200);
            fields = List.copyOf(fields);
        }
    }

    public record FieldCapability(
            String logicalFieldId,
            String code,
            String name,
            String type,
            String queryType,
            boolean readable
    ) {
        public FieldCapability {
            logicalFieldId = text(logicalFieldId, "logical field id", 20);
            code = text(code, "field code", 64);
            name = text(name, "field name", 200);
            type = text(type, "field type", 100);
            queryType = text(queryType, "field query type", 100);
        }
    }

    public record Issue(
            Severity severity,
            String code,
            String path,
            String message
    ) {
        public Issue {
            if (severity == null) {
                throw new IllegalArgumentException(
                        "Report issue severity is required");
            }
            code = text(code, "issue code", 100);
            path = text(path, "issue path", 300);
            message = text(message, "issue message", 500);
        }
    }

    public enum Severity { BLOCKER, WARNING }

    private static String text(String value, String label, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException("Report " + label + " is invalid");
        }
        return value.strip();
    }
}
