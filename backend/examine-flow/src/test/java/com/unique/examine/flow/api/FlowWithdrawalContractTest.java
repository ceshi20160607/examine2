package com.unique.examine.flow.api;

import com.unique.examine.flow.service.FlowMutationService;
import com.unique.examine.flow.security.FlowSession;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import static org.assertj.core.api.Assertions.assertThat;

class FlowWithdrawalContractTest {
    @Test
    void freezesExactRoutePermissionHeaderRequestAndTransactionalMutation() throws Exception {
        var controllerMethod = FlowController.class.getMethod(
                "withdraw",
                long.class,
                long.class,
                FlowRequests.Withdrawal.class,
                String.class,
                Object.class,
                HttpServletRequest.class);
        var mapping = controllerMethod.getAnnotation(PostMapping.class);
        var header = controllerMethod.getParameters()[3].getAnnotation(RequestHeader.class);
        var serviceMethod = FlowMutationService.class.getMethod(
                "withdraw",
                FlowSession.class,
                long.class,
                FlowRequests.Withdrawal.class,
                String.class);

        assertThat(mapping.value()).containsExactly("/instances/{instanceId}:withdraw");
        assertThat(header.name()).isEqualTo("Idempotency-Key");
        assertThat(FlowPermissions.INSTANCE_WITHDRAW).isEqualTo("flow.instance.withdraw");
        assertThat(serviceMethod.getAnnotation(Transactional.class)).isNotNull();
        assertThat(new FlowRequests.Withdrawal("reason").reason()).isEqualTo("reason");
    }
}
