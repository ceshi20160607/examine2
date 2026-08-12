package com.unique.examine.flow.service;

import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.ApprovalTaskStatus;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.PERSISTENCE_CONFLICT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalWorkflowClaimTest {
    @Test
    void openTaskLeavesOwnerInboxEntersClaimPoolAndReturnsToClaimantInbox() {
        var fixture = fixture();
        var started = fixture.start();

        var open = fixture.workflows.cancelClaim(started.id(), 20, "release");

        assertThat(open.claimState()).isEqualTo(ApprovalInstance.ClaimState.OPEN);
        assertThat(fixture.workflows.approvalTasks(20, ApprovalTaskStatus.PENDING, 1, 20).items())
                .isEmpty();
        assertThat(fixture.workflows.approvalTasks(20, ApprovalTaskStatus.ALL, 1, 20).items())
                .isEmpty();
        assertThat(fixture.workflows.claimableTasks(1, 20).items())
                .extracting(ApprovalInstance::id)
                .containsExactly(started.id());

        var claimed = fixture.workflows.claim(started.id(), 40, "mine");

        assertThat(claimed.approverId()).isEqualTo(40);
        assertThat(claimed.claimState()).isEqualTo(ApprovalInstance.ClaimState.CLAIMED);
        assertThat(fixture.workflows.claimableTasks(1, 20).total()).isZero();
        assertThat(fixture.workflows.approvalTasks(40, ApprovalTaskStatus.PENDING, 1, 20).items())
                .extracting(ApprovalInstance::id)
                .containsExactly(started.id());
    }

    @Test
    void twoStaleClaimSnapshotsUseHistoryVersionCasAndOnlyOneWins() {
        var fixture = fixture();
        var open = fixture.workflows.cancelClaim(fixture.start().id(), 20, "release");
        var firstSnapshot = fixture.repository.findInstance(open.id()).orElseThrow();
        var secondSnapshot = fixture.repository.findInstance(open.id()).orElseThrow();

        fixture.repository.saveInstance(firstSnapshot.claim(40, "first", fixture.now));

        assertThatThrownBy(() ->
                fixture.repository.saveInstance(secondSnapshot.claim(50, "second", fixture.now)))
                .isInstanceOfSatisfying(
                        ApprovalDomainException.class,
                        error -> assertThat(error.code()).isEqualTo(PERSISTENCE_CONFLICT)
                );
        assertThat(fixture.repository.findInstance(open.id()).orElseThrow().approverId())
                .isEqualTo(40);
    }

    private static Fixture fixture() {
        var sequence = new AtomicLong(100);
        var repository = new InMemoryApprovalRepository();
        var now = Instant.parse("2026-07-27T12:30:00Z");
        var workflows = new ApprovalWorkflowService(
                repository,
                new IdService() {
                    @Override
                    public long nextId() {
                        return sequence.incrementAndGet();
                    }
                },
                Clock.fixed(now, ZoneOffset.UTC)
        );
        return new Fixture(repository, workflows, now);
    }

    private record Fixture(
            InMemoryApprovalRepository repository,
            ApprovalWorkflowService workflows,
            Instant now
    ) {
        private ApprovalInstance start() {
            var draft = workflows.createDraft("Sequential", List.of(20L, 30L));
            workflows.publish(draft.id());
            return workflows.startLatest(draft.id(), "expense-001", 10);
        }
    }
}
