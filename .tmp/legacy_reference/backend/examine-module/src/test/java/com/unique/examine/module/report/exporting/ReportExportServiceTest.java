package com.unique.examine.module.report.exporting;

import com.unique.examine.core.job.DurableJobFacade;
import com.unique.examine.module.report.api.ReportRuntimeViews;
import com.unique.examine.module.report.domain.PublishedReport;
import com.unique.examine.module.report.domain.ReportDefinition;
import com.unique.examine.module.report.domain.ReportDraft;
import com.unique.examine.module.report.domain.ReportException;
import com.unique.examine.module.report.domain.ReportFieldPin;
import com.unique.examine.module.report.domain.ReportSourcePin;
import com.unique.examine.module.report.domain.ReportVersion;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportExportServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void snapshotsActivePublicationAndReplaysItAfterActiveMoves() {
        var access = new FakeAccess(publication(20, 1, 40,
                List.of("status", "amount")));
        var store = new MemoryStore();
        var jobs = new FakeJobs();
        var service = new ReportExportService(access, store, jobs,
                new com.unique.examine.core.id.IdService());

        var first = service.start(session(3), "ops_report",
                new ReportExportViews.StartRequest("manual-1"), "manual-1",
                "request-1", "trace-1");
        var saved = store.runs.getFirst();
        assertThat(first.reportVersionId()).isEqualTo("20");
        assertThat(first.dataSourceVersionId()).isEqualTo("40");
        assertThat(first.fieldCodes()).containsExactly("status", "amount");
        assertThat(saved.source().fields()).extracting(value -> value.code())
                .containsExactly("status", "amount");
        assertThat(jobs.enqueued.getFirst().input())
                .containsOnlyKeys("exportId");
        assertThat(saved.requestKeyHash()).doesNotContain("manual-1")
                .hasSize(64);

        access.active = publication(21, 2, 41, List.of("amount"));
        var replay = service.start(session(3), "ops_report",
                new ReportExportViews.StartRequest("manual-1"), "manual-1",
                "request-2", "trace-2");

        assertThat(replay.exportId()).isEqualTo(first.exportId());
        assertThat(replay.reportVersionId()).isEqualTo("20");
        assertThat(replay.fieldCodes()).containsExactly("status", "amount");
        assertThat(store.runs).hasSize(1);
        assertThat(jobs.enqueued).hasSize(1);
        assertThat(access.metadataVersions).containsExactly(20L, 20L);
    }

    @Test
    void rejectsMismatchedStableIdentityAndHidesOtherOwners() {
        var access = new FakeAccess(publication(20, 1, 40,
                List.of("status")));
        var store = new MemoryStore();
        var service = new ReportExportService(access, store,
                new FakeJobs(), new com.unique.examine.core.id.IdService());

        assertThatThrownBy(() -> service.start(session(3), "ops_report",
                new ReportExportViews.StartRequest("body-key"), "header-key",
                "request-1", "trace-1"))
                .isInstanceOfSatisfying(ReportException.class,
                        error -> assertThat(error.code()).isEqualTo(
                                "REPORT_EXPORT_REQUEST_INVALID"));

        var own = service.start(session(3), "ops_report",
                new ReportExportViews.StartRequest("manual-2"), "manual-2",
                "request-2", "trace-2");
        assertThatThrownBy(() -> service.get(session(4), "ops_report",
                Long.parseLong(own.exportId())))
                .isInstanceOfSatisfying(ReportException.class,
                        error -> assertThat(error.code()).isEqualTo(
                                "REPORT_EXPORT_NOT_FOUND"));
        assertThatThrownBy(() -> service.get(
                new RuntimeSession(9, 1, 3, 99L,
                        Set.of("system.runtime.access")),
                "ops_report", Long.parseLong(own.exportId())))
                .isInstanceOfSatisfying(ReportException.class,
                        error -> assertThat(error.code()).isEqualTo(
                                "REPORT_EXPORT_NOT_FOUND"));
    }

    private static RuntimeSession session(long memberId) {
        return new RuntimeSession(9, 1, memberId, 2L,
                Set.of("system.runtime.access"));
    }

    private static PublishedReport publication(
            long versionId,
            int versionNumber,
            long sourceVersionId,
            List<String> codes
    ) {
        var fields = new ArrayList<ReportFieldPin>();
        for (var index = 0; index < codes.size(); index++) {
            fields.add(new ReportFieldPin(100 + index, codes.get(index),
                    "Pinned " + codes.get(index),
                    "amount".equals(codes.get(index)) ? "NUMBER" : "STATUS",
                    "amount".equals(codes.get(index)) ? "NUMBER" : "STATUS"));
        }
        var source = new ReportSourcePin(30, "ops_source",
                "Operations source", sourceVersionId, versionNumber + 2,
                50, "work_order", "schema-" + versionNumber, fields);
        var root = new ReportDefinition(10, 1, 2, "ops_report",
                "Operations", "Pinned report", new ReportDraft(30, codes),
                versionNumber, versionId, versionNumber, NOW, NOW,
                versionNumber + 1L);
        var version = new ReportVersion(versionId, 10, 1, 2,
                versionNumber, versionNumber, "ops_report", "Operations",
                "Pinned report", source, "a".repeat(64), 3, NOW);
        return PublishedReport.active(root, version);
    }

    private static ReportRuntimeViews.Metadata metadata(
            PublishedReport report
    ) {
        var source = report.version().source();
        return new ReportRuntimeViews.Metadata(
                Long.toString(report.root().id()), report.root().code(),
                report.version().name(), report.version().description(),
                Long.toString(report.version().id()),
                report.version().versionNumber(),
                Long.toString(source.dataSourceId()), source.dataSourceCode(),
                source.dataSourceName(),
                Long.toString(source.dataSourceVersionId()),
                source.dataSourceVersionNumber(),
                Long.toString(source.moduleId()), source.moduleCode(),
                source.schemaVersionId(), source.fields().stream()
                .map(field -> new ReportRuntimeViews.Field(field.code(),
                        field.name(), field.type())).toList());
    }

    private static final class FakeAccess
            implements ReportExportReportAccess {
        private PublishedReport active;
        private final Map<Long, PublishedReport> publications =
                new java.util.HashMap<>();
        private final List<Long> metadataVersions = new ArrayList<>();

        private FakeAccess(PublishedReport active) {
            this.active = active;
            publications.put(active.version().id(), active);
        }

        @Override
        public PublishedReport active(RuntimeSession session, String code) {
            publications.put(active.version().id(), active);
            return active;
        }

        @Override
        public ReportRuntimeViews.Metadata metadata(
                RuntimeSession session,
                long reportId,
                long reportVersionId
        ) {
            metadataVersions.add(reportVersionId);
            return ReportExportServiceTest.metadata(
                    publications.get(reportVersionId));
        }

        @Override
        public ReportRuntimeViews.Rows rows(
                RuntimeSession session, long reportId, long reportVersionId,
                int page, int size
        ) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class MemoryStore implements ReportExportStore {
        private final List<Run> runs = new ArrayList<>();

        @Override
        public Optional<Run> findByRequestKey(
                long systemId, long tenantId, long memberId, long reportId,
                String requestKeyHash
        ) {
            return runs.stream().filter(run -> run.systemId() == systemId
                    && run.tenantId() == tenantId
                    && run.requestedByMemberId() == memberId
                    && run.reportId() == reportId
                    && run.requestKeyHash().equals(requestKeyHash)).findFirst();
        }

        @Override
        public Optional<Run> findById(
                long systemId, long tenantId, long exportId
        ) {
            return runs.stream().filter(run -> run.systemId() == systemId
                    && run.tenantId() == tenantId && run.id() == exportId)
                    .findFirst();
        }

        @Override
        public long countOwned(long systemId, long tenantId, long reportId,
                               long memberId) {
            return pageOwned(systemId, tenantId, reportId, memberId,
                    Integer.MAX_VALUE, 0).size();
        }

        @Override
        public List<Run> pageOwned(long systemId, long tenantId,
                                   long reportId, long memberId, int limit,
                                   long offset) {
            return runs.stream().filter(run -> run.systemId() == systemId
                    && run.tenantId() == tenantId
                    && run.reportId() == reportId
                    && run.requestedByMemberId() == memberId)
                    .skip(offset).limit(limit).toList();
        }

        @Override public void insert(Run run) { runs.add(run); }
        @Override public Run start(long id, long version, LocalDateTime now) { throw unsupported(); }
        @Override public Run requeue(long id, long version, LocalDateTime now) { throw unsupported(); }
        @Override public Run complete(long id, long version, long total, int processed, boolean truncated,
                                      String filename, byte[] content, LocalDateTime now) { throw unsupported(); }
        @Override public Run fail(long id, long version, String code, String message, LocalDateTime now) { throw unsupported(); }
        @Override public byte[] result(long systemId, long tenantId, long id) { throw unsupported(); }
    }

    private static final class FakeJobs implements DurableJobFacade {
        private final List<EnqueueCommand> enqueued = new ArrayList<>();

        @Override
        public JobRecord enqueue(EnqueueCommand command) {
            enqueued.add(command);
            return new JobRecord(70 + enqueued.size(), command.jobType(),
                    command.ownerType(), command.ownerId(), command.systemId(),
                    command.tenantId(), command.requestedBy(), "QUEUED", 0,
                    command.input(), Map.of(), 0, command.maxAttempts(), null,
                    null, null, null, null, null, null, 0);
        }

        @Override public Optional<JobRecord> claim(String type, Duration lease) { return Optional.empty(); }
        @Override public JobRecord succeed(long id, long version, Map<String, Object> result) { throw unsupported(); }
        @Override public JobRecord fail(long id, long version, String error, Duration retry) { throw unsupported(); }
        @Override public JobRecord require(long id) { throw unsupported(); }
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException();
    }
}
