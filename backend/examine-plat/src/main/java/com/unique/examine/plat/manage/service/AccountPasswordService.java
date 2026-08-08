package com.unique.examine.plat.manage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.core.api.AuditEvent;
import com.unique.examine.core.api.AuditFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.base.entity.Credential;
import com.unique.examine.plat.base.mapper.PlatAccountMapper;
import com.unique.examine.plat.base.mapper.PlatCredentialMapper;
import com.unique.examine.plat.manage.dto.ChangePasswordRequest;
import com.unique.examine.plat.manage.security.PasswordService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AccountPasswordService {
    private static final String PASSWORD = "PASSWORD";

    private final PlatAccountMapper accountMapper;
    private final PlatCredentialMapper credentialMapper;
    private final PasswordService passwordService;
    private final SessionService sessionService;
    private final AuditFacade auditFacade;

    public AccountPasswordService(
            PlatAccountMapper accountMapper,
            PlatCredentialMapper credentialMapper,
            PasswordService passwordService,
            SessionService sessionService,
            AuditFacade auditFacade
    ) {
        this.accountMapper = accountMapper;
        this.credentialMapper = credentialMapper;
        this.passwordService = passwordService;
        this.sessionService = sessionService;
        this.auditFacade = auditFacade;
    }

    @Transactional(noRollbackFor = PasswordChangeDeniedException.class)
    public void changePassword(
            AuthenticatedSession current,
            ChangePasswordRequest request,
            ClientRequest client
    ) {
        var account = accountMapper.selectById(current.accountId());
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw unavailable();
        }
        var credential = credentialMapper.selectOne(Wrappers.<Credential>lambdaQuery()
                .eq(Credential::getAccountId, current.accountId())
                .eq(Credential::getCredentialType, PASSWORD));
        if (credential == null) {
            throw unavailable();
        }
        if (!passwordService.matches(request.currentPassword(), credential.getPasswordHash())) {
            audit(current, client, "DENIED", "PASSWORD_CURRENT_INVALID");
            throw new PasswordChangeDeniedException();
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException(
                    "PASSWORD_UNCHANGED",
                    "New password must differ from the current password",
                    HttpStatus.BAD_REQUEST
            );
        }

        var replacement = passwordService.hash(request.newPassword());
        var now = LocalDateTime.now();
        credential.setPasswordHash(replacement.encoded());
        credential.setPasswordAlgorithm(replacement.algorithm());
        credential.setPasswordParameters(replacement.parameters());
        credential.setFailedAttempts(0);
        credential.setLockedUntil(null);
        credential.setPasswordChangedAt(now);
        credential.setUpdatedAt(now);
        if (credentialMapper.replacePassword(credential) != 1) {
            throw new BusinessException(
                    "PASSWORD_CHANGE_CONFLICT",
                    "Password changed concurrently; retry with a fresh session",
                    HttpStatus.CONFLICT
            );
        }

        sessionService.revokeAllForAccount(current.accountId());
        audit(current, client, "SUCCESS", null);
    }

    private void audit(
            AuthenticatedSession current,
            ClientRequest client,
            String result,
            String failureCode
    ) {
        auditFacade.recordSecurity(new AuditEvent(
                "PASSWORD_CHANGE",
                current.accountId(),
                null,
                current.systemId(),
                current.tenantId(),
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

    private static BusinessException unavailable() {
        return new BusinessException(
                "PASSWORD_CHANGE_UNAVAILABLE",
                "Password change is unavailable",
                HttpStatus.UNAUTHORIZED
        );
    }
}
