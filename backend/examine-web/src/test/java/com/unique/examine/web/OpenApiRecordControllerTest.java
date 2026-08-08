package com.unique.examine.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.openapi.OpenApiRecordFacade;
import com.unique.examine.openapi.domain.OpenApiApplication;
import com.unique.examine.openapi.domain.OpenApiCredential;
import com.unique.examine.openapi.security.OpenApiAuthentication;
import com.unique.examine.openapi.security.OpenApiMachineSession;
import com.unique.examine.openapi.service.OpenApiCallbackPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiRecordControllerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ROOT = "/openapi/v1/modules/orders/records";
    private static final String REQUEST_ID = "request-70";
    private static final String TRACE_ID = "trace-70";

    @Test
    void createsRecordAndForwardsBoundMachineContext() throws Exception {
        var operations = new CapturingOperations();
        operations.record = record("42", "ACTIVE");
        var callbackEvents = new ArrayList<OpenApiCallbackPublisher.Event>();
        OpenApiCallbackPublisher publisher = event -> {
            callbackEvents.add(event);
            return new OpenApiCallbackPublisher.Publication(1, 1, 0);
        };

        mvc(operations, publisher).perform(post(ROOT)
                        .requestAttr(OpenApiAuthentication.REQUEST_ATTRIBUTE,
                                authentication(machine()))
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, machine())
                        .requestAttr(WebRequestAttributes.REQUEST_ID, REQUEST_ID)
                        .requestAttr(WebRequestAttributes.TRACE_ID, TRACE_ID)
                        .header("Idempotency-Key", "idem-42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lifecycleState":"ACTIVE",
                                 "values":{"customer_name":"Acme"}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(jsonPath("$.data.recordId").value("42"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.values[0].value").value("Acme"));

        assertThat(operations.createCalls).isEqualTo(1);
        assertThat(operations.moduleCode).isEqualTo("orders");
        assertThat(operations.idempotencyKey).isEqualTo("idem-42");
        assertThat(operations.command.lifecycleState()).isEqualTo("ACTIVE");
        assertThat(operations.command.values().get("customer_name").asText())
                .isEqualTo("Acme");
        assertSession(operations.session);
        assertThat(callbackEvents).singleElement().satisfies(event -> {
            assertThat(event.systemId()).isEqualTo(20);
            assertThat(event.tenantId()).isEqualTo(30);
            assertThat(event.applicationId()).isEqualTo(10);
            assertThat(event.serviceMemberId()).isEqualTo(40);
            assertThat(event.eventId()).isEqualTo(
                    OpenApiCallbackPublisher.deterministicEventId(
                            "RECORD_CREATED", "10", "orders", "42", "3"));
            assertThat(event.eventId()).matches("RECORD_CREATED:[0-9a-f]{64}");
            assertThat(event.eventType()).isEqualTo("RECORD_CREATED");
            assertThat(event.resourceType()).isEqualTo("RECORD");
            assertThat(event.resourceId()).isEqualTo("42");
            assertThat(event.attributes()).containsEntry("moduleCode", "orders")
                    .containsEntry("status", "ACTIVE")
                    .containsEntry("version", "3");
            assertThat(event.requestId()).isEqualTo(REQUEST_ID);
            assertThat(event.traceId()).isEqualTo(TRACE_ID);
        });
    }

    @Test
    void listsAndReadsRecordsWithRequestCorrelation() throws Exception {
        var operations = new CapturingOperations();
        operations.page = new OpenApiRecordFacade.PageView(
                List.of(summary("43")), 2, 50, 101);
        operations.record = record("43", "DRAFT");
        var mvc = mvc(operations);

        mvc.perform(authenticated(get(ROOT + "?page=2&size=50")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(50))
                .andExpect(jsonPath("$.data.total").value(101))
                .andExpect(jsonPath("$.data.rows[0].recordId").value("43"));

        assertThat(operations.listCalls).isEqualTo(1);
        assertThat(operations.pageNumber).isEqualTo(2);
        assertThat(operations.pageSize).isEqualTo(50);
        assertSession(operations.session);

        mvc.perform(authenticated(get(ROOT + "/43")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordId").value("43"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        assertThat(operations.detailCalls).isEqualTo(1);
        assertThat(operations.moduleCode).isEqualTo("orders");
        assertThat(operations.recordId).isEqualTo("43");
        assertSession(operations.session);
    }

    @Test
    void updatesRecordAndForwardsOptimisticCommand() throws Exception {
        var operations = new CapturingOperations();
        operations.record = record("43", "ACTIVE");

        mvc(operations).perform(authenticated(put(ROOT + "/43")
                        .header("Idempotency-Key", "update-43")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":3,
                                 "values":{"customer_name":"Updated"}}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(jsonPath("$.data.recordId").value("43"));

        assertThat(operations.updateCalls).isOne();
        assertThat(operations.recordId).isEqualTo("43");
        assertThat(operations.idempotencyKey).isEqualTo("update-43");
        assertThat(operations.updateCommand.expectedVersion()).isEqualTo(3);
        assertThat(operations.updateCommand.values().get("customer_name").asText())
                .isEqualTo("Updated");
        assertSession(operations.session);
    }

    @Test
    void forwardsEveryLifecycleActionWithVersionAndIdempotency() throws Exception {
        var operations = new CapturingOperations();
        operations.record = record("43", "ACTIVE");
        var mvc = mvc(operations);
        var actions = List.of(
                "activate", "archive", "unarchive", "trash", "restore-from-trash");

        for (var action : actions) {
            mvc.perform(authenticated(post(ROOT + "/43:" + action)
                            .header("Idempotency-Key", "life-" + action)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedVersion\":3}")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.recordId").value("43"));
        }

        assertThat(operations.lifecycleActions).containsExactlyElementsOf(actions);
        assertThat(operations.versionCommand.expectedVersion()).isEqualTo(3);
        assertThat(operations.idempotencyKey).isEqualTo("life-restore-from-trash");
        assertSession(operations.session);
    }

    @Test
    void rejectsMissingOrUnboundAuthenticationBeforeFacade() throws Exception {
        var operations = new CapturingOperations();
        var mvc = mvc(operations);

        mvc.perform(get(ROOT)
                        .requestAttr(WebRequestAttributes.REQUEST_ID, REQUEST_ID)
                        .requestAttr(WebRequestAttributes.TRACE_ID, TRACE_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OPENAPI_AUTH_REQUIRED"));

        var authenticatedMachine = machine();
        var differentMachine = new OpenApiMachineSession(
                10, 700, 20L, 30L, 40L, 9,
                Set.of("system.runtime.access"));
        mvc.perform(get(ROOT)
                        .requestAttr(OpenApiAuthentication.REQUEST_ATTRIBUTE,
                                authentication(authenticatedMachine))
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                differentMachine))
                .andExpect(status().isUnauthorized());

        var tenantMismatch = authentication(machine(), 31, 40);
        mvc.perform(get(ROOT)
                        .requestAttr(OpenApiAuthentication.REQUEST_ATTRIBUTE,
                                tenantMismatch)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, machine()))
                .andExpect(status().isUnauthorized());

        var memberMismatch = authentication(machine(), 30, 41);
        mvc.perform(get(ROOT)
                        .requestAttr(OpenApiAuthentication.REQUEST_ATTRIBUTE,
                                memberMismatch)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, machine()))
                .andExpect(status().isUnauthorized());

        assertThat(operations.totalCalls()).isZero();
    }

    @Test
    void enforcesFacadePageBoundsBeforeListing() throws Exception {
        var operations = new CapturingOperations();
        var mvc = mvc(operations);

        mvc.perform(authenticated(get(ROOT + "?page=0&size=20")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("QUERY_INVALID"));
        mvc.perform(authenticated(get(ROOT + "?page=1000001&size=20")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(authenticated(get(ROOT + "?page=1&size=0")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(authenticated(get(ROOT + "?page=1&size=201")))
                .andExpect(status().isUnprocessableEntity());

        assertThat(operations.totalCalls()).isZero();
    }

    private static MockMvc mvc(CapturingOperations operations) {
        return mvc(operations, OpenApiCallbackPublisher.noop());
    }

    private static MockMvc mvc(CapturingOperations operations,
                               OpenApiCallbackPublisher publisher) {
        return MockMvcBuilders.standaloneSetup(
                        new OpenApiRecordController(operations, publisher))
                .setControllerAdvice(new TestExceptionAdvice())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(JSON))
                .build();
    }

    private static org.springframework.test.web.servlet.request
            .MockHttpServletRequestBuilder authenticated(
                    org.springframework.test.web.servlet.request
                            .MockHttpServletRequestBuilder request) {
        var machine = machine();
        return request
                .requestAttr(OpenApiAuthentication.REQUEST_ATTRIBUTE,
                        authentication(machine))
                .requestAttr(RequestSession.REQUEST_ATTRIBUTE, machine)
                .requestAttr(WebRequestAttributes.REQUEST_ID, REQUEST_ID)
                .requestAttr(WebRequestAttributes.TRACE_ID, TRACE_ID);
    }

    private static OpenApiAuthentication authentication(
            OpenApiMachineSession machine) {
        return authentication(machine, 30, 40);
    }

    private static OpenApiAuthentication authentication(
            OpenApiMachineSession machine,
            long applicationTenantId,
            long serviceMemberId) {
        var now = Instant.parse("2026-08-04T00:00:00Z");
        var application = new OpenApiApplication(
                10, 20, applicationTenantId, serviceMemberId,
                "app-key", "Record integration", OpenApiApplication.Status.ACTIVE,
                Set.of("record.read", "record.write"), List.of(), 100,
                3, now, 1, now, 1, 0);
        var credential = new OpenApiCredential(
                50, 10, 3, "secret-ref", OpenApiCredential.Status.ACTIVE,
                now, null, now, 1);
        return new OpenApiAuthentication(application, credential, machine);
    }

    private static OpenApiMachineSession machine() {
        return new OpenApiMachineSession(
                10, 700, 20L, 30L, 40L, 9,
                Set.of("system.runtime.access", "module.orders.view"));
    }

    private static OpenApiRecordFacade.RecordView record(
            String recordId, String status) {
        return new OpenApiRecordFacade.RecordView(
                recordId, "ORD-" + recordId, 3, status,
                "Acme", "schema-9",
                List.of(new OpenApiRecordFacade.FieldValueView(
                        "customer_name", "Customer", "TEXT",
                        "Acme", "Acme", null)));
    }

    private static OpenApiRecordFacade.SummaryView summary(String recordId) {
        return new OpenApiRecordFacade.SummaryView(
                recordId, "ORD-" + recordId, 3, "ACTIVE", "Acme",
                List.of(new OpenApiRecordFacade.FieldValueView(
                        "customer_name", "Customer", "TEXT",
                        "Acme", "Acme", null)));
    }

    private static void assertSession(OpenApiRecordFacade.Session session) {
        assertThat(session.applicationId()).isEqualTo(10);
        assertThat(session.accountId()).isEqualTo(700);
        assertThat(session.systemId()).isEqualTo(20);
        assertThat(session.tenantId()).isEqualTo(30);
        assertThat(session.serviceMemberId()).isEqualTo(40);
        assertThat(session.permissions()).containsExactlyInAnyOrder(
                "system.runtime.access", "module.orders.view");
        assertThat(session.requestId()).isEqualTo(REQUEST_ID);
        assertThat(session.traceId()).isEqualTo(TRACE_ID);
    }

    @RestControllerAdvice
    private static final class TestExceptionAdvice {
        @ExceptionHandler(BusinessException.class)
        ResponseEntity<Map<String, Object>> business(BusinessException error) {
            return ResponseEntity.status(error.status()).body(Map.of(
                    "code", error.code(), "message", error.getMessage()));
        }
    }

    private static final class CapturingOperations
            implements OpenApiRecordController.RecordOperations {
        private OpenApiRecordFacade.RecordView record;
        private OpenApiRecordFacade.PageView page;
        private OpenApiRecordFacade.Session session;
        private OpenApiRecordFacade.CreateCommand command;
        private OpenApiRecordFacade.UpdateCommand updateCommand;
        private OpenApiRecordFacade.VersionCommand versionCommand;
        private String moduleCode;
        private String recordId;
        private String idempotencyKey;
        private int pageNumber;
        private int pageSize;
        private int createCalls;
        private int detailCalls;
        private int listCalls;
        private int updateCalls;
        private final java.util.ArrayList<String> lifecycleActions = new java.util.ArrayList<>();

        @Override
        public OpenApiRecordFacade.RecordView create(
                OpenApiRecordFacade.Session requestedSession,
                String requestedModuleCode,
                OpenApiRecordFacade.CreateCommand requestedCommand,
                String requestedIdempotencyKey) {
            session = requestedSession;
            moduleCode = requestedModuleCode;
            command = requestedCommand;
            idempotencyKey = requestedIdempotencyKey;
            createCalls++;
            return record;
        }

        @Override
        public OpenApiRecordFacade.RecordView detail(
                OpenApiRecordFacade.Session requestedSession,
                String requestedModuleCode,
                String requestedRecordId) {
            session = requestedSession;
            moduleCode = requestedModuleCode;
            recordId = requestedRecordId;
            detailCalls++;
            return record;
        }

        @Override
        public OpenApiRecordFacade.PageView list(
                OpenApiRecordFacade.Session requestedSession,
                String requestedModuleCode,
                int requestedPage,
                int requestedSize) {
            session = requestedSession;
            moduleCode = requestedModuleCode;
            pageNumber = requestedPage;
            pageSize = requestedSize;
            listCalls++;
            return page;
        }

        @Override
        public OpenApiRecordFacade.RecordView update(
                OpenApiRecordFacade.Session requestedSession,
                String requestedModuleCode,
                String requestedRecordId,
                OpenApiRecordFacade.UpdateCommand requestedCommand,
                String requestedIdempotencyKey) {
            captureMutation(requestedSession, requestedModuleCode, requestedRecordId,
                    requestedIdempotencyKey);
            updateCommand = requestedCommand;
            updateCalls++;
            return record;
        }

        @Override
        public OpenApiRecordFacade.RecordView activate(
                OpenApiRecordFacade.Session requestedSession, String requestedModuleCode,
                String requestedRecordId, OpenApiRecordFacade.VersionCommand requestedCommand,
                String requestedIdempotencyKey) {
            return lifecycle("activate", requestedSession, requestedModuleCode, requestedRecordId,
                    requestedCommand, requestedIdempotencyKey);
        }

        @Override
        public OpenApiRecordFacade.RecordView archive(
                OpenApiRecordFacade.Session requestedSession, String requestedModuleCode,
                String requestedRecordId, OpenApiRecordFacade.VersionCommand requestedCommand,
                String requestedIdempotencyKey) {
            return lifecycle("archive", requestedSession, requestedModuleCode, requestedRecordId,
                    requestedCommand, requestedIdempotencyKey);
        }

        @Override
        public OpenApiRecordFacade.RecordView unarchive(
                OpenApiRecordFacade.Session requestedSession, String requestedModuleCode,
                String requestedRecordId, OpenApiRecordFacade.VersionCommand requestedCommand,
                String requestedIdempotencyKey) {
            return lifecycle("unarchive", requestedSession, requestedModuleCode, requestedRecordId,
                    requestedCommand, requestedIdempotencyKey);
        }

        @Override
        public OpenApiRecordFacade.RecordView trash(
                OpenApiRecordFacade.Session requestedSession, String requestedModuleCode,
                String requestedRecordId, OpenApiRecordFacade.VersionCommand requestedCommand,
                String requestedIdempotencyKey) {
            return lifecycle("trash", requestedSession, requestedModuleCode, requestedRecordId,
                    requestedCommand, requestedIdempotencyKey);
        }

        @Override
        public OpenApiRecordFacade.RecordView restoreFromTrash(
                OpenApiRecordFacade.Session requestedSession, String requestedModuleCode,
                String requestedRecordId, OpenApiRecordFacade.VersionCommand requestedCommand,
                String requestedIdempotencyKey) {
            return lifecycle("restore-from-trash", requestedSession, requestedModuleCode, requestedRecordId,
                    requestedCommand, requestedIdempotencyKey);
        }

        private OpenApiRecordFacade.RecordView lifecycle(
                String action,
                OpenApiRecordFacade.Session requestedSession,
                String requestedModuleCode,
                String requestedRecordId,
                OpenApiRecordFacade.VersionCommand requestedCommand,
                String requestedIdempotencyKey) {
            captureMutation(requestedSession, requestedModuleCode, requestedRecordId,
                    requestedIdempotencyKey);
            versionCommand = requestedCommand;
            lifecycleActions.add(action);
            return record;
        }

        private void captureMutation(
                OpenApiRecordFacade.Session requestedSession,
                String requestedModuleCode,
                String requestedRecordId,
                String requestedIdempotencyKey) {
            session = requestedSession;
            moduleCode = requestedModuleCode;
            recordId = requestedRecordId;
            idempotencyKey = requestedIdempotencyKey;
        }

        private int totalCalls() {
            return createCalls + detailCalls + listCalls + updateCalls + lifecycleActions.size();
        }
    }
}
