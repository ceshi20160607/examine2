package com.unique.examine.core.ai;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Work-owner boundary for bounded, read-only task and daily-report context. */
public interface AiWorkQueryFacade {
    int MAX_LIMIT = 50;
    int MAX_REPORT_RANGE_DAYS = 366;

    TaskResult taskQuery(TaskRequest request);

    DailyReportResult dailyReportQuery(DailyReportRequest request);

    ProjectMetricsResult projectMetrics(ProjectMetricsRequest request);

    enum TaskStatus { ALL, OPEN, COMPLETED }

    enum TaskRole { PARTICIPATING, CREATED_BY_ME, ASSIGNED_TO_ME, ALL }

    enum ReportScope { SELF, ALL }

    enum ReportStatus { ALL, DRAFT, SUBMITTED }

    enum ProjectVisibility { ALL, PARTICIPATING }

    record ProjectMetricsRequest(
            long systemId,
            long tenantId,
            long memberId,
            Set<String> effectivePermissions,
            String projectId,
            LocalDate fromInclusive,
            LocalDate toExclusive
    ) {
        public ProjectMetricsRequest {
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(memberId, "memberId");
            effectivePermissions = permissions(effectivePermissions);
            projectId = positiveDecimal(projectId, "projectId");
            fromInclusive = Objects.requireNonNull(
                    fromInclusive, "fromInclusive");
            toExclusive = Objects.requireNonNull(
                    toExclusive, "toExclusive");
            var days = ChronoUnit.DAYS.between(
                    fromInclusive, toExclusive);
            if (days < 1 || days > 31) {
                throw new IllegalArgumentException(
                        "project metrics date window is invalid");
            }
        }
    }

    record ProjectMetricsResult(
            String projectId,
            String title,
            String status,
            Instant updatedAt,
            LocalDate fromInclusive,
            LocalDate toExclusive,
            ProjectVisibility visibility,
            long total,
            long open,
            long completed,
            Metric overdueOpen,
            Metric dueInRangeOpen,
            Metric completedInRange,
            List<DailyMetric> daily,
            List<AssigneeOpen> topAssignees
    ) {
        public ProjectMetricsResult {
            projectId = positiveDecimal(projectId, "projectId");
            title = requiredText(title, "title", 200);
            status = requiredText(status, "status", 32);
            updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
            fromInclusive = Objects.requireNonNull(
                    fromInclusive, "fromInclusive");
            toExclusive = Objects.requireNonNull(
                    toExclusive, "toExclusive");
            visibility = Objects.requireNonNull(visibility, "visibility");
            if (!toExclusive.isAfter(fromInclusive)
                    || ChronoUnit.DAYS.between(
                    fromInclusive, toExclusive) > 31
                    || total < 0 || open < 0 || completed < 0
                    || total != open + completed) {
                throw new IllegalArgumentException(
                        "project metrics result is invalid");
            }
            overdueOpen = Objects.requireNonNull(
                    overdueOpen, "overdueOpen");
            dueInRangeOpen = Objects.requireNonNull(
                    dueInRangeOpen, "dueInRangeOpen");
            completedInRange = Objects.requireNonNull(
                    completedInRange, "completedInRange");
            daily = List.copyOf(Objects.requireNonNull(daily, "daily"));
            topAssignees = List.copyOf(Objects.requireNonNull(
                    topAssignees, "topAssignees"));
            if (daily.size() > 31 || topAssignees.size() > 20) {
                throw new IllegalArgumentException(
                        "project metrics result is too large");
            }
        }
    }

    record Metric(long count, String route) {
        public Metric {
            if (count < 0) {
                throw new IllegalArgumentException("metric count is invalid");
            }
            route = requiredText(route, "route", 1_000);
        }
    }

    record DailyMetric(
            LocalDate date,
            long createdCount,
            long completedCount,
            String createdRoute,
            String completedRoute
    ) {
        public DailyMetric {
            date = Objects.requireNonNull(date, "date");
            if (createdCount < 0 || completedCount < 0) {
                throw new IllegalArgumentException(
                        "daily metric count is invalid");
            }
            createdRoute = requiredText(
                    createdRoute, "createdRoute", 1_000);
            completedRoute = requiredText(
                    completedRoute, "completedRoute", 1_000);
        }
    }

    record AssigneeOpen(
            String assigneeMemberId,
            long openCount,
            String route
    ) {
        public AssigneeOpen {
            assigneeMemberId = positiveDecimal(
                    assigneeMemberId, "assigneeMemberId");
            if (openCount <= 0) {
                throw new IllegalArgumentException(
                        "assignee open count is invalid");
            }
            route = requiredText(route, "route", 1_000);
        }
    }

