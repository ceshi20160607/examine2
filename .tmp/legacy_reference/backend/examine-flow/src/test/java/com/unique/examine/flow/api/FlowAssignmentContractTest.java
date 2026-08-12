package com.unique.examine.flow.api;

import com.unique.examine.flow.security.FlowSession;
import com.unique.examine.flow.service.FlowMutationService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import static org.assertj.core.api.Assertions.assertThat;

class FlowAssignmentContractTest {
    @Test
    void freezesTransferAndAddSignRoutesPermissionsHeadersAndTransactions() throws Exception {
        var transfer = FlowController.class.getMethod(
                "transfer",
                long.class,
                long.class,
                FlowRequests.Transfer.class,
                String.class,
                Object.class,
                HttpServletRequest.class
        );
        var addSign = FlowController.class.getMethod(
                "addSign",
                long.class,
                long.class,
                FlowRequests.AddSign.class,
                String.class,
                Object.class,
                HttpServletRequest.class
        );

        assertThat(transfer.getAnnotation(PostMapping.class).value())
                .containsExactly("/instances/{instanceId}:transfer");
        assertThat(addSign.getAnnotation(PostMapping.class).value())
                .containsExactly("/instances/{instanceId}:add-sign");
        assertThat(transfer.getParameters()[3].getAnnotation(RequestHeader.class).name())
                .isEqualTo("Idempotency-Key");
        assertThat(addSign.getParameters()[3].getAnnotation(RequestHeader.class).name())
                .isEqualTo("Idempotency-Key");
        assertThat(FlowPermissions.INSTANCE_TRANSFER).isEqualTo("flow.instance.transfer");
        assertThat(FlowPermissions.INSTANCE_ADD_SIGN).isEqualTo("flow.instance.add-sign");
        assertThat(FlowMutationService.class.getMethod(
                        "transfer",
                        FlowSession.class,
                        long.class,
                        FlowRequests.Transfer.class,
                        String.class)
                .getAnnotation(Transactional.class)).isNotNull();
        assertThat(FlowMutationService.class.getMethod(
                        "addSign",
                        FlowSession.class,
                        long.class,
                        FlowRequests.AddSign.class,
                        String.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }
}
