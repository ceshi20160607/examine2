package com.unique.examine.work.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public record WorkDailyReportQuery(
        Scope scope,
        Long authorMemberId,
        LocalDate dateFrom,
        LocalDate dateTo,
        StatusFilter status,
        int page,
        int size
) {
    public static final int MAX_RANGE_DAYS = 366;
    public static final int MAX_SIZE = 100;

    public WorkDailyReportQuery {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(dateFrom, "dateFrom");
        Objects.requireNonNull(dateTo, "dateTo");
        Objects.requireNonNull(status, "status");
        if (authorMemberId != null && authorMemberId <= 0) {
            throw invalid("authorMemberId must be positive");
        }
        var rangeDays = ChronoUnit.DAYS.between(dateFrom, dateTo) + 1;
        if (rangeDays < 1 || rangeDays > MAX_RANGE_DAYS) {
            throw invalid("Report date range must contain 1 to "
                    + MAX_RANGE_DAYS + " days");
        }
        if (page < 1 || size < 1 || size > MAX_SIZE) {
            throw invalid("Report page controls are invalid");
        }
    }

    public long offset() {
        return Math.multiplyExact((long) page - 1, size);
    }

    private static WorkDomainException invalid(String message) {
        return new WorkDomainException("WORK_REPORT_QUERY_INVALID", message);
    }

    public enum Scope {
        SELF,
        ALL
    }

    public enum StatusFilter {
        ALL,
        DRAFT,
        SUBMITTED
    }
}
