package com.unique.examine.web.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.web.ApiResult;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 开放 API 幂等：{@code Idempotency-Key} + clientId + method + path。
 * <p>
 * 成功响应（code=0）会缓存 24h；重放时设置响应头 {@code X-Idempotency-Replay: 1}。
 * </p>
 */
@Service
public class OpenApiIdempotencyService {

    private static final String KEY_PREFIX = "openapi:idem:v1:";
    private static final int TTL_HOURS = 24;

    public static final String HDR_REPLAY = "X-Idempotency-Replay";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    public <T> ApiResult<T> execute(HttpServletResponse response,
                                    Long clientId,
                                    String method,
                                    String path,
                                    String idempotencyKey,
                                    Supplier<ApiResult<T>> action) {
        if (clientId == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }
        String key = redisKey(clientId, method, path, idempotencyKey.trim());
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                ApiResult<T> r = objectMapper.readValue(cached, new TypeReference<>() {});
                response.setHeader(HDR_REPLAY, "1");
                return r;
            } catch (Exception ignore) {
                // fall through: recompute
            }
        }

        ApiResult<T> result = action.get();
        if (result != null && result.getCode() == 0) {
            try {
                stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result), TTL_HOURS, TimeUnit.HOURS);
            } catch (Exception ignore) {
                // ignore cache failures
            }
        }
        return result;
    }

    private static String redisKey(Long clientId, String method, String path, String idem) {
        String raw = clientId + "|" + method + "|" + path + "|" + idem;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return KEY_PREFIX + Integer.toHexString(raw.hashCode());
        }
    }
}
