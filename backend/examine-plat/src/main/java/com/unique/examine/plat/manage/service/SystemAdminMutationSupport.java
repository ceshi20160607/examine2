package com.unique.examine.plat.manage.service;

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
import com.unique.examine.plat.api.AuthenticatedSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@Component
public class SystemAdminMutationSupport {
    private final IdempotencyFacade idempotencyFacade;
    private final OperationAuditFacade auditFacade;
    private final OutboxFacade outboxFacade;
    private final ObjectMapper objectMapper;

    public SystemAdminMutationSupport(
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
        var existing = idempotencyFacade.find("SYSTEM_ADMIN", scopeKey, key);
        if (existing.isPresent()) {
            var record = existing.get();
            if (!requestHash.equals(record.requestHash())) {
                throw conflict("IDEMPOTENCY_CONFLICT", "相同幂等键对应了不同请求");
            }
            if (!"COMPLETED".equals(record.status()) || record.responseBody() == null) {
                throw conflict("REQUEST_IN_PROGRESS", "请求正在处理中");
            }
            return read(record.responseBody(), responseType);
        }

        final long recordId;
        try {
            recordId = idempotencyFacade.begin(
                    "SYSTEM_ADMIN", scopeKey, key, requestHash, Duration.ofHours(24)
            );
        } catch (DataIntegrityViolationException exception) {
            throw conflict("REQUEST_IN_PROGRESS", "请求正在处理中");
        }
        var response = mutation.run();
        idempotencyFacade.complete(recordId, 200, "OK", write(response));
        return response;
    }

    public void success(
            AuthenticatedSession session,
            long systemId,
            String aggregateType,
            String aggregateId,
            String action,
            Object before,
            Object after,
            ClientRequest request
    ) {
        auditFacade.recordSuccess(OperationAudit.success(
                actor(session), context(session, systemId),
                new AggregateRef(aggregateType, aggregateId), action,
                before, after, request.requestId(), request.traceId()
        ));
    }

    public void denied(
            Object sessionValue,
            long systemId,
            String aggregateType,
            String aggregateId,
            String action,
            String failureCode,
            ClientRequest request
    ) {
        auditFacade.recordDenied(OperationAudit.denied(
                actor(sessionValue), requestedContext(sessionValue, systemId),
                new AggregateRef(aggregateType, aggregateId), action,
                null, null, new OperationAudit.Failure(failureCode),
                request.requestId(), request.traceId()
        ));
    }

    public void failed(
            Object sessionValue,
            long systemId,
            String aggregateType,
            String aggregateId,
            String action,
            String failureCode,
            ClientRequest request
    ) {
        auditFacade.recordFailed(OperationAudit.failed(
                actor(sessionValue), requestedContext(sessionValue, systemId),
                new AggregateRef(aggregateType, aggregateId), action,
                null, null, new OperationAudit.Failure(failureCode),
                request.requestId(), request.traceId()
        ));
    }

    public void outbox(
            AuthenticatedSession session,
            long systemId,
            String aggregateType,
            String aggregateId,
            String eventType,
            Object payload,
            String dedupeSource,
            ClientRequest request
    ) {
        var dedupeKey = "system-admin:" + sha256(
                systemId + ":" + eventType + ":" + aggregateId + ":" + dedupeSource
        );
        outboxFacade.enqueue(new OutboxEvent(
                eventType, 1, new AggregateRef(aggregateType, aggregateId),
                new OutboxEvent.Context(systemId, session.tenantId()),
                dedupeKey, payload, request.traceId()
        ));
    }

    public static long version(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException("VALIDATION_ERROR", "版本号格式无效", HttpStatus.BAD_REQUEST);
        }
    }

    public static long id(String value, String field) {
        try {
            var id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new BusinessException("VALIDATION_ERROR", field + " 格式无效", HttpStatus.BAD_REQUEST);
        }
    }

    public static BusinessException notFound() {
        return new BusinessException("RESOURCE_NOT_FOUND", "资源不存在", HttpStatus.NOT_FOUND);
    }

    public static BusinessException versionConflict() {
        return conflict("VERSION_CONFLICT", "数据版本已变化，请刷新后重试");
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    public static BusinessException invalidState(String message) {
        return new BusinessException("STATE_TRANSITION_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private OperationAudit.Actor actor(AuthenticatedSession session) {
        return new OperationAudit.Actor(session.accountId(), "WEB");
    }

    private OperationAudit.Actor actor(Object sessionValue) {
        var accountId = sessionValue instanceof AuthenticatedSession session ? session.accountId() : null;
        return new OperationAudit.Actor(accountId, "WEB");
    }

    private OperationAudit.Context context(AuthenticatedSession session, long systemId) {
        return new OperationAudit.Context(ContextType.SYSTEM, systemId, session.tenantId());
    }

    private OperationAudit.Context requestedContext(Object sessionValue, long systemId) {
        Long tenantId = null;
        if (sessionValue instanceof AuthenticatedSession session
                && session.systemId() != null
                && session.systemId() == systemId) {
            tenantId = session.tenantId();
        }
        return new OperationAudit.Context(ContextType.SYSTEM, systemId, tenantId);
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
            throw new IllegalArgumentException("System admin payload must be JSON serializable", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read idempotent system admin response", exception);
        }
    }

    public static String sha256(String value) {
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
