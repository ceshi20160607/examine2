package com.unique.examine.plat.manage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.base.entity.Credential;
import com.unique.examine.plat.base.mapper.PlatAccountMapper;
import com.unique.examine.plat.base.mapper.PlatCredentialMapper;
import com.unique.examine.plat.manage.config.BootstrapProperties;
import com.unique.examine.plat.manage.security.PasswordService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class PlatformRootBootstrapService implements ApplicationRunner {
    private final BootstrapProperties properties;
    private final PlatAccountMapper accountMapper;
    private final PlatCredentialMapper credentialMapper;
    private final IdService idService;
    private final PasswordService passwordService;
    private final AuthorizationProvisioningService authorizationProvisioningService;

    public PlatformRootBootstrapService(
            BootstrapProperties properties,
            PlatAccountMapper accountMapper,
            PlatCredentialMapper credentialMapper,
            IdService idService,
            PasswordService passwordService,
            AuthorizationProvisioningService authorizationProvisioningService
    ) {
        this.properties = properties;
        this.accountMapper = accountMapper;
        this.credentialMapper = credentialMapper;
        this.idService = idService;
        this.passwordService = passwordService;
        this.authorizationProvisioningService = authorizationProvisioningService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        var normalized = normalize(properties.username());
        var account = accountMapper.selectOne(Wrappers.<Account>lambdaQuery()
                .eq(Account::getUsernameNormalized, normalized));
        var now = LocalDateTime.now();
        if (account == null) {
            account = createAccount(normalized, now);
            createCredential(account.getId(), now);
        } else if (!"ACTIVE".equals(account.getStatus())) {
            throw new BusinessException(
                    "BOOTSTRAP_ROOT_UNAVAILABLE",
                    "平台 Root 引导账号已存在但当前不可用",
                    HttpStatus.CONFLICT
            );
        }
        authorizationProvisioningService.provisionPlatformRoot(account.getId(), now);
    }

    private Account createAccount(String normalized, LocalDateTime now) {
        var accountId = idService.nextId();
        var account = new Account();
        account.setId(accountId);
        account.setAccountCode("ACC_" + accountId);
        account.setUsername(properties.username().trim());
        account.setUsernameNormalized(normalized);
        account.setDisplayName(properties.effectiveDisplayName());
        account.setLocale("zh-CN");
        account.setTimeZone("Asia/Shanghai");
        account.setStatus("ACTIVE");
        account.setCreatedAt(now);
        account.setCreatedBy(accountId);
        account.setUpdatedAt(now);
        account.setUpdatedBy(accountId);
        account.setVersion(0L);
        accountMapper.insert(account);
        return account;
    }

    private void createCredential(long accountId, LocalDateTime now) {
        var password = passwordService.hash(properties.password());
        var credential = new Credential();
        credential.setId(idService.nextId());
        credential.setAccountId(accountId);
        credential.setCredentialType("PASSWORD");
        credential.setPasswordHash(password.encoded());
        credential.setPasswordAlgorithm(password.algorithm());
        credential.setPasswordParameters(password.parameters());
        credential.setFailedAttempts(0);
        credential.setPasswordChangedAt(now);
        credential.setCreatedAt(now);
        credential.setUpdatedAt(now);
        credential.setVersion(0L);
        credentialMapper.insert(credential);
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}
