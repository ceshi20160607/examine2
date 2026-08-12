package com.unique.examine.module.kpi.domain;

import java.util.List;

public record KpiCheckReport(
        long kpiId,
        long draftVersion,
        List<Issue> issues
) {
    public KpiCheckReport {
        if (kpiId <= 0 || draftVersion <= 0 || issues == null) {
            throw new IllegalArgumentException("KPI check report is invalid");
        }
        issues = List.copyOf(issues);
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

    public enum Severity { BLOCKER, WARNING }

    public record Issue(
            Severity severity,
            String code,
            String path,
            String message
    ) {
        public Issue {
            if (severity == null) {
                throw new IllegalArgumentException(
                        "KPI issue severity is required");
            }
            code = text(code, "code", 100);
            path = text(path, "path", 300);
            message = text(message, "message", 500);
        }

        private static String text(String value, String name, int max) {
            if (value == null || value.isBlank() || value.length() > max) {
                throw new IllegalArgumentException(
                        "KPI issue " + name + " is invalid");
            }
            return value.strip();
        }
    }
}
