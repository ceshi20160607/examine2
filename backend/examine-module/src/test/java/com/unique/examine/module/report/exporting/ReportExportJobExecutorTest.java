package com.unique.examine.module.report.exporting;

import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.api.ReportExportPrincipalFacade;
import com.unique.examine.core.api.ResultNotificationFacade;
import com.unique.examine.core.job.DurableJobFacade;
import com.unique.examine.module.report.api.ReportRuntimeViews;
import com.unique.examine.module.report.domain.PublishedReport;
import com.unique.examine.module.report.domain.ReportException;
import com.unique.examine.module.report.domain.ReportFieldPin;
import com.unique.examine.module.report.domain.ReportSourcePin;
import com.unique.examine.module.runtime.exporting.ExportXlsxCodec;
import com.unique.examine.module.runtime.notification.JobResultNotifier;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportExportJobExecutorTest {
    @Test
    void reauthorizesCurrentPrincipalAndStopsWhenPermissionWasRevoked() {
        var store = new MutableStore(run());
        var reports = new FakeAccess();
        var executor = executor(store, (system, tenant, member) ->
                Optional.empty(), reports, new CapturingCodec(),
                new FakeJobs());

        assertThatThrownBy(() -> executor.execute(job()))
                .isInstanceOfSatisfying(ReportException.class,
                        error -> assertThat(error.code()).isEqualTo(
                                "REPORT_EXPORT_PERMISSION_REVOKED"));
        assertThat(reports.metadataCalls).isZero();
        assertThat(reports.rowCalls).isZero();
        assertThat(store.current.status())
                .isEqualTo(ReportExportStore.Status.RUNNING);
    }

    @Test
    void exportsAtMostFiveThousandRowsAndRecordsTruncation() {
        var store = new MutableStore(run());
        var reports = new FakeAccess();
        var codec = new CapturingCodec();
        var jobs = new FakeJobs();
        var executor = executor(store, (system, tenant, member) ->
                Optional.of(new ReportExportPrincipalFacade.Principal(
                        9, member, 8, Set.of("system.runtime.access"))),
                reports, codec, jobs);

        executor.execute(job());

        assertThat(reports.rowCalls).isEqualTo(25);
        assertThat(codec.rows).isEqualTo(5_000);
        assertThat(codec.total).isEqualTo(5_001);
        assertThat(codec.truncated).isTrue();
        assertThat(store.current.status())
                .isEqualTo(ReportExportStore.Status.SUCCEEDED);
        assertThat(store.current.totalRows()).isEqualTo(5_001L);
        assertThat(store.current.processedRows()).isEqualTo(5_000);
        assertThat(store.current.truncated()).isTrue();
        assertThat(jobs.successResult).containsEntry("rows", 5_000)
                .containsEntry("totalRows", 5_001L)
                .containsEntry("truncated", true);
    }

    @Test
    void persistsTerminalPermissionFailureWithoutProducingAResult() {
        var store = new MutableStore(run());
        var executor = executor(store, (system, tenant, member) ->
                        Optional.empty(), new FakeAccess(),
                new CapturingCodec(), new FakeJobs());

        executor.markTerminalFailure(job(), new ReportException(
                "REPORT_EXPORT_PERMISSION_REVOKED",
                "Report export permission was revoked before execution"));

        assertThat(store.current.status())
                .isEqualTo(ReportExportStore.Status.FAILED);
        assertThat(store.current.failureCode())
                .isEqualTo("REPORT_EXPORT_PERMISSION_REVOKED");
        assertThat(store.current.resultFilename()).isNull();
        assertThat(store.current.resultSize()).isNull();
    }

    private static ReportExportJobExecutor executor(
            ReportExportStore store,
            ReportExportPrincipalFacade principals,
            ReportExportReportAccess reports,
            ExportXlsxCodec codec,
            DurableJobFacade jobs
    ) {
        var notifier = new JobResultNotifier(command ->
                new ResultNotificationFacade.DeliveryReceipt(
                        1, 2L, "DELIVERED", false));
        return new ReportExportJobExecutor(store, principals, reports, codec,
                jobs, new NoopAudit(), notifier);
    }

    private static DurableJobFacade.JobRecord job() {
        return new DurableJobFacade.JobRecord(70,
                ReportExportService.JOB_TYPE, ReportExportService.OWNER_TYPE,
                "60", 1L, 2L, 3L, "RUNNING", 0,
                Map.of("exportId", "60"), Map.of(), 1, 3, null, null,
                null, null, null, LocalDateTime.now(), LocalDateTime.now(), 5);
    }

    private static ReportExportStore.Run run() {
        var now = LocalDateTime.parse("2026-08-04T00:00:00");
        return new ReportExportStore.Run(60, 1, 2, 10, "ops_report",
                "Operations", 20, 1, source(), "a".repeat(64), 70,
                9, 3, ReportExportStore.Status.QUEUED, null, 0, false,
                null, null, null, null, "request-1", "trace-1",
                null, null, now, now, 0);
    }

    private static ReportSourcePin source() {
        return new ReportSourcePin(30, "ops_source", "Operations source",
                40, 3, 50, "work_order", "schema-7", List.of(
                new ReportFieldPin(101, "status", "Pinned status",
                        "STATUS", "STATUS")));
    }

    private static ReportRuntimeViews.Metadata metadata() {
        return new ReportRuntimeViews.Metadata("10", "ops_report",
                "Operations", null, "20", 1, "30", "ops_source",
                "Operations source", "40", 3, "50", "work_order",
                "schema-7", List.of(new ReportRuntimeViews.Field(
                "status", "Pinned status", "STATUS")));
    }

    private static final class FakeAccess
            implements ReportExportReportAccess {
        private int metadataCalls;
        private int rowCalls;

        @Override
        public PublishedReport active(RuntimeSession session, String code) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ReportRuntimeViews.Metadata metadata(
                RuntimeSession session, long reportId, long versionId
        ) {
            metadataCalls++;
            return ReportExportJobExecutorTest.metadata();
        }

        @Override
        public ReportRuntimeViews.Rows rows(
                RuntimeSession session, long reportId, long versionId,
                int page, int size
        ) {
            rowCalls++;
            var rows = new ArrayList<ReportRuntimeViews.Row>();
            for (var index = 0; index < 200; index++) {
                rows.add(new ReportRuntimeViews.Row(
                        page + "-" + index, "R-" + page + "-" + index,
                        1, "ACTIVE", "row", List.of(
                        new ReportRuntimeViews.Value("status", "Status",
                                "STATUS", "OPEN", "Open"))));
            }
            return new ReportRuntimeViews.Rows(rows, rows, page, size,
                    5_001, "stable-query");
        }
    }

    private static final class CapturingCodec extends ExportXlsxCodec {
        private int rows;
        private long total;
        private boolean truncated;

        @Override
        public byte[] writeReport(
                String reportCode,
                List<ReportColumn> columns,
                List<ReportRuntimeViews.Row> rows,
                long totalRows,
                boolean truncated
        ) {
            this.rows = rows.size();
            this.total = totalRows;
            this.truncated = truncated;
            return new byte[]{1};
        }
    }

    private static final class MutableStore implements ReportExportStore {
        private Run current;

        private MutableStore(Run current) {
            this.current = current;
        }

        @Override public Optional<Run> findByRequestKey(long s, long t, long m, long r, String h) { return Optional.empty(); }
        @Override public Optional<Run> findById(long system, long tenant, long id) {
            return current.systemId() == system && current.tenantId() == tenant
                    && current.id() == id ? Optional.of(current) : Optional.empty();
        }
        @Override public long countOwned(long s, long t, long r, long m) { return 0; }
        @Override public List<Run> pageOwned(long s, long t, long r, long m, int l, long o) { return List.of(); }
        @Override public void insert(Run run) { current = run; }

        @Override
        public Run start(long id, long version, LocalDateTime now) {
            current = copy(Status.RUNNING, null, 0, false, null, null,
                    null, null, now, null, current.version() + 1);
            return current;
        }

        @Override public Run requeue(long id, long version, LocalDateTime now) { throw new UnsupportedOperationException(); }

        @Override
        public Run complete(long id, long version, long total, int processed,
                            boolean truncated, String filename, byte[] content,
                            LocalDateTime now) {
            current = copy(Status.SUCCEEDED, total, processed, truncated,
                    filename, (long) content.length, null, null,
                    current.startedAt(), now, current.version() + 1);
            return current;
        }

        @Override
        public Run fail(long id, long version, String code, String message,
                        LocalDateTime now) {
            current = copy(Status.FAILED, null, 0, false, null, null,
                    code, message, current.startedAt(), now,
                    current.version() + 1);
            return current;
        }

        @Override public byte[] result(long s, long t, long id) { return new byte[]{1}; }

        private Run copy(Status status, Long total, int processed,
                         boolean truncated, String filename, Long size,
                         String code, String message, LocalDateTime started,
                         LocalDateTime finished, long version) {
            return new Run(current.id(), current.systemId(), current.tenantId(),
                    current.reportId(), current.reportCode(), current.reportName(),
                    current.reportVersionId(), current.reportVersionNumber(),
                    current.source(), current.requestKeyHash(), current.jobId(),
                    current.requestedByAccountId(), current.requestedByMemberId(),
                    status, total, processed, truncated, filename, size, code,
                    message, current.requestId(), current.traceId(), started,
                    finished, current.createdAt(), LocalDateTime.now(), version);
        }
    }

    private static final class FakeJobs implements DurableJobFacade {
        private Map<String, Object> successResult;
        @Override public JobRecord enqueue(EnqueueCommand command) { throw new UnsupportedOperationException(); }
        @Override public Optional<JobRecord> claim(String type, Duration lease) { return Optional.empty(); }
        @Override public JobRecord succeed(long id, long version, Map<String, Object> result) {
            successResult = result;
            return job();
        }
        @Override public JobRecord fail(long id, long version, String error, Duration retry) { throw new UnsupportedOperationException(); }
        @Override public JobRecord require(long id) { throw new UnsupportedOperationException(); }
    }

    private static final class NoopAudit implements OperationAuditFacade {
        @Override public void recordSuccess(OperationAudit audit) { }
        @Override public void recordDenied(OperationAudit audit) { }
        @Override public void recordFailed(OperationAudit audit) { }
    }
}
