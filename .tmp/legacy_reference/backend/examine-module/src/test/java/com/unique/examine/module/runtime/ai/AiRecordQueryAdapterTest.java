package com.unique.examine.module.runtime.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.ai.AiRecordQueryFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRecordQueryAdapterTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> VIEW = Set.of(
            "system.runtime.access",
            "module.work_order.view",
            "module.work_order.field.name.read",
            "module.work_order.field.secret_note.read");

    @Test
    void delegatesACompleteBoundedNativeQueryThroughTheAuthoritativeSession() throws Exception {
        var seenSession = new AtomicReference<RuntimeSession>();
        var seenModule = new AtomicReference<String>();
        var seenQuery = new AtomicReference<String>();
        var adapter = new AiRecordQueryAdapter(
                (session, moduleCode) -> schema("41"),
                (session, moduleCode, query) -> {
                    seenSession.set(session);
                    seenModule.set(moduleCode);
                    seenQuery.set(query);
                    return page(9L, 3);
                });
        var fragment = """
                {"recordScope":"active","q":null,"filter":null,"sort":[],
                 "columns":["name","secret_note"]}
                """;

        var result = adapter.query(request(VIEW, fragment, List.of("name", "secret_note"), 2));

        assertThat(result.total()).isEqualTo(9L);
        assertThat(result.records()).hasSize(2);
        assertThat(result.records().getFirst().values()).containsExactly(
                new AiRecordQueryFacade.DisplayValue("name", "Name 1"),
                new AiRecordQueryFacade.DisplayValue("secret_note", "******"));
        assertThat(seenSession.get()).isEqualTo(
                new RuntimeSession(0L, 11L, 17L, 13L, VIEW));
        assertThat(seenModule.get()).isEqualTo("work_order");
        var nativeQuery = JSON.readTree(seenQuery.get());
        assertThat(nativeQuery.path("schemaVersionId").asText()).isEqualTo("41");
        assertThat(nativeQuery.path("page").asInt()).isEqualTo(1);
        assertThat(nativeQuery.path("size").asInt()).isEqualTo(2);
        assertThat(nativeQuery.path("viewId").isNull()).isTrue();
        assertThat(nativeQuery.path("columns"))
                .isEqualTo(JSON.readTree("[\"name\",\"secret_note\"]"));
        assertThat(AiRecordQueryAdapter.class.getMethod(
                        "query", AiRecordQueryFacade.Request.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    @Test
    void outboundAllowlistDropsRawAndUnrequestedValuesAndDefensivelyCapsRows() throws Exception {
        var adapter = new AiRecordQueryAdapter(
                (session, moduleCode) -> schema("41"),
                (session, moduleCode, query) -> page(20L, 4));

        var result = adapter.query(request(
                VIEW,
                "{\"columns\":[\"name\"],\"filter\":null,\"sort\":[]}",
                List.of("name", "secret_note"),
                2));

        assertThat(result.total()).isEqualTo(20L);
        assertThat(result.records()).hasSize(2).allSatisfy(record ->
                assertThat(record.values())
                        .extracting(AiRecordQueryFacade.DisplayValue::fieldCode)
                        .containsExactly("name"));
        var serialized = JSON.writeValueAsString(result);
        assertThat(serialized)
                .doesNotContain("RAW-NAME", "RAW-SECRET", "not_allowed", "RAW-HIDDEN")
                .contains("Name 1");
    }

    @Test
    void deniesMissingShellOrModuleViewBeforeSchemaAndQuery() {
        var calls = new AtomicInteger();
        var adapter = new AiRecordQueryAdapter(
                (session, moduleCode) -> {
                    calls.incrementAndGet();
                    return schema("41");
                },
                (session, moduleCode, query) -> {
                    calls.incrementAndGet();
                    return page(0L, 0);
                });

        assertDenied(() -> adapter.query(request(
                Set.of("module.work_order.view"), "{}", List.of(), 1)));
        assertDenied(() -> adapter.query(request(
                Set.of("system.runtime.access"), "{}", List.of(), 1)));
        assertThat(calls).hasValue(0);
    }

    @Test
    void rejectsUnknownDuplicateOrOutOfPolicyFragmentFieldsBeforeOwnerCalls() {
        var calls = new AtomicInteger();
        var adapter = new AiRecordQueryAdapter(
                (session, moduleCode) -> {
                    calls.incrementAndGet();
                    return schema("41");
                },
                (session, moduleCode, query) -> {
                    calls.incrementAndGet();
                    return page(0L, 0);
                });

        assertInvalid(() -> adapter.query(request(
                VIEW, "{\"sql\":\"select *\"}", List.of("name"), 1)));
        assertInvalid(() -> adapter.query(request(
                VIEW, "{\"q\":null,\"q\":\"again\"}", List.of("name"), 1)));
        assertInvalid(() -> adapter.query(request(
                VIEW, "{\"columns\":[\"secret_note\"]}", List.of("name"), 1)));
        assertInvalid(() -> adapter.query(request(
                VIEW, "{\"columns\":[\"name\",\"name\"]}", List.of("name"), 1)));
        assertThat(calls).hasValue(0);
    }

    @Test
    void propagatesCanonicalSchemaAndQueryFailuresUnchanged() {
        var schemaFailure = new BusinessException(
                "MODULE_NOT_PUBLISHED", "hidden", HttpStatus.NOT_FOUND);
        var schemaAdapter = new AiRecordQueryAdapter(
                (session, moduleCode) -> {
                    throw schemaFailure;
                },
                (session, moduleCode, query) -> page(0L, 0));

        assertThatThrownBy(() -> schemaAdapter.query(request(VIEW, "{}", List.of(), 1)))
                .isSameAs(schemaFailure);

        var queryFailure = new BusinessException(
                "QUERY_SCOPE_FORBIDDEN", "revoked", HttpStatus.FORBIDDEN);
        var queryAdapter = new AiRecordQueryAdapter(
                (session, moduleCode) -> schema("41"),
                (session, moduleCode, query) -> {
                    throw queryFailure;
                });

        assertThatThrownBy(() -> queryAdapter.query(request(VIEW, "{}", List.of(), 1)))
                .isSameAs(queryFailure);
    }

    private static AiRecordQueryFacade.Request request(
            Set<String> permissions,
            String fragment,
            List<String> outboundFields,
            int maxRows
    ) {
        return new AiRecordQueryFacade.Request(
                11L, 13L, 17L, permissions,
                "work_order", fragment, outboundFields, maxRows);
    }

    private static RecordRuntimeViews.RecordSchema schema(String versionId) {
        return new RecordRuntimeViews.RecordSchema(
                versionId, "31", "31", "checksum", "READY", null, 1,
                List.of(), List.of(), new RecordRuntimeViews.QueryLimits(20, 200, 3));
    }

    private static RecordRuntimeViews.RecordPage page(long total, int rowCount) {
        var rows = new ArrayList<RecordRuntimeViews.RecordSummary>();
        for (var index = 1; index <= rowCount; index++) {
            rows.add(new RecordRuntimeViews.RecordSummary(
                    Integer.toString(index),
                    "WO-" + index,
                    index,
                    "ACTIVE",
                    "Record " + index,
                    List.of(
                            value("name", "TEXT", "RAW-NAME-" + index, "Name " + index),
                            value("secret_note", "SECRET", "RAW-SECRET-" + index, "******"),
                            value("not_allowed", "TEXT", "RAW-HIDDEN-" + index, "Hidden " + index))));
        }
        return new RecordRuntimeViews.RecordPage(rows, 1, rowCount, total);
    }

    private static RecordRuntimeViews.FieldValue value(
            String fieldCode,
            String type,
            String raw,
            String display
    ) {
        return new RecordRuntimeViews.FieldValue(
                fieldCode, fieldCode, type, raw, display, null);
    }

    private static void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("PERMISSION_DENIED");
                    assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("AI_RECORD_QUERY_INVALID");
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                });
    }
}
