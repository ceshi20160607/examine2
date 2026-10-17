package com.unique.unexamine.foundation.manage.event;

/**
 * 通知和自动化能力实现此本地接口。回调可能因投递失败重试，处理同一事件时应保持幂等。
 */
public interface LocalBusinessEventListener {
    void onEvent(LocalBusinessEvent event);
}
