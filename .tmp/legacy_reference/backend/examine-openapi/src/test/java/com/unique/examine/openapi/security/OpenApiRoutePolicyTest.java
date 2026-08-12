package com.unique.examine.openapi.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiRoutePolicyTest {
    private static final String START_PATH =
            "/openapi/v1/flow/definitions/42/instances";
    private static final String START_TEMPLATE =
            "/openapi/v1/flow/definitions/{definitionId}/instances";
    private static final String FLOW_DETAIL =
            "/openapi/v1/flow/instances/9223372036854775807";
    private static final String FLOW_DETAIL_TEMPLATE =
            "/openapi/v1/flow/instances/{instanceId}";
    private static final String RECORDS =
            "/openapi/v1/modules/Purchase_order/records";
    private static final String RECORDS_TEMPLATE =
            "/openapi/v1/modules/{moduleCode}/records";
    private static final String DETAIL = RECORDS + "/9223372036854775807";
    private static final String DETAIL_TEMPLATE =
            "/openapi/v1/modules/{moduleCode}/records/{recordId}";
    private static final String FILES = DETAIL + "/files";
    private static final String FILES_TEMPLATE = DETAIL_TEMPLATE + "/files";
    private static final String FILE_CONTENT = FILES + "/17/content";
    private static final String FILE_CONTENT_TEMPLATE =
            FILES_TEMPLATE + "/{fileId}/content";
    private static final String RELATION = DETAIL + "/relations/line_items";
    private static final String RELATION_TEMPLATE =
            DETAIL_TEMPLATE + "/relations/{fieldCode}";
    private static final String SUBTABLE = DETAIL + "/subtables/delivery_rows";
    private static final String SUBTABLE_TEMPLATE =
            DETAIL_TEMPLATE + "/subtables/{fieldCode}";
    private static final List<String> ACTIONS = List.of(
            "activate", "archive", "unarchive", "trash",
            "restore-from-trash");

    @Test
    void resolvesParameterizedFlowInstanceStartToFrozenPolicy() {
        var policy = OpenApiRoutePolicy.resolve("POST", START_PATH).orElseThrow();

        assertThat(policy.method()).isEqualTo("POST");
        assertThat(policy.path()).isEqualTo(START_PATH);
        assertThat(policy.routeTemplate()).isEqualTo(START_TEMPLATE);
        assertThat(policy.requiredScope()).isEqualTo("flow.instance.start");
        assertThat(policy.requiredMemberPermission()).isEqualTo("flow.instance.start");
    }

    @Test
    void resolvesMaximumPositiveFlowInstanceToReadPolicy() {
        assertThat(OpenApiRoutePolicy.resolve("get", FLOW_DETAIL))
                .hasValueSatisfying(policy -> assertThat(policy)
                        .extracting(OpenApiRoutePolicy::method,
                                OpenApiRoutePolicy::path,
                                OpenApiRoutePolicy::routeTemplate,
                                OpenApiRoutePolicy::requiredScope,
                                OpenApiRoutePolicy::requiredMemberPermission)
                        .containsExactly(
                                "GET", FLOW_DETAIL, FLOW_DETAIL_TEMPLATE,
                                "flow.read", "flow.instance.read"));
    }

    @Test
    void flowInstanceReadRejectsInvalidIdsSuffixesAndWrongMethods() {
        assertThat(List.of(
                "/openapi/v1/flow/instances/0",
                "/openapi/v1/flow/instances/-1",
                "/openapi/v1/flow/instances/01",
                "/openapi/v1/flow/instances/1.5",
                "/openapi/v1/flow/instances/not-an-id",
                "/openapi/v1/flow/instances/9223372036854775808",
                "/openapi/v1/flow/instances/42?include=steps",
                "/openapi/v1/flow/instances/42/",
                "/openapi/v1/flow/instances/42/steps"
        )).allSatisfy(path ->
                assertThat(OpenApiRoutePolicy.resolve("GET", path)).isEmpty());
        assertThat(List.of("POST", "PUT", "PATCH", "DELETE", "OPTIONS"))
                .allSatisfy(method -> assertThat(
                        OpenApiRoutePolicy.resolve(method, FLOW_DETAIL))
                        .isEmpty());
    }

    @Test
    void acceptsCaseInsensitivePostAndMaximumPositiveLongId() {
        var path = "/openapi/v1/flow/definitions/"
                + Long.MAX_VALUE + "/instances";

        assertThat(OpenApiRoutePolicy.resolve("post", path))
                .hasValueSatisfying(policy -> {
                    assertThat(policy.method()).isEqualTo("POST");
                    assertThat(policy.path()).isEqualTo(path);
                    assertThat(policy.routeTemplate()).isEqualTo(START_TEMPLATE);
                });
    }

    @Test
    void rejectsNonPositiveMalformedOverflowAndExtraSegments() {
        var rejectedPaths = List.of(
                "/openapi/v1/flow/definitions/0/instances",
                "/openapi/v1/flow/definitions/-1/instances",
                "/openapi/v1/flow/definitions/not-a-number/instances",
                "/openapi/v1/flow/definitions/1.5/instances",
                "/openapi/v1/flow/definitions/9223372036854775808/instances",
                "/openapi/v1/flow/definitions/42/instances/extra",
                "/openapi/v1/flow/definitions/42",
                "/openapi/v1/flow/definitions//instances",
                "/openapi/v1/flow/definitions/42/instances?extra=true"
        );

        assertThat(rejectedPaths)
                .allSatisfy(path ->
                        assertThat(OpenApiRoutePolicy.resolve("POST", path)).isEmpty());
    }

    @Test
    void rejectsWrongMethodsAndNullInputs() {
        assertThat(List.of("GET", "PUT", "PATCH", "DELETE", "OPTIONS"))
                .allSatisfy(method ->
                        assertThat(OpenApiRoutePolicy.resolve(method, START_PATH)).isEmpty());
        assertThat(OpenApiRoutePolicy.resolve(null, START_PATH)).isEmpty();
        assertThat(OpenApiRoutePolicy.resolve("POST", null)).isEmpty();
    }

    @Test
    void preservesExistingPingPolicyAndClosesUnknownOpenApiRoutes() {
        var ping = OpenApiRoutePolicy.resolve("GET", "/openapi/v1/ping").orElseThrow();

        assertThat(ping.routeTemplate()).isEqualTo("/openapi/v1/ping");
        assertThat(ping.requiredScope()).isEqualTo("openapi.ping");
        assertThat(ping.requiredMemberPermission()).isEqualTo("system.runtime.access");
        assertThat(OpenApiRoutePolicy.resolve("POST", "/openapi/v1/ping")).isEmpty();
        assertThat(OpenApiRoutePolicy.resolve(
                "POST", "/openapi/v1/flow/definitions/42/tasks")).isEmpty();
    }

    @Test
    void resolvesCreateListAndDetailToParameterizedRecordPolicies() {
        assertThat(OpenApiRoutePolicy.resolve("POST", RECORDS))
                .hasValueSatisfying(policy -> assertThat(policy)
                        .extracting(OpenApiRoutePolicy::method,
                                OpenApiRoutePolicy::path,
                                OpenApiRoutePolicy::routeTemplate,
                                OpenApiRoutePolicy::requiredScope,
                                OpenApiRoutePolicy::requiredMemberPermission)
                        .containsExactly("POST", RECORDS, RECORDS_TEMPLATE,
                                "record.write", "system.runtime.access"));
        assertThat(OpenApiRoutePolicy.resolve("get", RECORDS))
                .hasValueSatisfying(policy -> assertThat(policy)
                        .extracting(OpenApiRoutePolicy::method,
                                OpenApiRoutePolicy::routeTemplate,
                                OpenApiRoutePolicy::requiredScope,
                                OpenApiRoutePolicy::requiredMemberPermission)
                        .containsExactly("GET", RECORDS_TEMPLATE,
                                "record.read", "system.runtime.access"));
        assertThat(OpenApiRoutePolicy.resolve("GET", DETAIL))
                .hasValueSatisfying(policy -> assertThat(policy)
                        .extracting(OpenApiRoutePolicy::method,
                                OpenApiRoutePolicy::path,
                                OpenApiRoutePolicy::routeTemplate,
                                OpenApiRoutePolicy::requiredScope,
                                OpenApiRoutePolicy::requiredMemberPermission)
                        .containsExactly("GET", DETAIL, DETAIL_TEMPLATE,
                                "record.read", "system.runtime.access"));
    }

    @Test
    void resolvesUpdateAndEveryLifecycleActionToWritePolicy() {
        assertThat(OpenApiRoutePolicy.resolve("put", DETAIL))
                .hasValueSatisfying(policy -> assertThat(policy)
                        .extracting(OpenApiRoutePolicy::method,
                                OpenApiRoutePolicy::path,
                                OpenApiRoutePolicy::routeTemplate,
                                OpenApiRoutePolicy::requiredScope,
                                OpenApiRoutePolicy::requiredMemberPermission)
                        .containsExactly("PUT", DETAIL, DETAIL_TEMPLATE,
                                "record.write", "system.runtime.access"));

        assertThat(ACTIONS).allSatisfy(action -> {
            var path = DETAIL + ":" + action;
            assertThat(OpenApiRoutePolicy.resolve("post", path))
                    .hasValueSatisfying(policy -> assertThat(policy)
                            .extracting(OpenApiRoutePolicy::method,
                                    OpenApiRoutePolicy::path,
                                    OpenApiRoutePolicy::routeTemplate,
                                    OpenApiRoutePolicy::requiredScope,
                                    OpenApiRoutePolicy::requiredMemberPermission)
                            .containsExactly(
                                    "POST", path, DETAIL_TEMPLATE + ":" + action,
                                    "record.write", "system.runtime.access"));
        });
    }

    @Test
    void resolvesUploadListAndContentToParameterizedFilePolicies() {
        assertThat(OpenApiRoutePolicy.resolve("post", FILES))
                .hasValueSatisfying(policy -> assertThat(policy)
                        .extracting(OpenApiRoutePolicy::method,
                                OpenApiRoutePolicy::path,
                                OpenApiRoutePolicy::routeTemplate,
                                OpenApiRoutePolicy::requiredScope,
                                OpenApiRoutePolicy::requiredMemberPermission)
                        .containsExactly("POST", FILES, FILES_TEMPLATE,
                                "file.write", "system.runtime.access"));
        assertThat(OpenApiRoutePolicy.resolve("get", FILES))
                .hasValueSatisfying(policy -> assertThat(policy)
                        .extracting(OpenApiRoutePolicy::method,
                                OpenApiRoutePolicy::path,
                                OpenApiRoutePolicy::routeTemplate,
                                OpenApiRoutePolicy::requiredScope,
                                OpenApiRoutePolicy::requiredMemberPermission)
                        .containsExactly("GET", FILES, FILES_TEMPLATE,
                                "file.read", "system.runtime.access"));
        assertThat(OpenApiRoutePolicy.resolve("GET", FILE_CONTENT))
                .hasValueSatisfying(policy -> assertThat(policy)
                        .extracting(OpenApiRoutePolicy::method,
                                OpenApiRoutePolicy::path,
                                OpenApiRoutePolicy::routeTemplate,
                                OpenApiRoutePolicy::requiredScope,
                                OpenApiRoutePolicy::requiredMemberPermission)
                        .containsExactly("GET", FILE_CONTENT,
                                FILE_CONTENT_TEMPLATE,
                                "file.read", "system.runtime.access"));
    }

    @Test
    void resolvesRelationAndSubtableReadsAndMutations() {
        assertCompositionPolicy(
                "GET", RELATION, RELATION_TEMPLATE,
                "record.read", "system.runtime.access");
        assertCompositionPolicy(
                "POST", RELATION + ":mutate", RELATION_TEMPLATE + ":mutate",
                "record.write", "system.runtime.access");
        assertCompositionPolicy(
                "get", SUBTABLE, SUBTABLE_TEMPLATE,
                "record.read", "system.runtime.access");
        assertCompositionPolicy(
                "post", SUBTABLE + ":mutate", SUBTABLE_TEMPLATE + ":mutate",
                "record.write", "system.runtime.access");
    }

    @Test
    void compositionRoutesRejectMalformedCodesIdsSuffixesAndMethods() {
        assertThat(List.of(
                "/openapi/v1/modules/1orders/records/42/relations/items",
                "/openapi/v1/modules/order-items/records/42/relations/items",
                "/openapi/v1/modules/orders/records/0/relations/items",
                "/openapi/v1/modules/orders/records/-1/relations/items",
                "/openapi/v1/modules/orders/records/1.5/relations/items",
                "/openapi/v1/modules/orders/records/9223372036854775808/"
                        + "relations/items",
                "/openapi/v1/modules/orders/records/42/relation/items",
                "/openapi/v1/modules/orders/records/42/relations/1items",
                "/openapi/v1/modules/orders/records/42/relations/item-lines",
                "/openapi/v1/modules/orders/records/42/relations/items.value",
                "/openapi/v1/modules/orders/records/42/relations/"
                        + "f".repeat(65),
                "/openapi/v1/modules/orders/records/42/subtables/",
                "/openapi/v1/modules/orders/records/42/subtables/items:MUTATE",
                "/openapi/v1/modules/orders/records/42/subtables/items:delete",
                "/openapi/v1/modules/orders/records/42/subtables/items/extra",
                "/openapi/v1/modules/orders/records/42/subtables/items?page=1"
        )).allSatisfy(path -> {
            assertThat(OpenApiRoutePolicy.resolve("GET", path)).isEmpty();
            assertThat(OpenApiRoutePolicy.resolve("POST", path)).isEmpty();
        });
        assertThat(OpenApiRoutePolicy.resolve("POST", RELATION)).isEmpty();
        assertThat(OpenApiRoutePolicy.resolve("GET", RELATION + ":mutate"))
                .isEmpty();
        assertThat(OpenApiRoutePolicy.resolve("POST", SUBTABLE)).isEmpty();
        assertThat(OpenApiRoutePolicy.resolve("GET", SUBTABLE + ":mutate"))
                .isEmpty();
        assertThat(List.of("PUT", "PATCH", "DELETE", "OPTIONS"))
                .allSatisfy(method -> {
                    assertThat(OpenApiRoutePolicy.resolve(method, RELATION))
                            .isEmpty();
                    assertThat(OpenApiRoutePolicy.resolve(
                            method, SUBTABLE + ":mutate")).isEmpty();
                });
    }

    @Test
    void recordRoutesRejectMalformedCodesIdsQueriesExtraSegmentsAndMethods() {
        assertThat(List.of(
                "/openapi/v1/modules/1orders/records",
                "/openapi/v1/modules/order-items/records",
                "/openapi/v1/modules/orders%2Fadmin/records",
                "/openapi/v1/modules/orders/records/0",
                "/openapi/v1/modules/orders/records/-1",
                "/openapi/v1/modules/orders/records/not-an-id",
                "/openapi/v1/modules/orders/records/9223372036854775808",
                "/openapi/v1/modules/orders/records/42/fields",
                "/openapi/v1/modules/orders/records?page=1",
                "/openapi/v1/modules/orders/records/0:activate",
                "/openapi/v1/modules/orders/records/9223372036854775808:trash",
                "/openapi/v1/modules/orders/records/42:publish",
                "/openapi/v1/modules/orders/records/42:ACTIVATE",
                "/openapi/v1/modules/orders/records/42:restore",
                "/openapi/v1/modules/orders/records/42:activate/extra",
                "/openapi/v1/modules/orders/records/42:archive?force=true",
                "/openapi/v1/modules/orders/records/0/files",
                "/openapi/v1/modules/orders/records/9223372036854775808/files",
                "/openapi/v1/modules/orders/records/42/files/0/content",
                "/openapi/v1/modules/orders/records/42/files/-1/content",
                "/openapi/v1/modules/orders/records/42/files/not-an-id/content",
                "/openapi/v1/modules/orders/records/42/files/"
                        + "9223372036854775808/content",
                "/openapi/v1/modules/orders/records/42/files/17",
                "/openapi/v1/modules/orders/records/42/files/17/content/extra",
                "/openapi/v1/modules/orders/records/42/files?page=1"
        )).allSatisfy(path -> {
            assertThat(OpenApiRoutePolicy.resolve("GET", path)).isEmpty();
            assertThat(OpenApiRoutePolicy.resolve("POST", path)).isEmpty();
            assertThat(OpenApiRoutePolicy.resolve("PUT", path)).isEmpty();
        });
        assertThat(List.of("PATCH", "DELETE", "OPTIONS"))
                .allSatisfy(method -> {
                    assertThat(OpenApiRoutePolicy.resolve(method, RECORDS)).isEmpty();
                    assertThat(OpenApiRoutePolicy.resolve(method, DETAIL)).isEmpty();
                });
        assertThat(OpenApiRoutePolicy.resolve("POST", DETAIL)).isEmpty();
        assertThat(OpenApiRoutePolicy.resolve("PUT", RECORDS)).isEmpty();
        assertThat(ACTIONS).allSatisfy(action -> {
            var path = DETAIL + ":" + action;
            assertThat(OpenApiRoutePolicy.resolve("GET", path)).isEmpty();
            assertThat(OpenApiRoutePolicy.resolve("PUT", path)).isEmpty();
            assertThat(OpenApiRoutePolicy.resolve("PATCH", path)).isEmpty();
        });
        assertThat(List.of("PUT", "PATCH", "DELETE", "OPTIONS"))
                .allSatisfy(method ->
                        assertThat(OpenApiRoutePolicy.resolve(method, FILES))
                                .isEmpty());
        assertThat(List.of("POST", "PUT", "PATCH", "DELETE", "OPTIONS"))
                .allSatisfy(method -> assertThat(
                        OpenApiRoutePolicy.resolve(method, FILE_CONTENT))
                        .isEmpty());
    }

    private static void assertCompositionPolicy(
            String method,
            String path,
            String template,
            String scope,
            String permission
    ) {
        assertThat(OpenApiRoutePolicy.resolve(method, path))
                .hasValueSatisfying(policy -> assertThat(policy)
                        .extracting(OpenApiRoutePolicy::method,
                                OpenApiRoutePolicy::path,
                                OpenApiRoutePolicy::routeTemplate,
                                OpenApiRoutePolicy::requiredScope,
                                OpenApiRoutePolicy::requiredMemberPermission)
                        .containsExactly(
                                method.toUpperCase(), path, template,
                                scope, permission));
    }
}
