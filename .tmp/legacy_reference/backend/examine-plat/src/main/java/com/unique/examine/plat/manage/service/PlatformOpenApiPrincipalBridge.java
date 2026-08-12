package com.unique.examine.plat.manage.service;

import com.unique.examine.core.api.PlatformOpenApiPrincipalFacade;
import com.unique.examine.plat.base.mapper.PlatAccountMapper;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class PlatformOpenApiPrincipalBridge implements PlatformOpenApiPrincipalFacade {
    private final PlatAccountMapper accounts;
    private final AuthorizationService authorization;

    public PlatformOpenApiPrincipalBridge(PlatAccountMapper accounts,
                                          AuthorizationService authorization) {
        this.accounts = accounts;
        this.authorization = authorization;
    }

    @Override
    public Principal resolve(long accountId) {
        if (accountId <= 0) return denied();
        var account = accounts.selectById(accountId);
        if (account == null || !"ACTIVE".equals(account.getStatus())
                || account.getDeletedAt() != null) return denied();
        try {
            var snapshot = authorization.platform(accountId);
            return new Principal(accountId, snapshot.epoch(), true, snapshot.permissions());
        } catch (RuntimeException failure) {
            return denied();
        }
    }

    private static Principal denied() {
        return new Principal(0, 0, false, Set.of());
    }
}
