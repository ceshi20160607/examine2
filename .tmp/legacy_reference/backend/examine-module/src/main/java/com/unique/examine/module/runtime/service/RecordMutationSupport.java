package com.unique.examine.module.runtime.service;

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
import com.unique.examine.module.runtime.history.RecordHistoryWriter;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

@Component
public class RecordMutationSupport {
    private static final String IDEMPOTENCY_SCOPE = "RECORD_RUNTIME";
    private final IdempotencyFacade idempotency;
    private final OperationAuditFacade audit;
    private final OutboxFacade outbox;
    private final ObjectMapper objectMapper;
    private final RecordHistoryWriter history;

    public RecordMutationSupport(
            IdempotencyFacade idempotency,
            OperationAuditFacade audit,
            OutboxFacade outbox,
            ObjectMapper objectMapper,
            RecordHistoryWriter history
    ) {
        this.idempotency = idempotency;
        this.audit = audit;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.history = history;
    }

    public <T> T idempotent(
            String scopeKey,
            String key,
            Object request,
            Class<T> responseType,
            int successStatus,
            Mutation<T> mutation
    ) {
        requireKey(key);
        var requestHash = sha256(write(request));
        var existing = idempotency.find(IDEMPOTENCY_SCOPE, scopeKey, key);
        if (existing.isPresent()) {
            var record = existing.get();
            if (!requestHash.equals(record.requestHash())) {
                throw conflict("IDEMPOTENCY_CONFLICT", "同一个幂等键不能用于不同请求");
            }
            if (!"COMPLETED".equals(record.status()) || record.responseBody() == null) {
                throw conflict("REQUEST_IN_PROGRESS", "请求正在处理中，请稍后重试");
            }
            return read(record.responseBody(), responseType);
        }
        final long id;
        try {
            id = idempotency.begin(IDEMPOTENCY_SCOPE, scopeKey, key, requestHash, Duration.ofHours(24));
        } catch (DataIntegrityViolationException exception) {
            throw conflict("REQUEST_IN_PROGRESS", "请求正在处理中，请稍后重试");
        }
        var response = mutation.run();
        idempotency.complete(id, successStatus, "OK", write(response));
        return response;
    }

    public void changed(
            RuntimeSession session,
            long recordId,
            long recordVersion,
            String action,
            Object before,
            Object after,
            String requestId,
            String traceId
    ) {
        changed(session, recordId, recordVersion, action, before, after, requestId, traceId, Set.of());
    }

    public void changed(
            RuntimeSession session,
            long recordId,
            long recordVersion,
            String action,
            Object before,
            Object after,
            String requestId,
            String traceId,
            Set<String> forceMaskedFields
    ) {
        changed(session, recordId, recordVersion, action, before, after, requestId, traceId,
                forceMaskedFields, "WEB");
    }

    public void changed(
            RuntimeSession session,
            long recordId,
            long recordVersion,
            String action,
            Object before,
            Object after,
            String requestId,
            String traceId,
            Set<String> forceMaskedFields,
            String auditSource
    ) {
        if (!Set.of("WEB", "OPENAPI").contains(auditSource)) {
            throw new IllegalArgumentException("Unsupported record audit source: " + auditSource);
        }
        history.append(session, recordId, recordVersion, action, before, after, forceMaskedFields);
        var aggregate = new AggregateRef("RUNTIME_RECORD", Long.toString(recordId));
        audit.recordSuccess(OperationAudit.success(
                new OperationAudit.Actor(auditAccountId(session), auditSource),
                new OperationAudit.Context(ContextType.SYSTEM, session.systemId(), session.tenantId()),
                aggregate, action, before, after, requestId, traceId
        ));
        outbox.enqueue(new OutboxEvent(
                "RUNTIME_RECORD_CHANGED", 1, aggregate,
                new OutboxEvent.Context(session.systemId(), session.tenantId()),
                "runtime-record:" + session.systemId() + ":" + recordId + ":" + recordVersion + ":" + action,
                Map.of(
                        "systemId", Long.toString(session.systemId()),
                        "tenantId", Long.toString(session.tenantId()),
                        "recordId", Long.toString(recordId),
                        "recordVersion", Long.toString(recordVersion),
                        "action", action
                ),
                traceId
        ));
    }

    public void fieldChanged(
            RuntimeSession session,
            long recordId,
            long recordVersion,
            String action,
            String fieldCode,
            Object before,
            Object after,
            String requestId,
            String traceId
    ) {
        fieldChanged(
                session, recordId, recordVersion, action, fieldCode,
                before, after, "WEB", requestId, traceId);
    }

