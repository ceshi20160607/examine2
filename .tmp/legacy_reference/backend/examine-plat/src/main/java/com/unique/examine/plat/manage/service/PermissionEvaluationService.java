package com.unique.examine.plat.manage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.base.entity.Member;
import com.unique.examine.plat.base.entity.Permission;
import com.unique.examine.plat.base.entity.RolePermission;
import com.unique.examine.plat.base.mapper.PlatAccountMapper;
import com.unique.examine.plat.base.mapper.PlatMemberMapper;
import com.unique.examine.plat.base.mapper.PlatPermissionMapper;
import com.unique.examine.plat.base.mapper.PlatRolePermissionMapper;
import com.unique.examine.plat.manage.vo.PermissionEvaluationModels;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class PermissionEvaluationService {
    private final AuthorizationService authorizationService;
    private final PlatAccountMapper accountMapper;
    private final PlatMemberMapper memberMapper;
    private final PlatRolePermissionMapper rolePermissionMapper;
    private final PlatPermissionMapper permissionMapper;

    public PermissionEvaluationService(
            AuthorizationService authorizationService,
            PlatAccountMapper accountMapper,
            PlatMemberMapper memberMapper,
            PlatRolePermissionMapper rolePermissionMapper,
            PlatPermissionMapper permissionMapper
    ) {
        this.authorizationService = authorizationService;
        this.accountMapper = accountMapper;
        this.memberMapper = memberMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
    }

    public PermissionEvaluationModels.Result platform(long accountId) {
        var account = accountMapper.selectOne(Wrappers.<Account>lambdaQuery()
                .eq(Account::getId, accountId)
                .isNull(Account::getDeletedAt));
        if (account == null) {
            throw notFound();
        }
        return result("ACCOUNT", accountId, null, null, authorizationService.platform(accountId));
    }

    public PermissionEvaluationModels.Result system(long systemId, long tenantId, long memberId) {
        var member = memberMapper.selectOne(Wrappers.<Member>lambdaQuery()
                .eq(Member::getId, memberId)
                .eq(Member::getSystemId, systemId)
                .isNull(Member::getDeletedAt));
        if (member == null) {
            throw notFound();
        }
        return result(
                "MEMBER", memberId, systemId, tenantId,
                authorizationService.system(systemId, tenantId, memberId)
        );
    }

    private PermissionEvaluationModels.Result result(
            String principalType,
            long principalId,
            Long systemId,
            Long tenantId,
            AuthorizationSnapshot snapshot
    ) {
        var roleIds = snapshot.roles().stream().map(role -> Long.parseLong(role.id())).toList();
        var links = roleIds.isEmpty() ? List.<RolePermission>of() : rolePermissionMapper.selectList(
                Wrappers.<RolePermission>lambdaQuery().in(RolePermission::getRoleId, roleIds)
        );
        var permissionIds = links.stream().map(RolePermission::getPermissionId).distinct().toList();
        var permissions = permissionIds.isEmpty()
                ? List.<Permission>of()
                : permissionMapper.selectByIds(permissionIds);
        var permissionById = new LinkedHashMap<Long, Permission>();
        permissions.stream()
                .filter(permission -> "ACTIVE".equals(permission.getStatus()))
                .forEach(permission -> permissionById.put(permission.getId(), permission));

        var sources = new LinkedHashMap<String, PermissionSources>();
        for (var link : links) {
            var permission = permissionById.get(link.getPermissionId());
            if (permission == null) {
                continue;
            }
            var value = sources.computeIfAbsent(permission.getPermissionCode(), ignored -> new PermissionSources());
            if ("DENY".equals(link.getEffect())) {
                value.denyRoleIds.add(Long.toString(link.getRoleId()));
            } else {
                value.allowRoleIds.add(Long.toString(link.getRoleId()));
            }
        }

        var decisions = new ArrayList<PermissionEvaluationModels.Decision>();
        for (var entry : sources.entrySet()) {
            var value = entry.getValue();
            var denied = !value.denyRoleIds.isEmpty();
            var sourceRoleIds = denied ? value.denyRoleIds : value.allowRoleIds;
            decisions.add(new PermissionEvaluationModels.Decision(
                    entry.getKey(),
                    denied ? "DENY" : "ALLOW",
                    denied ? "EXPLICIT_DENY_WINS" : "ACTIVE_ROLE_ALLOW",
                    List.copyOf(sourceRoleIds)
            ));
        }
        decisions.sort(Comparator.comparing(PermissionEvaluationModels.Decision::permissionCode));

        return new PermissionEvaluationModels.Result(
                principalType,
                Long.toString(principalId),
                id(systemId),
                id(tenantId),
                Long.toString(snapshot.epoch()),
                snapshot.roles().stream().map(role -> new PermissionEvaluationModels.RoleSource(
                        role.id(), role.code(), role.name(), role.publishedVersion()
                )).toList(),
                snapshot.dataScopes().stream().map(scope -> new PermissionEvaluationModels.DataScopeSource(
                        scope.roleId(), scope.id(), scope.code(), scope.kind()
                )).toList(),
                decisions
        );
    }

    private static BusinessException notFound() {
        return new BusinessException("RESOURCE_NOT_FOUND", "目标身份不存在", HttpStatus.NOT_FOUND);
    }

    private static String id(Long value) {
        return value == null ? null : Long.toString(value);
    }

    private static final class PermissionSources {
        private final LinkedHashSet<String> allowRoleIds = new LinkedHashSet<>();
        private final LinkedHashSet<String> denyRoleIds = new LinkedHashSet<>();
    }
}
