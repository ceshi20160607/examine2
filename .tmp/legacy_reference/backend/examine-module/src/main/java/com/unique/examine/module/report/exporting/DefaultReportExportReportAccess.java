package com.unique.examine.module.report.exporting;

import com.unique.examine.module.report.api.ReportRuntimeViews;
import com.unique.examine.module.report.domain.PublishedReport;
import com.unique.examine.module.report.domain.ReportActor;
import com.unique.examine.module.report.runtime.ReportRuntimeService;
import com.unique.examine.module.report.service.ReportService;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
final class DefaultReportExportReportAccess
        implements ReportExportReportAccess {
    private final ReportService reports;
    private final ReportRuntimeService runtime;

    DefaultReportExportReportAccess(
            ReportService reports,
            ReportRuntimeService runtime
    ) {
        this.reports = Objects.requireNonNull(reports, "reports");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public PublishedReport active(
            RuntimeSession session,
            String reportCode
    ) {
        return reports.active(actor(session), reportCode);
    }

    @Override
    public ReportRuntimeViews.Metadata metadata(
            RuntimeSession session,
            long reportId,
            long reportVersionId
    ) {
        return runtime.metadata(session, reportId, reportVersionId);
    }

    @Override
    public ReportRuntimeViews.Rows rows(
            RuntimeSession session,
            long reportId,
            long reportVersionId,
            int page,
            int size
    ) {
        return runtime.rows(session, reportId, reportVersionId, page, size);
    }

    private static ReportActor actor(RuntimeSession session) {
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new com.unique.examine.module.report.domain.ReportException(
                    "REPORT_TENANT_REQUIRED",
                    "Select an active tenant before exporting reports");
        }
        return new ReportActor(session.systemId(), session.tenantId(),
                session.memberId());
    }
}
