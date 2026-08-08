package com.unique.examine.work.port;

import com.unique.examine.work.domain.WorkDailyReport;
import com.unique.examine.work.domain.WorkDailyReportPage;
import com.unique.examine.work.domain.WorkDailyReportQuery;

import java.time.LocalDate;
import java.util.Optional;

public interface WorkDailyReportRepository {
    long nextId();

    Optional<WorkDailyReport> findById(
            long systemId, long tenantId, long reportId);

    Optional<WorkDailyReport> findByAuthorAndDate(
            long systemId,
            long tenantId,
            long authorMemberId,
            LocalDate workDate
    );

    WorkDailyReportPage findPage(
            long systemId,
            long tenantId,
            long currentMemberId,
            boolean manager,
            WorkDailyReportQuery query
    );

    WorkDailyReport save(WorkDailyReport report);
}
