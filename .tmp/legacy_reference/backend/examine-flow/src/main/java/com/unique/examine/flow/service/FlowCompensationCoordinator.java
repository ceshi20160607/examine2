package com.unique.examine.flow.service;

import com.unique.examine.flow.domain.ApprovalCompletionExecution;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.repository.ApprovalRepository;

import java.time.Instant;

/** Transactional bridge from terminal forward failure into reverse execution. */
interface FlowCompensationCoordinator {
    boolean onTerminalForwardFailure(
            long systemId,
            long tenantId,
            ApprovalRepository repository,
            ApprovalInstance lockedParent,
            ApprovalCompletionExecution failed,
            long actorId,
            Instant occurredAt
    );
}
