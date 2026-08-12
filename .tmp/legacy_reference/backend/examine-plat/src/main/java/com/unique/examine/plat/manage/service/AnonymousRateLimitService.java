package com.unique.examine.plat.manage.service;

import com.unique.examine.core.error.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AnonymousRateLimitService {
    private final StringRedisTemplate redisTemplate;

    public AnonymousRateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void check(String action, String remoteAddress, int limit) {
        check(action, remoteAddress, limit, Duration.ofMinutes(1));
    }

    public void check(String action, String subject, int limit, Duration window) {
        if (action == null || !action.matches("[a-z0-9-]{1,64}")
                || limit < 1 || window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("A bounded anonymous rate limit is required");
        }
        var key = "examine:rate:" + action + ":" + safe(subject);
        var count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, window);
        }
        if (count != null && count > limit) {
            throw new BusinessException("RATE_LIMITED", "操作过于频繁，请稍后重试", HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    private static String safe(String value) {
        return value == null ? "unknown" : value.replaceAll("[^0-9A-Fa-f:.]", "_");
    }
}
