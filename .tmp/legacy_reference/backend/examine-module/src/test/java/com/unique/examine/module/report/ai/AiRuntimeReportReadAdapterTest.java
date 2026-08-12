package com.unique.examine.module.report.ai;

import com.unique.examine.core.ai.AiRuntimeReportReadFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.report.domain.ReportDefinition;
import com.unique.examine.module.report.domain.ReportDraft;
import com.unique.examine.module.report.domain.ReportException;
import com.unique.examine.module.report.domain.ReportFieldPin;
import com.unique.examine.module.report.domain.ReportSourcePin;
import com.unique.examine.module.report.domain.ReportVersion;
import com.unique.examine.module.report.port.ReportRepository;
import com.unique.examine.module.report.port.ReportSourceCatalog;
import com.unique.examine.module.report.runtime.ReportDataSourceRuntime;
import com.unique.examine.module.report.runtime.ReportRuntimeService;
import com.unique.examine.module.report.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRuntimeReportReadAdapterTest {
    private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");
    private static final String MODULE = "work_order";
    private static final Set<String> VIEW = Set.of(
            "system.runtime.access", "module.work_order.view");

    @Test
    void pinsActiveMetadataToExactRowsAndProjectsOnlyAuthorizedDisplayValues() {
        var repository = new FixedRepository();
        var source = new TrackingSourceRuntime();
        var adapter = adapter(repository, source);

        var result = adapter.query(request(
                1L, 2L, VIEW, Set.of(MODULE),
                Map.of(MODULE, Set.of("amount", "status")),
                MODULE, 1, 2, 10));

        assertThat(repository.activeVersionReads).isEqualTo(1);
        assertThat(repository.exactVersionReads).isEqualTo(1);
        assertThat(source.metadataVersionIds).containsExactly(40L, 40L);
        assertThat(source.rowsVersionIds).containsExactly(40L);
        assertThat(source.lastPage).isEqualTo(1);
        assertThat(source.lastSize).isEqualTo(2);

        assertThat(result.reportCode()).isEqualTo("ops_report");
        assertThat(result.reportName()).isEqualTo("Operations");
        assertThat(result.reportVersionNumber()).isEqualTo(1);
        assertThat(result.dataSourceCode()).isEqualTo("ops_source");
        assertThat(result.dataSourceVersionNumber()).isEqualTo(3);
        assertThat(result.moduleCode()).isEqualTo(MODULE);
        assertThat(result.route()).isEqualTo(
                "/systems/1/reports/ops_report");
        assertThat(result.fields())
                .extracting(AiRuntimeReportReadFacade.Field::fieldCode)
                .containsExactly("status", "amount");
        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().getFirst().values())
                .extracting(AiRuntimeReportReadFacade.Value::displayValue)
                .containsExactly(null, "12345678901234567890.123400");
        assertThat(result.rows().get(1).values())
                .extracting(AiRuntimeReportReadFacade.Value::displayValue)
                .containsExactly("Masked", "0.00");
        assertThat(result.returnedRows()).isEqualTo(2);
        assertThat(result.total()).isEqualTo(3L);
        assertThat(result.hasMore()).isTrue();
    }

    @Test
    void explosiveRawValuesAreNeverTouchedAndUnsafeEnvelopesNeverEscape() {
        var adapter = adapter(new FixedRepository(),
                new TrackingSourceRuntime());

        var result = adapter.query(request(
                1L, 2L, VIEW, Set.of(MODULE),
                Map.of(MODULE, Set.of("status", "amount", "secret_note")),
                MODULE, 1, 2, 10));

        assertThat(result.rows()).hasSize(2);
        assertThat(AiRuntimeReportReadFacade.Value.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("fieldCode", "displayValue")
                .doesNotContain("value");
        assertThat(AiRuntimeReportReadFacade.Row.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("values")
                .doesNotContain("recordId", "recordNo", "version", "status",
                        "title");
        assertThat(AiRuntimeReportReadFacade.Result.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("queryHash", "reportId", "versionId",
                        "dataSourceId", "dataSourceVersionId",
                        "schemaVersionId");
    }

    @Test
    void silentlyOmitsFieldsOutsidePolicyAndDeniesAnEmptyIntersection() {
        var source = new TrackingSourceRuntime();
        var adapter = adapter(new FixedRepository(), source);

        var oneField = adapter.query(request(
                1L, 2L, VIEW, Set.of(MODULE),
                Map.of(MODULE, Set.of("amount")), MODULE, 1, 2, 10));
        assertThat(oneField.fields())
                .extracting(AiRuntimeReportReadFacade.Field::fieldCode)
                .containsExactly("amount");
        assertThat(oneField.rows().getFirst().values())
                .extracting(AiRuntimeReportReadFacade.Value::fieldCode)
                .containsExactly("amount");

        var rowsBefore = source.rowsReads;
        assertThatThrownBy(() -> adapter.query(request(
                1L, 2L, VIEW, Set.of(MODULE),
                Map.of(MODULE, Set.of("not_in_report")),
                MODULE, 1, 2, 10)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("AI_POLICY_FIELD_DENIED"));
        assertThat(source.rowsReads).isEqualTo(rowsBefore);
    }

    @Test
    void permissionAndPolicyModuleRevocationStopBeforeOwnerRows() {
        var repository = new FixedRepository();
        var source = new TrackingSourceRuntime();
        var adapter = adapter(repository, source);

        assertThatThrownBy(() -> adapter.query(request(
                1L, 2L, Set.of("system.runtime.access"), Set.of(MODULE),
                Map.of(MODULE, Set.of("status")),
                MODULE, 1, 2, 10)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("PERMISSION_DENIED"));
        assertThatThrownBy(() -> adapter.query(request(
                1L, 2L, VIEW, Set.of("other_module"),
                Map.of("other_module", Set.of("status")),
                MODULE, 1, 2, 10)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("AI_POLICY_MODULE_DENIED"));

        assertThat(repository.activeVersionReads).isZero();
        assertThat(source.rowsReads).isZero();
    }

    @Test
    void rejectsTheRealModuleMismatchAndHidesForeignTenantReports() {
        var repository = new FixedRepository();
        var source = new TrackingSourceRuntime();
        var adapter = adapter(repository, source);
        var otherView = Set.of(
                "system.runtime.access", "module.other_module.view");

        assertThatThrownBy(() -> adapter.query(request(
                1L, 2L, otherView, Set.of("other_module"),
                Map.of("other_module", Set.of("status")),
                "other_module", 1, 2, 10)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("AI_POLICY_MODULE_DENIED"));
        assertThat(source.rowsReads).isZero();

        assertThatThrownBy(() -> adapter.query(request(
                1L, 99L, VIEW, Set.of(MODULE),
                Map.of(MODULE, Set.of("status")),
                MODULE, 1, 2, 10)))
                .isInstanceOfSatisfying(ReportException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("REPORT_NOT_FOUND"));
        assertThat(source.rowsReads).isZero();
    }

    @Test
    void validatesPageBoundsAndExposesOnlyAReadOnlyOwnerPort() throws Exception {
        assertThatThrownBy(() -> request(
                1L, 2L, VIEW, Set.of(MODULE),
                Map.of(MODULE, Set.of("status")),
                MODULE, 10_001, 1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
        assertThatThrownBy(() -> request(
                1L, 2L, VIEW, Set.of(MODULE),
                Map.of(MODULE, Set.of("status")),
                MODULE, 1, 11, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRows");

        assertThat(Modifier.isFinal(
                AiRuntimeReportReadAdapter.class.getModifiers())).isFalse();
        var method = AiRuntimeReportReadAdapter.class.getMethod(
                "query", AiRuntimeReportReadFacade.Request.class);
        assertThat(method.getAnnotation(Transactional.class)).satisfies(annotation ->
                assertThat(annotation.readOnly()).isTrue());
        assertThat(AiRuntimeReportReadAdapter.class.getDeclaredFields())
                .filteredOn(field -> !Modifier.isStatic(field.getModifiers()))
                .extracting(field -> field.getType().getName())
                .containsExactly(ReportRuntimeService.class.getName());
    }

    private static AiRuntimeReportReadAdapter adapter(
            FixedRepository repository,
            TrackingSourceRuntime source
    ) {
        var reports = new ReportService(
                repository, new EmptySourceCatalog(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new AiRuntimeReportReadAdapter(
                new ReportRuntimeService(reports, source));
    }

    private static AiRuntimeReportReadFacade.Request request(
            long systemId,
            long tenantId,
            Set<String> permissions,
            Set<String> modules,
            Map<String, Set<String>> fields,
            String moduleCode,
            int page,
            int size,
            int maxRows
    ) {
        return new AiRuntimeReportReadFacade.Request(
                systemId, tenantId, 3L, permissions, modules, fields,
                maxRows, moduleCode, "ops_report", page, size);
    }

    private static ReportDefinition root() {
        return new ReportDefinition(
                10L, 1L, 2L, "ops_report", "Operations",
                "Must not escape", new ReportDraft(
                30L, List.of("status", "amount", "secret_note")),
                1L, 20L, 1, NOW, NOW, 2L);
    }

    private static ReportVersion version() {
        return new ReportVersion(
                20L, 10L, 1L, 2L, 1, 1L,
                "ops_report", "Operations", "Must not escape",
                new ReportSourcePin(
                        30L, "ops_source", "Operations source",
                        40L, 3, 50L, MODULE, "schema-7", List.of(
                        new ReportFieldPin(
                                101L, "status", "Pinned status",
                                "STATUS", "STATUS"),
                        new ReportFieldPin(
                                102L, "amount", "Pinned amount",
                                "NUMBER", "NUMBER"),
                        new ReportFieldPin(
                                103L, "secret_note", "Pinned secret",
                                "TEXT", "TEXT"))),
                "a".repeat(64), 3L, NOW);
    }

    private static final class ExplosiveRawValue {
        @Override
        public String toString() {
            throw new AssertionError("raw report value was touched");
        }

        @Override
        public int hashCode() {
            throw new AssertionError("raw report value was hashed");
        }

        @Override
        public boolean equals(Object other) {
            throw new AssertionError("raw report value was compared");
        }
    }

    private static final class TrackingSourceRuntime
            implements ReportDataSourceRuntime {
        private final java.util.ArrayList<Long> metadataVersionIds =
                new java.util.ArrayList<>();
        private final java.util.ArrayList<Long> rowsVersionIds =
                new java.util.ArrayList<>();
        private int rowsReads;
        private int lastPage;
        private int lastSize;

        @Override
        public SourceMetadata metadata(
                com.unique.examine.module.runtime.security.RuntimeSession session,
                long dataSourceId,
                long dataSourceVersionId
        ) {
            metadataVersionIds.add(dataSourceVersionId);
            return new SourceMetadata(
                    "30", "ops_source", "Operations source", MODULE,
                    "40", 3, "schema-7", List.of(
                    new SourceField("amount", "Amount", "NUMBER"),
                    new SourceField("status", "Status", "STATUS"),
                    new SourceField("secret_note", "Secret", "TEXT")));
        }

        @Override
        public SourceRows rows(
                com.unique.examine.module.runtime.security.RuntimeSession session,
                long dataSourceId,
                long dataSourceVersionId,
                int page,
                int size
        ) {
            rowsReads++;
            rowsVersionIds.add(dataSourceVersionId);
            lastPage = page;
            lastSize = size;
            return new SourceRows(List.of(
                    row("501", null, "12345678901234567890.123400", "Hidden"),
                    row("502", "Masked", "0.00", null)),
                    page, size, 3L, "must-not-escape-query-hash");
        }

        private static SourceRow row(
                String recordId,
                String status,
                String amount,
                String secret
        ) {
            return new SourceRow(
                    recordId, "MUST-NOT-ESCAPE-" + recordId,
                    99L, "MUST_NOT_ESCAPE", "Must not escape", List.of(
                    new SourceValue(
                            "amount", "Amount", "NUMBER",
                            new ExplosiveRawValue(), amount),
                    new SourceValue(
                            "status", "Status", "STATUS",
                            new ExplosiveRawValue(), status),
                    new SourceValue(
                            "secret_note", "Secret", "TEXT",
                            new ExplosiveRawValue(), secret)));
        }
    }

    private static final class FixedRepository implements ReportRepository {
        private final ReportDefinition root = root();
        private final ReportVersion version = version();
        private int activeVersionReads;
        private int exactVersionReads;

        @Override
        public long nextReportId() {
            throw unsupported();
        }

        @Override
        public long nextVersionId() {
            throw unsupported();
        }

        @Override
        public Optional<ReportDefinition> findById(
                long systemId, long tenantId, long reportId) {
            return scoped(systemId, tenantId) && reportId == root.id()
                    ? Optional.of(root) : Optional.empty();
        }

        @Override
        public Optional<ReportDefinition> findByCode(
                long systemId, long tenantId, String code) {
            return scoped(systemId, tenantId) && root.code().equals(code)
                    ? Optional.of(root) : Optional.empty();
        }

        @Override
        public List<ReportDefinition> findAll(long systemId, long tenantId) {
            return scoped(systemId, tenantId) ? List.of(root) : List.of();
        }

        @Override
        public ReportDefinition insert(ReportDefinition root) {
            throw unsupported();
        }

        @Override
        public ReportDefinition saveDraft(
                ReportDefinition expected,
                ReportDefinition revised) {
            throw unsupported();
        }

        @Override
        public ReportVersion publish(
                ReportDefinition expected,
                ReportDefinition activated,
                ReportVersion version) {
            throw unsupported();
        }

        @Override
        public Optional<ReportVersion> findActiveVersion(
                long systemId, long tenantId, long reportId) {
            activeVersionReads++;
            return scoped(systemId, tenantId) && reportId == root.id()
                    ? Optional.of(version) : Optional.empty();
        }

        @Override
        public Optional<ReportVersion> findVersion(
                long systemId,
                long tenantId,
                long reportId,
                int versionNumber) {
            return scoped(systemId, tenantId) && reportId == root.id()
                    && versionNumber == version.versionNumber()
                    ? Optional.of(version) : Optional.empty();
        }

        @Override
        public Optional<ReportVersion> findVersionById(
                long systemId,
                long tenantId,
                long reportId,
                long versionId) {
            exactVersionReads++;
            return scoped(systemId, tenantId) && reportId == root.id()
                    && versionId == version.id()
                    ? Optional.of(version) : Optional.empty();
        }

        @Override
        public List<ReportVersion> findVersions(
                long systemId, long tenantId, long reportId) {
            return findActiveVersion(systemId, tenantId, reportId)
                    .stream().toList();
        }

        private boolean scoped(long systemId, long tenantId) {
            return systemId == root.systemId() && tenantId == root.tenantId();
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException();
        }
    }

    private static final class EmptySourceCatalog
            implements ReportSourceCatalog {
        @Override
        public Optional<SourceVersion> active(
                long systemId, long tenantId, long dataSourceId) {
            return Optional.empty();
        }

        @Override
        public Optional<SourceVersion> version(
                long systemId,
                long tenantId,
                long dataSourceId,
                long dataSourceVersionId) {
            return Optional.empty();
        }
    }
}
