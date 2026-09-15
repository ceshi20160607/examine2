package com.unique.unexamine.moduleconfig.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class StructuredRuleEvaluatorTest {
    private final ObjectMapper json = new ObjectMapper();
    private final StructuredRuleEvaluator evaluator = new StructuredRuleEvaluator();
    private JsonNode node(String value) throws Exception { return json.readTree(value); }
    private boolean matches(String tree, String fields) throws Exception {
        return evaluator.evaluateConditions(node(tree), node(fields), new ArrayList<>());
    }
    @Test void nestedGroupsUseNumericValuesAndReturnEveryLeafResult() throws Exception {
        var tree = node("""
          {"mode":"ALL","conditions":[{"field":"reading","operator":"GT","value":10},
          {"mode":"ANY","conditions":[{"field":"running","operator":"EQ","value":false},
          {"field":"reading","operator":"EQ","value":13}]}]}
          """);
        var results = new ArrayList<ModuleRuleModels.ConditionResult>();
        assertThat(evaluator.inspectConditions(tree, Set.of("reading", "running"))).isEmpty();
        assertThat(evaluator.evaluateConditions(tree, node("{\"reading\":13.0,\"running\":true}"), results)).isTrue();
        assertThat(results).hasSize(3);
        assertThat(evaluator.evaluateConditions(tree, node("{\"reading\":8,\"running\":false}"), new ArrayList<>())).isFalse();
    }
    @Test void emptyValuesDoNotConfuseZeroFalseOrNonemptySelections() throws Exception {
        String tree="{\"field\":\"value\",\"operator\":\"EMPTY\"}";
        for (String fields : new String[]{"{}","{\"value\":null}","{\"value\":[]}","{\"value\":\"  \"}"}) assertThat(matches(tree,fields)).isTrue();
        for (String fields : new String[]{"{\"value\":0}","{\"value\":false}","{\"value\":[\"one\"]}"}) assertThat(matches(tree,fields)).isFalse();
        assertThat(matches("{\"field\":\"value\",\"operator\":\"CONTAINS\",\"value\":\"one\"}","{\"value\":[\"one\",\"two\"]}")).isTrue();
        assertThat(matches("{\"field\":\"value\",\"operator\":\"EQ\",\"value\":\"001\"}","{\"value\":\"1\"}")).isFalse();
    }
    @Test void dateAndOffsetDatetimeComparisonsUseTimeRatherThanTextOrdering() throws Exception {
        assertThat(matches("{\"field\":\"value\",\"operator\":\"GTE\",\"value\":\"2026-09-15\"}","{\"value\":\"2026-09-16\"}")).isTrue();
        assertThat(matches("{\"field\":\"value\",\"operator\":\"GT\",\"value\":\"2026-09-15T09:00:00Z\"}","{\"value\":\"2026-09-15T18:00:00+08:00\"}")).isTrue();
        assertThat(matches("{\"field\":\"value\",\"operator\":\"GT\",\"value\":\"not a date\"}","{\"value\":\"tomorrow\"}")).isFalse();
    }
    @Test void malformedHiddenOrUnboundedConditionsCannotBeAccepted() throws Exception {
        assertThat(evaluator.inspectConditions(node("{\"field\":\"hidden\",\"operator\":\"EQ\",\"value\":1}"),Set.of("reading"))).isNotEmpty();
        assertThat(evaluator.inspectConditions(node("{\"mode\":\"ANY\",\"conditions\":[]}"),Set.of())).isNotEmpty();
        assertThat(evaluator.inspectConditions(node("{\"field\":\"reading\",\"operator\":\"IN\",\"value\":1}"),Set.of("reading"))).isNotEmpty();
        String deep="{\"field\":\"reading\",\"operator\":\"EQ\",\"value\":1}";
        for(int i=0;i<10;i++)deep="{\"mode\":\"ALL\",\"conditions\":["+deep+"]}";
        assertThat(evaluator.inspectConditions(node(deep),Set.of("reading"))).isNotEmpty();
        assertThat(matches("{\"mode\":\"ALL\",\"conditions\":[]}","{}")).isTrue();
    }
}
