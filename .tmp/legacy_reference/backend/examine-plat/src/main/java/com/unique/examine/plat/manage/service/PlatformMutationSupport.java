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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.function.Supplier;

@Component
public class PlatformMutationSupport {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final IdempotencyFacade idempotencyFacade;
    private final OperationAuditFacade auditFacade;
    private final OutboxFacade outboxFacade;
    private final ObjectMapper objectMapper;

    public PlatformMutationSupport(
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
            long actorAccountId,
            String operation,
            String target,
            String key,
            Object request,
            Class<T> responseType,
            Supplier<T> mutation
    ) {
        requireIdempotencyKey(key);
        var scopeKey = actorAccountId + ":" + operation + ":" + target;
        var requestHash = sha256(operation + "|" + target + "|" + write(request));
        var existing = idempotencyFacade.find("PLATFORM_ADMIN", scopeKey, key);
        if (existing.isPresent()) {
            var record = existing.get();
            if (!requestHash.equals(record.requestHash())) {
                throw new BusinessException(
                        "IDEMPOTENCY_CONFLICT",
                        "相同幂等键对应了不同请求",
                        HttpStatus.CONFLICT
                );
            }
            if (!"COMPLETED".equals(record.status()) || record.responseBody() == null) {
                throw new BusinessException("REQUEST_IN_PROGRESS", "请求正在处理中", HttpStatus.CONFLICT);
            }
            return read(record.responseBody(), responseType);
        }

        var id = idempotencyFacade.begin(
                "PLATFORM_ADMIN", scopeKey, key, requestHash, IDEMPOTENCY_TTL
        );
        var response = mutation.get();
        idempotencyFacade.complete(id, 200, "OK", write(response));
        return response;
    }

    public void success(
            AuthenticatedSession session,
            ClientRequest client,
            String aggregateType,
            String aggregateId,
            String action,
            Object before,
            Object after,
            String idempotencyKey
    ) {
        var aggregate = new AggregateRef(aggregateType, aggregateId);
        auditFacade.recordSuccess(OperationAudit.success(
                new OperationAudit.Actor(session.accountId(), "WEB"),
                new OperationAudit.Context(ContextType.PLATFORM, null, null),
                aggregate,
                action,
                before,
                after,
                client.requestId(),
                client.traceId()
        ));

        var payload = new LinkedHashMap<String, Object>();
        payload.put("action", action);
        payload.put("actorAccountId", Long.toString(session.accountId()));
        payload.put("before", before);
        payload.put("after", after);
        payload.put("requestId", client.requestId());
        outboxFacade.enqueue(new OutboxEvent(
                action,
                1,
                aggregate,
                new OutboxEvent.Context(null, null),
                sha256(action + "|" + (idempotencyKey == null ? client.requestId() : idempotencyKey)),
                payload,
                client.traceId()
        ));
    }

    public static long id(String value) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException("not positive");
            }
            return parsed;
        } catch (RuntimeException exception) {
            throw new BusinessException("RESOURCE_NOT_FOUND", "资源不存在", HttpStatus.NOT_FOUND);
        }
    }

    public static long version(String value) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed < 0) {
                throw new NumberFormatException("negative");
            }
            return parsed;
        } catch (RuntimeException exception) {
            throw new BusinessException("VERSION_CONFLICT", "版本已变化，请刷新后重试", HttpStatus.CONFLICT);
        }
    }

    public static int page(Integer value) {
        if (value == null) {
            return 1;
        }
        if (value < 1) {
            throw validation("page 必须大于等于 1");
        }
        return value;
    }

    public static int size(Integer value) {
        if (value == null) {
            return 20;
        }
        if (value < 1 || value > 500) {
            throw validation("size 必须在 1 到 500 之间");
        }
        return value;
    }

    public static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw validation(field + " 不能为空且长度不能超过 " + maxLength);
        }
        return value.trim();
    }

    public static String optional(String value, String field, int maxLength) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw validation(field + " 长度不能超过 " + maxLength);
        }
        return trimmed;
    }

    public static BusinessException validation(String message) {
        return new BusinessException("VALIDATION_ERROR", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static BusinessException notFound(String resource) {
        return new BusinessException("RESOURCE_NOT_FOUND", resource + "不存在", HttpStatus.NOT_FOUND);
    }

    public static BusinessException versionConflict() {
        return new BusinessException("VERSION_CONFLICT", "版本已变化，请刷新后重试", HttpStatus.CONFLICT);
    }

    private void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new BusinessException(
                    "IDEMPOTENCY_KEY_REQUIRED",
                    "缺少有效的 Idempotency-Key",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize platform mutation state", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read platform mutation state", exception);
        }
    }

    static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