    record TaskRequest(
            long systemId,
            long tenantId,
            long memberId,
            Set<String> effectivePermissions,
            String keyword,
            Long projectId,
            Instant dueFrom,
            Instant dueTo,
            TaskStatus status,
            TaskRole role,
            int limit
    ) {
        public TaskRequest {
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(memberId, "memberId");
            effectivePermissions = permissions(effectivePermissions);
            keyword = keyword == null ? "" : keyword.strip();
            if (keyword.codePointCount(0, keyword.length()) > 100) {
                throw new IllegalArgumentException("keyword is invalid");
            }
            if (projectId != null) positive(projectId, "projectId");
            if (dueFrom != null && dueTo != null && dueFrom.isAfter(dueTo)) {
                throw new IllegalArgumentException("due window is invalid");
            }
            status = Objects.requireNonNull(status, "status");
            role = Objects.requireNonNull(role, "role");
            requireLimit(limit);
        }
    }

    record DailyReportRequest(
            long systemId,
            long tenantId,
            long memberId,
            Set<String> effectivePermissions,
            ReportScope scope,
            String authorMemberId,
            LocalDate dateFrom,
            LocalDate dateTo,
            ReportStatus status,
            int limit
    ) {
        public DailyReportRequest {
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(memberId, "memberId");
            effectivePermissions = permissions(effectivePermissions);
            scope = Objects.requireNonNull(scope, "scope");
            if (authorMemberId != null) {
                authorMemberId = positiveDecimal(authorMemberId, "authorMemberId");
            }
            if (dateFrom != null && dateTo != null) {
                var days = ChronoUnit.DAYS.between(dateFrom, dateTo) + 1;
                if (days < 1 || days > MAX_REPORT_RANGE_DAYS) {
                    throw new IllegalArgumentException("report date window is invalid");
                }
            }
            status = Objects.requireNonNull(status, "status");
            requireLimit(limit);
        }
    }

    record TaskResult(long total, List<Task> tasks) {
        public TaskResult {
            if (total < 0) throw new IllegalArgumentException("total is invalid");
            tasks = bounded(tasks, "tasks");
        }
    }

    record Task(
            String taskId,
            long version,
            String title,
            String description,
            String status,
            String projectId,
            String creatorMemberId,
            String assigneeMemberId,
            Instant dueAt,
            Instant reminderAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        public Task {
            taskId = positiveDecimal(taskId, "taskId");
            positive(version, "version");
            title = requiredText(title, "title", 500);
            if (description != null
                    && description.codePointCount(0, description.length()) > 2_000) {
                throw new IllegalArgumentException("description is invalid");
            }
            status = requiredText(status, "status", 32);
            if (projectId != null) projectId = positiveDecimal(projectId, "projectId");
            creatorMemberId = positiveDecimal(creatorMemberId, "creatorMemberId");
            assigneeMemberId = positiveDecimal(assigneeMemberId, "assigneeMemberId");
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
            updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
            if (updatedAt.isBefore(createdAt)) {
                throw new IllegalArgumentException("task timestamps are invalid");
            }
        }
    }

    record DailyReportResult(long total, List<DailyReport> reports) {
        public DailyReportResult {
            if (total < 0) throw new IllegalArgumentException("total is invalid");
            reports = bounded(reports, "reports");
        }
    }

    record DailyReport(
            String reportId,
            long version,
            String authorMemberId,
            LocalDate workDate,
            String completedWork,
            String plannedWork,
            String blockers,
            String status,
            Instant createdAt,
            Instant updatedAt,
            Instant submittedAt
    ) {
        public DailyReport {
            reportId = positiveDecimal(reportId, "reportId");
            positive(version, "version");
            authorMemberId = positiveDecimal(authorMemberId, "authorMemberId");
            workDate = Objects.requireNonNull(workDate, "workDate");
            completedWork = requiredText(completedWork, "completedWork", 4_000);
            plannedWork = requiredText(plannedWork, "plannedWork", 4_000);
            if (blockers != null && blockers.codePointCount(0, blockers.length()) > 4_000) {
                throw new IllegalArgumentException("blockers are invalid");
            }
            status = requiredText(status, "status", 32);
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
            updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
            if (updatedAt.isBefore(createdAt)) {
                throw new IllegalArgumentException("report timestamps are invalid");
            }
        }
    }

    private static Set<String> permissions(Set<String> values) {
        if (values == null || values.stream().anyMatch(
                value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("effectivePermissions are invalid");
        }
        return Set.copyOf(values);
    }

    private static <T> List<T> bounded(List<T> values, String name) {
        values = List.copyOf(Objects.requireNonNull(values, name));
        if (values.size() > MAX_LIMIT) {
            throw new IllegalArgumentException(name + " are invalid");
        }
        return values;
    }

    private static String positiveDecimal(String value, String name) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) {
                throw new IllegalArgumentException(name + " is invalid");
            }
            return value;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(name + " is invalid", failure);
        }
    }

    private static String requiredText(String value, String name, int maximum) {
        if (value == null || value.isBlank()
                || value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static void positive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " is invalid");
    }

    private static void requireLimit(int value) {
        if (value < 1 || value > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be within 1..50");
        }
    }
}
