package com.unique.unexamine.foundation.manage.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.foundation.base.entity.CoreEventOutbox;
import com.unique.unexamine.foundation.manage.control.FoundationCommandExtension;
import org.springframework.stereotype.Component;

import java.util.List;

/** 在公共发件箱投递事务中调用本地订阅者，不依赖外部消息队列。 */
@Component
public class LocalBusinessEventExtension implements FoundationCommandExtension {
    private final ObjectMapper objectMapper;
    private final List<LocalBusinessEventListener> listeners;

    public LocalBusinessEventExtension(ObjectMapper objectMapper, List<LocalBusinessEventListener> listeners) {
        this.objectMapper = objectMapper;
        this.listeners = listeners;
    }

    @Override
    public void afterCommit(CoreEventOutbox event) {
        if (!LocalBusinessEventPublisher.AGGREGATE_TYPE.equals(event.getAggregateType())) return;
        try {
            LocalBusinessEvent fact = objectMapper.readValue(event.getPayloadJson(), LocalBusinessEvent.class);
            for (LocalBusinessEventListener listener : listeners) listener.onEvent(fact);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("本地业务事件无法读取", exception);
        }
    }
}
