package com.unique.examine.plat.manage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.core.api.OpenApiPrincipalFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.plat.base.entity.Member;
import com.unique.examine.plat.base.entity.MemberTenant;
import com.unique.examine.plat.base.entity.System;
import com.unique.examine.plat.base.entity.Tenant;
import com.unique.examine.plat.base.mapper.PlatMemberMapper;
import com.unique.examine.plat.base.mapper.PlatMemberTenantMapper;
import com.unique.examine.plat.base.mapper.PlatSystemMapper;
import com.unique.examine.plat.base.mapper.PlatTenantMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class OpenApiPrincipalBridge implements OpenApiPrincipalFacade {
    private final PlatSystemMapper systemMapper;
    private final PlatTenantMapper tenantMapper;
    private final PlatMemberMapper memberMapper;
    private final PlatMemberTenantMapper memberTenantMapper;
    private final AuthorizationService authorizationService;

    public OpenApiPrincipalBridge(
            PlatSystemMapper systemMapper,
            PlatTenantMapper tenantMapper,
            PlatMemberMapper memberMapper,
            PlatMemberTenantMapper memberTenantMapper,
            AuthorizationService authorizationService
    ) {
        this.systemMapper = systemMapper;
        this.tenantMapper = tenantMapper;
        this.memberMapper = memberMapper;
        this.memberTenantMapper = memberTenantMapper;
        this.authorizationService = authorizationService;
    }

    @Override
    public Principal resolve(long systemId, long tenantId, long memberId) {
        if (systemId <= 0 || tenantId <= 0 || memberId <= 0) {
            return denied();
        }
        var system = systemMapper.selectById(systemId);
        if (system == null) {
            return denied();
        }
        var permissionVersion = positive(system.getPermissionVersion());
        var systemActive = active(system);
        var tenant = tenantMapper.selectOne(Wrappers.<Tenant>lambdaQuery()
                .eq(Tenant::getId, tenantId)
                .eq(Tenant::getSystemId, systemId));
        var tenantActive = active(tenant);
        var member = memberMapper.selectOne(Wrappers.<Member>lambdaQuery()
                .eq(Member::getId, memberId)
                .eq(Member::getSystemId, systemId));
        var membership = memberTenantMapper.selectOne(Wrappers.<MemberTenant>lambdaQuery()
                .eq(MemberTenant::getSystemId, systemId)
                .eq(MemberTenant::getTenantId, tenantId)
                .eq(MemberTenant::getMemberId, memberId));
        var memberActive = active(member) && active(membership);
        if (!systemActive || !tenantActive || !memberActive) {
            return new Principal(
                    0,
                    permissionVersion,
                    systemActive,
                    tenantActive,
                    memberActive,
                    Set.of()
            );
        }
        try {
            var authorization = authorizationService.system(systemId, tenantId, memberId);
            return new Principal(
                    positive(member.getAccountId()),
                    authorization.epoch(),
                    true,
                    true,
                    true,
                    authorization.permissions()
            );
        } catch (BusinessException exception) {
            return new Principal(
                    0,
                    permissionVersion,
                    true,
                    true,
                    false,
                    Set.of()
            );
        }
    }

    private static boolean active(System value) {
        return value != null && "ACTIVE".equals(value.getStatus()) && value.getDeletedAt() == null;
    }

    private static boolean active(Tenant value) {
        return value != null && "ACTIVE".equals(value.getStatus()) && value.getDeletedAt() == null;
    }

    private static boolean active(Member value) {
        return value != null && "ACTIVE".equals(value.getStatus()) && value.getDeletedAt() == null;
    }

    private static boolean active(MemberTenant value) {
        return value != null
                && "ACTIVE".equals(value.getStatus())
                && value.getDeletedAt() == null
                && (value.getExpiresAt() == null || value.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    private static long positive(Long value) {
        return value == null || value <= 0 ? 0 : value;
    }

    private static Principal denied() {
        return new Principal(0, 0, false, false, false, Set.of());
    }
}
