package com.unique.examine.module.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.manage.api.ConfigTypes.PageType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigFilterScenarioValidatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StructuredPropertyValidator layouts = new StructuredPropertyValidator(objectMapper);
    private final ConfigFilterScenarioValidator scenarios = new ConfigFilterScenarioValidator(objectMapper);

    @Test
    void canonicalizesBoundedListScenariosAndDefaultReference() throws Exception {
        var canonical = objectMapper.readTree(layouts.layout(PageType.LIST, objectMapper.readTree("""
                {
                  "columns": 2,
                  "filterScenarios": [{
                    "code": "open_items",
                    "name": "  Open items  ",
                    "filter": {"kind":"PREDICATE","fieldCode":"status","operator":"EQ","value":"101"},
                    "sort": [{"fieldCode":"created_at","direction":"DESC","nulls":"LAST"}]
                  }],
                  "defaultFilterScenarioCode": "open_items"
                }
                """)));

        assertThat(canonical.path("filterScenarios")).hasSize(1);
        assertThat(canonical.at("/filterScenarios/0/name").asText()).isEqualTo("Open items");
        assertThat(canonical.at("/filterScenarios/0/filter/kind").asText()).isEqualTo("PREDICATE");
        assertThat(canonical.at("/filterScenarios/0/sort/0/direction").asText()).isEqualTo("DESC");
        assertThat(canonical.path("defaultFilterScenarioCode").asText()).isEqualTo("open_items");
    }

    @Test
    void detectsFilterAndSortFieldReferencesForRenameAndDeleteProtection() {
        var configured = layout(scenario("open_items", "Open items",
                predicate("status", "EQ", text("open")), sort("created_at")), null);

        assertThat(scenarios.referencesField(configured, "status")).isTrue();
        assertThat(scenarios.referencesField(configured, "created_at")).isTrue();
        assertThat(scenarios.referencesField(configured, "title")).isFalse();
    }

    @Test
    void rejectsScenarioKeysOutsideListAndMalformedScenarioMetadata() throws Exception {
        var valid = scenario("open_items", "Open items", predicate("title", "EQ", objectMapper.valueToTree("x")),
                objectMapper.createArrayNode());
        var layout = layout(valid, "open_items");
        for (var type : List.of(PageType.FORM, PageType.DETAIL)) {
            assertInvalid(() -> layouts.layout(type, layout));
        }

        var unknown = valid.deepCopy();
        unknown.put("default", true);
        assertInvalid(() -> layouts.layout(PageType.LIST, layout(unknown, null)));
        assertInvalid(() -> layouts.layout(PageType.LIST,
                layout(List.of(valid, scenario("open_items", "Other", null, sort("title"))), null)));
        assertInvalid(() -> layouts.layout(PageType.LIST,
                layout(List.of(valid, scenario("other", "Open items", null, sort("title"))), null)));
        assertInvalid(() -> layouts.layout(PageType.LIST, layout(valid, "missing")));
        assertInvalid(() -> layouts.layout(PageType.LIST,
                layout(scenario("Not_Snake", "Invalid", null, sort("title")), null)));
        assertInvalid(() -> layouts.layout(PageType.LIST,
                layout(scenario("empty", "Empty", null, objectMapper.createArrayNode()), null)));
    }

    @Test
    void reusesRuntimeFilterDepthPredicateAndSortBounds() {
        var predicates = objectMapper.createArrayNode();
        for (var index = 0; index < 21; index++) {
            predicates.add(predicate("title", "EQ", objectMapper.valueToTree("v" + index)));
        }
        var tooManyPredicates = objectMapper.createObjectNode();
        tooManyPredicates.put("kind", "AND");
        tooManyPredicates.set("children", predicates);
        assertInvalid(() -> layouts.layout(PageType.LIST,
                layout(scenario("too_many", "Too many", tooManyPredicates,
                        objectMapper.createArrayNode()), null)));

        var sorts = objectMapper.createArrayNode();
        for (var code : List.of("one", "two", "three", "four")) {
            sorts.add(sortItem(code));
        }
        assertInvalid(() -> layouts.layout(PageType.LIST,
                layout(scenario("too_many_sorts", "Too many sorts", null, sorts), null)));

        var unknownFilterKey = predicate("title", "EQ", objectMapper.valueToTree("x"));
        unknownFilterKey.put("sql", "unsafe");
        assertInvalid(() -> layouts.layout(PageType.LIST,
                layout(scenario("unknown", "Unknown", unknownFilterKey,
                        objectMapper.createArrayNode()), null)));
    }

    @Test
    void semanticCheckAcceptsEnabledIndexedScalarAndNativeRelation() throws Exception {
        var filter = predicate("owner", "HAS_ANY", objectMapper.valueToTree(List.of("101")));
        var configured = scenario("owned", "Owned", filter, sort("title"));
        var snapshot = snapshot(
                List.of(
                        field("11", "title", "TEXT", "ENABLED", true, "SORT"),
                        field("12", "owner", "RELATION", "ENABLED", false, "NONE")),
                layout(configured, "owned"));

        assertThat(scenarios.inspect(snapshot)).isEmpty();
    }

    @Test
    void semanticCheckBlocksUnavailableSensitiveUnindexedAndInvalidQueries() throws Exception {
        assertBlocked(List.of(field("11", "title", "TEXT", "ENABLED", true, "FILTER")),
                scenario("missing", "Missing", predicate("unknown", "EQ", text("x")), emptySort()),
                "missing field");
        assertBlocked(List.of(field("11", "title", "TEXT", "DISABLED", true, "FILTER")),
                scenario("disabled", "Disabled", predicate("title", "EQ", text("x")), emptySort()),
                "disabled field");
        assertBlocked(List.of(field("11", "secret", "SECRET", "ENABLED", true, "FILTER")),
                scenario("secret", "Secret", predicate("secret", "EQ", text("x")), emptySort()),
                "sensitive field");
        assertBlocked(List.of(field("11", "title", "TEXT", "ENABLED", true, "NONE")),
                scenario("no_filter_index", "No filter index", predicate("title", "EQ", text("x")), emptySort()),
                "not filterable");
        assertBlocked(List.of(field("11", "title", "TEXT", "ENABLED", true, "FILTER")),
                scenario("no_sort_index", "No sort index", null, sort("title")),
                "not sortable");
        assertBlocked(List.of(field("11", "title", "TEXT", "ENABLED", true, "FILTER")),
                scenario("operator", "Operator", predicate("title", "GT", text("x")), emptySort()),
                "Operator is unavailable");
        assertBlocked(List.of(field("11", "title", "TEXT", "ENABLED", true, "FILTER")),
                scenario("value", "Value", predicate("title", "EQ", objectMapper.valueToTree(7)), emptySort()),
                "non-empty string");
        assertBlocked(List.of(field("11", "attachment", "ATTACHMENT", "ENABLED", true, "FILTER")),
                scenario("unsupported", "Unsupported", predicate("attachment", "EQ", text("x")), emptySort()),
                "unsupported field type");
    }

    private void assertBlocked(List<ObjectNode> fields, ObjectNode scenario, String message) {
        var findings = scenarios.inspect(snapshot(fields, layout(scenario, null)));
        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo("FILTER_SCENARIO_INVALID");
            assertThat(finding.message()).containsIgnoringCase(message);
        });
    }

    private ObjectNode snapshot(List<ObjectNode> fields, ObjectNode layout) {
        var root = objectMapper.createObjectNode();
        root.set("fields", objectMapper.valueToTree(fields));
        var page = objectMapper.createObjectNode();
        page.put("id", "101");
        page.put("module_id", "1");
        page.put("page_type", "LIST");
        page.put("desired_status", "ENABLED");
        page.set("layout_json", layout);
        root.putArray("pages").add(page);
        return root;
    }

    private ObjectNode field(
            String id,
            String code,
            String type,
            String status,
            boolean filterable,
            String indexMode
    ) {
        var field = objectMapper.createObjectNode();
        field.put("id", id);
        field.put("module_id", "1");
        field.put("field_code", code);
        field.put("field_type", type);
        field.put("desired_status", status);
        field.put("is_filterable", filterable);
        field.put("index_mode", indexMode);
        field.putObject("property_json");
        return field;
    }

    private ObjectNode layout(ObjectNode scenario, String defaultCode) {
        return layout(List.of(scenario), defaultCode);
    }

    private ObjectNode layout(List<ObjectNode> scenarioItems, String defaultCode) {
        var layout = objectMapper.createObjectNode();
        layout.set("filterScenarios", objectMapper.valueToTree(scenarioItems));
        if (defaultCode == null) {
            layout.putNull("defaultFilterScenarioCode");
        } else {
            layout.put("defaultFilterScenarioCode", defaultCode);
        }
        return layout;
    }

    private ObjectNode scenario(String code, String name, JsonNode filter, ArrayNode sort) {
        var scenario = objectMapper.createObjectNode();
        scenario.put("code", code);
        scenario.put("name", name);
        if (filter == null) {
            scenario.putNull("filter");
        } else {
            scenario.set("filter", filter);
        }
        scenario.set("sort", sort);
        return scenario;
    }

    private ObjectNode predicate(String fieldCode, String operator, JsonNode value) {
        var predicate = objectMapper.createObjectNode();
        predicate.put("kind", "PREDICATE");
        predicate.put("fieldCode", fieldCode);
        predicate.put("operator", operator);
        predicate.set("value", value);
        return predicate;
    }

    private ArrayNode sort(String fieldCode) {
        return objectMapper.createArrayNode().add(sortItem(fieldCode));
    }

    private ObjectNode sortItem(String fieldCode) {
        var item = objectMapper.createObjectNode();
        item.put("fieldCode", fieldCode);
        item.put("direction", "ASC");
        item.put("nulls", "LAST");
        return item;
    }

    private ArrayNode emptySort() {
        return objectMapper.createArrayNode();
    }

    private JsonNode text(String value) {
        return objectMapper.valueToTree(value);
    }

    private void assertInvalid(ThrowingCall call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code())
                .isEqualTo("VALIDATION_ERROR");
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }
}
