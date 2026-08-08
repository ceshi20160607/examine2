package com.unique.examine.module.manage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.api.OutboxEvent;
import com.unique.examine.core.api.OutboxFacade;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.manage.security.ConfigSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

@Component
public class ConfigMutationSupport {
    private static final String IDEMPOTENCY_SCOPE = "MODULE_CONFIG";
    private final IdempotencyFacade idempotencyFacade;
    private final OperationAuditFacade auditFacade;
    private final OutboxFacade outboxFacade;
    private final ObjectMapper objectMapper;

    public ConfigMutationSupport(
            IdempotencyFacade idempotencyFacade,
            OperationAuditFacade auditFacade,
            OutboxFacade outboxFacade,
            ObjectMapper objectMapper
    ) {
        this.idempotencyFacade = idempotencyFacade;
        this.auditFacade = auditFacade;
        this.outboxFacade = outboxFacade;
        this.objectMapper = objectMapper;
    }

    public <T> T idempotent(
            String scopeKey,
            String key,
            Object request,
            Class<T> responseType,
            Mutation<T> mutation
    ) {
        requireIdempotencyKey(key);
        var requestHash = sha256(write(request));
        var existing = idempotencyFacade.find(IDEMPOTENCY_SCOPE, scopeKey, key);
        if (existing.isPresent()) {
            var record = existing.get();
            if (!requestHash.equals(record.requestHash())) {
                throw ConfigErrors.conflict("IDEMPOTENCY_CONFLICT", "相同幂等键对应了不同请求");
            }
            if (!"COMPLETED".equals(record.status()) || record.responseBody() == null) {
                throw ConfigErrors.conflict("REQUEST_IN_PROGRESS", "请求正在处理中");
            }
            return read(record.responseBody(), responseType);
        }
        final long recordId;
        try {
            recordId = idempotencyFacade.begin(
                    IDEMPOTENCY_SCOPE, scopeKey, key, requestHash, Duration.ofHours(24)
            );
        } catch (DataIntegrityViolationException exception) {
            throw ConfigErrors.conflict("REQUEST_IN_PROGRESS", "请求正在处理中");
        }
        var response = mutation.run();
        idempotencyFacade.complete(recordId, 200, "OK", write(response));
        return response;
    }

    public void changed(
            ConfigSession session,
            String aggregateType,
            String aggregateId,
            String action,
            Object before,
            Object after,
            long draftRevision,
            String dedupeSource,
            RequestContext request
    ) {
        var aggregate = new AggregateRef(aggregateType, aggregateId);
        auditFacade.recordSuccess(OperationAudit.success(
                new OperationAudit.Actor(session.accountId(), "WEB"),
                new OperationAudit.Context(ContextType.SYSTEM, session.systemId(), session.tenantId()),
                aggregate, action, before, after, request.requestId(), request.traceId()
        ));
        outboxFacade.enqueue(new OutboxEvent(
                "MODULE_CONFIG_DRAFT_CHANGED", 1, aggregate,
                new OutboxEvent.Context(session.systemId(), session.tenantId()),
                "module-config:" + sha256(session.systemId() + ":" + aggregateType + ":" + aggregateId
                        + ":" + draftRevision + ":" + dedupeSource),
                Map.of(
                        "systemId", Long.toString(session.systemId()),
                        "resourceType", aggregateType,
                        "resourceId", aggregateId,
                        "action", action,
                        "draftRevision", Long.toString(draftRevision)
                ),
                request.traceId()
        ));
    }

    private void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new BusinessException(
                    "IDEMPOTENCY_KEY_REQUIRED", "缺少有效的 Idempotency-Key", HttpStatus.BAD_REQUEST
            );
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Module config payload must be JSON serializable", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read idempotent module config response", exception);
        }
    }

    static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @FunctionalInterface
    public interface Mutation<T> {
        T run();
    }
}
