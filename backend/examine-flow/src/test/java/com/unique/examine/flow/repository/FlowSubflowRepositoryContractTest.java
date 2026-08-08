package com.unique.examine.flow.repository;

import com.unique.examine.flow.domain.ApprovalCompletionExecution;
import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.domain.ApprovalSubflowRun;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowSubflowRepositoryContractTest {
    private static final Instant NOW =
            Instant.parse("2026-07-31T10:30:00Z");
    private static final ApprovalCompletionStep STEP =
            ApprovalCompletionStep.subflow(
                    "child", "Child",
                    new ApprovalCompletionStep.Subflow(102L, 2));

    @Test
    void persistsIdempotentLaunchTerminalResultAndRestartReadback() {
        var repository = new InMemoryApprovalRepository();
        var available = ApprovalCompletionExecution.materialize(
                301L, 201L, 101L, 1, 0, STEP, "{}", NOW);
        repository.materializeCompletionExecutions(List.of(available));
        var running = available.startSubflow(NOW.plusSeconds(1));
        repository.saveCompletionExecution(running);
        var launched = ApprovalSubflowRun.launch(
                401L, 301L, 1, "a".repeat(64), 501L,
                STEP.subflow(), 201L, 1, NOW.plusSeconds(1));

        assertThat(repository.appendSubflowRun(launched)).isEqualTo(launched);
        assertThat(repository.appendSubflowRun(new ApprovalSubflowRun(
                999L, 301L, 1, "a".repeat(64), 999L,
                102L, 2, 201L, 1, ApprovalSubflowRun.Status.RUNNING,
                NOW.plusSeconds(2), null, null, null, 0)))
                .isEqualTo(launched);
        assertThat(repository.findSubflowRunByChildInstance(501L))
                .contains(launched);
        assertThat(repository.findRunningSubflowRuns(20))
                .containsExactly(launched);

        var terminal = launched.observeTerminal(
                ApprovalSubflowRun.Status.REJECTED, NOW.plusSeconds(3));
        repository.saveSubflowRun(terminal);
        assertThat(repository.findPendingSubflowResults(20))
                .containsExactly(terminal);
        var applied = terminal.markResultApplied(NOW.plusSeconds(4));
        repository.saveSubflowRun(applied);

        assertThat(repository.findPendingSubflowResults(20)).isEmpty();
        assertThat(repository.findSubflowRunsByExecution(301L))
                .containsExactly(applied);
    }

    @Test
    void retryPreservesPriorChildAndCreatesASecondAttempt() {
        var repository = new InMemoryApprovalRepository();
        var available = ApprovalCompletionExecution.materialize(
                301L, 201L, 101L, 1, 0, STEP, "{}", NOW);
        repository.materializeCompletionExecutions(List.of(available));
        var firstExecution = available.startSubflow(NOW.plusSeconds(1));
        repository.saveCompletionExecution(firstExecution);
        var first = ApprovalSubflowRun.launch(
                401L, 301L, 1, "a".repeat(64), 501L,
                STEP.subflow(), 201L, 1, NOW.plusSeconds(1));
        repository.appendSubflowRun(first);
        repository.saveSubflowRun(first.observeTerminal(
                ApprovalSubflowRun.Status.REJECTED, NOW.plusSeconds(2)));
        var failed = firstExecution.failSubflow(
                "REJECTED", "Child was rejected", NOW.plusSeconds(2));
        repository.saveCompletionExecution(failed);
        var retried = failed.retry(NOW.plusSeconds(3));
        repository.saveCompletionExecution(retried);
        var secondExecution = retried.startSubflow(NOW.plusSeconds(3));
        repository.saveCompletionExecution(secondExecution);
        var second = ApprovalSubflowRun.launch(
                402L, 301L, 2, "b".repeat(64), 502L,
                STEP.subflow(), 201L, 1, NOW.plusSeconds(3));
        repository.appendSubflowRun(second);

        assertThat(repository.findSubflowRunsByExecution(301L))
                .extracting(ApprovalSubflowRun::childInstanceId)
                .containsExactly(501L, 502L);
        assertThat(repository.findRunningSubflowRuns(20))
                .containsExactly(second);
        assertThatThrownBy(() -> repository.saveSubflowRun(second))
                .isInstanceOf(ApprovalDomainException.class)
                .hasMessageContaining("concurrently");
    }

    @Test
    void exposesOnlyDueSubflowLaunchCandidates() {
        var repository = new InMemoryApprovalRepository();
        var subflow = ApprovalCompletionExecution.materialize(
                301L, 201L, 101L, 1, 0, STEP, "{}", NOW);
        var external = ApprovalCompletionExecution.materialize(
                302L, 202L, 101L, 1, 0,
                ApprovalCompletionStep.externalTask(
                        "work", "Work",
                        new ApprovalCompletionStep.ExternalTask(
                                "flow.work", 60, 2, 1024)),
                "{}", NOW);
        repository.materializeCompletionExecutions(List.of(subflow));
        repository.materializeCompletionExecutions(List.of(external));

        assertThat(repository.findDueSubflowExecutions(NOW, 20))
                .containsExactly(subflow);
        assertThat(repository.findDueSubflowExecutions(
                NOW.minusMillis(1), 20)).isEmpty();
    }
}
