package com.unique.examine.module.report.runtime;

import com.unique.examine.module.report.domain.ReportDefinition;
import com.unique.examine.module.report.domain.ReportDraft;
import com.unique.examine.module.report.domain.ReportException;
import com.unique.examine.module.report.domain.ReportFieldPin;
import com.unique.examine.module.report.domain.ReportSourcePin;
import com.unique.examine.module.report.domain.ReportVersion;
import com.unique.examine.module.report.port.ReportRepository;
import com.unique.examine.module.report.port.ReportSourceCatalog;
import com.unique.examine.module.report.service.ReportService;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportRuntimeServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void projectsCurrentReadableValuesInImmutableReportOrder() {
        var runtime = new FakeSourceRuntime(List.of(
                field("amount", "Current amount", "NUMBER"),
                field("status", "Current status", "STATUS")), false);
        var service = service(runtime);

        var metadata = service.metadata(session(), "ops_report");
        assertThat(metadata.fields()).extracting(value -> value.fieldCode())
                .containsExactly("status", "amount");
        assertThat(metadata.fields()).extracting(value -> value.fieldName())
                .containsExactly("Pinned status", "Pinned amount");

        var rows = service.rows(session(), "ops_report", 1, 20);
        assertThat(rows.total()).isEqualTo(1);
        assertThat(rows.queryHash()).isEqualTo("query-hash");
        assertThat(rows.rows().getFirst().values())
                .extracting(value -> value.fieldCode())
                .containsExactly("status", "amount");
        assertThat(rows.rows().getFirst().values())
                .extracting(value -> value.displayValue())
                .containsExactly("Open", "12.50");
    }

    @Test
    void resolvesExactPublicationForDurableConsumers() {
        var service = service(new FakeSourceRuntime(List.of(
                field("status", "Current status", "STATUS"),
                field("amount", "Current amount", "NUMBER")), false));

        var metadata = service.metadata(session(), 10L, 20L);
        var rows = service.rows(session(), 10L, 20L, 1, 200);

        assertThat(metadata.versionId()).isEqualTo("20");
        assertThat(metadata.dataSourceVersionId()).isEqualTo("40");
        assertThat(metadata.fields()).extracting(value -> value.fieldCode())
                .containsExactly("status", "amount");
        assertThat(rows.rows()).hasSize(1);
    }

    @Test
    void omitsAFieldThatCurrentAuthorityCannotRead() {
        var runtime = new FakeSourceRuntime(
                List.of(field("amount", "Amount", "NUMBER")), false);

        var metadata = service(runtime).metadata(session(), "ops_report");

        assertThat(metadata.fields()).extracting(value -> value.fieldCode())
                .containsExactly("amount");
    }

    @Test
    void failsClosedWhenNoPinnedFieldIsCurrentlyReadable() {
        var service = service(new FakeSourceRuntime(List.of(), false));

        assertThatThrownBy(() -> service.metadata(session(), "ops_report"))
                .isInstanceOfSatisfying(ReportException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("REPORT_FIELDS_FORBIDDEN"));
    }

    @Test
    void failsClosedWhenExactSourceIdentityOrTypeIsStale() {
        var wrongIdentity = service(new FakeSourceRuntime(List.of(
                field("status", "Status", "STATUS")), true));
        assertThatThrownBy(() -> wrongIdentity.metadata(session(), "ops_report"))
                .isInstanceOfSatisfying(ReportException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("REPORT_SOURCE_UNAVAILABLE"));

        var wrongType = service(new FakeSourceRuntime(List.of(
                field("status", "Status", "TEXT")), false));
        assertThatThrownBy(() -> wrongType.metadata(session(), "ops_report"))
                .isInstanceOfSatisfying(ReportException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("REPORT_SOURCE_UNAVAILABLE"));
    }

    @Test
    void validatesTenantAndPageBoundsBeforeExecution() {
        var service = service(new FakeSourceRuntime(List.of(
                field("status", "Status", "STATUS")), false));
        assertThatThrownBy(() -> service.rows(session(), "ops_report", 0, 20))
                .isInstanceOfSatisfying(ReportException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("REPORT_PAGE_INVALID"));
        var tenantless = new RuntimeSession(9L, 1L, 3L, null, Set.of());
        assertThatThrownBy(() -> service.list(tenantless))
                .isInstanceOfSatisfying(ReportException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("REPORT_TENANT_REQUIRED"));
    }

    private static ReportRuntimeService service(ReportDataSourceRuntime runtime) {
        var repository = new FixedRepository(root(), version());
        var reports = new ReportService(repository, new EmptySourceCatalog(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new ReportRuntimeService(reports, runtime);
    }

    private static RuntimeSession session() {
        return new RuntimeSession(9L, 1L, 3L, 2L, Set.of());
    }

    private static ReportDefinition root() {
        return new ReportDefinition(10L, 1L, 2L, "ops_report",
                "Operations", "Pinned report",
                new ReportDraft(30L, List.of("status", "amount")),
                1L, 20L, 1, NOW, NOW, 2L);
    }

    private static ReportVersion version() {
        return new ReportVersion(20L, 10L, 1L, 2L, 1, 1L,
                "ops_report", "Operations", "Pinned report",
                new ReportSourcePin(30L, "ops_source", "Operations source",
                        40L, 3, 50L, "work_order", "schema-7", List.of(
                        new ReportFieldPin(101L, "status", "Pinned status",
                                "STATUS", "STATUS"),
                        new ReportFieldPin(102L, "amount", "Pinned amount",
                                "NUMBER", "NUMBER"))),
                "a".repeat(64), 3L, NOW);
    }

    private static ReportDataSourceRuntime.SourceField field(
            String code,
            String name,
            String type
    ) {
        return new ReportDataSourceRuntime.SourceField(code, name, type);
    }

    private static final class FakeSourceRuntime
            implements ReportDataSourceRuntime {
        private final List<SourceField> fields;
        private final boolean wrongIdentity;

        private FakeSourceRuntime(List<SourceField> fields, boolean wrongIdentity) {
            this.fields = List.copyOf(fields);
            this.wrongIdentity = wrongIdentity;
        }

        @Override
        public SourceMetadata metadata(
                RuntimeSession session,
                long dataSourceId,
                long dataSourceVersionId
        ) {
            return new SourceMetadata(
                    wrongIdentity ? "31" : "30", "ops_source",
                    "Operations source", "work_order", "40", 3,
                    "schema-7", fields);
        }

        @Override
        public SourceRows rows(
                RuntimeSession session,
                long dataSourceId,
                long dataSourceVersionId,
                int page,
                int size
        ) {
            return new SourceRows(List.of(new SourceRow(
                    "501", "WO-501", 4L, "ACTIVE", "Order 501", List.of(
                    new SourceValue("amount", "Amount", "NUMBER", "12.50", "12.50"),
                    new SourceValue("status", "Status", "STATUS", "OPEN", "Open")))),
                    page, size, 1L, "query-hash");
        }
    }

    private static final class FixedRepository implements ReportRepository {
        private final ReportDefinition root;
        private final ReportVersion version;

        private FixedRepository(ReportDefinition root, ReportVersion version) {
            this.root = root;
            this.version = version;
        }

        @Override public long nextReportId() { throw unsupported(); }
        @Override public long nextVersionId() { throw unsupported(); }
        @Override public Optional<ReportDefinition> findById(long systemId, long tenantId, long reportId) {
            return scoped(systemId, tenantId) && reportId == root.id()
                    ? Optional.of(root) : Optional.empty();
        }
        @Override public Optional<ReportDefinition> findByCode(long systemId, long tenantId, String code) {
            return scoped(systemId, tenantId) && root.code().equals(code)
                    ? Optional.of(root) : Optional.empty();
        }
        @Override public List<ReportDefinition> findAll(long systemId, long tenantId) {
            return scoped(systemId, tenantId) ? List.of(root) : List.of();
        }
        @Override public ReportDefinition insert(ReportDefinition root) { throw unsupported(); }
        @Override public ReportDefinition saveDraft(ReportDefinition expected, ReportDefinition revised) { throw unsupported(); }
        @Override public ReportVersion publish(ReportDefinition expected, ReportDefinition activated, ReportVersion version) { throw unsupported(); }
        @Override public Optional<ReportVersion> findActiveVersion(long systemId, long tenantId, long reportId) {
            return scoped(systemId, tenantId) && reportId == root.id()
                    ? Optional.of(version) : Optional.empty();
        }
        @Override public Optional<ReportVersion> findVersion(long systemId, long tenantId, long reportId, int versionNumber) {
            return scoped(systemId, tenantId) && reportId == root.id()
                    && versionNumber == version.versionNumber()
                    ? Optional.of(version) : Optional.empty();
        }
        @Override public Optional<ReportVersion> findVersionById(long systemId, long tenantId, long reportId, long versionId) {
            return scoped(systemId, tenantId) && reportId == root.id()
                    && versionId == version.id() ? Optional.of(version) : Optional.empty();
        }
        @Override public List<ReportVersion> findVersions(long systemId, long tenantId, long reportId) {
            return findActiveVersion(systemId, tenantId, reportId).stream().toList();
        }
        private boolean scoped(long systemId, long tenantId) {
            return systemId == root.systemId() && tenantId == root.tenantId();
        }
        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException();
        }
    }

    private static final class EmptySourceCatalog implements ReportSourceCatalog {
        @Override public Optional<SourceVersion> active(long systemId, long tenantId, long dataSourceId) {
            return Optional.empty();
        }
        @Override public Optional<SourceVersion> version(long systemId, long tenantId, long dataSourceId, long dataSourceVersionId) {
            return Optional.empty();
        }
    }
}
