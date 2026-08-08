package com.unique.examine.module.datasource.service;

import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.domain.DataSourceVersion;
import com.unique.examine.module.datasource.domain.ModuleDataSource;
import com.unique.examine.module.datasource.port.DataSourceModuleCatalog;
import com.unique.examine.module.datasource.port.DataSourceRepository;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataSourceServiceTest {
    private static final DataSourceActor ACTOR =
            new DataSourceActor(10, 20, 30);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-01T01:02:03Z"), ZoneOffset.UTC);

    private InMemoryRepository repository;
    private MutableCatalog catalog;
    private DataSourceService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        catalog = new MutableCatalog(module("schema-1"));
        service = new DataSourceService(repository, catalog, CLOCK);
    }

    @Test
    void checkIsReadOnlyAndReportsIssuesInStableDraftOrder() {
        var root = service.create(ACTOR, "orders", 91, "Orders", null,
                new DataSourceDraft(
                        List.of(
                                output("title"),
                                output("title"),
                                output("missing")),
                        List.of(filter("priority", "EQ", "bad")),
                        sort("title"), "title"));

        var report = service.check(ACTOR, root.id());

        assertThat(report.schemaVersionId()).isEqualTo("schema-1");
        assertThat(report.issues()).extracting(issue -> issue.code())
                .containsExactly(
                        "OUTPUT_FIELD_DUPLICATE",
                        "OUTPUT_FIELD_NOT_FOUND",
                        "FILTER_VALUE_INVALID",
                        "DEFAULT_SORT_FIELD_UNSORTABLE",
                        "DEFAULT_TIME_FIELD_NOT_TEMPORAL");
        assertThat(report.blockerCount()).isEqualTo(5);
        assertThat(report.warningCount()).isZero();
        assertThat(report.publishable()).isFalse();
        assertThat(repository.saveCount).isZero();
        assertThat(repository.publishCount).isZero();
    }

    @Test
    void checkEmitsWarningsForOptionalDefaultsWithoutBlockingPublish() {
        var root = service.create(ACTOR, "orders", 91, "Orders", null,
                new DataSourceDraft(
                        List.of(output("title")), List.of(), null, null));

        var report = service.check(ACTOR, root.id());

        assertThat(report.issues()).extracting(issue -> issue.code())
                .containsExactly(
                        "DEFAULT_SORT_MISSING",
                        "DEFAULT_TIME_FIELD_MISSING");
        assertThat(report.blockerCount()).isZero();
        assertThat(report.warningCount()).isEqualTo(2);
        assertThat(report.publishable()).isTrue();
        assertThat(service.modules(ACTOR))
                .extracting(value -> value.moduleName())
                .containsExactly("Orders module");
    }

    @Test
    void publishCanonicalizesSnapshotReplaysUnchangedAndKeepsHistoryImmutable() {
        var preflight = new RecordingPreflight();
        service = new DataSourceService(
                repository, catalog, CLOCK, preflight);
        var root = service.create(ACTOR, "orders", 91, "Orders", null,
                validDraft("07"));

        var first = service.publish(ACTOR, root.id(), 1);
        var replay = service.publish(ACTOR, root.id(), 1);

        assertThat(replay).isEqualTo(first);
        assertThat(first.snapshot().fixedFilters().getFirst().canonicalValue())
                .isEqualTo("7");
        assertThat(repository.publishCount).isEqualTo(1);

        var revised = service.revise(
                ACTOR, root.id(), 1, "Orders", "next draft",
                validDraft("08"));
        var second = service.publish(
                ACTOR, root.id(), revised.draftVersion());

        assertThat(second.versionNumber()).isEqualTo(2);
        assertThat(first.snapshot().fixedFilters().getFirst().canonicalValue())
                .isEqualTo("7");
        assertThat(second.snapshot().fixedFilters().getFirst().canonicalValue())
                .isEqualTo("8");

        catalog.module = module("schema-2");
        var third = service.publish(
                ACTOR, root.id(), revised.draftVersion());

        assertThat(third.versionNumber()).isEqualTo(3);
        assertThat(third.schemaVersionId()).isEqualTo("schema-2");
        assertThat(service.versions(ACTOR, root.id()))
                .extracting(DataSourceVersion::versionNumber)
                .containsExactly(3, 2, 1);
        assertThat(service.version(ACTOR, root.id(), 1)).isEqualTo(first);
        assertThat(service.active(ACTOR, "orders").version()).isEqualTo(third);
        assertThat(repository.publishCount).isEqualTo(3);
        assertThat(preflight.calls).isZero();
    }

    @Test
    void restoreCreatesDraftThenExplicitPublishMovesRuntime() {
        var root = service.create(ACTOR, "orders", 91, "Orders", null,
                validDraft("1"));
        var first = service.publish(ACTOR, root.id(), 1);
        var revised = service.saveDraft(ACTOR, root.id(), 1,
                "Orders changed", null, validDraft("2"));
        var second = service.publish(
                ACTOR, root.id(), revised.draftVersion());

        var restored = service.restoreVersion(ACTOR, root.id(),
                first.versionNumber(), revised.draftVersion());

        assertThat(restored.draftVersion()).isEqualTo(3);
        assertThat(restored.draft()).isEqualTo(first.snapshot());
        assertThat(restored.activeVersionId()).isEqualTo(second.id());
        assertThat(service.active(ACTOR, "orders").version()).isEqualTo(second);
        assertThat(service.check(ACTOR, root.id()).publishable()).isTrue();

        var republished = service.publish(
                ACTOR, root.id(), restored.draftVersion());
        assertThat(republished.versionNumber()).isEqualTo(3);
        assertThat(republished.snapshot()).isEqualTo(first.snapshot());
        assertThat(service.active(ACTOR, "orders").version())
                .isEqualTo(republished);
        assertThat(service.version(ACTOR, root.id(), 1)).isEqualTo(first);
    }

    @Test
    void draftCompareAndSwapAndTenantScopeAreEnforced() {
        var root = service.create(ACTOR, "orders", 91, "Orders", null,
                validDraft("1"));
        var otherTenant = new DataSourceActor(10, 21, 31);

        assertThatThrownBy(() -> service.revise(
                ACTOR, root.id(), 9, "Changed", null, validDraft("2")))
                .isInstanceOf(DataSourceException.class)
                .satisfies(error -> assertThat(
                        ((DataSourceException) error).code())
                        .isEqualTo("DATA_SOURCE_VERSION_CONFLICT"));
        assertThatThrownBy(() -> service.detail(otherTenant, root.id()))
                .isInstanceOf(DataSourceException.class)
                .satisfies(error -> assertThat(
                        ((DataSourceException) error).code())
                        .isEqualTo("DATA_SOURCE_NOT_FOUND"));

        var sameCodeOtherTenant = service.create(
                otherTenant, "orders", 91, "Other", null, validDraft("1"));
        assertThat(sameCodeOtherTenant.tenantId()).isEqualTo(21);
        assertThat(service.list(ACTOR)).containsExactly(root);
    }

    @Test
    void publishFailsWhenCurrentPublishedModuleNoLongerExists() {
        var root = service.create(ACTOR, "orders", 91, "Orders", null,
                validDraft("1"));
        catalog.module = null;

        assertThat(service.check(ACTOR, root.id()).issues())
                .extracting(issue -> issue.code())
                .contains("MODULE_UNAVAILABLE");
        assertThatThrownBy(() -> service.publish(ACTOR, root.id(), 1))
                .isInstanceOf(DataSourceException.class)
                .satisfies(error -> assertThat(
                        ((DataSourceException) error).code())
                        .isEqualTo("DATA_SOURCE_CHECK_BLOCKED"));
        assertThat(repository.publishCount).isZero();
    }

    @Test
    void threeArgumentServiceRejectsHttpPublicationWithoutPreflight() {
        var connection = new DataSourceDraft.HttpJsonConnection(
                "https://datasource.example.test/query",
                "env://EXAMINE_DS_S10_T20_ORDERS_V1", 5);
        var draft = new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.HTTP_JSON, connection,
                List.of(
                        projection("external_title", "title", "STRING"),
                        projection("external_amount", "amount", "INTEGER"),
                        projection("external_total", "total", "DECIMAL"),
                        projection("external_enabled", "enabled", "BOOLEAN")));
        var root = service.create(
                ACTOR, "external_orders", 91, "External orders", null,
                draft);

        assertThat(service.detail(ACTOR, root.id()).draft().sourceKind())
                .isEqualTo(DataSourceDraft.SourceKind.HTTP_JSON);
        assertThat(service.detail(ACTOR, root.id()).draft().httpConnection())
                .isEqualTo(connection);
        assertThat(service.detail(ACTOR, root.id()).draft()
                .httpFieldProjections()).containsExactlyElementsOf(
                draft.httpFieldProjections());
        assertThat(service.check(ACTOR, root.id()).issues())
                .extracting(issue -> issue.code())
                .containsExactly("SOURCE_RUNTIME_UNAVAILABLE");
        assertThatThrownBy(() -> service.publish(ACTOR, root.id(), 1))
                .isInstanceOf(DataSourceException.class)
                .satisfies(error -> assertThat(
                        ((DataSourceException) error).code())
                        .isEqualTo(
                                "DATA_SOURCE_PUBLICATION_PREFLIGHT_UNAVAILABLE"));
        assertThat(repository.publishCount).isZero();
    }

    @Test
    void newHttpPublicationRunsOnePreflightAndReplayRunsNone() {
        var preflight = new RecordingPreflight();
        service = new DataSourceService(
                repository, catalog, CLOCK, preflight);
        var draft = validHttpDraft();
        var root = service.create(
                ACTOR, "external_orders", 91, "External orders", null,
                draft);

        var first = service.publish(ACTOR, root.id(), root.draftVersion());
        var replay = service.publish(ACTOR, root.id(), root.draftVersion());

        assertThat(replay).isEqualTo(first);
        assertThat(first.snapshot()).isEqualTo(draft);
        assertThat(first.snapshot().httpFieldProjections())
                .containsExactlyElementsOf(draft.httpFieldProjections());
        assertThat(preflight.calls).isEqualTo(1);
        assertThat(preflight.actor).isEqualTo(ACTOR);
        assertThat(preflight.draft).isEqualTo(draft);
        assertThat(repository.publishCount).isEqualTo(1);
    }

    @Test
    void httpCheckKeepsNativeQueryFieldsSeparateFromAnchorMappings() {
        var preflight = new RecordingPreflight();
        service = new DataSourceService(
                repository, catalog, CLOCK, preflight);
        var connection = new DataSourceDraft.HttpJsonConnection(
                "https://datasource.example.test/query", null, 5);
        var draft = new DataSourceDraft(
                List.of(output("title")),
                List.of(filter("priority", "EQ", "1")),
                sort("title"), "title",
                DataSourceDraft.SourceKind.HTTP_JSON, connection,
                List.of(
                        projection("external_missing", "missing", "STRING"),
                        projection("external_deferred", "deferred", "STRING"),
                        projection("external_count", "title", "INTEGER")));
        var root = service.create(
                ACTOR, "external_invalid", 91, "Invalid external", null,
                draft);

        assertThat(service.check(ACTOR, root.id()).issues())
                .extracting(issue -> issue.code())
                .containsExactly(
                        "SOURCE_RUNTIME_UNAVAILABLE",
                        "HTTP_NATIVE_OUTPUT_FIELDS_FORBIDDEN",
                        "HTTP_NATIVE_FILTERS_FORBIDDEN",
                        "HTTP_NATIVE_SORT_FORBIDDEN",
                        "HTTP_NATIVE_TIME_FIELD_FORBIDDEN",
                        "HTTP_PROJECTION_FIELD_NOT_FOUND",
                        "HTTP_PROJECTION_FIELD_UNAVAILABLE",
                        "HTTP_PROJECTION_TYPE_INCOMPATIBLE");
        assertThatThrownBy(() -> service.publish(
                ACTOR, root.id(), root.draftVersion()))
                .isInstanceOfSatisfying(DataSourceException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("DATA_SOURCE_CHECK_BLOCKED"));
        assertThat(preflight.calls).isZero();
        assertThat(repository.publishCount).isZero();
    }

    @Test
    void httpStringProjectionCannotPublishToRichText() {
        var preflight = new RecordingPreflight();
        service = new DataSourceService(
                repository, catalog, CLOCK, preflight);
        var root = service.create(
                ACTOR, "external_rich", 91, "External rich text", null,
                new DataSourceDraft(
                        List.of(), List.of(), null, null,
                        DataSourceDraft.SourceKind.HTTP_JSON,
                        new DataSourceDraft.HttpJsonConnection(
                                "https://datasource.example.test/query",
                                null, 5),
                        List.of(projection(
                                "external_html", "rich", "STRING"))));

        assertThatThrownBy(() -> service.publish(
                ACTOR, root.id(), root.draftVersion()))
                .isInstanceOfSatisfying(DataSourceException.class,
                        error -> assertThat(error.code()).isEqualTo(
                                "DATA_SOURCE_HTTP_RICH_TEXT_BLOCKED"))
                .hasMessage(
                        "HTTP string projections cannot be published to rich text fields");
        assertThat(preflight.calls).isZero();
        assertThat(repository.publishCount).isZero();
    }

    @Test
    void failedHttpPreflightCreatesNoVersionOrActivePointer() {
        var preflight = new RecordingPreflight();
        preflight.failure = new DataSourceException(
                "DATA_SOURCE_PUBLICATION_PREFLIGHT_BLOCKED",
                "HTTP data source publication preflight failed");
        service = new DataSourceService(
                repository, catalog, CLOCK, preflight);
        var root = service.create(
                ACTOR, "external_failed", 91, "External failed", null,
                validHttpDraft());

        assertThatThrownBy(() -> service.publish(
                ACTOR, root.id(), root.draftVersion()))
                .isSameAs(preflight.failure);
        assertThat(preflight.calls).isEqualTo(1);
        assertThat(repository.publishCount).isZero();
        assertThat(repository.versions.getOrDefault(
                root.id(), List.of())).isEmpty();
        assertThat(service.detail(ACTOR, root.id()).activeVersionId()).isNull();
    }

    @Test
    void concurrentDraftChangeAfterPreflightFailsTheExistingPublishCas() {
        var preflight = new RecordingPreflight();
        service = new DataSourceService(
                repository, catalog, CLOCK, preflight);
        var root = service.create(
                ACTOR, "external_race", 91, "External race", null,
                validHttpDraft());
        preflight.afterVerify = () -> repository.concurrentRevision(root);

        assertThatThrownBy(() -> service.publish(
                ACTOR, root.id(), root.draftVersion()))
                .isInstanceOfSatisfying(DataSourceException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("DATA_SOURCE_VERSION_CONFLICT"));
        assertThat(preflight.calls).isEqualTo(1);
        assertThat(repository.publishCount).isZero();
        assertThat(repository.versions.getOrDefault(
                root.id(), List.of())).isEmpty();
        assertThat(service.detail(ACTOR, root.id()).activeVersionId()).isNull();
        assertThat(service.detail(ACTOR, root.id()).draftVersion()).isEqualTo(2);
    }

    @Test
    void b103HttpDraftCanBeSavedEmptyButCheckRequiresProjection() {
        var root = service.create(
                ACTOR, "external_empty", 91, "Empty external", null,
                new DataSourceDraft(
                        List.of(), List.of(), null, null,
                        DataSourceDraft.SourceKind.HTTP_JSON,
                        new DataSourceDraft.HttpJsonConnection(
                                "https://datasource.example.test/query",
                                null, 5)));

        assertThat(service.check(ACTOR, root.id()).issues())
                .extracting(issue -> issue.code())
                .containsExactly(
                        "SOURCE_RUNTIME_UNAVAILABLE",
                        "HTTP_PROJECTION_REQUIRED");
    }

    @Test
    void jdbcCheckValidatesAnchorMappingsAndDefaultServiceFailsClosed() {
        var root = service.create(
                ACTOR, "jdbc_orders", 91, "JDBC orders", null,
                validJdbcDraft());

        assertThat(service.check(ACTOR, root.id()).issues()).isEmpty();
        assertThatThrownBy(() -> service.publish(
                ACTOR, root.id(), root.draftVersion()))
                .isInstanceOfSatisfying(DataSourceException.class,
                        error -> assertThat(error.code()).isEqualTo(
                                "DATA_SOURCE_JDBC_PUBLICATION_PREFLIGHT_UNAVAILABLE"));
        assertThat(repository.publishCount).isZero();
    }

    @Test
    void jdbcPublicationRunsFreshPreflightOnceAndFreezesFullSnapshot() {
        var httpPreflight = new RecordingPreflight();
        var jdbcPreflight = new RecordingJdbcPreflight();
        service = new DataSourceService(
                repository, catalog, CLOCK,
                httpPreflight, jdbcPreflight);
        var draft = validJdbcDraft();
        var root = service.create(
                ACTOR, "jdbc_orders", 91, "JDBC orders", null, draft);

        var first = service.publish(
                ACTOR, root.id(), root.draftVersion());
        var replay = service.publish(
                ACTOR, root.id(), root.draftVersion());

        assertThat(replay).isEqualTo(first);
        assertThat(first.snapshot()).isEqualTo(draft);
        assertThat(first.snapshot().jdbcTableConnection())
                .isEqualTo(draft.jdbcTableConnection());
        assertThat(first.snapshot().jdbcFieldProjections())
                .containsExactlyElementsOf(draft.jdbcFieldProjections());
        assertThat(jdbcPreflight.calls).isEqualTo(1);
        assertThat(jdbcPreflight.actor).isEqualTo(ACTOR);
        assertThat(jdbcPreflight.draft).isEqualTo(draft);
        assertThat(httpPreflight.calls).isZero();
        assertThat(repository.publishCount).isEqualTo(1);
    }

    @Test
    void jdbcCheckRejectsNativeFieldsAndIncompatibleProjection() {
        var invalid = new DataSourceDraft(
                List.of(output("title")),
                List.of(filter("priority", "EQ", "1")),
                sort("title"), "title",
                DataSourceDraft.SourceKind.JDBC_TABLE, null, List.of(),
                jdbcConnection(), List.of(
                new DataSourceDraft.JdbcTableFieldProjection(
                        "missing_column", "missing",
                        DataSourceDraft.JdbcTableSourceType.STRING),
                new DataSourceDraft.JdbcTableFieldProjection(
                        "deferred_column", "deferred",
                        DataSourceDraft.JdbcTableSourceType.STRING),
                new DataSourceDraft.JdbcTableFieldProjection(
                        "count_column", "title",
                        DataSourceDraft.JdbcTableSourceType.INTEGER)));
        var root = service.create(
                ACTOR, "jdbc_invalid", 91, "Invalid JDBC", null, invalid);

        assertThat(service.check(ACTOR, root.id()).issues())
                .extracting(issue -> issue.code())
                .containsExactly(
                        "JDBC_NATIVE_OUTPUT_FIELDS_FORBIDDEN",
                        "JDBC_NATIVE_FILTERS_FORBIDDEN",
                        "JDBC_NATIVE_SORT_FORBIDDEN",
                        "JDBC_NATIVE_TIME_FIELD_FORBIDDEN",
                        "JDBC_PROJECTION_FIELD_NOT_FOUND",
                        "JDBC_PROJECTION_FIELD_UNAVAILABLE",
                        "JDBC_PROJECTION_TYPE_INCOMPATIBLE");
    }

    private static DataSourceDraft validDraft(String priority) {
        return new DataSourceDraft(
                List.of(output("title"), output("createdAt")),
                List.of(filter("priority", "EQ", priority)),
                sort("createdAt"), "createdAt");
    }

    private static DataSourceDraft validHttpDraft() {
        return new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.HTTP_JSON,
                new DataSourceDraft.HttpJsonConnection(
                        "https://datasource.example.test/query",
                        "env://EXAMINE_DS_S10_T20_ORDERS_V1", 5),
                List.of(
                        projection("external_title", "title", "STRING"),
                        projection("external_amount", "amount", "INTEGER"),
                        projection("external_total", "total", "DECIMAL"),
                        projection("external_enabled", "enabled", "BOOLEAN")));
    }

    private static DataSourceDraft validJdbcDraft() {
        return new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.JDBC_TABLE, null, List.of(),
                jdbcConnection(), List.of(
                new DataSourceDraft.JdbcTableFieldProjection(
                        "external_title", "title",
                        DataSourceDraft.JdbcTableSourceType.STRING),
                new DataSourceDraft.JdbcTableFieldProjection(
                        "external_amount", "amount",
                        DataSourceDraft.JdbcTableSourceType.DECIMAL),
                new DataSourceDraft.JdbcTableFieldProjection(
                        "external_enabled", "enabled",
                        DataSourceDraft.JdbcTableSourceType.BOOLEAN),
                new DataSourceDraft.JdbcTableFieldProjection(
                        "external_created_at", "createdAt",
                        DataSourceDraft.JdbcTableSourceType.DATETIME)));
    }

    private static DataSourceDraft.JdbcTableConnection jdbcConnection() {
        return new DataSourceDraft.JdbcTableConnection(
                "mysql.internal", 3306, "operations", "orders",
                "secret://systems/10/tenants/20/mysql-user",
                "secret://systems/10/tenants/20/mysql-password", 3, 5);
    }

    private static DataSourceDraft.OutputField output(String code) {
        return new DataSourceDraft.OutputField(code);
    }

    private static DataSourceDraft.FixedFilter filter(
            String code,
            String operator,
            String value
    ) {
        return new DataSourceDraft.FixedFilter(code, operator, value);
    }

    private static DataSourceDraft.DefaultSort sort(String code) {
        return new DataSourceDraft.DefaultSort(
                code, DataSourceDraft.Direction.ASC);
    }

    private static DataSourceDraft.HttpJsonFieldProjection projection(
            String sourceField,
            String fieldCode,
            String sourceType
    ) {
        return new DataSourceDraft.HttpJsonFieldProjection(
                sourceField, fieldCode,
                DataSourceDraft.HttpJsonSourceType.valueOf(sourceType));
    }

    private static DataSourceModuleCatalog.PublishedModule module(
            String schemaVersion
    ) {
        return new DataSourceModuleCatalog.PublishedModule(
                91, "orders_module", "Orders module", schemaVersion,
                List.of(
                        new DataSourceModuleCatalog.FieldCapability(
                                "title", "Title", "TEXT", Set.of("EQ"),
                                false, false, true),
                        new DataSourceModuleCatalog.FieldCapability(
                                "createdAt", "Created at", "DATETIME",
                                Set.of("EQ", "GTE", "LTE"),
                                true, true, true),
                        new DataSourceModuleCatalog.FieldCapability(
                                "priority", "Priority", "INTEGER",
                                Set.of("EQ"), true, false, true),
                        new DataSourceModuleCatalog.FieldCapability(
                                "amount", "Amount", "NUMBER",
                                Set.of("EQ"), true, false, true),
                        new DataSourceModuleCatalog.FieldCapability(
                                "total", "Total", "NUMBER",
                                Set.of("EQ"), true, false, true),
                        new DataSourceModuleCatalog.FieldCapability(
                                "enabled", "Enabled", "SWITCH",
                                Set.of("EQ"), true, false, true),
                        new DataSourceModuleCatalog.FieldCapability(
                                "rich", "Rich text", "RICH_TEXT",
                                Set.of("EQ"), false, false, true),
                        new DataSourceModuleCatalog.FieldCapability(
                                "deferred", "Deferred", "TEXT",
                                Set.of("EQ"), false, false, false)));
    }

    private static final class MutableCatalog
            implements DataSourceModuleCatalog {
        private PublishedModule module;

        private MutableCatalog(PublishedModule module) {
            this.module = module;
        }

        @Override
        public List<PublishedModule> publishedModules(
                long systemId,
                long tenantId
        ) {
            return module == null ? List.of() : List.of(module);
        }

        @Override
        public Optional<PublishedModule> publishedModule(
                long systemId,
                long tenantId,
                long moduleId
        ) {
            return Optional.ofNullable(module)
                    .filter(value -> value.moduleId() == moduleId);
        }

        @Override
        public CanonicalFilterValue canonicalizeFilterValue(
                long systemId,
                long tenantId,
                long moduleId,
                FieldCapability field,
                String operator,
                String canonicalValue
        ) {
            if ("bad".equals(canonicalValue)) {
                return CanonicalFilterValue.rejected(
                        "Priority must be an integer");
            }
            if ("priority".equals(field.code())) {
                return CanonicalFilterValue.accepted(
                        Integer.toString(Integer.parseInt(canonicalValue)));
            }
            return CanonicalFilterValue.accepted(canonicalValue);
        }
    }

    private static final class InMemoryRepository
            implements DataSourceRepository {
        private long rootSequence = 100;
        private long versionSequence = 500;
        private int saveCount;
        private int publishCount;
        private final Map<Key, ModuleDataSource> roots = new LinkedHashMap<>();
        private final Map<Long, List<DataSourceVersion>> versions =
                new HashMap<>();

        private void concurrentRevision(ModuleDataSource expected) {
            var current = roots.get(key(expected));
            roots.put(key(expected), current.reviseDraft(
                    current.name(), current.description(), current.draft(),
                    current.updatedAt().plusSeconds(1)));
        }

        @Override
        public long nextDataSourceId() {
            return ++rootSequence;
        }

        @Override
        public long nextVersionId() {
            return ++versionSequence;
        }

        @Override
        public Optional<ModuleDataSource> findById(
                long systemId,
                long tenantId,
                long dataSourceId
        ) {
            return Optional.ofNullable(
                    roots.get(new Key(systemId, tenantId, dataSourceId)));
        }

        @Override
        public Optional<ModuleDataSource> findByCode(
                long systemId,
                long tenantId,
                String code
        ) {
            return roots.values().stream()
                    .filter(root -> root.systemId() == systemId
                            && root.tenantId() == tenantId
                            && root.code().equals(code))
                    .findFirst();
        }

        @Override
        public List<ModuleDataSource> findAll(
                long systemId,
                long tenantId
        ) {
            return roots.values().stream()
                    .filter(root -> root.systemId() == systemId
                            && root.tenantId() == tenantId)
                    .toList();
        }

        @Override
        public ModuleDataSource insert(ModuleDataSource root) {
            var key = key(root);
            if (roots.putIfAbsent(key, root) != null) {
                throw new DataSourceException(
                        "DATA_SOURCE_CODE_CONFLICT", "Duplicate root");
            }
            return root;
        }

        @Override
        public ModuleDataSource saveDraft(
                ModuleDataSource expected,
                ModuleDataSource revised
        ) {
            var key = key(expected);
            if (!roots.replace(key, expected, revised)) {
                throw new DataSourceException(
                        "DATA_SOURCE_VERSION_CONFLICT", "Stale root");
            }
            saveCount++;
            return revised;
        }

        @Override
        public DataSourceVersion publish(
                ModuleDataSource expected,
                ModuleDataSource activated,
                DataSourceVersion version
        ) {
            var key = key(expected);
            if (!roots.replace(key, expected, activated)) {
                throw new DataSourceException(
                        "DATA_SOURCE_VERSION_CONFLICT", "Stale root");
            }
            versions.computeIfAbsent(expected.id(), ignored ->
                    new ArrayList<>()).add(version);
            publishCount++;
            return version;
        }

        @Override
        public Optional<DataSourceVersion> findActiveVersion(
                long systemId,
                long tenantId,
                long dataSourceId
        ) {
            var root = findById(systemId, tenantId, dataSourceId);
            if (root.isEmpty() || root.get().activeVersionNumber() == null) {
                return Optional.empty();
            }
            return findVersion(systemId, tenantId, dataSourceId,
                    root.get().activeVersionNumber());
        }

        @Override
        public Optional<DataSourceVersion> findVersion(
                long systemId,
                long tenantId,
                long dataSourceId,
                int versionNumber
        ) {
            if (findById(systemId, tenantId, dataSourceId).isEmpty()) {
                return Optional.empty();
            }
            return versions.getOrDefault(dataSourceId, List.of()).stream()
                    .filter(value -> value.versionNumber() == versionNumber)
                    .findFirst();
        }

        @Override
        public Optional<DataSourceVersion> findVersionById(
                long systemId,
                long tenantId,
                long dataSourceId,
                long versionId
        ) {
            if (findById(systemId, tenantId, dataSourceId).isEmpty()) {
                return Optional.empty();
            }
            return versions.getOrDefault(dataSourceId, List.of()).stream()
                    .filter(value -> value.id() == versionId)
                    .findFirst();
        }

        @Override
        public List<DataSourceVersion> findVersions(
                long systemId,
                long tenantId,
                long dataSourceId
        ) {
            if (findById(systemId, tenantId, dataSourceId).isEmpty()) {
                return List.of();
            }
            return List.copyOf(versions.getOrDefault(
                    dataSourceId, List.of()));
        }

        private static Key key(ModuleDataSource root) {
            return new Key(root.systemId(), root.tenantId(), root.id());
        }

        private record Key(long systemId, long tenantId, long id) {
        }
    }

    private static final class RecordingPreflight
            implements DataSourcePublicationPreflight {
        private int calls;
        private DataSourceActor actor;
        private DataSourceDraft draft;
        private DataSourceException failure;
        private Runnable afterVerify = () -> { };

        @Override
        public void verify(
                DataSourceActor actor,
                DataSourceDraft normalizedHttpDraft
        ) {
            calls++;
            this.actor = actor;
            this.draft = normalizedHttpDraft;
            if (failure != null) {
                throw failure;
            }
            afterVerify.run();
        }
    }

    private static final class RecordingJdbcPreflight
            implements JdbcTableDataSourcePublicationPreflight {
        private int calls;
        private DataSourceActor actor;
        private DataSourceDraft draft;

        @Override
        public void verify(
                DataSourceActor actor,
                DataSourceDraft normalizedJdbcDraft
        ) {
            calls++;
            this.actor = actor;
            this.draft = normalizedJdbcDraft;
        }
    }
}
