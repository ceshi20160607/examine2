package com.unique.examine.flow.api;

import com.unique.examine.flow.interaction.FlowInteractionMutationService;
import com.unique.examine.flow.security.FlowSession;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import static org.assertj.core.api.Assertions.assertThat;

class FlowInteractionContractTest {
    @Test
    void freezesUrgeAndCommentRoutesPermissionsHeadersAndTransactionalMutations() throws Exception {
        var urge = FlowController.class.getMethod(
                "urge",
                long.class,
                long.class,
                FlowRequests.Urge.class,
                String.class,
                Object.class,
                HttpServletRequest.class);
        var urges = FlowController.class.getMethod(
                "urges",
                long.class,
                long.class,
                int.class,
                int.class,
                Object.class,
                HttpServletRequest.class);
        var comment = FlowController.class.getMethod(
                "comment",
                long.class,
                long.class,
                FlowRequests.Comment.class,
                String.class,
                Object.class,
                HttpServletRequest.class);
        var comments = FlowController.class.getMethod(
                "comments",
                long.class,
                long.class,
                int.class,
                int.class,
                Object.class,
                HttpServletRequest.class);

        assertThat(urge.getAnnotation(PostMapping.class).value())
                .containsExactly("/instances/{instanceId}:urge");
        assertThat(urges.getAnnotation(GetMapping.class).value())
                .containsExactly("/instances/{instanceId}/urges");
        assertThat(comment.getAnnotation(PostMapping.class).value())
                .containsExactly("/instances/{instanceId}/comments");
        assertThat(comments.getAnnotation(GetMapping.class).value())
                .containsExactly("/instances/{instanceId}/comments");
        assertThat(urge.getParameters()[3].getAnnotation(RequestHeader.class).name())
                .isEqualTo("Idempotency-Key");
        assertThat(comment.getParameters()[3].getAnnotation(RequestHeader.class).name())
                .isEqualTo("Idempotency-Key");
        assertThat(FlowPermissions.INSTANCE_URGE).isEqualTo("flow.instance.urge");
        assertThat(FlowPermissions.INSTANCE_COMMENT).isEqualTo("flow.instance.comment");
        assertThat(FlowInteractionMutationService.class.getMethod(
                        "urge",
                        FlowSession.class,
                        long.class,
                        FlowRequests.Urge.class,
                        String.class)
                .getAnnotation(Transactional.class)).isNotNull();
        assertThat(FlowInteractionMutationService.class.getMethod(
                        "comment",
                        FlowSession.class,
                        long.class,
                        FlowRequests.Comment.class,
                        String.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }
}
