package com.unique.examine.plat.vnext.manage.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.plat.manage.config.SecurityProperties;
import com.unique.examine.plat.manage.security.PasswordService;
import com.unique.examine.plat.vnext.base.entity.Account;
import com.unique.examine.plat.vnext.base.entity.Credential;
import com.unique.examine.plat.vnext.base.service.IVNextPlatAccountService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatCredentialService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class VNextLoginService {
    private final IVNextPlatAccountService accountService;
    private final IVNextPlatCredentialService credentialService;
    private final PasswordService passwordService;
    private final VNextAuthTokenService tokenService;
    private final VNextPlatformPermissionResolver permissionResolver;
    private final VNextSessionService sessionService;
    private final VNextSecurityAuditService securityAuditService;
    private final SecurityProperties properties;
    private final String dummyHash;

    public VNextLoginService(
            IVNextPlatAccountService accountService,
            IVNextPlatCredentialService credentialService,
            PasswordService passwordService,
            VNextAuthTokenService tokenService,
            VNextPlatformPermissionResolver permissionResolver,
            VNextSessionService sessionService,
            VNextSecurityAuditService securityAuditService,
            SecurityProperties properties
    ) {
        this.accountService = accountService;
        this.credentialService = credentialService;
        this.passwordService = passwordService;
        this.tokenService = tokenService;
        this.permissionResolver = permissionResolver;
        this.sessionService = sessionService;
        this.securityAuditService = securityAuditService;
        this.properties = properties;
        this.dummyHash = passwordService.hash("vnext-login-timing-equalizer").encoded();
    }

    @Transactional(noRollbackFor = VNextAuthException.class)
    public IssuedSession login(String accountValue, String password, ClientRequest client) {
        var normalized = normalize(accountValue);
        var account = accountService.getOne(Wrappers.<Account>lambdaQuery()
                .and(query -> query
                        .eq(Account::getUsernameNormalized, normalized)
                        .or().eq(Account::getEmailNormalized, normalized)
                        .or().eq(Account::getPhone, accountValue.trim())), false);
        if (account == null) {
            passwordService.matches(password, dummyHash);
            denied(null, normalized, "AUTH_INVALID_CREDENTIALS", client);
        }
        if (!"ACTIVE".equals(account.getStatus())) {
            denied(account.getId(), normalized, "AUTH_ACCOUNT_DISABLED", client);
        }

        var credential = credentialService.getOne(Wrappers.<Credential>lambdaQuery()
                .eq(Credential::getAccountId, account.getId())
                .eq(Credential::getCredentialType, "PASSWORD"), false);
        if (credential == null) {
            passwordService.matches(password, dummyHash);
            denied(account.getId(), normalized, "AUTH_INVALID_CREDENTIALS", client);
        }

        var now = LocalDateTime.now();
        if (credential.getLockedUntil() != null && credential.getLockedUntil().isAfter(now)) {
            denied(account.getId(), normalized, "AUTH_RATE_LIMITED", client);
        }
        if (!passwordService.matches(password, credential.getPasswordHash())) {
            var failures = (credential.getFailedAttempts() == null ? 0 : credential.getFailedAttempts()) + 1;
            credential.setFailedAttempts(failures);
            if (failures >= properties.loginMaxFailures()) {
                credential.setLockedUntil(now.plus(properties.loginLockDuration()));
            }
            credential.setUpdatedAt(now);
            updateCredential(credential);
            denied(account.getId(), normalized, "AUTH_INVALID_CREDENTIALS", client);
        }

        credential.setFailedAttempts(0);
        credential.setLockedUntil(null);
        credential.setUpdatedAt(now);
        updateCredential(credential);

        account.setLastLoginAt(now);
        account.setUpdatedAt(now);
        account.setUpdatedBy(account.getId());
        updateAccount(account);

        var authorization = permissionResolver.resolve(account.getId());
        var issued = sessionService.issue(account, authorization);
        securityAuditService.record("LOGIN", account.getId(), accountHint(normalized), "SUCCESS", null, client);
        return issued;
    }

    private void denied(Long accountId, String normalized, String code, ClientRequest client) {
        securityAuditService.record("LOGIN", accountId, accountHint(normalized), "DENIED", code, client);
        var message = switch (code) {
            case "AUTH_ACCOUNT_DISABLED" -> "Account is unavailable";
            case "AUTH_RATE_LIMITED" -> "Login is temporarily rate limited";
            default -> "Account or password is incorrect";
        };
        var status = "AUTH_RATE_LIMITED".equals(code)
                ? HttpStatus.TOO_MANY_REQUESTS
                : HttpStatus.UNAUTHORIZED;
        throw new VNextAuthException(code, message, status);
    }

    private String accountHint(String normalized) {
        return tokenService.hash(normalized).substring(0, 16);
    }

    private void updateCredential(Credential credential) {
        if (!credentialService.updateById(credential)) {
            throw new IllegalStateException("Credential update was rejected");
        }
    }

    private void updateAccount(Account account) {
        if (!accountService.updateById(account)) {
            throw new IllegalStateException("Account update was rejected");
        }
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}
