package com.unique.examine.module.report.exporting;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.api.ReportExportPrincipalFacade;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.job.DurableJobFacade;
import com.unique.examine.module.report.domain.ReportException;
import com.unique.examine.module.runtime.exporting.ExportXlsxCodec;
import com.unique.examine.module.runtime.notification.JobResultNotifier;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ReportExportJobExecutor {
    private static final int PAGE_SIZE = 200;

    private final ReportExportStore store;
    private final ReportExportPrincipalFacade principals;
    private final ReportExportReportAccess reports;
    private final ExportXlsxCodec xlsx;
    private final DurableJobFacade jobs;
    private final OperationAuditFacade audit;
    private final JobResultNotifier notifier;

    public ReportExportJobExecutor(
            ReportExportStore store,
            ReportExportPrincipalFacade principals,
            ReportExportReportAccess reports,
            ExportXlsxCodec xlsx,
            DurableJobFacade jobs,
            OperationAuditFacade audit,
            JobResultNotifier notifier
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.principals = Objects.requireNonNull(principals, "principals");
        this.reports = Objects.requireNonNull(reports, "reports");
        this.xlsx = Objects.requireNonNull(xlsx, "xlsx");
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    @Transactional
    public void execute(DurableJobFacade.JobRecord job) {
        requireJob(job);
        var exportId = number(job.input(), "exportId");
        var queued = store.findById(job.systemId(), job.tenantId(), exportId)
                .filter(candidate -> candidate.jobId() == job.id()
                        && candidate.requestedByMemberId()
                        == job.requestedBy())
                .orElseThrow(ReportExportJobExecutor::notFound);
        var run = store.start(queued.id(), queued.version(),
                LocalDateTime.now());

        var current = principals.current(run.systemId(), run.tenantId(),
                        run.requestedByMemberId())
                .filter(value -> value.accountId()
                        == run.requestedByAccountId())
                .filter(value -> value.permissions().contains(
                        "system.runtime.access"))
                .orElseThrow(ReportExportJobExecutor::permissionRevoked);
        var session = new RuntimeSession(current.accountId(), run.systemId(),
                current.memberId(), run.tenantId(), current.permissions());

        var metadata = reports.metadata(session, run.reportId(),
                run.reportVersionId());
        var expectedFields = run.source().fields().stream()
                .map(field -> field.code()).toList();
        var currentFields = metadata.fields().stream()
                .map(field -> field.fieldCode()).toList();
        if (!expectedFields.equals(currentFields)
                || !metadata.versionId().equals(
                Long.toString(run.reportVersionId()))
                || !metadata.dataSourceVersionId().equals(
                Long.toString(run.source().dataSourceVersionId()))) {
            throw permissionRevoked();
        }

        var output = new ArrayList<com.unique.examine.module.report.api.ReportRuntimeViews.Row>();
        long total = -1;
        String queryHash = null;
        for (var page = 1; output.size() < ReportExportStore.MAX_ROWS; page++) {
            var result = reports.rows(session, run.reportId(),
                    run.reportVersionId(), page, PAGE_SIZE);
            if (result.partial()) {
                throw sourceChanged();
            }
            if (page == 1) {
                total = result.total();
                queryHash = result.queryHash();
                if (total < 0) {
                    throw sourceChanged();
                }
            } else if (!Objects.equals(queryHash, result.queryHash())
                    || result.total() != total) {
                throw sourceChanged();
            }
            var remaining = (int) Math.min(
                    ReportExportStore.MAX_ROWS - output.size(),
                    Math.max(0L, total - output.size()));
            output.addAll(result.rows().subList(0,
                    Math.min(remaining, result.rows().size())));
            if (output.size() >= total) {
                break;
            }
            if (result.rows().isEmpty()) {
                throw sourceChanged();
            }
        }
        if (total < 0) {
            throw sourceChanged();
        }
        var truncated = total > output.size();
        var columns = metadata.fields().stream()
                .map(field -> new ExportXlsxCodec.ReportColumn(
                        field.fieldCode(), field.fieldName(), field.type()))
                .toList();
        var content = xlsx.writeReport(run.reportCode(), columns, output,
                total, truncated);
        var filename = run.reportCode() + "-report-export-" + run.id()
                + ".xlsx";
        var completed = store.complete(run.id(), run.version(), total,
                output.size(), truncated, filename, content,
                LocalDateTime.now());
        jobs.succeed(job.id(), job.version(), Map.of(
                "exportId", Long.toString(run.id()),
                "rows", output.size(),
                "totalRows", total,
                "truncated", truncated,
                "bytes", content.length));
        audit.recordSuccess(OperationAudit.success(
                new OperationAudit.Actor(current.accountId(), "SYSTEM"),
                new OperationAudit.Context(ContextType.SYSTEM,
                        run.systemId(), run.tenantId()),
                new AggregateRef(ReportExportService.OWNER_TYPE,
                        Long.toString(run.id())),
                "REPORT_XLSX_EXPORTED", null,
                Map.of("status", "SUCCEEDED", "rows", output.size(),
                        "totalRows", total, "truncated", truncated),
                run.requestId(), run.traceId()));
        notifier.reportExportSucceeded(completed);
    }

    @Transactional
    public void markTerminalFailure(
            DurableJobFacade.JobRecord job,
            RuntimeException failure
    ) {
        requireJob(job);
        var exportId = number(job.input(), "exportId");
        var run = store.findById(job.systemId(), job.tenantId(), exportId)
                .filter(candidate -> candidate.jobId() == job.id()
                        && candidate.requestedByMemberId()
                        == job.requestedBy())
                .orElseThrow(ReportExportJobExecutor::notFound);
        if (run.status() == ReportExportStore.Status.SUCCEEDED
                || run.status() == ReportExportStore.Status.FAILED) {
            return;
        }
        var code = failure instanceof BusinessException business
                ? safeCode(business.code()) : "REPORT_EXPORT_FAILED";
        var message = failure instanceof BusinessException
                ? safeMessage(failure.getMessage())
                : "Report export failed safely";
        var failed = store.fail(run.id(), run.version(), code, message,
                LocalDateTime.now());
        audit.recordFailed(OperationAudit.failed(
                new OperationAudit.Actor(run.requestedByAccountId(), "SYSTEM"),
                new OperationAudit.Context(ContextType.SYSTEM,
                        run.systemId(), run.tenantId()),
                new AggregateRef(ReportExportService.OWNER_TYPE,
                        Long.toString(run.id())),
                "REPORT_XLSX_EXPORTED", null,
                Map.of("status", "FAILED"),
                new OperationAudit.Failure(code),
                run.requestId(), run.traceId()));
        notifier.reportExportFailed(failed, message);
    }

    private static void requireJob(DurableJobFacade.JobRecord job) {
        if (job == null
                || !ReportExportService.JOB_TYPE.equals(job.jobType())
                || !ReportExportService.OWNER_TYPE.equals(job.ownerType())
                || job.systemId() == null || job.systemId() <= 0
                || job.tenantId() == null || job.tenantId() <= 0
                || job.requestedBy() == null || job.requestedBy() <= 0) {
            throw new IllegalArgumentException("Unsupported report export job");
        }
    }

    private static long number(Map<String, Object> input, String key) {
        try {
            var value = Long.parseLong(String.valueOf(input.get(key)));
            if (value <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return value;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Missing job input " + key,
                    exception);
        }
    }

    private static ReportException notFound() {
        return new ReportException("REPORT_EXPORT_NOT_FOUND",
                "Report export does not exist");
    }

    private static ReportException permissionRevoked() {
        return new ReportException("REPORT_EXPORT_PERMISSION_REVOKED",
                "Report export permission was revoked before execution");
    }

    private static ReportException sourceChanged() {
        return new ReportException("REPORT_EXPORT_SOURCE_STALE",
                "Report source changed while the export was being generated");
    }

    private static String safeCode(String value) {
        if (value == null || !value.matches("^[A-Z][A-Z0-9_]{1,63}$")) {
            return "REPORT_EXPORT_FAILED";
        }
        return value;
    }

    static String safeMessage(String value) {
        if (value == null || value.isBlank()) {
            return "Report export failed safely";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