    public void fieldChanged(
            RuntimeSession session,
            long recordId,
            long recordVersion,
            String action,
            String fieldCode,
            Object before,
            Object after,
            String auditSource,
            String requestId,
            String traceId
    ) {
        if (!Set.of("WEB", "OPENAPI").contains(auditSource)) {
            throw new IllegalArgumentException("Unsupported record field audit source");
        }
        history.appendField(
                session,
                recordId,
                recordVersion,
                action,
                fieldCode,
                before,
                after);
        var aggregate = new AggregateRef("RUNTIME_RECORD", Long.toString(recordId));
        audit.recordSuccess(OperationAudit.success(
                new OperationAudit.Actor(auditAccountId(session), auditSource),
                new OperationAudit.Context(ContextType.SYSTEM, session.systemId(), session.tenantId()),
                aggregate, action, before, after, requestId, traceId
        ));
        outbox.enqueue(new OutboxEvent(
                "RUNTIME_RECORD_CHANGED", 1, aggregate,
                new OutboxEvent.Context(session.systemId(), session.tenantId()),
                "runtime-record:" + session.systemId() + ":" + recordId + ":" + recordVersion + ":" + action,
                Map.of(
                        "systemId", Long.toString(session.systemId()),
                        "tenantId", Long.toString(session.tenantId()),
                        "recordId", Long.toString(recordId),
                        "recordVersion", Long.toString(recordVersion),
                        "action", action
                ),
                traceId
        ));
    }

    public void systemChanged(
            long systemId,
            long tenantId,
            long recordId,
            long recordVersion,
            String action,
            Object before,
            Object after,
            String requestId,
            String traceId
    ) {
        history.appendSystem(systemId, tenantId, recordId, recordVersion, action, before, after);
        var aggregate = new AggregateRef("RUNTIME_RECORD", Long.toString(recordId));
        audit.recordSuccess(OperationAudit.success(
                new OperationAudit.Actor(null, "SYSTEM"),
                new OperationAudit.Context(ContextType.SYSTEM, systemId, tenantId),
                aggregate, action, before, after, requestId, traceId
        ));
        outbox.enqueue(new OutboxEvent(
                "RUNTIME_RECORD_CHANGED", 1, aggregate,
                new OutboxEvent.Context(systemId, tenantId),
                "runtime-record:" + systemId + ":" + recordId + ":" + recordVersion + ":" + action,
                Map.of(
                        "systemId", Long.toString(systemId),
                        "tenantId", Long.toString(tenantId),
                        "recordId", Long.toString(recordId),
                        "recordVersion", Long.toString(recordVersion),
                        "action", action
                ),
                traceId
        ));
    }

    public void referenceRetryRequested(
            RuntimeSession session,
            long recordId,
            long recordVersion,
            long sourceRecordId,
            long sourceRecordVersion,
            String correlationId,
            String requestId,
            String traceId
    ) {
        var aggregate = new AggregateRef("RUNTIME_RECORD", Long.toString(recordId));
        var detail = Map.of(
                "recordVersion", Long.toString(recordVersion),
                "sourceRecordId", Long.toString(sourceRecordId),
                "sourceRecordVersion", Long.toString(sourceRecordVersion),
                "correlationId", correlationId
        );
        audit.recordSuccess(OperationAudit.success(
                new OperationAudit.Actor(session.accountId(), "WEB"),
                new OperationAudit.Context(ContextType.SYSTEM, session.systemId(), session.tenantId()),
                aggregate, "REFERENCE_RECALCULATION_RETRIED", null, detail, requestId, traceId
        ));
        outbox.enqueue(new OutboxEvent(
                "REFERENCE_RECALCULATION_REQUESTED", 1, aggregate,
                new OutboxEvent.Context(session.systemId(), session.tenantId()),
                "runtime-reference-retry:" + session.systemId() + ":" + recordId + ":" + correlationId,
                detail,
                traceId
        ));
    }

    private void requireKey(String key) {
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
            throw new IllegalArgumentException("Runtime record payload must be JSON serializable", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read idempotent runtime record response", exception);
        }
    }

    private static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    private static Long auditAccountId(RuntimeSession session) {
        // Some owner ports authenticate a system member without transporting
        // the platform account identity. Record history still records the
        // authoritative member; the generic operation actor is nullable by
        // contract and must never be populated with a member id by guesswork.
        return session.accountId() > 0 ? session.accountId() : null;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @FunctionalInterface
    public interface Mutation<T> {
        T run();
    }
}
