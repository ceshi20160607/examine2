package com.unique.examine.plat.manage.auth;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Redis-backed authentication token service.
 */
@Service
public class AuthTokenService {

    private static final String ACCESS_PREFIX = "unexamine:auth:access:";
    private static final String REFRESH_PREFIX = "unexamine:auth:refresh:";

    private final StringRedisTemplate redisTemplate;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public AuthTokenService(StringRedisTemplate redisTemplate,
                            @Value("${unexamine.security.access-token-ttl:2h}") Duration accessTokenTtl,
                            @Value("${unexamine.security.refresh-token-ttl:7d}") Duration refreshTokenTtl) {
        this.redisTemplate = redisTemplate;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    /**
     * Issue a new access/refresh token pair for an account.
     *
     * @param accountId account id
     * @return token pair
     */
    public TokenPair issueTokens(Long accountId) {
        if (Objects.isNull(accountId)) {
            throw new BusinessException(CommonErrorCode.AUTH_UNAUTHORIZED);
        }
        String accessToken = "access_" + randomToken();
        String refreshToken = "refresh_" + randomToken();
        saveToken(ACCESS_PREFIX + accessToken, accountId, accessTokenTtl);
        saveToken(REFRESH_PREFIX + refreshToken, accountId, refreshTokenTtl);
        return new TokenPair(accessToken, refreshToken);
    }

    /**
     * Rotate a valid refresh token.
     *
     * @param refreshToken refresh token
     * @return new token pair
     */
    public TokenPair rotateRefreshToken(String refreshToken) {
        Long accountId = resolveStoredAccountId(REFRESH_PREFIX, refreshToken);
        if (Objects.isNull(accountId)) {
            throw new BusinessException(AuthErrorCode.BAD_CREDENTIALS, "刷新令牌无效或已过期");
        }
        redisTemplate.delete(REFRESH_PREFIX + refreshToken);
        return issueTokens(accountId);
    }

    /**
     * Resolve account id from an access token.
     *
     * @param accessToken access token or bearer header
     * @return account id, or null when token is missing/invalid
     */
    public Long resolveAccessToken(String accessToken) {
        return resolveStoredAccountId(ACCESS_PREFIX, normalizeToken(accessToken));
    }

    /**
     * Revoke the current access token when present.
     */
    public void revokeCurrentAccessToken() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (Objects.isNull(attributes)) {
            return;
        }
        String token = bearerToken(attributes.getRequest());
        if (StringUtils.hasText(token)) {
            redisTemplate.delete(ACCESS_PREFIX + token);
        }
    }

    /**
     * Extract bearer token from the current HTTP request.
     *
     * @param request HTTP request
     * @return token without Bearer prefix
     */
    public static String bearerToken(HttpServletRequest request) {
        if (Objects.isNull(request)) {
            return null;
        }
        return normalizeToken(request.getHeader("Authorization"));
    }

    private Long resolveStoredAccountId(String prefix, String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String value = redisTemplate.opsForValue().get(prefix + token);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void saveToken(String key, Long accountId, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, String.valueOf(accountId), ttl);
        } catch (RuntimeException ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "登录会话存储不可用");
        }
    }

    private static String normalizeToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String trimmed = token.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            return trimmed.substring("Bearer ".length()).trim();
        }
        return trimmed;
    }

    private static String randomToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public record TokenPair(String accessToken, String refreshToken) {
    }
}
