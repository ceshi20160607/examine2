package com.unique.examine.flow.interaction;

import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.interaction.memory.InMemoryFlowInteractionRepository;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import com.unique.examine.flow.service.ApprovalWorkflowService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowInteractionServiceTest {
    @Test
    void timelinesAreScopedStableAscendingAndUseExistingPageBounds() {
        var fixture = fixture();
        var pending = fixture.start();
        fixture.interactions.urge(pending.id(), 10, "first");
        fixture.interactions.urge(pending.id(), 10, "second");
        fixture.interactions.comment(pending.id(), 99, "first");
        fixture.interactions.comment(pending.id(), 99, "second");

        assertThat(fixture.interactions.urges(pending.id(), 1, 1).items())
                .extracting(FlowUrge::message)
                .containsExactly("first");
        assertThat(fixture.interactions.urges(pending.id(), 2, 1).items())
                .extracting(FlowUrge::message)
                .containsExactly("second");
        assertThat(fixture.interactions.urges(pending.id(), 1, 20).total()).isEqualTo(2);
        assertThat(fixture.interactions.comments(pending.id(), 1, 20).items())
                .extracting(FlowComment::body)
                .containsExactly("first", "second");
        assertThatThrownBy(() -> fixture.interactions.comments(pending.id(), 1, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void permittedInteractionServiceAllowsCommentsAfterTerminalDecision() {
        var fixture = fixture();
        var pending = fixture.start();
        fixture.workflows.approve(pending.id(), 20, "done");

        var comment = fixture.interactions.comment(pending.id(), 99, "terminal note");

        assertThat(comment.body()).isEqualTo("terminal note");
        assertThat(fixture.workflows.instance(pending.id()).status())
                .isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(fixture.workflows.history(pending.id())).hasSize(2);
    }

    private static Fixture fixture() {
        var sequence = new AtomicLong(100);
        IdService ids = new IdService() {
            @Override
            public long nextId() {
                return sequence.incrementAndGet();
            }
        };
        var clock = Clock.fixed(Instant.parse("2026-07-27T10:00:00Z"), ZoneOffset.UTC);
        var workflows = new ApprovalWorkflowService(new InMemoryApprovalRepository(), ids, clock);
        var interactions = new FlowInteractionService(
                workflows,
                new InMemoryFlowInteractionRepository(),
                ids,
                clock
        );
        return new Fixture(workflows, interactions);
    }

    private record Fixture(
            ApprovalWorkflowService workflows,
            FlowInteractionService interactions
    ) {
        private ApprovalInstance start() {
            var definition = workflows.createDraft("Approval", 20);
            workflows.publish(definition.id());
            return workflows.startLatest(definition.id(), "expense-" + definition.id(), 10);
        }
    }
}
