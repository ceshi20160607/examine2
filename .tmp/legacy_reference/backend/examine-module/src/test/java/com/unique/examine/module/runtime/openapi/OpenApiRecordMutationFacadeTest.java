package com.unique.examine.module.runtime.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class OpenApiRecordMutationFacadeTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void updateDelegatesOnlyExpectedVersionAndWritableValues() {
        var runtime = new MutationRuntime(schema(field("name", "TEXT", true)));
        var facade = new OpenApiRecordFacade(runtime);
        var result = facade.update(
                session(101), "work_order", "23",
                new OpenApiRecordFacade.UpdateCommand(
                        4, Map.of("name", JSON.getNodeFactory().textNode("Beta"))),
                "update-key");

        assertThat(result.recordId()).isEqualTo("23");
        assertThat(result.version()).isEqualTo(5);
        assertThat(runtime.lastOperation).isEqualTo("update");
        assertThat(runtime.lastRecordId).isEqualTo(23);
        assertThat(runtime.lastApplicationId).isEqualTo(101);
        assertThat(runtime.lastExpectedVersion).isEqualTo(4);
        assertThat(runtime.lastValues).containsOnlyKeys("name");
        assertThat(runtime.lastIdempotencyKey).isEqualTo("update-key");
        assertAuthoritativeSession(runtime.lastSession);
    }

    @Test
    void updateRejectsReservedUnknownAndCurrentlyNonWritableValues() {
        var runtime = new MutationRuntime(schema(
                field("approval", "TEXT", false),
                field("tenant_code", "TENANT", false)));
        var facade = new OpenApiRecordFacade(runtime);

        var exception = catchThrowableOfType(
                () -> facade.update(
                        session(101), "work_order", "23",
                        new OpenApiRecordFacade.UpdateCommand(4, Map.of(
                                "ownerMemberId", JSON.getNodeFactory().textNode("99"),
                                "tenant_code", JSON.getNodeFactory().textNode("13"),
                                "approval", JSON.getNodeFactory().textNode("yes"),
                                "retired", JSON.getNodeFactory().textNode("x"))),
                        "update-key"),
                BusinessException.class);

        assertThat(exception.code()).isEqualTo("OPENAPI_RECORD_VALUES_INVALID");
        assertThat(exception.errors()).extracting(error -> error.code())
                .containsExactlyInAnyOrder(
                        "SYSTEM_FIELD_CLIENT_VALUE_FORBIDDEN",
                        "SYSTEM_FIELD_CLIENT_VALUE_FORBIDDEN",
                        "OPENAPI_FIELD_WRITE_FORBIDDEN",
                        "OPENAPI_FIELD_UNKNOWN");
        assertThat(runtime.mutationCalls).hasValue(0);
    }

    @Test
    void lifecycleMethodsDelegateTheirExactExistingRuntimeActions() {
        var runtime = new MutationRuntime(schema());
        var facade = new OpenApiRecordFacade(runtime);
        var command = new OpenApiRecordFacade.VersionCommand(7);

        assertThat(facade.activate(session(101), "work_order", "23", command, "k1").status())
                .isEqualTo("ACTIVE");
        assertThat(runtime.lastOperation).isEqualTo("activate");
        assertThat(facade.archive(session(101), "work_order", "23", command, "k2").status())
                .isEqualTo("ARCHIVED");
        assertThat(runtime.lastOperation).isEqualTo("archive");
        assertThat(facade.unarchive(session(101), "work_order", "23", command, "k3").status())
                .isEqualTo("ACTIVE");
        assertThat(runtime.lastOperation).isEqualTo("unarchive");
        assertThat(facade.trash(session(101), "work_order", "23", command, "k4").status())
                .isEqualTo("TRASHED");
        assertThat(runtime.lastOperation).isEqualTo("trash");
        assertThat(facade.restoreFromTrash(session(101), "work_order", "23", command, "k5").status())
                .isEqualTo("ACTIVE");
        assertThat(runtime.lastOperation).isEqualTo("restore-from-trash");
        assertThat(runtime.lastExpectedVersion).isEqualTo(7);
        assertThat(runtime.lastApplicationId).isEqualTo(101);
        assertThat(runtime.mutationCalls).hasValue(5);
        assertAuthoritativeSession(runtime.lastSession);
    }

    @Test
    void exactReplayReturnsOriginalChangedPayloadOrActionConflictsAndApplicationsAreIsolated() {
        var runtime = new MutationRuntime(schema(field("name", "TEXT", true)));
        var facade = new OpenApiRecordFacade(runtime);
        var alpha = new OpenApiRecordFacade.UpdateCommand(
                4, Map.of("name", JSON.getNodeFactory().textNode("Alpha")));
        var beta = new OpenApiRecordFacade.UpdateCommand(
                4, Map.of("name", JSON.getNodeFactory().textNode("Beta")));

        var first = facade.update(session(101), "work_order", "23", alpha, "stable-key");
        var replay = facade.update(session(101), "work_order", "23", alpha, "stable-key");
        var payloadConflict = catchThrowableOfType(
                () -> facade.update(session(101), "work_order", "23", beta, "stable-key"),
                BusinessException.class);
        var actionConflict = catchThrowableOfType(
                () -> facade.archive(session(101), "work_order", "23",
                        new OpenApiRecordFacade.VersionCommand(4), "stable-key"),
                BusinessException.class);
        var otherApplication = facade.archive(session(102), "work_order", "23",
                new OpenApiRecordFacade.VersionCommand(4), "stable-key");

        assertThat(replay).isEqualTo(first);
        assertThat(payloadConflict.code()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(actionConflict.code()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(otherApplication.status()).isEqualTo("ARCHIVED");
        assertThat(runtime.persistedMutations).hasValue(2);
    }

    @Test
    void staleVersionAndOutOfScopeRecordFailuresRemainOwnedByRuntime() {
        var runtime = new MutationRuntime(schema(field("name", "TEXT", true)));
        var facade = new OpenApiRecordFacade(runtime);
        runtime.failure = new BusinessException(
                "RECORD_VERSION_CONFLICT", "stale", HttpStatus.CONFLICT);

        var stale = catchThrowableOfType(
                () -> facade.update(session(101), "work_order", "23",
                        new OpenApiRecordFacade.UpdateCommand(2, Map.of()), "update-key"),
                BusinessException.class);
        assertThat(stale.code()).isEqualTo("RECORD_VERSION_CONFLICT");

        runtime.failure = new BusinessException(
                "RECORD_NOT_FOUND", "hidden by current scope", HttpStatus.NOT_FOUND);
        var hidden = catchThrowableOfType(
                () -> facade.trash(session(101), "work_order", "23",
                        new OpenApiRecordFacade.VersionCommand(3), "trash-key"),
                BusinessException.class);
        assertThat(hidden.code()).isEqualTo("RECORD_NOT_FOUND");
    }

    private static void assertAuthoritativeSession(RuntimeSession session) {
        assertThat(session.accountId()).isEqualTo(7);
        assertThat(session.systemId()).isEqualTo(11);
        assertThat(session.tenantId()).isEqualTo(13);
        assertThat(session.memberId()).isEqualTo(17);
        assertThat(session.permissions()).containsExactlyInAnyOrder(
                "module.work_order.view", "module.work_order.create", "module.work_order.update",
                "module.work_order.action.archive", "module.work_order.action.unarchive",
                "module.work_order.delete", "module.work_order.action.restore_trash");
    }

    private static OpenApiRecordFacade.Session session(long applicationId) {
        return new OpenApiRecordFacade.Session(
                applicationId, 7, 11, 13, 17,
                Set.of(
                        "module.work_order.view", "module.work_order.create", "module.work_order.update",
                        "module.work_order.action.archive", "module.work_order.action.unarchive",
                        "module.work_order.delete", "module.work_order.action.restore_trash"),
                "request-71", "trace-71");
    }

    private static RecordRuntimeViews.RecordSchema schema(RecordRuntimeViews.FieldCapability... fields) {
        return new RecordRuntimeViews.RecordSchema(
                "41", "31", "31", "checksum", "READY", null, 5,
                List.of(fields), List.of("CREATE"), new RecordRuntimeViews.QueryLimits(50, 200, 3));
    }

    private static RecordRuntimeViews.FieldCapability field(String code, String type, boolean writable) {
        return new RecordRuntimeViews.FieldCapability(
                code, code, "1", type, writable ? "WRITABLE" : "READONLY", true, writable,
                false, false, false, List.of(), false, true, true, List.of(), JSON.createObjectNode());
    }

    private static final class MutationRuntime implements OpenApiRecordFacade.OpenApiRecordRuntime {
        private final RecordRuntimeViews.RecordSchema schema;
        private final Map<String, Replay> replays = new HashMap<>();
        private final AtomicInteger mutationCalls = new AtomicInteger();
        private final AtomicInteger persistedMutations = new AtomicInteger();
        private RuntimeSession lastSession;
        private long lastApplicationId;
        private long lastRecordId;
        private long lastExpectedVersion;
        private String lastOperation;
        private String lastIdempotencyKey;
        private Map<String, JsonNode> lastValues = Map.of();
        private BusinessException failure;

        private MutationRuntime(RecordRuntimeViews.RecordSchema schema) {
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
            throw new UnsupportedOperationException();
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
            return mutate(session, moduleCode, recordId, applicationId, "update", expectedVersion, values,
                    idempotencyKey);
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
            return mutate(session, moduleCode, recordId, applicationId, action, expectedVersion, Map.of(),
                    idempotencyKey);
        }

        private RecordRuntimeViews.RecordDetail mutate(
                RuntimeSession session,
                String moduleCode,
                long recordId,
                long applicationId,
                String operation,
                long expectedVersion,
                Map<String, JsonNode> values,
                String idempotencyKey
        ) {
            mutationCalls.incrementAndGet();
            lastSession = session;
            lastApplicationId = applicationId;
            lastRecordId = recordId;
            lastExpectedVersion = expectedVersion;
            lastOperation = operation;
            lastIdempotencyKey = idempotencyKey;
            lastValues = Map.copyOf(values);
            if (failure != null) {
                throw failure;
            }
            var scope = session.systemId() + ":" + session.tenantId() + ":" + session.memberId() + ":"
                    + applicationId + ":" + moduleCode + ":" + recordId + ":" + idempotencyKey;
            var fingerprint = operation + ":" + expectedVersion + ":" + new TreeMap<>(values);
            var existing = replays.get(scope);
            if (existing != null) {
                if (!existing.fingerprint().equals(fingerprint)) {
                    throw new BusinessException("IDEMPOTENCY_CONFLICT", "changed mutation", HttpStatus.CONFLICT);
                }
                return existing.response();
            }
            persistedMutations.incrementAndGet();
            var status = switch (operation) {
                case "archive" -> "ARCHIVED";
                case "trash" -> "TRASHED";
                default -> "ACTIVE";
            };
            var response = new RecordRuntimeViews.RecordDetail(
                    Long.toString(recordId), "R-" + recordId, expectedVersion + 1, status,
                    "Record", "41", List.of(), List.of());
            replays.put(scope, new Replay(fingerprint, response));
            return response;
        }

        @Override
        public RecordRuntimeViews.RecordDetail detail(RuntimeSession session, String moduleCode, long recordId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RecordRuntimeViews.RecordPage list(RuntimeSession session, String moduleCode, int page, int size) {
            throw new UnsupportedOperationException();
        }

        private record Replay(String fingerprint, RecordRuntimeViews.RecordDetail response) { }
    }
}
