package com.unique.examine.flow.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.flow.domain.ApprovalCompletionAttempt;
import com.unique.examine.flow.domain.ApprovalCompletionExecution;
import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.domain.ApprovalSubflowRun;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlowSubflowApiContractTest {
    private static final Instant NOW =
            Instant.parse("2026-07-31T00:00:00Z");

    @Test
    void definitionAndExecutionReadbackExposeOnlyFrozenSanitizedFacts() {
        var step = ApprovalCompletionStep.subflow(
                "child", "Child approval",
                new ApprovalCompletionStep.Subflow(901, 4));
        var execution = ApprovalCompletionExecution.materialize(
                100, 200, 300, 2, 0, step, "{}", NOW)
                .startSubflow(NOW);
        var run = ApprovalSubflowRun.launch(
                400, execution.id(), 1, "a".repeat(64),
                500, step.subflow(), 200, 1, NOW);
        var started = new ApprovalCompletionAttempt(
                600, execution.id(), 1, 2,
                ApprovalCompletionAttempt.Event.STARTED,
                7L, null, null, null, null, null, NOW);

        var mapper = new ObjectMapper();
        var definition = mapper.valueToTree(
                FlowViews.CompletionStep.from(step));
        var detail = mapper.valueToTree(
                FlowViews.CompletionExecution.from(
                        execution, List.of(started), List.of(run)));
        var history = mapper.valueToTree(
                FlowViews.CompletionHistory.from(
                        execution, started, run));

        assertThat(definition.path("type").asText()).isEqualTo("SUBFLOW");
        assertThat(definition.path("subflow").path("definitionId").asText())
                .isEqualTo("901");
        assertThat(definition.path("subflow").path("version").asInt())
                .isEqualTo(4);
        assertThat(detail.path("status").asText()).isEqualTo("RUNNING");
        assertThat(detail.path("subflowRuns").get(0)
                .path("childInstanceId").asText()).isEqualTo("500");
        assertThat(detail.path("subflowRuns").get(0)
                .path("childStatus").asText()).isEqualTo("RUNNING");
        assertThat(history.path("status").asText()).isEqualTo("RUNNING");
        assertThat(history.path("subflowRun").path("attempt").asInt())
                .isEqualTo(1);
        assertThat(detail.toString())
                .doesNotContain("launchKey")
                .doesNotContain("rootInstanceId")
                .doesNotContain("resultAppliedAt")
                .doesNotContain("stateVersion");
    }
}
