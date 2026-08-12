package com.unique.examine.plat.manage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.core.api.AccountRecoveryMailFacade;
import com.unique.examine.core.api.AuditEvent;
import com.unique.examine.core.api.AuditFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.base.entity.Credential;
import com.unique.examine.plat.base.entity.PasswordRecoveryToken;
import com.unique.examine.plat.base.mapper.PlatAccountMapper;
import com.unique.examine.plat.base.mapper.PlatCredentialMapper;
import com.unique.examine.plat.base.mapper.PlatPasswordRecoveryTokenMapper;
import com.unique.examine.plat.manage.config.PasswordRecoveryProperties;
import com.unique.examine.plat.manage.dto.PasswordRecoveryRequest;
import com.unique.examine.plat.manage.dto.PasswordRecoveryResetRequest;
import com.unique.examine.plat.manage.security.PasswordService;
import com.unique.examine.plat.manage.security.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

@Service
public class PasswordRecoveryService {
    private static final String ACTIVE = "ACTIVE";
    private static final String PASSWORD = "PASSWORD";

    private final PlatAccountMapper accountMapper;
    private final PlatCredentialMapper credentialMapper;
    private final PlatPasswordRecoveryTokenMapper recoveryMapper;
    private final PasswordService passwordService;
    private final TokenService tokenService;
    private final SessionService sessionService;
    private final AnonymousRateLimitService rateLimitService;
    private final AccountRecoveryMailFacade mailFacade;
    private final PasswordRecoveryProperties properties;
    private final IdService idService;
    private final AuditFacade auditFacade;
    private final TransactionTemplate transactions;

    public PasswordRecoveryService(
            PlatAccountMapper accountMapper,
            PlatCredentialMapper credentialMapper,
            PlatPasswordRecoveryTokenMapper recoveryMapper,
            PasswordService passwordService,
            TokenService tokenService,
            SessionService sessionService,
            AnonymousRateLimitService rateLimitService,
            AccountRecoveryMailFacade mailFacade,
            PasswordRecoveryProperties properties,
            IdService idService,
            AuditFacade auditFacade,
            TransactionTemplate transactions
    ) {
        this.accountMapper = accountMapper;
        this.credentialMapper = credentialMapper;
        this.recoveryMapper = recoveryMapper;
        this.passwordService = passwordService;
        this.tokenService = tokenService;
        this.sessionService = sessionService;
        this.rateLimitService = rateLimitService;
        this.mailFacade = mailFacade;
        this.properties = properties;
        this.idService = idService;
        this.auditFacade = auditFacade;
        this.transactions = transactions;
    }

    public void request(PasswordRecoveryRequest request, ClientRequest client) {
        var normalized = normalize(request.account());
        rateLimitService.check(
                "password-recovery-ip",
                client.remoteAddress(),
                properties.ipRequestsPerMinute(),
                Duration.ofMinutes(1)
        );
        rateLimitService.check(
                "password-recovery-account",
                tokenService.hash(normalized),
                properties.accountRequestsPerHour(),
                Duration.ofHours(1)
        );

        var pending = transactions.execute(status -> issue(normalized, request.account().trim(), client));
        if (pending == null) {
            return;
        }

        AccountRecoveryMailFacade.Status deliveryStatus;
        try {
            var receipt = mailFacade.deliver(new AccountRecoveryMailFacade.Command(
                    pending.recipientEmail(),
                    pending.rawToken(),
                    pending.expiresAt().atZone(ZoneId.systemDefault()).toInstant(),
                    client.requestId()
            ));
            deliveryStatus = receipt == null || !client.requestId().equals(receipt.requestId())
                    ? AccountRecoveryMailFacade.Status.FAILED
                    : receipt.status();
        } catch (RuntimeException failure) {
            deliveryStatus = AccountRecoveryMailFacade.Status.FAILED;
        }

        var finalDeliveryStatus = deliveryStatus;
        if (finalDeliveryStatus == AccountRecoveryMailFacade.Status.SENT) {
            transactions.executeWithoutResult(status -> audit(
                    "PASSWORD_RECOVERY_DELIVERY",
                    pending.accountId(),
                    client,
                    "SUCCESS",
                    null
            ));
            return;
        }
        transactions.executeWithoutResult(status -> {
            recoveryMapper.revoke(pending.recoveryId(), LocalDateTime.now());
            audit(
                    "PASSWORD_RECOVERY_DELIVERY",
                    pending.accountId(),
                    client,
                    "FAILED",
                    "PASSWORD_RECOVERY_MAIL_" + finalDeliveryStatus.name()
            );
        });
    }

