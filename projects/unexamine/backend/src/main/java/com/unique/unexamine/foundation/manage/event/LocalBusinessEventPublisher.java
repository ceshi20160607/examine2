package com.unique.unexamine.foundation.manage.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.foundation.base.entity.CoreEventOutbox;
import com.unique.unexamine.foundation.base.service.CoreEventOutboxBaseService;
import com.unique.unexamine.foundation.manage.control.FoundationExtensionDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

/** 将业务事件随原业务事务写入公共发件箱，提交后由本地接口分发。 */
@Service
public class LocalBusinessEventPublisher {
    public static final String AGGREGATE_TYPE = "LOCAL_BUSINESS_EVENT";
    private static final Logger LOG = LoggerFactory.getLogger(LocalBusinessEventPublisher.class);

    private final CoreEventOutboxBaseService outboxService;
    private final FoundationExtensionDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    public LocalBusinessEventPublisher(CoreEventOutboxBaseService outboxService,
            FoundationExtensionDispatcher dispatcher, ObjectMapper objectMapper) {
        this.outboxService = outboxService;
        this.dispatcher = dispatcher;
        this.objectMapper = objectMapper;
    }

    /**
     * 业务写入成功后调用；事务回滚会同时撤销事件。数据租户从业务对象传入，避免共享操作误记到执行人租户。
     */
    @Transactional
    public Long publish(AuthenticatedContext actor, Long dataTenantId, LocalBusinessEvent.Type type,
            String sourceType, String sourceId, String moduleCode) {
        LocalDateTime now = LocalDateTime.now();
        LocalBusinessEvent fact = new LocalBusinessEvent(type,
                actor.systemId() == null ? "PLATFORM" : "SYSTEM", actor.systemId(), dataTenantId,
                sourceType, sourceId, moduleCode, actor.accountId(), actor.memberId(), now);
        CoreEventOutbox event = new CoreEventOutbox();
        event.setContextType(fact.contextType());
        event.setSystemId(fact.systemId());
        event.setTenantId(fact.tenantId());
        event.setAggregateType(AGGREGATE_TYPE);
        event.setAggregateId(sourceType + ":" + sourceId);
        event.setEventType(type.name());
        try {
            event.setPayloadJson(objectMapper.writeValueAsString(fact));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("本地业务事件无法保存", exception);
        }
        event.setStatus("PENDING");
        event.setAvailableTime(now);
        event.setRetryCount(0);
        event.setVersion(0);
        outboxService.insert(event);
        long eventId = event.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    dispatcher.dispatch(eventId);
                } catch (RuntimeException exception) {
                    // 原业务已提交，未投递事件由公共发件箱扫描重试。
                    LOG.warn("Local business event delivery deferred: {}", eventId, exception);
                }
            }
        });
        return eventId;
    }
}
