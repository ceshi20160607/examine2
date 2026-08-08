package com.unique.examine.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalCompletionExecutionTest {
    private static final Instant NOW =
            Instant.parse("2026-07-31T06:00:00Z");
    private static final String TOKEN = "a".repeat(64);

    @Test
    void validatesAndPublishesImmutableOrderedCompletionSteps() {
        var external = ApprovalCompletionStep.externalTask(
                "sync_ledger", "Sync ledger",
                new ApprovalCompletionStep.ExternalTask(
                        "ledger.sync", 60, 3, 2048));
        var webhook = ApprovalCompletionStep.webhook(
                "notify", "Notify downstream",
                new ApprovalCompletionStep.Webhook(
                        "https://example.test/hooks/approval", "vault/flow",
                        10, 4, 5));
        var draft = new ApprovalDefinitionDraft(
                101L, "Expense", List.of(20L), 1, NOW,
                null, null, null, ApprovalMode.SEQUENTIAL,
                null, null, null, null, null, null, null,
                null, List.of(external, webhook));

        var published = ApprovalDefinitionVersion.publish(
                draft, 1, NOW.plusSeconds(1));

        assertThat(published.completionSteps())
                .containsExactly(external, webhook);
        assertThatThrownBy(() -> ApprovalCompletionStep.requireSteps(
                List.of(external, external)))
                .isInstanceOf(ApprovalDomainException.class)
                .hasMessageContaining("unique");
        assertThatThrownBy(() -> new ApprovalCompletionStep.ExternalTask(
                "Invalid Topic", 29, 0, 9000))
                .isInstanceOf(ApprovalDomainException.class);
    }

    @Test
    void enforcesLeaseOwnershipRetryBackoffAndOptimisticVersion() {
        var step = ApprovalCompletionStep.webhook(
                "notify", "Notify downstream",
                new ApprovalCompletionStep.Webhook(
                        "https://example.test/hooks/approval", null,
                        10, 3, 7));
        var available = ApprovalCompletionExecution.materialize(
                301L, 201L, 101L, 1, 0, step, "{\"version\":1}", NOW);

        var leased = available.claim(
                "webhook-worker", TOKEN, NOW.plusSeconds(60), NOW);
        var retrying = leased.fail(
                TOKEN, "HTTP_503", "Downstream unavailable", true,
                NOW.plusSeconds(2));

        assertThat(leased.attemptCount()).isEqualTo(1);
        assertThat(leased.stateVersion()).isEqualTo(1);
        assertThat(retrying.status())
                .isEqualTo(ApprovalCompletionExecution.Status.RETRYING);
        assertThat(retrying.availableAt()).isEqualTo(NOW.plusSeconds(9));
        assertThat(retrying.stateVersion()).isEqualTo(2);
        assertThatThrownBy(() -> leased.complete(
                "b".repeat(64), "{}", NOW.plusSeconds(1)))
                .isInstanceOf(ApprovalDomainException.class)
                .hasMessageContaining("stale or invalid");
    }

    @Test
    void holdsTheInstancePendingUntilEveryCompletionSucceeds() {
        var definition = new ApprovalDefinitionVersion(
                101L, 1, "Expense", 20L, 1, NOW.minusSeconds(10));
        var approved = ApprovalInstance.start(
                201L, definition, "expense-201", 10L,
                NOW.minusSeconds(5)).approve(20L, "", NOW);
        var execution = ApprovalCompletionExecution.materialize(
                301L, 201L, 101L, 1, 0,
                ApprovalCompletionStep.externalTask(
                        "archive", "Archive",
                        new ApprovalCompletionStep.ExternalTask(
                                "flow.archive", 60, 2, 1024)),
                "{\"version\":1}", NOW);

        var running = approved.beginCompletion(List.of(execution));
        var succeeded = execution.claim(
                "worker-1", TOKEN, NOW.plusSeconds(60), NOW)
                .complete(TOKEN, "{\"archived\":true}", NOW.plusSeconds(1));
        var completed = running.completeCompletion(
                List.of(succeeded), 10L, NOW.plusSeconds(2));

        assertThat(running.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(running.completionPhase())
                .isEqualTo(ApprovalInstance.CompletionPhase.EXTERNAL_EXECUTION);
        assertThat(running.activeApproverIds()).isEmpty();
        assertThat(running.history().getLast().toStatus())
                .isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(running.activeCompletionExecution()).isEqualTo(execution);
        assertThat(completed.status()).isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(completed.completionPhase())
                .isEqualTo(ApprovalInstance.CompletionPhase.COMPLETED);
        assertThat(completed.history().getLast().type())
                .isEqualTo(ApprovalHistoryEvent.Type.COMPLETION_COMPLETED);
    }

    @Test
    void deadlineAutoApprovalAlsoEntersAConsistentExternalPhase() {
        var policy = new ApprovalDeadlinePolicy(
                1, null,
                ApprovalDeadlinePolicy.TimeoutAction.AUTO_APPROVE);
        var definition = new ApprovalDefinitionVersion(
                101L, 1, "Timed approval", List.of(20L), 1,
                NOW.minusSeconds(10), null, null, null,
                ApprovalMode.SEQUENTIAL, null, null,
                ApprovalApproverSources.fixedFor(null, null, null),
                ApprovalQuorumRules.none(),
                new ApprovalDeadlinePolicies(policy, Map.of()));
        var pending = ApprovalInstance.start(
                201L, definition, "timed-201", 10L, NOW);
        var autoApproved = pending.processDeadline(
                null, 10L, pending.deadline().dueAt());
        var execution = ApprovalCompletionExecution.materialize(
                301L, 201L, 101L, 1, 0,
                ApprovalCompletionStep.externalTask(
                        "archive", "Archive",
                        new ApprovalCompletionStep.ExternalTask(
                                "flow.archive", 60, 2, 1024)),
                "{}", pending.deadline().dueAt());

        var running = autoApproved.beginCompletion(List.of(execution));

        assertThat(running.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(running.history().getLast().type())
                .isEqualTo(
                        ApprovalHistoryEvent.Type.DEADLINE_AUTO_APPROVED);
        assertThat(running.history().getLast().toStatus())
                .isEqualTo(ApprovalInstance.Status.PENDING);
    }

    @Test
    void terminationCancelsEveryNonTerminalCompletionExecution() {
        var definition = new ApprovalDefinitionVersion(
                101L, 1, "Expense", 20L, 1, NOW.minusSeconds(10));
        var approved = ApprovalInstance.start(
                201L, definition, "expense-202", 10L,
                NOW.minusSeconds(5)).approve(20L, "", NOW);
        var step = ApprovalCompletionStep.externalTask(
                "archive", "Archive",
                new ApprovalCompletionStep.ExternalTask(
                        "flow.archive", 60, 2, 1024));
        var first = ApprovalCompletionExecution.materialize(
                301L, 201L, 101L, 1, 0, step, "{}", NOW);
        var second = ApprovalCompletionExecution.materialize(
                302L, 201L, 101L, 1, 1, step, "{}", NOW);
        var running = approved.beginCompletion(List.of(first, second));

        var terminated = running.terminate(
                99L, "Cancelled by administrator", NOW.plusSeconds(1));

        assertThat(terminated.completionExecutions())
                .extracting(ApprovalCompletionExecution::status)
                .containsExactly(
                        ApprovalCompletionExecution.Status.CANCELLED,
                        ApprovalCompletionExecution.Status.CANCELLED);
        assertThat(terminated.activeCompletionOrdinal()).isNull();
    }

    @Test
    void persistsSanitizedWebhookAttemptDiagnosticsOnly() {
        var attempt = new ApprovalCompletionAttempt(
                401L, 301L, 1, 3,
                ApprovalCompletionAttempt.Event.FAILED,
                null, "webhook-worker", null, null,
                "HTTP_502", "Bad gateway", 502, 18L,
                "b".repeat(64), NOW, NOW.plusMillis(18),
                NOW.plusMillis(18));

        assertThat(attempt.httpStatus()).isEqualTo(502);
        assertThat(attempt.durationMs()).isEqualTo(18L);
        assertThat(attempt.responseSha256()).hasSize(64);
        assertThat(attempt.resultJson()).isNull();
    }
}