    public void reset(PasswordRecoveryResetRequest request, ClientRequest client) {
        try {
            transactions.executeWithoutResult(status -> resetInTransaction(request, client));
        } catch (PasswordRecoveryDeniedException denied) {
            transactions.executeWithoutResult(status -> audit(
                    "PASSWORD_RECOVERY_RESET",
                    denied.accountId(),
                    client,
                    "DENIED",
                    denied.code()
            ));
            throw denied;
        } catch (BusinessException failure) {
            transactions.executeWithoutResult(status -> audit(
                    "PASSWORD_RECOVERY_RESET",
                    null,
                    client,
                    "FAILED",
                    failure.code()
            ));
            throw failure;
        }
    }

    private PendingRecovery issue(String normalized, String supplied, ClientRequest client) {
        var matches = accountMapper.selectList(Wrappers.<Account>lambdaQuery()
                .and(query -> query
                        .eq(Account::getUsernameNormalized, normalized)
                        .or().eq(Account::getEmailNormalized, normalized)
                        .or().eq(Account::getPhone, supplied))
                .last("limit 2"));
        var account = matches.size() == 1 ? matches.getFirst() : null;
        var eligible = account != null
                && ACTIVE.equals(account.getStatus())
                && account.getEmail() != null
                && !account.getEmail().isBlank();
        audit(
                "PASSWORD_RECOVERY_REQUEST",
                account == null ? null : account.getId(),
                client,
                "SUCCESS",
                null
        );
        if (!eligible) {
            return null;
        }

        var now = LocalDateTime.now();
        recoveryMapper.revokeActiveForAccount(account.getId(), null, now);
        var rawToken = tokenService.accessToken();
        var token = new PasswordRecoveryToken();
        token.setId(idService.nextId());
        token.setAccountId(account.getId());
        token.setTokenHash(tokenService.hash(rawToken));
        token.setStatus(ACTIVE);
        token.setRequestedAt(now);
        token.setExpiresAt(now.plus(properties.tokenTtl()));
        token.setCreatedAt(now);
        token.setUpdatedAt(now);
        token.setVersion(0L);
        recoveryMapper.insert(token);
        return new PendingRecovery(
                token.getId(), account.getId(), account.getEmail().trim(), rawToken, token.getExpiresAt());
    }

    private void resetInTransaction(PasswordRecoveryResetRequest request, ClientRequest client) {
        var token = recoveryMapper.lockByHash(tokenService.hash(request.token()));
        if (token == null || !ACTIVE.equals(token.getStatus())) {
            throw PasswordRecoveryDeniedException.invalid(token == null ? null : token.getAccountId());
        }
        var now = LocalDateTime.now();
        if (!token.getExpiresAt().isAfter(now)) {
            throw PasswordRecoveryDeniedException.expired(token.getAccountId());
        }
        var account = accountMapper.selectById(token.getAccountId());
        if (account == null || !ACTIVE.equals(account.getStatus())) {
            throw PasswordRecoveryDeniedException.disabled(token.getAccountId());
        }
        var credential = credentialMapper.selectOne(Wrappers.<Credential>lambdaQuery()
                .eq(Credential::getAccountId, account.getId())
                .eq(Credential::getCredentialType, PASSWORD));
        if (credential == null) {
            throw PasswordRecoveryDeniedException.invalid(account.getId());
        }
        if (passwordService.matches(request.newPassword(), credential.getPasswordHash())) {
            throw PasswordRecoveryDeniedException.unchanged(account.getId());
        }

        var replacement = passwordService.hash(request.newPassword());
        credential.setPasswordHash(replacement.encoded());
        credential.setPasswordAlgorithm(replacement.algorithm());
        credential.setPasswordParameters(replacement.parameters());
        credential.setFailedAttempts(0);
        credential.setLockedUntil(null);
        credential.setPasswordChangedAt(now);
        credential.setUpdatedAt(now);
        if (credentialMapper.replacePassword(credential) != 1) {
            throw conflict();
        }
        if (recoveryMapper.consume(token.getId(), token.getVersion(), now) != 1) {
            throw conflict();
        }
        recoveryMapper.revokeActiveForAccount(account.getId(), token.getId(), now);
        sessionService.revokeAllForAccount(account.getId());
        audit("PASSWORD_RECOVERY_RESET", account.getId(), client, "SUCCESS", null);
    }

    private void audit(
            String eventType,
            Long accountId,
            ClientRequest client,
            String result,
            String failureCode
    ) {
        auditFacade.recordSecurity(new AuditEvent(
                eventType,
                accountId,
                null,
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

    private static BusinessException conflict() {
        return new BusinessException(
                "PASSWORD_RECOVERY_CONFLICT",
                "Password recovery changed concurrently; request a new link",
                HttpStatus.CONFLICT
        );
    }

    private static String normalize(String value) {
        return java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    private record PendingRecovery(
            long recoveryId,
            long accountId,
            String recipientEmail,
            String rawToken,
            LocalDateTime expiresAt
    ) {
    }
}
