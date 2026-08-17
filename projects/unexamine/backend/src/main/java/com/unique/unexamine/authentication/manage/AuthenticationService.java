package com.unique.unexamine.authentication.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.authentication.base.entity.AuthenticationSession;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.entity.PlatformAccountCredential;
import com.unique.unexamine.authentication.base.service.AuthenticationSessionBaseService;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.platform.base.service.PlatformAccountCredentialBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authorization.manage.ResolvedPermissions;
import com.unique.unexamine.authorization.manage.DataScopeExpression;
import com.unique.unexamine.authorization.manage.PermissionGrant;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AuthenticationService {
    private final PlatformAccountBaseService accountService;
    private final PlatformAccountCredentialBaseService credentialService;
    private final AuthenticationSessionBaseService sessionService;
    private final Pbkdf2PasswordHasher passwordHasher;
    private final TokenFactory tokenFactory;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int accessMinutes;
    private final int refreshDays;
    private final String dummyPasswordHash;

    public AuthenticationService(
            PlatformAccountBaseService accountService,
            PlatformAccountCredentialBaseService credentialService,
            AuthenticationSessionBaseService sessionService,
            Pbkdf2PasswordHasher passwordHasher,
            TokenFactory tokenFactory,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper,
            @Value("${app.authentication.access-minutes:30}") int accessMinutes,
            @Value("${app.authentication.refresh-days:14}") int refreshDays) {
        this.accountService = accountService;
        this.credentialService = credentialService;
        this.sessionService = sessionService;
        this.passwordHasher = passwordHasher;
        this.tokenFactory = tokenFactory;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.clock = Clock.systemUTC();
        this.accessMinutes = accessMinutes;
        this.refreshDays = refreshDays;
        this.dummyPasswordHash = passwordHasher.hash("not-a-real-account-password".toCharArray());
    }

    @Transactional(noRollbackFor = DomainException.class)
    public SessionTokens login(String username, char[] password, String traceId) {
        PlatformAccount account = findAccount(username);
        PlatformAccountCredential credential = account == null ? null : findCredential(account.getId());
        String candidateHash = credential == null ? dummyPasswordHash : credential.getPasswordHash();
        boolean passwordMatches = passwordHasher.matches(password, candidateHash);
        if (account == null || credential == null || !passwordMatches) {
            auditRecorder.recordFailure(traceId, account == null ? null : account.getId(), "AUTH_LOGIN", "AUTHENTICATION_FAILED",
                    Map.of("reason", "INVALID_CREDENTIALS"));
            throw new DomainException("AUTHENTICATION_FAILED", "用户名或密码错误", HttpStatus.UNAUTHORIZED);
        }
        if (!"ACTIVE".equals(account.getStatus())) {
            revokeAll(account.getId());
            auditRecorder.recordFailure(traceId, account.getId(), "AUTH_LOGIN", "ACCOUNT_DISABLED",
                    Map.of("reason", "ACCOUNT_DISABLED"));
            throw new DomainException("ACCOUNT_DISABLED", "账号已停用", HttpStatus.FORBIDDEN);
        }

        SessionTokens tokens = createSession(account.getId(), null, null, null, "[]", "[]", "{}");
        auditRecorder.record(traceId, account.getId(), null, null, null, "AUTH_LOGIN", "PLATFORM_ACCOUNT",
                account.getId().toString(), "SUCCESS", Map.of("context", "PLATFORM"));
        return tokens;
    }

    @Transactional
    public SessionTokens createSystemSession(
            Long accountId,
            Long systemId,
            Long tenantId,
            Long memberId,
            ResolvedPermissions permissions) {
        return createSession(accountId, systemId, tenantId, memberId,
                toJson(permissions.roleIds()), toJson(permissions.permissions()), toJson(permissions.dataScopes()));
    }

    @Transactional(noRollbackFor = DomainException.class)
    public AuthenticatedContext authenticateAccessToken(String rawToken, String traceId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<AuthenticationSession> sessions = sessionService.selectList(Wrappers.<AuthenticationSession>lambdaQuery()
                .eq(AuthenticationSession::getAccessTokenHash, tokenFactory.hash(rawToken))
                .eq(AuthenticationSession::getRevoked, false)
                .gt(AuthenticationSession::getAccessExpiresAt, now));
        if (sessions.isEmpty()) {
            auditRecorder.recordFailure(traceId, null, "AUTH_ACCESS", "AUTHENTICATION_REQUIRED",
                    Map.of("reason", "TOKEN_INVALID_OR_EXPIRED"));
            throw new DomainException("AUTHENTICATION_REQUIRED", "登录状态无效或已过期", HttpStatus.UNAUTHORIZED);
        }
        AuthenticationSession session = sessions.getFirst();
        PlatformAccount account = accountService.selectById(session.getAccountId());
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            session.setRevoked(true);
            sessionService.updateById(session);
            auditRecorder.record(traceId, session.getAccountId(), session.getSystemId(), session.getTenantId(), session.getMemberId(),
                    "AUTH_ACCESS", "AUTH_SESSION", session.getId().toString(), "ACCOUNT_DISABLED",
                    Map.of("reason", "ACCOUNT_DISABLED"));
            throw new DomainException("ACCOUNT_DISABLED", "账号已停用", HttpStatus.FORBIDDEN);
        }
        return new AuthenticatedContext(session.getId(), account.getId(), session.getSystemId(), session.getTenantId(),
                session.getMemberId(), account.getUsername(), account.getDisplayName(),
                readJson(session.getRoleIdsJson(), new TypeReference<List<Long>>() {}),
                readJson(session.getActionPermissionsJson(), new TypeReference<List<PermissionGrant>>() {}),
                readJson(session.getDataScopesJson(), new TypeReference<Map<String, DataScopeExpression>>() {}));
    }

    @Transactional(noRollbackFor = DomainException.class)
    public SessionTokens refresh(String rawRefreshToken, String traceId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<AuthenticationSession> sessions = sessionService.selectList(Wrappers.<AuthenticationSession>lambdaQuery()
                .eq(AuthenticationSession::getRefreshTokenHash, tokenFactory.hash(rawRefreshToken))
                .eq(AuthenticationSession::getRevoked, false)
                .gt(AuthenticationSession::getRefreshExpiresAt, now));
        if (sessions.isEmpty()) {
            throw new DomainException("REFRESH_TOKEN_INVALID", "刷新登录状态失败", HttpStatus.UNAUTHORIZED);
        }
        AuthenticationSession session = sessions.getFirst();
        PlatformAccount account = accountService.selectById(session.getAccountId());
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            session.setRevoked(true);
            sessionService.updateById(session);
            auditRecorder.recordFailure(traceId, session.getAccountId(), "AUTH_REFRESH", "ACCOUNT_DISABLED",
                    Map.of("reason", "ACCOUNT_DISABLED"));
            throw new DomainException("ACCOUNT_DISABLED", "账号已停用", HttpStatus.FORBIDDEN);
        }
        String accessToken = tokenFactory.create();
        String refreshToken = tokenFactory.create();
        LocalDateTime accessExpiresAt = now.plusMinutes(accessMinutes);
        LocalDateTime refreshExpiresAt = now.plusDays(refreshDays);
        session.setAccessTokenHash(tokenFactory.hash(accessToken));
        session.setRefreshTokenHash(tokenFactory.hash(refreshToken));
        session.setAccessExpiresAt(accessExpiresAt);
        session.setRefreshExpiresAt(refreshExpiresAt);
        sessionService.updateById(session);
        auditRecorder.record(traceId, account.getId(), session.getSystemId(), session.getTenantId(), session.getMemberId(),
                "AUTH_REFRESH", "AUTH_SESSION", session.getId().toString(), "SUCCESS", Map.of("rotated", true));
        return new SessionTokens(accessToken, refreshToken, accessExpiresAt, refreshExpiresAt);
    }

    @Transactional
    public void logout(String rawAccessToken, String traceId) {
        List<AuthenticationSession> sessions = sessionService.selectList(Wrappers.<AuthenticationSession>lambdaQuery()
                .eq(AuthenticationSession::getAccessTokenHash, tokenFactory.hash(rawAccessToken)));
        if (sessions.isEmpty()) {
            return;
        }
        AuthenticationSession session = sessions.getFirst();
        session.setRevoked(true);
        sessionService.updateById(session);
        auditRecorder.record(traceId, session.getAccountId(), session.getSystemId(), session.getTenantId(), session.getMemberId(),
                "AUTH_LOGOUT", "AUTH_SESSION", session.getId().toString(), "SUCCESS", Map.of());
    }

    private SessionTokens createSession(
            Long accountId,
            Long systemId,
            Long tenantId,
            Long memberId,
            String roleIds,
            String actionPermissions,
            String dataScopes) {
        LocalDateTime now = LocalDateTime.now(clock);
        String accessToken = tokenFactory.create();
        String refreshToken = tokenFactory.create();
        AuthenticationSession session = new AuthenticationSession();
        session.setAccessTokenHash(tokenFactory.hash(accessToken));
        session.setRefreshTokenHash(tokenFactory.hash(refreshToken));
        session.setAccountId(accountId);
        session.setSystemId(systemId);
        session.setTenantId(tenantId);
        session.setMemberId(memberId);
        session.setRoleIdsJson(roleIds);
        session.setActionPermissionsJson(actionPermissions);
        session.setDataScopesJson(dataScopes);
        session.setAccessExpiresAt(now.plusMinutes(accessMinutes));
        session.setRefreshExpiresAt(now.plusDays(refreshDays));
        session.setRevoked(false);
        sessionService.insert(session);
        return new SessionTokens(accessToken, refreshToken, session.getAccessExpiresAt(), session.getRefreshExpiresAt());
    }

    private PlatformAccount findAccount(String username) {
        if (username == null) {
            return null;
        }
        List<PlatformAccount> accounts = accountService.selectList(Wrappers.<PlatformAccount>lambdaQuery()
                .eq(PlatformAccount::getUsername, username.strip().toLowerCase(Locale.ROOT)));
        return accounts.isEmpty() ? null : accounts.getFirst();
    }

    private PlatformAccountCredential findCredential(Long accountId) {
        List<PlatformAccountCredential> credentials = credentialService.selectList(
                Wrappers.<PlatformAccountCredential>lambdaQuery().eq(PlatformAccountCredential::getAccountId, accountId));
        return credentials.isEmpty() ? null : credentials.getFirst();
    }

    private void revokeAll(Long accountId) {
        List<AuthenticationSession> sessions = sessionService.selectList(
                Wrappers.<AuthenticationSession>lambdaQuery()
                        .eq(AuthenticationSession::getAccountId, accountId)
                        .eq(AuthenticationSession::getRevoked, false));
        sessions.forEach(session -> {
            session.setRevoked(true);
            sessionService.updateById(session);
        });
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize authentication context", exception);
        }
    }

    private <T> T readJson(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read authentication context", exception);
        }
    }
}
