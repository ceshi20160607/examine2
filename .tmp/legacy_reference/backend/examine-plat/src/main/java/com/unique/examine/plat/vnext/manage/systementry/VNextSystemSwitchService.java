package com.unique.examine.plat.vnext.manage.systementry;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.plat.vnext.base.entity.ContextSession;
import com.unique.examine.plat.vnext.base.entity.DataScope;
import com.unique.examine.plat.vnext.base.entity.Member;
import com.unique.examine.plat.vnext.base.entity.MemberRole;
import com.unique.examine.plat.vnext.base.entity.MemberTenant;
import com.unique.examine.plat.vnext.base.entity.Permission;
import com.unique.examine.plat.vnext.base.entity.RefreshToken;
import com.unique.examine.plat.vnext.base.entity.Role;
import com.unique.examine.plat.vnext.base.entity.RolePermission;
import com.unique.examine.plat.vnext.base.entity.Tenant;
import com.unique.examine.plat.vnext.base.service.IVNextPlatContextSessionService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatDataScopeService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatMemberRoleService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatMemberService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatMemberTenantService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatPermissionService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatRefreshTokenService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatRolePermissionService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatRoleService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatSystemService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatTenantService;
import com.unique.examine.plat.vnext.manage.auth.IssuedSession;
import com.unique.examine.plat.vnext.manage.auth.VNextSystemSessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Transaction worker with the frozen eleven direct generated IService dependencies. */
@Service
public class VNextSystemSwitchService {
    private final IVNextPlatSystemService systemService;
    private final IVNextPlatTenantService tenantService;
    private final IVNextPlatMemberService memberService;
    private final IVNextPlatMemberTenantService memberTenantService;
    private final IVNextPlatMemberRoleService memberRoleService;
    private final IVNextPlatRoleService roleService;
    private final IVNextPlatDataScopeService dataScopeService;
    private final IVNextPlatRolePermissionService rolePermissionService;
    private final IVNextPlatPermissionService permissionService;
    private final IVNextPlatContextSessionService contextSessionService;
    private final IVNextPlatRefreshTokenService refreshTokenService;
    private final VNextSystemSessionService systemSessionService;

    public VNextSystemSwitchService(
            IVNextPlatSystemService systemService,
            IVNextPlatTenantService tenantService,
            IVNextPlatMemberService memberService,
            IVNextPlatMemberTenantService memberTenantService,
            IVNextPlatMemberRoleService memberRoleService,
            IVNextPlatRoleService roleService,
            IVNextPlatDataScopeService dataScopeService,
            IVNextPlatRolePermissionService rolePermissionService,
            IVNextPlatPermissionService permissionService,
            IVNextPlatContextSessionService contextSessionService,
            IVNextPlatRefreshTokenService refreshTokenService,
            VNextSystemSessionService systemSessionService
    ) {
        this.systemService = systemService;
        this.tenantService = tenantService;
        this.memberService = memberService;
        this.memberTenantService = memberTenantService;
        this.memberRoleService = memberRoleService;
        this.roleService = roleService;
        this.dataScopeService = dataScopeService;
        this.rolePermissionService = rolePermissionService;
        this.permissionService = permissionService;
        this.contextSessionService = contextSessionService;
        this.refreshTokenService = refreshTokenService;
        this.systemSessionService = systemSessionService;
    }

    @Transactional
    public IssuedSession switchSystem(RequestSession source, long systemId) {
        var now = LocalDateTime.now();
        var sourceContext = requireSource(source, now);
        var system = requireSystem(systemId);
        var member = requireMember(source.accountId(), systemId);
        var tenant = requireTenant(member, systemId);
        requireMemberTenant(member, tenant, systemId, now);
        var roles = requireRoles(member, tenant, systemId, now);
        var scope = requireCommonScope(roles, systemId);
        var grants = rolePermissions(roles, systemId);
        var permissions = permissions(grants);
        var authorization = SystemEntryAuthorizationResolver.resolve(
                system, roles, scope, grants, permissions
        );
        var issue = systemSessionService.issue(
                source.accountId(), system, tenant, member, authorization.permissionVersion(),
                authorization.permissions(), authorization.roleSnapshot(),
                authorization.dataScopeSnapshot(), now, ignored -> { }
        );
        replaceSource(sourceContext, now);
        return systemSessionService.complete(issue);
    }

    private ContextSession requireSource(RequestSession source, LocalDateTime now) {
        var context = contextSessionService.getById(source.sessionId());
        if (context == null || !Objects.equals(context.getAccountId(), source.accountId())
                || !"ACTIVE".equals(context.getStatus()) || context.getExpiresAt() == null
                || !context.getExpiresAt().isAfter(now)) {
            throw SystemEntryErrors.sessionRequired();
        }
        return context;
    }

    private com.unique.examine.plat.vnext.base.entity.System requireSystem(long systemId) {
        if (systemId <= 0) throw SystemEntryErrors.systemNotFound();
        var system = systemService.getById(systemId);
        if (system == null || "ARCHIVED".equals(system.getStatus())) throw SystemEntryErrors.systemNotFound();
        return system;
    }

