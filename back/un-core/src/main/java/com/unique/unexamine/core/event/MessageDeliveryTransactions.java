package com.unique.unexamine.core.event;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 消息预留、实际写入和失败记录分别提交，避免一次写入回滚丢失重试依据。 */
@Component
public class MessageDeliveryTransactions {
    private final TransactionTemplate transaction;

    public MessageDeliveryTransactions(PlatformTransactionManager manager) {
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 在独立事务内执行一个投递阶段。 */
    public <T> T execute(Supplier<T> action) {
        return transaction.execute(status -> action.get());
    }
}
