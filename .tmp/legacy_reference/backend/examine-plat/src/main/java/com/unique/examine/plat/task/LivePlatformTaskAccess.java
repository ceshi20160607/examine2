package com.unique.examine.plat.task;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.plat.base.mapper.PlatAccountMapper;
import com.unique.examine.plat.manage.service.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Component
public class LivePlatformTaskAccess implements PlatformTaskAccess {
    private final PlatAccountMapper accounts;
    private final AuthorizationService authorization;

    public LivePlatformTaskAccess(
            PlatAccountMapper accounts, AuthorizationService authorization) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.authorization = Objects.requireNonNull(
                authorization, "authorization");
    }

    @Override
    @Transactional(readOnly = true)
    public LiveAuthorization current(long accountId) {
        var account = accountId <= 0 ? null : accounts.selectById(accountId);
        if (account == null || !"ACTIVE".equals(account.getStatus())
                || account.getDeletedAt() != null) {
            throw new BusinessException(
                    "PLATFORM_TASK_ACCOUNT_UNAVAILABLE",
                    "The platform account is unavailable",
                    HttpStatus.FORBIDDEN);
        }
        var snapshot = authorization.platform(accountId);
        return new LiveAuthorization(snapshot.epoch(), snapshot.permissions());
    }
}
