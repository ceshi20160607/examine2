package com.unique.examine.module.manage.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.ai.AiConfigurationArtifactFacade;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiConfigurationArtifactCommandCodecTest {

    @Test
    void roundTripsBothStrictCanonicalCommands() {
        var codec = new AiConfigurationArtifactCommandCodec(
                new ObjectMapper().setSerializationInclusion(
                        JsonInclude.Include.NON_NULL));
        var selection = selectionCommand(codec);
        var page = pageCommand(codec);

        assertThat(codec.decode(codec.encode(selection))).isEqualTo(selection);
        assertThat(codec.decode(codec.encode(page))).isEqualTo(page);
        assertThat(codec.encode(selection))
                .contains("\"sessionId\":\"session-1\"")
                .contains("\"turnId\":\"turn-1\"")
                .contains("\"semanticKey\":null")
                .contains("\"color\":null")
                .contains("\"maxSelections\":null")
                .doesNotContain("sql", "publish");
        assertThat(codec.encode(page))
                .contains("\"pageSize\":null")
                .contains("\"searchEnabled\":null")
                .contains("\"filterEnabled\":null");
    }

    @Test
    void rejectsUnknownNestedAndNonCanonicalPayloads() throws Exception {
        var json = new ObjectMapper();
        var codec = new AiConfigurationArtifactCommandCodec(json);
        var encoded = codec.encode(selectionCommand(codec));

        var unknownRoot = (ObjectNode) json.readTree(encoded);
        unknownRoot.put("publish", true);
        var unknownRootText = json.writeValueAsString(unknownRoot);
        assertInvalid(() -> codec.decode(unknownRootText));

        var unknownOption = (ObjectNode) json.readTree(encoded);
        ((ObjectNode) unknownOption.path("selectionField").path("draft")
                .path("options").get(0)).put("status", "ENABLED");
        var unknownOptionText = json.writeValueAsString(unknownOption);
        assertInvalid(() -> codec.decode(unknownOptionText));

        var reordered = (ObjectNode) json.readTree(encoded);
        var permissions = reordered.putArray("effectivePermissions");
        permissions.add("system.admin.access");
        permissions.add("module.config.manage");
        var reorderedText = json.writeValueAsString(reordered);
        assertInvalid(() -> codec.decode(reorderedText));
    }

    @Test
    void roundTripsFilterScenarioAndPermissionStageWithoutExecutableAuthority()
            throws Exception {
        var json = new ObjectMapper();
        var codec = new AiConfigurationArtifactCommandCodec(json);
        var scenario = new AiConfigurationArtifactFacade.FilterScenario(
                "open_items", "Open items", null,
                json.readTree("""
                        [{"fieldCode":"updated_at","direction":"DESC","nulls":"LAST"}]
                        """));
        var layout = json.readTree("""
                {"columns":1,"filterScenarios":[{
                  "code":"open_items","name":"Open items","filter":null,
                  "sort":[{"direction":"DESC","fieldCode":"updated_at","nulls":"LAST"}]
                }],"defaultFilterScenarioCode":"open_items"}
                """);
        var filterRequest = new AiConfigurationArtifactFacade.PrepareRequest(
                "proposal-3", "session-1", "turn-3", 7, 11, 21, 17,
                3, permissions(), "customers",
                AiConfigurationArtifactFacade.Operation.CONFIG_FILTER_SCENARIO_DRAFT,
                null, null,
                new AiConfigurationArtifactFacade.FilterScenarioDraft(
                        "list", scenario, true), null,
                "51", "61", 0, "prompt-v1", "request-1", "trace-1");
        var filter = codec.command(filterRequest, 31, 41, 5, null, null,
                new AiConfigurationArtifactFacade.FilterScenarioPreview(
                        "71", "list", 2, true, scenario,
                        new AiConfigurationArtifactFacade.FilterScenarioState(
                                List.of(scenario), "open_items"), layout),
                null, Instant.parse("2026-08-04T01:15:00Z"));

        var permissionRequest = new AiConfigurationArtifactFacade.PrepareRequest(
                "proposal-4", "session-1", "turn-4", 7, 11, 21, 17,
                3, permissions(), "customers",
                AiConfigurationArtifactFacade.Operation
                        .CONFIG_FIELD_PERMISSION_STAGE_DRAFT,
                null, null, null,
                new AiConfigurationArtifactFacade.FieldPermissionStageDraft(
                        "status", true, false),
                "51", "61", 0, "prompt-v1", "request-1", "trace-1");
        var permission = codec.command(
                permissionRequest, 31, 41, 5, null, null, null,
                new AiConfigurationArtifactFacade.FieldPermissionStagePreview(
                        "81", "status", "Status", 3, true, false,
                        AiConfigurationArtifactFacade.FieldPermissionMode.INHERIT,
                        AiConfigurationArtifactFacade.FieldPermissionMode.INHERIT,
                        AiConfigurationArtifactFacade.FieldPermissionMode.STAGED,
                        AiConfigurationArtifactFacade.FieldPermissionMode.INHERIT,
                        "module.customers.field.status.read",
                        "module.customers.field.status.write"),
                Instant.parse("2026-08-04T01:15:00Z"));

        assertThat(codec.decode(codec.encode(filter))).isEqualTo(filter);
        assertThat(codec.decode(codec.encode(permission))).isEqualTo(permission);
        assertThat(codec.encode(filter)).doesNotContain("publish", "grant", "sql");
        assertThat(codec.encode(permission)).doesNotContain("ENFORCED", "publish", "grant");
    }

    static AiConfigurationArtifactCommandCodec.Command selectionCommand(
            AiConfigurationArtifactCommandCodec codec) {
        return codec.command(selectionRequest(), 31, 41, 5,
                new AiConfigurationArtifactFacade.SelectionFieldPreview(
                        20, selection()), null,
                Instant.parse("2026-08-04T01:15:00Z"));
    }

    static AiConfigurationArtifactCommandCodec.Command pageCommand(
            AiConfigurationArtifactCommandCodec codec) {
        var request = pageRequest();
        return codec.command(request, 31, 41, 5, null,
                new AiConfigurationArtifactFacade.PageLayoutPreview(
                        "71", "form", AiConfigurationArtifactFacade.PageType.FORM,
                        2, request.pageLayout().layout()),
                Instant.parse("2026-08-04T01:15:00Z"));
    }

    static AiConfigurationArtifactFacade.PrepareRequest selectionRequest() {
        return new AiConfigurationArtifactFacade.PrepareRequest(
                "proposal-1", "session-1", "turn-1",
                7, 11, 21, 17, 3, permissions(), "customers",
                AiConfigurationArtifactFacade.Operation
                        .CONFIG_SELECTION_FIELD_DRAFT,
                selection(), null, "51", "61", 0, "prompt-v1",
                "request-1", "trace-1");
    }

    static AiConfigurationArtifactFacade.PrepareRequest pageRequest() {
        return new AiConfigurationArtifactFacade.PrepareRequest(
                "proposal-2", "session-1", "turn-2",
                7, 11, 21, 17, 3, permissions(), "customers",
                AiConfigurationArtifactFacade.Operation.CONFIG_PAGE_LAYOUT_DRAFT,
                null, new AiConfigurationArtifactFacade.PageLayoutDraft(
                "form", AiConfigurationArtifactFacade.PageType.FORM,
                new AiConfigurationArtifactFacade.PageLayout(
                        2, 16, AiConfigurationArtifactFacade.LabelPosition.TOP,
                        AiConfigurationArtifactFacade.Density.DEFAULT, true,
                        null, null, null,
                        List.of(new AiConfigurationArtifactFacade.PageSection(
                                "main", "Main", List.of("name", "priority"))))),
                "51", "61", 0, "prompt-v1", "request-1", "trace-1");
    }

    static AiConfigurationArtifactFacade.SelectionFieldDraft selection() {
        return new AiConfigurationArtifactFacade.SelectionFieldDraft(
                "priority", "Priority",
                AiConfigurationArtifactFacade.SelectionType.RADIO, true,
                "priority_options", "Priority options",
                List.of(
                        new AiConfigurationArtifactFacade.OptionDraft(
                                "high", "High", "urgent", "#FF0000", true),
                        new AiConfigurationArtifactFacade.OptionDraft(
                                "low", "Low", null, null, false)), null);
    }

    static Set<String> permissions() {
        return Set.of("system.admin.access", "module.config.manage");
    }

    static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).code())
                .isEqualTo("AI_CONFIG_ARTIFACT_COMMAND_INVALID");
    }
}
