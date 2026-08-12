package com.unique.examine.flow.api;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import static org.assertj.core.api.Assertions.assertThat;

class FlowClaimContractTest {
    @Test
    void freezesReturnCancelClaimClaimAndClaimableRoutes() throws Exception {
        var returned = FlowController.class.getMethod(
                "returnToPrevious",
                long.class,
                long.class,
                FlowRequests.Return.class,
                String.class,
                Object.class,
                HttpServletRequest.class
        );
        var cancelled = FlowController.class.getMethod(
                "cancelClaim",
                long.class,
                long.class,
                FlowRequests.CancelClaim.class,
                String.class,
                Object.class,
                HttpServletRequest.class
        );
        var claimed = FlowController.class.getMethod(
                "claim",
                long.class,
                long.class,
                FlowRequests.Claim.class,
                String.class,
                Object.class,
                HttpServletRequest.class
        );
        var claimable = FlowController.class.getMethod(
                "claimableTasks",
                long.class,
                int.class,
                int.class,
                Object.class,
                HttpServletRequest.class
        );

        assertThat(returned.getAnnotation(PostMapping.class).value())
                .containsExactly("/instances/{instanceId}:return");
        assertThat(cancelled.getAnnotation(PostMapping.class).value())
                .containsExactly("/instances/{instanceId}:cancel-claim");
        assertThat(claimed.getAnnotation(PostMapping.class).value())
                .containsExactly("/instances/{instanceId}:claim");
        assertThat(claimable.getAnnotation(GetMapping.class).value())
                .containsExactly("/claimable-tasks");
        assertThat(returned.getParameters()[3].getAnnotation(RequestHeader.class).name())
                .isEqualTo("Idempotency-Key");
        assertThat(cancelled.getParameters()[3].getAnnotation(RequestHeader.class).name())
                .isEqualTo("Idempotency-Key");
        assertThat(claimed.getParameters()[3].getAnnotation(RequestHeader.class).name())
                .isEqualTo("Idempotency-Key");
        assertThat(FlowPermissions.INSTANCE_RETURN).isEqualTo("flow.instance.return");
        assertThat(FlowPermissions.INSTANCE_CLAIM).isEqualTo("flow.instance.claim");
        assertThat(FlowPermissions.INSTANCE_CANCEL_CLAIM)
                .isEqualTo("flow.instance.cancel-claim");
    }
}
