package com.unique.unexamine.foundation.manage.control;

import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class RedisRateLimiter {
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redis;
    private final FoundationContextKeyFactory keyFactory;
    private final AuditRecorder auditRecorder;

    public RedisRateLimiter(StringRedisTemplate redis,
                            FoundationContextKeyFactory keyFactory,
                            AuditRecorder auditRecorder) {
        this.redis = redis;
        this.keyFactory = keyFactory;
        this.auditRecorder = auditRecorder;
    }

    public RateLimitState check(AuthenticatedContext context, String applicationCode, String operationCode,
                                int limit, Duration window, String traceId) {
        String key = keyFactory.rateLimitKey(context, applicationCode, operationCode);
        try {
            Long current = redis.execute(INCREMENT_SCRIPT, List.of(key), Long.toString(window.toMillis()));
            if (current == null) throw new RedisConnectionFailureException("Redis returned no rate-limit value");
            long retryAfter = Math.max(1L, redis.getExpire(key, java.util.concurrent.TimeUnit.SECONDS));
            RateLimitState state = new RateLimitState(current, limit, retryAfter, current <= limit, "REDIS");
            if (!state.allowed()) {
                auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                        "FOUNDATION_RATE_LIMIT_DENIED", "RATE_LIMIT", operationCode, "DENIED",
                        Map.of("count", current, "limit", limit, "retryAfterSeconds", retryAfter,
                                "applicationScope", applicationCode == null ? "" : applicationCode));
                throw new DomainException("RATE_LIMIT_EXCEEDED",
                        "请求过于频繁，请在 " + retryAfter + " 秒后重试", HttpStatus.TOO_MANY_REQUESTS,
                        Map.of("retryAfterSeconds", retryAfter, "limit", limit, "current", current));
            }
            return state;
        } catch (DomainException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                    "FOUNDATION_RATE_LIMIT_UNAVAILABLE", "RATE_LIMIT", operationCode, "DENIED",
                    Map.of("failureMode", "FAIL_CLOSED"));
            throw new DomainException("RATE_LIMIT_BACKEND_UNAVAILABLE",
                    "限流状态暂时不可确认，高风险操作已安全拒绝，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE,
                    Map.of("retryAfterSeconds", 5, "failureMode", "FAIL_CLOSED"));
        }
    }

    public RateLimitState current(AuthenticatedContext context, String applicationCode, String operationCode,
                                  int limit) {
        String key = keyFactory.rateLimitKey(context, applicationCode, operationCode);
        try {
            String value = redis.opsForValue().get(key);
            long count = value == null ? 0L : Long.parseLong(value);
            long retryAfter = value == null ? 0L
                    : Math.max(1L, redis.getExpire(key, java.util.concurrent.TimeUnit.SECONDS));
            return new RateLimitState(count, limit, retryAfter, count <= limit, "REDIS");
        } catch (RuntimeException exception) {
            return new RateLimitState(0L, limit, 0L, false, "UNAVAILABLE_FAIL_CLOSED");
        }
    }

    public record RateLimitState(long current, long limit, long retryAfterSeconds,
                                 boolean allowed, String source) {
    }
}
