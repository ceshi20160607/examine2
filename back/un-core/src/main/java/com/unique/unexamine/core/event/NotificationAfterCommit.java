package com.unique.unexamine.core.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 非关键站内通知在业务提交后执行，失败不改变已提交的业务结果。 */
public final class NotificationAfterCommit {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationAfterCommit.class);

    private NotificationAfterCommit() {}

    /** 有事务时只在提交后发送；无事务时立即发送并记录失败。 */
    public static void dispatch(Runnable notification) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { run(notification); }
            });
        } else run(notification);
    }

    private static void run(Runnable notification) {
        try { notification.run(); }
        catch (RuntimeException ex) { LOG.warn("业务已完成，但站内通知投递失败", ex); }
    }
}
