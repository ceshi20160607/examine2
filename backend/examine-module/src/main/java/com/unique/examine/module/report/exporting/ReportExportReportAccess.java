package com.unique.examine.module.report.exporting;

import com.unique.examine.module.report.api.ReportRuntimeViews;
import com.unique.examine.module.report.domain.PublishedReport;
import com.unique.examine.module.runtime.security.RuntimeSession;

interface ReportExportReportAccess {
    PublishedReport active(RuntimeSession session, String reportCode);

    ReportRuntimeViews.Metadata metadata(
            RuntimeSession session,
            long reportId,
            long reportVersionId);

    ReportRuntimeViews.Rows rows(
            RuntimeSession session,
            long reportId,
            long reportVersionId,
            int page,
            int size);
}
