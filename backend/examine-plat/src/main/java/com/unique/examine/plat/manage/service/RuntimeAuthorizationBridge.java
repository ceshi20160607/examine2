package com.unique.examine.plat.manage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.core.api.RuntimeAuthorizationFacade;
import com.unique.examine.plat.base.entity.DataScope;
import com.unique.examine.plat.base.entity.DataScopeTarget;
import com.unique.examine.plat.base.entity.DepartmentClosure;
import com.unique.examine.plat.base.entity.MemberDepartment;
import com.unique.examine.plat.base.entity.Permission;
import com.unique.examine.plat.base.entity.RolePermission;
import com.unique.examine.plat.base.mapper.PlatDataScopeMapper;
import com.unique.examine.plat.base.mapper.PlatDataScopeTargetMapper;
import com.unique.examine.plat.base.mapper.PlatDepartmentClosureMapper;
import com.unique.examine.plat.base.mapper.PlatMemberDepartmentMapper;
import com.unique.examine.plat.base.mapper.PlatPermissionMapper;
import com.unique.examine.plat.base.mapper.PlatRolePermissionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RuntimeAuthorizationBridge implements RuntimeAuthorizationFacade {
    private final AuthorizationService authorizationService;
    private final AuthzEpochService epochService;
    private final PlatPermissionMapper permissionMapper;
    private final PlatRolePermissionMapper rolePermissionMapper;
    private final PlatDataScopeMapper dataScopeMapper;
    private final PlatDataScopeTargetMapper dataScopeTargetMapper;
    private final PlatMemberDepartmentMapper memberDepartmentMapper;
    private final PlatDepartmentClosureMapper departmentClosureMapper;

    public RuntimeAuthorizationBridge(
            AuthorizationService authorizationService,
            AuthzEpochService epochService,
            PlatPermissionMapper permissionMapper,
            PlatRolePermissionMapper rolePermissionMapper,
            PlatDataScopeMapper dataScopeMapper,
            PlatDataScopeTargetMapper dataScopeTargetMapper,
            PlatMemberDepartmentMapper memberDepartmentMapper,
            PlatDepartmentClosureMapper departmentClosureMapper
    ) {
        this.authorizationService = authorizationService;
        this.epochService = epochService;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.dataScopeMapper = dataScopeMapper;
        this.dataScopeTargetMapper = dataScopeTargetMapper;
        this.memberDepartmentMapper = memberDepartmentMapper;
        this.departmentClosureMapper = departmentClosureMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public long currentSystemEpoch(long systemId) {
        return epochService.currentSystem(systemId);
    }

    @Override
    public RuntimeGrant resolve(RuntimeAuthorizationRequest request) {
        var authorization = authorizationService.system(
                request.systemId(), request.tenantId(), request.memberId());
        var roleIds = authorization.roles().stream().map(role -> Long.parseLong(role.id())).toList();
        if (roleIds.isEmpty()) {
            return RuntimeGrant.denied(authorization.epoch());
        }
        var permission = permissionMapper.selectOne(Wrappers.<Permission>lambdaQuery()
                .eq(Permission::getScopeType, "SYSTEM")
                .eq(Permission::getSystemId, request.systemId())
                .eq(Permission::getPermissionCode, request.modulePermissionCode())
                .eq(Permission::getStatus, "ACTIVE"));
        if (permission == null) {
            return RuntimeGrant.denied(authorization.epoch());
        }
        var links = rolePermissionMapper.selectList(Wrappers.<RolePermission>lambdaQuery()
                .in(RolePermission::getRoleId, roleIds)
                .eq(RolePermission::getPermissionId, permission.getId()));
        if (links.stream().anyMatch(link -> "DENY".equals(link.getEffect()))) {
            return RuntimeGrant.denied(authorization.epoch());
        }
        var allowedRoleIds = new HashSet<Long>();
        links.stream().filter(link -> "ALLOW".equals(link.getEffect()))
                .forEach(link -> allowedRoleIds.add(link.getRoleId()));
        if (allowedRoleIds.isEmpty()) {
            return RuntimeGrant.denied(authorization.epoch());
        }

        var scopeSources = authorization.dataScopes().stream()
                .filter(scope -> allowedRoleIds.contains(Long.parseLong(scope.roleId())))
                .toList();
        var scopeIds = scopeSources.stream().map(scope -> Long.parseLong(scope.id())).distinct().toList();
        var scopeById = new HashMap<Long, DataScope>();
        if (!scopeIds.isEmpty()) {
            dataScopeMapper.selectByIds(scopeIds).stream()
                    .filter(scope -> "ACTIVE".equals(scope.getStatus()))
                    .forEach(scope -> scopeById.put(scope.getId(), scope));
        }
        var targets = scopeIds.isEmpty() ? List.<DataScopeTarget>of()
                : dataScopeTargetMapper.selectList(Wrappers.<DataScopeTarget>lambdaQuery()
                .in(DataScopeTarget::getDataScopeId, scopeIds)
                .eq(DataScopeTarget::getSystemId, request.systemId())
                .and(query -> query.isNull(DataScopeTarget::getTenantId)
                        .or().eq(DataScopeTarget::getTenantId, request.tenantId())));
        var targetsByScope = new HashMap<Long, List<DataScopeTarget>>();
        for (var target : targets) {
            targetsByScope.computeIfAbsent(target.getDataScopeId(), ignored -> new ArrayList<>()).add(target);
        }

        var ownerMembers = new HashSet<Long>();
        var ownerDepartments = new HashSet<Long>();
        var fieldRules = new ArrayList<String>();
        var allRecords = false;
        Set<Long> primaryDepartments = null;
        for (var source : scopeSources) {
            var scopeId = Long.parseLong(source.id());
            var scope = scopeById.get(scopeId);
            if (scope == null) {
                continue;
            }
            switch (scope.getScopeKind()) {
                case "ALL" -> allRecords = true;
                case "SELF" -> ownerMembers.add(request.memberId());
                case "PRIMARY_DEPARTMENT" -> {
                    if (primaryDepartments == null) {
                        primaryDepartments = primaryDepartments(request);
                    }
                    ownerDepartments.addAll(primaryDepartments);
                }
                case "DEPARTMENT_TREE" -> {
                    if (primaryDepartments == null) {
                        primaryDepartments = primaryDepartments(request);
                    }
                    ownerDepartments.addAll(departmentTree(request, primaryDepartments));
                }
                case "SELECTED_DEPARTMENTS" -> targetsByScope.getOrDefault(scopeId, List.of()).stream()
                        .filter(target -> "DEPARTMENT".equals(target.getTargetType()))
                        .map(DataScopeTarget::getDepartmentId).forEach(ownerDepartments::add);
                case "SELECTED_MEMBERS" -> targetsByScope.getOrDefault(scopeId, List.of()).stream()
                        .filter(target -> "MEMBER".equals(target.getTargetType()))
                        .map(DataScopeTarget::getMemberId).forEach(ownerMembers::add);
                case "FIELD_RULE" -> {
                    if (scope.getFieldRuleJson() != null && !scope.getFieldRuleJson().isBlank()) {
                        fieldRules.add(scope.getFieldRuleJson());
                    }
                }
                default -> { }
            }
        }
        return new RuntimeGrant(false, authorization.epoch(), allRecords,
                ownerMembers, ownerDepartments, fieldRules);
    }

    private Set<Long> primaryDepartments(RuntimeAuthorizationRequest request) {
        return memberDepartmentMapper.selectList(Wrappers.<MemberDepartment>lambdaQuery()
                        .eq(MemberDepartment::getScopeType, "SYSTEM")
                        .eq(MemberDepartment::getScopeKey, request.systemId())
                        .eq(MemberDepartment::getSystemId, request.systemId())
                        .eq(MemberDepartment::getMemberId, request.memberId())
                        .eq(MemberDepartment::getIsPrimary, true)
                        .and(query -> query.isNull(MemberDepartment::getTenantId)
                                .or().eq(MemberDepartment::getTenantId, request.tenantId())))
                .stream().map(MemberDepartment::getDepartmentId).collect(java.util.stream.Collectors.toSet());
    }

    private Set<Long> departmentTree(RuntimeAuthorizationRequest request, Set<Long> roots) {
        if (roots.isEmpty()) {
            return Set.of();
        }
        return departmentClosureMapper.selectList(Wrappers.<DepartmentClosure>lambdaQuery()
                        .eq(DepartmentClosure::getScopeType, "SYSTEM")
                        .eq(DepartmentClosure::getScopeKey, request.systemId())
                        .in(DepartmentClosure::getAncestorId, roots)
                        .and(query -> query.isNull(DepartmentClosure::getTenantId)
                                .or().eq(DepartmentClosure::getTenantId, request.tenantId())))
                .stream().map(DepartmentClosure::getDescendantId)
                .collect(java.util.stream.Collectors.toSet());
    }
}
