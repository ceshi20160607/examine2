package com.unique.unexamine.authorization.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.unexamine.platform.base.entity.PlatformMember;
import com.unique.unexamine.platform.base.entity.PlatformMemberRole;
import com.unique.unexamine.platform.base.entity.PlatformRolePermission;
import com.unique.unexamine.platform.base.service.PlatformMemberBaseService;
import com.unique.unexamine.platform.base.service.PlatformMemberRoleBaseService;
import com.unique.unexamine.platform.base.service.PlatformRolePermissionBaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class PlatformPermissionResolver {
    private final PlatformMemberBaseService memberService;
    private final PlatformMemberRoleBaseService memberRoleService;
    private final PlatformRolePermissionBaseService permissionService;

    public PlatformPermissionResolver(
            PlatformMemberBaseService memberService,
            PlatformMemberRoleBaseService memberRoleService,
            PlatformRolePermissionBaseService permissionService) {
        this.memberService = memberService;
        this.memberRoleService = memberRoleService;
        this.permissionService = permissionService;
    }

    @Transactional(readOnly = true)
    public ResolvedPermissions resolve(Long platformId, Long accountId) {
        PlatformMember member = memberService.selectList(Wrappers.<PlatformMember>lambdaQuery()
                        .eq(PlatformMember::getPlatformId, platformId)
                        .eq(PlatformMember::getAccountId, accountId)
                        .eq(PlatformMember::getStatus, "ACTIVE"))
                .stream().findFirst().orElse(null);
        if (member == null) {
            return new ResolvedPermissions(List.of(), List.of(), Map.of());
        }
        List<Long> roleIds = memberRoleService.selectList(Wrappers.<PlatformMemberRole>lambdaQuery()
                        .eq(PlatformMemberRole::getPlatformId, platformId)
                        .eq(PlatformMemberRole::getMemberId, member.getId()))
                .stream().map(PlatformMemberRole::getRoleId).distinct().sorted().toList();
        if (roleIds.isEmpty()) {
            return new ResolvedPermissions(List.of(), List.of(), Map.of());
        }
        List<PlatformRolePermission> rows = permissionService.selectList(
                Wrappers.<PlatformRolePermission>lambdaQuery()
                        .eq(PlatformRolePermission::getPlatformId, platformId)
                        .in(PlatformRolePermission::getRoleId, roleIds));
        LinkedHashSet<String> denied = new LinkedHashSet<>();
        for (PlatformRolePermission row : rows) {
            if ("DENY".equals(row.getEffect())) {
                denied.add(key(row));
            }
        }
        LinkedHashMap<String, LinkedHashSet<Long>> grantedByRoles = new LinkedHashMap<>();
        for (PlatformRolePermission row : rows) {
            String key = key(row);
            if (!"DENY".equals(row.getEffect()) && !denied.contains(key)) {
                grantedByRoles.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(row.getRoleId());
            }
        }
        List<PermissionGrant> permissions = new ArrayList<>();
        for (Map.Entry<String, LinkedHashSet<Long>> entry : grantedByRoles.entrySet()) {
            String[] key = entry.getKey().split(":", 3);
            permissions.add(new PermissionGrant(key[0], key[1], key[2], List.copyOf(entry.getValue())));
        }
        return new ResolvedPermissions(roleIds, List.copyOf(permissions), Map.of());
    }

    private String key(PlatformRolePermission permission) {
        return permission.getResourceType() + ":" + permission.getResourceCode() + ":" + permission.getActionCode();
    }
}
