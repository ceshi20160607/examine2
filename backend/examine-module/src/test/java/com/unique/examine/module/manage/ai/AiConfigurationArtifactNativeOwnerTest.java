package com.unique.examine.module.manage.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.ai.AiConfigurationArtifactFacade;
import com.unique.examine.module.manage.api.ConfigRequests;
import com.unique.examine.module.manage.api.ConfigTypes;
import com.unique.examine.module.manage.api.ConfigViews;
import com.unique.examine.module.manage.security.ConfigSession;
import com.unique.examine.module.manage.service.ConfigDraftService;
import com.unique.examine.module.manage.service.RequestContext;
import com.unique.examine.module.manage.service.StructuredPropertyValidator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AiConfigurationArtifactNativeOwnerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AiConfigurationArtifactCanonicalizer canonicalizer =
            new AiConfigurationArtifactCanonicalizer(
                    new StructuredPropertyValidator(json), json);

    @Test
    void confirmationUsesOnlyVersionedPageAndFieldDraftOwners() throws Exception {
        var drafts = new NativeDrafts();
        var writer = new AiConfigurationArtifactDraftWriter(drafts, canonicalizer);

        var filterResult = writer.write(filterCommand(drafts.page.layout()),
                session(), new RequestContext("request-1", "trace-1"));
        var permissionResult = writer.write(permissionCommand(), session(),
                new RequestContext("request-2", "trace-2"));

        assertThat(drafts.pageUpdates).isOne();
        assertThat(drafts.fieldUpdates).isOne();
        assertThat(drafts.pageRequest.draftRevision()).isEqualTo("5");
        assertThat(drafts.pageRequest.version()).isEqualTo("2");
        assertThat(drafts.pageRequest.layout().path("columns").asInt()).isEqualTo(2);
        assertThat(drafts.fieldRequest.draftRevision()).isEqualTo("5");
        assertThat(drafts.fieldRequest.version()).isEqualTo("3");
        assertThat(drafts.fieldRequest.readPermissionMode())
                .isEqualTo(ConfigTypes.FieldPermissionMode.STAGED);
        assertThat(drafts.fieldRequest.writePermissionMode())
                .isEqualTo(ConfigTypes.FieldPermissionMode.INHERIT);
        assertThat(filterResult.filterScenario().state().defaultFilterScenarioCode())
                .isEqualTo("open_items");
        assertThat(permissionResult.fieldPermissionStage().readPermissionCode())
                .isEqualTo("module.customers.field.status.read");
        assertThat(filterResult.draftRevision()).isEqualTo(6);
        assertThat(permissionResult.draftRevision()).isEqualTo(6);
    }

    private AiConfigurationArtifactCommandCodec.Command filterCommand(
            com.fasterxml.jackson.databind.JsonNode current) throws Exception {
        var scenario = new AiConfigurationArtifactFacade.FilterScenario(
                "open_items", "Open items", null,
                json.readTree("""
                        [{"direction":"DESC","fieldCode":"updated_at","nulls":"LAST"}]
                        """));
        var resolved = ((com.fasterxml.jackson.databind.node.ObjectNode)
                current.deepCopy());
        resolved.set("filterScenarios", json.readTree("""
                [{"code":"open_items","name":"Open items","filter":null,
                  "sort":[{"direction":"DESC","fieldCode":"updated_at","nulls":"LAST"}]}]
                """));
        resolved.put("defaultFilterScenarioCode", "open_items");
        return command(
                AiConfigurationArtifactFacade.Operation.CONFIG_FILTER_SCENARIO_DRAFT,
                new AiConfigurationArtifactFacade.FilterScenarioPreview(
                        "71", "list", 2, true, scenario,
                        new AiConfigurationArtifactFacade.FilterScenarioState(
                                List.of(scenario), "open_items"), resolved),
                null);
    }

    private AiConfigurationArtifactCommandCodec.Command permissionCommand() {
        return command(
                AiConfigurationArtifactFacade.Operation
                        .CONFIG_FIELD_PERMISSION_STAGE_DRAFT,
                null,
                new AiConfigurationArtifactFacade.FieldPermissionStagePreview(
                        "81", "status", "Status", 3, true, false,
                        AiConfigurationArtifactFacade.FieldPermissionMode.INHERIT,
                        AiConfigurationArtifactFacade.FieldPermissionMode.INHERIT,
                        AiConfigurationArtifactFacade.FieldPermissionMode.STAGED,
                        AiConfigurationArtifactFacade.FieldPermissionMode.INHERIT,
                        "module.customers.field.status.read",
                        "module.customers.field.status.write"));
    }

    private AiConfigurationArtifactCommandCodec.Command command(
            AiConfigurationArtifactFacade.Operation operation,
            AiConfigurationArtifactFacade.FilterScenarioPreview filter,
            AiConfigurationArtifactFacade.FieldPermissionStagePreview permission) {
        return new AiConfigurationArtifactCommandCodec.Command(
                "proposal-1", "session-1", "turn-1", 7, 11, 21, 17, 3,
                permissions(), 31, 41, "customers", 5, operation,
                null, null, filter, permission, "51", "61", 0,
                "prompt-v1", Instant.parse("2026-08-05T01:15:00Z"),
                "request-1", "trace-1");
    }

    private static ConfigSession session() {
        return new ConfigSession(7, 11, 17, 21L, permissions());
    }

    private static Set<String> permissions() {
        return Set.of("system.admin.access", "module.config.manage");
    }

    private final class NativeDrafts extends ConfigDraftService {
        private ConfigViews.Page page;
        private ConfigViews.Field field;
        private int pageUpdates;
        private int fieldUpdates;
        private ConfigRequests.UpdatePage pageRequest;
        private ConfigRequests.UpdateField fieldRequest;

        private NativeDrafts() throws Exception {
            super(null, null, null, null, null);
            page = new ConfigViews.Page(
                    "71", "41", "list", "List", ConfigTypes.PageType.LIST,
                    true, ConfigTypes.DesiredStatus.ENABLED,
                    json.readTree("{\"columns\":2,\"gap\":24}"), "2", "5");
            field = new ConfigViews.Field(
                    "81", "41", null, null, "status", "Status",
                    ConfigTypes.FieldType.TEXT, 20, false, false, false,
                    false, false, true, true, ConfigTypes.IndexMode.NONE,
                    ConfigTypes.DesiredStatus.ENABLED, json.createObjectNode(),
                    ConfigTypes.FieldPermissionMode.INHERIT,
                    ConfigTypes.FieldPermissionMode.INHERIT, "3", "5");
        }

        @Override
        public List<ConfigViews.Page> pages(long systemId, long moduleId) {
            return List.of(page);
        }

        @Override
        public ConfigViews.Page updatePage(
                ConfigSession session, long moduleId, long id,
                ConfigRequests.UpdatePage request, RequestContext context) {
            pageUpdates++;
            pageRequest = request;
            page = new ConfigViews.Page(
                    page.id(), page.moduleId(), request.code(), request.name(),
                    request.type(), request.isDefault(), request.status(),
                    request.layout(), "3", "6");
            return page;
        }

        @Override
        public List<ConfigViews.Field> fields(long systemId, long moduleId) {
            return List.of(field);
        }

        @Override
        public ConfigViews.Field updateField(
                ConfigSession session, long moduleId, long id,
                ConfigRequests.UpdateField request, RequestContext context) {
            fieldUpdates++;
            fieldRequest = request;
            field = new ConfigViews.Field(
                    field.id(), field.moduleId(), request.dictionaryId(),
                    request.targetModuleId(), request.code(), request.name(),
                    request.type(), request.sortOrder(), request.required(),
                    request.hidden(), request.readonly(), request.searchable(),
                    request.filterable(), request.showInList(),
                    request.showInDetail(), request.indexMode(), request.status(),
                    request.properties(), request.readPermissionMode(),
                    request.writePermissionMode(), "4", "6");
            return field;
        }
    }
}
