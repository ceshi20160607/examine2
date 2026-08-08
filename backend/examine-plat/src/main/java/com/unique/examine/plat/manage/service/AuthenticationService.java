package com.unique.examine.plat.manage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.core.api.AuditEvent;
import com.unique.examine.core.api.AuditFacade;
import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.base.entity.Credential;
import com.unique.examine.plat.base.mapper.PlatAccountMapper;
import com.unique.examine.plat.base.mapper.PlatCredentialMapper;
import com.unique.examine.plat.manage.config.SecurityProperties;
import com.unique.examine.plat.manage.dto.LoginRequest;
import com.unique.examine.plat.manage.security.PasswordService;
import com.unique.examine.plat.manage.security.TokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class AuthenticationService {
    private final PlatAccountMapper accountMapper;
    private final PlatCredentialMapper credentialMapper;
    private final PasswordService passwordService;
    private final TokenService tokenService;
    private final SessionService sessionService;
    private final SecurityProperties properties;
    private final AuditFacade auditFacade;
    private final String dummyHash;

    public AuthenticationService(
            PlatAccountMapper accountMapper,
            PlatCredentialMapper credentialMapper,
            PasswordService passwordService,
            TokenService tokenService,
            SessionService sessionService,
            SecurityProperties properties,
            AuditFacade auditFacade
    ) {
        this.accountMapper = accountMapper;
        this.credentialMapper = credentialMapper;
        this.passwordService = passwordService;
        this.tokenService = tokenService;
        this.sessionService = sessionService;
        this.properties = properties;
        this.auditFacade = auditFacade;
        this.dummyHash = passwordService.hash("not-a-real-account-password").encoded();
    }

    @Transactional(noRollbackFor = LoginDeniedException.class)
    public IssuedSession login(LoginRequest request, ClientRequest clientRequest) {
        var normalized = normalize(request.account());
        var account = accountMapper.selectOne(Wrappers.<Account>lambdaQuery()
                .and(query -> query
                        .eq(Account::getUsernameNormalized, normalized)
                        .or().eq(Account::getEmailNormalized, normalized)
                        .or().eq(Account::getPhone, request.account().trim())));
        if (account == null) {
            passwordService.matches(request.password(), dummyHash);
            record(clientRequest, null, normalized, "DENIED", "LOGIN_INVALID");
            throw invalidLogin();
        }
        var credential = credentialMapper.selectOne(Wrappers.<Credential>lambdaQuery()
                .eq(Credential::getAccountId, account.getId())
                .eq(Credential::getCredentialType, "PASSWORD"));
        var now = LocalDateTime.now();
        if (!"ACTIVE".equals(account.getStatus()) || credential == null) {
            record(clientRequest, account.getId(), normalized, "DENIED", "LOGIN_INVALID");
            throw invalidLogin();
        }
        if (credential.getLockedUntil() != null && credential.getLockedUntil().isAfter(now)) {
            record(clientRequest, account.getId(), normalized, "DENIED", "LOGIN_LOCKED");
            throw invalidLogin();
        }
        if (!passwordService.matches(request.password(), credential.getPasswordHash())) {
            var failures = credential.getFailedAttempts() + 1;
            credential.setFailedAttempts(failures);
            if (failures >= properties.loginMaxFailures()) {
                credential.setLockedUntil(now.plus(properties.loginLockDuration()));
            }
            credential.setUpdatedAt(now);
            credentialMapper.updateById(credential);
            record(clientRequest, account.getId(), normalized, "DENIED", "LOGIN_INVALID");
            throw invalidLogin();
        }

        credential.setFailedAttempts(0);
        credential.setLockedUntil(null);
        credential.setUpdatedAt(now);
        credentialMapper.updateById(credential);
        account.setLastLoginAt(now);
        account.setUpdatedAt(now);
        account.setUpdatedBy(account.getId());
        accountMapper.updateById(account);
        record(clientRequest, account.getId(), normalized, "SUCCESS", null);
        return sessionService.issuePlatform(account);
    }

    private void record(
            ClientRequest client,
            Long accountId,
            String normalizedAccount,
            String result,
            String failureCode
    ) {
        auditFacade.recordSecurity(new AuditEvent(
                "LOGIN",
                accountId,
                tokenService.hash(normalizedAccount).substring(0, 16),
                null,
                null,
                "WEB",
                client.remoteAddress(),
                client.userAgent(),
                client.requestId(),
                client.traceId(),
                result,
                failureCode,
                "{}"
        ));
    }

    private static LoginDeniedException invalidLogin() {
        return new LoginDeniedException("LOGIN_INVALID", "账号或密码不正确");
    }

    private static String normalize(String value) {
        return java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }
}
