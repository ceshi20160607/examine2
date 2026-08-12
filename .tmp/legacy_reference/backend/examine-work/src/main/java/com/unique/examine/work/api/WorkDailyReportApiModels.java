package com.unique.examine.work.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.examine.work.domain.WorkDailyReport;
import com.unique.examine.work.domain.WorkDailyReportPage;
import com.unique.examine.work.service.WorkDailyReportService;

import java.time.LocalDate;
import java.util.List;

public final class WorkDailyReportApiModels {
    private WorkDailyReportApiModels() {
    }

    public record CreateReport(
            LocalDate workDate,
            String completedWork,
            String plannedWork,
            String blockers,
            JsonNode customFields
    ) {
        public CreateReport(LocalDate workDate, String completedWork,
                            String plannedWork, String blockers) {
            this(workDate, completedWork, plannedWork, blockers, null);
        }
    }

    public record UpdateReport(
            String completedWork,
            String plannedWork,
            String blockers,
            long version,
            JsonNode customFields
    ) {
        public UpdateReport(String completedWork, String plannedWork,
                            String blockers, long version) {
            this(completedWork, plannedWork, blockers, version, null);
        }
    }

    public record ReportVersion(long version) {
    }

    public record ReportPage(
            List<ReportView> items,
            int page,
            int size,
            long total
    ) {
        public ReportPage {
            items = List.copyOf(items);
        }

        static ReportPage from(WorkDailyReportPage value) {
            return new ReportPage(
                    value.items().stream().map(ReportView::from).toList(),
                    value.page(), value.size(), value.total());
        }
    }

    public record ReportView(
            String id,
            String systemId,
            String tenantId,
            String authorMemberId,
            String workDate,
            String completedWork,
            String plannedWork,
            String blockers,
            String status,
            String createdAt,
            String updatedAt,
            String submittedAt,
            long version,
            com.unique.examine.work.configuration.WorkConfigurationService.RuntimeView
                    runtime
    ) {
        static ReportView from(WorkDailyReport value) {
            return from(value, null);
        }

        static ReportView from(
                WorkDailyReport value,
                com.unique.examine.work.configuration.WorkConfigurationService.RuntimeView
                        runtime) {
            return new ReportView(
                    Long.toString(value.id()),
                    Long.toString(value.systemId()),
                    Long.toString(value.tenantId()),
                    Long.toString(value.authorMemberId()),
                    value.workDate().toString(),
                    value.completedWork(), value.plannedWork(),
                    value.blockers(), value.status().name(),
                    value.createdAt().toString(), value.updatedAt().toString(),
                    value.submittedAt() == null
                            ? null : value.submittedAt().toString(),
                    value.version(), runtime);
        }
    }

    public record SummaryView(
            String memberId,
            String dateFrom,
            String dateTo,
            int submittedCount,
            int draftCount,
            int missingCount,
            List<SummaryDayView> days
    ) {
        public SummaryView {
            days = List.copyOf(days);
        }

        static SummaryView from(WorkDailyReportService.Summary value) {
            return new SummaryView(
                    Long.toString(value.memberId()),
                    value.dateFrom().toString(), value.dateTo().toString(),
                    value.submittedCount(), value.draftCount(),
                    value.missingCount(),
                    value.days().stream().map(SummaryDayView::from).toList());
        }
    }

    public record SummaryDayView(
            String workDate,
            String state,
            String reportId,
            Long version
    ) {
        static SummaryDayView from(
                WorkDailyReportService.SummaryDay value
        ) {
            return new SummaryDayView(
                    value.workDate().toString(), value.state().name(),
                    value.reportId() == null
                            ? null : Long.toString(value.reportId()),
                    value.version());
        }
    }
}
