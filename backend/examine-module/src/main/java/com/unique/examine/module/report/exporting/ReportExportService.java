package com.unique.examine.module.report.exporting;

import com.unique.examine.core.id.IdService;
import com.unique.examine.core.job.DurableJobFacade;
import com.unique.examine.module.report.domain.ReportException;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ReportExportService {
    public static final String JOB_TYPE = "REPORT_XLSX_EXPORT";
    public static final String OWNER_TYPE = "REPORT_EXPORT_RUN";

    private final ReportExportReportAccess reports;
    private final ReportExportStore store;
    private final DurableJobFacade jobs;
    private final IdService ids;

    public ReportExportService(
            ReportExportReportAccess reports,
            ReportExportStore store,
            DurableJobFacade jobs,
            IdService ids
    ) {
        this.reports = Objects.requireNonNull(reports, "reports");
        this.store = Objects.requireNonNull(store, "store");
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    @Transactional
    public ReportExportViews.Task start(
            RuntimeSession session,
            String reportCode,
            ReportExportViews.StartRequest request,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        var tenantId = tenant(session);
        var key = requestKey(request, idempotencyKey);
        var active = reports.active(session, reportCode);
        var root = active.root();
        var hash = sha256(key);
        var replay = store.findByRequestKey(session.systemId(), tenantId,
                session.memberId(), root.id(), hash);
        if (replay.isPresent()) {
            authorizeExact(session, replay.get());
            return view(replay.get());
        }

        var metadata = reports.metadata(session, root.id(),
                active.version().id());
        requireExactFields(active.version().source().fields().stream()
                .map(field -> field.code()).toList(), metadata.fields().stream()
                .map(field -> field.fieldCode()).toList());

        var exportId = ids.nextId();
        var job = jobs.enqueue(new DurableJobFacade.EnqueueCommand(
                JOB_TYPE, OWNER_TYPE, Long.toString(exportId),
                session.systemId(), tenantId, session.memberId(),
                Map.of("exportId", Long.toString(exportId)), 3));
        var now = LocalDateTime.now();
        var run = new ReportExportStore.Run(
                exportId, session.systemId(), tenantId, root.id(),
                root.code(), active.version().name(), active.version().id(),
                active.version().versionNumber(), active.version().source(),
                hash, job.id(), session.accountId(), session.memberId(),
                ReportExportStore.Status.QUEUED, null, 0, false,
                null, null, null, null, required(requestId, "request id"),
                required(traceId, "trace id"), null, null, now, now, 0);
        store.insert(run);
        return view(run);
    }

    @Transactional(readOnly = true)
    public ReportExportViews.TaskPage list(
            RuntimeSession session,
            String reportCode,
            int page,
            int size
    ) {
        if (page < 1 || size < 1 || size > 50) {
            throw invalid("REPORT_EXPORT_PAGE_INVALID",
                    "Export history page must be positive and size must be 1..50");
        }
        var tenantId = tenant(session);
        var active = reports.active(session, reportCode);
        reports.metadata(session, active.root().id(), active.version().id());
        var total = store.countOwned(session.systemId(), tenantId,
                active.root().id(), session.memberId());
        var offset = Math.multiplyExact((long) page - 1L, size);
        var items = store.pageOwned(session.systemId(), tenantId,
                        active.root().id(), session.memberId(), size, offset)
                .stream().map(this::view).toList();
        return new ReportExportViews.TaskPage(items, page, size, total);
    }

    @Transactional(readOnly = true)
    public ReportExportViews.Task get(
            RuntimeSession session,
            String reportCode,
            long exportId
    ) {
        var run = owned(session, reportCode, exportId);
        authorizeExact(session, run);
        return view(run);
    }

    @Transactional(readOnly = true)
    public Result result(
            RuntimeSession session,
            String reportCode,
            long exportId
    ) {
        var run = owned(session, reportCode, exportId);
        authorizeExact(session, run);
        if (run.status() != ReportExportStore.Status.SUCCEEDED) {
            throw new ReportException("REPORT_EXPORT_RESULT_CONFLICT",
                    "Report export result is not ready");
        }
        return new Result(run.resultFilename(), store.result(run.systemId(),
                run.tenantId(), run.id()));
    }

    ReportExportViews.Task view(ReportExportStore.Run run) {
        return new ReportExportViews.Task(
                Long.toString(run.id()), run.reportCode(),
                Long.toString(run.reportVersionId()),
                run.reportVersionNumber(),
                Long.toString(run.source().dataSourceVersionId()),
                run.source().fields().stream().map(field -> field.code()).toList(),
                run.status().name(), run.totalRows(), run.processedRows(),
                run.truncated(), Long.toString(run.jobId()),
                run.resultFilename(), run.resultSize(), run.failureCode(),
                run.failureMessage(), run.createdAt(), run.startedAt(),
                run.finishedAt());
    }

    private ReportExportStore.Run owned(
            RuntimeSession session,
            String reportCode,
            long exportId
    ) {
        var tenantId = tenant(session);
        if (exportId <= 0) {
            throw notFound();
        }
        var run = store.findById(session.systemId(), tenantId, exportId)
                .orElseThrow(ReportExportService::notFound);
        if (run.requestedByMemberId() != session.memberId()
                || !run.reportCode().equals(reportCode)) {
            throw notFound();
        }
        return run;
    }

    private void authorizeExact(
            RuntimeSession session,
            ReportExportStore.Run run
    ) {
        var metadata = reports.metadata(session, run.reportId(),
                run.reportVersionId());
        requireExactFields(run.source().fields().stream()
                        .map(field -> field.code()).toList(),
                metadata.fields().stream()
                        .map(field -> field.fieldCode()).toList());
    }

    private static void requireExactFields(
            List<String> expected,
            List<String> current
    ) {
        if (!expected.equals(current)) {
            throw new ReportException("REPORT_EXPORT_FIELDS_FORBIDDEN",
                    "One or more exported report fields are no longer readable");
        }
    }

    private static String requestKey(
            ReportExportViews.StartRequest request,
            String header
    ) {
        if (request == null || request.requestKey() == null || header == null) {
            throw invalid("REPORT_EXPORT_REQUEST_INVALID",
                    "requestKey and Idempotency-Key are required");
        }
        var body = request.requestKey().strip();
        var value = header.strip();
        if (!body.equals(value)
                || !value.matches("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")) {
            throw invalid("REPORT_EXPORT_REQUEST_INVALID",
                    "requestKey must equal Idempotency-Key and use a stable safe value");
        }
        return value;
    }

    private static long tenant(RuntimeSession session) {
        if (session == null || session.systemId() <= 0
                || session.memberId() <= 0 || session.accountId() <= 0
                || session.tenantId() == null || session.tenantId() <= 0) {
            throw new ReportException("REPORT_TENANT_REQUIRED",
                    "Select an active tenant before exporting reports");
        }
        return session.tenantId();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank() || value.length() > 64) {
            throw invalid("REPORT_EXPORT_REQUEST_INVALID",
                    label + " is required");
        }
        return value.strip();
    }

    private static ReportException invalid(String code, String message) {
        return new ReportException(code, message);
    }

    private static ReportException notFound() {
        return new ReportException("REPORT_EXPORT_NOT_FOUND",
                "Report export does not exist");
    }

    public record Result(String filename, byte[] content) {
        public Result {
            content = content.clone();
        }
    }
}
