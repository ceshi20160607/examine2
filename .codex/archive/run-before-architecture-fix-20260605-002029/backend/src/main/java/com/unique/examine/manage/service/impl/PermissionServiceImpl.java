package com.unique.examine.manage.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.base.entity.MemberRole;
import com.unique.examine.base.entity.RolePermission;
import com.unique.examine.base.entity.SystemMember;
import com.unique.examine.base.service.IMemberRoleService;
import com.unique.examine.base.service.IRolePermissionService;
import com.unique.examine.base.service.ISystemMemberService;
import com.unique.examine.manage.enums.ErrorCode;
import com.unique.examine.manage.enums.StatusEnums;
import com.unique.examine.manage.exception.BusinessException;
import com.unique.examine.manage.security.CurrentUser;
import com.unique.examine.manage.security.SecurityContext;
import com.unique.examine.manage.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {
    private final ISystemMemberService systemMemberService;
    private final IMemberRoleService memberRoleService;
    private final IRolePermissionService rolePermissionService;

    @Override
    public void requireScope(Long systemId, Long tenantId) {
        CurrentUser currentUser = SecurityContext.currentUser();
        if (currentUser.getSystemId() == null || currentUser.getTenantId() == null
                || !Objects.equals(currentUser.getSystemId(), systemId)
                || !Objects.equals(currentUser.getTenantId(), tenantId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请求上下文与当前用户系统/租户不一致");
        }
        getEnabledSystemMember(systemId, tenantId, currentUser.getAccountId());
    }

    @Override
    public void requireAction(Long systemId, Long tenantId, String actionCode) {
        requireScope(systemId, tenantId);
        CurrentUser currentUser = SecurityContext.currentUser();
        SystemMember member = getEnabledSystemMember(systemId, tenantId, currentUser.getAccountId());
        List<Long> roleIds = memberRoleService.list(Wrappers.<MemberRole>lambdaQuery()
                        .eq(MemberRole::getMemberId, member.getId()))
                .stream().map(MemberRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号未分配系统内角色");
        }
        long count = rolePermissionService.count(Wrappers.<RolePermission>lambdaQuery()
                .in(RolePermission::getRoleId, roleIds)
                .eq(RolePermission::getSystemId, systemId)
                .eq(RolePermission::getTenantId, tenantId)
                .eq(RolePermission::getResourceType, "ACTION")
                .eq(RolePermission::getActionCode, actionCode));
        if (count == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少操作权限：" + actionCode);
        }
    }

    @Override
    public void requirePlatformAction(String actionCode) {
        CurrentUser currentUser = SecurityContext.currentUser();
        if (isPlatformAdmin(currentUser)) {
            return;
        }
        SystemMember member = systemMemberService.getOne(Wrappers.<SystemMember>lambdaQuery()
                .eq(SystemMember::getSystemId, 0L)
                .eq(SystemMember::getTenantId, 0L)
                .eq(SystemMember::getAccountId, currentUser.getAccountId())
                .eq(SystemMember::getStatus, StatusEnums.ENABLED), false);
        if (member == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少平台管理权限");
        }
        List<Long> roleIds = memberRoleService.list(Wrappers.<MemberRole>lambdaQuery()
                        .eq(MemberRole::getMemberId, member.getId()))
                .stream().map(MemberRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少平台管理角色");
        }
        long count = rolePermissionService.count(Wrappers.<RolePermission>lambdaQuery()
                .in(RolePermission::getRoleId, roleIds)
                .eq(RolePermission::getSystemId, 0L)
                .eq(RolePermission::getTenantId, 0L)
                .eq(RolePermission::getResourceType, "ACTION")
                .eq(RolePermission::getActionCode, actionCode));
        if (count == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少平台操作权限：" + actionCode);
        }
    }

    private SystemMember getEnabledSystemMember(Long systemId, Long tenantId, Long accountId) {
        SystemMember member = systemMemberService.getOne(Wrappers.<SystemMember>lambdaQuery()
                .eq(SystemMember::getSystemId, systemId)
                .eq(SystemMember::getTenantId, tenantId)
                .eq(SystemMember::getAccountId, accountId)
                .eq(SystemMember::getStatus, StatusEnums.ENABLED), false);
        if (member == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号不是系统成员");
        }
        return member;
    }

    private boolean isPlatformAdmin(CurrentUser currentUser) {
        return "admin".equals(currentUser.getAccount());
    }
}
