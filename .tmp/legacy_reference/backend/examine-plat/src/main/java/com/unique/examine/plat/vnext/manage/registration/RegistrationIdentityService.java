package com.unique.examine.plat.vnext.manage.registration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.manage.security.PasswordService;
import com.unique.examine.plat.vnext.base.entity.Account;
import com.unique.examine.plat.vnext.base.entity.Credential;
import com.unique.examine.plat.vnext.base.service.IVNextPlatAccountService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatCredentialService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
final class RegistrationIdentityService {
    private final IVNextPlatAccountService accountService;
    private final IVNextPlatCredentialService credentialService;
    private final PasswordService passwordService;
    private final IdService idService;

    RegistrationIdentityService(
            IVNextPlatAccountService accountService,
            IVNextPlatCredentialService credentialService,
            PasswordService passwordService,
            IdService idService
    ) {
        this.accountService = accountService;
        this.credentialService = credentialService;
        this.passwordService = passwordService;
        this.idService = idService;
    }

    Account create(RegistrationCommand command, LocalDateTime now) {
        var account = new Account();
        account.setId(idService.nextId());
        account.setAccountCode("ACC_" + account.getId());
        account.setUsername(command.username());
        account.setUsernameNormalized(command.usernameNormalized());
        account.setDisplayName(command.displayName());
        account.setLocale("zh-CN");
        account.setTimeZone("Asia/Shanghai");
        account.setStatus("ACTIVE");
        account.setLastLoginAt(now);
        account.setCreatedAt(now);
        account.setCreatedBy(account.getId());
        account.setUpdatedAt(now);
        account.setUpdatedBy(account.getId());
        account.setVersion(0L);
        require(accountService.save(account), "Account persistence");

        var hash = passwordService.hash(command.password());
        var credential = new Credential();
        credential.setId(idService.nextId());
        credential.setAccountId(account.getId());
        credential.setCredentialType("PASSWORD");
        credential.setPasswordHash(hash.encoded());
        credential.setPasswordAlgorithm(hash.algorithm());
        credential.setPasswordParameters(hash.parameters());
        credential.setFailedAttempts(0);
        credential.setLockedUntil(null);
        credential.setPasswordChangedAt(now);
        credential.setCreatedAt(now);
        credential.setUpdatedAt(now);
        credential.setVersion(0L);
        require(credentialService.save(credential), "Credential persistence");
        return account;
    }

    Account find(String usernameNormalized) {
        return accountService.getOne(Wrappers.<Account>lambdaQuery()
                .eq(Account::getUsernameNormalized, usernameNormalized), false);
    }

    Account requireActive(long accountId) {
        var account = accountService.getById(accountId);
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw RegistrationErrors.replayMismatch();
        }
        return account;
    }

    boolean passwordMatches(long accountId, String rawPassword) {
        var credential = credentialService.getOne(Wrappers.<Credential>lambdaQuery()
                .eq(Credential::getAccountId, accountId)
                .eq(Credential::getCredentialType, "PASSWORD"), false);
        return credential != null
                && "ARGON2ID".equals(credential.getPasswordAlgorithm())
                && passwordService.matches(rawPassword, credential.getPasswordHash());
    }

    private static void require(boolean result, String operation) {
        if (!result) {
            throw new IllegalStateException(operation + " was rejected");
        }
    }
}
