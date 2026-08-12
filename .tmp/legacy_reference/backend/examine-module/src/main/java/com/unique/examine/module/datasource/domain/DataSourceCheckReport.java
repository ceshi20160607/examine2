package com.unique.examine.module.datasource.domain;

import java.util.List;

public record DataSourceCheckReport(
        long dataSourceId,
        long draftVersion,
        String schemaVersionId,
        List<Issue> issues
) {
    public DataSourceCheckReport {
        if (dataSourceId <= 0 || draftVersion <= 0 || issues == null) {
            throw new IllegalArgumentException(
                    "Data source check report is invalid");
        }
        issues = List.copyOf(issues);
        if (schemaVersionId != null) {
            schemaVersionId = schemaVersionId.strip();
            if (schemaVersionId.isEmpty() || schemaVersionId.length() > 200) {
                throw new IllegalArgumentException(
                        "Data source schema version is invalid");
            }
        }
    }

    public long blockerCount() {
        return issues.stream()
                .filter(issue -> issue.severity() == Severity.BLOCKER)
                .count();
    }

    public long warningCount() {
        return issues.stream()
                .filter(issue -> issue.severity() == Severity.WARNING)
                .count();
    }

    public boolean publishable() {
        return blockerCount() == 0;
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
                        "Data source issue severity is required");
            }
            code = text(code, "code", 100);
            path = text(path, "path", 300);
            message = text(message, "message", 500);
        }
    }

    public enum Severity { BLOCKER, WARNING }

    private static String text(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(
                    "Data source issue " + name + " is invalid");
        }
        return value.strip();
    }
}
