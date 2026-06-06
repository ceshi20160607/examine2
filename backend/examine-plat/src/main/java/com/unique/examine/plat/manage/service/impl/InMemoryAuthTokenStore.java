package com.unique.examine.plat.manage.service.impl;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.manage.service.AuthTokenStore;
import org.springframework.stereotype.Service;

/**
 * MVP 内存 token 存储。
 */
@Service
public class InMemoryAuthTokenStore implements AuthTokenStore {

    private static final int TOKEN_BYTES = 48;

    private final ConcurrentMap<String, MutableTokenSession> accessSessions = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, MutableTokenSession> refreshSessions = new ConcurrentHashMap<>();

    private final SecureRandom secureRandom = new SecureRandom();

    private final Duration accessTtl;

    private final Duration refreshTtl;

    private final Clock clock;

    /**
     * 创建默认 MVP token 存储。
     */
    public InMemoryAuthTokenStore() {
        this(Duration.ofHours(2), Duration.ofDays(7), Clock.systemDefaultZone());
    }

    InMemoryAuthTokenStore(Duration accessTtl, Duration refreshTtl, Clock clock) {
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
        this.clock = clock;
    }

    /**
     * 签发新的访问和刷新 token。
     *
     * @param account 平台账号
     * @return token 会话
     */
    @Override
    public TokenSession issue(Account account) {
        MutableTokenSession session = createSession(account);
        accessSessions.put(session.accessToken, session);
        refreshSessions.put(session.refreshToken, session);
        return session.snapshot();
    }

    /**
     * 使用 refreshToken 轮换新 token。
     *
     * @param refreshToken 刷新 token
     * @return token 会话
     */
    @Override
    public Optional<TokenSession> refresh(String refreshToken) {
        MutableTokenSession session = refreshSessions.remove(refreshToken);
        if (session == null || session.refreshTokenExpiresAt.isBefore(now())) {
            return Optional.empty();
        }
        accessSessions.remove(session.accessToken);
        session.accessToken = newToken();
        session.refreshToken = newToken();
        session.accessTokenExpiresAt = now().plus(accessTtl);
        session.refreshTokenExpiresAt = now().plus(refreshTtl);
        accessSessions.put(session.accessToken, session);
        refreshSessions.put(session.refreshToken, session);
        return Optional.of(session.snapshot());
    }

    /**
     * 校验 accessToken。
     *
     * @param accessToken 访问 token
     * @return token 会话
     */
    @Override
    public Optional<TokenSession> validateAccess(String accessToken) {
        MutableTokenSession session = accessSessions.get(accessToken);
        if (session == null) {
            return Optional.empty();
        }
        if (session.accessTokenExpiresAt.isBefore(now())) {
            revokeAccess(accessToken);
            return Optional.empty();
        }
        return Optional.of(session.snapshot());
    }

    /**
     * 撤销 accessToken 对应会话。
     *
     * @param accessToken 访问 token
     */
    @Override
    public void revokeAccess(String accessToken) {
        MutableTokenSession session = accessSessions.remove(accessToken);
        if (session != null) {
            refreshSessions.remove(session.refreshToken);
        }
    }

    private MutableTokenSession createSession(Account account) {
        Instant current = now();
        return new MutableTokenSession(
                account.getId(),
                account.getLoginName(),
                account.getDisplayName(),
                newToken(),
                newToken(),
                current.plus(accessTtl),
                current.plus(refreshTtl));
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static final class MutableTokenSession {

        private final Long accountId;

        private final String loginName;

        private final String displayName;

        private String accessToken;

        private String refreshToken;

        private Instant accessTokenExpiresAt;

        private Instant refreshTokenExpiresAt;

        private MutableTokenSession(Long accountId, String loginName, String displayName, String accessToken,
                String refreshToken, Instant accessTokenExpiresAt, Instant refreshTokenExpiresAt) {
            this.accountId = accountId;
            this.loginName = loginName;
            this.displayName = displayName;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.accessTokenExpiresAt = accessTokenExpiresAt;
            this.refreshTokenExpiresAt = refreshTokenExpiresAt;
        }

        private TokenSession snapshot() {
            return new TokenSession(accountId, loginName, displayName, accessToken, refreshToken,
                    accessTokenExpiresAt, refreshTokenExpiresAt);
        }
    }
}
