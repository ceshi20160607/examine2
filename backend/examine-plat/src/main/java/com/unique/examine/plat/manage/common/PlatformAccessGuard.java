package com.unique.examine.plat.manage.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.plat.base.entity.PlatRole;
import com.unique.examine.plat.base.entity.PlatRoleMember;
import com.unique.examine.plat.base.service.PlatRoleBaseService;
import com.unique.examine.plat.base.service.PlatRoleMemberBaseService;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Backend guard for platform administration APIs.
 */
@Component
public class PlatformAccessGuard {

    private static final int ENABLED = 1;
    private static final int DELETED_NO = 0;
    private static final String SCOPE_PLATFORM = "PLATFORM";
    private static final Set<String> PLATFORM_ADMIN_ROLES = Set.of("PLATFORM_ADMIN", "PLATFORM_ROOT");

    private final PlatRoleMemberBaseService roleMemberBaseService;
    private final PlatRoleBaseService roleBaseService;

    public PlatformAccessGuard(PlatRoleMemberBaseService roleMemberBaseService,
                               PlatRoleBaseService roleBaseService) {
        this.roleMemberBaseService = roleMemberBaseService;
        this.roleBaseService = roleBaseService;
    }

    public boolean canManagePlatform(Long accountId) {
        if (Objects.isNull(accountId)) {
            return false;
        }
        return roleMemberBaseService.list(new LambdaQueryWrapper<PlatRoleMember>()
                        .eq(PlatRoleMember::getAccountId, accountId)
                        .isNull(PlatRoleMember::getSystemMemberId))
                .stream()
                .map(PlatRoleMember::getRoleId)
                .filter(Objects::nonNull)
                .anyMatch(this::isPlatformAdminRole);
    }

    private boolean isPlatformAdminRole(Long roleId) {
        PlatRole role = roleBaseService.getOne(new LambdaQueryWrapper<PlatRole>()
                .eq(PlatRole::getId, roleId)
                .eq(PlatRole::getScope, SCOPE_PLATFORM)
                .eq(PlatRole::getStatus, ENABLED)
                .eq(PlatRole::getDeleted, DELETED_NO)
                .last("LIMIT 1"), false);
        return Objects.nonNull(role) && PLATFORM_ADMIN_ROLES.contains(role.getRoleCode());
    }
}
