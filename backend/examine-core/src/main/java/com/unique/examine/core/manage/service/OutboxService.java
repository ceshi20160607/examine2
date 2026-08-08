package com.unique.examine.core.manage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.OutboxEvent;
import com.unique.examine.core.api.OutboxFacade;
import com.unique.examine.core.base.mapper.CoreOutboxEventMapper;
import com.unique.examine.core.id.IdService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class OutboxService implements OutboxFacade {
    private final CoreOutboxEventMapper mapper;
    private final IdService idService;
    private final ObjectMapper objectMapper;

    public OutboxService(CoreOutboxEventMapper mapper, IdService idService, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.idService = idService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long enqueue(OutboxEvent event) {
        Objects.requireNonNull(event, "event is required");
        var now = LocalDateTime.now();
        var entity = new com.unique.examine.core.base.entity.OutboxEvent();
        entity.setId(idService.nextId());
        entity.setEventType(event.eventType());
        entity.setEventVersion(event.eventVersion());
        entity.setAggregateType(event.aggregate().type());
        entity.setAggregateId(event.aggregate().id());
        entity.setSystemId(event.context().systemId());
        entity.setTenantId(event.context().tenantId());
        entity.setDedupeKey(event.idempotencyKey());
        entity.setPayloadJson(writePayload(event.payload()));
        entity.setTraceId(event.traceId());
        entity.setStatus("PENDING");
        entity.setAvailableAt(now);
        entity.setAttemptCount(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        mapper.insert(entity);
        return entity.getId();
    }

    private String writePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Outbox payload must be JSON serializable", exception);
        }
    }
}
