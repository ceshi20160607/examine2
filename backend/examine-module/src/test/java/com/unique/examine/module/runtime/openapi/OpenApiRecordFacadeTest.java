package com.unique.examine.module.runtime.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class OpenApiRecordFacadeTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void createUsesAuthoritativeMachineSessionAndCurrentWritableSchema() {
        var runtime = new FakeRuntime(schema(
                field("name", "TEXT", true, false),
                field("secret_note", "SECRET", true, true)));
        var facade = new OpenApiRecordFacade(runtime);
        var values = new LinkedHashMap<String, JsonNode>();
        values.put("name", JSON.getNodeFactory().textNode("Alpha"));

        var result = facade.create(session(101), "work_order",
                new OpenApiRecordFacade.CreateCommand("ACTIVE", values), "create-1");

        assertThat(result.recordId()).isEqualTo("1");
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(runtime.lastSession).satisfies(actual -> {
            assertThat(actual.accountId()).isEqualTo(7);
            assertThat(actual.systemId()).isEqualTo(11);
            assertThat(actual.tenantId()).isEqualTo(13);
            assertThat(actual.memberId()).isEqualTo(17);
            assertThat(actual.permissions()).containsExactlyInAnyOrder(
                    "module.work_order.view", "module.work_order.create");
        });
        assertThat(runtime.lastApplicationId).isEqualTo(101);
        assertThat(runtime.lastLifecycle).isEqualTo("ACTIVE");
        assertThat(runtime.lastRequestId).isEqualTo("request-1");
        assertThat(runtime.lastTraceId).isEqualTo("trace-1");
        assertThat(runtime.lastValues).containsOnlyKeys("name");
    }

    @Test
    void rejectsTransportAndPublishedSystemFieldsBeforeMutation() {
        var runtime = new FakeRuntime(schema(field("tenant_code", "TENANT", false, false)));
        var facade = new OpenApiRecordFacade(runtime);

        var transportField = catchThrowableOfType(
                () -> facade.create(session(101), "work_order", command("tenantId", "99"), "create-1"),
                BusinessException.class);
        var publishedSystemField = catchThrowableOfType(
                () -> facade.create(session(101), "work_order", command("tenant_code", "99"), "create-2"),
                BusinessException.class);

        assertThat(transportField.code()).isEqualTo("OPENAPI_RECORD_VALUES_INVALID");
        assertThat(transportField.errors()).extracting(error -> error.code())
                .containsExactly("SYSTEM_FIELD_CLIENT_VALUE_FORBIDDEN");
        assertThat(publishedSystemField.errors()).extracting(error -> error.code())
                .containsExactly("SYSTEM_FIELD_CLIENT_VALUE_FORBIDDEN");
        assertThat(runtime.createCalls).hasValue(0);
    }

    @Test
    void rejectsUnknownAndCurrentlyNonWritableFieldsBeforeMutation() {
        var runtime = new FakeRuntime(schema(field("approval", "TEXT", false, false)));
        var facade = new OpenApiRecordFacade(runtime);

        var exception = catchThrowableOfType(
                () -> facade.create(session(101), "work_order",
                        new OpenApiRecordFacade.CreateCommand("DRAFT", Map.of(
                                "approval", JSON.getNodeFactory().textNode("yes"),
                                "retired_field", JSON.getNodeFactory().textNode("x"))),
                        "create-1"),
                BusinessException.class);

        assertThat(exception.code()).isEqualTo("OPENAPI_RECORD_VALUES_INVALID");
        assertThat(exception.errors()).extracting(error -> error.code())
                .containsExactlyInAnyOrder("OPENAPI_FIELD_WRITE_FORBIDDEN", "OPENAPI_FIELD_UNKNOWN");
        assertThat(runtime.createCalls).hasValue(0);
    }

    @Test
    void exactReplayIsStableChangedPayloadConflictsAndApplicationsAreIsolated() {
        var runtime = new FakeRuntime(schema(field("name", "TEXT", true, false)));
        var facade = new OpenApiRecordFacade(runtime);

        var first = facade.create(session(101), "work_order", command("name", "Alpha"), "stable-key");
        var replay = facade.create(session(101), "work_order", command("name", "Alpha"), "stable-key");
        var conflict = catchThrowableOfType(
                () -> facade.create(session(101), "work_order", command("name", "Beta"), "stable-key"),
                BusinessException.class);
        var anotherApplication = facade.create(session(102), "work_order",
                command("name", "Beta"), "stable-key");

        assertThat(replay).isEqualTo(first);
        assertThat(conflict.code()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(anotherApplication.recordId()).isEqualTo("2");
        assertThat(runtime.persistedCreates).hasValue(2);
    }

    @Test
    void detailAndListDelegatePermissionSafeProjectionAndDataScope() {
        var runtime = new FakeRuntime(schema(field("secret_note", "SECRET", true, true)));
        runtime.detail = new RecordRuntimeViews.RecordDetail(
                "9", "R-9", 3, "ACTIVE", "Visible", "41",
                List.of(new RecordRuntimeViews.FieldValue(
                        "secret_note", "Secret", "SECRET", null, "******", null)),
                List.of("UPDATE"));
        runtime.page = new RecordRuntimeViews.RecordPage(List.of(new RecordRuntimeViews.RecordSummary(
                "9", "R-9", 3, "ACTIVE", "Visible", runtime.detail.values())), 1, 20, 1);
        var facade = new OpenApiRecordFacade(runtime);

        var detail = facade.detail(session(101), "work_order", "9");
        var page = facade.list(session(101), "work_order", 1, 20);

        assertThat(detail.values().getFirst().value()).isNull();
        assertThat(detail.values().getFirst().displayValue()).isEqualTo("******");
        assertThat(page.rows()).hasSize(1);
        assertThat(runtime.detailCalls).hasValue(1);
        assertThat(runtime.listCalls).hasValue(1);
        assertThat(runtime.lastSession.permissions()).contains("module.work_order.view");

        runtime.detailFailure = new BusinessException(
                "RECORD_NOT_FOUND", "hidden by scope", HttpStatus.NOT_FOUND);
        var hidden = catchThrowableOfType(
                () -> facade.detail(session(101), "work_order", "10"), BusinessException.class);
        assertThat(hidden.code()).isEqualTo("RECORD_NOT_FOUND");
    }

    @Test
    void compositionReadsDelegateCanonicalScopeAndReturnStableProjections() {
        var runtime = new FakeRuntime(schema(field("name", "TEXT", true, false)));
        runtime.relationPage = new RecordRuntimeViews.RelationPage(
                List.of(new RecordRuntimeViews.RelationItem("81", 3L, 0, "Related")),
                2, 25, 26L, capabilities(), "trace-1");
        runtime.subtablePage = new RecordRuntimeViews.SubtablePage(
                List.of(new RecordRuntimeViews.SubRowResponse(
                        "91", 4L, 1,
                        List.of(new RecordRuntimeViews.FieldValue(
                                "line_name", "Line name", "TEXT", "One", "One")))),
                1, 100, 1L, capabilities(), "trace-1");
        var facade = new OpenApiRecordFacade(runtime);

        var relations = facade.relations(
                session(101), "work_order", "9", "related_orders", 2, 25);
        var subtable = facade.subtable(
                session(101), "work_order", "9", "lines", 1, 100);

        assertThat(relations).isEqualTo(new OpenApiRecordFacade.RelationPageView(
                List.of(new OpenApiRecordFacade.RelationItemView(
                        "81", 3L, 0, "Related")),
                2, 25, 26L,
                capabilityView(), "trace-1"));
        assertThat(subtable.items()).containsExactly(
                new OpenApiRecordFacade.SubtableRowView(
                        "91", 4L, 1,
                        List.of(new OpenApiRecordFacade.FieldValueView(
                                "line_name", "Line name", "TEXT", "One", "One", null))));
        assertThat(subtable.capabilities()).isEqualTo(capabilityView());
        assertThat(runtime.compositionReadCalls).hasValue(2);
        assertThat(runtime.lastSession).satisfies(actual -> {
            assertThat(actual.systemId()).isEqualTo(11L);
            assertThat(actual.tenantId()).isEqualTo(13L);
            assertThat(actual.memberId()).isEqualTo(17L);
        });
        assertThat(runtime.lastRecordId).isEqualTo(9L);
        assertThat(runtime.lastCorrelationId).isEqualTo("trace-1");
    }

    @Test
    void compositionMutationsMapCanonicalCommandsAndStableReceipts() {
        var runtime = new FakeRuntime(schema(field("name", "TEXT", true, false)));
        var facade = new OpenApiRecordFacade(runtime);
        var relation = new OpenApiRecordFacade.RelationMutationCommand(
                4L,
                List.of(new OpenApiRecordFacade.RelationTargetCommand("81", 3L, 0)),
                List.of("80"),
                List.of("81"));
        var subtable = new OpenApiRecordFacade.SubtableMutationCommand(
                5L,
                List.of(new OpenApiRecordFacade.SubtableRowCreateCommand(
                        "client-1", 0, Map.of("quantity", JSON.getNodeFactory().numberNode(2)))),
                List.of(new OpenApiRecordFacade.SubtableRowUpdateCommand(
                        "91", 4L, 1, Map.of("quantity", JSON.getNodeFactory().numberNode(3)))),
                List.of(new OpenApiRecordFacade.SubtableRowReferenceCommand("92", 2L)),
                List.of("client-1", "91"));

        var relationReceipt = facade.mutateRelation(
                session(101), "work_order", "9", "related_orders", relation, "relation-key");
        var subtableReceipt = facade.mutateSubtable(
                session(102), "work_order", "9", "lines", subtable, "subtable-key");

        assertThat(relationReceipt).isEqualTo(new OpenApiRecordFacade.MutationReceipt(
                "9", 5L, "41", "ACTIVE", "701", "trace-1"));
        assertThat(subtableReceipt).isEqualTo(relationReceipt);
        assertThat(runtime.lastRelationMutation).isEqualTo(
                new RecordRuntimeViews.RelationMutationRequest(
                        4L,
                        List.of(new RecordRuntimeViews.RelationTargetInput("81", 3L, 0)),
                        List.of("80"),
                        List.of("81")));
        assertThat(runtime.lastSubtableMutation.expectedVersion()).isEqualTo(5L);
        assertThat(runtime.lastSubtableMutation.add().getFirst().values())
                .containsEntry("quantity", JSON.getNodeFactory().numberNode(2));
        assertThat(runtime.lastSubtableMutation.update().getFirst().rowId()).isEqualTo("91");
        assertThat(runtime.lastSubtableMutation.remove().getFirst().rowId()).isEqualTo("92");
        assertThat(runtime.mutationApplications).containsExactly(101L, 102L);
        assertThat(runtime.mutationKeys).containsExactly("relation-key", "subtable-key");
        assertThat(runtime.lastRequestId).isEqualTo("request-1");
        assertThat(runtime.lastTraceId).isEqualTo("trace-1");
    }

    @Test
    void compositionRequestsRejectInvalidPathsPagingBodiesAndIdempotencyKeys() {
        var runtime = new FakeRuntime(schema(field("name", "TEXT", true, false)));
        var facade = new OpenApiRecordFacade(runtime);

        for (var recordId : List.of("0", "-1", "01", "+1", " 1", "9223372036854775808")) {
            var failure = catchThrowableOfType(
                    () -> facade.relations(
                            session(101), "work_order", recordId, "related_orders", 1, 20),
                    BusinessException.class);
            assertThat(failure.code()).isEqualTo("RECORD_NOT_FOUND");
        }
        for (var pageAndSize : List.of(new int[]{0, 20}, new int[]{1, 0}, new int[]{1, 101})) {
            var failure = catchThrowableOfType(
                    () -> facade.subtable(
                            session(101), "work_order", "9", "lines",
                            pageAndSize[0], pageAndSize[1]),
                    BusinessException.class);
            assertThat(failure.code()).isEqualTo("QUERY_INVALID");
        }
        var nullRelation = catchThrowableOfType(
                () -> facade.mutateRelation(
                        session(101), "work_order", "9", "related_orders", null, "key"),
                BusinessException.class);
        var nullSubtable = catchThrowableOfType(
                () -> facade.mutateSubtable(
                        session(101), "work_order", "9", "lines", null, "key"),
                BusinessException.class);
        var missingKey = catchThrowableOfType(
                () -> facade.mutateRelation(
                        session(101), "work_order", "9", "related_orders",
                        new OpenApiRecordFacade.RelationMutationCommand(
                                1L, List.of(), List.of(), List.of()), " "),
                BusinessException.class);

        assertThat(nullRelation.code()).isEqualTo("OPENAPI_RELATION_REQUEST_INVALID");
        assertThat(nullSubtable.code()).isEqualTo("OPENAPI_SUBTABLE_REQUEST_INVALID");
        assertThat(missingKey.code()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
        assertThat(runtime.compositionReadCalls).hasValue(0);
        assertThat(runtime.compositionMutationCalls).hasValue(0);
    }

    private static OpenApiRecordFacade.Session session(long applicationId) {
        return new OpenApiRecordFacade.Session(
                applicationId, 7, 11, 13, 17,
                Set.of("module.work_order.view", "module.work_order.create"),
                "request-1", "trace-1");
    }

    private static OpenApiRecordFacade.CreateCommand command(String fieldCode, String value) {
        return new OpenApiRecordFacade.CreateCommand(
                "ACTIVE", Map.of(fieldCode, JSON.getNodeFactory().textNode(value)));
    }

    private static RecordRuntimeViews.RecordSchema schema(RecordRuntimeViews.FieldCapability... fields) {
        return new RecordRuntimeViews.RecordSchema(
                "41", "31", "31", "checksum", "READY", null, 5,
                List.of(fields), List.of("CREATE"), new RecordRuntimeViews.QueryLimits(50, 200, 3));
    }

    private static RecordRuntimeViews.FieldCapability field(
            String code,
            String type,
            boolean writable,
            boolean masked
    ) {
        return new RecordRuntimeViews.FieldCapability(
                code, code, "1", type, writable ? "WRITABLE" : "READONLY", true, writable,
                false, false, masked, List.of(), false, true, true, List.of(),
                JSON.createObjectNode());
    }

    private static RecordRuntimeViews.CompositionCapabilities capabilities() {
        var relationRead = new RecordRuntimeViews.OperationCapability(
                "module.work_order.field.related_orders.read", true, null);
        var relationWrite = new RecordRuntimeViews.OperationCapability(
                "module.work_order.field.related_orders.write", true, null);
        var subtableRead = new RecordRuntimeViews.OperationCapability(
                "module.work_order.field.lines.read", true, null);
        var subtableWrite = new RecordRuntimeViews.OperationCapability(
                "module.work_order.field.lines.write", true, null);
        return new RecordRuntimeViews.CompositionCapabilities(
                relationRead, relationWrite, relationWrite, relationWrite,
                subtableRead, subtableWrite, subtableWrite, subtableWrite, subtableWrite);
    }

    private static OpenApiRecordFacade.CompositionCapabilitiesView capabilityView() {
        var relationRead = new OpenApiRecordFacade.OperationCapabilityView(
                "module.work_order.field.related_orders.read", true, null);
        var relationWrite = new OpenApiRecordFacade.OperationCapabilityView(
                "module.work_order.field.related_orders.write", true, null);
        var subtableRead = new OpenApiRecordFacade.OperationCapabilityView(
                "module.work_order.field.lines.read", true, null);
        var subtableWrite = new OpenApiRecordFacade.OperationCapabilityView(
                "module.work_order.field.lines.write", true, null);
        return new OpenApiRecordFacade.CompositionCapabilitiesView(
                relationRead, relationWrite, relationWrite, relationWrite,
                subtableRead, subtableWrite, subtableWrite, subtableWrite, subtableWrite);
    }

    private static final class FakeRuntime implements OpenApiRecordFacade.OpenApiRecordRuntime {
        private final RecordRuntimeViews.RecordSchema schema;
        private final AtomicInteger createCalls = new AtomicInteger();
        private final AtomicInteger persistedCreates = new AtomicInteger();
        private final AtomicInteger detailCalls = new AtomicInteger();
        private final AtomicInteger listCalls = new AtomicInteger();
        private final AtomicInteger compositionReadCalls = new AtomicInteger();
        private final AtomicInteger compositionMutationCalls = new AtomicInteger();
        private final Map<String, Replay> replays = new HashMap<>();
        private final List<Long> mutationApplications = new ArrayList<>();
        private final List<String> mutationKeys = new ArrayList<>();
        private RuntimeSession lastSession;
        private long lastApplicationId;
        private String lastLifecycle;
        private String lastRequestId;
        private String lastTraceId;
        private Map<String, JsonNode> lastValues = Map.of();
        private RecordRuntimeViews.RecordDetail detail = detail("1", "ACTIVE");
        private RecordRuntimeViews.RecordPage page = new RecordRuntimeViews.RecordPage(List.of(), 1, 20, 0);
        private BusinessException detailFailure;
        private long lastRecordId;
        private String lastCorrelationId;
        private RecordRuntimeViews.RelationMutationRequest lastRelationMutation;
        private RecordRuntimeViews.SubtableMutationRequest lastSubtableMutation;
        private RecordRuntimeViews.RelationPage relationPage = new RecordRuntimeViews.RelationPage(
                List.of(), 1, 20, 0, capabilities(), "trace-1");
        private RecordRuntimeViews.SubtablePage subtablePage = new RecordRuntimeViews.SubtablePage(
                List.of(), 1, 20, 0, capabilities(), "trace-1");

        private FakeRuntime(RecordRuntimeViews.RecordSchema schema) {
            this.schema = schema;
        }

        @Override
        public RecordRuntimeViews.RecordSchema schema(RuntimeSession session, String moduleCode) {
            lastSession = session;
            return schema;
        }

        @Override
        public RecordRuntimeViews.RecordDetail create(
                RuntimeSession session,
                String moduleCode,
                long applicationId,
                String lifecycleState,
                Map<String, JsonNode> values,
                String idempotencyKey,
                String requestId,
                String traceId
        ) {
            createCalls.incrementAndGet();
            lastSession = session;
            lastApplicationId = applicationId;
            lastLifecycle = lifecycleState;
            lastValues = Map.copyOf(values);
            lastRequestId = requestId;
            lastTraceId = traceId;
            var scope = session.systemId() + ":" + session.tenantId() + ":" + session.memberId() + ":"
                    + applicationId + ":" + moduleCode + ":" + idempotencyKey;
            var fingerprint = lifecycleState + ":" + new TreeMap<>(values);
            var existing = replays.get(scope);
            if (existing != null) {
                if (!existing.fingerprint().equals(fingerprint)) {
                    throw new BusinessException("IDEMPOTENCY_CONFLICT", "changed payload", HttpStatus.CONFLICT);
                }
                return existing.response();
            }
            var response = detail(Integer.toString(persistedCreates.incrementAndGet()), lifecycleState);
            replays.put(scope, new Replay(fingerprint, response));
            return response;
        }

        @Override
        public RecordRuntimeViews.RecordDetail update(
                RuntimeSession session,
                String moduleCode,
                long recordId,
                long applicationId,
                long expectedVersion,
                Map<String, JsonNode> values,
                String idempotencyKey,
                String requestId,
                String traceId
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RecordRuntimeViews.RecordDetail lifecycle(
                RuntimeSession session,
                String moduleCode,
                long recordId,
                long applicationId,
                String action,
                long expectedVersion,
                String idempotencyKey,
                String requestId,
                String traceId
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RecordRuntimeViews.RecordDetail detail(RuntimeSession session, String moduleCode, long recordId) {
            detailCalls.incrementAndGet();
            lastSession = session;
            if (detailFailure != null) {
                throw detailFailure;
            }
            return detail;
        }

        @Override
        public RecordRuntimeViews.RecordPage list(RuntimeSession session, String moduleCode, int page, int size) {
            listCalls.incrementAndGet();
            lastSession = session;
            return this.page;
        }

        @Override
        public RecordRuntimeViews.RelationPage relations(
                RuntimeSession session,
                String moduleCode,
                long recordId,
                String fieldCode,
                int page,
                int size,
                String correlationId
        ) {
            compositionReadCalls.incrementAndGet();
            lastSession = session;
            lastRecordId = recordId;
            lastCorrelationId = correlationId;
            return relationPage;
        }

        @Override
        public RecordRuntimeViews.SubtablePage subtable(
                RuntimeSession session,
                String moduleCode,
                long recordId,
                String fieldCode,
                int page,
                int size,
                String correlationId
        ) {
            compositionReadCalls.incrementAndGet();
            lastSession = session;
            lastRecordId = recordId;
            lastCorrelationId = correlationId;
            return subtablePage;
        }

        @Override
        public RecordRuntimeViews.RecordMutationResponse mutateRelation(
                RuntimeSession session,
                String moduleCode,
                long recordId,
                String fieldCode,
                long applicationId,
                RecordRuntimeViews.RelationMutationRequest request,
                String idempotencyKey,
                String requestId,
                String traceId
        ) {
            compositionMutationCalls.incrementAndGet();
            lastSession = session;
            lastRecordId = recordId;
            lastRelationMutation = request;
            mutationApplications.add(applicationId);
            mutationKeys.add(idempotencyKey);
            lastRequestId = requestId;
            lastTraceId = traceId;
            return mutationResponse();
        }

        @Override
        public RecordRuntimeViews.RecordMutationResponse mutateSubtable(
                RuntimeSession session,
                String moduleCode,
                long recordId,
                String fieldCode,
                long applicationId,
                RecordRuntimeViews.SubtableMutationRequest request,
                String idempotencyKey,
                String requestId,
                String traceId
        ) {
            compositionMutationCalls.incrementAndGet();
            lastSession = session;
            lastRecordId = recordId;
            lastSubtableMutation = request;
            mutationApplications.add(applicationId);
            mutationKeys.add(idempotencyKey);
            lastRequestId = requestId;
            lastTraceId = traceId;
            return mutationResponse();
        }

        private static RecordRuntimeViews.RecordMutationResponse mutationResponse() {
            return new RecordRuntimeViews.RecordMutationResponse(
                    "9", 5L, "41", "ACTIVE", "701", "trace-1");
        }

        private static RecordRuntimeViews.RecordDetail detail(String id, String status) {
            return new RecordRuntimeViews.RecordDetail(
                    id, "R-" + id, 0, status, "Alpha", "41", List.of(), List.of("UPDATE"));
        }

        private record Replay(String fingerprint, RecordRuntimeViews.RecordDetail response) { }
    }
}
