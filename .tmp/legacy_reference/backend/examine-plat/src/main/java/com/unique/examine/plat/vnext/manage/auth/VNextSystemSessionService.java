package com.unique.examine.plat.vnext.manage.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.manage.config.SecurityProperties;
import com.unique.examine.plat.vnext.base.entity.Account;
import com.unique.examine.plat.vnext.base.entity.ContextSession;
import com.unique.examine.plat.vnext.base.entity.Member;
import com.unique.examine.plat.vnext.base.entity.RefreshToken;
import com.unique.examine.plat.vnext.base.entity.Tenant;
import com.unique.examine.plat.vnext.base.service.IVNextPlatContextSessionService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatRefreshTokenService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Shared SYSTEM-context issuer/revoker for registration and later context-entry cycles. */
@Service
public class VNextSystemSessionService {
    private final IVNextPlatContextSessionService contextService;
    private final IVNextPlatRefreshTokenService refreshService;
    private final VNextAuthTokenService tokenService;
    private final SecurityProperties properties;
    private final IdService idService;
    private final SystemContextSnapshotCodec snapshotCodec;
    private final VNextSessionService sessionService;

    public VNextSystemSessionService(
            IVNextPlatContextSessionService contextService,
            IVNextPlatRefreshTokenService refreshService,
            VNextAuthTokenService tokenService,
            SecurityProperties properties,
            IdService idService,
            ObjectMapper objectMapper,
            VNextSessionService sessionService
    ) {
        this.contextService = contextService;
        this.refreshService = refreshService;
        this.tokenService = tokenService;
        this.properties = properties;
        this.idService = idService;
        this.snapshotCodec = new SystemContextSnapshotCodec(objectMapper);
        this.sessionService = sessionService;
    }

    public SystemSessionIssue issue(
            Account account,
            com.unique.examine.plat.vnext.base.entity.System system,
            Tenant tenant,
            Member member,
            Set<String> permissions,
            Object roleSnapshot,
            Object dataScopeSnapshot,
            LocalDateTime now,
            Consumer<SystemSessionStage> observer
    ) {
        return issue(
                account.getId(), system, tenant, member, 1L, permissions,
                roleSnapshot, dataScopeSnapshot, now, observer
        );
    }

    public SystemSessionIssue issue(
            long accountId,
            com.unique.examine.plat.vnext.base.entity.System system,
            Tenant tenant,
            Member member,
            long permissionVersion,
            Set<String> permissions,
            Object roleSnapshot,
            Object dataScopeSnapshot,
            LocalDateTime now,
            Consumer<SystemSessionStage> observer
    ) {
        var rawAccess = tokenService.accessToken();
        var rawRefresh = tokenService.refreshToken();
        var context = new ContextSession();
        context.setId(idService.nextId());
        context.setTokenHash(tokenService.hash(rawAccess));
        context.setContextType(ContextType.SYSTEM.name());
        context.setAccountId(accountId);
        context.setSystemId(system.getId());
        context.setTenantId(tenant.getId());
        context.setMemberId(member.getId());
        context.setPermissionVersion(permissionVersion);
        context.setAuthzEpoch(permissionVersion);
        context.setPermissionsJson(snapshotCodec.write(permissions));
        context.setRoleSnapshotJson(snapshotCodec.write(roleSnapshot));
        context.setDataScopeSnapshotJson(snapshotCodec.write(dataScopeSnapshot));
        context.setStatus("ACTIVE");
        context.setIssuedAt(now);
        context.setExpiresAt(now.plus(properties.accessTokenTtl()));
        context.setLastSeenAt(now);
        context.setCreatedAt(now);
        context.setUpdatedAt(now);
        context.setVersion(0L);
        require(contextService.save(context), "Context session persistence");
        observer.accept(SystemSessionStage.CONTEXT_SESSION);

        var refresh = new RefreshToken();
        refresh.setId(idService.nextId());
        refresh.setContextSessionId(context.getId());
        refresh.setTokenHash(tokenService.hash(rawRefresh));
        refresh.setTokenFamily(UUID.randomUUID().toString());
        refresh.setStatus("ACTIVE");
        refresh.setIssuedAt(now);
        refresh.setExpiresAt(now.plus(properties.refreshTokenTtl()));
        refresh.setCreatedAt(now);
        require(refreshService.save(refresh), "Refresh token persistence");
        observer.accept(SystemSessionStage.REFRESH_TOKEN);
        return new SystemSessionIssue(context, refresh, rawAccess, rawRefresh, tokenService.csrfToken());
    }

    public IssuedSession complete(Account account, SystemSessionIssue issue, long firstSystemId) {
        var projected = sessionService.accountContext(account, issue.context());
        var context = new AccountContext(
                projected.account(), projected.context(), projected.systems(), projected.tenants(),
                Long.toString(firstSystemId)
        );
        return new IssuedSession(
                context, issue.accessToken(), issue.refreshToken(), issue.csrfToken(),
                properties.accessTokenTtl(), properties.refreshTokenTtl()
        );
    }

    public IssuedSession complete(SystemSessionIssue issue) {
        return issued(sessionService.accountContext(issue.context()), issue);
    }

    public void revoke(long contextSessionId, long refreshTokenId, LocalDateTime now) {
        var refresh = refreshService.getById(refreshTokenId);
        if (refresh == null || !java.util.Objects.equals(
                refresh.getContextSessionId(), contextSessionId)) {
            throw new IllegalStateException("SYSTEM refresh pointer is invalid");
        }
        var familyRevoked = refreshService.update(Wrappers.<RefreshToken>lambdaUpdate()
                .eq(RefreshToken::getTokenFamily, refresh.getTokenFamily())
                .set(RefreshToken::getStatus, "REVOKED")
                .set(RefreshToken::getRevokedAt, now));
        var contextRevoked = contextService.update(Wrappers.<ContextSession>lambdaUpdate()
                .eq(ContextSession::getId, contextSessionId)
                .set(ContextSession::getStatus, "REVOKED")
                .set(ContextSession::getRevokedAt, now)
                .set(ContextSession::getUpdatedAt, now));
        require(familyRevoked && contextRevoked, "SYSTEM session revocation");
    }

    private IssuedSession issued(AccountContext context, SystemSessionIssue issue) {
        return new IssuedSession(
                context, issue.accessToken(), issue.refreshToken(), issue.csrfToken(),
                properties.accessTokenTtl(), properties.refreshTokenTtl()
        );
    }

    private static void require(boolean result, String operation) {
        if (!result) {
            throw new IllegalStateException(operation + " was rejected");
        }
    }

    public enum SystemSessionStage {
        CONTEXT_SESSION,
        REFRESH_TOKEN
    }

    public record SystemSessionIssue(
            ContextSession context, RefreshToken refresh,
            String accessToken, String refreshToken, String csrfToken
    ) {
    }
}
