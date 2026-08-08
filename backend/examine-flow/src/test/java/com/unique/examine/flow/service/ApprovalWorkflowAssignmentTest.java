package com.unique.examine.flow.service;

import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.domain.ApprovalHistoryEvent;
import com.unique.examine.flow.domain.ApprovalTaskStatus;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalWorkflowAssignmentTest {
    @Test
    void transferAndAddSignUpdatePendingTaskProjectionWithoutMutatingDefinition() {
        var sequence = new AtomicLong(100);
        var ids = new IdService() {
            @Override
            public long nextId() {
                return sequence.incrementAndGet();
            }
        };
        var service = new ApprovalWorkflowService(
                new InMemoryApprovalRepository(),
                ids,
                Clock.fixed(Instant.parse("2026-07-27T11:00:00Z"), ZoneOffset.UTC)
        );
        var draft = service.createDraft("Sequential", List.of(20L, 40L));
        var version = service.publish(draft.id());
        var started = service.startLatest(draft.id(), "expense-001", 10);

        var expected = service.requireCurrentApprover(started.id(), 20);
        var transferred = service.transfer(expected, 20, 30, "owning reviewer");
        assertThat(service.approvalTasks(20, ApprovalTaskStatus.PENDING, 1, 20).items()).isEmpty();
        assertThat(service.approvalTasks(30, ApprovalTaskStatus.PENDING, 1, 20).items())
                .extracting(instance -> instance.id())
                .containsExactly(started.id());

        var added = service.addSign(
                service.requireCurrentApprover(started.id(), 30),
                30,
                50,
                ApprovalHistoryEvent.AssignmentPosition.BEFORE,
                "security first"
        );
        assertThat(service.approvalTasks(30, ApprovalTaskStatus.PENDING, 1, 20).items()).isEmpty();
        assertThat(service.approvalTasks(50, ApprovalTaskStatus.PENDING, 1, 20).items())
                .extracting(instance -> instance.id())
                .containsExactly(started.id());
        assertThat(added.approverIds()).containsExactly(50L, 30L, 40L);
        assertThat(version.approverIds()).containsExactly(20L, 40L);
        assertThat(transferred.history()).hasSize(2);
        assertThat(added.history()).hasSize(3);
    }
}
