package com.unique.unexamine.authentication.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.base.entity.AuthenticationSession;
import com.unique.unexamine.authentication.base.service.AuthenticationSessionBaseService;
import com.unique.unexamine.platform.base.entity.PlatAccountMfa;
import com.unique.unexamine.platform.base.entity.PlatPasswordHistory;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.entity.PlatformAccountCredential;
import com.unique.unexamine.platform.base.service.PlatAccountMfaBaseService;
import com.unique.unexamine.platform.base.service.PlatPasswordHistoryBaseService;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.platform.base.service.PlatformAccountCredentialBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AccountSecurityService {
    private final PlatformAccountBaseService accountService;
    private final PlatformAccountCredentialBaseService credentialService;
    private final PlatPasswordHistoryBaseService passwordHistoryService;
    private final PlatAccountMfaBaseService mfaService;
    private final AuthenticationSessionBaseService sessionService;
    private final Pbkdf2PasswordHasher passwordHasher;
    private final AuditRecorder auditRecorder;

    public AccountSecurityService(
            PlatformAccountBaseService accountService,
            PlatformAccountCredentialBaseService credentialService,
            PlatPasswordHistoryBaseService passwordHistoryService,
            PlatAccountMfaBaseService mfaService,
            AuthenticationSessionBaseService sessionService,
            Pbkdf2PasswordHasher passwordHasher,
            AuditRecorder auditRecorder) {
        this.accountService = accountService;
        this.credentialService = credentialService;
        this.passwordHistoryService = passwordHistoryService;
        this.mfaService = mfaService;
        this.sessionService = sessionService;
        this.passwordHasher = passwordHasher;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public AccountProfileView profile() {
        AuthenticatedContext context = AuthenticationContextHolder.require();
        PlatformAccount account = requireAccount(context.accountId());
        PlatformAccountCredential credential = requireCredential(context.accountId());
        List<MfaMethodView> methods = mfaService.selectList(Wrappers.<PlatAccountMfa>lambdaQuery()
                        .eq(PlatAccountMfa::getAccountId, context.accountId())
                        .orderByAsc(PlatAccountMfa::getId)).stream()
                .map(value -> new MfaMethodView(value.getId(), value.getMethodType(), value.getDisplayLabel(),
                        value.getStatus(), value.getVerifiedAt()))
                .toList();
        return view(account, credential, methods, sessions(context));
    }

    @Transactional
    public AccountProfileView updateProfile(AccountProfileUpdateRequest request, String traceId) {
        AuthenticatedContext context = AuthenticationContextHolder.require();
        PlatformAccount account = requireAccount(context.accountId());
        account.setDisplayName(request.displayName().strip());
        account.setEmail(normalize(request.email()));
        account.setMobile(normalize(request.mobile()));
        account.setLocale(request.locale().strip());
        account.setTimezone(request.timezone().strip());
        account.setVersion(request.version());
        if (accountService.updateById(account) != 1) {
            throw new DomainException("ACCOUNT_VERSION_CONFLICT", "个人资料已被更新，请刷新后重试", HttpStatus.CONFLICT);
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "ACCOUNT_PROFILE_UPDATED", "PLATFORM_ACCOUNT", context.accountId().toString(), "SUCCESS",
                Map.of("fields", List.of("displayName", "email", "mobile", "locale", "timezone")));
        return profile();
    }

    @Transactional
    public AccountProfileView changePassword(PasswordChangeRequest request, String traceId) {
        AuthenticatedContext context = AuthenticationContextHolder.require();
        PlatformAccountCredential credential = requireCredential(context.accountId());
        if (!passwordHasher.matches(request.currentPassword().toCharArray(), credential.getPasswordHash())) {
            throw new DomainException("CURRENT_PASSWORD_INVALID", "当前密码错误", HttpStatus.BAD_REQUEST);
        }
        requireStrongPassword(request.newPassword());
        List<PlatPasswordHistory> previous = passwordHistoryService.selectList(
                Wrappers.<PlatPasswordHistory>lambdaQuery()
                        .eq(PlatPasswordHistory::getAccountId, context.accountId())
                        .orderByDesc(PlatPasswordHistory::getCredentialVersion)
                        .last("limit 5"));
        if (passwordHasher.matches(request.newPassword().toCharArray(), credential.getPasswordHash())
                || previous.stream().anyMatch(value -> passwordHasher.matches(
                request.newPassword().toCharArray(), value.getPasswordHash()))) {
            throw new DomainException("PASSWORD_REUSED", "新密码不能与最近使用的密码相同", HttpStatus.BAD_REQUEST);
        }

        PlatPasswordHistory history = new PlatPasswordHistory();
        history.setAccountId(context.accountId());
        history.setPasswordHash(credential.getPasswordHash());
        history.setCredentialVersion(credential.getCredentialVersion());
        passwordHistoryService.insert(history);

        credential.setPasswordHash(passwordHasher.hash(request.newPassword().toCharArray()));
        credential.setCredentialVersion(credential.getCredentialVersion() + 1);
        credential.setChangedAt(LocalDateTime.now());
        credential.setMustChangePassword(false);
        credential.setFailedAttempts(0);
        credential.setLockedUntil(null);
        credentialService.updateById(credential);

        int revoked = 0;
        for (AuthenticationSession session : activeSessions(context.accountId())) {
            if (!session.getId().equals(context.sessionId())) {
                session.setRevoked(true);
                session.setRevokedReason("PASSWORD_CHANGED");
                sessionService.updateById(session);
                revoked++;
            }
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "ACCOUNT_PASSWORD_CHANGED", "PLATFORM_ACCOUNT", context.accountId().toString(), "SUCCESS",
                Map.of("credentialVersion", credential.getCredentialVersion(), "revokedOtherSessions", revoked));
        return profile();
    }

    @Transactional
    public void revokeSession(Long sessionId, String traceId) {
        AuthenticatedContext context = AuthenticationContextHolder.require();
        if (sessionId.equals(context.sessionId())) {
            throw new DomainException("CURRENT_SESSION_REVOKE_NOT_ALLOWED", "请使用退出登录结束当前会话", HttpStatus.BAD_REQUEST);
        }
        AuthenticationSession session = sessionService.selectById(sessionId);
        if (session == null || !context.accountId().equals(session.getAccountId())) {
            throw new DomainException("SESSION_NOT_FOUND", "会话不存在", HttpStatus.NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(session.getRevoked())) {
            session.setRevoked(true);
            session.setRevokedReason("USER_REVOKED");
            sessionService.updateById(session);
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "ACCOUNT_SESSION_REVOKED", "AUTH_SESSION", sessionId.toString(), "SUCCESS", Map.of());
    }

    private AccountProfileView view(
            PlatformAccount account,
            PlatformAccountCredential credential,
            List<MfaMethodView> methods,
            List<AccountSessionView> sessions) {
        return new AccountProfileView(account.getId(), account.getUsername(), account.getDisplayName(), account.getEmail(),
                account.getMobile(), account.getLocale(), account.getTimezone(), account.getStatus(), account.getLastLoginAt(),
                credential.getCredentialVersion(), account.getVersion(), methods, sessions);
    }

    private List<AccountSessionView> sessions(AuthenticatedContext context) {
        return sessionService.selectList(Wrappers.<AuthenticationSession>lambdaQuery()
                        .eq(AuthenticationSession::getAccountId, context.accountId())
                        .orderByDesc(AuthenticationSession::getCreatedAt)).stream()
                .map(value -> new AccountSessionView(value.getId(), value.getSystemId() == null ? "PLATFORM" : "SYSTEM",
                        value.getSystemId(), value.getTenantId(), value.getMfaLevel(), value.getAccessExpiresAt(),
                        value.getRefreshExpiresAt(), value.getCreatedAt(), value.getId().equals(context.sessionId()),
                        Boolean.TRUE.equals(value.getRevoked())))
                .toList();
    }

    private List<AuthenticationSession> activeSessions(Long accountId) {
        return sessionService.selectList(Wrappers.<AuthenticationSession>lambdaQuery()
                .eq(AuthenticationSession::getAccountId, accountId)
                .eq(AuthenticationSession::getRevoked, false));
    }

    private PlatformAccount requireAccount(Long accountId) {
        PlatformAccount account = accountService.selectById(accountId);
        if (account == null) {
            throw new DomainException("ACCOUNT_NOT_FOUND", "账号不存在", HttpStatus.NOT_FOUND);
        }
        return account;
    }

    private PlatformAccountCredential requireCredential(Long accountId) {
        List<PlatformAccountCredential> credentials = credentialService.selectList(
                Wrappers.<PlatformAccountCredential>lambdaQuery()
                        .eq(PlatformAccountCredential::getAccountId, accountId));
        if (credentials.isEmpty()) {
            throw new DomainException("CREDENTIAL_NOT_FOUND", "账号未配置密码凭证", HttpStatus.CONFLICT);
        }
        return credentials.getFirst();
    }

    private void requireStrongPassword(String password) {
        boolean lower = password.chars().anyMatch(Character::isLowerCase);
        boolean upper = password.chars().anyMatch(Character::isUpperCase);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        boolean symbol = password.chars().anyMatch(value -> !Character.isLetterOrDigit(value));
        if (!(lower && upper && digit && symbol)) {
            throw new DomainException("PASSWORD_WEAK", "新密码需至少 12 位并包含大小写字母、数字和符号", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip().toLowerCase(Locale.ROOT);
    }
}
