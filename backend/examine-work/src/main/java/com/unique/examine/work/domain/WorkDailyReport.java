package com.unique.examine.work.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public record WorkDailyReport(
        long id,
        long systemId,
        long tenantId,
        long authorMemberId,
        LocalDate workDate,
        String completedWork,
        String plannedWork,
        String blockers,
        Status status,
        Instant createdAt,
        Instant updatedAt,
        Instant submittedAt,
        long version
) {
    public static final int MAX_NARRATIVE_CHARACTERS = 4_000;

    public WorkDailyReport {
        if (id <= 0 || systemId <= 0 || tenantId <= 0
                || authorMemberId <= 0) {
            throw invalid("WORK_REPORT_IDENTITY_INVALID",
                    "Report identity and scope values must be positive");
        }
        Objects.requireNonNull(workDate, "workDate");
        completedWork = narrative(completedWork, "completedWork", false);
        plannedWork = narrative(plannedWork, "plannedWork", false);
        blockers = narrative(blockers, "blockers", true);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt) || version <= 0
                || status == Status.DRAFT && submittedAt != null
                || status == Status.SUBMITTED
                && (submittedAt == null || submittedAt.isBefore(createdAt)
                || submittedAt.isAfter(updatedAt))) {
            throw invalid("WORK_REPORT_STATE_INVALID",
                    "Report timestamps, status or version are inconsistent");
        }
    }

    public static WorkDailyReport draft(
            long id,
            long systemId,
            long tenantId,
            long authorMemberId,
            LocalDate workDate,
            String completedWork,
            String plannedWork,
            String blockers,
            LocalDate businessDate,
            Instant now
    ) {
        requireNotFuture(workDate, businessDate);
        return new WorkDailyReport(
                id, systemId, tenantId, authorMemberId, workDate,
                completedWork, plannedWork, blockers, Status.DRAFT,
                now, now, null, 1);
    }

    public WorkDailyReport revise(
            String nextCompletedWork,
            String nextPlannedWork,
            String nextBlockers,
            LocalDate businessDate,
            Instant now
    ) {
        if (status != Status.DRAFT) {
            throw invalid("WORK_REPORT_STATE_INVALID",
                    "Only a draft report can be revised");
        }
        requireNotFuture(workDate, businessDate);
        return new WorkDailyReport(
                id, systemId, tenantId, authorMemberId, workDate,
                nextCompletedWork, nextPlannedWork, nextBlockers, status,
                createdAt, nextTime(now), null, version + 1);
    }

    public WorkDailyReport submit(Instant now) {
        if (status == Status.SUBMITTED) {
            return this;
        }
        var occurredAt = nextTime(now);
        return new WorkDailyReport(
                id, systemId, tenantId, authorMemberId, workDate,
                completedWork, plannedWork, blockers, Status.SUBMITTED,
                createdAt, occurredAt, occurredAt, version + 1);
    }

    public WorkDailyReport reopen(Instant now) {
        if (status == Status.DRAFT) {
            return this;
        }
        return new WorkDailyReport(
                id, systemId, tenantId, authorMemberId, workDate,
                completedWork, plannedWork, blockers, Status.DRAFT,
                createdAt, nextTime(now), null, version + 1);
    }

    public static void requireNotFuture(
            LocalDate workDate, LocalDate businessDate
    ) {
        Objects.requireNonNull(workDate, "workDate");
        Objects.requireNonNull(businessDate, "businessDate");
        if (workDate.isAfter(businessDate)) {
            throw invalid("WORK_REPORT_DATE_INVALID",
                    "workDate cannot be in the future");
        }
    }

    private Instant nextTime(Instant now) {
        Objects.requireNonNull(now, "now");
        if (now.isBefore(updatedAt)) {
            throw invalid("WORK_REPORT_STATE_INVALID",
                    "Report update time cannot move backwards");
        }
        return now;
    }

    private static String narrative(
            String value, String field, boolean optional
    ) {
        if (value == null) {
            if (optional) {
                return null;
            }
            throw invalid("WORK_REPORT_CONTENT_INVALID",
                    field + " is required");
        }
        value = value.strip();
        if (optional && value.isEmpty()) {
            return null;
        }
        var characters = value.codePointCount(0, value.length());
        if (characters < 1 || characters > MAX_NARRATIVE_CHARACTERS) {
            throw invalid("WORK_REPORT_CONTENT_INVALID",
                    field + " must contain 1 to "
                            + MAX_NARRATIVE_CHARACTERS + " characters");
        }
        return value;
    }

    private static WorkDomainException invalid(String code, String message) {
        return new WorkDomainException(code, message);
    }

    public enum Status {
        DRAFT,
        SUBMITTED
    }
}
