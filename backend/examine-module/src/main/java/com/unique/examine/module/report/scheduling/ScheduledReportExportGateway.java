package com.unique.examine.module.report.scheduling;

import com.unique.examine.module.report.exporting.ReportExportViews;
import com.unique.examine.module.runtime.security.RuntimeSession;

interface ScheduledReportExportGateway {
    ReportExportViews.Task start(
            RuntimeSession owner,
            String reportCode,
            String occurrenceKey,
            String requestId,
            String traceId);

    ReportExportViews.Task get(
            RuntimeSession owner,
            String reportCode,
            long exportId);
}
