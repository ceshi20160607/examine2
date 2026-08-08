package com.unique.examine.module.dashboard.domain;

import java.util.List;

public record DashboardCheckReport(
        long dashboardId,
        long draftVersion,
        List<Issue> issues
) {
    public DashboardCheckReport {
        if (dashboardId <= 0 || draftVersion <= 0 || issues == null) {
            throw new IllegalArgumentException(
                    "Dashboard check report is invalid");
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

    public record Issue(
            Severity severity,
            String code,
            String path,
            String message
    ) {
        public Issue {
            if (severity == null) {
                throw new IllegalArgumentException(
                        "Dashboard issue severity is required");
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
                    "Dashboard issue " + name + " is invalid");
        }
        return value.strip();
    }
}
