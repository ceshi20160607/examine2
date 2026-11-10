package com.unique.unexamine.core.event;

import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 本地同步业务事件发布器。
 *
 * <p>使用 Spring 注入的监听器列表维持统一扩展入口；不创建线程、不使用消息队列，
 * 因而监听器对当前事务的成功或失败保持明确的传播关系。</p>
 */
@Component
public class BusinessEventPublisher {
    private final List<BusinessEventListener> listeners;

    public BusinessEventPublisher(List<BusinessEventListener> listeners) {
        this.listeners = listeners == null ? List.of() : List.copyOf(listeners);
    }

    /** 按注册顺序同步调用所有本地监听器。 */
    public void publish(BusinessEventContext context) {
        if (context == null || context.eventCode() == null || context.eventCode().isBlank()) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "业务事件上下文无效");
        }
        for (BusinessEventListener listener : listeners) {
            listener.onEvent(context);
        }
    }
}
