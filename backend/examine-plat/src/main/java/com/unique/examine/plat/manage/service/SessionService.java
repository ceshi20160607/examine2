package com.unique.examine.plat.manage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.api.AuditEvent;
import com.unique.examine.core.api.AuditFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.base.entity.ContextSession;
import com.unique.examine.plat.base.entity.Member;
import com.unique.examine.plat.base.entity.MemberTenant;
import com.unique.examine.plat.base.entity.RefreshToken;
import com.unique.examine.plat.base.entity.Tenant;
import com.unique.examine.plat.base.mapper.PlatAccountMapper;
import com.unique.examine.plat.base.mapper.PlatContextSessionMapper;
import com.unique.examine.plat.base.mapper.PlatMemberMapper;
import com.unique.examine.plat.base.mapper.PlatMemberTenantMapper;
import com.unique.examine.plat.base.mapper.PlatRefreshTokenMapper;
import com.unique.examine.plat.base.mapper.PlatSystemMapper;
import com.unique.examine.plat.base.mapper.PlatTenantMapper;
import com.unique.examine.plat.manage.config.SecurityProperties;
import com.unique.examine.plat.manage.security.TokenService;
import com.unique.examine.plat.manage.vo.AccountSummaryVo;
import com.unique.examine.plat.manage.vo.AuthResultVo;
import com.unique.examine.plat.manage.vo.SessionContextVo;
import com.unique.examine.plat.manage.vo.SystemSummaryVo;
import com.unique.examine.plat.manage.vo.TenantSummaryVo;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class SessionService {
    private static final Set<String> PLATFORM_ADMIN_PAGE_PERMISSIONS = Set.of(
            "platform.admin.access",
            "platform.system.manage",
            "platform.organization.manage",
            "platform.role.manage",
            "platform.ai.policy.manage"
    );
    private static final Set<String> SYSTEM_ADMIN_PAGE_PERMISSIONS = Set.of(
            "system.admin.access",
            "system.settings.manage",
            "system.tenant.manage",
            "system.organization.manage",
            "system.member.manage",
            "system.role.manage",
            "system.access.review",
            "event.template.manage",
            "ai.policy.manage",
            "openapi.application.manage"
    );
    private static final String SESSION_KEY_PREFIX = "examine:session:";
    private static final TypeReference<Set<String>> STRING_SET = new TypeReference<>() {
    };

    private final PlatAccountMapper accountMapper;
    private final PlatContextSessionMapper contextSessionMapper;
    private final PlatRefreshTokenMapper refreshTokenMapper;
    private final PlatSystemMapper systemMapper;
    private final PlatTenantMapper tenantMapper;
    private final PlatMemberMapper memberMapper;
    private final PlatMemberTenantMapper memberTenantMapper;
    private final AuthorizationService authorizationService;
    private final AuthzEpochService epochService;
    private final IdService idService;
    private final TokenService tokenService;
    private final SecurityProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuditFacade auditFacade;

    public SessionService(
            PlatAccountMapper accountMapper,
            PlatContextSessionMapper contextSessionMapper,
            PlatRefreshTokenMapper refreshTokenMapper,
            PlatSystemMapper systemMapper,
            PlatTenantMapper tenantMapper,
            PlatMemberMapper memberMapper,
            PlatMemberTenantMapper memberTenantMapper,
            AuthorizationService authorizationService,
            AuthzEpochService epochService,
            IdService idService,
            TokenService tokenService,
            SecurityProperties properties,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            AuditFacade auditFacade
    ) {
        this.accountMapper = accountMapper;
        this.contextSessionMapper = contextSessionMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.systemMapper = systemMapper;
        this.tenantMapper = tenantMapper;
        this.memberMapper = memberMapper;
        this.memberTenantMapper = memberTenantMapper;
        this.authorizationService = authorizationService;
        this.epochService = epochService;
        this.idService = idService;
        this.tokenService = tokenService;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.auditFacade = auditFacade;
    }

    @Transactional
    public IssuedSession issuePlatform(Account account) {
        requireActiveAccount(account);
        return issuePlatformInternal(account, UUID.randomUUID().toString());
    }

    @Transactional
    public IssuedSession switchPlatform(AuthenticatedSession current, ClientRequest client) {
        var account = requireActiveAccount(accountMapper.selectById(current.accountId()));
        revokeContext(current.sessionId());
        var issued = issuePlatformInternal(account, UUID.randomUUID().toString());
        audit("CONTEXT_SWITCH_PLATFORM", current.accountId(), null, null, client);
        return issued;
    }

    @Transactional
    public IssuedSession switchSystem(AuthenticatedSession current, long systemId, ClientRequest client) {
        var account = requireActiveAccount(accountMapper.selectById(current.accountId()));
        var target = systemTarget(account.getId(), systemId, null);
        revokeContext(current.sessionId());
        var issued = issueSystemInternal(account, target, UUID.randomUUID().toString());
        audit("CONTEXT_SWITCH_SYSTEM", current.accountId(), target.system().getId(), target.tenant().getId(), client);
        return issued;
    }

    @Transactional
    public IssuedSession switchTenant(AuthenticatedSession current, long tenantId, ClientRequest client) {
        if (current.contextType() != ContextType.SYSTEM || current.systemId() == null) {
            throw new BusinessException("CONTEXT_SYSTEM_MISMATCH", "请先进入系统上下文", HttpStatus.FORBIDDEN);
        }
        var account = requireActiveAccount(accountMapper.selectById(current.accountId()));
        var target = systemTarget(account.getId(), current.systemId(), tenantId);
        revokeContext(current.sessionId());
        var issued = issueSystemInternal(account, target, UUID.randomUUID().toString());
        audit("CONTEXT_SWITCH_TENANT", current.accountId(), target.system().getId(), target.tenant().getId(), client);
        return issued;
    }

    @Transactional
    public IssuedSession refresh(String rawRefreshToken, ClientRequest client) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException("REFRESH_TOKEN_INVALID", "登录状态已失效", HttpStatus.UNAUTHORIZED);
        }
        var token = refreshTokenMapper.selectOne(Wrappers.<RefreshToken>lambdaQuery()
                .eq(RefreshToken::getTokenHash, tokenService.hash(rawRefreshToken)));
        var now = LocalDateTime.now();
        if (token == null || !"ACTIVE".equals(token.getStatus()) || !token.getExpiresAt().isAfter(now)) {
            throw new BusinessException("REFRESH_TOKEN_INVALID", "登录状态已失效", HttpStatus.UNAUTHORIZED);
        }
        var oldContext = contextSessionMapper.selectById(token.getContextSessionId());
        if (oldContext == null || !"ACTIVE".equals(oldContext.getStatus())) {
            throw new BusinessException("REFRESH_TOKEN_INVALID", "登录状态已失效", HttpStatus.UNAUTHORIZED);
        }
        var account = requireActiveAccount(accountMapper.selectById(oldContext.getAccountId()));

        token.setStatus("USED");
        token.setUsedAt(now);
        refreshTokenMapper.updateById(token);
        revokeSessionOnly(oldContext);

        IssuedSession issued;
        if ("SYSTEM".equals(oldContext.getContextType())) {
            var target = systemTarget(account.getId(), oldContext.getSystemId(), oldContext.getTenantId());
            issued = issueSystemInternal(account, target, token.getTokenFamily());
        } else {
            issued = issuePlatformInternal(account, token.getTokenFamily());
        }
        var nextToken = refreshTokenMapper.selectOne(Wrappers.<RefreshToken>lambdaQuery()
                .eq(RefreshToken::getTokenHash, tokenService.hash(issued.refreshToken())));
        token.setRotatedToId(nextToken.getId());
        refreshTokenMapper.updateById(token);
        audit("SESSION_REFRESH", account.getId(), oldContext.getSystemId(), oldContext.getTenantId(), client);
        return issued;
    }

    @Transactional
    public void logout(AuthenticatedSession current, ClientRequest client) {
        revokeContext(current.sessionId());
        audit("LOGOUT", current.accountId(), current.systemId(), current.tenantId(), client);
    }

    @Transactional
    public void revokeAllForAccount(long accountId) {
        var contexts = contextSessionMapper.selectList(Wrappers.<ContextSession>lambdaQuery()
                .eq(ContextSession::getAccountId, accountId)
                .eq(ContextSession::getStatus, "ACTIVE"));
        for (var context : contexts) {
            revokeContext(context.getId());
        }
    }

    public List<OwnedSessionView> listOwnedSessions(AuthenticatedSession current) {
        return contextSessionMapper.selectList(Wrappers.<ContextSession>lambdaQuery()
                        .eq(ContextSession::getAccountId, current.accountId())
                        .in(ContextSession::getStatus, List.of("ACTIVE", "REVOKED"))
                        .orderByDesc(ContextSession::getIssuedAt))
                .stream().limit(30).map(session -> new OwnedSessionView(
                        session.getId().toString(), session.getContextType(), id(session.getSystemId()),
                        id(session.getTenantId()), session.getStatus(), session.getId() == current.sessionId(),
                        session.getIssuedAt(), session.getLastSeenAt(), session.getExpiresAt(), session.getRevokedAt()))
                .toList();
    }

    @Transactional
    public boolean revokeOwnedSession(AuthenticatedSession current, long sessionId, ClientRequest client) {
        var target = contextSessionMapper.selectById(sessionId);
        if (target == null || !target.getAccountId().equals(current.accountId())) {
            throw new BusinessException("SESSION_NOT_FOUND", "会话不存在", HttpStatus.NOT_FOUND);
        }
        if ("ACTIVE".equals(target.getStatus())) revokeContext(sessionId);
        audit("SESSION_REVOKE", current.accountId(), target.getSystemId(), target.getTenantId(), client);
        return sessionId == current.sessionId();
    }

    @Transactional
    public void revokeOtherSessions(AuthenticatedSession current, ClientRequest client) {
        var contexts = contextSessionMapper.selectList(Wrappers.<ContextSession>lambdaQuery()
                .eq(ContextSession::getAccountId, current.accountId())
                .eq(ContextSession::getStatus, "ACTIVE")
                .ne(ContextSession::getId, current.sessionId()));
        for (var context : contexts) revokeContext(context.getId());
        audit("SESSION_REVOKE_OTHERS", current.accountId(), current.systemId(), current.tenantId(), client);
    }

    public Optional<AuthenticatedSession> resolve(String rawAccessToken) {
        if (rawAccessToken == null || rawAccessToken.isBlank()) {
            return Optional.empty();
        }
        var hash = tokenService.hash(rawAccessToken);
        var cacheKey = SESSION_KEY_PREFIX + hash;
        var sessionId = redisTemplate.opsForValue().get(cacheKey);
        ContextSession entity;
        if (sessionId == null) {
            entity = contextSessionMapper.selectOne(Wrappers.<ContextSession>lambdaQuery()
                    .eq(ContextSession::getTokenHash, hash));
        } else {
            try {
                entity = contextSessionMapper.selectById(Long.parseLong(sessionId));
            } catch (NumberFormatException exception) {
                redisTemplate.delete(cacheKey);
                return Optional.empty();
            }
        }
        if (entity == null || !"ACTIVE".equals(entity.getStatus())
                || !entity.getExpiresAt().isAfter(LocalDateTime.now())) {
            redisTemplate.delete(cacheKey);
            return Optional.empty();
        }
        var account = accountMapper.selectById(entity.getAccountId());
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            redisTemplate.delete(cacheKey);
            throw new BusinessException("ACCOUNT_UNAVAILABLE", "账号当前不可用", HttpStatus.UNAUTHORIZED);
        }
        if (!authorizationFactsCurrent(entity)) {
            redisTemplate.delete(cacheKey);
            throw new BusinessException(
                    "AUTHZ_SNAPSHOT_STALE",
                    "权限或成员状态已变化，请重新进入上下文",
                    HttpStatus.UNAUTHORIZED
            );
        }
        redisTemplate.opsForValue().set(cacheKey, entity.getId().toString(), properties.accessTokenTtl());
        return Optional.of(toAuthenticated(entity));
    }

    public AuthResultVo currentResult(AuthenticatedSession current) {
        var account = requireActiveAccount(accountMapper.selectById(current.accountId()));
        var entity = contextSessionMapper.selectById(current.sessionId());
        if (!validSession(entity)) {
            throw new BusinessException("SESSION_EXPIRED", "登录状态已失效", HttpStatus.UNAUTHORIZED);
        }
        return buildResult(account, entity, null);
    }

    public List<SystemSummaryVo> listSystems(long accountId) {
        var members = memberMapper.selectList(Wrappers.<Member>lambdaQuery()
                .eq(Member::getAccountId, accountId)
                .orderByDesc(Member::getJoinedAt));
        if (members.isEmpty()) {
            return List.of();
        }
        var systems = systemMapper.selectByIds(members.stream().map(Member::getSystemId).distinct().toList());
        var byId = new java.util.HashMap<Long, com.unique.examine.plat.base.entity.System>();
        systems.forEach(system -> byId.put(system.getId(), system));
        var result = new ArrayList<SystemSummaryVo>();
        for (var member : members) {
            var system = byId.get(member.getSystemId());
            if (system == null) {
                continue;
            }
            result.add(new SystemSummaryVo(
                    id(system.getId()),
                    system.getSystemCode(),
                    system.getName(),
                    system.getStatus(),
                    member.getStatus(),
                    id(member.getDefaultTenantId())
            ));
        }
        return List.copyOf(result);
    }

    /**
     * Reuses the exact system-switch resolver to expose only targets the
     * current platform account can enter now. No tenant identity is projected.
     */
    @Transactional(readOnly = true)
    public List<AuthorizedSystemProjection> listAuthorizedSystems(long accountId) {
        requireActiveAccount(accountMapper.selectById(accountId));
        var result = new java.util.LinkedHashMap<Long, AuthorizedSystemProjection>();
        for (var summary : listSystems(accountId)) {
            var systemId = Long.parseLong(summary.id());
            try {
                var target = systemTarget(accountId, systemId, null);
                result.putIfAbsent(systemId, new AuthorizedSystemProjection(
                        target.system().getId(), target.system().getSystemCode(),
                        target.system().getName(), target.system().getStatus(),
                        target.member().getStatus(), "AUTHORIZED",
                        "/api/v1/context/systems/" + systemId + ":switch"));
            } catch (BusinessException unavailable) {
                // A target rejected by the live switch resolver is not currently authorized.
            }
        }
        return List.copyOf(result.values());
    }

    public List<TenantSummaryVo> listTenants(AuthenticatedSession current) {
        if (current.contextType() != ContextType.SYSTEM || current.systemId() == null || current.memberId() == null) {
            throw new BusinessException("CONTEXT_SYSTEM_MISMATCH", "请先进入系统上下文", HttpStatus.FORBIDDEN);
        }
        var now = LocalDateTime.now();
        var access = memberTenantMapper.selectList(Wrappers.<MemberTenant>lambdaQuery()
                .eq(MemberTenant::getSystemId, current.systemId())
                .eq(MemberTenant::getMemberId, current.memberId())
                .eq(MemberTenant::getStatus, "ACTIVE")
                .and(query -> query.isNull(MemberTenant::getExpiresAt).or().gt(MemberTenant::getExpiresAt, now)));
        if (access.isEmpty()) {
            return List.of();
        }
        var tenants = tenantMapper.selectByIds(access.stream().map(MemberTenant::getTenantId).distinct().toList());
        return tenants.stream()
                .filter(tenant -> tenant.getSystemId().equals(current.systemId()))
                .map(tenant -> new TenantSummaryVo(
                        id(tenant.getId()),
                        tenant.getTenantCode(),
                        tenant.getName(),
                        tenant.getStatus(),
                        Boolean.TRUE.equals(tenant.getIsDefault())
                ))
                .toList();
    }

    private IssuedSession issuePlatformInternal(Account account, String tokenFamily) {
        return issue(account, ContextType.PLATFORM, null, null, null, authorizationService.platform(account.getId()), tokenFamily);
    }

    private IssuedSession issueSystemInternal(Account account, SystemTarget target, String tokenFamily) {
        return issue(
                account,
                ContextType.SYSTEM,
                target.system().getId(),
                target.tenant().getId(),
                target.member().getId(),
                target.authorization(),
                tokenFamily
        );
    }

    private IssuedSession issue(
            Account account,
            ContextType type,
            Long systemId,
            Long tenantId,
            Long memberId,
            AuthorizationSnapshot authorization,
            String tokenFamily
    ) {
        var now = LocalDateTime.now();
        var accessToken = tokenService.accessToken();
        var refreshToken = tokenService.refreshToken();
        var context = new ContextSession();
        context.setId(idService.nextId());
        context.setTokenHash(tokenService.hash(accessToken));
        context.setContextType(type.name());
        context.setAccountId(account.getId());
        context.setSystemId(systemId);
        context.setTenantId(tenantId);
        context.setMemberId(memberId);
        context.setPermissionVersion(authorization.epoch());
        context.setAuthzEpoch(authorization.epoch());
        context.setPermissionsJson(writeJson(authorization.permissions()));
        context.setRoleSnapshotJson(writeJson(authorization.roles()));
        context.setDataScopeSnapshotJson(writeJson(authorization.dataScopes()));
        context.setStatus("ACTIVE");
        context.setIssuedAt(now);
        context.setExpiresAt(now.plus(properties.accessTokenTtl()));
        context.setLastSeenAt(now);
        context.setCreatedAt(now);
        context.setUpdatedAt(now);
        context.setVersion(0L);
        contextSessionMapper.insert(context);

        var refresh = new RefreshToken();
        refresh.setId(idService.nextId());
        refresh.setContextSessionId(context.getId());
        refresh.setTokenHash(tokenService.hash(refreshToken));
        refresh.setTokenFamily(tokenFamily);
        refresh.setStatus("ACTIVE");
        refresh.setIssuedAt(now);
        refresh.setExpiresAt(now.plus(properties.refreshTokenTtl()));
        refresh.setCreatedAt(now);
        refreshTokenMapper.insert(refresh);

        redisTemplate.opsForValue().set(
                SESSION_KEY_PREFIX + context.getTokenHash(),
                context.getId().toString(),
                properties.accessTokenTtl()
        );
        return new IssuedSession(
                buildResult(account, context, null),
                accessToken,
                refreshToken,
                tokenService.csrfToken(),
                properties.accessTokenTtl(),
                properties.refreshTokenTtl()
        );
    }

    private AuthResultVo buildResult(Account account, ContextSession context, String firstSystemId) {
        String systemName = null;
        String tenantName = null;
        if (context.getSystemId() != null) {
            var system = systemMapper.selectById(context.getSystemId());
            systemName = system == null ? null : system.getName();
            var tenant = tenantMapper.selectById(context.getTenantId());
            tenantName = tenant == null ? null : tenant.getName();
        }
        var permissions = readPermissions(context.getPermissionsJson());
        var shells = projectShells(context.getContextType(), permissions);
        var accountVo = new AccountSummaryVo(id(account.getId()), account.getUsername(), account.getDisplayName());
        var contextVo = new SessionContextVo(
                context.getContextType(),
                accountVo,
                id(context.getSystemId()),
                systemName,
                id(context.getTenantId()),
                tenantName,
                id(context.getMemberId()),
                Long.toString(context.getPermissionVersion()),
                permissions,
                shells
        );
        var tenants = "SYSTEM".equals(context.getContextType())
                ? listTenants(toAuthenticated(context))
                : List.<TenantSummaryVo>of();
        return new AuthResultVo(accountVo, contextVo, listSystems(account.getId()), tenants, firstSystemId);
    }

    static List<String> projectShells(String contextType, Collection<String> permissions) {
        var shells = new ArrayList<String>();
        if ("PLATFORM".equals(contextType)) {
            shells.add("PLATFORM_RUNTIME");
            if (permissions.stream().anyMatch(PLATFORM_ADMIN_PAGE_PERMISSIONS::contains)) {
                shells.add("PLATFORM_ADMIN");
            }
        } else {
            if (permissions.contains("system.runtime.access")) {
                shells.add("SYSTEM_RUNTIME");
            }
            if (permissions.stream().anyMatch(SYSTEM_ADMIN_PAGE_PERMISSIONS::contains)) {
                shells.add("SYSTEM_ADMIN");
            }
        }
        return shells;
    }

    private boolean validSession(ContextSession context) {
        if (context == null || !"ACTIVE".equals(context.getStatus()) || !context.getExpiresAt().isAfter(LocalDateTime.now())) {
            return false;
        }
        var account = accountMapper.selectById(context.getAccountId());
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            return false;
        }
        return authorizationFactsCurrent(context);
    }

    private boolean authorizationFactsCurrent(ContextSession context) {
        if ("PLATFORM".equals(context.getContextType())) {
            return context.getAuthzEpoch().equals(epochService.currentPlatform());
        }
        var system = systemMapper.selectById(context.getSystemId());
        var member = memberMapper.selectById(context.getMemberId());
        var tenant = tenantMapper.selectById(context.getTenantId());
        return system != null
                && Set.of("ACTIVE", "INITIALIZING").contains(system.getStatus())
                && context.getAuthzEpoch().equals(epochService.currentSystem(system.getId()))
                && context.getPermissionVersion().equals(context.getAuthzEpoch())
                && member != null
                && "ACTIVE".equals(member.getStatus())
                && member.getSystemId().equals(system.getId())
                && tenant != null
                && "ACTIVE".equals(tenant.getStatus())
                && tenant.getSystemId().equals(system.getId());
    }

    private AuthenticatedSession toAuthenticated(ContextSession context) {
        return new AuthenticatedSession(
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

    private SystemTarget systemTarget(long accountId, long systemId, Long requestedTenantId) {
        var system = systemMapper.selectById(systemId);
        if (system == null || !Set.of("ACTIVE", "INITIALIZING").contains(system.getStatus())) {
            throw new BusinessException("SYSTEM_UNAVAILABLE", "系统不存在或当前不可用", HttpStatus.NOT_FOUND);
        }
        var member = memberMapper.selectOne(Wrappers.<Member>lambdaQuery()
                .eq(Member::getSystemId, systemId)
                .eq(Member::getAccountId, accountId));
        if (member == null || !"ACTIVE".equals(member.getStatus())) {
            throw new BusinessException("SYSTEM_MEMBER_REQUIRED", "当前账号没有有效系统成员身份", HttpStatus.FORBIDDEN);
        }
        var tenantId = requestedTenantId == null ? member.getDefaultTenantId() : requestedTenantId;
        if (tenantId == null) {
            throw new BusinessException(
                    "TENANT_UNAVAILABLE", "Default tenant is unavailable", HttpStatus.FORBIDDEN);
        }
        var tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || !"ACTIVE".equals(tenant.getStatus()) || !tenant.getSystemId().equals(systemId)) {
            throw new BusinessException("TENANT_UNAVAILABLE", "默认租户当前不可用", HttpStatus.FORBIDDEN);
        }
        return new SystemTarget(
                system,
                tenant,
                member,
                authorizationService.system(systemId, tenant.getId(), member.getId())
        );
    }

    private void revokeContext(long contextId) {
        var context = contextSessionMapper.selectById(contextId);
        if (context == null) {
            return;
        }
        revokeSessionOnly(context);
        var now = LocalDateTime.now();
        var tokens = refreshTokenMapper.selectList(Wrappers.<RefreshToken>lambdaQuery()
                .eq(RefreshToken::getContextSessionId, contextId)
                .eq(RefreshToken::getStatus, "ACTIVE"));
        for (var token : tokens) {
            token.setStatus("REVOKED");
            token.setRevokedAt(now);
            if (refreshTokenMapper.updateById(token) != 1) {
                throw revocationConflict();
            }
        }
    }

    private void revokeSessionOnly(ContextSession context) {
        var now = LocalDateTime.now();
        context.setStatus("REVOKED");
        context.setRevokedAt(now);
        context.setUpdatedAt(now);
        if (contextSessionMapper.updateById(context) != 1) {
            throw revocationConflict();
        }
        redisTemplate.delete(SESSION_KEY_PREFIX + context.getTokenHash());
    }

    private static BusinessException revocationConflict() {
        return new BusinessException(
                "SESSION_REVOCATION_CONFLICT",
                "Session changed concurrently; retry the operation",
                HttpStatus.CONFLICT
        );
    }

    private Account requireActiveAccount(Account account) {
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw new BusinessException("ACCOUNT_UNAVAILABLE", "账号当前不可用", HttpStatus.UNAUTHORIZED);
        }
        return account;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize permission snapshot", exception);
        }
    }

    private Set<String> readPermissions(String value) {
        try {
            return Set.copyOf(objectMapper.readValue(value, STRING_SET));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read permission snapshot", exception);
        }
    }

    private static String id(Long value) {
        return value == null ? null : Long.toString(value);
    }

    private void audit(String eventType, long accountId, Long systemId, Long tenantId, ClientRequest client) {
        auditFacade.recordSecurity(new AuditEvent(
                eventType,
                accountId,
                null,
                systemId,
                tenantId,
                "WEB",
                client.remoteAddress(),
                client.userAgent(),
                client.requestId(),
                client.traceId(),
                "SUCCESS",
                null,
                "{}"
        ));
    }

    private record SystemTarget(
            com.unique.examine.plat.base.entity.System system,
            Tenant tenant,
            Member member,
            AuthorizationSnapshot authorization
    ) {
    }

    public record AuthorizedSystemProjection(
            long systemId,
            String systemCode,
            String systemName,
            String status,
            String membershipState,
            String accessState,
            String switchTarget
    ) {
    }

    public record OwnedSessionView(String id, String contextType, String systemId, String tenantId,
                                   String status, boolean current, LocalDateTime issuedAt,
                                   LocalDateTime lastSeenAt, LocalDateTime expiresAt,
                                   LocalDateTime revokedAt) { }
}
