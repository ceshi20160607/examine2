package com.unique.examine.core.ai;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiFlowDefinitionDraftFacadeTest {
    @Test
    void normalizesOneBoundedOrderedDefinitionDraft() {
        var draft = new AiFlowDefinitionDraftFacade.Draft(
                "  Expense approval  ", List.of("20", "20", "30"));
        assertThat(draft.name()).isEqualTo("Expense approval");
        assertThat(draft.approverMemberIds())
                .containsExactly("20", "20", "30");
        var request = request(draft);
        assertThat(request.operation()).isEqualTo(
                AiFlowDefinitionDraftFacade.Operation.FLOW_DEFINITION_DRAFT);
    }

    @Test
    void rejectsUnsafeNarrativeInvalidIdsAndOversizedRoutes() {
        assertThatThrownBy(() -> new AiFlowDefinitionDraftFacade.Draft(
                "bad\u0000name", List.of("20")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiFlowDefinitionDraftFacade.Draft(
                "Approval", List.of("020")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiFlowDefinitionDraftFacade.Draft(
                "Approval", java.util.stream.LongStream.rangeClosed(1, 11)
                .mapToObj(Long::toString).toList()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static AiFlowDefinitionDraftFacade.PrepareRequest request(
            AiFlowDefinitionDraftFacade.Draft draft) {
        return new AiFlowDefinitionDraftFacade.PrepareRequest(
                "proposal-1", "session-1", "turn-1",
                99, 1, 2, 10, 7,
                Set.of("flow.definition.manage"),
                AiFlowDefinitionDraftFacade.Operation.FLOW_DEFINITION_DRAFT,
                draft, "51", "61", 2, "prompt-v1",
                "prepare-request", "prepare-trace");
    }

    static AiFlowDefinitionDraftFacade.DefinitionReadback readback() {
        return new AiFlowDefinitionDraftFacade.DefinitionReadback(
                AiFlowDefinitionDraftFacade.Operation.FLOW_DEFINITION_DRAFT,
                "501", "Expense approval", List.of("20", "30"),
                0, Instant.parse("2026-08-04T08:00:00Z"), false);
    }
}
