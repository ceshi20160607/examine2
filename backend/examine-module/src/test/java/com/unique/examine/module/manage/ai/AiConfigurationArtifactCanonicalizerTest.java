package com.unique.examine.module.manage.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.ai.AiConfigurationArtifactFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.manage.api.ConfigTypes;
import com.unique.examine.module.manage.api.ConfigViews;
import com.unique.examine.module.manage.service.StructuredPropertyValidator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiConfigurationArtifactCanonicalizerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AiConfigurationArtifactCanonicalizer canonicalizer =
            new AiConfigurationArtifactCanonicalizer(
                    new StructuredPropertyValidator(json), json);

    @Test
    void upsertsOnlyOneScenarioAndPreservesTheCurrentDefaultAndLayout()
            throws Exception {
        var current = json.readTree("""
                {
                  "columns":2,
                  "gap":24,
                  "filterScenarios":[{
                    "code":"open_items","name":"Open items","filter":null,
                    "sort":[{"fieldCode":"title","direction":"ASC","nulls":"LAST"}]
                  }],
                  "defaultFilterScenarioCode":"open_items"
                }
                """);
        var proposed = new AiConfigurationArtifactFacade.FilterScenarioDraft(
                "list", new AiConfigurationArtifactFacade.FilterScenario(
                "recent_items", "Recent items", null,
                json.readTree("""
                        [{"fieldCode":"updated_at","direction":"DESC","nulls":"LAST"}]
                        """)), false);

        var preview = canonicalizer.filterScenario(
                proposed, page(current));

        assertThat(preview.resolvedLayout().path("columns").asInt()).isEqualTo(2);
        assertThat(preview.resolvedLayout().path("gap").asInt()).isEqualTo(24);
        assertThat(preview.resolvedState().filterScenarios())
                .extracting(AiConfigurationArtifactFacade.FilterScenario::code)
                .containsExactly("open_items", "recent_items");
        assertThat(preview.resolvedState().defaultFilterScenarioCode())
                .isEqualTo("open_items");
    }

    @Test
    void stagesOnlyInheritedDirectionsAndNeverTouchesEnforcedOrUnsupportedWrite()
            throws Exception {
        var field = field(
                ConfigTypes.FieldType.TEXT, false,
                ConfigTypes.FieldPermissionMode.INHERIT,
                ConfigTypes.FieldPermissionMode.STAGED);
        var preview = canonicalizer.fieldPermission(
                "customers",
                new AiConfigurationArtifactFacade.FieldPermissionStageDraft(
                        "status", true, true),
                new AiConfigurationArtifactContextReader.FieldPermissionSnapshot(
                        common(), field));

        assertThat(preview.readPermissionMode()).isEqualTo(
                AiConfigurationArtifactFacade.FieldPermissionMode.STAGED);
        assertThat(preview.writePermissionMode()).isEqualTo(
                AiConfigurationArtifactFacade.FieldPermissionMode.STAGED);
        assertThat(preview.readPermissionCode())
                .isEqualTo("module.customers.field.status.read");

        assertCode("AI_CONFIG_FIELD_PERMISSION_ENFORCED", () ->
                canonicalizer.fieldPermission(
                        "customers",
                        new AiConfigurationArtifactFacade.FieldPermissionStageDraft(
                                "status", true, false),
                        new AiConfigurationArtifactContextReader.FieldPermissionSnapshot(
                                common(), field(ConfigTypes.FieldType.TEXT, false,
                                ConfigTypes.FieldPermissionMode.ENFORCED,
                                ConfigTypes.FieldPermissionMode.INHERIT))));
        assertCode("AI_CONFIG_FIELD_WRITE_UNSUPPORTED", () ->
                canonicalizer.fieldPermission(
                        "customers",
                        new AiConfigurationArtifactFacade.FieldPermissionStageDraft(
                                "status", false, true),
                        new AiConfigurationArtifactContextReader.FieldPermissionSnapshot(
                                common(), field(ConfigTypes.FieldType.AI_FILL, true,
                                ConfigTypes.FieldPermissionMode.INHERIT,
                                ConfigTypes.FieldPermissionMode.INHERIT))));
    }

    private AiConfigurationArtifactContextReader.FilterScenarioSnapshot page(
            com.fasterxml.jackson.databind.JsonNode layout) {
        return new AiConfigurationArtifactContextReader.FilterScenarioSnapshot(
                common(), new ConfigViews.Page(
                "71", "41", "list", "List", ConfigTypes.PageType.LIST,
                true, ConfigTypes.DesiredStatus.ENABLED, layout, "2", "5"));
    }

    private ConfigViews.Field field(
            ConfigTypes.FieldType type,
            boolean readonly,
            ConfigTypes.FieldPermissionMode read,
            ConfigTypes.FieldPermissionMode write) {
        return new ConfigViews.Field(
                "81", "41", null, null, "status", "Status", type,
                20, false, false, readonly, false, false, true, true,
                ConfigTypes.IndexMode.NONE, ConfigTypes.DesiredStatus.ENABLED,
                json.createObjectNode(), read, write, "3", "5");
    }

    private static AiConfigurationArtifactContextReader.CommonSnapshot common() {
        return new AiConfigurationArtifactContextReader.CommonSnapshot(
                31, 41, "customers", 5, 3,
                Set.of("system.admin.access", "module.config.manage"));
    }

    private static void assertCode(String code, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).code())
                .isEqualTo(code);
    }
}
