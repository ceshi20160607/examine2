package com.unique.examine.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.flow.api.FlowViews;
import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.security.FlowSession;
import com.unique.examine.openapi.domain.OpenApiApplication;
import com.unique.examine.openapi.domain.OpenApiCredential;
import com.unique.examine.openapi.security.OpenApiAuthentication;
import com.unique.examine.openapi.security.OpenApiMachineSession;
import org.junit.jupiter.api.Test;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiFlowStatusControllerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PATH = "/openapi/v1/flow/instances/91";
    private static final String REQUEST_ID = "request-73";
    private static final String TRACE_ID = "trace-73";

    @Test
    void returnsStableStatusAndForwardsBoundMachineSession() throws Exception {
        var operations = new CapturingOperations();

        mvc(operations).perform(authenticated(get(PATH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(jsonPath("$.data.instanceId").value("91"))
                .andExpect(jsonPath("$.data.definitionId").value("71"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.currentStageCode").value("stage-1"))
                .andExpect(jsonPath("$.data.recordBinding.moduleCode").value("orders"))
                .andExpect(jsonPath("$.data.recordBinding.recordId").value("42"))
                .andExpect(jsonPath("$.data.approverId").doesNotExist())
                .andExpect(jsonPath("$.data.completionExecutions").doesNotExist());

        assertThat(operations.calls).isOne();
        assertThat(operations.instanceId).isEqualTo(91);
        assertThat(operations.session).isEqualTo(new FlowSession(
                700, 20, 30, 40, 9,
                Set.of("system.runtime.access", "flow.instance.read", "module.orders.view")));
    }

    @Test
    void rejectsMissingOrMismatchedAuthenticationBeforeOwnerCall() throws Exception {
        var operations = new CapturingOperations();
        var mvc = mvc(operations);

        mvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OPENAPI_AUTH_REQUIRED"));

        var current = machine();
        var different = new OpenApiMachineSession(
                10, 700, 20L, 31L, 40L, 9,
                Set.of("system.runtime.access", "flow.instance.read"));
        mvc.perform(get(PATH)
                        .requestAttr(
                                OpenApiAuthentication.REQUEST_ATTRIBUTE,
                                authentication(current))
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, different))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OPENAPI_AUTH_REQUIRED"));

        assertThat(operations.calls).isZero();
    }

    @Test
    void requiresCurrentFlowReadPermissionBeforeOwnerCall() throws Exception {
        var operations = new CapturingOperations();
        var machine = new OpenApiMachineSession(
                10, 700, 20L, 30L, 40L, 9,
                Set.of("system.runtime.access"));

        mvc(operations).perform(get(PATH)
                        .requestAttr(
                                OpenApiAuthentication.REQUEST_ATTRIBUTE,
                                authentication(machine))
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, machine))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        assertThat(operations.calls).isZero();
    }

    @Test
    void translatesOwnerNotFoundWithoutLeakingTenantExistence() throws Exception {
        var mvc = mvc((session, instanceId) -> {
            throw new ApprovalDomainException(
                    ApprovalDomainException.Code.INSTANCE_NOT_FOUND,
                    "Approval instance was not found");
        });

        mvc.perform(authenticated(get(PATH)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FLOW_INSTANCE_NOT_FOUND"));
    }

    private static MockMvc mvc(OpenApiFlowStatusController.StatusOperations operations) {
        return MockMvcBuilders.standaloneSetup(
                        new OpenApiFlowStatusController(operations))
                .setControllerAdvice(new TestExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(JSON))
                .build();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            authenticated(
                    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
            ) {
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
                10, 20, 30, 40, "app-key", "Flow integration",
                OpenApiApplication.Status.ACTIVE,
                Set.of("flow.read"), List.of(), 100,
                3, now, 1, now, 1, 0);
        var credential = new OpenApiCredential(
                50, 10, 3, "secret-ref", OpenApiCredential.Status.ACTIVE,
                now, null, now, 1);
        return new OpenApiAuthentication(application, credential, machine);
    }

    private static OpenApiMachineSession machine() {
        return new OpenApiMachineSession(
                10, 700, 20L, 30L, 40L, 9,
                Set.of("system.runtime.access", "flow.instance.read", "module.orders.view"));
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
            implements OpenApiFlowStatusController.StatusOperations {
        private FlowSession session;
        private long instanceId;
        private int calls;

        @Override
        public FlowViews.OpenApiInstanceStatus status(
                FlowSession requestedSession,
                long requestedInstanceId
        ) {
            session = requestedSession;
            instanceId = requestedInstanceId;
            calls++;
            return new FlowViews.OpenApiInstanceStatus(
                    "91", "71", 3, "PO-42", "PENDING",
                    "2026-08-04T00:00:00Z", null,
                    0, 0, "stage-1", "SEQUENTIAL", "HUMAN_APPROVAL",
                    new FlowViews.RecordBinding("orders", "42"));
        }
    }
}
