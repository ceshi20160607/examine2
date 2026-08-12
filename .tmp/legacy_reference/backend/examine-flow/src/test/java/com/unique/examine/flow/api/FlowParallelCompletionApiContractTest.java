package com.unique.examine.flow.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.domain.ApprovalCompletionAttempt;
import com.unique.examine.flow.domain.ApprovalCompletionExecution;
import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.domain.ApprovalMode;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import com.unique.examine.flow.service.ApprovalWorkflowService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class FlowParallelCompletionApiContractTest {
    private static final Instant NOW =
            Instant.parse("2026-07-31T00:00:00Z");

    @Test
    void roundTripAndDetailExposeOnlySanitizedParallelStageFacts() {
        var first = external("archive", "post_commit");
        var second = external("index", "post_commit");
        var repository = new InMemoryApprovalRepository();
        var sequence = new AtomicLong(10_000);
        var workflow = new ApprovalWorkflowService(
                repository,
                ids(sequence),
                Clock.fixed(NOW, ZoneOffset.UTC),
                1,
                2);
        var draft = workflow.createDraft(
                "Approval", List.of(3L), null, null, null,
                ApprovalMode.SEQUENTIAL, null, null, null, null,
                null, null, null, null, List.of(first, second));
        var version = workflow.publish(draft.id());
        var started = workflow.start(
                draft.id(), version.version(), "record:42", 9);
        var pending = workflow.approve(started.id(), 3, "approved");

        var json = new ObjectMapper();
        var definition = json.valueToTree(
                FlowViews.CompletionStep.from(first));
        var detail = json.valueToTree(FlowViews.Instance.from(
                pending,
                pending.completionExecutions().stream()
                        .map(execution -> FlowViews.CompletionExecution.from(
                                execution, List.of()))
                        .toList()));

        assertThat(definition.path("parallelGroup").asText())
                .isEqualTo("post_commit");
        assertThat(detail.path("activeCompletionOrdinal").asInt())
                .isZero();
        assertThat(detail.path("activeCompletionOrdinals"))
                .extracting(node -> node.asInt())
                .containsExactly(0, 1);
        assertThat(detail.path("completionExecutions").get(0)
                .path("parallelGroup").asText())
                .isEqualTo("post_commit");
        assertThat(detail.toString())
                .doesNotContain("stateVersion")
                .doesNotContain("leaseTokenHash")
                .doesNotContain("launchKey");
    }

    @Test
    void historyRepresentsOneSanitizedStageJoinFact() {
        var step = external("archive", "post_commit");
        var execution = ApprovalCompletionExecution.materialize(
                100, 200, 300, 1, 0, step, "{}", NOW);
        var joined = new ApprovalCompletionAttempt(
                400, execution.id(), 1, 3,
                ApprovalCompletionAttempt.Event.STAGE_JOINED,
                7L, null, null, null, null, null, NOW);

        var value = new ObjectMapper().valueToTree(
                FlowViews.CompletionHistory.from(execution, joined));

        assertThat(value.path("event").asText())
                .isEqualTo("STAGE_JOINED");
        assertThat(value.path("status").asText())
                .isEqualTo("SUCCEEDED");
        assertThat(value.path("ordinal").asInt()).isZero();
        assertThat(value.path("parallelGroup").asText())
                .isEqualTo("post_commit");
        assertThat(value.toString())
                .doesNotContain("lock")
                .doesNotContain("stateVersion");
    }

    private static ApprovalCompletionStep external(
            String code,
            String parallelGroup
    ) {
        return ApprovalCompletionStep.externalTask(
                        code, code,
                        new ApprovalCompletionStep.ExternalTask(
                                "records." + code, 60, 3, 1024))
                .withParallelGroup(parallelGroup);
    }

    private static IdService ids(AtomicLong sequence) {
        return new IdService() {
            @Override
            public long nextId() {
                return sequence.incrementAndGet();
            }
        };
    }
}
