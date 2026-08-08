package com.unique.examine.core.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiConfigurationArtifactFacadeTest {

    @Test
    void selectionIsBoundedNormalizedAndTypeSafe() {
        var draft = new AiConfigurationArtifactFacade.SelectionFieldDraft(
                "priority", "  Priority   level ",
                AiConfigurationArtifactFacade.SelectionType.MULTI_SELECT,
                true, "priority_options", " Priority options ",
                List.of(
                        new AiConfigurationArtifactFacade.OptionDraft(
                                "high", " High ", "urgent", "#ff0000", true),
                        new AiConfigurationArtifactFacade.OptionDraft(
                                "low", "Low", null, null, false)),
                2);

        assertThat(draft.fieldName()).isEqualTo("Priority level");
        assertThat(draft.options().getFirst().color()).isEqualTo("#FF0000");
        assertThatThrownBy(() -> new AiConfigurationArtifactFacade.SelectionFieldDraft(
                "priority", "Priority",
                AiConfigurationArtifactFacade.SelectionType.RADIO, false,
                "priority_options", "Options", draft.options(), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiConfigurationArtifactFacade.SelectionFieldDraft(
                "priority", "Priority",
                AiConfigurationArtifactFacade.SelectionType.MULTI_SELECT, false,
                "priority_options", "Options",
                List.of(draft.options().getFirst(),
                        new AiConfigurationArtifactFacade.OptionDraft(
                                "HIGH", "Other", null, null, false)), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pageLayoutAppliesCanonicalDefaultsAndRejectsUnboundedReferences() {
        var list = new AiConfigurationArtifactFacade.PageLayoutDraft(
                "list", AiConfigurationArtifactFacade.PageType.LIST,
                new AiConfigurationArtifactFacade.PageLayout(
                        null, null, null, null, null,
                        null, null, null,
                        List.of(new AiConfigurationArtifactFacade.PageSection(
                                "main", "Main", List.of("name", "priority")))));

        assertThat(list.layout().columns()).isOne();
        assertThat(list.layout().gap()).isEqualTo(16);
        assertThat(list.layout().pageSize()).isEqualTo(20);
        assertThat(list.layout().searchEnabled()).isTrue();
        assertThat(list.layout().filterEnabled()).isTrue();
        assertThatThrownBy(() -> new AiConfigurationArtifactFacade.PageLayoutDraft(
                "form", AiConfigurationArtifactFacade.PageType.FORM,
                new AiConfigurationArtifactFacade.PageLayout(
                        2, 16, null, null, true,
                        20, null, null, list.layout().sections())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiConfigurationArtifactFacade.PageLayout(
                2, 16, null, null, true,
                null, null, null,
                List.of(
                        new AiConfigurationArtifactFacade.PageSection(
                                "first", "First", List.of("name")),
                        new AiConfigurationArtifactFacade.PageSection(
                                "second", "Second", List.of("name")))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void prepareRequiresExactlyTheDraftMatchingItsOperation() {
        var selection = new AiConfigurationArtifactFacade.SelectionFieldDraft(
                "priority", "Priority",
                AiConfigurationArtifactFacade.SelectionType.RADIO, false,
                "priority_options", "Options",
                List.of(
                        new AiConfigurationArtifactFacade.OptionDraft(
                                "high", "High", null, null, false),
                        new AiConfigurationArtifactFacade.OptionDraft(
                                "low", "Low", null, null, false)), null);
        var request = new AiConfigurationArtifactFacade.PrepareRequest(
                "proposal-1", "session-1", "turn-1",
                7, 11, 21, 17, 3, permissions(), "customers",
                AiConfigurationArtifactFacade.Operation
                        .CONFIG_SELECTION_FIELD_DRAFT,
                selection, null, "51", "61", 0, "prompt-v1",
                "request-1", "trace-1");

        assertThat(request.selectionField()).isEqualTo(selection);
        assertThatThrownBy(() -> new AiConfigurationArtifactFacade.PrepareRequest(
                "proposal-1", "session-1", "turn-1",
                7, 11, 21, 17, 3, permissions(), "customers",
                AiConfigurationArtifactFacade.Operation.CONFIG_PAGE_LAYOUT_DRAFT,
                selection, null, "51", "61", 0, "prompt-v1",
                "request-1", "trace-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void filterScenarioAndPermissionStageStayBoundedAndFailClosed()
            throws Exception {
        var json = new ObjectMapper();
        var scenario = new AiConfigurationArtifactFacade.FilterScenario(
                "open_items", " Open items ",
                json.readTree("""
                        {"kind":"PREDICATE","fieldCode":"status","operator":"EQ","value":"OPEN"}
                        """), json.readTree("[]"));
        var state = new AiConfigurationArtifactFacade.FilterScenarioState(
                List.of(scenario), "open_items");

        assertThat(scenario.name()).isEqualTo("Open items");
        assertThat(state.defaultFilterScenarioCode()).isEqualTo("open_items");
        assertThatThrownBy(() -> new AiConfigurationArtifactFacade.FilterScenarioState(
                List.of(scenario), "missing"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiConfigurationArtifactFacade.FieldPermissionStageDraft(
                "status", false, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiConfigurationArtifactFacade.FieldPermissionStagePreview(
                "71", "status", "Status", 2, true, false,
                AiConfigurationArtifactFacade.FieldPermissionMode.ENFORCED,
                AiConfigurationArtifactFacade.FieldPermissionMode.INHERIT,
                AiConfigurationArtifactFacade.FieldPermissionMode.STAGED,
                AiConfigurationArtifactFacade.FieldPermissionMode.INHERIT,
                "module.customers.field.status.read",
                "module.customers.field.status.write"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Set<String> permissions() {
        return Set.of("system.admin.access", "module.config.manage");
    }
}
