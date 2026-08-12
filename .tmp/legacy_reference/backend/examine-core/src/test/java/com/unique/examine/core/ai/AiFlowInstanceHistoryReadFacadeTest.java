package com.unique.examine.core.ai;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiFlowInstanceHistoryReadFacadeTest {
    private static final Instant OCCURRED =
            Instant.parse("2026-08-05T02:00:00Z");

    @Test
    void resultIsBoundedChronologicalAndDefensivelyCopied() {
        var event = event(7, OCCURRED);
        var events = new ArrayList<>(List.of(event));
        var result = new AiFlowInstanceHistoryReadFacade.Result(
                "40", "PENDING", 7, "/systems/10/flows", events);
        events.clear();

        assertThat(result.events()).containsExactly(event);
        assertThat(result.events().getFirst())
                .extracting(
                        AiFlowInstanceHistoryReadFacade.Event::eventType,
                        AiFlowInstanceHistoryReadFacade.Event::actorMemberId,
                        AiFlowInstanceHistoryReadFacade.Event::comment)
                .containsExactly("APPROVED", "30", "Looks good");
    }

    @Test
    void rejectsInvalidRequestAndUnsafeOrUnorderedResults() {
        assertThatThrownBy(() -> request("040", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("instanceId");
        assertThatThrownBy(() -> request("40", 21))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
        assertThatThrownBy(() -> new AiFlowInstanceHistoryReadFacade.Event(
                1, "completion.started", null, "RUNNING", null, null, OCCURRED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventType");
        assertThatThrownBy(() -> new AiFlowInstanceHistoryReadFacade.Event(
                1, "APPROVED", "PENDING", "APPROVED", "30",
                "x".repeat(AiFlowInstanceHistoryReadFacade
                        .MAX_COMMENT_CHARACTERS + 1), OCCURRED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("comment");
        assertThatThrownBy(() -> new AiFlowInstanceHistoryReadFacade.Result(
                "40", "PENDING", 2, "/systems/10/flows",
                List.of(event(2, OCCURRED), event(1, OCCURRED.plusSeconds(1)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chronology");
    }

    private static AiFlowInstanceHistoryReadFacade.Request request(
            String instanceId, int limit) {
        return new AiFlowInstanceHistoryReadFacade.Request(
                1, 10, 20, 30,
                Set.of("flow.instance.read"), instanceId, limit);
    }

    private static AiFlowInstanceHistoryReadFacade.Event event(
            int sequence, Instant occurredAt) {
        return new AiFlowInstanceHistoryReadFacade.Event(
                sequence, "APPROVED", "PENDING", "APPROVED", "30",
                "Looks good", occurredAt);
    }
}
