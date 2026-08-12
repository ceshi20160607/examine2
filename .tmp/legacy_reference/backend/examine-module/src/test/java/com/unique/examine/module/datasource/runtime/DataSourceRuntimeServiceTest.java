package com.unique.examine.module.datasource.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.domain.DataSourcePublication;
import com.unique.examine.module.datasource.domain.DataSourceVersion;
import com.unique.examine.module.datasource.domain.ModuleDataSource;
import com.unique.examine.module.datasource.port.DataSourceModuleCatalog;
import com.unique.examine.module.datasource.port.DataSourceRepository;
import com.unique.examine.module.datasource.service.DataSourceService;
import com.unique.examine.module.datasource.service.PublishedHttpDataSourceRowsReader;
import com.unique.examine.module.datasource.service.PublishedJdbcTableDataSourceRowsReader;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataSourceRuntimeServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");
    private static final DataSourceActor ACTOR = new DataSourceActor(10, 20, 30);
    private static final RuntimeSession SESSION = new RuntimeSession(
            40, 10, 30, 20L, Set.of("system.runtime.access"));

    @Test
    void delegatesToNativeQueryAndOmitsUnreadableSelectedFields() throws Exception {
        var repository = new MemoryRepository();
        var sourceService = sourceService(repository);
        var published = publish(sourceService);
        var gateway = new StubGateway(schema("501"));
        gateway.page = new RecordRuntimeViews.RecordPage(List.of(
                new RecordRuntimeViews.RecordSummary(
                        "901", "R-901", 3, "ACTIVE", "First", List.of(
                        new RecordRuntimeViews.FieldValue(
                                "title", "Title", "TEXT", "First", "First")))),
                1, 20, 1, "native-hash", "snapshot", List.of());
        var service = new DataSourceRuntimeService(
                sourceService, gateway, new ObjectMapper());

        var metadata = service.metadata(SESSION, "work_items");
        var rows = service.rows(SESSION, "work_items", 1, 20);
        var query = new ObjectMapper().readTree(gateway.queryJson);

        assertThat(metadata.versionId())
                .isEqualTo(Long.toString(published.id()));
        assertThat(metadata.outputFields())
                .extracting(value -> value.fieldCode())
                .containsExactly("title");
        assertThat(query.path("columns")).extracting(node -> node.asText())
                .containsExactly("title");
        assertThat(query.path("filter").path("children").get(0)
                .path("fieldCode").asText())
                .isEqualTo("status");
        assertThat(query.path("filter").path("children").get(0)
                .path("value").asText())
                .isEqualTo("OPEN");
        assertThat(query.path("filter").path("children").get(1)
                .path("operator").asText()).isEqualTo("EMPTY");
        assertThat(query.path("filter").path("children").get(1)
                .path("value").asBoolean()).isTrue();
        assertThat(query.path("sort").get(0).path("fieldCode").asText())
                .isEqualTo("title");
        assertThat(rows.rows()).hasSize(1);
        assertThat(rows.rows().getFirst().values())
                .extracting(value -> value.fieldCode())
                .containsExactly("title");
    }

    @Test
    void rejectsStaleSchemaAndOutOfRangePagesBeforeNativeQuery() {
        var sourceService = sourceService(new MemoryRepository());
        publish(sourceService);
        var gateway = new StubGateway(schema("999"));
        var service = new DataSourceRuntimeService(
                sourceService, gateway, new ObjectMapper());

        assertThatThrownBy(() -> service.metadata(SESSION, "work_items"))
                .hasMessageContaining("republished");
        assertThatThrownBy(() -> service.rows(
                SESSION, "work_items", 0, 201))
                .hasMessageContaining("size must be between");
        assertThat(gateway.queryJson).isNull();
    }

    @Test
    void readsThePinnedHistoricalPublicationAfterTheSourceIsRepublished()
            throws Exception {
        var repository = new MemoryRepository();
        var sourceService = sourceService(repository);
        var first = publish(sourceService);
        var root = sourceService.detail(ACTOR, first.dataSourceId());
        var revisedDraft = new DataSourceDraft(
                List.of(new DataSourceDraft.OutputField("title")),
                List.of(new DataSourceDraft.FixedFilter(
                        "status", "EQ", "\"CLOSED\"")),
                new DataSourceDraft.DefaultSort(
                        "title", DataSourceDraft.Direction.DESC),
                null);
        var revised = sourceService.revise(
                ACTOR, root.id(), root.draftVersion(),
                "Revised work items", null, revisedDraft);
        var second = sourceService.publish(
                ACTOR, revised.id(), revised.draftVersion());
        var gateway = new StubGateway(schema("501"));
        var service = new DataSourceRuntimeService(
                sourceService, gateway, new ObjectMapper());

        var metadata = service.metadata(
                SESSION, first.dataSourceId(), first.id());
        service.rows(SESSION, first.dataSourceId(), first.id(), 1, 10);
        var query = new ObjectMapper().readTree(gateway.queryJson);

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(metadata.versionId()).isEqualTo(Long.toString(first.id()));
        assertThat(metadata.name()).isEqualTo("Work items");
        assertThat(query.path("filter").path("children").get(0)
                .path("value").asText()).isEqualTo("OPEN");
        assertThat(query.path("sort").get(0).path("direction").asText())
                .isEqualTo("ASC");
    }

    @Test
    void exposesHttpMetadataAndFailsClosedWithoutSafeReader() {
        var repository = new MemoryRepository();
        var sourceService = sourceService(repository);
        var nativeVersion = publish(sourceService);
        repository.replaceVersion(httpVersion(nativeVersion));
        var gateway = new StubGateway(schema("501"));
        var service = new DataSourceRuntimeService(
                sourceService, gateway, new ObjectMapper());

        assertThat(service.metadata(SESSION, "work_items").outputFields())
                .extracting(value -> value.fieldCode())
                .containsExactly("title");
        assertThatThrownBy(() -> service.rows(
                SESSION, nativeVersion.dataSourceId(), nativeVersion.id(),
                1, 20))
                .isInstanceOf(DataSourceException.class)
                .satisfies(error -> assertThat(
                        ((DataSourceException) error).code())
                        .isEqualTo("DATA_SOURCE_PUBLISHED_HTTP_ROWS_UNAVAILABLE"));
        assertThat(gateway.schemaCount).isEqualTo(2);
        assertThat(gateway.queryJson).isNull();
    }

    @Test
    void readsActiveAndExactPinnedHttpPublicationsWithSafeOrderedDtos()
            throws Exception {
        var repository = new MemoryRepository();
        var sourceService = sourceService(repository);
        var firstNative = publish(sourceService);
        var root = sourceService.detail(ACTOR, firstNative.dataSourceId());
        var revised = sourceService.revise(
                ACTOR, root.id(), root.draftVersion(),
                "Revised work items", null,
                new DataSourceDraft(
                        List.of(new DataSourceDraft.OutputField("title")),
                        List.of(), null, null));
        var secondNative = sourceService.publish(
                ACTOR, revised.id(), revised.draftVersion());
        var first = httpVersion(firstNative);
        var second = httpVersion(secondNative);
        repository.replaceVersion(first);
        repository.replaceVersion(second);
        var gateway = new StubGateway(schema("501"));
        var reader = new RecordingHttpRowsReader();
        var service = new DataSourceRuntimeService(
                sourceService, gateway, new ObjectMapper(), reader);

        var active = service.publishedHttpRows(SESSION, "work_items");
        var pinned = service.publishedHttpRows(
                SESSION, first.dataSourceId(), first.id());
        var json = new ObjectMapper().valueToTree(active);

        assertThat(reader.publications)
                .extracting(value -> value.version().id())
                .containsExactly(second.id(), first.id());
        assertThat(gateway.schemaCount).isEqualTo(2);
        assertThat(active.dataSourceId())
                .isEqualTo(Long.toString(second.dataSourceId()));
        assertThat(active.dataSourceVersionId())
                .isEqualTo(Long.toString(second.id()));
        assertThat(pinned.dataSourceVersionId())
                .isEqualTo(Long.toString(first.id()));
        assertThat(active.fields())
                .extracting(value -> value.fieldCode())
                .containsExactly("title", "count");
        assertThat(active.rows()).singleElement().satisfies(row -> {
            assertThat(row.rowIndex()).isEqualTo(1);
            assertThat(row.values().keySet())
                    .containsExactly("title", "count");
            assertThat(row.values())
                    .containsEntry("title", "First")
                    .containsEntry("count", new BigInteger("7"));
        });
        assertThat(json.has("endpoint")).isFalse();
        assertThat(json.has("sourceField")).isFalse();
        assertThat(json.has("secret")).isFalse();
        assertThat(json.has("raw")).isFalse();
        assertThat(json.has("recordId")).isFalse();
        assertThat(json.has("status")).isFalse();
        assertThat(json.has("title")).isFalse();
        assertThat(json.has("total")).isFalse();
        assertThat(json.has("queryHash")).isFalse();
        assertThat(active.toString()).doesNotContain("First");
        assertThat(active.rows().getFirst().toString())
                .doesNotContain("First");
    }

    @Test
    void explicitHttpRowsRejectNativeAndMissingVersionsBeforeReader() {
        var repository = new MemoryRepository();
        var sourceService = sourceService(repository);
        var nativeVersion = publish(sourceService);
        var gateway = new StubGateway(schema("501"));
        var reader = new RecordingHttpRowsReader();
        var service = new DataSourceRuntimeService(
                sourceService, gateway, new ObjectMapper(), reader);

        assertThatThrownBy(() -> service.publishedHttpRows(
                SESSION, "work_items"))
                .isInstanceOfSatisfying(DataSourceException.class,
                        error -> assertThat(error.code()).isEqualTo(
                                "DATA_SOURCE_HTTP_ROWS_SOURCE_INVALID"));
        assertThatThrownBy(() -> service.publishedHttpRows(
                SESSION, nativeVersion.dataSourceId(), 999_999))
                .isInstanceOfSatisfying(DataSourceException.class,
                        error -> assertThat(error.code()).isEqualTo(
                                "DATA_SOURCE_VERSION_NOT_FOUND"));
        assertThat(reader.publications).isEmpty();
        assertThat(gateway.schemaCount).isZero();
    }

    @Test
    void compatibilityConstructorFailsClosedWhenHttpReaderIsAbsent() {
        var repository = new MemoryRepository();
        var sourceService = sourceService(repository);
        var nativeVersion = publish(sourceService);
        repository.replaceVersion(httpVersion(nativeVersion));
        var gateway = new StubGateway(schema("501"));
        var service = new DataSourceRuntimeService(
                sourceService, gateway, new ObjectMapper());

        assertThatThrownBy(() -> service.publishedHttpRows(
                SESSION, "work_items"))
                .isInstanceOfSatisfying(DataSourceException.class,
                        error -> assertThat(error.code()).isEqualTo(
                                "DATA_SOURCE_PUBLISHED_HTTP_ROWS_UNAVAILABLE"));
        assertThat(gateway.schemaCount).isEqualTo(1);
        assertThat(gateway.queryJson).isNull();
    }

    @Test
    void readsActiveAndPinnedJdbcSnapshotsWithoutEnteringNativeOrHttp()
            throws Exception {
        var repository = new MemoryRepository();
        var sourceService = sourceService(repository);
        var firstNative = publish(sourceService);
        var root = sourceService.detail(ACTOR, firstNative.dataSourceId());
        var revised = sourceService.revise(
                ACTOR, root.id(), root.draftVersion(), "Revised", null,
                new DataSourceDraft(
                        List.of(new DataSourceDraft.OutputField("title")),
                        List.of(), null, null));
        var secondNative = sourceService.publish(
                ACTOR, revised.id(), revised.draftVersion());
        var first = jdbcVersion(firstNative);
        var second = jdbcVersion(secondNative);
        repository.replaceVersion(first);
        repository.replaceVersion(second);
        var gateway = new StubGateway(schema("501"));
        var httpReader = new RecordingHttpRowsReader();
        var jdbcReader = new RecordingJdbcRowsReader();
        var service = new DataSourceRuntimeService(
                sourceService, gateway, new ObjectMapper(),
                httpReader, jdbcReader);

        assertThat(service.metadata(SESSION, "work_items").outputFields())
                .extracting(value -> value.fieldCode())
                .containsExactly("title", "occurredAt");
        var active = service.publishedJdbcRows(SESSION, "work_items");
        var pinned = service.publishedJdbcRows(
                SESSION, first.dataSourceId(), first.id());
        var json = new ObjectMapper().writeValueAsString(active);

        assertThat(jdbcReader.publications)
                .extracting(value -> value.version().id())
                .containsExactly(second.id(), first.id());
        assertThat(httpReader.publications).isEmpty();
        assertThat(gateway.schemaCount).isEqualTo(3);
        assertThat(gateway.queryJson).isNull();
        assertThat(active.dataSourceVersionId())
                .isEqualTo(Long.toString(second.id()));
        assertThat(pinned.dataSourceVersionId())
                .isEqualTo(Long.toString(first.id()));
        assertThat(active.fields())
                .extracting(value -> value.fieldCode())
                .containsExactly("title", "occurredAt");
        assertThat(active.rows()).singleElement().satisfies(row -> {
            assertThat(row.rowIndex()).isEqualTo(1);
            assertThat(row.values().keySet())
                    .containsExactly("title", "occurredAt");
        });
        assertThat(json).doesNotContain(
                "mysql.internal", "databaseName", "tableName",
                "sourceColumn", "SecretRef", "recordId", "total",
                "queryHash");
        assertThat(active.toString()).doesNotContain("First");
    }

    @Test
    void explicitJdbcRowsRejectOtherKindsAndMissingReaderSafely() {
        var repository = new MemoryRepository();
        var sourceService = sourceService(repository);
        var nativeVersion = publish(sourceService);
        var gateway = new StubGateway(schema("501"));
        var service = new DataSourceRuntimeService(
                sourceService, gateway, new ObjectMapper());

        assertThatThrownBy(() -> service.publishedJdbcRows(
                SESSION, "work_items"))
                .isInstanceOfSatisfying(DataSourceException.class,
                        error -> assertThat(error.code()).isEqualTo(
                                "DATA_SOURCE_JDBC_ROWS_SOURCE_INVALID"));
        repository.replaceVersion(jdbcVersion(nativeVersion));
        assertThatThrownBy(() -> service.publishedJdbcRows(
                SESSION, "work_items"))
                .isInstanceOfSatisfying(DataSourceException.class,
                        error -> assertThat(error.code()).isEqualTo(
                                "DATA_SOURCE_PUBLISHED_JDBC_ROWS_UNAVAILABLE"));
        assertThat(gateway.schemaCount).isEqualTo(1);
        assertThat(gateway.queryJson).isNull();
    }

    private static DataSourceService sourceService(MemoryRepository repository) {
        var title = new DataSourceModuleCatalog.FieldCapability(
                "title", "Title", "TEXT", Set.of("EQ", "EMPTY"),
                true, false, true);
        var secret = new DataSourceModuleCatalog.FieldCapability(
                "secret", "Secret", "SECRET", Set.of("EQ", "EMPTY"),
                false, false, true);
        var status = new DataSourceModuleCatalog.FieldCapability(
                "status", "Status", "RADIO", Set.of("EQ", "EMPTY"),
                false, false, true);
        var module = new DataSourceModuleCatalog.PublishedModule(
                100, "work", "Work", "501",
                List.of(title, secret, status));
        var catalog = new DataSourceModuleCatalog() {
            @Override
            public List<PublishedModule> publishedModules(
                    long systemId, long tenantId) {
                return List.of(module);
            }

            @Override
            public Optional<PublishedModule> publishedModule(
                    long systemId, long tenantId, long moduleId) {
                return moduleId == module.moduleId()
                        ? Optional.of(module) : Optional.empty();
            }
        };
        return new DataSourceService(
                repository, catalog,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static DataSourceVersion publish(DataSourceService service) {
        var draft = new DataSourceDraft(
                List.of(new DataSourceDraft.OutputField("title"),
                        new DataSourceDraft.OutputField("secret")),
                List.of(new DataSourceDraft.FixedFilter(
                                "status", "EQ", "\"OPEN\""),
                        new DataSourceDraft.FixedFilter(
                                "title", "EMPTY", null)),
                new DataSourceDraft.DefaultSort(
                        "title", DataSourceDraft.Direction.ASC),
                null);
        var source = service.create(
                ACTOR, "work_items", 100, "Work items", null, draft);
        return service.publish(ACTOR, source.id(), source.draftVersion());
    }

    private static DataSourceVersion httpVersion(DataSourceVersion source) {
        var draft = new DataSourceDraft(
                List.of(new DataSourceDraft.OutputField("title")),
                List.of(), null, null,
                DataSourceDraft.SourceKind.HTTP_JSON,
                new DataSourceDraft.HttpJsonConnection(
                        "https://data.example.invalid/rows", null, 3),
                List.of(new DataSourceDraft.HttpJsonFieldProjection(
                        "title", "title",
                        DataSourceDraft.HttpJsonSourceType.STRING)));
        return new DataSourceVersion(
                source.id(), source.dataSourceId(), source.systemId(),
                source.tenantId(), source.versionNumber(), source.code(),
                source.moduleId(), source.moduleCode(),
                source.schemaVersionId(), source.name(), source.description(),
                draft, source.fingerprint(), source.publishedByMemberId(),
                source.publishedAt());
    }

    private static DataSourceVersion jdbcVersion(DataSourceVersion source) {
        var draft = new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.JDBC_TABLE, null, List.of(),
                new DataSourceDraft.JdbcTableConnection(
                        "mysql.internal", 3306, "operations", "work_items",
                        "secret://mysql-user", "secret://mysql-password",
                        3, 5),
                List.of(
                        new DataSourceDraft.JdbcTableFieldProjection(
                                "external_title", "title",
                                DataSourceDraft.JdbcTableSourceType.STRING),
                        new DataSourceDraft.JdbcTableFieldProjection(
                                "occurred_at", "occurredAt",
                                DataSourceDraft.JdbcTableSourceType.DATETIME)));
        return new DataSourceVersion(
                source.id(), source.dataSourceId(), source.systemId(),
                source.tenantId(), source.versionNumber(), source.code(),
                source.moduleId(), source.moduleCode(),
                source.schemaVersionId(), source.name(), source.description(),
                draft, source.fingerprint(), source.publishedByMemberId(),
                source.publishedAt());
    }

    private static RecordRuntimeViews.RecordSchema schema(String versionId) {
        return new RecordRuntimeViews.RecordSchema(
                versionId, "600", "100", "checksum", "READY", null,
                1, List.of(
                field("title", "Title", "TEXT", true, true),
                field("count", "Count", "NUMBER", true, true),
                field("occurredAt", "Occurred at", "DATETIME", true, true),
                field("secret", "Secret", "SECRET", false, false),
                field("status", "Status", "RADIO", true, false)),
                List.of(), new RecordRuntimeViews.QueryLimits(50, 200, 3));
    }

    private static RecordRuntimeViews.FieldCapability field(
            String code,
            String name,
            String type,
            boolean readable,
            boolean sortable
    ) {
        return new RecordRuntimeViews.FieldCapability(
                code, name, code, type, "OPTIONAL", readable, false,
                readable, readable, !readable, List.of("EQ", "EMPTY"),
                sortable, true, true, List.of(),
                new ObjectMapper().createObjectNode());
    }

    private static final class StubGateway
            implements DataSourceRecordQueryGateway {
        private final RecordRuntimeViews.RecordSchema schema;
        private RecordRuntimeViews.RecordPage page =
                new RecordRuntimeViews.RecordPage(List.of(), 1, 20, 0);
        private String queryJson;
        private int schemaCount;

        private StubGateway(RecordRuntimeViews.RecordSchema schema) {
            this.schema = schema;
        }

        @Override
        public RecordRuntimeViews.RecordSchema schema(
                RuntimeSession session, String moduleCode) {
            schemaCount++;
            return schema;
        }

        @Override
        public RecordRuntimeViews.RecordSchema schema(
                RuntimeSession session,
                String moduleCode,
                String schemaVersionId
        ) {
            return schema(session, moduleCode);
        }

        @Override
        public RecordRuntimeViews.RecordPage query(
                RuntimeSession session, String moduleCode, String queryJson) {
            this.queryJson = queryJson;
            return page;
        }
    }

    private static final class RecordingHttpRowsReader
            implements PublishedHttpDataSourceRowsReader {
        private final List<DataSourcePublication> publications =
                new ArrayList<>();

        @Override
        public Result read(
                DataSourceActor actor,
                DataSourcePublication publication
        ) {
            publications.add(publication);
            var values = new LinkedHashMap<String, Object>();
            values.put("title", "First");
            values.put("count", new BigInteger("7"));
            return new Result(
                    publication.root().id(), publication.root().code(),
                    publication.version().id(),
                    publication.version().versionNumber(),
                    List.of(
                            new Field("title", "STRING"),
                            new Field("count", "INTEGER")),
                    List.of(new Row(1, values)));
        }
    }

    private static final class RecordingJdbcRowsReader
            implements PublishedJdbcTableDataSourceRowsReader {
        private final List<DataSourcePublication> publications =
                new ArrayList<>();

        @Override
        public Result read(
                DataSourceActor actor,
                DataSourcePublication publication
        ) {
            publications.add(publication);
            var values = new LinkedHashMap<String, Object>();
            values.put("title", "First");
            values.put("occurredAt", "2026-08-05T01:02:03");
            return new Result(
                    publication.root().id(), publication.root().code(),
                    publication.version().id(),
                    publication.version().versionNumber(),
                    List.of(
                            new Field("title", "STRING"),
                            new Field("occurredAt", "DATETIME")),
                    List.of(new Row(1, values)));
        }
    }

    private static final class MemoryRepository
            implements DataSourceRepository {
        private long nextSource = 1_000;
        private long nextVersion = 2_000;
        private final Map<Long, ModuleDataSource> sources =
                new LinkedHashMap<>();
        private final Map<Long, DataSourceVersion> versions =
                new LinkedHashMap<>();

        private void replaceVersion(DataSourceVersion version) {
            versions.put(version.id(), version);
        }

        @Override
        public long nextDataSourceId() {
            return nextSource++;
        }

        @Override
        public long nextVersionId() {
            return nextVersion++;
        }

        @Override
        public Optional<ModuleDataSource> findById(
                long systemId, long tenantId, long dataSourceId) {
            return Optional.ofNullable(sources.get(dataSourceId))
                    .filter(value -> value.systemId() == systemId
                            && value.tenantId() == tenantId);
        }

        @Override
        public Optional<ModuleDataSource> findByCode(
                long systemId, long tenantId, String code) {
            return sources.values().stream()
                    .filter(value -> value.systemId() == systemId
                            && value.tenantId() == tenantId
                            && value.code().equals(code)).findFirst();
        }

        @Override
        public List<ModuleDataSource> findAll(
                long systemId, long tenantId) {
            return sources.values().stream()
                    .filter(value -> value.systemId() == systemId
                            && value.tenantId() == tenantId).toList();
        }

        @Override
        public ModuleDataSource insert(ModuleDataSource root) {
            sources.put(root.id(), root);
            return root;
        }

        @Override
        public ModuleDataSource saveDraft(
                ModuleDataSource expected, ModuleDataSource revised) {
            sources.put(revised.id(), revised);
            return revised;
        }

        @Override
        public DataSourceVersion publish(
                ModuleDataSource expected,
                ModuleDataSource activated,
                DataSourceVersion version) {
            sources.put(activated.id(), activated);
            versions.put(version.id(), version);
            return version;
        }

        @Override
        public Optional<DataSourceVersion> findActiveVersion(
                long systemId, long tenantId, long dataSourceId) {
            return findById(systemId, tenantId, dataSourceId)
                    .map(ModuleDataSource::activeVersionId)
                    .map(versions::get);
        }

        @Override
        public Optional<DataSourceVersion> findVersion(
                long systemId, long tenantId, long dataSourceId,
                int versionNumber) {
            return versions.values().stream()
                    .filter(value -> value.systemId() == systemId
                            && value.tenantId() == tenantId
                            && value.dataSourceId() == dataSourceId
                            && value.versionNumber() == versionNumber)
                    .findFirst();
        }

        @Override
        public Optional<DataSourceVersion> findVersionById(
                long systemId, long tenantId, long dataSourceId,
                long versionId) {
            return versions.values().stream()
                    .filter(value -> value.systemId() == systemId
                            && value.tenantId() == tenantId
                            && value.dataSourceId() == dataSourceId
                            && value.id() == versionId)
                    .findFirst();
        }

        @Override
        public List<DataSourceVersion> findVersions(
                long systemId, long tenantId, long dataSourceId) {
            var result = new ArrayList<DataSourceVersion>();
            versions.values().stream()
                    .filter(value -> value.systemId() == systemId
                            && value.tenantId() == tenantId
                            && value.dataSourceId() == dataSourceId)
                    .forEach(result::add);
            return result;
        }
    }
}
