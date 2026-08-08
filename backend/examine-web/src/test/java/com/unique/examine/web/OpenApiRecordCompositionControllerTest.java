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
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiRecordCompositionControllerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ROOT = "/openapi/v1/modules/orders/records/42";
    private static final String REQUEST_ID = "request-74";
    private static final String TRACE_ID = "trace-74";

    @Test
    void readsRelationAndSubtablePagesWithBoundContext() throws Exception {
        var operations = new CapturingOperations();
        operations.relationPage = relationPage();
        operations.subtablePage = subtablePage();
        var mvc = mvc(operations);

        mvc.perform(authenticated(get(ROOT + "/relations/customers?page=2&size=25")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(jsonPath("$.data.total").value(26))
                .andExpect(jsonPath("$.data.items[0].targetRecordId").value("91"));
        mvc.perform(authenticated(get(ROOT + "/subtables/lines?page=3&size=10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(21))
                .andExpect(jsonPath("$.data.items[0].rowId").value("101"));

        assertThat(operations.relationReads).isOne();
        assertThat(operations.subtableReads).isOne();
        assertThat(operations.moduleCode).isEqualTo("orders");
        assertThat(operations.recordId).isEqualTo("42");
        assertThat(operations.fieldCode).isEqualTo("lines");
        assertThat(operations.page).isEqualTo(3);
        assertThat(operations.size).isEqualTo(10);
        assertSession(operations.session);
    }

    @Test
    void mutatesRelationAndSubtableWithIdempotencyKeys() throws Exception {
        var operations = new CapturingOperations();
        operations.receipt = receipt();
        var mvc = mvc(operations);

        mvc.perform(authenticated(post(ROOT + "/relations/customers:mutate")
                        .header("Idempotency-Key", "relation-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":4,
                                 "add":[{"targetRecordId":"91","targetExpectedVersion":2,"ordinal":0}],
                                 "remove":[],"order":[]}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordId").value("42"))
                .andExpect(jsonPath("$.data.version").value(5));
        assertThat(operations.relationCommand.expectedVersion()).isEqualTo(4);
        assertThat(operations.relationCommand.add().getFirst().targetRecordId()).isEqualTo("91");
        assertThat(operations.idempotencyKey).isEqualTo("relation-key");

        mvc.perform(authenticated(post(ROOT + "/subtables/lines:mutate")
                        .header("Idempotency-Key", "subtable-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":5,
                                 "add":[{"clientRowKey":"line-1","ordinal":0,
                                          "values":{"amount":12.5}}],
                                 "update":[],"remove":[],"order":[]}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.historyId").value("701"));
        assertThat(operations.subtableCommand.expectedVersion()).isEqualTo(5);
        assertThat(operations.subtableCommand.add().getFirst().clientRowKey()).isEqualTo("line-1");
        assertThat(operations.idempotencyKey).isEqualTo("subtable-key");
        assertSession(operations.session);
    }

    @Test
    void rejectsMissingOrMismatchedMachineBinding() throws Exception {
        var operations = new CapturingOperations();
        var mvc = mvc(operations);

        mvc.perform(get(ROOT + "/relations/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OPENAPI_AUTH_REQUIRED"));

        var current = machine();
        var different = new OpenApiMachineSession(
                10, 700, 20L, 31L, 40L, 9,
                Set.of("system.runtime.access", "module.orders.view"));
        mvc.perform(get(ROOT + "/subtables/lines")
                        .requestAttr(OpenApiAuthentication.REQUEST_ATTRIBUTE, authentication(current))
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, different))
                .andExpect(status().isUnauthorized());

        assertThat(operations.totalCalls()).isZero();
    }

    @Test
    void preservesOwnerDomainErrors() throws Exception {
        var operations = new CapturingOperations();
        operations.failure = new BusinessException(
                "RECORD_NOT_FOUND",
                "Record does not exist or is outside the current data scope",
                HttpStatus.NOT_FOUND);

        mvc(operations).perform(authenticated(
                        get(ROOT + "/relations/customers?page=1&size=20")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECORD_NOT_FOUND"));
        assertThat(operations.relationReads).isOne();
    }

    private static MockMvc mvc(CapturingOperations operations) {
        return MockMvcBuilders.standaloneSetup(
                        new OpenApiRecordCompositionController(operations))
                .setControllerAdvice(new TestExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(JSON))
                .build();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            authenticated(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        var machine = machine();
        return request
                .requestAttr(OpenApiAuthentication.REQUEST_ATTRIBUTE, authentication(machine))
                .requestAttr(RequestSession.REQUEST_ATTRIBUTE, machine)
                .requestAttr(WebRequestAttributes.REQUEST_ID, REQUEST_ID)
                .requestAttr(WebRequestAttributes.TRACE_ID, TRACE_ID);
    }

    private static OpenApiAuthentication authentication(OpenApiMachineSession machine) {
        var now = Instant.parse("2026-08-04T00:00:00Z");
        var application = new OpenApiApplication(
                10, 20, 30, 40, "app-key", "Composition integration",
                OpenApiApplication.Status.ACTIVE,
                Set.of("record.write", "record.read"), List.of(), 100,
                3, now, 1, now, 1, 0);
        var credential = new OpenApiCredential(
                50, 10, 3, "secret-ref", OpenApiCredential.Status.ACTIVE,
                now, null, now, 1);
        return new OpenApiAuthentication(application, credential, machine);
    }

    private static OpenApiMachineSession machine() {
        return new OpenApiMachineSession(
                10, 700, 20L, 30L, 40L, 9,
                Set.of("system.runtime.access", "module.orders.view", "module.orders.update"));
    }

    private static OpenApiRecordFacade.RelationPageView relationPage() {
        return new OpenApiRecordFacade.RelationPageView(
                List.of(new OpenApiRecordFacade.RelationItemView(
                        "91", 2, 0, "Acme")),
                2, 25, 26, capabilities(), TRACE_ID);
    }

    private static OpenApiRecordFacade.SubtablePageView subtablePage() {
        return new OpenApiRecordFacade.SubtablePageView(
                List.of(new OpenApiRecordFacade.SubtableRowView(
                        "101", 1, 0,
                        List.of(new OpenApiRecordFacade.FieldValueView(
                                "amount", "Amount", "NUMBER", 12.5, "12.5", null)))),
                3, 10, 21, capabilities(), TRACE_ID);
    }

    private static OpenApiRecordFacade.CompositionCapabilitiesView capabilities() {
        var yes = new OpenApiRecordFacade.OperationCapabilityView(
                "module.orders.update", true, null);
        var no = new OpenApiRecordFacade.OperationCapabilityView(
                "module.orders.update", false, "NOT_APPLICABLE");
        return new OpenApiRecordFacade.CompositionCapabilitiesView(
                yes, yes, yes, yes, yes, yes, yes, yes, no);
    }

    private static OpenApiRecordFacade.MutationReceipt receipt() {
        return new OpenApiRecordFacade.MutationReceipt(
                "42", 5, "301", "ACTIVE", "701", TRACE_ID);
    }

    private static void assertSession(OpenApiRecordFacade.Session session) {
        assertThat(session.applicationId()).isEqualTo(10);
        assertThat(session.accountId()).isEqualTo(700);
        assertThat(session.systemId()).isEqualTo(20);
        assertThat(session.tenantId()).isEqualTo(30);
        assertThat(session.serviceMemberId()).isEqualTo(40);
        assertThat(session.permissions()).contains(
                "system.runtime.access", "module.orders.update");
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
            implements OpenApiRecordCompositionController.CompositionOperations {
        private OpenApiRecordFacade.RelationPageView relationPage;
        private OpenApiRecordFacade.SubtablePageView subtablePage;
        private OpenApiRecordFacade.MutationReceipt receipt;
        private BusinessException failure;
        private OpenApiRecordFacade.Session session;
        private OpenApiRecordFacade.RelationMutationCommand relationCommand;
        private OpenApiRecordFacade.SubtableMutationCommand subtableCommand;
        private String moduleCode;
        private String recordId;
        private String fieldCode;
        private String idempotencyKey;
        private int page;
        private int size;
        private int relationReads;
        private int subtableReads;
        private int relationMutations;
        private int subtableMutations;

        @Override
        public OpenApiRecordFacade.RelationPageView relations(
                OpenApiRecordFacade.Session requestedSession,
                String requestedModuleCode,
                String requestedRecordId,
                String requestedFieldCode,
                int requestedPage,
                int requestedSize) {
            capture(requestedSession, requestedModuleCode, requestedRecordId, requestedFieldCode);
            page = requestedPage;
            size = requestedSize;
            relationReads++;
            failIfRequested();
            return relationPage;
        }

        @Override
        public OpenApiRecordFacade.SubtablePageView subtable(
                OpenApiRecordFacade.Session requestedSession,
                String requestedModuleCode,
                String requestedRecordId,
                String requestedFieldCode,
                int requestedPage,
                int requestedSize) {
            capture(requestedSession, requestedModuleCode, requestedRecordId, requestedFieldCode);
            page = requestedPage;
            size = requestedSize;
            subtableReads++;
            failIfRequested();
            return subtablePage;
        }

        @Override
        public OpenApiRecordFacade.MutationReceipt mutateRelation(
                OpenApiRecordFacade.Session requestedSession,
                String requestedModuleCode,
                String requestedRecordId,
                String requestedFieldCode,
                OpenApiRecordFacade.RelationMutationCommand requestedCommand,
                String requestedIdempotencyKey) {
            capture(requestedSession, requestedModuleCode, requestedRecordId, requestedFieldCode);
            relationCommand = requestedCommand;
            idempotencyKey = requestedIdempotencyKey;
            relationMutations++;
            failIfRequested();
            return receipt;
        }

        @Override
        public OpenApiRecordFacade.MutationReceipt mutateSubtable(
                OpenApiRecordFacade.Session requestedSession,
                String requestedModuleCode,
                String requestedRecordId,
                String requestedFieldCode,
                OpenApiRecordFacade.SubtableMutationCommand requestedCommand,
                String requestedIdempotencyKey) {
            capture(requestedSession, requestedModuleCode, requestedRecordId, requestedFieldCode);
            subtableCommand = requestedCommand;
            idempotencyKey = requestedIdempotencyKey;
            subtableMutations++;
            failIfRequested();
            return receipt;
        }

        private void capture(
                OpenApiRecordFacade.Session requestedSession,
                String requestedModuleCode,
                String requestedRecordId,
                String requestedFieldCode) {
            session = requestedSession;
            moduleCode = requestedModuleCode;
            recordId = requestedRecordId;
            fieldCode = requestedFieldCode;
        }

        private void failIfRequested() {
            if (failure != null) {
                throw failure;
            }
        }

        private int totalCalls() {
            return relationReads + subtableReads + relationMutations + subtableMutations;
        }
    }
}
