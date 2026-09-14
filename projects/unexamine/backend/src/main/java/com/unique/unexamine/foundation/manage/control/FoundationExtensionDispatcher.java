package com.unique.unexamine.foundation.manage.control;

import com.unique.unexamine.foundation.base.entity.CoreEventOutbox;
import com.unique.unexamine.foundation.base.service.CoreEventOutboxBaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FoundationExtensionDispatcher {
    private final CoreEventOutboxBaseService outboxService;
    private final List<FoundationCommandExtension> extensions;
    private final BusinessActionMapper actionMapper;
    private final TransactionTemplate transactions;
    private final org.springframework.beans.factory.ObjectProvider<BusinessActionApprovalCoordinator> approvals;

    public FoundationExtensionDispatcher(CoreEventOutboxBaseService outboxService,
                                         List<FoundationCommandExtension> extensions,
                                         BusinessActionMapper actionMapper, PlatformTransactionManager transactionManager,
                                         org.springframework.beans.factory.ObjectProvider<BusinessActionApprovalCoordinator> approvals) {
        this.outboxService = outboxService;
        this.extensions = extensions;
        this.actionMapper = actionMapper;
        this.approvals = approvals;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void dispatch(long outboxId) {
        try {
            transactions.executeWithoutResult(transaction -> {
                CoreEventOutbox event = actionMapper.lockEvent(outboxId);
                if (!ready(event)) return;
                for (FoundationCommandExtension extension : extensions) extension.afterCommit(event);
                event.setStatus("PUBLISHED");
                event.setPublishedAt(LocalDateTime.now());
                event.setLastError(null);
                update(event);
            });
        } catch (RuntimeException exception) {
            // Save failure in a new transaction even when an extension marked delivery rollback-only.
            transactions.executeWithoutResult(transaction -> {
                CoreEventOutbox event = actionMapper.lockEvent(outboxId);
                if (event == null || "PUBLISHED".equals(event.getStatus())) return;
                event.setStatus("FAILED");
                int attempts = (event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1;
                event.setRetryCount(attempts);
                event.setAvailableAt(LocalDateTime.now().plusSeconds(Math.min(300, 1L << Math.min(attempts, 8))));
                String error = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                event.setLastError(error.substring(0, Math.min(1900, error.length())));
                update(event);
            });
        }
    }

    @Scheduled(fixedDelayString = "${unexamine.actions.outbox.poll-delay-ms:1000}")
    public void retryDue() {
        for (CoreEventOutbox event : outboxService.selectList(Wrappers.<CoreEventOutbox>lambdaQuery()
                .in(CoreEventOutbox::getAggregateType, "BUSINESS_ACTION", "BUSINESS_ACTION_COMPLETION", "FOUNDATION_COMMAND")
                .in(CoreEventOutbox::getStatus, "PENDING", "FAILED")
                .le(CoreEventOutbox::getAvailableAt, LocalDateTime.now())
                .orderByAsc(CoreEventOutbox::getId).last("limit 50"))) {
            dispatch(event.getId());
        }
        approvals.getObject().reconcileDue();
    }

    private boolean ready(CoreEventOutbox event) {
        return event != null && ("PENDING".equals(event.getStatus()) || "FAILED".equals(event.getStatus()))
                && !event.getAvailableAt().isAfter(LocalDateTime.now());
    }

    private void update(CoreEventOutbox event) {
        if (outboxService.updateById(event) != 1) throw new IllegalStateException("Outbox version conflict");
    }
}
