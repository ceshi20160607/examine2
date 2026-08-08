package com.unique.examine.module.report.service;

import com.unique.examine.module.report.domain.PublishedReport;
import com.unique.examine.module.report.domain.ReportActor;
import com.unique.examine.module.report.domain.ReportDefinition;
import com.unique.examine.module.report.domain.ReportDraft;
import com.unique.examine.module.report.domain.ReportException;
import com.unique.examine.module.report.domain.ReportFieldPin;
import com.unique.examine.module.report.domain.ReportVersion;
import com.unique.examine.module.report.port.ReportRepository;
import com.unique.examine.module.report.port.ReportSourceCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportServiceTest {
    private static final ReportActor ACTOR = new ReportActor(10, 20, 30);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-04T00:10:00Z"), ZoneOffset.UTC);

    private InMemoryRepository repository;
    private MutableSourceCatalog sources;
    private ReportService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        sources = new MutableSourceCatalog();
        sources.add(10, 20, 100, 501, 1);
        service = new ReportService(repository, sources, CLOCK);
    }

    @Test
    void publicationPinsExactSourceReplaysAndHistoryStaysImmutable() {
        var root = create("salesReport", List.of("title", "amount"));

        var checked = service.check(ACTOR, root.id());
        assertThat(checked.publishable()).isTrue();
        assertThat(checked.source().fields())
                .extracting(field -> field.code())
                .containsExactly("title", "amount", "secret");
        var first = service.publish(ACTOR, root.id(), 1);
        var replay = service.publish(ACTOR, root.id(), 1);

        assertThat(replay).isEqualTo(first);
        assertThat(repository.publishCount).isEqualTo(1);
        assertThat(first.source())
                .satisfies(source -> {
                    assertThat(source.dataSourceVersionId()).isEqualTo(501);
                    assertThat(source.dataSourceVersionNumber()).isEqualTo(1);
                    assertThat(source.moduleId()).isEqualTo(300);
                    assertThat(source.schemaVersionId()).isEqualTo("schema-1");
                    assertThat(source.fields())
                            .extracting(ReportFieldPin::code)
                            .containsExactly("title", "amount");
                });

        var normalized = service.saveDraft(
                ACTOR, root.id(), 1, "  Sales report  ", "   ", root.draft());
        assertThat(service.publish(
                ACTOR, root.id(), normalized.draftVersion())).isEqualTo(first);
        assertThat(repository.publishCount).isEqualTo(1);

        var revised = service.saveDraft(
                ACTOR, root.id(), normalized.draftVersion(),
                "Sales report", "reordered",
                new ReportDraft(100, List.of("amount", "title")));
        var second = service.publish(
                ACTOR, root.id(), revised.draftVersion());
        assertThat(second.versionNumber()).isEqualTo(2);
        assertThat(second.source().fields())
                .extracting(ReportFieldPin::code)
                .containsExactly("amount", "title");
        assertThat(first.source().fields())
                .extracting(ReportFieldPin::code)
                .containsExactly("title", "amount");

        sources.add(10, 20, 100, 502, 2);
        var third = service.publish(
                ACTOR, root.id(), revised.draftVersion());
        assertThat(third.versionNumber()).isEqualTo(3);
        assertThat(third.source().dataSourceVersionId()).isEqualTo(502);
        assertThat(first.source().dataSourceVersionId()).isEqualTo(501);
        assertThat(service.versions(ACTOR, root.id()))
                .extracting(ReportVersion::versionNumber)
                .containsExactly(3, 2, 1);

        assertThat(service.active(ACTOR, "salesReport").version())
                .isEqualTo(third);
        assertThat(service.publication(ACTOR, root.id(), first.id()).version())
                .isEqualTo(first);
    }

    @Test
    void restoreCreatesDraftThenExplicitPublishMovesRuntime() {
        var root = create("salesReport", List.of("title", "amount"));
        var first = service.publish(ACTOR, root.id(), 1);
        var revised = service.saveDraft(ACTOR, root.id(), 1,
                "Changed", null,
                new ReportDraft(100, List.of("amount", "title")));
        var second = service.publish(
                ACTOR, root.id(), revised.draftVersion());

        var restored = service.restoreVersion(ACTOR, root.id(),
                first.versionNumber(), revised.draftVersion());

        assertThat(restored.draftVersion()).isEqualTo(3);
        assertThat(restored.draft().outputFieldCodes())
                .containsExactly("title", "amount");
        assertThat(restored.activeVersionId()).isEqualTo(second.id());
        assertThat(service.active(ACTOR, root.id()).version())
                .isEqualTo(second);
        assertThat(service.check(ACTOR, root.id()).publishable()).isTrue();

        var republished = service.publish(
                ACTOR, root.id(), restored.draftVersion());
        assertThat(republished.versionNumber()).isEqualTo(3);
        assertThat(republished.source().fields())
                .extracting(ReportFieldPin::code)
                .containsExactly("title", "amount");
        assertThat(service.version(ACTOR, root.id(), 1)).isEqualTo(first);
    }

    @Test
    void checkReturnsCapabilitiesAndBlocksEmptyDuplicateMissingAndUnavailable() {
        var empty = create("emptyReport", List.of());
        assertThat(service.check(ACTOR, empty.id()).issues())
                .extracting(issue -> issue.code())
                .containsExactly("OUTPUT_FIELDS_REQUIRED");

        var invalid = create("invalidReport",
                List.of("title", "title", "missing", "secret"));
        var result = service.check(ACTOR, invalid.id());

        assertThat(result.source().fields())
                .extracting(field -> field.code(), field -> field.readable())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("title", true),
                        org.assertj.core.groups.Tuple.tuple("amount", true),
                        org.assertj.core.groups.Tuple.tuple("secret", false));
        assertThat(result.issues()).extracting(issue -> issue.code())
                .containsExactly("OUTPUT_FIELD_DUPLICATE",
                        "OUTPUT_FIELD_NOT_FOUND", "OUTPUT_FIELD_UNAVAILABLE");
        assertThatThrownBy(() -> service.publish(ACTOR, invalid.id(), 1))
                .isInstanceOf(ReportException.class)
                .satisfies(error -> assertThat(((ReportException) error).code())
                        .isEqualTo("REPORT_CHECK_BLOCKED"));
        assertThat(repository.publishCount).isZero();
    }

    @Test
    void optimisticWritesCodeUniquenessAndTenantHidingFailClosed() {
        var root = create("salesReport", List.of("title"));

        assertThatThrownBy(() -> create("salesReport", List.of("amount")))
                .isInstanceOf(ReportException.class)
                .satisfies(error -> assertThat(((ReportException) error).code())
                        .isEqualTo("REPORT_CODE_CONFLICT"));
        assertThatThrownBy(() -> service.saveDraft(
                ACTOR, root.id(), 9, "Changed", null, root.draft()))
                .isInstanceOf(ReportException.class)
                .satisfies(error -> assertThat(((ReportException) error).code())
                        .isEqualTo("REPORT_VERSION_CONFLICT"));
        assertThatThrownBy(() -> service.detail(
                new ReportActor(10, 21, 30), root.id()))
                .isInstanceOf(ReportException.class)
                .satisfies(error -> assertThat(((ReportException) error).code())
                        .isEqualTo("REPORT_NOT_FOUND"));
        assertThatThrownBy(() -> service.active(
                new ReportActor(10, 21, 30), "salesReport"))
                .isInstanceOf(ReportException.class)
                .satisfies(error -> assertThat(((ReportException) error).code())
                        .isEqualTo("REPORT_NOT_FOUND"));
    }

    @Test
    void crossTenantCatalogAnswerIsHiddenAsUnavailable() {
        var root = create("hiddenSource", List.of("title"));
        var scoped = sources.source(10, 20, 100).orElseThrow();
        sources.putForRequest(10, 20, 100,
                new ReportSourceCatalog.SourceVersion(
                        scoped.dataSourceId(), scoped.systemId(), 21,
                        scoped.dataSourceCode(), scoped.dataSourceName(),
                        scoped.dataSourceVersionId(),
                        scoped.dataSourceVersionNumber(), scoped.moduleId(),
                        scoped.moduleCode(), scoped.schemaVersionId(),
                        scoped.fields()));

        var result = service.check(ACTOR, root.id());

        assertThat(result.source()).isNull();
        assertThat(result.issues()).extracting(issue -> issue.code())
                .containsExactly("SOURCE_UNAVAILABLE");
    }

    @Test
    void listIsStableAndUnpublishedActiveReadFailsClosed() {
        create("zReport", List.of("title"));
        var first = create("aReport", List.of("amount"));

        assertThat(service.list(ACTOR)).extracting(ReportDefinition::code)
                .containsExactly("aReport", "zReport");
        assertThatThrownBy(() -> service.active(ACTOR, first.id()))
                .isInstanceOf(ReportException.class)
                .satisfies(error -> assertThat(((ReportException) error).code())
                        .isEqualTo("REPORT_UNPUBLISHED"));
    }

    private ReportDefinition create(String code, List<String> fields) {
        return service.create(ACTOR, code, "Sales report", null,
                new ReportDraft(100, fields));
    }

    private static final class MutableSourceCatalog
            implements ReportSourceCatalog {
        private final Map<SourceKey, SourceVersion> active = new HashMap<>();
        private final Map<VersionKey, SourceVersion> versions = new HashMap<>();

        private void add(
                long systemId,
                long tenantId,
                long sourceId,
                long versionId,
                int versionNumber
        ) {
            var value = source(systemId, tenantId, sourceId,
                    versionId, versionNumber);
            active.put(new SourceKey(systemId, tenantId, sourceId), value);
            versions.put(new VersionKey(
                    systemId, tenantId, sourceId, versionId), value);
        }

        private void putForRequest(
                long systemId,
                long tenantId,
                long sourceId,
                SourceVersion value
        ) {
            active.put(new SourceKey(systemId, tenantId, sourceId), value);
        }

        private Optional<SourceVersion> source(
                long systemId, long tenantId, long sourceId) {
            return active(systemId, tenantId, sourceId);
        }

        @Override
        public Optional<SourceVersion> active(
                long systemId, long tenantId, long dataSourceId) {
            return Optional.ofNullable(active.get(
                    new SourceKey(systemId, tenantId, dataSourceId)));
        }

        @Override
        public Optional<SourceVersion> version(
                long systemId,
                long tenantId,
                long dataSourceId,
                long dataSourceVersionId
        ) {
            return Optional.ofNullable(versions.get(new VersionKey(
                    systemId, tenantId, dataSourceId, dataSourceVersionId)));
        }

        private static SourceVersion source(
                long systemId,
                long tenantId,
                long sourceId,
                long versionId,
                int versionNumber
        ) {
            return new SourceVersion(
                    sourceId, systemId, tenantId, "orders", "Orders",
                    versionId, versionNumber, 300, "orders_module",
                    "schema-" + versionNumber, List.of(
                    new Field(901, "title", "Title", "TEXT", "TEXT", true),
                    new Field(902, "amount", "Amount", "NUMBER", "NUMBER", true),
                    new Field(903, "secret", "Secret", "TEXT", "TEXT", false)));
        }

        private record SourceKey(long systemId, long tenantId, long sourceId) {
        }

        private record VersionKey(
                long systemId,
                long tenantId,
                long sourceId,
                long versionId
        ) {
        }
    }

    private static final class InMemoryRepository implements ReportRepository {
        private long rootSequence = 100;
        private long versionSequence = 500;
        private int publishCount;
        private final Map<RootKey, ReportDefinition> roots =
                new LinkedHashMap<>();
        private final Map<Long, List<ReportVersion>> versions = new HashMap<>();

        @Override public long nextReportId() { return ++rootSequence; }
        @Override public long nextVersionId() { return ++versionSequence; }

        @Override
        public Optional<ReportDefinition> findById(
                long systemId, long tenantId, long reportId) {
            return Optional.ofNullable(roots.get(
                    new RootKey(systemId, tenantId, reportId)));
        }

        @Override
        public Optional<ReportDefinition> findByCode(
                long systemId, long tenantId, String code) {
            return roots.values().stream().filter(root ->
                    root.systemId() == systemId && root.tenantId() == tenantId
                            && root.code().equals(code)).findFirst();
        }

        @Override
        public List<ReportDefinition> findAll(long systemId, long tenantId) {
            return roots.values().stream().filter(root ->
                    root.systemId() == systemId
                            && root.tenantId() == tenantId).toList();
        }

        @Override
        public ReportDefinition insert(ReportDefinition root) {
            roots.put(key(root), root);
            return root;
        }

        @Override
        public ReportDefinition saveDraft(
                ReportDefinition expected, ReportDefinition revised) {
            if (!roots.replace(key(expected), expected, revised)) {
                throw conflict();
            }
            return revised;
        }

        @Override
        public ReportVersion publish(
                ReportDefinition expected,
                ReportDefinition activated,
                ReportVersion version
        ) {
            if (!roots.replace(key(expected), expected, activated)) {
                throw conflict();
            }
            versions.computeIfAbsent(expected.id(), ignored ->
                    new ArrayList<>()).add(version);
            publishCount++;
            return version;
        }

        @Override
        public Optional<ReportVersion> findActiveVersion(
                long systemId, long tenantId, long reportId) {
            return findById(systemId, tenantId, reportId)
                    .filter(root -> root.activeVersionId() != null)
                    .flatMap(root -> findVersionById(systemId, tenantId,
                            reportId, root.activeVersionId()));
        }

        @Override
        public Optional<ReportVersion> findVersion(
                long systemId,
                long tenantId,
                long reportId,
                int versionNumber
        ) {
            return scopedVersions(systemId, tenantId, reportId).stream()
                    .filter(value -> value.versionNumber() == versionNumber)
                    .findFirst();
        }

        @Override
        public Optional<ReportVersion> findVersionById(
                long systemId,
                long tenantId,
                long reportId,
                long versionId
        ) {
            return scopedVersions(systemId, tenantId, reportId).stream()
                    .filter(value -> value.id() == versionId).findFirst();
        }

        @Override
        public List<ReportVersion> findVersions(
                long systemId, long tenantId, long reportId) {
            return List.copyOf(scopedVersions(systemId, tenantId, reportId));
        }

        private List<ReportVersion> scopedVersions(
                long systemId, long tenantId, long reportId) {
            return findById(systemId, tenantId, reportId).isPresent()
                    ? versions.getOrDefault(reportId, List.of()) : List.of();
        }

        private static RootKey key(ReportDefinition root) {
            return new RootKey(root.systemId(), root.tenantId(), root.id());
        }

        private static ReportException conflict() {
            return new ReportException("REPORT_VERSION_CONFLICT", "Stale state");
        }

        private record RootKey(long systemId, long tenantId, long reportId) {
        }
    }
}
