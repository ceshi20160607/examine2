package com.unique.examine.flow.api;

import com.unique.examine.flow.security.FlowSession;
import com.unique.examine.flow.service.FlowMutationService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import static org.assertj.core.api.Assertions.assertThat;

class FlowTerminationContractTest {
    @Test
    void freezesExactRoutePermissionHeaderRequestAndTransactionalMutation() throws Exception {
        var controllerMethod = FlowController.class.getMethod(
                "terminate",
                long.class,
                long.class,
                FlowRequests.Termination.class,
                String.class,
                Object.class,
                HttpServletRequest.class);
        var mapping = controllerMethod.getAnnotation(PostMapping.class);
        var header = controllerMethod.getParameters()[3].getAnnotation(RequestHeader.class);
        var serviceMethod = FlowMutationService.class.getMethod(
                "terminate",
                FlowSession.class,
                long.class,
                FlowRequests.Termination.class,
                String.class);

        assertThat(mapping.value()).containsExactly("/instances/{instanceId}:terminate");
        assertThat(header.name()).isEqualTo("Idempotency-Key");
        assertThat(FlowPermissions.INSTANCE_TERMINATE).isEqualTo("flow.instance.terminate");
        assertThat(serviceMethod.getAnnotation(Transactional.class)).isNotNull();
        assertThat(new FlowRequests.Termination("reason").reason()).isEqualTo("reason");
    }
}
