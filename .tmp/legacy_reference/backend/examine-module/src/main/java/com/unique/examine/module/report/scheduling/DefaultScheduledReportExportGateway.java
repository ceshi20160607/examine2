package com.unique.examine.module.report.scheduling;

import com.unique.examine.module.report.exporting.ReportExportService;
import com.unique.examine.module.report.exporting.ReportExportViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
final class DefaultScheduledReportExportGateway
        implements ScheduledReportExportGateway {
    private final ReportExportService exports;

    DefaultScheduledReportExportGateway(ReportExportService exports) {
        this.exports = Objects.requireNonNull(exports, "exports");
    }

    @Override
    public ReportExportViews.Task start(
            RuntimeSession owner,
            String reportCode,
            String occurrenceKey,
            String requestId,
            String traceId
    ) {
        return exports.start(owner, reportCode,
                new ReportExportViews.StartRequest(occurrenceKey),
                occurrenceKey, requestId, traceId);
    }

    @Override
    public ReportExportViews.Task get(
            RuntimeSession owner,
            String reportCode,
            long exportId
    ) {
        return exports.get(owner, reportCode, exportId);
    }
}
