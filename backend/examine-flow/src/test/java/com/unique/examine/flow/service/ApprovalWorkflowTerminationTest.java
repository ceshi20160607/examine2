package com.unique.examine.flow.service;

import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.domain.ApprovalHistoryEvent;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.ApprovalTaskStatus;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalWorkflowTerminationTest {
    @Test
    void operatorTerminationPersistsTerminalHistoryAndRemovesCurrentPendingTask() {
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
                Clock.fixed(Instant.parse("2026-07-27T09:00:00Z"), ZoneOffset.UTC));
        var draft = service.createDraft("Sequential", List.of(20L, 30L));
        service.publish(draft.id());
        var started = service.startLatest(draft.id(), "expense-001", 10);
        service.approve(started.id(), 20, "first step");

        var terminated = service.terminate(started.id(), 99, "duplicate request");

        assertThat(terminated.status()).isEqualTo(ApprovalInstance.Status.TERMINATED);
        assertThat(terminated.completedAt()).isNotNull();
        assertThat(terminated.history().getLast().type())
                .isEqualTo(ApprovalHistoryEvent.Type.TERMINATED);
        assertThat(terminated.history().getLast().actorId()).isEqualTo(99);
        assertThat(terminated.history().getLast().comment()).isEqualTo("duplicate request");
        assertThat(service.approvalTasks(30, ApprovalTaskStatus.PENDING, 1, 20).items())
                .isEmpty();
        assertThat(service.approvalTasks(30, ApprovalTaskStatus.COMPLETED, 1, 20).items())
                .extracting(ApprovalInstance::id)
                .containsExactly(started.id());
    }
}