    private Member requireMember(long accountId, long systemId) {
        var member = memberService.getOne(Wrappers.<Member>lambdaQuery()
                .eq(Member::getSystemId, systemId)
                .eq(Member::getAccountId, accountId), false);
        if (member == null) throw SystemEntryErrors.memberRequired();
        if (!"ACTIVE".equals(member.getStatus())) throw SystemEntryErrors.memberDisabled();
        return member;
    }

    private Tenant requireTenant(Member member, long systemId) {
        var tenant = member.getDefaultTenantId() == null
                ? null : tenantService.getById(member.getDefaultTenantId());
        if (tenant == null || !Objects.equals(tenant.getSystemId(), systemId)
                || !Boolean.TRUE.equals(tenant.getIsDefault()) || !"ACTIVE".equals(tenant.getStatus())) {
            throw SystemEntryErrors.noTenant();
        }
        return tenant;
    }

    private void requireMemberTenant(Member member, Tenant tenant, long systemId, LocalDateTime now) {
        var link = memberTenantService.getOne(Wrappers.<MemberTenant>lambdaQuery()
                .eq(MemberTenant::getSystemId, systemId)
                .eq(MemberTenant::getMemberId, member.getId())
                .eq(MemberTenant::getTenantId, tenant.getId()), false);
        if (link == null || !"ACTIVE".equals(link.getStatus())
                || link.getExpiresAt() != null && !link.getExpiresAt().isAfter(now)) {
            throw SystemEntryErrors.noTenant();
        }
    }

    private List<Role> requireRoles(Member member, Tenant tenant, long systemId, LocalDateTime now) {
        var links = memberRoleService.list(Wrappers.<MemberRole>lambdaQuery()
                .eq(MemberRole::getSystemId, systemId)
                .eq(MemberRole::getMemberId, member.getId())
                .le(MemberRole::getValidFrom, now)
                .and(query -> query.isNull(MemberRole::getValidUntil).or().gt(MemberRole::getValidUntil, now))
                .and(query -> query.isNull(MemberRole::getTenantId).or().eq(MemberRole::getTenantId, tenant.getId())));
        var ids = links.stream().map(MemberRole::getRoleId).distinct().toList();
        if (ids.isEmpty()) throw SystemEntryErrors.memberRequired();
        var roles = roleService.listByIds(ids).stream()
                .filter(role -> validRole(role, systemId, tenant.getId()))
                .toList();
        if (roles.size() != ids.size()) throw SystemEntryErrors.memberRequired();
        return roles;
    }

    private static boolean validRole(Role role, long systemId, long tenantId) {
        return "SYSTEM".equals(role.getScopeType())
                && Objects.equals(role.getScopeKey(), systemId)
                && Objects.equals(role.getSystemId(), systemId)
                && (role.getTenantId() == null || Objects.equals(role.getTenantId(), tenantId))
                && "ACTIVE".equals(role.getStatus())
                && role.getPublishedVersion() != null && role.getPublishedVersion() > 0
                && role.getDataScopeId() != null;
    }

    private DataScope requireCommonScope(List<Role> roles, long systemId) {
        var ids = roles.stream().map(Role::getDataScopeId).distinct().toList();
        if (ids.size() != 1) throw SystemEntryErrors.memberRequired();
        var scope = dataScopeService.getById(ids.getFirst());
        if (scope == null || !Objects.equals(scope.getSystemId(), systemId)) {
            throw SystemEntryErrors.memberRequired();
        }
        return scope;
    }

    private List<RolePermission> rolePermissions(List<Role> roles, long systemId) {
        return rolePermissionService.list(Wrappers.<RolePermission>lambdaQuery()
                .eq(RolePermission::getScopeType, "SYSTEM")
                .eq(RolePermission::getScopeKey, systemId)
                .in(RolePermission::getRoleId, roles.stream().map(Role::getId).toList()));
    }

    private List<Permission> permissions(List<RolePermission> grants) {
        var ids = grants.stream().map(RolePermission::getPermissionId).distinct().toList();
        return ids.isEmpty() ? List.of() : permissionService.listByIds(ids);
    }

    private void replaceSource(ContextSession source, LocalDateTime now) {
        var claimed = contextSessionService.update(Wrappers.<ContextSession>lambdaUpdate()
                .eq(ContextSession::getId, source.getId())
                .eq(ContextSession::getStatus, "ACTIVE")
                .set(ContextSession::getStatus, "REVOKED")
                .set(ContextSession::getRevokedAt, now)
                .set(ContextSession::getUpdatedAt, now));
        if (!claimed) throw SystemEntryErrors.sessionRequired();
        var refreshRevoked = refreshTokenService.update(Wrappers.<RefreshToken>lambdaUpdate()
                .eq(RefreshToken::getContextSessionId, source.getId())
                .set(RefreshToken::getStatus, "REVOKED")
                .set(RefreshToken::getRevokedAt, now));
        if (!refreshRevoked) throw new IllegalStateException("Source refresh revocation was rejected");
    }
}
