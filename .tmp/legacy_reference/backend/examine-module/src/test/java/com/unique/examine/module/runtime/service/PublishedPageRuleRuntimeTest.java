package com.unique.examine.module.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PublishedPageRuleRuntimeTest {
    private final ObjectMapper json = new ObjectMapper();
    private final PublishedPageRuleRuntime runtime = new PublishedPageRuleRuntime();

    @Test
    void compilesPageGeometryAndEvaluatesStructuredEffectsFromOnePublishedVersion() throws Exception {
        var snapshot = json.readTree("""
                {
                  "fields": [
                    {"id":"11","field_code":"status","sort_order":1,"desired_status":"ENABLED","show_in_list":1,"show_in_detail":1,"property_json":{"width":120}},
                    {"id":"12","field_code":"amount","sort_order":2,"desired_status":"ENABLED","show_in_list":1,"show_in_detail":1,"property_json":{"width":220}},
                    {"id":"13","field_code":"reason","sort_order":3,"desired_status":"ENABLED","show_in_list":0,"show_in_detail":1,"property_json":{}}
                  ],
                  "pages": [
                    {"id":"21","module_id":"7","page_code":"orders","page_type":"LIST","is_default":1,"desired_status":"ENABLED","layout_json":{"density":"COMPACT","columns":24,"gap":8,"pageSize":20}},
                    {"id":"22","module_id":"7","page_code":"edit","page_type":"FORM","is_default":1,"desired_status":"ENABLED","layout_json":{"columns":24,"gap":16}}
                  ],
                  "components": [
                    {"id":"31","page_id":"21","field_id":"12","component_key":"amount","component_type":"FIELD","sort_order":1,"grid_row":1,"grid_column":0,"grid_span":12,"property_json":{"variant":"FIXED_LEFT"}},
                    {"id":"32","page_id":"21","field_id":"11","component_key":"status","component_type":"FIELD","sort_order":2,"grid_row":1,"grid_column":12,"grid_span":12,"property_json":{}},
                    {"id":"35","page_id":"21","field_id":"13","component_key":"hidden_reason","component_type":"FIELD","sort_order":3,"grid_row":2,"grid_column":0,"grid_span":24,"property_json":{"visible":false}},
                    {"id":"33","page_id":"22","component_key":"finance","component_type":"SECTION","sort_order":1,"grid_row":1,"grid_column":0,"grid_span":24,"property_json":{"title":"财务信息","columns":24,"collapsible":true,"collapsed":true}},
                    {"id":"34","page_id":"22","parent_component_id":"33","field_id":"12","component_key":"amount","component_type":"FIELD","sort_order":2,"grid_row":1,"grid_column":0,"grid_span":12,"property_json":{}}
                  ],
                  "actions": [{"id":"41","action_code":"remove","action_type":"DELETE"}],
                  "rules": [
                    {"id":"51","module_id":"7","rule_code":"large","rule_type":"FIELD_REQUIRED","priority":10,"desired_status":"ENABLED","condition_json":{"fieldId":"12","operator":"GT","value":1000,"children":[]},"effect_json":[{"effect":"REQUIRED","targetId":"13","value":true}]},
                    {"id":"52","module_id":"7","rule_code":"closed","rule_type":"DELETE_ALLOWED","priority":20,"desired_status":"ENABLED","condition_json":{"fieldId":"11","operator":"EQ","value":"CLOSED","children":[]},"effect_json":[{"effect":"DELETE_ALLOWED","value":false},{"effect":"APPROVAL_REQUIRED","value":true},{"effect":"ACTION_ENABLED","targetId":"41","value":false}]}
                  ]
                }
                """);

        var contract = runtime.compile("88", snapshot, "7", Map.of("11", "status", "12", "amount", "13", "reason"));

        assertThat(contract.schemaVersionId()).isEqualTo("88");
        assertThat(contract.pages()).filteredOn(page -> page.type().equals("LIST")).singleElement()
                .satisfies(page -> {
                    assertThat(page.density()).isEqualTo("COMPACT");
                    assertThat(page.pageSize()).isEqualTo(20);
                    assertThat(page.fields()).extracting(field -> field.fieldCode())
                            .containsExactly("amount", "status");
                    assertThat(page.fields().getFirst().width()).isEqualTo(220);
                    assertThat(page.fields().getFirst().fixed()).isEqualTo("LEFT");
                });
        assertThat(contract.pages()).filteredOn(page -> page.type().equals("FORM")).singleElement()
                .satisfies(page -> assertThat(page.sections()).singleElement()
                        .satisfies(section -> {
                            assertThat(section.title()).isEqualTo("财务信息");
                            assertThat(section.collapsible()).isTrue();
                            assertThat(section.collapsed()).isTrue();
                        }));

        var decision = runtime.evaluate(contract,
                Map.of("status", json.getNodeFactory().textNode("CLOSED"),
                        "amount", json.getNodeFactory().numberNode(1500)),
                Set.of(), Set.of());
        assertThat(decision.requiredFields()).containsExactly("reason");
        assertThat(decision.deleteAllowed()).isFalse();
        assertThat(decision.approvalRequired()).isTrue();
        assertThat(decision.disabledActions()).contains("remove", "DELETE");
    }

    @Test
    void removesRulesWhoseConditionWouldExposeAnUnreadableField() throws Exception {
        var snapshot = json.readTree("""
                {"fields":[],"pages":[],"components":[],"actions":[],"rules":[
                  {"id":"1","module_id":"7","rule_code":"secret","rule_type":"FIELD_VISIBILITY","priority":1,"desired_status":"ENABLED","condition_json":{"fieldId":"99","operator":"EQ","value":"x","children":[]},"effect_json":[{"effect":"VISIBLE","targetId":"11","value":false}]}
                ]}
                """);

        var contract = runtime.compile("89", snapshot, "7", Map.of("11", "status"));

        assertThat(contract.rules()).isEmpty();
    }
}
