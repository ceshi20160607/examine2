package com.unique.unexamine.core.event;

/**
 * 当前进程内的业务事件监听器。
 *
 * <p>实现类按实际业务扩展点注册为 Spring Bean。方法抛出异常时由调用方事务统一回滚，
 * 不允许捕获异常后静默继续。</p>
 */
@FunctionalInterface
public interface BusinessEventListener {

    /** 处理已经完成参数校验、但仍处于当前业务事务中的事件。 */
    void onEvent(BusinessEventContext context);
}
