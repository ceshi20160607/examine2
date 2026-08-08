package com.unique.examine.web.datasource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceVersion;
import com.unique.examine.module.datasource.domain.ModuleDataSource;
import com.unique.examine.module.datasource.controller.DataSourceAdminController;
import com.unique.examine.module.datasource.controller.DataSourceRuntimeController;
import com.unique.examine.module.datasource.port.DataSourceModuleCatalog;
import com.unique.examine.module.datasource.port.DataSourceRepository;
import com.unique.examine.module.datasource.runtime.DataSourceRecordQueryGateway;
import com.unique.examine.module.datasource.runtime.DataSourceRuntimeService;
import com.unique.examine.module.datasource.service.DataSourceService;
import com.unique.examine.module.datasource.service.PublishedHttpDataSourceRowsReader;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DataSourceControllerTest {
    private static final String ADMIN_ROOT =
            "/api/v1/systems/10/admin/data-sources";
    private static final String RUNTIME_ROOT =
            "/api/v1/systems/10/data-sources";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void provesAuthorizationStringIdsCasPublishAndDraftIsolation()
            throws Exception {
        var repository = new MemoryRepository();
        var sources = sourceService(repository);
        var gateway = new StubGateway();
        var runtime = new DataSourceRuntimeService(sources, gateway, JSON);
        var mvc = mvc(sources, runtime);

        mvc.perform(get(ADMIN_ROOT)).andExpect(status().isUnauthorized());
        mvc.perform(get(ADMIN_ROOT).requestAttr(
                        RequestSession.REQUEST_ATTRIBUTE, runtimeSession(20)))
                .andExpect(status().isForbidden());

        var created = data(mvc.perform(post(ADMIN_ROOT)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                adminSession(20))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"work_items","moduleId":"100",
                                 "name":"Work items","description":null}
                                """))
                .andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
        assertThat(created.path("id").isTextual()).isTrue();
        assertThat(created.path("moduleId").asText()).isEqualTo("100");
        var id = created.path("id").asText();

        var saved = data(mvc.perform(put(ADMIN_ROOT + "/" + id + "/draft")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                adminSession(20))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":1,"name":"Published name",
                                 "description":null,
                                 "outputFields":[{"fieldCode":"title"}],
                                 "fixedFilters":[],
                                 "defaultSort":{"fieldCode":"title","direction":"ASC"},
                                 "defaultTimeFieldCode":null}
                                """))
                .andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
        assertThat(saved.path("draftVersion").asLong()).isEqualTo(2);

        var check = data(mvc.perform(post(
                        ADMIN_ROOT + "/" + id + "/draft:check")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                adminSession(20)))
                .andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
        assertThat(check.path("valid").asBoolean()).isTrue();
        assertThat(check.path("blockerCount").asLong()).isZero();

        var published = data(mvc.perform(post(
                        ADMIN_ROOT + "/" + id + "/draft:publish")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                adminSession(20))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2}"))
                .andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
        assertThat(published.path("version").path("id").isTextual())
                .isTrue();
        assertThat(published.path("version").path("schemaVersionId")
                .asText()).isEqualTo("501");

        mvc.perform(put(ADMIN_ROOT + "/" + id + "/draft")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                adminSession(20))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":2,"name":"Draft-only name",
                                 "description":"not published",
                                 "outputFields":[{"fieldCode":"title"}],
                                 "fixedFilters":[],"defaultSort":null,
                                 "defaultTimeFieldCode":null}
                                """))
                .andExpect(status().isOk());

        var metadata = data(mvc.perform(get(
                        RUNTIME_ROOT + "/work_items")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                runtimeSession(20)))
                .andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
        assertThat(metadata.path("name").asText())
                .isEqualTo("Published name");
        assertThat(metadata.path("outputFields").get(0)
                .path("fieldCode").asText()).isEqualTo("title");

        mvc.perform(get(RUNTIME_ROOT + "/work_items/rows?page=0&size=201")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                runtimeSession(20)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get(ADMIN_ROOT + "/" + id)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                adminSession(21)))
                .andExpect(status().isNotFound());
    }

    @Test
    void exposesOnlySafeActiveAndPinnedPublishedHttpRows()
            throws Exception {
        var repository = new MemoryRepository();
        var sources = sourceService(repository);
        var actor = new DataSourceActor(10, 20, 30);
        var draft = new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.HTTP_JSON,
                new DataSourceDraft.HttpJsonConnection(
                        "https://datasource.example.test/work", null, 5),
                List.of(new DataSourceDraft.HttpJsonFieldProjection(
                        "external_title", "title",
                        DataSourceDraft.HttpJsonSourceType.STRING)));
        var root = sources.create(
                actor, "external_work", 100,
                "External work", null, draft);
        var version = sources.publish(
                actor, root.id(), root.draftVersion());
        PublishedHttpDataSourceRowsReader reader = (ignored, publication) -> {
            var values = new LinkedHashMap<String, Object>();
            values.put("title", "First");
            return new PublishedHttpDataSourceRowsReader.Result(
                    publication.root().id(), publication.root().code(),
                    publication.version().id(),
                    publication.version().versionNumber(),
                    List.of(new PublishedHttpDataSourceRowsReader.Field(
                            "title", "STRING")),
                    List.of(new PublishedHttpDataSourceRowsReader.Row(
                            1, values)));
        };
        var runtime = new DataSourceRuntimeService(
                sources, new StubGateway(), JSON, reader);
        var mvc = mvc(sources, runtime);

        var active = data(mvc.perform(get(
                        RUNTIME_ROOT + "/external_work/http-rows")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                runtimeSession(20)))
                .andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
        var pinned = data(mvc.perform(get(
                        RUNTIME_ROOT + "/" + root.id()
                                + "/versions/" + version.id()
                                + "/http-rows")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                runtimeSession(20)))
                .andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());

        assertSafeHttpRows(active, root, version);
        assertSafeHttpRows(pinned, root, version);
    }

    private static void assertSafeHttpRows(
            com.fasterxml.jackson.databind.JsonNode value,
            ModuleDataSource root,
            DataSourceVersion version
    ) {
        assertThat(fieldNames(value)).containsExactly(
                "dataSourceId", "dataSourceCode",
                "dataSourceVersionId", "dataSourceVersionNumber",
                "fields", "rows");
        assertThat(value.path("dataSourceId").asText())
                .isEqualTo(Long.toString(root.id()));
        assertThat(value.path("dataSourceCode").asText())
                .isEqualTo(root.code());
        assertThat(value.path("dataSourceVersionId").asText())
                .isEqualTo(Long.toString(version.id()));
        assertThat(value.path("dataSourceVersionNumber").asInt())
                .isEqualTo(version.versionNumber());
        assertThat(fieldNames(value.path("fields").get(0)))
                .containsExactly("fieldCode", "sourceType");
        assertThat(value.path("fields").get(0).path("fieldCode").asText())
                .isEqualTo("title");
        assertThat(fieldNames(value.path("rows").get(0)))
                .containsExactly("rowIndex", "values");
        assertThat(value.path("rows").get(0).path("rowIndex").asInt())
                .isEqualTo(1);
        assertThat(value.path("rows").get(0).path("values")
                .path("title").asText()).isEqualTo("First");
    }

    private static List<String> fieldNames(
            com.fasterxml.jackson.databind.JsonNode value
    ) {
        var result = new ArrayList<String>();
        value.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private static com.fasterxml.jackson.databind.JsonNode data(String body)
            throws Exception {
        return JSON.readTree(body).path("data");
    }

    private static MockMvc mvc(
            DataSourceService sources,
            DataSourceRuntimeService runtime
    ) {
        return MockMvcBuilders.standaloneSetup(
                        new DataSourceAdminController(sources, JSON),
                        new DataSourceRuntimeController(runtime))
                .setControllerAdvice(new TestExceptionAdvice())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(JSON))
                .build();
    }

    private static DataSourceService sourceService(
            MemoryRepository repository
    ) {
        var field = new DataSourceModuleCatalog.FieldCapability(
                "title", "Title", "TEXT", Set.of("EQ", "EMPTY"),
                true, false, true);
        var module = new DataSourceModuleCatalog.PublishedModule(
                100, "work", "Work", "501", List.of(field));
        var catalog = new DataSourceModuleCatalog() {
            @Override
            public List<PublishedModule> publishedModules(
                    long systemId, long tenantId) {
                return List.of(module);
            }

            @Override
            public Optional<PublishedModule> publishedModule(
                    long systemId, long tenantId, long moduleId) {
                return moduleId == 100
                        ? Optional.of(module) : Optional.empty();
            }
        };
        return new DataSourceService(
                repository, catalog, Clock.fixed(
                Instant.parse("2026-08-01T00:00:00Z"), ZoneOffset.UTC),
                (actor, draft) -> { });
    }

    private static TestSession adminSession(long tenantId) {
        return new TestSession(10, tenantId, 30,
                Set.of("system.admin.access", "module.config.manage"));
    }

    private static TestSession runtimeSession(long tenantId) {
        return new TestSession(10, tenantId, 30,
                Set.of("system.runtime.access"));
    }

    private record TestSession(
            long requestedSystemId,
            long requestedTenantId,
            long requestedMemberId,
            Set<String> requestedPermissions
    ) implements RequestSession {
        @Override public long sessionId() { return 1; }
        @Override public long accountId() { return 2; }
        @Override public ContextType contextType() { return ContextType.SYSTEM; }
        @Override public Long systemId() { return requestedSystemId; }
        @Override public Long tenantId() { return requestedTenantId; }
        @Override public Long memberId() { return requestedMemberId; }
        @Override public long permissionVersion() { return 1; }
        @Override public Set<String> permissions() { return requestedPermissions; }
    }

    private static final class StubGateway
            implements DataSourceRecordQueryGateway {
        @Override
        public RecordRuntimeViews.RecordSchema schema(
                RuntimeSession session, String moduleCode) {
            return new RecordRuntimeViews.RecordSchema(
                    "501", "600", "100", "checksum", "READY", null,
                    1, List.of(new RecordRuntimeViews.FieldCapability(
                    "title", "Title", "title", "TEXT", "OPTIONAL",
                    true, false, true, true, false,
                    List.of("EQ", "EMPTY"), true, true, true,
                    List.of(), JSON.createObjectNode())),
                    List.of(), new RecordRuntimeViews.QueryLimits(50, 200, 3));
        }

        @Override
        public RecordRuntimeViews.RecordPage query(
                RuntimeSession session, String moduleCode, String queryJson) {
            return new RecordRuntimeViews.RecordPage(List.of(), 1, 20, 0);
        }
    }

    @RestControllerAdvice
    private static final class TestExceptionAdvice {
        @ExceptionHandler(BusinessException.class)
        ResponseEntity<Map<String, Object>> business(BusinessException error) {
            return ResponseEntity.status(error.status()).body(Map.of(
                    "code", error.code(), "message", error.getMessage()));
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

        @Override public long nextDataSourceId() { return nextSource++; }
        @Override public long nextVersionId() { return nextVersion++; }

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
            return sources.values().stream().filter(value ->
                    value.systemId() == systemId
                            && value.tenantId() == tenantId
                            && value.code().equals(code)).findFirst();
        }

        @Override
        public List<ModuleDataSource> findAll(
                long systemId, long tenantId) {
            return sources.values().stream().filter(value ->
                    value.systemId() == systemId
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
            return versions.values().stream().filter(value ->
                    value.systemId() == systemId
                            && value.tenantId() == tenantId
                            && value.dataSourceId() == dataSourceId
                            && value.versionNumber() == versionNumber)
                    .findFirst();
        }

        @Override
        public Optional<DataSourceVersion> findVersionById(
                long systemId, long tenantId, long dataSourceId,
                long versionId) {
            return versions.values().stream().filter(value ->
                    value.systemId() == systemId
                            && value.tenantId() == tenantId
                            && value.dataSourceId() == dataSourceId
                            && value.id() == versionId)
                    .findFirst();
        }

        @Override
        public List<DataSourceVersion> findVersions(
                long systemId, long tenantId, long dataSourceId) {
            return versions.values().stream().filter(value ->
                    value.systemId() == systemId
                            && value.tenantId() == tenantId
                            && value.dataSourceId() == dataSourceId).toList();
        }
    }
}
