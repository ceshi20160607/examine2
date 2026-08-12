package com.unique.examine.core.ai;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiMessageReadFacadeTest {
    @Test
    void acceptsOneBoundedTypedCurrentMemberProjection() {
        var request = new AiMessageReadFacade.Request(
                10, 20, 30, Set.of("event.message.access"),
                AiMessageReadFacade.Status.UNREAD, 20);
        var message = new AiMessageReadFacade.Message(
                "40", "TASK_ASSIGNED", "Assigned", "Review",
                new AiMessageReadFacade.Target("WORK_TASK", "50"),
                "/systems/10/work/tasks/50", "UNREAD",
                Instant.parse("2026-08-04T08:00:00Z"), null, null, 1);
        var result = new AiMessageReadFacade.Result(
                request.status(), 1, 1, List.of(message));

        assertThat(result.items()).containsExactly(message);
        assertThat(result.unreadCount()).isOne();
    }

    @Test
    void rejectsEscapedScopeLimitAndInconsistentState() {
        assertThatThrownBy(() -> new AiMessageReadFacade.Request(
                10, 20, 0, Set.of(), AiMessageReadFacade.Status.ALL, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiMessageReadFacade.Request(
                10, 20, 30, Set.of(), AiMessageReadFacade.Status.ALL, 21))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiMessageReadFacade.Message(
                "40", "NOTICE", "Notice", "Body", null, null, "READ",
                Instant.parse("2026-08-04T08:00:00Z"), null, null, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
