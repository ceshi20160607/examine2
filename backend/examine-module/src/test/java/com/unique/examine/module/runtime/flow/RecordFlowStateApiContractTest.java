package com.unique.examine.module.runtime.flow;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.context.RequestSession;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;

class RecordFlowStateApiContractTest {

    @Test
    void exposesTheFrozenRecordFlowStateRoute() throws Exception {
        var root = RecordFlowStateController.class.getAnnotation(RequestMapping.class);
        var method = RecordFlowStateController.class.getMethod(
                "find",
                long.class,
                String.class,
                long.class,
                Object.class,
                HttpServletRequest.class);

        assertThat(root.value()).containsExactly(
                "/api/v1/systems/{systemId}/runtime/modules/{moduleCode}/records/{recordId}/flow-state");
        assertThat(method.getAnnotation(GetMapping.class)).isNotNull();
        assertThat(method.getParameterAnnotations()[3])
                .anySatisfy(annotation -> assertThat(annotation.toString())
                        .contains(RequestSession.REQUEST_ATTRIBUTE));
    }

    @Test
    void successfulEnvelopeAllowsNullDataForAnUnboundVisibleRecord() {
        var response = ApiResponse.success(null, "request-1", "trace-1");

        assertThat(response.code()).isEqualTo("OK");
        assertThat(response.data()).isNull();
        assertThat(response.errors()).isEmpty();
    }

    @Test
    void pluralRouteReturnsTheStateArrayDirectly() throws Exception {
        var root = RecordFlowStatesController.class.getAnnotation(RequestMapping.class);
        var method = RecordFlowStatesController.class.getMethod(
                "findAll",
                long.class,
                String.class,
                long.class,
                Object.class,
                HttpServletRequest.class);

        assertThat(root.value()).containsExactly(
                "/api/v1/systems/{systemId}/runtime/modules/{moduleCode}/records/{recordId}/flow-states");
        assertThat(method.getAnnotation(GetMapping.class)).isNotNull();
        assertThat(method.getGenericReturnType().getTypeName())
                .contains("ApiResponse<java.util.List<")
                .contains("RecordFlowViews$RecordFlowState");
    }
}
