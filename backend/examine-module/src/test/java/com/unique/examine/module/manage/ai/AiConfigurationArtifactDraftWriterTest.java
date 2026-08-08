package com.unique.examine.module.manage.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiConfigurationArtifactDraftWriterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void pageLayoutRewritePreservesNativeSharedFilterScenarioKeys() throws Exception {
        var current = (ObjectNode) objectMapper.readTree("""
                {
                  "columns": 1,
                  "filterScenarios": [{
                    "code":"open_items","name":"Open items",
                    "filter":null,
                    "sort":[{"fieldCode":"title","direction":"ASC","nulls":"LAST"}]
                  }],
                  "defaultFilterScenarioCode":"open_items"
                }
                """);
        var proposed = (ObjectNode) objectMapper.readTree("""
                {"columns":2,"gap":24,"sections":[]}
                """);

        var merged = AiConfigurationArtifactDraftWriter.preserveFilterScenarios(proposed, current);

        assertThat(merged.path("columns").asInt()).isEqualTo(2);
        assertThat(merged.path("gap").asInt()).isEqualTo(24);
        assertThat(merged.path("filterScenarios")).isEqualTo(current.path("filterScenarios"));
        assertThat(merged.path("defaultFilterScenarioCode").asText()).isEqualTo("open_items");
    }

    @Test
    void pageLayoutRewriteDoesNotInventScenarioMetadata() throws Exception {
        var current = (ObjectNode) objectMapper.readTree("{\"columns\":1}");
        var proposed = (ObjectNode) objectMapper.readTree("{\"columns\":2}");

        var merged = AiConfigurationArtifactDraftWriter.preserveFilterScenarios(proposed, current);

        assertThat(merged.has("filterScenarios")).isFalse();
        assertThat(merged.has("defaultFilterScenarioCode")).isFalse();
    }
}
