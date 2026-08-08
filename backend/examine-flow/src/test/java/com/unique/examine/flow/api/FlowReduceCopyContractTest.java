package com.unique.examine.flow.api;

import com.unique.examine.flow.interaction.FlowInteractionMutationService;
import com.unique.examine.flow.security.FlowSession;
import com.unique.examine.flow.service.FlowMutationService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import static org.assertj.core.api.Assertions.assertThat;

class FlowReduceCopyContractTest {
    @Test
    void freezesReduceSignAndCopyRoutesPermissionsHeadersAndTransactions() throws Exception {
        var reduce = FlowController.class.getMethod(
                "reduceSign",
                long.class,
                long.class,
                FlowRequests.ReduceSign.class,
                String.class,
                Object.class,
                HttpServletRequest.class
        );
        var copy = FlowController.class.getMethod(
                "copy",
                long.class,
                long.class,
                FlowRequests.Copy.class,
                String.class,
                Object.class,
                HttpServletRequest.class
        );
        var copies = FlowController.class.getMethod(
                "copies",
                long.class,
                long.class,
                int.class,
                int.class,
                Object.class,
                HttpServletRequest.class
        );

        assertThat(reduce.getAnnotation(PostMapping.class).value())
                .containsExactly("/instances/{instanceId}:reduce-sign");
        assertThat(copy.getAnnotation(PostMapping.class).value())
                .containsExactly("/instances/{instanceId}/copies");
        assertThat(copies.getAnnotation(GetMapping.class).value())
                .containsExactly("/instances/{instanceId}/copies");
        assertThat(reduce.getParameters()[3].getAnnotation(RequestHeader.class).name())
                .isEqualTo("Idempotency-Key");
        assertThat(copy.getParameters()[3].getAnnotation(RequestHeader.class).name())
                .isEqualTo("Idempotency-Key");
        assertThat(FlowPermissions.INSTANCE_REDUCE_SIGN)
                .isEqualTo("flow.instance.reduce-sign");
        assertThat(FlowPermissions.INSTANCE_COPY).isEqualTo("flow.instance.copy");
        assertThat(FlowMutationService.class.getMethod(
                        "reduceSign",
                        FlowSession.class,
                        long.class,
                        FlowRequests.ReduceSign.class,
                        String.class
                ).getAnnotation(Transactional.class))
                .isNotNull();
        assertThat(FlowInteractionMutationService.class.getMethod(
                        "copy",
                        FlowSession.class,
                        long.class,
                        FlowRequests.Copy.class,
                        String.class
                ).getAnnotation(Transactional.class))
                .isNotNull();
    }
}
