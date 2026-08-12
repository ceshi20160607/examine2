package com.unique.examine.module.runtime.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.api.RuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalSearchServiceTest {
    private static final RuntimeSession SESSION =
            new RuntimeSession(1, 7, 9, null, Set.of("system.runtime.access"));

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void pagesAcrossModulesInNavigationOrderUsingEachModulesStableQueryOrder() {
        var runtime = new FakeRuntime(mapper, navigation("alpha", "bravo"));
        runtime.addReady("bravo", 3, definition(field("public_text", "TEXT", true, false, "ENABLED", "FILTER")),
                schema("bravo", capabilities(capability("public_text", "TEXT", true, false))));
        runtime.addReady("alpha", 205, definition(field("public_text", "TEXT", true, false, "ENABLED", "FILTER")),
                schema("alpha", capabilities(capability("public_text", "TEXT", true, false))));
        var service = service(runtime);

        var page = service.search(SESSION, "pump", "5", "50");

        assertThat(page.total()).isEqualTo(208);
        assertThat(page.items()).hasSize(8);
        assertThat(page.items().stream().map(GlobalSearchViews.SearchItem::recordId))
                .containsExactly(
                        "alpha-200", "alpha-201", "alpha-202", "alpha-203", "alpha-204",
                        "bravo-0", "bravo-1", "bravo-2");
        assertThat(page.items().stream().map(GlobalSearchViews.SearchItem::moduleCode))
                .containsExactly(
                        "alpha", "alpha", "alpha", "alpha", "alpha",
                        "bravo", "bravo", "bravo");
        assertThat(runtime.callsFor("alpha"))
                .extracting(QueryCall::page, QueryCall::size)
                .containsExactly(tuple(1, 1), tuple(2, 200));
        assertThat(runtime.callsFor("bravo"))
                .extracting(QueryCall::page, QueryCall::size)
                .containsExactly(tuple(1, 1), tuple(1, 200));
        assertThat(runtime.calls)
                .allSatisfy(call -> {
                    assertThat(call.body().path("recordScope").asText()).isEqualTo("active");
                    assertThat(call.body().path("sort")).isEmpty();
                    assertThat(call.body().path("filter").isNull()).isTrue();
                    assertThat(call.columns()).containsExactly("public_text");
                });
    }

    @Test
    void omitsUnauthorizedAndRuntimeUnreadyModulesWithoutChangingVisibleOrdering() {
        var runtime = new FakeRuntime(mapper, navigation("denied", "unready", "visible"));
        runtime.deniedInspection.add("denied");
        runtime.definitions.put("unready",
                definition(field("public_text", "TEXT", true, false, "ENABLED", "FILTER")));
        runtime.schemas.put("unready",
                schema("unready", "NOT_READY",
                        capabilities(capability("public_text", "TEXT", true, false))));
        runtime.addReady("visible", 2,
                definition(field("public_text", "TEXT", true, false, "ENABLED", "FILTER")),
                schema("visible", capabilities(capability("public_text", "TEXT", true, false))));

        var page = service(runtime).search(SESSION, "pump", "1", "20");

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(GlobalSearchViews.SearchItem::moduleCode)
                .containsExactly("visible", "visible");
        assertThat(runtime.calls).extracting(QueryCall::moduleCode)
                .containsOnly("visible");
    }

    @Test
    void removesAModuleIfItsPermissionDisappearsBetweenCountAndPageFetch() {
        var runtime = new FakeRuntime(mapper, navigation("vanished", "visible"));
        runtime.addReady("vanished", 4,
                definition(field("public_text", "TEXT", true, false, "ENABLED", "FILTER")),
                schema("vanished", capabilities(capability("public_text", "TEXT", true, false))));
        runtime.addReady("visible", 2,
                definition(field("public_text", "TEXT", true, false, "ENABLED", "FILTER")),
                schema("visible", capabilities(capability("public_text", "TEXT", true, false))));
        runtime.deniedFetch.add("vanished");

        var page = service(runtime).search(SESSION, "pump", "1", "20");

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(GlobalSearchViews.SearchItem::moduleCode)
                .containsExactly("visible", "visible");
        assertThat(runtime.callsFor("vanished"))
                .extracting(QueryCall::size)
                .containsExactly(1, 200);
    }

    @Test
    void projectsOnlyReadableSearchableOrdinaryFieldsAndNeverMatchesSensitiveValues() {
        var runtime = new FakeRuntime(mapper, navigation("visible"));
        runtime.addReady(
                "visible",
                1,
                definition(
                        field("public_text", "TEXT", true, false, "ENABLED", "FILTER"),
                        field("secret_code", "SECRET", true, false, "ENABLED", "FILTER"),
                        field("identity_no", "IDENTITY", true, false, "ENABLED", "FILTER"),
                        field("not_searchable", "TEXT", false, false, "ENABLED", "FILTER"),
                        field("hidden_text", "TEXT", true, true, "ENABLED", "FILTER"),
                        field("disabled_text", "TEXT", true, false, "DISABLED", "FILTER"),
                        field("not_indexed", "TEXT", true, false, "ENABLED", "NONE"),
                        field("masked_text", "TEXT", true, false, "ENABLED", "FILTER"),
                        field("unreadable_text", "TEXT", true, false, "ENABLED", "FILTER")),
                schema(
                        "visible",
                        capabilities(
                                capability("secret_code", "SECRET", true, false),
                                capability("public_text", "TEXT", true, false),
                                capability("identity_no", "IDENTITY", true, false),
                                capability("not_searchable", "TEXT", true, false),
                                capability("hidden_text", "TEXT", true, false),
                                capability("disabled_text", "TEXT", true, false),
                                capability("not_indexed", "TEXT", true, false),
                                capability("masked_text", "TEXT", true, true),
                                capability("unreadable_text", "TEXT", false, false))));
        runtime.injectSensitiveValue = true;

        var page = service(runtime).search(SESSION, "pump", "1", "20");

        assertThat(runtime.calls).allSatisfy(call ->
                assertThat(call.columns()).containsExactly("public_text"));
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.matchedFieldCodes()).containsExactly("public_text");
            assertThat(item.displayLabel()).isEqualTo("visible-0 Pump record");
        });
    }

    @Test
    void leavesMatchedFieldsEmptyWhenTokensCannotBeReliablyAttributedToOneField() {
        var runtime = new FakeRuntime(mapper, navigation("visible"));
        runtime.addReady(
                "visible",
                1,
                definition(
                        field("summary", "TEXT", true, false, "ENABLED", "FILTER"),
                        field("detail", "TEXT", true, false, "ENABLED", "FILTER")),
                schema(
                        "visible",
                        capabilities(
                                capability("summary", "TEXT", true, false),
                                capability("detail", "TEXT", true, false))));
        runtime.splitQueryAcrossFields = true;

        var page = service(runtime).search(SESSION, "pump alpha", "1", "20");

        assertThat(page.items()).singleElement()
                .extracting(GlobalSearchViews.SearchItem::matchedFieldCodes)
                .isEqualTo(List.of());
    }

    private GlobalSearchService service(GlobalSearchRuntime runtime) {
        return new GlobalSearchService(runtime, new GlobalSearchRequestParser(), mapper);
    }

    private RuntimeViews.Navigation navigation(String... moduleCodes) {
        var modules = java.util.Arrays.stream(moduleCodes)
                .map(code -> new RuntimeViews.Module(
                        code + "-id", code, code + " name", null, 0, code + ".view", null))
                .toList();
        return new RuntimeViews.Navigation(
                "100",
                "1",
                List.of(new RuntimeViews.Group("group-id", "operations", "Operations", null, 0, modules)));
    }

    private RuntimeViews.Definition definition(JsonNode... fields) {
        var array = mapper.createArrayNode();
        array.addAll(java.util.Arrays.asList(fields));
        var empty = mapper.createArrayNode();
        return new RuntimeViews.Definition(
                "100", "1", mapper.createObjectNode(), array,
                empty, empty, empty, empty, empty, empty, true);
    }

    private JsonNode field(
            String code,
            String type,
            boolean searchable,
            boolean hidden,
            String status,
            String indexMode
    ) {
        return mapper.createObjectNode()
                .put("field_code", code)
                .put("field_type", type)
                .put("is_searchable", searchable)
                .put("is_hidden", hidden)
                .put("desired_status", status)
                .put("index_mode", indexMode);
    }

    private RecordRuntimeViews.RecordSchema schema(
            String moduleCode,
            List<RecordRuntimeViews.FieldCapability> fields
    ) {
        return schema(moduleCode, "READY", fields);
    }

    private RecordRuntimeViews.RecordSchema schema(
            String moduleCode,
            String state,
            List<RecordRuntimeViews.FieldCapability> fields
    ) {
        return new RecordRuntimeViews.RecordSchema(
                moduleCode + "-schema",
                moduleCode + "-snapshot",
                moduleCode + "-logical",
                moduleCode + "-checksum",
                state,
                "READY".equals(state) ? null : "not ready",
                1,
                fields,
                List.of(),
                new RecordRuntimeViews.QueryLimits(50, 200, 3));
    }

    private static List<RecordRuntimeViews.FieldCapability> capabilities(
            RecordRuntimeViews.FieldCapability... capabilities
    ) {
        return List.of(capabilities);
    }

    private RecordRuntimeViews.FieldCapability capability(
            String code,
            String type,
            boolean readable,
            boolean masked
    ) {
        return new RecordRuntimeViews.FieldCapability(
                code,
                code + " name",
                code + "-logical",
                type,
                "EDIT",
                readable,
                false,
                false,
                false,
                masked,
                List.of("CONTAINS"),
                false,
                true,
                true,
                List.of(),
                mapper.createObjectNode());
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.groups.Tuple.tuple(values);
    }

    private record QueryCall(String moduleCode, JsonNode body) {
        private int page() {
            return body.path("page").asInt();
        }

        private int size() {
            return body.path("size").asInt();
        }

        private List<String> columns() {
            var result = new ArrayList<String>();
            body.path("columns").forEach(value -> result.add(value.asText()));
            return result;
        }
    }

    private static final class FakeRuntime implements GlobalSearchRuntime {
        private final ObjectMapper mapper;
        private final RuntimeViews.Navigation navigation;
        private final Map<String, RuntimeViews.Definition> definitions = new LinkedHashMap<>();
        private final Map<String, RecordRuntimeViews.RecordSchema> schemas = new LinkedHashMap<>();
        private final Map<String, Long> totals = new LinkedHashMap<>();
        private final Set<String> deniedInspection = new LinkedHashSet<>();
        private final Set<String> deniedFetch = new LinkedHashSet<>();
        private final List<QueryCall> calls = new ArrayList<>();
        private boolean injectSensitiveValue;
        private boolean splitQueryAcrossFields;

        private FakeRuntime(ObjectMapper mapper, RuntimeViews.Navigation navigation) {
            this.mapper = mapper;
            this.navigation = navigation;
        }

        private void addReady(
                String moduleCode,
                long total,
                RuntimeViews.Definition definition,
                RecordRuntimeViews.RecordSchema schema
        ) {
            definitions.put(moduleCode, definition);
            schemas.put(moduleCode, schema);
            totals.put(moduleCode, total);
        }

        @Override
        public RuntimeViews.Navigation navigation(RuntimeSession session) {
            return navigation;
        }

        @Override
        public RuntimeViews.Definition definition(RuntimeSession session, String moduleCode) {
            if (deniedInspection.contains(moduleCode)) {
                throw denied();
            }
            return definitions.get(moduleCode);
        }

        @Override
        public RecordRuntimeViews.RecordSchema schema(RuntimeSession session, String moduleCode) {
            if (deniedInspection.contains(moduleCode)) {
                throw denied();
            }
            return schemas.get(moduleCode);
        }

        @Override
        public RecordRuntimeViews.RecordPage query(
                RuntimeSession session,
                String moduleCode,
                String body
        ) {
            var request = read(body);
            var call = new QueryCall(moduleCode, request);
            calls.add(call);
            if (call.size() > 1 && deniedFetch.contains(moduleCode)) {
                throw denied();
            }
            var total = totals.getOrDefault(moduleCode, 0L);
            var offset = (long) (call.page() - 1) * call.size();
            var count = (int) Math.min(call.size(), Math.max(0, total - offset));
            var rows = new ArrayList<RecordRuntimeViews.RecordSummary>();
            for (var index = 0; index < count; index++) {
                var ordinal = offset + index;
                rows.add(record(moduleCode, ordinal, call.columns()));
            }
            return new RecordRuntimeViews.RecordPage(rows, call.page(), call.size(), total);
        }

        private RecordRuntimeViews.RecordSummary record(
                String moduleCode,
                long ordinal,
                List<String> columns
        ) {
            var values = new ArrayList<RecordRuntimeViews.FieldValue>();
            for (var column : columns) {
                var text = splitQueryAcrossFields
                        ? ("summary".equals(column) ? "Pump" : "Alpha")
                        : "Pump Alpha";
                values.add(new RecordRuntimeViews.FieldValue(
                        column, column + " name", "TEXT", text, text));
            }
            if (injectSensitiveValue) {
                values.add(new RecordRuntimeViews.FieldValue(
                        "secret_code", "Secret", "SECRET", "Pump Alpha secret", "Pump Alpha secret"));
            }
            return new RecordRuntimeViews.RecordSummary(
                    moduleCode + "-" + ordinal,
                    moduleCode + "-" + ordinal,
                    1,
                    "ACTIVE",
                    "Pump record",
                    values);
        }

        private JsonNode read(String body) {
            try {
                return mapper.readTree(body);
            } catch (java.io.IOException exception) {
                throw new IllegalArgumentException(exception);
            }
        }

        private List<QueryCall> callsFor(String moduleCode) {
            return calls.stream().filter(call -> call.moduleCode().equals(moduleCode)).toList();
        }

        private static BusinessException denied() {
            return new BusinessException("PERMISSION_DENIED", "denied", HttpStatus.FORBIDDEN);
        }
    }
}
