package com.unique.unexamine.authentication.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.authentication.base.entity.AuthenticationSession;
import com.unique.unexamine.authentication.base.entity.AuthorizationPermissionVersion;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.entity.PlatformAccountCredential;
import com.unique.unexamine.authentication.base.service.AuthenticationSessionBaseService;
import com.unique.unexamine.authentication.base.service.AuthorizationPermissionVersionBaseService;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.platform.base.service.PlatformAccountCredentialBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authorization.manage.ResolvedPermissions;
import com.unique.unexamine.authorization.manage.DataScopeExpression;
import com.unique.unexamine.authorization.manage.PermissionGrant;
import com.unique.unexamine.authorization.manage.PlatformPermissionResolver;
import com.unique.unexamine.authorization.manage.PermissionResolver;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.platform.manage.foundation.PlatformDefinitionManager;
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
    private final PlatformDefinitionManager platformDefinitionManager;
    private final PlatformPermissionResolver platformPermissionResolver;
    private final PermissionResolver systemPermissionResolver;
    private final AuthorizationPermissionVersionBaseService permissionVersionService;

    public AuthenticationService(
            PlatformAccountBaseService accountService,
            PlatformAccountCredentialBaseService credentialService,
            AuthenticationSessionBaseService sessionService,
            Pbkdf2PasswordHasher passwordHasher,
            TokenFactory tokenFactory,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper,
            PlatformDefinitionManager platformDefinitionManager,
            PlatformPermissionResolver platformPermissionResolver,
            PermissionResolver systemPermissionResolver,
            AuthorizationPermissionVersionBaseService permissionVersionService,
            @Value("${app.authentication.access-minutes:30}") int accessMinutes,
            @Value("${app.authentication.refresh-days:14}") int refreshDays) {
        this.accountService = accountService;
        this.credentialService = credentialService;
        this.sessionService = sessionService;
        this.passwordHasher = passwordHasher;
        this.tokenFactory = tokenFactory;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.platformDefinitionManager = platformDefinitionManager;
        this.platformPermissionResolver = platformPermissionResolver;
        this.systemPermissionResolver = systemPermissionResolver;
        this.permissionVersionService = permissionVersionService;
        // Session timestamps are persisted as timezone-less DATETIME values and
        // compared by MySQL with NOW().  Use the application/database zone so a
        // freshly issued token is not considered expired when the deployment is
        // configured for a non-UTC timezone.
        this.clock = Clock.systemDefaultZone();
        this.accessMinutes = accessMinutes;
        this.refreshDays = refreshDays;
        this.dummyPasswordHash = passwordHasher.hash("not-a-real-account-password".toCharArray());
    }

    @Transactional(noRollbackFor = DomainException.class)
    public SessionTokens login(String username, char[] password, String traceId) {
        PlatformAccount account = findAccount(username);
        PlatformAccountCredential credential = account == null ? null : findCredential(account.getId());
        LocalDateTime now = LocalDateTime.now(clock);
        String candidateHash = credential == null ? dummyPasswordHash : credential.getPasswordHash();
        boolean passwordMatches = passwordHasher.matches(password, candidateHash);
        if (credential != null && credential.getLockedUntil() != null && credential.getLockedUntil().isAfter(now)) {
            auditRecorder.recordFailure(traceId, account.getId(), "AUTH_LOGIN", "ACCOUNT_TEMPORARILY_LOCKED",
                    Map.of("lockedUntil", credential.getLockedUntil().toString()));
            throw new DomainException("ACCOUNT_TEMPORARILY_LOCKED", "登录失败次数过多，请稍后再试", HttpStatus.LOCKED);
        }
        if (account == null || credential == null || !passwordMatches) {
            if (credential != null) {
                int attempts = credential.getFailedAttempts() == null ? 1 : credential.getFailedAttempts() + 1;
                credential.setFailedAttempts(attempts);
                if (attempts >= 5) {
                    credential.setLockedUntil(now.plusMinutes(15));
                }
                credentialService.updateById(credential);
            }
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
        if (Boolean.TRUE.equals(credential.getMustChangePassword())) {
            throw new DomainException("PASSWORD_CHANGE_REQUIRED", "请先完成密码修改", HttpStatus.FORBIDDEN);
        }

        credential.setFailedAttempts(0);
        credential.setLockedUntil(null);
        credentialService.updateById(credential);
        account.setLastLoginAt(now);
        accountService.updateById(account);

        SessionTokens tokens = createPlatformSession(account.getId(), "NONE");
        auditRecorder.record(traceId, account.getId(), null, null, null, "AUTH_LOGIN", "PLATFORM_ACCOUNT",
                account.getId().toString(), "SUCCESS", Map.of("context", "PLATFORM"));
        return tokens;
    }

    @Transactional
    public SessionTokens createPlatformSession(Long accountId, String mfaLevel) {
        Long platformId = platformDefinitionManager.requireDefaultPlatform().getId();
        ResolvedPermissions permissions = platformPermissionResolver.resolve(platformId, accountId);
        return createSession(accountId, null, null, null, null,
                toJson(permissions.roleIds()), toJson(permissions.permissions()), toJson(permissions.dataScopes()), mfaLevel);
    }

    @Transactional
    public SessionTokens createSystemSession(
            Long accountId,
            Long systemId,
            Long tenantId,
            Long memberId,
            Long tenantMemberId,
            ResolvedPermissions permissions,
            String mfaLevel) {
        return createSession(accountId, systemId, tenantId, memberId, tenantMemberId,
                toJson(permissions.roleIds()), toJson(permissions.permissions()), toJson(permissions.dataScopes()), mfaLevel);
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
        refreshSystemAuthorizationSnapshot(session);
        return new AuthenticatedContext(session.getId(), account.getId(), session.getPlatformId(), session.getSystemId(), session.getTenantId(),
                session.getMemberId(), session.getTenantMemberId(), account.getUsername(), account.getDisplayName(), session.getMfaLevel(),
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
            Long tenantMemberId,
            String roleIds,
            String actionPermissions,
            String dataScopes,
            String mfaLevel) {
        LocalDateTime now = LocalDateTime.now(clock);
        String accessToken = tokenFactory.create();
        String refreshToken = tokenFactory.create();
        AuthenticationSession session = new AuthenticationSession();
        session.setAccessTokenHash(tokenFactory.hash(accessToken));
        session.setRefreshTokenHash(tokenFactory.hash(refreshToken));
        session.setAccountId(accountId);
        session.setPlatformId(platformDefinitionManager.requireDefaultPlatform().getId());
        session.setSystemId(systemId);
        session.setTenantId(tenantId);
        session.setMemberId(memberId);
        session.setTenantMemberId(tenantMemberId);
        session.setRoleIdsJson(roleIds);
        session.setActionPermissionsJson(actionPermissions);
        session.setDataScopesJson(dataScopes);
        session.setPermissionVersion(systemId == null ? 0L : latestPermissionVersion(systemId, tenantId));
        session.setMfaLevel(mfaLevel);
        session.setAccessExpiresAt(now.plusMinutes(accessMinutes));
        session.setRefreshExpiresAt(now.plusDays(refreshDays));
        session.setRevoked(false);
        sessionService.insert(session);
        return new SessionTokens(accessToken, refreshToken, session.getAccessExpiresAt(), session.getRefreshExpiresAt());
    }

    private void refreshSystemAuthorizationSnapshot(AuthenticationSession session) {
        if (session.getSystemId() == null || session.getTenantId() == null || session.getTenantMemberId() == null) {
            return;
        }
        long latestVersion = latestPermissionVersion(session.getSystemId(), session.getTenantId());
        if (session.getPermissionVersion() != null && session.getPermissionVersion() >= latestVersion) {
            return;
        }
        ResolvedPermissions resolved = systemPermissionResolver.resolve(
                session.getSystemId(), session.getTenantId(), session.getTenantMemberId());
        session.setRoleIdsJson(toJson(resolved.roleIds()));
        session.setActionPermissionsJson(toJson(resolved.permissions()));
        session.setDataScopesJson(toJson(resolved.dataScopes()));
        session.setPermissionVersion(latestVersion);
        sessionService.updateById(session);
    }

    private long latestPermissionVersion(Long systemId, Long tenantId) {
        return permissionVersionService.selectList(Wrappers.<AuthorizationPermissionVersion>lambdaQuery()
                        .eq(AuthorizationPermissionVersion::getContextType, "SYSTEM")
                        .eq(AuthorizationPermissionVersion::getSystemId, systemId)
                        .eq(AuthorizationPermissionVersion::getTenantId, tenantId)).stream()
                .map(AuthorizationPermissionVersion::getVersionNumber).max(Long::compareTo).orElse(0L);
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
