package com.unique.examine.plat.vnext.manage.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.manage.config.SecurityProperties;
import com.unique.examine.plat.vnext.base.entity.Account;
import com.unique.examine.plat.vnext.base.entity.ContextSession;
import com.unique.examine.plat.vnext.base.entity.RefreshToken;
import com.unique.examine.plat.vnext.base.service.IVNextPlatMemberService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatAccountService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatContextSessionService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatRefreshTokenService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatSystemService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatTenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class VNextSessionService {
    private static final TypeReference<Set<String>> STRING_SET = new TypeReference<>() {
    };
    private final IVNextPlatAccountService accountService;
    private final IVNextPlatContextSessionService contextSessionService;
    private final IVNextPlatRefreshTokenService refreshTokenService;
    private final VNextSecurityAuditService securityAuditService;
    private final VNextAuthTokenService tokenService;
    private final SecurityProperties properties;
    private final IdService idService;
    private final ObjectMapper objectMapper;
    private final SystemContextSnapshotCodec systemSnapshotCodec;
    private final IVNextPlatSystemService systemService;
    private final IVNextPlatTenantService tenantService;
    private final IVNextPlatMemberService memberService;
    public VNextSessionService(
            IVNextPlatAccountService accountService,
            IVNextPlatContextSessionService contextSessionService,
            IVNextPlatRefreshTokenService refreshTokenService,
            VNextSecurityAuditService securityAuditService,
            VNextAuthTokenService tokenService,
            SecurityProperties properties,
            IdService idService,
            ObjectMapper objectMapper
    ) {
        this(
                accountService, contextSessionService, refreshTokenService, securityAuditService,
                tokenService, properties, idService, objectMapper, null, null, null
        );
    }

    @Autowired
    public VNextSessionService(
            IVNextPlatAccountService accountService,
            IVNextPlatContextSessionService contextSessionService,
            IVNextPlatRefreshTokenService refreshTokenService,
            VNextSecurityAuditService securityAuditService,
            VNextAuthTokenService tokenService,
            SecurityProperties properties,
            IdService idService,
            ObjectMapper objectMapper,
            IVNextPlatSystemService systemService,
            IVNextPlatTenantService tenantService,
            IVNextPlatMemberService memberService
    ) {
        this.accountService = accountService;
        this.contextSessionService = contextSessionService;
        this.refreshTokenService = refreshTokenService;
        this.securityAuditService = securityAuditService;
        this.tokenService = tokenService;
        this.properties = properties;
        this.idService = idService;
        this.objectMapper = objectMapper;
        this.systemSnapshotCodec = new SystemContextSnapshotCodec(objectMapper);
        this.systemService = systemService;
        this.tenantService = tenantService;
        this.memberService = memberService;
    }
    IssuedSession issue(
            Account account,
            VNextPlatformPermissionResolver.PlatformAuthorization authorization
    ) {
        var now = LocalDateTime.now();
        var accessToken = tokenService.accessToken();
        var refreshToken = tokenService.refreshToken();

        var context = new ContextSession();
        context.setId(idService.nextId());
        context.setTokenHash(tokenService.hash(accessToken));
        context.setContextType(ContextType.PLATFORM.name());
        context.setAccountId(account.getId());
        context.setPermissionVersion(authorization.permissionVersion());
        context.setAuthzEpoch(authorization.permissionVersion());
        context.setPermissionsJson(writeJson(authorization.permissions()));
        context.setRoleSnapshotJson(writeJson(authorization.roleCodes()));
        context.setDataScopeSnapshotJson("{}");
        context.setStatus("ACTIVE");
        context.setIssuedAt(now);
        context.setExpiresAt(now.plus(properties.accessTokenTtl()));
        context.setLastSeenAt(now);
        context.setCreatedAt(now);
        context.setUpdatedAt(now);
        context.setVersion(0L);
        saveContext(context);

        var refresh = newRefreshToken(
                context.getId(),
                refreshToken,
                UUID.randomUUID().toString(),
                now
        );
        saveRefresh(refresh);
        return issued(account, context, accessToken, refreshToken);
    }
    @Transactional(noRollbackFor = VNextAuthException.class)
    public IssuedSession refresh(String rawRefreshToken, ClientRequest client) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            invalidRefresh(client, null);
        }
        var tokenHash = tokenService.hash(rawRefreshToken);
        var token = refreshTokenService.getOne(Wrappers.<RefreshToken>lambdaQuery()
                .eq(RefreshToken::getTokenHash, tokenHash), false);
        if (token == null) {
            invalidRefresh(client, null);
        }
        if ("USED".equals(token.getStatus())) {
            replay(token, client);
        }

        var now = LocalDateTime.now();
        if (!"ACTIVE".equals(token.getStatus()) || !token.getExpiresAt().isAfter(now)) {
            invalidRefresh(client, null);
        }
        var context = contextSessionService.getById(token.getContextSessionId());
        if (context == null || !"ACTIVE".equals(context.getStatus())) {
            invalidRefresh(client, null);
        }
        var account = requireActiveAccount(accountService.getById(context.getAccountId()), client, "SESSION_REFRESH");

        claimRefresh(token, now, client);

        var accessToken = tokenService.accessToken();
        var nextRefreshToken = tokenService.refreshToken();
        context.setTokenHash(tokenService.hash(accessToken));
        context.setIssuedAt(now);
        context.setExpiresAt(now.plus(properties.accessTokenTtl()));
        context.setLastSeenAt(now);
        context.setUpdatedAt(now);
        updateContext(context);

        var next = newRefreshToken(context.getId(), nextRefreshToken, token.getTokenFamily(), now);
        saveRefresh(next);
        var linked = refreshTokenService.update(Wrappers.<RefreshToken>lambdaUpdate()
                .eq(RefreshToken::getId, token.getId())
                .eq(RefreshToken::getStatus, "USED")
                .set(RefreshToken::getRotatedToId, next.getId()));
        if (!linked) {
            throw new IllegalStateException("Refresh token rotation link was rejected");
        }
        token.setRotatedToId(next.getId());

        securityAuditService.record("SESSION_REFRESH", account.getId(), null, "SUCCESS", null, client);
        return issued(account, context, accessToken, nextRefreshToken);
    }
    public AccountContext current(String rawAccessToken) {
        var context = requireCurrentContext(rawAccessToken);
        var account = accountService.getById(context.getAccountId());
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw new VNextAuthException(
                    "AUTH_ACCOUNT_DISABLED", "Account is unavailable", HttpStatus.UNAUTHORIZED
            );
        }
        return accountContext(account, context);
    }
    public Optional<VNextAuthenticatedSession> resolve(String rawAccessToken) {
        if (rawAccessToken == null || rawAccessToken.isBlank()) {
            return Optional.empty();
        }
        var context = findContext(rawAccessToken);
        if (!active(context, LocalDateTime.now())) {
            return Optional.empty();
        }
        var account = accountService.getById(context.getAccountId());
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            return Optional.empty();
        }
        return Optional.of(authenticated(context));
    }
    @Transactional
    public void logout(String rawAccessToken, ClientRequest client) {
        if (rawAccessToken == null || rawAccessToken.isBlank()) {
            throw sessionRequired();
        }
        var context = findContext(rawAccessToken);
        if (context == null) {
            throw sessionRequired();
        }
        revokeContext(context, LocalDateTime.now());
        securityAuditService.record("LOGOUT", context.getAccountId(), null, "SUCCESS", null, client);
    }
    private ContextSession requireCurrentContext(String rawAccessToken) {
        if (rawAccessToken == null || rawAccessToken.isBlank()) {
            throw sessionRequired();
        }
        var context = findContext(rawAccessToken);
        if (!active(context, LocalDateTime.now())) {
            throw new VNextAuthException(
                    "AUTH_SESSION_EXPIRED", "Session has expired", HttpStatus.UNAUTHORIZED
            );
        }
        return context;
    }
    private ContextSession findContext(String rawAccessToken) {
        return contextSessionService.getOne(Wrappers.<ContextSession>lambdaQuery()
                .eq(ContextSession::getTokenHash, tokenService.hash(rawAccessToken)), false);
    }

    private void replay(RefreshToken replayed, ClientRequest client) {
        var now = LocalDateTime.now();
        refreshTokenService.update(Wrappers.<RefreshToken>lambdaUpdate()
                .eq(RefreshToken::getTokenFamily, replayed.getTokenFamily())
                .set(RefreshToken::getStatus, "REVOKED")
                .set(RefreshToken::getRevokedAt, now));
        contextSessionService.update(Wrappers.<ContextSession>lambdaUpdate()
                .eq(ContextSession::getId, replayed.getContextSessionId())
                .set(ContextSession::getStatus, "REVOKED")
                .set(ContextSession::getRevokedAt, now)
                .set(ContextSession::getUpdatedAt, now));
        var context = contextSessionService.getById(replayed.getContextSessionId());
        if (context != null) {
            context.setStatus("REVOKED");
            context.setRevokedAt(now);
            context.setUpdatedAt(now);
        }
        var accountId = context == null ? null : context.getAccountId();
        securityAuditService.record(
                "SESSION_REFRESH_REPLAY", accountId, null, "DENIED", "AUTH_REFRESH_REPLAY", client
        );
        throw new VNextAuthException(
                "AUTH_REFRESH_REPLAY", "Refresh token replay was detected", HttpStatus.UNAUTHORIZED
        );
    }

    private void claimRefresh(RefreshToken token, LocalDateTime now, ClientRequest client) {
        var claimed = refreshTokenService.update(Wrappers.<RefreshToken>lambdaUpdate()
                .eq(RefreshToken::getId, token.getId())
                .eq(RefreshToken::getStatus, "ACTIVE")
                .set(RefreshToken::getStatus, "USED")
                .set(RefreshToken::getUsedAt, now));
        if (!claimed) {
            replay(token, client);
        }
        token.setStatus("USED");
        token.setUsedAt(now);
    }

    private void invalidRefresh(ClientRequest client, Long accountId) {
        securityAuditService.record(
                "SESSION_REFRESH", accountId, null, "DENIED", "AUTH_REFRESH_INVALID", client
        );
        throw new VNextAuthException(
                "AUTH_REFRESH_INVALID", "Refresh token is invalid", HttpStatus.UNAUTHORIZED
        );
    }

    private Account requireActiveAccount(Account account, ClientRequest client, String eventType) {
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            securityAuditService.record(
                    eventType,
                    account == null ? null : account.getId(),
                    null,
                    "DENIED",
                    "AUTH_ACCOUNT_DISABLED",
                    client
            );
            throw new VNextAuthException(
                    "AUTH_ACCOUNT_DISABLED", "Account is unavailable", HttpStatus.UNAUTHORIZED
            );
        }
        return account;
    }

    private RefreshToken newRefreshToken(
            long contextSessionId,
            String rawToken,
            String tokenFamily,
            LocalDateTime now
    ) {
        var refresh = new RefreshToken();
        refresh.setId(idService.nextId());
        refresh.setContextSessionId(contextSessionId);
        refresh.setTokenHash(tokenService.hash(rawToken));
        refresh.setTokenFamily(tokenFamily);
        refresh.setStatus("ACTIVE");
        refresh.setIssuedAt(now);
        refresh.setExpiresAt(now.plus(properties.refreshTokenTtl()));
        refresh.setCreatedAt(now);
        return refresh;
    }

    private IssuedSession issued(
            Account account,
            ContextSession context,
            String accessToken,
            String refreshToken
    ) {
        return new IssuedSession(
                accountContext(account, context),
                accessToken,
                refreshToken,
                tokenService.csrfToken(),
                properties.accessTokenTtl(),
                properties.refreshTokenTtl()
        );
    }

    public AccountContext accountContext(Account account, ContextSession context) {
        var accountSummary = new AccountSummary(
                Long.toString(account.getId()), account.getUsername(), account.getDisplayName()
        );
        var permissions = readPermissions(context.getPermissionsJson());
        var systemContext = ContextType.SYSTEM.name().equals(context.getContextType());
        var system = systemContext && systemService != null && context.getSystemId() != null
                ? systemService.getById(context.getSystemId()) : null;
        var tenant = systemContext && tenantService != null && context.getTenantId() != null
                ? tenantService.getById(context.getTenantId()) : null;
        var member = systemContext && memberService != null && context.getMemberId() != null
                ? memberService.getById(context.getMemberId()) : null;
        var snapshot = systemContext ? systemSnapshotCodec.restore(context)
                : SystemContextSnapshotCodec.SystemSnapshot.platform();
        var sessionContext = new SessionContext(
                context.getContextType(),
                accountSummary,
                id(context.getSystemId()),
                system == null ? null : system.getName(),
                id(context.getTenantId()),
                tenant == null ? null : tenant.getName(),
                id(context.getMemberId()),
                snapshot.roleIds(),
                snapshot.dataScope(),
                Long.toString(context.getPermissionVersion()),
                permissions,
                snapshot.restrictedMode(),
                projectShells(context.getContextType(), permissions)
        );
        if (!systemContext || system == null || tenant == null) {
            return new AccountContext(accountSummary, sessionContext, List.of(), List.of(), null);
        }
        var systemSummary = new SystemSummary(
                id(system.getId()), system.getSystemCode(), system.getName(), system.getStatus(),
                member == null ? "ACTIVE" : member.getStatus(), id(tenant.getId()),
                snapshot.roleNames(), null
        );
        var tenantSummary = new TenantSummary(
                id(tenant.getId()), tenant.getTenantCode(), tenant.getName(), tenant.getStatus(),
                Boolean.TRUE.equals(tenant.getIsDefault())
        );
        return new AccountContext(
                accountSummary, sessionContext,
                List.<Object>of(systemSummary), List.<Object>of(tenantSummary), null
        );
    }

    public AccountContext accountContext(ContextSession context) {
        var account = accountService.getById(context.getAccountId());
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw new VNextAuthException(
                    "AUTH_ACCOUNT_DISABLED", "Account is unavailable", HttpStatus.UNAUTHORIZED
            );
        }
        return accountContext(account, context);
    }

    private static List<String> projectShells(String contextType, Set<String> permissions) {
        var shells = new ArrayList<String>();
        if (ContextType.SYSTEM.name().equals(contextType)) {
            if (permissions.contains("system.runtime.access")) {
                shells.add("SYSTEM_RUNTIME");
            }
            if (permissions.contains("system.admin.access")) {
                shells.add("SYSTEM_ADMIN");
            }
        } else {
            if (permissions.contains("platform.runtime.access")) {
                shells.add("PLATFORM_RUNTIME");
            }
            if (permissions.contains("platform.admin.access")) {
                shells.add("PLATFORM_ADMIN");
            }
        }
        return List.copyOf(shells);
    }

    private static String id(Long value) {
        return value == null ? null : Long.toString(value);
    }

    private VNextAuthenticatedSession authenticated(ContextSession context) {
        return new VNextAuthenticatedSession(
                context.getId(),
                context.getAccountId(),
                ContextType.valueOf(context.getContextType()),
                context.getSystemId(),
                context.getTenantId(),
                context.getMemberId(),
                context.getPermissionVersion(),
                readPermissions(context.getPermissionsJson())
        );
    }

    private void revokeContext(ContextSession context, LocalDateTime now) {
        revokeSessionOnly(context, now);
        var tokens = refreshTokenService.list(Wrappers.<RefreshToken>lambdaQuery()
                .eq(RefreshToken::getContextSessionId, context.getId()));
        for (var token : tokens) {
            if (!"REVOKED".equals(token.getStatus())) {
                token.setStatus("REVOKED");
                token.setRevokedAt(now);
                updateRefresh(token);
            }
        }
    }

    private void revokeSessionOnly(ContextSession context, LocalDateTime now) {
        if (!"REVOKED".equals(context.getStatus())) {
            context.setStatus("REVOKED");
            context.setRevokedAt(now);
            context.setUpdatedAt(now);
            updateContext(context);
        }
    }

    private void saveContext(ContextSession context) {
        if (!contextSessionService.save(context)) {
            throw new IllegalStateException("Context session persistence was rejected");
        }
    }

    private void updateContext(ContextSession context) {
        if (!contextSessionService.updateById(context)) {
            throw new IllegalStateException("Context session update was rejected");
        }
    }

    private void saveRefresh(RefreshToken refreshToken) {
        if (!refreshTokenService.save(refreshToken)) {
            throw new IllegalStateException("Refresh token persistence was rejected");
        }
    }

    private void updateRefresh(RefreshToken refreshToken) {
        if (!refreshTokenService.updateById(refreshToken)) {
            throw new IllegalStateException("Refresh token update was rejected");
        }
    }

    private static boolean active(ContextSession context, LocalDateTime now) {
        return context != null
                && "ACTIVE".equals(context.getStatus())
                && context.getExpiresAt() != null
                && context.getExpiresAt().isAfter(now);
    }

    private static VNextAuthException sessionRequired() {
        return new VNextAuthException(
                "AUTH_SESSION_REQUIRED", "An authenticated session is required", HttpStatus.UNAUTHORIZED
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize authorization snapshot", exception);
        }
    }

    private Set<String> readPermissions(String json) {
        try {
            return objectMapper.readValue(json, STRING_SET);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read authorization snapshot", exception);
        }
    }

}
