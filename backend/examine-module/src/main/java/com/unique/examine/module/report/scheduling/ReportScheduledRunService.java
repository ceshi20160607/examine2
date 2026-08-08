package com.unique.examine.module.report.scheduling;

import com.unique.examine.module.report.domain.ReportException;
import com.unique.examine.module.report.exporting.ReportExportStore;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class ReportScheduledRunService {
    private final ReportScheduleStore schedules;
    private final ReportExportStore exports;

    public ReportScheduledRunService(
            ReportScheduleStore schedules,
            ReportExportStore exports
    ) {
        this.schedules = Objects.requireNonNull(schedules, "schedules");
        this.exports = Objects.requireNonNull(exports, "exports");
    }

    @Transactional(readOnly = true)
    public ReportScheduleViews.RunPage list(
            RuntimeSession session,
            String reportCode,
            int page,
            int size
    ) {
        page(page, size);
        var tenantId = tenant(session);
        var total = schedules.countDelivered(session.systemId(), tenantId,
                reportCode, session.memberId());
        var offset = Math.multiplyExact((long) page - 1, size);
        var items = schedules.pageDelivered(session.systemId(), tenantId,
                        reportCode, session.memberId(), size, offset).stream()
                .map(ReportScheduledRunService::view).toList();
        return new ReportScheduleViews.RunPage(items, page, size, total);
    }

    @Transactional(readOnly = true)
    public ReportScheduleViews.Run detail(
            RuntimeSession session,
            String reportCode,
            long occurrenceId
    ) {
        return view(delivered(session, reportCode, occurrenceId));
    }

    @Transactional(readOnly = true)
    public Result result(
            RuntimeSession session,
            String reportCode,
            long occurrenceId
    ) {
        var occurrence = delivered(session, reportCode, occurrenceId);
        if (occurrence.status()
                != ReportScheduleStore.OccurrenceStatus.SUCCEEDED
                || occurrence.exportId() == null
                || occurrence.resultFilename() == null) {
            throw new ReportException("REPORT_SCHEDULE_RESULT_CONFLICT",
                    "Scheduled report result is not ready");
        }
        var export = exports.findById(occurrence.systemId(),
                        occurrence.tenantId(), occurrence.exportId())
                .filter(value -> value.status()
                        == ReportExportStore.Status.SUCCEEDED)
                .filter(value -> value.reportId() == occurrence.reportId())
                .filter(value -> value.reportCode().equals(reportCode))
                .orElseThrow(ReportScheduledRunService::notFound);
        return new Result(occurrence.resultFilename(), exports.result(
                export.systemId(), export.tenantId(), export.id()));
    }

    static ReportScheduleViews.Run view(
            ReportScheduleStore.Occurrence value
    ) {
        return new ReportScheduleViews.Run(
                Long.toString(value.id()), Long.toString(value.scheduleId()),
                value.scheduleCode(), value.scheduleName(), value.reportCode(),
                value.scheduledAt().toString(), value.status().name(),
                value.exportId() == null ? null
                        : Long.toString(value.exportId()),
                value.resultFilename(), value.totalRows(),
                value.processedRows(), value.truncated(), value.failureCode(),
                value.failureMessage(), value.createdAt().toString(),
                value.finishedAt() == null ? null
                        : value.finishedAt().toString());
    }

    private ReportScheduleStore.Occurrence delivered(
            RuntimeSession session,
            String reportCode,
            long occurrenceId
    ) {
        if (occurrenceId <= 0) {
            throw notFound();
        }
        return schedules.findDelivered(session.systemId(), tenant(session),
                        reportCode, occurrenceId, session.memberId())
                .orElseThrow(ReportScheduledRunService::notFound);
    }

    private static long tenant(RuntimeSession session) {
        if (session == null || session.systemId() <= 0
                || session.memberId() <= 0 || session.tenantId() == null
                || session.tenantId() <= 0) {
            throw new ReportException("REPORT_TENANT_REQUIRED",
                    "Select an active tenant before viewing scheduled reports");
        }
        return session.tenantId();
    }

    private static void page(int page, int size) {
        if (page < 1 || size < 1 || size > 50) {
            throw new ReportException("REPORT_SCHEDULE_PAGE_INVALID",
                    "Scheduled report page must be positive and size must be 1..50");
        }
    }

    private static ReportException notFound() {
        return new ReportException("REPORT_SCHEDULE_RUN_NOT_FOUND",
                "Scheduled report run does not exist");
    }

    public record Result(String filename, byte[] content) {
        public Result {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
