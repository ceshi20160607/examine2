package com.unique.unexamine.core.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 核对通知只在提交后执行，投递异常不会改写业务动作结果。 */
class NotificationAfterCommitTest {
    @Test
    void waitsForCommitAndIsolatesDeliveryFailure() {
        AtomicInteger delivered = new AtomicInteger();
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            NotificationAfterCommit.dispatch(delivered::incrementAndGet);
            assertEquals(0, delivered.get());
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations())
                synchronization.afterCommit();
            assertEquals(1, delivered.get());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertDoesNotThrow(() -> NotificationAfterCommit.dispatch(() -> {
            throw new IllegalStateException("模拟消息服务失败");
        }));
    }
}
