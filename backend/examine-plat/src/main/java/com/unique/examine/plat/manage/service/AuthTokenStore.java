package com.unique.examine.plat.manage.service;

import java.time.Instant;
import java.util.Optional;

import com.unique.examine.plat.base.entity.Account;

/**
 * 认证 token 存储边界。
 */
public interface AuthTokenStore {

    /**
     * 签发新的访问和刷新 token。
     *
     * @param account 平台账号
     * @return token 会话
     */
    TokenSession issue(Account account);

    /**
     * 使用 refreshToken 轮换新 token。
     *
     * @param refreshToken 刷新 token
     * @return token 会话
     */
    Optional<TokenSession> refresh(String refreshToken);

    /**
     * 校验 accessToken。
     *
     * @param accessToken 访问 token
     * @return token 会话
     */
    Optional<TokenSession> validateAccess(String accessToken);

    /**
     * 撤销 accessToken 对应会话。
     *
     * @param accessToken 访问 token
     */
    void revokeAccess(String accessToken);

    /**
     * token 会话快照。
     */
    record TokenSession(Long accountId, String loginName, String displayName, String accessToken, String refreshToken,
            Instant accessTokenExpiresAt, Instant refreshTokenExpiresAt) {
    }
}
